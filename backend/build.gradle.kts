val ktor_version: String by project
val kotlin_version: String by project
val logback_version: String by project
val coroutines_version: String by project
val serialization_json_version: String by project
val jtsgen_Version: String by project

plugins {
    application
    kotlin("jvm") version "1.6.21"
    kotlin("plugin.serialization") version "1.6.21"
    kotlin("kapt") version "1.6.21"
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
    implementation("io.ktor:ktor-server-netty-jvm:2.0.0-rc-1")
    implementation("io.ktor:ktor-server-core-jvm:2.0.0-rc-1")
    implementation("io.ktor:ktor-server-host-common-jvm:2.0.0-rc-1")

    implementation("org.jsoup:jsoup:1.13.1")
    implementation("org.jetbrains.kotlinx:kotlinx-coroutines-core:$coroutines_version")
    implementation("org.jetbrains.kotlinx:kotlinx-serialization-json:$serialization_json_version") // JVM dependency
    implementation("org.quartz-scheduler:quartz:2.3.2")
    implementation("org.reflections:reflections:0.9.12")
    implementation("io.ktor:ktor-server-html-builder-jvm:2.0.0-rc-1")
    implementation("io.ktor:ktor-server-websockets-jvm:2.0.0-rc-1")
    implementation("io.ktor:ktor-server-content-negotiation-jvm:2.0.0-rc-1")
    implementation("io.ktor:ktor-client-apache-jvm:2.0.0-rc-1")
    implementation("io.ktor:ktor-server-status-pages:2.0.0-rc-1")
    implementation("io.ktor:ktor-server-cors:2.0.0-rc-1")
    implementation("io.ktor:ktor-server-partial-content:2.0.0-rc-1")
    implementation("io.ktor:ktor-server-call-logging:2.0.0-rc-1")
    implementation("io.ktor:ktor-server-content-negotiation:2.0.0-rc-1")
    implementation("io.ktor:ktor-client-core-jvm:2.0.0-rc-1")
    implementation("io.ktor:ktor-client-core-jvm:2.0.0-rc-1")
    implementation("io.ktor:ktor-client-serialization-jvm:2.0.0-rc-1")
    implementation("io.ktor:ktor-client-cio-jvm:2.0.0-rc-1")
    implementation("io.ktor:ktor-client-cio-jvm:2.0.0-rc-1")
    testImplementation("io.ktor:ktor-server-tests-jvm:2.0.0-rc-1")
    compileOnly("com.github.dzuvic:jtsgen-annotations:${jtsgen_Version}")
    compileOnly("com.github.dzuvic:jtsgen-processor:${jtsgen_Version}")
    kapt("com.github.dzuvic:jtsgen-processor:${jtsgen_Version}")
    implementation("io.minio:minio:8.3.3")
    implementation("com.github.pgreze:kotlin-process:1.3.1")
    implementation("com.michael-bull.kotlin-retry:kotlin-retry:1.0.9")
}

tasks.getByName<Copy>("processResources") {
    dependsOn(":frontend:build")
    from("../frontend/build/") {
        into("frontend")
    }
}

tasks.withType<org.jetbrains.kotlin.gradle.tasks.KotlinCompile> {
    kotlinOptions {
        freeCompilerArgs += "-Xopt-in=kotlin.RequiresOptIn"
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

kapt {
    correctErrorTypes = true
    arguments {
        arg("jtsgenModuleVersion", version)
    }
}
