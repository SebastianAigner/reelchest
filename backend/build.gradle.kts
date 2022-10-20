val ktor_version: String by project
val kotlin_version: String by project
val logback_version: String by project
val coroutines_version: String by project
val serialization_json_version: String by project
val jtsgen_Version: String by project


buildscript {
    repositories {
        mavenCentral()
    }
    dependencies {
        classpath("app.cash.sqldelight:gradle-plugin:_")
    }
}

plugins {
    application
    kotlin("jvm")
    kotlin("plugin.serialization")
    kotlin("kapt")
    id("app.cash.sqldelight") version "2.0.0-alpha02"
}

group = "io.sebi"
version = "0.0.1"

application {
    mainClass.set("io.ktor.server.netty.EngineMain")
}

repositories {
    mavenLocal()
    mavenCentral()
    maven { url = uri("https://kotlin.bintray.com/ktor") }
    maven {
        url = uri("https://maven.pkg.jetbrains.space/public/p/ktor/eap")
        name = "ktor-eap"
    }
}

dependencies {
    implementation(Kotlin.stdlib.jdk8)
    implementation("ch.qos.logback:logback-classic:_")
    implementation("io.ktor:ktor-server-netty-jvm:_")
    implementation("io.ktor:ktor-server-core-jvm:_")
    implementation("io.ktor:ktor-server-host-common-jvm:_")

    implementation("org.jsoup:jsoup:_")
    implementation(KotlinX.coroutines.core)
    implementation(KotlinX.serialization.json) // JVM dependency
    implementation("org.quartz-scheduler:quartz:_")
    implementation("org.reflections:reflections:_")
    implementation("io.ktor:ktor-server-html-builder-jvm:_")
    implementation("io.ktor:ktor-server-websockets-jvm:_")
    implementation("io.ktor:ktor-server-content-negotiation-jvm:_")
    implementation("io.ktor:ktor-client-apache-jvm:_")
    implementation(Ktor.server.statusPages)
    implementation(Ktor.server.cors)
    implementation(Ktor.server.partialContent)
    implementation(Ktor.server.callLogging)
    implementation(Ktor.server.contentNegotiation)
    implementation("io.ktor:ktor-client-core-jvm:_")
    implementation("io.ktor:ktor-client-serialization-jvm:_")
    implementation("io.ktor:ktor-client-cio-jvm:_")
    implementation(Ktor.client.contentNegotiation)
    implementation(Ktor.plugins.serialization.kotlinx.json)
    testImplementation("io.ktor:ktor-server-tests-jvm:_")
    compileOnly("com.github.dzuvic:jtsgen-annotations:_")
    compileOnly("com.github.dzuvic:jtsgen-processor:_")
    kapt("com.github.dzuvic:jtsgen-processor:_")
    implementation("io.minio:minio:_")
    implementation("com.github.pgreze:kotlin-process:_")
    implementation("com.michael-bull.kotlin-retry:kotlin-retry:_")

    implementation("org.xerial:sqlite-jdbc:_")

    implementation("app.cash.sqldelight:sqlite-driver:_")
}

tasks.getByName<Copy>("processResources") {
    dependsOn(":frontend:build")
    from("../frontend/dist") {
        into("frontend")
    }
}

tasks.withType<org.jetbrains.kotlin.gradle.tasks.KotlinCompile> {
    kotlinOptions {
        freeCompilerArgs = listOf("-Xopt-in=kotlin.RequiresOptIn", "-Xcontext-receivers")
    }
}


tasks.withType(JavaExec::class.java) {
    systemProperty("java.io.tmpdir", System.getProperty("java.io.tmpdir"))
    // ^ this forwards the "tmpdir" as passed via -Djava.io.tmpdir
    // It's only relevant when running the application via ./gradlew run
    // As the gradle daemon does not forward custom system properties by default.

    setWorkingDir("..")
    // ^ this is an ugly hack to preserve the previous behavior of the `run` tasks,
    // i.e. expecting mediaLibrary & co. folders in the root of the project.
    // ideally this can be ditched once config management is improved.
}

sqldelight {
    database("MediaDatabase") { // This will be the name of the generated database class.
        packageName = "io.sebi.database"
        dialect("app.cash.sqldelight:sqlite-3-25-dialect:_")
        module("app.cash.sqldelight:sqlite-json-module:_")
    }
}

kapt {
    correctErrorTypes = true
    arguments {
        arg("jtsgenModuleVersion", version)
    }
}
