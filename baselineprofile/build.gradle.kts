import org.jetbrains.kotlin.gradle.dsl.JvmTarget

plugins {
    id("com.android.test")
    id("androidx.baselineprofile")
}

android {
    namespace = "io.github.aedev.flow.baselineprofile"
    compileSdk = 37

    compileOptions {
        sourceCompatibility = JavaVersion.VERSION_17
        targetCompatibility = JavaVersion.VERSION_17
    }

    defaultConfig {
        // Baseline profile generation requires API 28+.
        minSdk = 28
        targetSdk = 36

        testInstrumentationRunner = "androidx.test.runner.AndroidJUnitRunner"

        // The app declares a "version" flavor dimension (github/foss); the generator only
        // needs one of them, and github is the default flavor.
        missingDimensionStrategy("version", "github")
    }

    targetProjectPath = ":app"

    testOptions.managedDevices.localDevices {
        // systemImageSource must be "aosp": the generator needs root, which the Google Play
        // images do not grant.
        create("pixel6Api34") {
            device = "Pixel 6"
            apiLevel = 34
            systemImageSource = "aosp"
        }
    }
}

kotlin {
    compilerOptions {
        jvmTarget = JvmTarget.JVM_17
    }
}

baselineProfile {
    // Generation is driven by UiAutomator gestures, which need the INJECT_EVENTS permission.
    // On MIUI/HyperOS that requires Developer options > "USB debugging (Security settings)".
    //
    // Default: the connected device (fast, no emulator boot).
    // Pass -PbaselineProfileEmulator=true for the managed Pixel 6 instead — needed on CI, or on
    // any machine whose attached phone withholds INJECT_EVENTS.
    val useEmulator = project.findProperty("baselineProfileEmulator") == "true"
    if (useEmulator) {
        managedDevices += "pixel6Api34"
    }
    useConnectedDevices = !useEmulator
}

dependencies {
    implementation(libs.androidx.test.ext.junit)
    implementation(libs.androidx.espresso.core)
    implementation(libs.androidx.uiautomator)
    implementation(libs.androidx.benchmark.macro.junit4)
}

androidComponents {
    onVariants { v ->
        v.instrumentationRunnerArguments.put(
            "targetAppId",
            v.testedApks.map { artifacts ->
                v.artifacts
                    .getBuiltArtifactsLoader()
                    .load(artifacts)
                    ?.applicationId
            },
        )
    }
}
