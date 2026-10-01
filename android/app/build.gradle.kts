plugins {
    id("com.android.application")
    kotlin("android")
    kotlin("plugin.serialization")
}

android {
    namespace = "com.lanboardgame.host"
    compileSdk = 35

    defaultConfig {
        applicationId = "com.lanboardgame.host"
        minSdk = 26
        targetSdk = 35
        versionCode = 9
        versionName = "0.6.0"
    }

    buildFeatures { buildConfig = false }

    dependenciesInfo {
        includeInApk = false
        includeInBundle = false
    }

    buildTypes {
        getByName("release") {
            isMinifyEnabled = true
            isShrinkResources = true
            proguardFiles(
                getDefaultProguardFile("proguard-android-optimize.txt"),
                "proguard-rules.pro",
            )
        }
    }

    compileOptions {
        sourceCompatibility = JavaVersion.VERSION_17
        targetCompatibility = JavaVersion.VERSION_17
    }

    kotlinOptions { jvmTarget = "17" }

    packaging.resources.excludes += setOf(
        "META-INF/AL2.0",
        "META-INF/LGPL2.1",
        "META-INF/kotlin-project-structure-metadata.json",
        "commonMain/**",
    )
}

dependencies {
    implementation(project(":server-core"))
    implementation("org.nanohttpd:nanohttpd-websocket:2.3.1")
    implementation("com.google.zxing:core:3.5.3")
    implementation("org.jetbrains.kotlinx:kotlinx-serialization-json:1.8.0")
    testImplementation("junit:junit:4.13.2")
}
