package cn.danmaku.anchor.protocol.bili

import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonElement
import okhttp3.mockwebserver.MockResponse
import java.nio.file.Files
import java.nio.file.Path
import java.util.Base64
import kotlin.io.path.readBytes
import kotlin.io.path.readText

internal val testJson: Json = Json { ignoreUnknownKeys = true }

internal fun fixtureRoot(): Path {
    System.getProperty("fixture.root")?.let { configured ->
        val path = Path.of(configured).toAbsolutePath().normalize()
        if (Files.exists(path.resolve("manifest.json"))) {
            return path
        }
    }
    var current = Path.of(System.getProperty("user.dir")).toAbsolutePath().normalize()
    while (current != current.root) {
        val candidate = current.resolve("fixtures").resolve("bilibili").resolve("manifest.json")
        if (Files.exists(candidate)) {
            return candidate.parent
        }
        current = current.parent ?: break
    }
    error("Cannot locate fixtures/bilibili from ${System.getProperty("user.dir")}")
}

internal fun readFixtureText(relativePath: String): String =
    fixtureRoot().resolve(relativePath).readText()

internal fun readFixtureBytes(relativePath: String): ByteArray =
    fixtureRoot().resolve(relativePath).readBytes()

internal fun readBase64Fixture(relativePath: String): ByteArray =
    Base64.getDecoder().decode(readFixtureText(relativePath).trim())

internal fun readJson(relativePath: String): JsonElement =
    testJson.parseToJsonElement(readFixtureText(relativePath))

internal fun wbiNavResponse(): MockResponse = MockResponse()
    .setResponseCode(200)
    .setHeader("Content-Type", "application/json")
    .setBody(
        """
        {"code":0,"message":"OK","ttl":1,"data":{"wbi_img":{
          "img_url":"https://i0.hdslb.com/bfs/wbi/7cd084941338484aae1ad9425b84077c.png",
          "sub_url":"https://i0.hdslb.com/bfs/wbi/4932caff0ff746eab6f01bf08b70ac45.png"
        }}}
        """.trimIndent(),
    )
