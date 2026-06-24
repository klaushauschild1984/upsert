plugins {
    id("kompute.kotlin-conventions")
}

dependencies {
    api(project(":parser"))
    implementation(libs.kotlin.csv)
}
