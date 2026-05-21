plugins {
    id("net.fabricmc.fabric-loom-remap") version "1.14.4"
    id("com.diffplug.spotless") version "7.2.1"
    id("com.modrinth.minotaur") version "2.9.0"
    `maven-publish`
    checkstyle
}

val modId: String by project
val modVersion: String by project
val mavenGroup: String by project
val archivesBaseName: String by project
val minecraftVersion: String by project
val fabricLoaderVersion: String by project
val fabricApiVersion: String by project

version = "$modVersion+$minecraftVersion"
group = mavenGroup

base {
    archivesName.set("$archivesBaseName-$minecraftVersion")
}

repositories {
    mavenCentral()
}

loom {
    splitEnvironmentSourceSets()

    mods {
        create(modId) {
            sourceSet(sourceSets["main"])
            sourceSet(sourceSets["client"])
        }
    }
}

dependencies {
    minecraft("com.mojang:minecraft:$minecraftVersion")
    mappings(loom.officialMojangMappings())
    modImplementation("net.fabricmc:fabric-loader:$fabricLoaderVersion")
    modImplementation("net.fabricmc.fabric-api:fabric-api:$fabricApiVersion")
}

java {
    toolchain {
        languageVersion.set(JavaLanguageVersion.of(21))
    }

    withSourcesJar()
}

tasks.withType<JavaCompile>().configureEach {
    options.encoding = "UTF-8"
    options.release.set(21)
}

tasks.processResources {
    inputs.property("version", project.version)
    inputs.property("minecraft_version", minecraftVersion)
    inputs.property("loader_version", fabricLoaderVersion)

    filesMatching("fabric.mod.json") {
        expand(
            mapOf(
                "version" to project.version,
                "minecraft_version" to minecraftVersion,
                "loader_version" to fabricLoaderVersion,
            ),
        )
    }
}

spotless {
    java {
        target("src/**/*.java")
        eclipse().configFile(rootProject.file("config/eclipse/enchantpeek-style.xml"))
        trimTrailingWhitespace()
        endWithNewline()
    }

    kotlinGradle {
        target("*.gradle.kts")
        ktlint()
        trimTrailingWhitespace()
        endWithNewline()
    }

    format("misc") {
        target("*.md", ".gitignore", ".editorconfig", ".vscode/*.json")
        trimTrailingWhitespace()
        endWithNewline()
    }
}

checkstyle {
    toolVersion = "10.21.4"
    configFile = rootProject.file("config/checkstyle/checkstyle.xml")
    isShowViolations = true
}

tasks.withType<Checkstyle>().configureEach {
    reports {
        xml.required.set(false)
        html.required.set(true)
    }
}

modrinth {
    token.set(providers.environmentVariable("MODRINTH_TOKEN"))
    projectId.set(providers.gradleProperty("modrinthProjectId").orElse(providers.environmentVariable("MODRINTH_PROJECT_ID")))
    versionNumber.set(project.version.toString())
    versionName.set("EnchantPeek $modVersion for Minecraft $minecraftVersion")
    versionType.set("release")
    uploadFile.set(tasks.named("remapJar"))
    gameVersions.add(minecraftVersion)
    loaders.add("fabric")
    changelog.set(providers.environmentVariable("MODRINTH_CHANGELOG").orElse("No changelog was specified."))
    debugMode.set(providers.gradleProperty("modrinthDebug").map(String::toBoolean).orElse(false))

    dependencies {
        required.project("fabric-api")
    }
}

publishing {
    publications {
        create<MavenPublication>("mavenJava") {
            artifactId = archivesBaseName
            from(components["java"])
        }
    }
}
