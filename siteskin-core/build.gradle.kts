plugins {
    alias(libs.plugins.kotlin.jvm)
    alias(libs.plugins.kotlin.serialization)
}

// This module must never gain an Android dependency. Everything security-critical
// lives here — origin binding, scheme allow-listing, action resolution, contrast
// correction — and it is testable with plain JUnit, no emulator, no SDK.
//
// The rule is enforced, not just documented: `check` fails if anything androidx or
// com.android appears on the compile classpath, and CI additionally runs
// `:siteskin-core:test` with ANDROID_HOME unset.

kotlin {
    jvmToolchain(25)

    compilerOptions {
        // Bytecode level, NOT the toolchain. This module is dexed into the APK, so it
        // inherits :app's D8 ceiling even though it is a plain JVM library.
        // See docs/DEVELOPMENT_PLAN.md § "Java version — two different knobs".
        jvmTarget.set(org.jetbrains.kotlin.gradle.dsl.JvmTarget.JVM_21)
        freeCompilerArgs.add("-Xjvm-default=all")
    }
}

java {
    sourceCompatibility = JavaVersion.VERSION_21
    targetCompatibility = JavaVersion.VERSION_21
}

dependencies {
    implementation(libs.kotlinx.serialization.json)
    implementation(libs.kotlinx.coroutines.core)

    testImplementation(libs.junit)
    testImplementation(libs.kotlinx.coroutines.test)
}

val assertNoAndroidDependencies by tasks.registering {
    group = "verification"
    description = "Fails if an Android dependency leaks into the pure-JVM core module."

    val classpath = configurations.named("compileClasspath")
    doLast {
        val offenders = classpath.get()
            .resolvedConfiguration
            .resolvedArtifacts
            .map { it.moduleVersion.id }
            .filter { it.group.startsWith("androidx.") || it.group.startsWith("com.android") }
            .map { "${it.group}:${it.name}:${it.version}" }
            .distinct()

        if (offenders.isNotEmpty()) {
            error(
                "siteskin-core must stay Android-free, but found:\n  " +
                    offenders.joinToString("\n  ") +
                    "\nMove the Android-touching code to :app and keep an interface here."
            )
        }
    }
}

tasks.named("check") { dependsOn(assertNoAndroidDependencies) }
