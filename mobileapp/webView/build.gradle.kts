plugins {
    alias(libs.plugins.kotlinMultiplatform)
    kotlin("native.cocoapods")
    alias(libs.plugins.androidLib)
    alias(libs.plugins.jetbrainsCompose)
    alias(libs.plugins.compose.compiler)
}

kotlin {
    cocoapods {
        version = "1.0"
        ios.deploymentTarget = "13.5"
        summary = "CocoaPods test library"
        homepage = "https://github.com/JetBrains/kotlin"
    }
    androidTarget()
    jvm()

    iosX64()
    iosArm64()
    iosSimulatorArm64()
    sourceSets {
        val jvmMain by getting {
            dependencies {
                implementation(compose.desktop.common)
                implementation(compose.desktop.currentOs)
            }
        }
        val commonMain by getting {
            dependencies {
                implementation(compose.runtime)
                implementation(compose.foundation)
                implementation(compose.material)
                @OptIn(org.jetbrains.compose.ExperimentalComposeLibrary::class)
                implementation(compose.components.resources)
            }
        }
        val androidMain by getting {
            dependencies {
            }
        }
    }
}

android {
    compileSdk = (findProperty("android.compileSdk") as String).toInt()
    namespace = "com.myapplication.windowmanager"

    defaultConfig {
        minSdk = (findProperty("android.minSdk") as String).toInt()
    }
}

kotlin {
    jvmToolchain(17)
}