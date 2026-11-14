import org.jetbrains.kotlin.gradle.dsl.JvmTarget

plugins {
    id("com.android.application")
    id("org.jetbrains.kotlin.android")
}

android {
    namespace = "dev.screenbuddy"
    compileSdk = 35

    defaultConfig {
        applicationId = "dev.screenbuddy"
        minSdk = 29
        targetSdk = 35
        versionCode = 1
        versionName = "0.1"
    }

    // "full" uses an accessibility service so the cat can see where you tap; Play Protect blocks
    // installing it from a browser or file manager, so it has to be installed with adb.
    // "lite" uses a plain overlay and installs normally, but the cat cannot tell where you tap.
    flavorDimensions += "mode"
    productFlavors {
        create("full") { dimension = "mode" }
        create("lite") {
            dimension = "mode"
            applicationIdSuffix = ".lite"
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
