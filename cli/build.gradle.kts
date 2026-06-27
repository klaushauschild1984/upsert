plugins {
    id("kompute.kotlin-conventions")
    alias(libs.plugins.shadow)
}

dependencies {
    implementation(project(":sql"))
    implementation(project(":parser:csv"))
    implementation(libs.clikt)
    implementation(libs.postgresql)
}

tasks.shadowJar {
    archiveClassifier = ""
    manifest {
        attributes["Main-Class"] = "de.hauschild.upsert.cli.MainKt"
    }
}
