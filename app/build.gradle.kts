plugins {
    id("com.android.application")
    id("org.jetbrains.kotlin.android")
    id("kotlin-kapt")
    id("com.google.dagger.hilt.android")
    id("org.jetbrains.kotlin.plugin.serialization")
}

android {
    namespace = "com.flow.youtube"
    compileSdk = 34

    defaultConfig {
        applicationId = "com.flow.youtube"
        minSdk = 21
        targetSdk = 34
        versionCode = 2
        versionName = "1.2.7"

        testInstrumentationRunner = "androidx.test.runner.AndroidJUnitRunner"
        vectorDrawables {
            useSupportLibrary = true
        }
        
        // Support all architectures for maximum device compatibility
        ndk {
            abiFilters.addAll(listOf("armeabi-v7a", "arm64-v8a", "x86", "x86_64"))
        }
        
        // Enable multidex for older devices
        multiDexEnabled = true
    }

    buildFeatures {
        buildConfig = true
        compose = true
    }

    signingConfigs {
        create("release") {
            storeFile = rootProject.file("release.keystore")
            storePassword = project.findProperty("storePassword")?.toString() ?: System.getenv("STORE_PASSWORD") ?: "your_store_password"
            keyAlias = project.findProperty("keyAlias")?.toString() ?: System.getenv("KEY_ALIAS") ?: "your_key_alias"
            keyPassword = project.findProperty("keyPassword")?.toString() ?: System.getenv("KEY_PASSWORD") ?: "your_key_password"
        }
    }

    buildTypes {
        debug {
            isDebuggable = true
            isMinifyEnabled = false
            isShrinkResources = false
        }
        release {
            isDebuggable = false
            // Follow NewPipe approach: minify but don't shrink resources
            isMinifyEnabled = true
            isShrinkResources = false // disabled for reproducible builds
            proguardFiles(
                getDefaultProguardFile("proguard-android.txt"),
                "proguard-rules.pro"
            )
            // Use release signing if configured, otherwise fallback to debug
            val releaseKeystore = signingConfigs.getByName("release").storeFile
            if (releaseKeystore?.exists() == true) {
                signingConfig = signingConfigs.getByName("release")
                println("Using RELEASE signing config with keystore: ${releaseKeystore.absolutePath}")
            } else {
                signingConfig = signingConfigs.getByName("debug")
                println("WARNING: Release keystore not found. Using DEBUG signing config for release build.")
            }
        }
    }

    compileOptions {
        sourceCompatibility = JavaVersion.VERSION_17
        targetCompatibility = JavaVersion.VERSION_17
        isCoreLibraryDesugaringEnabled = true  // Enable desugaring

    }

    kotlinOptions {
        jvmTarget = "17"
    }

    buildFeatures {
        compose = true
    }

    composeOptions {
        kotlinCompilerExtensionVersion = "1.5.3"
    }

    packaging {
        resources {
            excludes += "/META-INF/{AL2.0,LGPL2.1}"
        }
    }
}

dependencies {
    // Core Android
    implementation("androidx.core:core-ktx:1.12.0")
    implementation("androidx.lifecycle:lifecycle-runtime-ktx:2.6.2")
    implementation("androidx.activity:activity-compose:1.8.0")
    
    // APK Updater for GitHub releases
    implementation("com.github.supersu-man:apkupdater-library:v2.1.0")
    
    // Multidex support for older devices
    implementation("androidx.multidex:multidex:2.0.1")

    // Compose BOM
    implementation(platform("androidx.compose:compose-bom:2023.10.01"))
    implementation("androidx.compose.ui:ui")
    implementation("androidx.compose.ui:ui-graphics")
    implementation("androidx.compose.ui:ui-tooling-preview")
    implementation("androidx.compose.material3:material3")
    implementation("androidx.compose.material:material") // Add Material 2 for Swipeable
    implementation("androidx.compose.material:material-icons-extended")

    // Navigation
    implementation("androidx.navigation:navigation-compose:2.7.5")

    // ConstraintLayout Compose (MotionLayout)
    implementation("androidx.constraintlayout:constraintlayout-compose:1.0.1")

    // ViewModel
    implementation("androidx.lifecycle:lifecycle-viewmodel-compose:2.6.2")
    implementation("androidx.lifecycle:lifecycle-runtime-compose:2.6.2")

    // Coil for image loading
    implementation("io.coil-kt:coil-compose:2.5.0")

    // NewPipeExtractor - Official JitPack dependency (updated to latest version)
    implementation("com.github.TeamNewPipe.NewPipeExtractor:NewPipeExtractor:v0.24.8")
    
    // ViewModel
    implementation("androidx.lifecycle:lifecycle-viewmodel-compose:2.6.2")

    // Coroutines
    implementation("org.jetbrains.kotlinx:kotlinx-coroutines-android:1.7.3")

    // DataStore
    implementation("androidx.datastore:datastore-preferences:1.0.0")
    
    // Paging 3 for infinite scroll
    implementation("androidx.paging:paging-runtime-ktx:3.2.1")
    implementation("androidx.paging:paging-compose:3.2.1")

    // Gson for JSON serialization
    implementation("com.google.code.gson:gson:2.10.1")

    // Picasso for image loading (used for seekbar preview thumbnails)
    implementation("com.squareup.picasso:picasso:2.8")

    // HTML compatibility library
    implementation("androidx.core:core:1.12.0")

    // RxJava3 for reactive state management
    implementation("io.reactivex.rxjava3:rxjava:3.1.8")
    implementation("io.reactivex.rxjava3:rxandroid:3.0.2")

    // Media3 (latest stable) for video playback - corresponds to ExoPlayer 2.19.1 improvements
    implementation("androidx.media3:media3-exoplayer:1.4.1")
    implementation("androidx.media3:media3-ui:1.4.1")
    implementation("androidx.media3:media3-common:1.4.1")
    implementation("androidx.media3:media3-session:1.4.1")
    implementation("androidx.media3:media3-exoplayer-hls:1.4.1")
    implementation("androidx.media3:media3-exoplayer-dash:1.4.1")
    implementation("androidx.media3:media3-datasource:1.4.1")
    
    // Media Session support for notifications
    implementation("androidx.media:media:1.7.0")
    
    // WorkManager for background tasks (subscription checks, etc.)
    implementation("androidx.work:work-runtime-ktx:2.9.0")

    coreLibraryDesugaring("com.android.tools:desugar_jdk_libs_nio:2.0.4")
    
    // Hilt
    implementation("com.google.dagger:hilt-android:2.50")
    kapt("com.google.dagger:hilt-android-compiler:2.50")
    implementation("androidx.hilt:hilt-navigation-compose:1.0.0")

    // Room
    implementation("androidx.room:room-runtime:2.6.0")
    implementation("androidx.room:room-ktx:2.6.0")
    kapt("androidx.room:room-compiler:2.6.0")

    // Testing
    testImplementation("junit:junit:4.13.2")
    androidTestImplementation("androidx.test.ext:junit:1.1.5")
    androidTestImplementation("androidx.test.espresso:espresso-core:3.5.1")
    androidTestImplementation(platform("androidx.compose:compose-bom:2023.10.01"))
    androidTestImplementation("androidx.compose.ui:ui-test-junit4")
    debugImplementation("androidx.compose.ui:ui-tooling")
    debugImplementation("androidx.compose.ui:ui-test-manifest")

    // YouTubeDL-Android
    // implementation("com.github.yausername.youtubedl-android:library:0.14.0") // Dependency failing resolution
    // implementation("com.github.yausername.youtubedl-android:ffmpeg:v0.17.1") // Disabled
    
    // OkHttp
    implementation("com.squareup.okhttp3:okhttp:4.12.0")

    // Ktor Client for Innertube
    val ktorVersion = "2.3.12"
    implementation("io.ktor:ktor-client-core:$ktorVersion")
    implementation("io.ktor:ktor-client-okhttp:$ktorVersion")
    implementation("io.ktor:ktor-client-content-negotiation:$ktorVersion")
    implementation("io.ktor:ktor-serialization-kotlinx-json:$ktorVersion")
    implementation("io.ktor:ktor-client-encoding:$ktorVersion")
    
    // Brotli for compression
    implementation("org.brotli:dec:0.1.2") 

    // Kotlin Serialization
    implementation("org.jetbrains.kotlinx:kotlinx-serialization-json:1.6.3")
}

// Allow references to generated code
kapt {
    correctErrorTypes = true
}

hilt {
    enableTransformForLocalTests = false
}
