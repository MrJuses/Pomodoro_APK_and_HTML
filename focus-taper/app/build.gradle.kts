plugins {
    id("com.android.application")
    id("org.jetbrains.kotlin.android")
}

// Optional release signing: set these env vars (CI secrets or your shell) to sign with your own key.
// Without them the release APK is signed with the debug key, which still installs fine for sideloading.
val ksFile: String? = System.getenv("KEYSTORE_FILE")

android {
    namespace = "dk.decpt.focustaper"
    compileSdk = 34

    defaultConfig {
        applicationId = "dk.decpt.focustaper"
        minSdk = 29
        targetSdk = 34
        versionCode = System.getenv("GITHUB_RUN_NUMBER")?.toIntOrNull() ?: 1
        versionName = "1.0.$versionCode"
    }

    signingConfigs {
        if (ksFile != null) {
            create("release") {
                storeFile = file(ksFile)
                storePassword = System.getenv("KEYSTORE_PASSWORD")
                keyAlias = System.getenv("KEY_ALIAS")
                keyPassword = System.getenv("KEY_PASSWORD")
            }
        }
    }

    buildTypes {
        release {
            isMinifyEnabled = false
            signingConfig = signingConfigs.getByName(if (ksFile != null) "release" else "debug")
        }
    }

    compileOptions {
        sourceCompatibility = JavaVersion.VERSION_17
        targetCompatibility = JavaVersion.VERSION_17
    }
    kotlinOptions { jvmTarget = "17" }

    lint {
        checkReleaseBuilds = false
        abortOnError = false
    }
}

dependencies {
    // No AndroidX: the app uses only framework APIs, so there is nothing to keep in sync.
    testImplementation("junit:junit:4.13.2")
}
