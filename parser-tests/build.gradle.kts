plugins {
    kotlin("jvm") version "2.0.21"
}

repositories { mavenCentral() }

kotlin { jvmToolchain(17) }

dependencies {
    // Android supplies org.json; do not package a second implementation in the APK.
    compileOnly("org.json:json:20240303")
    testImplementation("org.json:json:20240303")
    testImplementation("junit:junit:4.13.2")
}

tasks.test {
    useJUnit()
    testLogging { events("passed", "skipped", "failed") }
}
