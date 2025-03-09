
plugins {
    application
    kotlin("jvm")
    kotlin("plugin.serialization")
    alias(libs.plugins.io.ktor.plugin)
    alias(libs.plugins.com.google.devtools.ksp)
    alias(libs.plugins.org.jetbrains.kotlinx.rpc.plugin)
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

    ksp(libs.kotlin.inject.compiler.ksp)
    implementation(libs.kotlin.inject.runtime)

    // Client API
    implementation(libs.kotlinx.rpc.krpc.client)
    // Server API
    implementation(libs.kotlinx.rpc.krpc.server)
    // Serialization module. Also, protobuf and cbor are provided
    implementation(libs.kotlinx.rpc.krpc.serialization.json)

    // Transport implementation for Ktor
    implementation(libs.kotlinx.rpc.krpc.ktor.client)
    implementation(libs.kotlinx.rpc.krpc.ktor.server)

    // Ktor API
    implementation("io.ktor:ktor-client-cio-jvm:3.1.1")
    implementation("io.ktor:ktor-server-netty-jvm:3.1.1")
    implementation(libs.kotlinx.datetime)

}

// Configure frontend input
configurations {
    create("frontendInput")
}

dependencies {
    "frontendInput"(project(":frontend", "frontendOutput"))
}

tasks.named<Copy>("processResources") {
    dependsOn(":frontend:build")
    from(configurations.getByName("frontendInput")) {
        into("frontend")  // Match the staticResources("/", "frontend") configuration
    }
}

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

//kapt {
//    correctErrorTypes = true
//    arguments {
//        arg("jtsgenModuleVersion", version)
//    }
//}

kotlin {
    jvmToolchain(11)
}
