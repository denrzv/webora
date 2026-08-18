import java.io.File
import java.util.Properties

plugins {
    alias(libs.plugins.android.application)
    alias(libs.plugins.kotlin.compose)
}

android {
    namespace = "app.webora.browser"
    compileSdk = 36

    defaultConfig {
        applicationId = "app.webora.browser"
        minSdk = 26
        targetSdk = 36
        versionCode = resolveVersionCode()
        versionName = "0.1.0"
        testInstrumentationRunner = "androidx.test.runner.AndroidJUnitRunner"
    }

    // Release signing resolves env → gradle property → local.properties. Implemented,
    // not merely documented — the reference repo shipped a SIGNING.md describing a
    // config its build file never contained, and both variants silently used the
    // debug key. Absent credentials leave signingConfig null, which fails the release
    // build loudly instead of producing a debug-signed artifact.
    signingConfigs {
        // The debug key is PINNED to a keystore in the repository rather than left to AGP's
        // per-machine `~/.android/debug.keystore`.
        //
        // Android refuses to install an APK over one signed with a different key
        // (INSTALL_FAILED_UPDATE_INCOMPATIBLE) whatever the versionCode, and AGP generates a fresh
        // random debug key on any machine that lacks one. Every CI runner starts without one, so
        // successive release builds were signed differently and could not upgrade each other —
        // which defeated the point of deriving versionCode from the commit count.
        //
        // This is not a secret. It is the well-known Android debug identity, its credentials are
        // published here in plain sight, and it cannot sign a release build: `release` resolves
        // separate credentials from the environment and leaves signingConfig null when they are
        // absent, failing the build loudly. `.gitignore` still excludes every other keystore.
        getByName("debug") {
            storeFile = rootProject.file("app/debug.keystore")
            storePassword = "android"
            keyAlias = "androiddebugkey"
            keyPassword = "android"
        }

        create("release") {
            val storeFilePath = resolveSigning("WEBORA_UPLOAD_STORE_FILE")
            if (storeFilePath != null) {
                storeFile = file(storeFilePath)
                storePassword = resolveSigning("WEBORA_UPLOAD_STORE_PASSWORD")
                keyAlias = resolveSigning("WEBORA_UPLOAD_KEY_ALIAS")
                keyPassword = resolveSigning("WEBORA_UPLOAD_KEY_PASSWORD")
            }
        }
    }

    buildTypes {
        debug {
            isMinifyEnabled = false
            applicationIdSuffix = ".debug"
        }
        release {
            isMinifyEnabled = true
            isShrinkResources = true
            proguardFiles(
                getDefaultProguardFile("proguard-android-optimize.txt"),
                "proguard-rules.pro",
            )
            signingConfig = signingConfigs.findByName("release")
                ?.takeIf { it.storeFile != null }
        }
        // Local-testing variant: release settings, but cleartext HTTP permitted so the
        // browser can be pointed at a local demo server. The relaxation lives in the
        // debugRelease source set only and cannot reach `release`.
        create("debugRelease") {
            initWith(getByName("release"))
            isMinifyEnabled = false
            isShrinkResources = false
            isDebuggable = true
            applicationIdSuffix = ".debugrelease"
            signingConfig = signingConfigs.getByName("debug")
        }
    }

    compileOptions {
        // Bytecode level is capped by D8, independent of the JDK running the build.
        sourceCompatibility = JavaVersion.VERSION_21
        targetCompatibility = JavaVersion.VERSION_21
        isCoreLibraryDesugaringEnabled = true
    }

    kotlin {
        jvmToolchain(25)
        compilerOptions {
            jvmTarget.set(org.jetbrains.kotlin.gradle.dsl.JvmTarget.JVM_21)
        }
    }

    buildFeatures {
        compose = true
        buildConfig = true
    }

    // `initWith(release)` copies build-type configuration, not source sets, so the debugRelease
    // variant would otherwise have no SiteSkinInspectorHost declaration at all and fail to compile.
    // Sharing the release stub is preferable to a third copy: two stubs that can drift is exactly
    // the failure the variant seam exists to prevent.
    sourceSets {
        getByName("debugRelease") {
            java.srcDir("src/release/java")
            kotlin.srcDir("src/release/java")
        }

        // CI-002's screenshot-evidence policy decides what the capture harness may dismiss, so it
        // needs a test that fails when it is widened. In `androidTest` alone that test could not run
        // here — managed checkouts have no /dev/kvm — and in `main` it would ship harness policy
        // inside the browser. Sharing one directory into both test source sets and into no variant
        // gives the JVM gate the decision and the device the same bytes, with no second copy to
        // drift. `kotlin.srcDir` as well as `java.srcDir`, for the same AGP 9 reason as above.
        listOf("test", "androidTest").forEach { testSourceSet ->
            getByName(testSourceSet) {
                java.srcDir("src/screenshotPolicy/java")
                kotlin.srcDir("src/screenshotPolicy/java")
            }
        }
    }

    testOptions {
        unitTests.isReturnDefaultValues = true
    }

    packaging {
        resources.excludes += "/META-INF/{AL2.0,LGPL2.1}"
    }
}

/**
 * Fails when the developer inspector's panel reaches a variant that must not contain it.
 *
 * Asserted against compiled output rather than against where a source file lives, because the
 * question is what ends up in the artifact. `./gradlew test` cannot answer it: AGP 9.1 creates
 * `testDebugUnitTest` and nothing else, so no JUnit run ever executes in a release variant.
 * Enabling host tests for `release` was tried and fails inside AGP with a NullPointerException at
 * `VariantManager.createTestComponents` — see `docs/research/DEVX-001.md`.
 *
 * [requiredClass] is not decoration. Without it, renaming or deleting the panel would make the
 * absence check pass while proving nothing, which is how a gate stops being a gate. Requiring the
 * stub's own class keeps the check anchored to a variant that really did compile the seam.
 */
abstract class AssertInspectorAbsent : DefaultTask() {
    @get:InputFiles
    @get:PathSensitive(PathSensitivity.RELATIVE)
    abstract val compiledClasses: ConfigurableFileCollection

    @get:Input abstract val variantName: Property<String>

    @get:Input abstract val forbiddenClassPrefix: Property<String>

    @get:Input abstract val requiredClass: Property<String>

    @TaskAction
    fun verify() {
        val names = compiledClasses.asFileTree.files.map { it.name }
        val forbidden = forbiddenClassPrefix.get()
        val offenders = names.filter { it.contains(forbidden) }.sorted()
        if (offenders.isNotEmpty()) {
            error(
                "The SiteSkin inspector must not compile into the ${variantName.get()} variant, " +
                    "but found:\n  " + offenders.joinToString("\n  ") +
                    "\nKeep the panel in app/src/debug/java; the release stub is app/src/release/java."
            )
        }
        val required = requiredClass.get()
        if (names.none { it == required }) {
            error(
                "Expected ${required} in the ${variantName.get()} variant's compiled output and " +
                    "found none. Either the variant seam was renamed — in which case this check was " +
                    "about to pass without proving anything — or it no longer compiles at all."
            )
        }
    }
}

val inspectorAbsenceChecks = mapOf(
    "release" to "compileReleaseKotlin",
    "debugRelease" to "compileDebugReleaseKotlin",
).map { (variant, compileTask) ->
    val suffix = variant.replaceFirstChar(Char::uppercaseChar)
    tasks.register<AssertInspectorAbsent>("assertInspectorAbsentFrom$suffix") {
        group = "verification"
        description = "Fails if the SiteSkin inspector panel compiles into the $variant variant."
        compiledClasses.from(tasks.named(compileTask))
        variantName.set(variant)
        forbiddenClassPrefix.set("SiteSkinInspectorPanel")
        requiredClass.set("SiteSkinInspectorHostKt.class")
    }
}

val assertInspectorAbsentFromReleaseVariants by tasks.registering {
    group = "verification"
    description = "Fails if the SiteSkin inspector panel compiles into any non-debug variant."
    dependsOn(inspectorAbsenceChecks)
}

tasks.named("check") { dependsOn(assertInspectorAbsentFromReleaseVariants) }

tasks.withType<Test>().configureEach {
    // BrowserSurfaceConventionsTest reads the Compose sources to enforce conventions no single
    // call site owns. Declared as inputs so editing a composable reruns the scan instead of
    // hitting an up-to-date check, mirroring how :siteskin-core wires the conformance corpus.
    //
    // Every variant source root that can declare a composable is listed. A debug-only screen is
    // still browser-owned UI, and leaving it outside the accessibility gate would make the source
    // set an escape hatch from the rule the gate exists to enforce. `debugRelease` compiles
    // `src/release/java`, so these three roots cover all four variants.
    val composeSourceRoots = listOf("src/main/java", "src/debug/java", "src/release/java")
        .map(layout.projectDirectory::dir)
    composeSourceRoots.forEach { root ->
        inputs.dir(root).withPropertyName("appComposeSources-${root.asFile.parentFile.name}")
    }
    systemProperty(
        "webora.app.src",
        composeSourceRoots.joinToString(File.pathSeparator) { it.asFile.absolutePath },
    )

    // UX-002's icon contract is checked by reading the drawables, for the same reason the Compose
    // conventions are checked by reading the sources: the bound C6 puts on the icon set is a budget,
    // and a budget nothing counts is a suggestion. Declared as an input so adding a drawable reruns
    // the scan rather than hitting an up-to-date check.
    val resourceRoot = layout.projectDirectory.dir("src/main/res")
    inputs.dir(resourceRoot).withPropertyName("appResources")
    systemProperty("webora.app.res", resourceRoot.asFile.absolutePath)

    // ExpressiveBloomJourneyContractTest reads the hosted journey's sources — the frame inventory,
    // the per-frame checks, the showcase and smoke markers. `./gradlew detekt` does not analyse
    // `androidTest` and no JUnit run compiles it, so this contract file is the *only* gate standing
    // between an edited hosted journey and a silently reduced one.
    //
    // It was invisible to Gradle. `UX-024`'s review dropped a CI-008 prerequisite from the showcase
    // and watched `:app:testDebugUnitTest` finish green with exit 0 and no test XML at all, because
    // nothing here declared the files the test reads; `--rerun-tasks` failed it immediately. The
    // hole survived several tickets only because every one of them edited this contract file in the
    // same run, which invalidates the task for an unrelated reason.
    //
    // SPEC-001 records the same lesson and the same fix for the conformance corpus: declare it as an
    // input, so editing a fixture reruns the tests instead of hitting an up-to-date check. The path
    // is passed as a property for the second half of that lesson — the working directory a test runs
    // in is not a contract, and a scan that silently fails to find its subject passes for the wrong
    // reason.
    val instrumentedRoot = layout.projectDirectory.dir("src/androidTest/java")
    inputs.dir(instrumentedRoot).withPropertyName("appInstrumentedSources")
    systemProperty("webora.app.androidTest", instrumentedRoot.asFile.absolutePath)
}

/**
 * `versionCode` is the number of commits reachable from `HEAD`.
 *
 * Android refuses to install an APK whose `versionCode` is not greater than the installed one, so a
 * constant `1` meant every demo build after the first had to be uninstalled before it could be
 * tried. Commit count fixes that and keeps two properties worth having:
 *
 * - **Monotonic** along a branch — every commit that lands raises it.
 * - **Reproducible** — the same commit yields the same number on any machine, so a local build and
 *   a CI build of one commit are the same artifact. Re-running a build on an unchanged commit
 *   therefore does *not* bump; use `-PweboraVersionCode=` to force one.
 *
 * `providers.exec` rather than a bare `exec {}` because this repository builds with the
 * configuration cache: a provider is recorded as an input, while running a process directly at
 * configuration time is not cacheable.
 *
 * **A shallow clone counts 1 commit.** `actions/checkout` defaults to `fetch-depth: 1`, so any
 * workflow that publishes an APK must set `fetch-depth: 0` or every release ships `versionCode` 1
 * and the upgrade path silently breaks again.
 */
fun resolveVersionCode(): Int {
    providers.gradleProperty("weboraVersionCode").orNull?.toIntOrNull()?.let { return it }

    val counted = runCatching {
        providers.exec {
            commandLine("git", "rev-list", "--count", "HEAD")
        }.standardOutput.asText.get().trim().toIntOrNull()
    }.getOrNull()

    // No git, no history, or a source archive: fall back rather than fail the build. A local build
    // outside a checkout is not worth breaking over, and the release workflow is where the number
    // has to be right.
    return counted?.takeIf { it > 0 } ?: 1
}

fun resolveSigning(key: String): String? =
    System.getenv(key)
        ?: providers.gradleProperty(key).orNull
        // `java` is the JavaPluginExtension accessor inside a build script, so the
        // `java.util` package name is shadowed — Properties must be imported.
        ?: runCatching {
            Properties().apply {
                rootProject.file("local.properties").inputStream().use { load(it) }
            }.getProperty(key)
        }.getOrNull()

dependencies {
    implementation(project(":siteskin-core"))

    implementation(libs.androidx.core.ktx)
    implementation(libs.androidx.lifecycle.runtime.ktx)
    implementation(libs.androidx.activity.compose)
    implementation(platform(libs.androidx.compose.bom))
    implementation(libs.androidx.compose.ui)
    implementation(libs.androidx.compose.ui.graphics)
    implementation(libs.androidx.compose.ui.tooling.preview)
    implementation(libs.androidx.compose.material3)
    implementation(libs.androidx.datastore.preferences)
    implementation(libs.androidx.webkit)
    implementation(libs.androidx.browser)
    implementation(libs.kotlinx.coroutines.android)
    implementation(libs.coil.compose)
    implementation(libs.okhttp)

    coreLibraryDesugaring(libs.desugar.jdk.libs)

    testImplementation(libs.junit)
    testImplementation(libs.mockk)
    testImplementation(libs.okhttp.mockwebserver)
    testImplementation(libs.okhttp.tls)
    testImplementation(libs.kotlinx.coroutines.test)

    androidTestImplementation(platform(libs.androidx.compose.bom))
    androidTestImplementation(libs.androidx.junit)
    androidTestImplementation(libs.androidx.espresso.core)
    androidTestImplementation(libs.androidx.uiautomator)
    androidTestImplementation(libs.androidx.compose.ui.test.junit4)

    debugImplementation(libs.androidx.compose.ui.tooling)
    debugImplementation(libs.androidx.compose.ui.test.manifest)
    debugImplementation(libs.okhttp.logging.interceptor)
}
