plugins {
    alias(libs.plugins.kotlin.jvm)
    application
}

// Composes the canonical screenshot frames into one labelled contact sheet.
//
// A Gradle module rather than a step in scripts/android-screenshot-ci.sh, because a
// step that only ever executes on a GitHub runner is verified by nothing: `./gradlew
// test` picks this module up with no extra wiring, so scripts/pre-commit-check.sh and
// CI both fail on a broken composer. The same reasoning put ScreenEvidencePolicy in a
// shared source set instead of androidTest, which the gate never compiles at all.
//
// It takes no third-party dependency. javax.imageio ships a PNG reader and writer as
// required standard plugins, and java.awt draws headless, so the image work needs
// nothing the JDK toolchain does not already provide.

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
    mainClass.set("app.webora.evidence.MainKt")
    applicationName = "evidence-sheet"
}

dependencies {
    testImplementation(libs.junit)
}
