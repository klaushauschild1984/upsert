plugins {
    `kotlin-dsl`
}

repositories {
    gradlePluginPortal()
}

dependencies {
    implementation(files(libs.javaClass.superclass.protectionDomain.codeSource.location))
    val catalog = versionCatalogs.named("libs")
    fun version(alias: String) = catalog.findVersion(alias).get().requiredVersion
    implementation("org.jetbrains.kotlin:kotlin-gradle-plugin:${version("kotlin")}")
    implementation("com.saveourtool.diktat:diktat-gradle-plugin:${version("diktat")}")
    implementation("io.gitlab.arturbosch.detekt:detekt-gradle-plugin:${version("detekt")}")
}
