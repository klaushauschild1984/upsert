plugins {
    id("kompute.kotlin-conventions")
}

dependencies {
    testImplementation(project(":sql"))
    testImplementation(project(":parser:csv"))
    testImplementation(libs.postgresql)
    testImplementation(libs.testcontainers)
    testImplementation(libs.testcontainers.junit.jupiter)
    testImplementation(libs.testcontainers.postgresql)
}
