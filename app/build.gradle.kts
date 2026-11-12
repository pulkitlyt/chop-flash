plugins {
    id("com.android.application")
    id("org.jetbrains.kotlin.android")
}

// CI passes these in; local builds fall back to the debug key.
val releaseKeystore = System.getenv("KEYSTORE_PATH")?.let { file(it) }?.takeIf { it.exists() && it.length() > 0 }

android {
    namespace = "com.example.chopflash"
    compileSdk = 35

    defaultConfig {
        applicationId = "com.example.chopflash"
        minSdk = 26
        targetSdk = 35
        versionCode = System.getenv("VERSION_CODE")?.toIntOrNull() ?: 1
        versionName = System.getenv("VERSION_NAME") ?: "1.0"
    }

    signingConfigs {
        if (releaseKeystore != null) {
            create("release") {
                storeFile = releaseKeystore
                storePassword = System.getenv("KEYSTORE_PASSWORD")
                keyAlias = System.getenv("KEY_ALIAS")
                keyPassword = System.getenv("KEY_PASSWORD")
            }
        }
    }

    buildTypes {
        release {
            isMinifyEnabled = false
            signingConfig = signingConfigs.findByName("release") ?: signingConfigs.getByName("debug")
        }
    }

    compileOptions {
        sourceCompatibility = JavaVersion.VERSION_17
        targetCompatibility = JavaVersion.VERSION_17
    }
    kotlinOptions {
        jvmTarget = "17"
    }
}
