//buildscript {
//    repositories {
//        mavenCentral()
//    }
//    dependencies {
//        classpath(CashApp.sqlDelight.gradlePlugin)
//    }
//}

plugins {
    application
    kotlin("jvm")
    kotlin("plugin.serialization")
    id("app.cash.sqldelight")
    id("io.ktor.plugin")
    id("com.google.devtools.ksp")
    id("org.jetbrains.kotlinx.rpc.plugin") version "0.5.1"
}

group = "io.sebi"
version = "0.0.1"

application {
    mainClass.set("io.sebi.ApplicationKt")
}

repositories {
//    mavenLocal()
    mavenCentral()
    maven {
        url = uri("https://maven.pkg.jetbrains.space/public/p/ktor/eap")
        name = "ktor-eap"
    }
    maven(url = "https://oss.sonatype.org/content/repositories/snapshots")
}

dependencies {
    implementation(project(":process"))
    implementation(kotlin("test"))
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

    implementation(libs.ktor.server.status.pages)
    implementation(libs.ktor.server.cors)
    implementation(libs.ktor.server.partial.content)
    implementation(libs.ktor.server.call.logging)
    implementation(libs.ktor.server.content.negotiation)
    implementation(libs.ktor.client.core)
    implementation(libs.ktor.client.serialization)
    implementation(libs.ktor.client.content.negotiation)
    implementation(libs.ktor.serialization.kotlinx.json)
    implementation(libs.ktor.client.cio)
    implementation(libs.ktor.client.content.negotiation)
    implementation(libs.ktor.serialization.kotlinx.json)

//    testImplementation(libs.ktor.server.tests)
    testImplementation(kotlin("test"))
    compileOnly(libs.jtsgen.annotations)
    compileOnly(libs.jtsgen.processor)
//    kapt(libs.jtsgen.processor)
    implementation(libs.minio)
    implementation(libs.kotlin.retry)

    implementation(libs.sqlite.jdbc)
    implementation(libs.sqlite.driver)

    ksp(libs.kotlin.inject.compiler.ksp)
    implementation(libs.kotlin.inject.runtime)

    // Client API
    implementation("org.jetbrains.kotlinx:kotlinx-rpc-krpc-client:0.5.1")
    // Server API
    implementation("org.jetbrains.kotlinx:kotlinx-rpc-krpc-server:0.5.1")
    // Serialization module. Also, protobuf and cbor are provided
    implementation("org.jetbrains.kotlinx:kotlinx-rpc-krpc-serialization-json:0.5.1")

    // Transport implementation for Ktor
    implementation("org.jetbrains.kotlinx:kotlinx-rpc-krpc-ktor-client:0.5.1")
    implementation("org.jetbrains.kotlinx:kotlinx-rpc-krpc-ktor-server:0.5.1")

    // Ktor API
    implementation("io.ktor:ktor-client-cio-jvm:3.1.1")
    implementation("io.ktor:ktor-server-netty-jvm:3.1.1")
    implementation(libs.kotlinx.datetime)

}

//tasks.getByName("build") {
//    dependsOn(":frontend:build")
//}

//// i truly don't understand why i need this, but gradle won't shut up otherwise.
//// if you can explain it, feel free to DM me.
//tasks.named("generateMainMediaDatabaseInterface") {
//    dependsOn(":frontend:build")
//}


//tasks.getByName<Copy>("processResources") {
//    dependsOn(":frontend:build")
//    from("../frontend/dist") {
//        into("frontend")
//    }
//}

tasks.withType<org.jetbrains.kotlin.gradle.tasks.KotlinCompile> {
    kotlinOptions {
        freeCompilerArgs = listOf("-Xcontext-receivers")
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
            dialect(libs.sqlite325dialect)
            module(libs.sqlite.json.module.get())
        }
    }
}

//kapt {
//    correctErrorTypes = true
//    arguments {
//        arg("jtsgenModuleVersion", version)
//    }
//}

kotlin {
    jvmToolchain(11)
}
