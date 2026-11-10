plugins {
    id("com.android.application")
    id("org.jetbrains.kotlin.android")
}

android {
    namespace = "id.asta.app"
    compileSdk = 34

    defaultConfig {
        applicationId = "id.asta.app"
        minSdk = 26
        targetSdk = 34
        versionCode = 1
        versionName = "0.1"
        // Alamat PWA Asta; diisi otomatis oleh GitHub Actions (-PalamatAsta=...)
        val alamat = (project.findProperty("alamatAsta") as String?) ?: ""
        buildConfigField("String", "ALAMAT_ASTA", "\"$alamat\"")
    }

    buildFeatures {
        buildConfig = true
    }

    // Kunci tanda tangan tetap supaya APK baru bisa menimpa APK lama tanpa mencopot.
    signingConfigs {
        create("tetap") {
            storeFile = file("asta-debug.keystore")
            storePassword = "asta-debug"
            keyAlias = "asta"
            keyPassword = "asta-debug"
        }
    }

    buildTypes {
        debug {
            signingConfig = signingConfigs.getByName("tetap")
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
