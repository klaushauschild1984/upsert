import org.gradle.accessors.dm.LibrariesForLibs

plugins {
    kotlin("jvm")
    id("com.saveourtool.diktat")
    id("io.gitlab.arturbosch.detekt")
    id("jacoco")
}

val libs = the<LibrariesForLibs>()

group = "de.hauschild.upsert"

kotlin {
    jvmToolchain(21)
}

repositories {
    mavenCentral()
}

dependencies {
    testImplementation(kotlin("test"))
    testImplementation("org.junit.jupiter:junit-jupiter-params")
    testRuntimeOnly(libs.logback)
}

sourceSets {
    test {
        resources {
            srcDir(rootProject.file("src/test/resources"))
        }
    }
}

tasks.jar {
    manifest {
        attributes["Implementation-Version"] = project.version
    }
}

tasks.test {
    useJUnitPlatform()
    testLogging {
        showStandardStreams = true
    }
    finalizedBy(tasks.jacocoTestReport)
}

tasks.jacocoTestReport {
    dependsOn(tasks.test)
    reports {
        html.required = true
        csv.required = true
    }
}

detekt {
    buildUponDefaultConfig = true
    config.setFrom(rootProject.file("detekt.yml"))
}

tasks.withType<io.gitlab.arturbosch.detekt.Detekt>().configureEach {
    reports {
        html.required = true
        sarif.required = true
    }
}

diktat {
    reporters {
        sarif()
    }
    inputs {
        include("src/**/*.kt")
        exclude("**/generated/**")
    }
}

tasks.withType<com.saveourtool.diktat.plugin.gradle.tasks.DiktatCheckTask> {
    outputs.upToDateWhen { false }
}
tasks.withType<com.saveourtool.diktat.plugin.gradle.tasks.DiktatFixTask> {
    outputs.upToDateWhen { false }
}

