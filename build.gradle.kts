plugins {
    id("java")
}

group = "com.bia10.herblore"
version = "1.0"

repositories {
    mavenLocal()
    maven {
        url = uri("https://repo.runelite.net")
        content {
            includeGroupByRegex("net\\.runelite.*")
        }
    }
    mavenCentral()
}

val apiVersion = "latest.release"

dependencies {
    compileOnly("net.runelite:client:$apiVersion")
    compileOnly("com.tonic:base-api:$apiVersion")
    compileOnly("com.tonic:api:$apiVersion")
}