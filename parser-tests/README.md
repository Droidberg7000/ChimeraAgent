# Isolated SSP parser tests

This project compiles the repaired parser and runs the six supplied JUnit tests on a JVM. It does NOT integrate the Android app, router, modem tools, Firebase, or Compose UI. The parser is copied unchanged from the standalone review bundle.

CI uses JDK 17, Gradle 8.8, Kotlin 2.0.21, JUnit 4.13.2, and org.json 20240303. The JSON dependency is the JVM implementation; passing does not establish identical behavior on Android's bundled org.json. Android device/instrumentation tests remain separate.

With JDK 17 and Gradle 8.8 installed: `gradle --no-daemon clean test`. No Gradle wrapper binary is supplied. Dependency and tool downloads require network access.

Workflow: .github/workflows/parser-tests.yml. A matching push runs tests on GitHub Actions if Actions is enabled and quota/policy permits. Results appear in the job summary, test logs, and uploaded XML/HTML reports. Infrastructure or dependency failures are not test passes. No execution result is claimed before the workflow completes.
