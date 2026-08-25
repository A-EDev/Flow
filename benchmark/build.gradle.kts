import org.jetbrains.kotlin.gradle.dsl.JvmTarget

plugins {
    id("com.android.test")
    id("androidx.baselineprofile")
}

android {
    namespace = "io.github.aedev.flow.benchmark"
    compileSdk = 37

    compileOptions {
        sourceCompatibility = JavaVersion.VERSION_17
        targetCompatibility = JavaVersion.VERSION_17
    }

    defaultConfig {
        minSdk = 28
        targetSdk = 36

        testInstrumentationRunner = "androidx.test.runner.AndroidJUnitRunner"
        missingDimensionStrategy("version", "github")
    }

    targetProjectPath = ":app"

    testOptions.managedDevices.localDevices {
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
