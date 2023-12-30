plugins {
    alias(libs.plugins.kotlinMultiplatform)
    kotlin("native.cocoapods")
    alias(libs.plugins.androidLib)
    alias(libs.plugins.jetbrainsCompose)
    alias(libs.plugins.serialization)
    alias(libs.plugins.realm.kotlin)
}

kotlin {
    androidTarget()

    iosX64()
    iosArm64()
    iosSimulatorArm64()

    cocoapods {
        version = "1.0.0"
        summary = "Some description for the Shared Module"
        homepage = "Link to the Shared Module homepage"
        ios.deploymentTarget = "14.1"
        podfile = project.file("../iosApp/Podfile")
        framework {
            baseName = "shared"
            isStatic = true
        }

        pod("MobileVLCKit") {
            version = "~>3.3.0"
        }
    }

    sourceSets {
        val commonMain by getting {
            dependencies {

                implementation(project(":mediaPlayer"))

                implementation(libs.kamel.image)

                val voyagerVersion = "1.0.0-rc06"
                implementation(libs.voyager.navigator)
//                implementation("cafe.adriel.voyager:voyager-transitions:$voyagerVersion")
                // BottomSheetNavigator
                implementation(libs.voyager.bottom.sheet.navigator)
                // TabNavigator
                implementation(libs.voyager.tab.navigator)
                implementation(libs.ktor.client.core)
                implementation(libs.ktor.client.content.negotiation)
                implementation(libs.ktor.serialization.kotlinx.json)
                implementation(libs.kotlinx.serialization.json)

                implementation(libs.multiplatform.settings)
                implementation(libs.multiplatform.settings.no.arg)
                implementation(libs.multiplatform.settings.coroutines)

                implementation(libs.store5)

                implementation(libs.library.base)
                implementation(libs.library.sync) // If using Device Sync
                implementation(libs.kotlinx.coroutines.core) // If using coroutines with the SDK

                implementation(compose.runtime)
                implementation(compose.foundation)
                implementation(compose.material)
                @OptIn(org.jetbrains.compose.ExperimentalComposeLibrary::class)
                implementation(compose.components.resources)

            }
        }
        val androidMain by getting {
            dependencies {
                api(libs.activity.compose)
                api(libs.appcompat)
                api(libs.core.ktx)
                implementation(libs.media3.exoplayer)
                implementation(libs.libvlc.all)
                implementation(libs.media3.exoplayer.dash)
                implementation(libs.androidx.media3.ui)
                implementation(libs.ktor.client.android)

            }
        }
        val iosMain by creating {
            dependencies {
                implementation(libs.ktor.client.darwin)
            }
        }
    }
}

android {
    compileSdk = (findProperty("android.compileSdk") as String).toInt()
    namespace = "com.myapplication.common"

    sourceSets["main"].manifest.srcFile("src/androidMain/AndroidManifest.xml")
    sourceSets["main"].res.srcDirs("src/androidMain/res")
    sourceSets["main"].resources.srcDirs("src/commonMain/resources")

    defaultConfig {
        minSdk = (findProperty("android.minSdk") as String).toInt()
        targetSdk = (findProperty("android.targetSdk") as String).toInt()
    }
    compileOptions {
        sourceCompatibility = JavaVersion.VERSION_17
        targetCompatibility = JavaVersion.VERSION_17
    }
    kotlin {
        jvmToolchain(17)
    }
}


// TODO: Workaround for https://github.com/realm/realm-kotlin/issues/887
project.afterEvaluate {
    kotlin.targets.all {
        compilations.all {
            (kotlinOptions as? org.jetbrains.kotlin.gradle.dsl.KotlinJvmOptions)?.jvmTarget = "17"
        }
    }
}