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
    // Adopt formatting incrementally from the last pre-lint revision.
    ratchetFrom("628a7332219f24631d0cbfd5181df224a99e31ed")
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
