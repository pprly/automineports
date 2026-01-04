plugins {
    java
}

group = "com.example"
version = "1.0.0-ALPHA"
description = "Advanced boat transportation system with auto-pathfinding"

repositories {
    mavenCentral()
    maven {
        name = "papermc"
        url = uri("https://repo.papermc.io/repository/maven-public/")
    }
}

dependencies {
    compileOnly("io.papermc.paper:paper-api:1.21.10-R0.1-SNAPSHOT")
}

java {
    toolchain.languageVersion.set(JavaLanguageVersion.of(21))
}

tasks.processResources {
    filesMatching("plugin.yml") {
        expand("version" to version)
    }
}

tasks.jar {
    manifest {
        attributes["paperweight-mappings-namespace"] = "mojang"
    }
}

// Auto-copy to test server after build
tasks.register<Copy>("copyToTestServer") {
    dependsOn("jar")
    from(tasks.jar)
    into("test-server/plugins")
}

tasks.named("build") {
    finalizedBy("copyToTestServer")
}
