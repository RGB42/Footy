plugins {
    id("com.android.application")
    id("org.jetbrains.kotlin.android")
}

android {
    namespace = "com.rgb42.footy"
    compileSdk = 35

    defaultConfig {
        applicationId = "com.rgb42.footy"
        minSdk = 26
        targetSdk = 35
        versionCode = 1
        versionName = "1.0"
    }

    signingConfigs {
        // Fester Schlüssel, damit Updates über die alte Version installiert
        // werden können, ohne die gespeicherten Schritte zu verlieren.
        create("footy") {
            storeFile = file("footy.keystore")
            storePassword = "footy123"
            keyAlias = "footy"
            keyPassword = "footy123"
        }
    }

    buildTypes {
        release {
            isMinifyEnabled = true
            isShrinkResources = true
            proguardFiles(getDefaultProguardFile("proguard-android-optimize.txt"))
            signingConfig = signingConfigs.getByName("footy")
        }
        debug {
            signingConfig = signingConfigs.getByName("footy")
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
