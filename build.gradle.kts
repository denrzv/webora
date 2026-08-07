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

    // Detekt runs on its own embedded Kotlin compiler, whose --jvm-target ceiling is
    // lower than the build toolchain's. Left unset it inherits the toolchain's 25 and
    // fails with "Invalid value (25) passed to --jvm-target". Pin it to the module's
    // bytecode target instead — the third place the 21 from CLAUDE.md § Java version
    // has to be stated, and the one that is easiest to forget.
    tasks.withType<io.gitlab.arturbosch.detekt.Detekt>().configureEach {
        jvmTarget = "21"
    }
    tasks.withType<io.gitlab.arturbosch.detekt.DetektCreateBaselineTask>().configureEach {
        jvmTarget = "21"
    }
}
