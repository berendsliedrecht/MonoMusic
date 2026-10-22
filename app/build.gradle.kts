plugins {
    id("com.android.application")
    kotlin("android")
    id("org.jetbrains.kotlin.plugin.compose")
}

android {
    namespace = "com.monoapps.monomusic"
    compileSdk = 35

    defaultConfig {
        applicationId = "com.monoapps.monomusic"
        minSdk = 29
        targetSdk = 35
        versionCode = 25
        versionName = "3.0.0-beta.6"

        testInstrumentationRunner = "androidx.test.runner.AndroidJUnitRunner"
        vectorDrawables {
            useSupportLibrary = true
        }
    }

    buildTypes {
        release {
            // R8 matters on this device: it strips the unused bulk of
            // material-icons-extended and friends, shrinking the APK and
            // the dex the slow SoC has to load.
            isMinifyEnabled = true
            isShrinkResources = true
            // Debug-signed so the release APK is directly sideloadable
            signingConfig = signingConfigs.getByName("debug")
            proguardFiles(getDefaultProguardFile("proguard-android-optimize.txt"), "proguard-rules.pro")
        }
    }

    compileOptions {
        sourceCompatibility = JavaVersion.VERSION_1_8
        targetCompatibility = JavaVersion.VERSION_1_8
    }

    kotlinOptions {
        jvmTarget = "1.8"
    }
    // Compress native libs: AGP 8.2 does not 16 KB-align uncompressed .so
    // files, which makes newer Android builds show a compatibility dialog
    // on debuggable apps. Compressed libs are exempt from the check.
    packaging { jniLibs { useLegacyPackaging = true } }

    buildFeatures {
        compose = true
    }

    packagingOptions {
        resources {
            excludes += "/META-INF/{AL2.0,LGPL2.1}"
        }
    }
}

dependencies {
    implementation("androidx.core:core-ktx:1.12.0")
    implementation("androidx.lifecycle:lifecycle-runtime-ktx:2.6.2")
    implementation("androidx.activity:activity-compose:1.8.0")
    implementation("androidx.lifecycle:lifecycle-viewmodel-compose:2.6.2")

    implementation("com.squareup.okhttp3:okhttp:4.12.0")

    val composeBom = platform("androidx.compose:compose-bom:2024.06.00")
    implementation(composeBom)
    implementation("androidx.compose.ui:ui")
    implementation("androidx.compose.ui:ui-graphics")
    implementation("androidx.compose.material3:material3:1.2.1")
    implementation("androidx.compose.material:material-icons-extended:1.7.0")
    implementation("androidx.navigation:navigation-compose:2.7.4")

    // Mudita Mindful Design Framework
    implementation("com.mudita:MMD:1.0.0")

    // Media3 (ExoPlayer) for streaming playback
    implementation("androidx.media3:media3-exoplayer:1.9.4")
    implementation("androidx.media3:media3-session:1.9.4")
    implementation("androidx.media3:media3-datasource-okhttp:1.9.4")

    testImplementation("junit:junit:4.13.2")
    // Real org.json for JVM unit tests (the Android SDK one is a stub there)
    testImplementation("org.json:json:20240303")
}
