plugins {
    id("com.android.application")
}

android {
    namespace = "com.dexkeybridge.app"
    compileSdk = 35

    defaultConfig {
        applicationId = "com.dexkeybridge.lite"
        minSdk = 29
        targetSdk = 35
        versionCode = 2
        versionName = "0.2.0-lite"
    }

    buildTypes {
        release {
            isMinifyEnabled = false
        }
    }

    compileOptions {
        sourceCompatibility = JavaVersion.VERSION_17
        targetCompatibility = JavaVersion.VERSION_17
    }
}
