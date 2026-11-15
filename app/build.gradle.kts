import org.jetbrains.kotlin.gradle.dsl.JvmTarget

plugins {
    id("com.android.application")
    id("org.jetbrains.kotlin.android")
}

// The Play upload key never lives in the repository. CI writes it to a file from the
// repository's secrets and passes its location and passwords in these environment variables.
val uploadKey: String? = System.getenv("UPLOAD_KEYSTORE_FILE")?.takeIf { file(it).exists() }

android {
    namespace = "dev.screenbuddy"
    compileSdk = 36

    defaultConfig {
        applicationId = "dev.screenbuddy"
        minSdk = 29
        targetSdk = 36
        // Play needs a higher versionCode for every upload; CI passes its run number.
        versionCode = (findProperty("versionCode") as String?)?.toInt() ?: 3
        versionName = "1.0"
    }

    // Every build signs debug APKs with this one checked-in key. Without it each CI run makes a
    // new random key, and Android refuses to install a new APK over the previous one. It is the
    // standard debug key (password "android"), not a secret; Play releases need a separate key.
    signingConfigs {
        getByName("debug") {
            storeFile = file("debug.keystore")
            storePassword = "android"
            keyAlias = "androiddebugkey"
            keyPassword = "android"
        }
        if (uploadKey != null) {
            create("upload") {
                storeFile = file(uploadKey)
                storePassword = System.getenv("UPLOAD_KEYSTORE_PASSWORD")
                keyAlias = System.getenv("UPLOAD_KEY_ALIAS")
                keyPassword = System.getenv("UPLOAD_KEY_PASSWORD")
            }
        }
    }

    buildTypes {
        getByName("release") {
            isMinifyEnabled = true
            isShrinkResources = true
            proguardFiles(getDefaultProguardFile("proguard-android-optimize.txt"))
            // unsigned without the upload key; Play rejects unsigned bundles, so CI only uploads signed ones
            signingConfig = uploadKey?.let { signingConfigs.getByName("upload") }
        }
    }

    compileOptions {
        sourceCompatibility = JavaVersion.VERSION_17
        targetCompatibility = JavaVersion.VERSION_17
    }

    lint {
        abortOnError = false
    }
}

kotlin {
    compilerOptions {
        jvmTarget.set(JvmTarget.JVM_17)
    }
}

dependencies {
    implementation(project(":core"))
}
