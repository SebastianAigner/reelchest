include("backend")
include("frontend")
include("process")
//includeBuild("mobileapp")
rootProject.name = "reelchest"


// settings.gradle.kts
pluginManagement {
    repositories {
        gradlePluginPortal()
        maven(url = "https://oss.sonatype.org/content/repositories/snapshots")
    }
}