plugins {
    id("com.android.library")
    id("org.jetbrains.kotlin.android")
}

// Gemeinsamer Code für Handy und Uhr: Zählung, Speicher, Berechnung, Diagramm
android {
    namespace = "com.rgb42.footy.shared"
    compileSdk = 35
    defaultConfig { minSdk = 26 }
    compileOptions {
        sourceCompatibility = JavaVersion.VERSION_17
        targetCompatibility = JavaVersion.VERSION_17
    }
    kotlinOptions { jvmTarget = "17" }
}
