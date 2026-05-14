@file:Suppress("UnstableApiUsage")

import org.jetbrains.kotlin.gradle.dsl.JvmTarget

plugins {
    id("fabric-loom") version "1.15-SNAPSHOT"
    kotlin("jvm") version "2.3.0"
    `maven-publish`
}

val mc = "1.21.11"
version = "1.2.1"
group = "com.autoleap"
base.archivesName = "trji"

repositories {
    mavenCentral()
    maven("https://maven.fabricmc.net/")
    maven("https://maven.kikugie.dev/snapshots")
    maven { url = uri("https://api.modrinth.com/maven") }
    maven("https://maven.parchmentmc.org/")
    maven("https://jitpack.io")
}

dependencies {
    minecraft("com.mojang:minecraft:$mc")
    mappings(loom.layered {
        officialMojangMappings()
        parchment("org.parchmentmc.data:parchment-1.21.11:2025.12.20")
    })

    modImplementation("net.fabricmc:fabric-loader:0.18.5")
    modImplementation("net.fabricmc.fabric-api:fabric-api:0.141.3+1.21.11")
    modImplementation("net.fabricmc:fabric-language-kotlin:1.13.10+kotlin.2.3.20")

    // Odin mod (prod build for 1.21.11)
    modImplementation("maven.modrinth:odin:CwOwZGVZ")

    // Command DSL
    modImplementation("com.github.stivais:Commodore:1.0.1")

    // Odin transitive deps required in dev env (Modrinth doesn't ship them)
    modImplementation("com.squareup.okhttp3:okhttp-jvm:5.2.1")
    modImplementation("com.squareup.okio:okio-jvm:3.16.1")
    modImplementation("org.lwjgl:lwjgl-nanovg:3.3.3")
}

loom {
    runConfigs.named("client") {
        isIdeConfigGenerated = true
        vmArgs("-Dmixin.debug.export=true")
    }
    runConfigs.named("server") {
        isIdeConfigGenerated = false
    }
}

tasks {
    processResources {
        inputs.property("version", project.version)
        filesMatching("fabric.mod.json") {
            expand(mapOf("version" to project.version))
        }
    }

    compileKotlin {
        compilerOptions {
            jvmTarget = JvmTarget.JVM_21
            freeCompilerArgs.add("-Xlambdas=class") // required for Commodore
        }
    }

    compileJava {
        sourceCompatibility = "21"
        targetCompatibility = "21"
        options.encoding = "UTF-8"
    }
}
