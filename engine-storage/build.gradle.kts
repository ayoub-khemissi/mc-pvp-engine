// Persistence: JDBC repositories + migrations.
// Deliberately Bukkit-free, so it can be tested against a real database engine
// (H2 in MySQL mode) without a Minecraft server.

dependencies {
    api(project(":engine-domain"))

    // Provided at runtime by Paper's library loader (see engine-core plugin.yml).
    compileOnly("com.zaxxer:HikariCP:5.1.0")
    compileOnly("com.mysql:mysql-connector-j:9.1.0")

    // Tests run against a real SQL engine, in memory.
    testImplementation("com.h2database:h2:2.3.232")
    testImplementation("com.zaxxer:HikariCP:5.1.0")
}
