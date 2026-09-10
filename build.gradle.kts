plugins { java; application }

group = "net.openosrs"
version = "1.0.0"

java {
    sourceCompatibility = JavaVersion.toVersion(21)
    targetCompatibility = JavaVersion.toVersion(21)
}
application { mainClass.set("net.openosrs.launcher.Launcher") }
tasks.jar {
    archiveFileName.set("openosrs-launcher-${project.version}.jar")
    manifest { attributes("Main-Class" to application.mainClass.get(), "Implementation-Version" to project.version) }
    from("LICENSE") { into("META-INF/openosrs") }
}
tasks.withType<AbstractArchiveTask>().configureEach {
    isPreserveFileTimestamps = false
    isReproducibleFileOrder = true
}
