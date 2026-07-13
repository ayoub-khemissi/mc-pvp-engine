// The Fortress game mode.
//
// Bigger than mode-duel, because it really does have its own world: players build
// fortresses, and those have to be stored. That storage is ITS OWN — the engine has no
// idea a "fortresses" table exists. It reaches the connection pool through the SPI.
//
// engine-api and engine-domain are compileOnly: at runtime those classes come from the
// PvPEngine plugin, which is already on the classpath.

dependencies {
    compileOnly("io.papermc.paper:paper-api:26.1.2.build.74-stable")
    compileOnly(project(":engine-api"))
    compileOnly(project(":engine-domain"))

    // The repository is Bukkit-free, so it is tested against a real SQL engine, in memory —
    // the same deal as engine-storage. No MySQL, no Docker, no server.
    testImplementation(project(":engine-domain"))
    testImplementation(project(":engine-storage"))   // MigrationRunner: run our own .sql
    testImplementation("com.h2database:h2:2.3.232")
    testImplementation("com.zaxxer:HikariCP:5.1.0")
}
