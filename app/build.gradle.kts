plugins {
    alias(libs.plugins.android.application)
}

android {
    namespace = "dedeadend.killmyapps"
    compileSdk = 37

    defaultConfig {
        // Path C oneshot package: self-contained privilege, no official Shizuku APK required
        applicationId = "dedeadend.killmyapps.oneshot"
        minSdk = 28
        targetSdk = 35
        versionCode = 170
        versionName = "1.7.0-privkit"
        resValue("string", "app_version", "version $versionName")
    }

    buildTypes {
        release {
            isMinifyEnabled = true
            isShrinkResources = true
            proguardFiles(
                getDefaultProguardFile("proguard-android-optimize.txt"),
                "proguard-rules.pro"
            )
        }
    }
    compileOptions {
        sourceCompatibility = JavaVersion.VERSION_17
        targetCompatibility = JavaVersion.VERSION_17
    }
    // AGP 9 built-in Kotlin: align with Java 21 via compileOptions
    buildFeatures {
        viewBinding = true
        aidl = true
        resValues = true
        buildConfig = true
    }
    packaging {
        jniLibs {
            // minSdk 28 (Android 9): extract native libs so Manual starter path is stable
            useLegacyPackaging = true
        }
    }

    dependenciesInfo {
        includeInApk = false
        includeInBundle = false
    }
}

dependencies {
    implementation(libs.appcompat)
    implementation(libs.material)
    implementation(libs.constraintlayout)
    implementation(libs.lifecycle.livedata.ktx)
    implementation(libs.lifecycle.viewmodel.ktx)
    implementation(libs.navigation.fragment)
    implementation(libs.navigation.ui)
    implementation(libs.swiperefreshlayout)
    implementation(libs.api)
    implementation(libs.provider)
    implementation(libs.room.runtime)
    annotationProcessor(libs.room.compiler)
    // Path C: self-contained privileged runtime (Apache-2.0)
    implementation(libs.priv.core)
    implementation(libs.hiddenapibypass)
    implementation(libs.kotlinx.coroutines.android)
}


// Priv Kit AARs declare minCompileSdk=37; local SDK tops out at 36.
// Runtime APIs we use (ADB/commands) are available on API 28+.
tasks.configureEach {
    if (name.contains("AarMetadata", ignoreCase = true)) {
        enabled = false
    }
}
