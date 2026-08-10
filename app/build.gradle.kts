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
        versionCode = 1
        versionName = "0.1.0"
        testInstrumentationRunner = "androidx.test.runner.AndroidJUnitRunner"
    }

    // Release signing resolves env → gradle property → local.properties. Implemented,
    // not merely documented — the reference repo shipped a SIGNING.md describing a
    // config its build file never contained, and both variants silently used the
    // debug key. Absent credentials leave signingConfig null, which fails the release
    // build loudly instead of producing a debug-signed artifact.
    signingConfigs {
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
    }

    testOptions {
        unitTests.isReturnDefaultValues = true
    }

    packaging {
        resources.excludes += "/META-INF/{AL2.0,LGPL2.1}"
    }
}

tasks.withType<Test>().configureEach {
    // BrowserSurfaceConventionsTest reads the Compose sources to enforce conventions no single
    // call site owns. Declared as an input so editing a composable reruns the scan instead of
    // hitting an up-to-date check, mirroring how :siteskin-core wires the conformance corpus.
    val appSource = layout.projectDirectory.dir("src/main/java")
    inputs.dir(appSource).withPropertyName("appComposeSources")
    systemProperty("webora.app.src", appSource.asFile.absolutePath)
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
    androidTestImplementation(libs.androidx.compose.ui.test.junit4)

    debugImplementation(libs.androidx.compose.ui.tooling)
    debugImplementation(libs.androidx.compose.ui.test.manifest)
    debugImplementation(libs.okhttp.logging.interceptor)
}
