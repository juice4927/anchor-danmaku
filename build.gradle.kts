import groovy.json.JsonSlurper
import java.nio.charset.StandardCharsets
import java.security.MessageDigest
import java.util.Base64
import java.util.zip.ZipFile
import javax.xml.parsers.DocumentBuilderFactory
import org.gradle.api.GradleException
import org.gradle.api.tasks.testing.Test

plugins {
    alias(libs.plugins.android.application) apply false
    alias(libs.plugins.kotlin.android) apply false
    alias(libs.plugins.kotlin.jvm) apply false
    alias(libs.plugins.kotlin.serialization) apply false
}

allprojects {
    tasks.withType<Test>().configureEach {
        useJUnit()
        systemProperty("file.encoding", "UTF-8")
        // Test workers run with the module directory as user.dir. Pass the
        // versioned fixture root explicitly so tests remain stable from an
        // ASCII junction or a normal Unicode workspace path.
        systemProperty("fixture.root", rootProject.file("fixtures/bilibili").absolutePath)
    }
}

fun ByteArray.sha256(): String =
    MessageDigest.getInstance("SHA-256")
        .digest(this)
        .joinToString("") { "%02x".format(it) }

fun requireGate(condition: Boolean, message: () -> String) {
    if (!condition) throw GradleException(message())
}

val protocolFixtureCheck by tasks.registering {
    group = "verification"
    description = "Validates the versioned Bilibili protocol fixtures."
    val fixtureRoot = layout.projectDirectory.dir("fixtures/bilibili")
    inputs.dir(fixtureRoot)
    doLast {
        val root = fixtureRoot.asFile
        val required = listOf(
            "manifest.json",
            "http/room-init-valid-short.json",
            "http/room-init-not-found.json",
            "http/room-init-restricted.json",
            "http/room-init-not-live.json",
            "http/danmu-info-valid.json",
            "http/danmu-info-no-host.json",
            "ws/auth-ok.b64",
            "ws/heartbeat-popularity.b64",
            "ws/danmaku-plain.b64",
            "ws/danmaku-zlib-nested.b64",
            "ws/danmaku-brotli-nested.b64",
            "ws/super-chat.b64",
            "ws/gift-gold.b64",
            "ws/gift-silver.b64",
            "ws/guard-buy.b64",
            "ws/multiple-packets.b64",
            "ws/unknown-command.b64",
            "ws/unsupported-protocol-version.b64",
            "ws/malformed-header.b64",
            "ws/oversized-decompressed.b64",
            "expected/danmaku-plain.json",
            "expected/danmaku-zlib-nested.json",
            "expected/danmaku-brotli-nested.json",
            "expected/super-chat.json",
            "expected/gift-gold.json",
            "expected/gift-silver.json",
            "expected/guard-buy.json",
        )
        val missing = required.filterNot { root.resolve(it).isFile }
        requireGate(missing.isEmpty()) { "Missing protocol fixtures: ${missing.joinToString()}" }

        @Suppress("UNCHECKED_CAST")
        val manifest = JsonSlurper().parse(root.resolve("manifest.json")) as Map<String, Any?>
        @Suppress("UNCHECKED_CAST")
        val entries = manifest["fixtures"] as? List<Map<String, Any?>>
            ?: throw GradleException("fixtures/bilibili/manifest.json must contain a fixtures array")
        requireGate(entries.isNotEmpty()) { "Fixture manifest must not be empty" }

        val referenced = mutableSetOf<String>()
        val ids = mutableSetOf<String>()
        entries.forEach { entry ->
            val id = entry["id"] as? String
                ?: throw GradleException("Fixture entry is missing id")
            requireGate(id.isNotBlank() && ids.add(id)) {
                "Fixture ids must be unique and non-empty: $id"
            }
            val sourcePath = entry["sourceFile"] as? String
                ?: throw GradleException("Fixture $id is missing sourceFile")
            val expectedHash = (entry["sha256"] as? String)?.lowercase()
                ?: throw GradleException("Fixture $id is missing sha256")
            requireGate(expectedHash.matches(Regex("[0-9a-f]{64}"))) {
                "Fixture $id sha256 must be a 64-character hexadecimal digest"
            }
            val operation = entry["operation"]
                ?: throw GradleException("Fixture $id is missing operation")
            val compression = entry["compression"] as? String
                ?: throw GradleException("Fixture $id is missing compression")
            val tags = entry["tags"] as? List<*>
            requireGate(tags != null && tags.isNotEmpty() && tags.all { it is String && it.isNotBlank() }) {
                "Fixture $id must declare non-empty string tags"
            }
            val source = root.resolve(sourcePath)
            requireGate(source.toPath().normalize().startsWith(root.toPath().normalize())) {
                "Fixture $id source escapes fixture root: $sourcePath"
            }
            requireGate(source.isFile) { "Fixture $id source does not exist: $sourcePath" }
            requireGate(referenced.add(sourcePath)) { "Duplicate fixture source: $sourcePath" }
            requireGate(source.readBytes().sha256() == expectedHash) {
                "Fixture $id SHA-256 mismatch"
            }
            if (source.extension == "b64") {
                requireGate(entry["protocolVersion"] is Number) {
                    "Binary fixture $id must declare numeric protocolVersion"
                }
                requireGate(operation is Number) {
                    "Binary fixture $id must declare numeric operation"
                }
                requireGate(compression in setOf("none", "zlib", "brotli", "unsupported")) {
                    "Binary fixture $id declares unsupported compression: $compression"
                }
                val decoded = try {
                    Base64.getMimeDecoder().decode(source.readText().trim())
                } catch (error: IllegalArgumentException) {
                    throw GradleException("Fixture $id is not valid Base64", error)
                }
                requireGate(decoded.size >= 16 || id == "malformed-header") {
                    "Fixture $id does not contain a complete binary payload"
                }
            } else {
                requireGate(operation == "http") {
                    "HTTP fixture $id must declare operation=http"
                }
                requireGate(compression == "none") {
                    "HTTP fixture $id must declare compression=none"
                }
            }
            val expectedPath = entry["expectedFile"] as? String
            if (expectedPath != null) {
                requireGate(root.resolve(expectedPath).isFile) {
                    "Fixture $id expected snapshot does not exist: $expectedPath"
                }
                referenced += expectedPath
            } else {
                requireGate(entry["expectedError"] is String || id in setOf(
                    "room-init-valid-short",
                    "room-init-not-live",
                    "danmu-info-valid",
                    "auth-ok",
                    "heartbeat-popularity",
                    "multiple-packets",
                )) { "Fixture $id must declare expectedFile or expectedError" }
            }
        }
        val expectedFiles = root.resolve("expected")
            .walkTopDown()
            .filter { it.isFile }
            .map { it.relativeTo(root).invariantSeparatorsPath }
            .toSet()
        requireGate(expectedFiles.all { it in referenced }) {
            "Unreferenced expected snapshots: ${(expectedFiles - referenced).joinToString()}"
        }
        val declaredSources = entries.map { it["sourceFile"] as String }.toSet()
        val fixtureSources = sequenceOf("http", "ws")
            .flatMap { directory ->
                root.resolve(directory).walkTopDown().filter { it.isFile }
            }
            .map { it.relativeTo(root).invariantSeparatorsPath }
            .toSet()
        requireGate(fixtureSources == declaredSources) {
            "Fixture source list does not match manifest (missing=${fixtureSources - declaredSources}, extra=${declaredSources - fixtureSources})"
        }
        logger.lifecycle("protocolFixtureCheck PASS: ${entries.size} manifest entries")
    }
}

val permissionAllowlistCheck by tasks.registering {
    group = "verification"
    description = "Validates the merged release manifest permission and component allowlist."
    dependsOn(":app:processReleaseMainManifest")
    doLast {
        val manifest = fileTree("app/build/intermediates/merged_manifest/release")
            .matching { include("**/AndroidManifest.xml") }
            .files
            .maxByOrNull { it.lastModified() }
            ?: throw GradleException("Merged release manifest was not produced")
        val factory = DocumentBuilderFactory.newInstance().apply { isNamespaceAware = true }
        val document = factory.newDocumentBuilder().parse(manifest)
        val androidNamespace = "http://schemas.android.com/apk/res/android"
        val permissionNodes = document.getElementsByTagName("uses-permission")
        val permissions = buildSet {
            for (index in 0 until permissionNodes.length) {
                val node = permissionNodes.item(index)
                add(node.attributes.getNamedItemNS(androidNamespace, "name").nodeValue)
            }
        }
        val allowed = setOf(
            "android.permission.INTERNET",
            "android.permission.ACCESS_NETWORK_STATE",
            "android.permission.FOREGROUND_SERVICE",
            "android.permission.FOREGROUND_SERVICE_DATA_SYNC",
            "android.permission.POST_NOTIFICATIONS",
            "android.permission.VIBRATE",
        )
        val required = setOf(
            "android.permission.INTERNET",
            "android.permission.ACCESS_NETWORK_STATE",
            "android.permission.FOREGROUND_SERVICE",
        )
        requireGate(permissions.all { it in allowed }) {
            "Unexpected release permissions: ${(permissions - allowed).joinToString()}"
        }
        requireGate(required.all { it in permissions }) {
            "Missing required release permissions: ${(required - permissions).joinToString()}"
        }

        val application = document.getElementsByTagName("application").item(0)
        requireGate(application.attributes.getNamedItemNS(androidNamespace, "allowBackup").nodeValue == "false") {
            "Release application must set allowBackup=false"
        }
        requireGate(application.attributes.getNamedItemNS(androidNamespace, "usesCleartextTraffic").nodeValue == "false") {
            "Release application must set usesCleartextTraffic=false"
        }

        val services = document.getElementsByTagName("service")
        var connectionServiceFound = false
        for (index in 0 until services.length) {
            val service = services.item(index)
            val name = service.attributes.getNamedItemNS(androidNamespace, "name").nodeValue
            if (name.endsWith("ConnectionForegroundService")) {
                connectionServiceFound = true
                requireGate(service.attributes.getNamedItemNS(androidNamespace, "exported").nodeValue == "false") {
                    "ConnectionForegroundService must not be exported"
                }
                requireGate(service.attributes.getNamedItemNS(androidNamespace, "foregroundServiceType").nodeValue == "dataSync") {
                    "ConnectionForegroundService must use foregroundServiceType=dataSync"
                }
            }
        }
        requireGate(connectionServiceFound) { "ConnectionForegroundService missing from merged manifest" }

        val exportedComponents = mutableListOf<String>()
        listOf("activity", "activity-alias", "service", "receiver", "provider").forEach { tag ->
            val components = document.getElementsByTagName(tag)
            for (index in 0 until components.length) {
                val component = components.item(index)
                val exported = component.attributes
                    .getNamedItemNS(androidNamespace, "exported")
                    ?.nodeValue
                if (exported == "true") {
                    val name = component.attributes
                        .getNamedItemNS(androidNamespace, "name")
                        ?.nodeValue
                    exportedComponents += "$tag:$name"
                }
            }
        }
        requireGate(exportedComponents.size == 1 && exportedComponents.single().endsWith(":cn.danmaku.anchor.MainActivity")) {
            "MainActivity must be the only exported component: $exportedComponents"
        }

        val mainActivity = document.getElementsByTagName("activity").item(0)
        requireGate(mainActivity.attributes.getNamedItemNS(androidNamespace, "name").nodeValue.endsWith("MainActivity")) {
            "MainActivity entry is missing from the merged manifest"
        }
        val filters = (0 until mainActivity.childNodes.length)
            .map { mainActivity.childNodes.item(it) }
            .filter { it.nodeName == "intent-filter" }
        requireGate(filters.size == 1) { "MainActivity must have exactly one intent-filter" }
        val filterNames = filters.single().childNodes.let { children ->
            (0 until children.length)
                .map { children.item(it) }
                .filter { it.nodeName == "action" || it.nodeName == "category" }
                .mapNotNull { it.attributes.getNamedItemNS(androidNamespace, "name")?.nodeValue }
                .toSet()
        }
        requireGate(filterNames == setOf("android.intent.action.MAIN", "android.intent.category.LAUNCHER")) {
            "MainActivity must expose only MAIN/LAUNCHER: $filterNames"
        }
        logger.lifecycle("permissionAllowlistCheck PASS: ${permissions.size} allowed permissions")
    }
}

val releaseHygieneCheck by tasks.registering {
    group = "verification"
    description = "Validates that release artifacts contain no debug fixture or sensitive material."
    dependsOn(":app:assembleRelease")
    doLast {
        val apk = fileTree("app/build/outputs/apk/release")
            .matching { include("*.apk") }
            .singleFile
        val forbidden = listOf(
            "fixtures/bilibili",
            "fixture-token-not-secret",
            "测试观众",
            "回放演示",
            "SESSDATA",
            "bili_jct",
            "trustAllCertificates",
        )
        ZipFile(apk).use { zip ->
            val names = zip.entries().asSequence().map { it.name }.toList()
            requireGate(names.none { it.contains("fixtures/", ignoreCase = true) }) {
                "Release APK contains fixture resources"
            }
            requireGate(names.none { it.endsWith("fixtures/bilibili/manifest.json", ignoreCase = true) }) {
                "Release APK contains the fixture manifest"
            }
            zip.entries().asSequence().filterNot { it.isDirectory }.forEach { entry ->
                val bytes = zip.getInputStream(entry).use { it.readBytes() }
                val searchable = listOf(
                    String(bytes, StandardCharsets.ISO_8859_1),
                    String(bytes, StandardCharsets.UTF_8),
                )
                val hit = forbidden.firstOrNull { marker ->
                    searchable.any { content -> content.contains(marker, ignoreCase = true) }
                }
                requireGate(hit == null) { "Release APK contains forbidden marker '$hit' in ${entry.name}" }
            }
        }
        val sourceFiles = files(
            fileTree("app/src/main") { include("**/*.kt", "**/*.java", "**/*.xml") },
            fileTree("app/src/release") { include("**/*.kt", "**/*.java", "**/*.xml") },
            fileTree("core") { include("**/src/main/**/*.kt", "**/src/main/**/*.java", "**/src/main/**/*.xml") },
        ).asFileTree
        val forbiddenSourcePatterns = listOf(
            Regex("""android\.util\.Log"""),
            Regex("""\bLog\.(v|d|i|w|e|wtf)\s*\("""),
            Regex("""printStackTrace\s*\("""),
            Regex("""trustAllCertificates""", RegexOption.IGNORE_CASE),
            Regex("""hostnameVerifier\s*=\s*HostnameVerifier\s*\{[^}]*true"""),
            Regex("""\b(TODO|FIXME|NotImplementedError)\b"""),
        )
        sourceFiles.forEach { source ->
            val content = source.readText()
            val pattern = forbiddenSourcePatterns.firstOrNull { it.containsMatchIn(content) }
            requireGate(pattern == null) { "Release source hygiene violation in ${source.relativeTo(projectDir)}: $pattern" }
        }
        logger.lifecycle("releaseHygieneCheck PASS: ${apk.name}")
    }
}

val perfSmoke by tasks.registering {
    group = "verification"
    description = "Runs the deterministic 12,000-event pipeline smoke test."
    dependsOn(":core:domain:test")
    doLast {
        val resultFiles = fileTree("core/domain/build/test-results/test") {
            include("TEST-*.xml")
        }.files
        val matching = resultFiles.filter {
            it.readText().contains("PerfSmoke", ignoreCase = true)
        }
        requireGate(matching.isNotEmpty()) {
            "No deterministic PerfSmoke test result was produced"
        }
        matching.forEach { result ->
            val document = DocumentBuilderFactory.newInstance().newDocumentBuilder().parse(result)
            val suite = document.documentElement
            val failures = suite.getAttribute("failures").toIntOrNull() ?: 0
            val errors = suite.getAttribute("errors").toIntOrNull() ?: 0
            requireGate(failures == 0 && errors == 0) {
                "Performance smoke failed: ${result.name}"
            }
        }
        logger.lifecycle("perfSmoke PASS: deterministic 12,000-event test")
    }
}

val apkSizeCheck by tasks.registering {
    group = "verification"
    description = "Validates APK existence and the 25 MiB release size limit."
    dependsOn(":app:assembleDebug", ":app:assembleRelease")
    doLast {
        val debug = fileTree("app/build/outputs/apk/debug").matching { include("*.apk") }.singleFile
        val release = fileTree("app/build/outputs/apk/release").matching { include("*.apk") }.singleFile
        val maxReleaseBytes = 25L * 1024L * 1024L
        requireGate(debug.isFile && release.isFile) { "Debug or release APK is missing" }
        requireGate(release.length() <= maxReleaseBytes) {
            "Release APK exceeds 25 MiB: ${release.length()} bytes"
        }
        logger.lifecycle("Debug APK: ${debug.absolutePath} | ${debug.length()} | ${debug.readBytes().sha256()}")
        logger.lifecycle("Release APK: ${release.absolutePath} | ${release.length()} | ${release.readBytes().sha256()}")
        logger.lifecycle("apkSizeCheck PASS")
    }
}

tasks.register("verifyAll") {
    group = "verification"
    description = "Runs the complete offline delivery gate."
    dependsOn(
        ":core:model:test",
        ":core:protocol:test",
        ":core:domain:test",
        ":app:testDebugUnitTest",
        ":core:protocol:jacocoTestCoverageVerification",
        ":core:domain:jacocoTestCoverageVerification",
        ":app:lintDebug",
        ":app:assembleDebug",
        ":app:assembleRelease",
        ":app:assembleDebugAndroidTest",
        protocolFixtureCheck,
        permissionAllowlistCheck,
        releaseHygieneCheck,
        perfSmoke,
        apkSizeCheck,
    )
}
