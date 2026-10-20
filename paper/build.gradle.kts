plugins {
    `paper-plugin`
    `maven-publish`
}

project.group = "${rootProject.group}.paper"

repositories {
    maven("https://maven.fancyspaces.net/fancyinnovations/snapshots/")

    maven("https://repo.fancyinnovations.com/releases/")

    maven("https://repo.momirealms.net/releases/")

    maven("https://repo.hibiscusmc.com/releases/")

    maven("https://repo.nexomc.com/releases/")

    maven("https://repo.oraxen.com/releases/")
}

dependencies {
    implementation(project(":common"))

    implementation(libs.triumph.cmds)

    implementation(libs.fusion.paper)

    implementation(libs.metrics)

    compileOnly(libs.bundles.holograms)
    compileOnly(libs.bettermodel)
    compileOnly(libs.bundles.shared)
    compileOnly(libs.bundles.crates)
    compileOnly(libs.bundles.cmi)
}

tasks {
    build {
        dependsOn(shadowJar)
    }

    shadowJar {
        listOf(
            "com.ryderbelserion.fusion",
            "io.leangen.geantyref",
            "dev.triumphteam.cmd",
            "com.zaxxer.hikari",
            "org.spongepowered",
            "com.google.gson",
            "org.jspecify",
            "org.bstats"
        ).forEach {
            relocate(it, "libs.$it")
        }
    }

    runPaper.folia.registerTask()

    runServer {
        jvmArgs("-Dnet.kyori.ansi.colorLevel=truecolor")
        jvmArgs("-Dcom.mojang.eula.agree=true")

        defaultCharacterEncoding = Charsets.UTF_8.name()

        minecraftVersion(libs.versions.minecraft.get())
    }
}
// DropMC: the survival modules compile against the shaded jar, the same classes the server runs, so the relocated
// libraries match. Published to GitHub Packages by .github/workflows/dropmc-publish.yml.
publishing {
    repositories {
        maven("https://maven.pkg.github.com/DropMC/CrazyCrates") {
            name = "GitHubPackages"

            credentials {
                username = System.getenv("GITHUB_ACTOR")
                password = System.getenv("GITHUB_TOKEN")
            }
        }
    }

    publications {
        create<MavenPublication>("dropmc") {
            groupId = "gg.dropmc.crazycrates"
            artifactId = "crazycrates-paper"
            version = providers.gradleProperty("dropmcVersion").getOrElse("dropmc-SNAPSHOT")

            artifact(tasks.shadowJar) {
                classifier = null
            }
        }
    }
}

// jar and shadowJar both write paper.jar, so the publish waits for both to be done with it.
tasks.withType<AbstractPublishToMaven>().configureEach {
    dependsOn(tasks.jar, tasks.shadowJar)
}
