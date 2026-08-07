plugins {
    alias(libs.plugins.kotlin.jvm)
    application
}

// The tool a site owner runs before publishing a manifest. It wraps the same
// validator the browser uses, so "passes lint" means "the browser will activate".
// If these two ever diverge, the spec has stopped being a contract.

kotlin {
    jvmToolchain(25)
}

application {
    mainClass.set("dev.siteskin.lint.MainKt")
    applicationName = "siteskin-lint"
}

dependencies {
    implementation(project(":siteskin-core"))
    implementation(libs.okhttp)

    testImplementation(libs.junit)
    testImplementation(libs.okhttp.mockwebserver)
}
