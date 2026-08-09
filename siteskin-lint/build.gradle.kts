plugins {
    alias(libs.plugins.kotlin.jvm)
    application
}

// The tool a site owner runs before publishing a manifest. It wraps the same
// validator the browser uses, so "passes lint" means "the browser will activate".
// If these two ever diverge, the spec has stopped being a contract.

kotlin {
    jvmToolchain(25)

    compilerOptions {
        jvmTarget.set(org.jetbrains.kotlin.gradle.dsl.JvmTarget.JVM_21)
    }
}

java {
    sourceCompatibility = JavaVersion.VERSION_21
    targetCompatibility = JavaVersion.VERSION_21
}

application {
    mainClass.set("dev.siteskin.lint.MainKt")
    applicationName = "siteskin-lint"
}

dependencies {
    implementation(project(":siteskin-core"))
    implementation(libs.okhttp)

    testImplementation(libs.junit)
    testImplementation(libs.kotlinx.serialization.json)
    testImplementation(libs.okhttp.mockwebserver)
}

tasks.named<Test>("test") {
    val specDir = rootProject.layout.projectDirectory.dir("spec")
    inputs.dir(specDir).withPropertyName("specCorpus")
    systemProperty("siteskin.spec.dir", specDir.asFile.absolutePath)
}
