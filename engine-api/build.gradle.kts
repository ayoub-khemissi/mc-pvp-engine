// Interfaces + models that game modes compile against.
// May touch Paper types (Player, ItemStack, Component) but contains no logic.

dependencies {
    compileOnly("io.papermc.paper:paper-api:26.1.2.build.74-stable")
    api(project(":engine-domain"))
}
