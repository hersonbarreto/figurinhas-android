import java.util.Properties

plugins {
    id("com.android.application")
    id("org.jetbrains.kotlin.android")
    id("org.jetbrains.kotlin.plugin.compose")
}

android {
    namespace = "com.hbfr.figurinhas"
    compileSdk = 36
    ndkVersion = "27.2.12479018"

    defaultConfig {
        applicationId = "com.hbfr.figurinhas"
        minSdk = 28
        targetSdk = 36
        versionCode = 2
        versionName = "1.0"
        // O ContentProvider que o WhatsApp lê. O WhatsApp exige este formato.
        manifestPlaceholders["contentProviderAuthority"] = "com.hbfr.figurinhas.stickercontentprovider"
        ndk { abiFilters += listOf("arm64-v8a", "armeabi-v7a", "x86_64") }
        // APK de teste instalado fora da Play: mostra "Liberar Pro (teste)" no Sobre. Nunca no AAB da loja.
        buildConfigField("boolean", "TEST_UNLOCK", (project.findProperty("testUnlock")?.toString() == "true").toString())
        buildConfigField("String", "CONTENT_PROVIDER_AUTHORITY", "\"com.hbfr.figurinhas.stickercontentprovider\"")
    }

    // Chave de envio da Play: -PsigningProps=/caminho/keystore.properties (fica fora do GitHub).
    // Sem ela, o release é assinado com a chave de debug (só para testes fora da loja).
    val signingProps = project.findProperty("signingProps")?.toString()?.let { file(it) }?.takeIf { it.exists() }
    signingConfigs {
        if (signingProps != null) create("upload") {
            val p = Properties().apply { signingProps.inputStream().use { load(it) } }
            storeFile = file(p.getProperty("storeFile"))
            storePassword = p.getProperty("storePassword")
            keyAlias = p.getProperty("keyAlias")
            keyPassword = p.getProperty("keyPassword")
        }
    }
    buildTypes {
        release {
            signingConfig = signingConfigs.findByName("upload") ?: signingConfigs.getByName("debug")
            isMinifyEnabled = true
            isShrinkResources = true
            proguardFiles(getDefaultProguardFile("proguard-android-optimize.txt"), "proguard-rules.pro")
        }
    }
    externalNativeBuild {
        cmake { path = file("src/main/cpp/CMakeLists.txt"); version = "3.22.1" }
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
    implementation("com.google.android.gms:play-services-mlkit-subject-segmentation:16.0.0-beta1")
    implementation("com.android.billingclient:billing-ktx:8.3.0")
    implementation("org.jetbrains.kotlinx:kotlinx-coroutines-android:1.9.0")
    debugImplementation("androidx.compose.ui:ui-tooling")
}
