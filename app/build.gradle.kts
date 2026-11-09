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
        versionCode = 14
        versionName = "1.4.0"
    }

    signingConfigs {
        create("stableDebug") {
            storeFile = rootProject.file(".signing/dexkeybridge.keystore")
            storePassword = "dexkeybridge123"
            keyAlias = "dexkeybridge"
            keyPassword = "dexkeybridge123"
        }
    }

    buildTypes {
        debug {
            signingConfig = signingConfigs.getByName("stableDebug")
        }
        release {
            isMinifyEnabled = false
        }
    }

    compileOptions {
        sourceCompatibility = JavaVersion.VERSION_17
        targetCompatibility = JavaVersion.VERSION_17
    }
}
