# Shared SSP parser and JVM tests

The source in src/main/kotlin/com/example/ai/SspParser.kt is the single production parser. The Android build includes this directory's Gradle project as :parser and the app depends on it. No second parser copy is maintained.

The org.json dependency is compileOnly. JVM tests use org.json 20240303; Android uses the platform implementation, without packaging the JVM org.json jar. Six JUnit tests remain under src/test/kotlin. The Android app's androidTest source set reuses those exact six tests for device/emulator execution.

Standalone JVM checks: JDK 17 and Gradle 8.8, then `gradle --no-daemon clean test`. Android build: from ../android with Gradle 8.9, `gradle :parser:test :app:testDebugUnitTest :app:assembleDebug`. For the Android JSON implementation, connect a device/emulator and run `gradle :app:connectedDebugAndroidTest`.

Tool/dependency downloads require internet. No Gradle wrapper binary is supplied. CI results must be inspected; no passing run is claimed merely because a workflow was committed. Android, network, UI, and device behavior are not certified by JVM parser tests.
