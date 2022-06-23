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
        classpath("app.cash.sqldelight:gradle-plugin:2.0.0-alpha02")
    }
}

plugins {
    application
    kotlin("jvm") version "1.7.0"
    kotlin("plugin.serialization") version "1.6.21"
    kotlin("kapt") version "1.6.21"
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
    implementation("org.jetbrains.kotlin:kotlin-stdlib-jdk8:$kotlin_version")
    implementation("ch.qos.logback:logback-classic:$logback_version")
    implementation("io.ktor:ktor-server-netty-jvm:$ktor_version")
    implementation("io.ktor:ktor-server-core-jvm:$ktor_version")
    implementation("io.ktor:ktor-server-host-common-jvm:$ktor_version")

    implementation("org.jsoup:jsoup:1.13.1")
    implementation("org.jetbrains.kotlinx:kotlinx-coroutines-core:$coroutines_version")
    implementation("org.jetbrains.kotlinx:kotlinx-serialization-json:$serialization_json_version") // JVM dependency
    implementation("org.quartz-scheduler:quartz:2.3.2")
    implementation("org.reflections:reflections:0.9.12")
    implementation("io.ktor:ktor-server-html-builder-jvm:$ktor_version")
    implementation("io.ktor:ktor-server-websockets-jvm:$ktor_version")
    implementation("io.ktor:ktor-server-content-negotiation-jvm:$ktor_version")
    implementation("io.ktor:ktor-client-apache-jvm:$ktor_version")
    implementation("io.ktor:ktor-server-status-pages:$ktor_version")
    implementation("io.ktor:ktor-server-cors:$ktor_version")
    implementation("io.ktor:ktor-server-partial-content:$ktor_version")
    implementation("io.ktor:ktor-server-call-logging:$ktor_version")
    implementation("io.ktor:ktor-server-content-negotiation:$ktor_version")
    implementation("io.ktor:ktor-client-core-jvm:$ktor_version")
    implementation("io.ktor:ktor-client-serialization-jvm:$ktor_version")
    implementation("io.ktor:ktor-client-cio-jvm:$ktor_version")
    implementation("io.ktor:ktor-client-content-negotiation:$ktor_version")
    implementation("io.ktor:ktor-serialization-kotlinx-json:$ktor_version")
    testImplementation("io.ktor:ktor-server-tests-jvm:$ktor_version")
    compileOnly("com.github.dzuvic:jtsgen-annotations:${jtsgen_Version}")
    compileOnly("com.github.dzuvic:jtsgen-processor:${jtsgen_Version}")
    kapt("com.github.dzuvic:jtsgen-processor:${jtsgen_Version}")
    implementation("io.minio:minio:8.3.3")
    implementation("com.github.pgreze:kotlin-process:1.3.1")
    implementation("com.michael-bull.kotlin-retry:kotlin-retry:1.0.9")

    implementation("org.xerial:sqlite-jdbc:3.36.0.3")

    implementation("app.cash.sqldelight:sqlite-driver:2.0.0-alpha02")
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
        dialect = "app.cash.sqldelight:sqlite-3-25-dialect:2.0.0-alpha02"
        module("app.cash.sqldelight:sqlite-json-module:2.0.0-alpha02")
    }
}

kapt {
    correctErrorTypes = true
    arguments {
        arg("jtsgenModuleVersion", version)
    }
}
