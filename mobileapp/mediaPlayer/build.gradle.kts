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

        pod("MobileVLCKit") {
            version = "~>3.3.0"
        }
    }
    androidTarget()

    iosX64()
    iosArm64()
    iosSimulatorArm64()
    sourceSets {
        val commonMain by getting {
            dependencies {
                implementation(compose.runtime)
                implementation(compose.foundation)
            }
        }
        val androidMain by getting {
            dependencies {
                implementation(libs.media3.exoplayer)
                implementation(libs.media3.exoplayer.dash)
                implementation(libs.androidx.media3.ui)
            }
        }
    }
}

android {
    compileSdk = (findProperty("android.compileSdk") as String).toInt()
    namespace = "com.myapplication.mediaplayer"

    defaultConfig {
        minSdk = (findProperty("android.minSdk") as String).toInt()
    }
}

kotlin {
    jvmToolchain(17)
}