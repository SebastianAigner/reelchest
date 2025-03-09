tasks.register<Exec>("buildReactFrontend") {
    dependsOn("installReactFrontendDeps")
    inputs.dir("src")
//    inputs.dir("static")
    inputs.dir("node_modules")
    outputs.dir("build")
    commandLine("yarn --verbose build".split(" ")) //--public-url /react
}

tasks.register<Exec>("installReactFrontendDeps") {
    inputs.file("package.json")
    inputs.file("yarn.lock")
    outputs.dir("node_modules")
    commandLine("yarn --verbose".split(" ")) //--public-url /react
}

tasks.register<Copy>("build") {
    dependsOn("buildReactFrontend")
//    dependsOn(":backend:kaptKotlin")
    destinationDir = rootDir
//    from("../backend/build/generated/source/kapt/main/") {
//        into("frontend/src/generated")
//    }
    outputs.dir("build")
}

println(tasks.toList())

tasks.register<Delete>("clean") {
    delete("node_modules")
    delete("dist")
    delete("build")
}