plugins {
    id("com.android.application")
    id("org.jetbrains.kotlin.android")
}

android {
    namespace = "com.rgb42.footy.wear"
    compileSdk = 35

    defaultConfig {
        // Handy und Uhr brauchen dieselbe ID und denselben Schlüssel für den Datenaustausch
        applicationId = "com.rgb42.footy"
        // Uhr-APK: eigene versionCode-Reihe
        minSdk = 30
        targetSdk = 34
        versionCode = 1
        versionName = "1.0"
    }

    signingConfigs {
        // Fester Schlüssel, damit Updates über die alte Version installiert
        // werden können, ohne die gespeicherten Schritte zu verlieren.
        create("footy") {
            storeFile = rootProject.file("app/footy.keystore")
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

dependencies {
    implementation(project(":shared"))
    implementation("com.google.android.gms:play-services-wearable:19.0.0")
}
