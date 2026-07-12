// The Paper plugin. Implements the API using Bukkit/Paper.

dependencies {
    compileOnly("io.papermc.paper:paper-api:26.1.2.build.74-stable")

    implementation(project(":engine-api"))
    implementation(project(":engine-domain"))
    implementation(project(":engine-storage"))

    // Provided at runtime by Paper's library loader (see plugin.yml "libraries").
    compileOnly("com.zaxxer:HikariCP:5.1.0")
    compileOnly("com.mysql:mysql-connector-j:9.1.0")
}

// Bundle engine-api + engine-domain classes into the plugin jar.
// (Hikari/MySQL are NOT bundled — Paper downloads them at runtime.)
tasks.named<Jar>("jar") {
    dependsOn(":engine-api:jar", ":engine-domain:jar", ":engine-storage:jar")
    from(configurations.runtimeClasspath.map { classpath ->
        classpath.filter { it.name.endsWith(".jar") }.map { zipTree(it) }
    })
    duplicatesStrategy = DuplicatesStrategy.EXCLUDE
}
