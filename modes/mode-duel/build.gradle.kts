// A game mode plugin.
//
// Note what is NOT here: no queue, no arena, no ELO, no GUI, no database.
// The engine provides all of that. This jar only says "here are my rules".
//
// engine-api is compileOnly: at runtime those classes come from the PvPEngine plugin.

dependencies {
    compileOnly("io.papermc.paper:paper-api:26.1.2.build.74-stable")
    compileOnly(project(":engine-api"))
    compileOnly(project(":engine-domain"))
}
