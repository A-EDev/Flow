import com.diffplug.spotless.LineEnding

// Top-level build file
plugins {
    id("com.android.application") version "8.7.2" apply false
    id("com.android.library") version "8.7.2" apply false
    id("org.jetbrains.kotlin.android") version "1.9.22" apply false
    id("com.google.dagger.hilt.android") version "2.51.1" apply false
    id("org.jetbrains.kotlin.plugin.serialization") version "1.9.22" apply false
    id("com.google.devtools.ksp") version "1.9.22-1.0.17" apply false
    id("com.android.test") version "8.7.2" apply false
    id("androidx.baselineprofile") version "1.4.1" apply false
    id("com.diffplug.spotless") version "8.8.0"
}

spotless {
    // Adopt formatting incrementally from the main commit that introduced linting.
    ratchetFrom("52c4928e5af05141080f46f6c1e41cbf9c457023")
    lineEndings = LineEnding.UNIX

    kotlin {
        target(
            "app/src/**/*.kt",
            "baselineprofile/src/**/*.kt",
        )
        targetExclude(
            "**/build/**",
            "**/generated/**",
        )
        ktlint("1.8.0")
    }

    kotlinGradle {
        target(
            "*.gradle.kts",
            "app/*.gradle.kts",
            "baselineprofile/*.gradle.kts",
        )
        targetExclude(
            "**/build/**",
        )
        ktlint("1.8.0")
    }
}

tasks.register("ktlintCheck") {
    group = "verification"
    description = "Checks Kotlin formatting with ktlint."
    dependsOn("spotlessCheck")
}

tasks.register("ktlintFormat") {
    group = "formatting"
    description = "Formats changed Kotlin files with ktlint."
    dependsOn("spotlessApply")
}
