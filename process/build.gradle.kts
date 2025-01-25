plugins {
    kotlin("jvm")
}

group = "io.sebi"
version = "0.0.1"

repositories {
    mavenCentral()
}

dependencies {
    implementation(libs.kotlinx.coroutines.core)
    testImplementation(kotlin("test"))
}

kotlin {
    jvmToolchain(11)
}