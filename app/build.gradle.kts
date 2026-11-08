plugins {
    id("com.android.application")
}

android {
    namespace = "com.dexkeybridge.app"
    compileSdk = 35

    defaultConfig {
        applicationId = "com.dexkeybridge.diagnosticv2"
        minSdk = 29
        targetSdk = 35
        versionCode = 6
        versionName = "0.6.0"
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
