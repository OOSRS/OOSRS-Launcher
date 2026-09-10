plugins { java; application }

group = "net.openosrs"
version = "1.0.4"

repositories { mavenCentral() }
dependencies { implementation("org.apache.commons:commons-compress:1.28.0") }

java {
    sourceCompatibility = JavaVersion.VERSION_11
    targetCompatibility = JavaVersion.VERSION_11
}
tasks.withType<JavaCompile>().configureEach { options.release.set(11) }
application { mainClass.set("net.openosrs.launcher.Launcher") }
tasks.jar {
    duplicatesStrategy = DuplicatesStrategy.EXCLUDE
    from(configurations.runtimeClasspath.get().map { zipTree(it) }) {
        exclude("META-INF/*.SF", "META-INF/*.RSA", "META-INF/*.DSA", "module-info.class", "META-INF/versions/**/module-info.class")
    }
    configurations.runtimeClasspath.get().forEach { dependency ->
        from(zipTree(dependency)) {
            include("META-INF/LICENSE*", "META-INF/NOTICE*")
            into("META-INF/third-party/${dependency.name}")
        }
    }
    archiveFileName.set("openosrs-launcher-${project.version}.jar")
    manifest { attributes("Main-Class" to application.mainClass.get(), "Implementation-Version" to project.version) }
    from("LICENSE") { into("META-INF/openosrs") }
}
tasks.withType<AbstractArchiveTask>().configureEach {
    isPreserveFileTimestamps = false
    isReproducibleFileOrder = true
}
