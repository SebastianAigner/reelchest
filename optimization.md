# Docker Layered Jars Optimization (Reelchest)

## Goal

Avoid shipping a single fat/uber JAR. Instead, ship:
- **Dependency JARs** in a `/app/lib` directory
- **Your application JAR** as `/app/app.jar`

This lets Docker cache the dependency layer and only rebuild/push the small app layer when your code changes.

## How it works here

### 1) Gradle tasks that stage files for Docker

In `backend/build.gradle.kts` we create two copy tasks and a `dockerPrepare` task:

- `dockerClean`: deletes `build/docker/lib` and `build/docker/app` to prevent stale jars
- `dockerCopyLibs`: copies `configurations.runtimeClasspath` into `build/docker/lib`
- `dockerCopyJar`: copies the app JAR from the `jar` task into `build/docker/app/app.jar`
- `dockerPrepare`: depends on both and becomes the single entry point for staging

Resulting layout:

```
backend/build/docker/
  lib/        <- dependency jars
  app/
    app.jar   <- application jar
```

### 2) Dockerfile uses separate layers

The Dockerfile copies the dependency layer first, then the app JAR:

```
COPY backend/build/docker/lib/ /app/lib/
COPY backend/build/docker/app/app.jar /app/app.jar
```

At runtime, the app runs via classpath:

```
java -cp /app/app.jar:/app/lib/* io.sebi.ApplicationKt
```

Because `lib/` changes far less often than your app JAR, Docker reuses the cached layer for dependencies.

## How to apply this to another project

### Gradle (Kotlin DSL)

Add tasks similar to:

```
val dockerLibDir = layout.buildDirectory.dir("docker/lib")
val dockerAppDir = layout.buildDirectory.dir("docker/app")

tasks.register<Copy>("dockerCopyLibs") {
    dependsOn("dockerClean")
    from(configurations.runtimeClasspath)
    into(dockerLibDir)
    duplicatesStrategy = DuplicatesStrategy.EXCLUDE
}

tasks.register<Copy>("dockerCopyJar") {
    dependsOn("dockerClean")
    dependsOn(tasks.named("jar"))
    from(tasks.named<Jar>("jar").flatMap { it.archiveFile })
    into(dockerAppDir)
    rename { "app.jar" }
}

tasks.register("dockerPrepare") {
    dependsOn("dockerClean", "dockerCopyLibs", "dockerCopyJar")
}
```

Then build with:

```
./gradlew :backend:dockerPrepare
```

### Dockerfile

Use separate copy layers and run via classpath:

```
COPY backend/build/docker/lib/ /app/lib/
COPY backend/build/docker/app/app.jar /app/app.jar
ENTRYPOINT ["sh", "-c", "java ${JAVA_OPTS} -cp /app/app.jar:/app/lib/* com.example.MainKt"]
```

## Notes

- This assumes you are **not** using a shadow/fat JAR.
- The app JAR should be small and change frequently; dependencies should be stable.
- If you use Spring Boot’s `bootJar`, either disable it or use `jar` for the thin JAR.
- Keep `dockerPrepare` as the build step for CI to ensure the folder structure is ready for Docker.
