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
    id("io.ktor.plugin") version "2.3.1"
}

group = "io.sebi"
version = "0.0.1"

application {
    mainClass.set("io.ktor.server.netty.EngineMain")
}

repositories {
    mavenLocal()
    mavenCentral()
    maven {
        url = uri("https://maven.pkg.jetbrains.space/public/p/ktor/eap")
        name = "ktor-eap"
    }
}

dependencies {
    implementation(libs.logback.classic)
    implementation(libs.ktor.server.netty)
    implementation(libs.ktor.server.core)
    implementation(libs.ktor.server.host.common)

    implementation(libs.ktor.server.html.builder)
    implementation(libs.ktor.server.websockets)
    implementation(libs.ktor.server.content.negotiation)
    implementation(libs.ktor.client.apache)

    implementation(libs.jsoup)
    implementation(libs.kotlinx.coroutines.core)
    implementation(libs.kotlinx.serialization.json)

    implementation(libs.quartz)
    implementation(libs.reflections)


    implementation(Ktor.server.statusPages)
    implementation(Ktor.server.cors)
    implementation(Ktor.server.partialContent)
    implementation(Ktor.server.callLogging)
    implementation(Ktor.server.contentNegotiation)
    implementation(libs.ktor.client.core)
    implementation(libs.ktor.client.serialization)
    implementation(libs.ktor.client.content.negotiation)
    implementation(libs.ktor.serialization.kotlinx.json)
    implementation(libs.xodus.openapi)
    implementation(libs.xodus.entity.store)
    implementation(libs.xodus.environment)
    implementation(libs.ktor.client.cio)
    implementation(Ktor.client.contentNegotiation)
    implementation(Ktor.plugins.serialization.kotlinx.json)

    testImplementation(libs.ktor.server.tests)
    compileOnly(libs.jtsgen.annotations)
    compileOnly(libs.jtsgen.processor)
    kapt(libs.jtsgen.processor)
    implementation(libs.minio)
    implementation(libs.kotlin.process)
    implementation(libs.kotlin.retry)

    implementation(libs.sqlite.jdbc)
    implementation(libs.sqlite.driver)
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
    databases {
        create("MediaDatabase") {
            packageName.set("io.sebi.database")
            dialect("app.cash.sqldelight:sqlite-3-25-dialect:_")
            module("app.cash.sqldelight:sqlite-json-module:_")
        }
    }
}

kapt {
    correctErrorTypes = true
    arguments {
        arg("jtsgenModuleVersion", version)
    }
}
