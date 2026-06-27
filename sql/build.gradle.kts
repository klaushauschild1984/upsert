plugins {
    id("kompute.kotlin-conventions")
}

dependencies {
    api(project(":model"))
    api(libs.micrometer.observation)
    implementation(libs.postgresql)
}
