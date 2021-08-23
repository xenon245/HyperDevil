plugins {
    kotlin("jvm") version "1.5.10"
}

java {
    toolchain {
        languageVersion.set(JavaLanguageVersion.of(16))
    }
}

project.extra.set("packageName", name.replace("-", ""))
project.extra.set("pluginName", name.split('-').joinToString("") { it.capitalize() })

repositories {
    mavenCentral()
    maven("https://papermc.io/repo/repository/maven-public/")
}

dependencies {
    implementation(kotlin("stdlib"))
    compileOnly("io.papermc.paper:paper-api:1.17.1-R0.1-SNAPSHOT")
    implementation("io.github.monun:kommand-api:+")
    implementation("io.github.monun:tap-api:+")
}

tasks {
    processResources {
        filesMatching("**/*.yml") {
            expand(project.properties)
            expand(project.extra.properties)
        }
    }

    test {
        useJUnitPlatform()
    }

    create<Jar>("paperJar") {
        from(sourceSets["main"].output)
        archiveBaseName.set(project.extra.properties["pluginName"].toString())
        archiveVersion.set("") // For bukkit plugin update

        doLast {
            copy {
                from(archiveFile)
                val plugins = File(rootDir, ".debug/plugins/")
                into(if (File(plugins, archiveFileName.get()).exists()) File(plugins, "update") else plugins)
            }
        }
    }
}