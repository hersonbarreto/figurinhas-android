plugins {
    id("com.android.application")
    id("org.jetbrains.kotlin.android")
    id("org.jetbrains.kotlin.plugin.compose")
}

android {
    namespace = "com.hbfr.figurinhas"
    compileSdk = 35

    defaultConfig {
        applicationId = "com.hbfr.figurinhas"
        minSdk = 28
        targetSdk = 35
        versionCode = 1
        versionName = "0.1"
        // O ContentProvider que o WhatsApp lê. O WhatsApp exige este formato.
        manifestPlaceholders["contentProviderAuthority"] = "com.hbfr.figurinhas.stickercontentprovider"
        buildConfigField("String", "CONTENT_PROVIDER_AUTHORITY", "\"com.hbfr.figurinhas.stickercontentprovider\"")
    }

    // Para testar: o release usa a chave de debug até existir a chave de envio da Play Store.
    buildTypes {
        release {
            signingConfig = signingConfigs.getByName("debug")
            isMinifyEnabled = true
            isShrinkResources = true
            proguardFiles(getDefaultProguardFile("proguard-android-optimize.txt"), "proguard-rules.pro")
        }
    }
    buildFeatures {
        compose = true
        buildConfig = true
    }
    compileOptions {
        sourceCompatibility = JavaVersion.VERSION_17
        targetCompatibility = JavaVersion.VERSION_17
    }
    kotlinOptions { jvmTarget = "17" }
    androidResources {
        // WebP das figurinhas não pode ser recomprimido.
        noCompress += "webp"
    }
}

dependencies {
    val composeBom = platform("androidx.compose:compose-bom:2024.12.01")
    implementation(composeBom)
    implementation("androidx.core:core-ktx:1.15.0")
    implementation("androidx.activity:activity-compose:1.9.3")
    implementation("androidx.lifecycle:lifecycle-runtime-compose:2.8.7")
    implementation("androidx.compose.ui:ui")
    implementation("androidx.compose.foundation:foundation")
    implementation("androidx.compose.material3:material3")
    implementation("androidx.compose.material:material-icons-core")
    implementation("androidx.exifinterface:exifinterface:1.3.7")
    implementation("org.jetbrains.kotlinx:kotlinx-coroutines-android:1.9.0")
    debugImplementation("androidx.compose.ui:ui-tooling")
}
