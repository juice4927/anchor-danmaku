# 自动验收报告

AUTO GATE: PASS

Command: `.\gradlew.bat --no-daemon clean verifyAll`

- Verified at: `2026-08-12 04:00 +08:00`
- Environment: JDK 21.0.8; Android SDK 34; Gradle worker count limited to 1 through `GRADLE_OPTS` to control host memory.
- Gradle result: PASS; `verifyAll` completed with exit code 0 after a fresh `clean`.
- Unit tests: PASS; model 4, domain 52, protocol 78, app 18; total 152, failures 0, errors 0, skipped 0.
- Coverage: PASS; protocol line 93.32% (727/779), branch 78.52% (424/540); domain line 92.98% (609/655), branch 76.17% (227/298). Thresholds are line 85% and branch 75%.
- Lint: PASS; 0 errors, 7 non-blocking warnings. HTML report: `app/build/reports/lint-results-debug.html`.
- Builds: PASS; Debug APK, unsigned Release APK and Debug AndroidTest APK all produced.
- Fixture check: PASS; 20 manifest entries, required files and SHA-256 values verified.
- Security checks: PASS; 6 allowed permissions, only `MainActivity` exported, cleartext disabled, backups disabled, release fixture/credential/log/trust markers absent.
- Performance smoke: PASS; exactly 12,000 events, 30-second timeout, bounded visible state, all 80 important events retained, `criticalDropCount=0`.
- Debug APK: `app/build/outputs/apk/debug/app-debug.apk`; 17,391,693 bytes; SHA-256 `562d19490a49e0277911761a60c7adac8d36668b23da9e9643008bb2039fc12e`.
- Release APK: `app/build/outputs/apk/release/app-release-unsigned.apk`; 1,489,643 bytes; SHA-256 `2d27084dc6840703d4f3b579509c436686c1f7145313f660709f4869ddc4ef28`; below 25 MiB.
- AndroidTest APK: `app/build/outputs/apk/androidTest/debug/app-debug-androidTest.apk`; 997,729 bytes; SHA-256 `202814df7b1bc64794640ab97bd88b8287f340fc4619ea0e92b7653827f61dca`.
- Device tests: NOT RUN; the runner could not initialize adb state (`Cannot mkdir '\.android': Permission denied`), so no connected-device evidence exists. Android instrumentation sources and APK compilation passed.
- Manual release gate: NOT RUN
- Known risks: B站非官方协议变化、游客字段缺失、OEM 后台限制和真机长稳尚待人工门验证。

自动门通过只证明离线构建、单元测试、覆盖率、Lint、Manifest/Release 安全门和产物检查。公开分发前仍必须完成 `docs/manual-release-checklist.md`。
