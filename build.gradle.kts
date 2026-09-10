plugins { java; application }

group = "net.openosrs"
version = "1.0.1"

java {
    sourceCompatibility = JavaVersion.toVersion(21)
    targetCompatibility = JavaVersion.toVersion(21)
}
val bootstrap = sourceSets.create("bootstrap")
tasks.named<JavaCompile>(bootstrap.compileJavaTaskName) { options.release.set(11) }
application { mainClass.set("net.openosrs.launcher.Bootstrap") }
tasks.named<JavaExec>("run") { classpath += bootstrap.output }
tasks.jar {
    archiveFileName.set("openosrs-launcher-${project.version}.jar")
    manifest { attributes("Main-Class" to application.mainClass.get(), "Implementation-Version" to project.version) }
    from("LICENSE") { into("META-INF/openosrs") }
    from(bootstrap.output)
}
tasks.withType<AbstractArchiveTask>().configureEach {
    isPreserveFileTimestamps = false
    isReproducibleFileOrder = true
}
