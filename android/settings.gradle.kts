pluginManagement {
    repositories { google(); mavenCentral(); gradlePluginPortal() }
}
dependencyResolutionManagement {
    repositories { google(); mavenCentral() }
}
rootProject.name = "ChimeraAndroid"
include(":app", ":parser")
project(":parser").projectDir = file("../parser-tests")
