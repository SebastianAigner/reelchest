tasks.register<Exec>("buildReactFrontend") {
    dependsOn("installReactFrontendDeps")
    inputs.dir("src")
    inputs.dir("node_modules")
    inputs.file("package.json")
    inputs.file("vite.config.ts")
    outputs.dir("dist")  // Vite's default output directory

    // Use workingDir to ensure we're in the right directory
    workingDir = projectDir
    commandLine("yarn", "build")

    // Add more debug logging
    doLast {
        println("Vite build completed")
        println("dist directory exists: ${file("dist").exists()}")
        if (file("dist").exists()) {
            println("dist directory contents: ${file("dist").list()?.joinToString(", ")}")
        }
    }
}

tasks.register<Exec>("installReactFrontendDeps") {
    inputs.file("package.json")
    inputs.file("yarn.lock")
    outputs.dir("node_modules")
    commandLine("yarn --verbose".split(" ")) //--public-url /react
}

tasks.register<Copy>("build") {
    dependsOn("buildReactFrontend")
    from("dist") {  // Vite's output directory
        include("**/*")  // Include all files and subdirectories
    }
    into(layout.buildDirectory.dir("web").get().asFile)
}

// Create a configuration to expose frontend artifacts
configurations.create("frontendOutput")
artifacts {
    add("frontendOutput", layout.buildDirectory.dir("web").get().asFile) {
        builtBy("build")
    }
}

tasks.register<Delete>("clean") {
    delete("node_modules")
    delete("dist")
    delete(layout.buildDirectory)  // Updated to use layout.buildDirectory
}
