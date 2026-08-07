plugins {
    alias(libs.plugins.android.application) apply false
    alias(libs.plugins.kotlin.jvm) apply false
    alias(libs.plugins.kotlin.compose) apply false
    alias(libs.plugins.kotlin.serialization) apply false
    alias(libs.plugins.detekt)
}

// Detekt is configured for every module here rather than per-module, so adding a
// module cannot accidentally leave it outside the complexity gate.
subprojects {
    apply(plugin = "io.gitlab.arturbosch.detekt")

    detekt {
        config.setFrom(files("$rootDir/config/detekt/detekt.yml"))
        buildUponDefaultConfig = true
        allRules = false
        basePath = rootDir.absolutePath
        baseline = file("$rootDir/config/detekt/baseline.xml")
    }
}
