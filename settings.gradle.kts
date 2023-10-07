include("backend")
include("frontend")
includeBuild("mobileapp")
rootProject.name = "reelchest"

plugins {
    // See https://jmfayard.github.io/refreshVersions
    id("de.fayard.refreshVersions") version "0.51.0"
}