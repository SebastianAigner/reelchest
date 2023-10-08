plugins {
    kotlin("multiplatform")
    kotlin("native.cocoapods")
    id("com.android.library")
    id("org.jetbrains.compose")
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
                implementation(libs.libvlc.all)
                implementation(libs.media3.exoplayer.dash)
                implementation(libs.androidx.media3.ui)
            }
        }
        val iosX64Main by getting
        val iosArm64Main by getting
        val iosSimulatorArm64Main by getting
        val iosMain by creating {
            dependsOn(commonMain)
            iosX64Main.dependsOn(this)
            iosArm64Main.dependsOn(this)
            iosSimulatorArm64Main.dependsOn(this)
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