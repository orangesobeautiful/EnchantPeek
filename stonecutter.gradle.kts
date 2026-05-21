plugins {
    id("dev.kikugie.stonecutter")
}

val activeMinecraftVersion = "1.21.11"

stonecutter.active(activeMinecraftVersion)

tasks.register("checkActive") {
    group = "verification"
    description = "Runs checks for the active Minecraft version."
    dependsOn(":$activeMinecraftVersion:check")
}

tasks.register("buildActive") {
    group = "build"
    description = "Builds the active Minecraft version."
    dependsOn(":$activeMinecraftVersion:build")
}

tasks.register("runActiveClient") {
    group = "fabric"
    description = "Runs the active Minecraft version's development client."
    dependsOn(":$activeMinecraftVersion:runClient")
}
