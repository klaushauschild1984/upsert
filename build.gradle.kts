plugins {
    base
    jacoco
    id("com.saveourtool.diktat")
    alias(libs.plugins.ben.manes.versions)
}

allprojects {
    version = "0.1.0-SNAPSHOT"
}

repositories {
    mavenCentral()
}

tasks.named<com.github.benmanes.gradle.versions.updates.DependencyUpdatesTask>("dependencyUpdates") {
    revision = "release"
    outputFormatter = "json,html"
    outputDir = layout.buildDirectory.dir("dependencyUpdates").get().asFile.path
    reportfileName = "report"
}

