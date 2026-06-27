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

val jacocoRootReport by tasks.registering(JacocoReport::class) {
    subprojects
        .filter { it.name != "integration-test" }
        .forEach { subproject ->
            dependsOn(subproject.tasks.withType<Test>())
            sourceDirectories.from(subproject.files("src/main/kotlin"))
            classDirectories.from(
                subproject.fileTree(subproject.layout.buildDirectory.dir("classes/kotlin/main"))
            )
            executionData.from(
                subproject.fileTree(subproject.layout.buildDirectory) { include("jacoco/*.exec") }
            )
        }
    reports {
        html.required = true
        xml.required = true
        csv.required = true
    }
}

