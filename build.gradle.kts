plugins {
    alias(libs.plugins.android.application) apply false
    alias(libs.plugins.kotlin.jvm) apply false
    alias(libs.plugins.kotlin.compose) apply false
    alias(libs.plugins.kotlin.serialization) apply false
    alias(libs.plugins.detekt)
    alias(libs.plugins.cyclonedx)
}

// Detekt is configured for every module here rather than per-module, so adding a
// module cannot accidentally leave it outside the complexity gate.
subprojects {
    apply(plugin = "dev.detekt")

    detekt {
        config.setFrom(files("$rootDir/config/detekt/detekt.yml"))
        buildUponDefaultConfig = true
        allRules = false
        basePath.set(rootDir)
        baseline = file("$rootDir/config/detekt/baseline.xml")
        // detekt 2.x removed maxIssues. Fail on every finding to preserve the
        // previous maxIssues=0 gate semantics across Error/Warning/Info severities.
        failOnSeverity = dev.detekt.gradle.extensions.FailOnSeverity.Info
    }

    // The project runs on JDK 25 but emits JVM 21 bytecode. Keep detekt's type
    // resolution aligned with the bytecode it analyses rather than the host JDK.
    tasks.withType<dev.detekt.gradle.Detekt>().configureEach {
        jvmTarget.set("21")
    }
    tasks.withType<dev.detekt.gradle.DetektCreateBaselineTask>().configureEach {
        jvmTarget.set("21")
    }
}
