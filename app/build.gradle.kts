import org.jetbrains.kotlin.gradle.dsl.JvmTarget

plugins {
    alias(libs.plugins.android.application)
    alias(libs.plugins.kotlin.android)
    alias(libs.plugins.kotlin.compose)
}

// Versión: el workflow de release la inyecta desde el tag (`-PappVersionName=v0.2.0`)
// y usa el número de ejecución como versionCode, que Android exige creciente.
// En local se cae a los valores de aquí.
val appVersionName = ((findProperty("appVersionName") as String?) ?: "0.1.0").removePrefix("v")
val appVersionCode = (findProperty("appVersionCode") as String?)?.toIntOrNull() ?: 1

// Firma: CI pasa el keystore por variables de entorno (el MISMO que firma en
// local, para que la actualización se instale encima). Sin esa variable se
// firma con la clave de debug, que es la que usan los builds locales.
val envKeystore = System.getenv("TUBOLSILLO_KEYSTORE")

android {
    namespace = "dev.tubolsillo.app"
    compileSdk = 35

    signingConfigs {
        if (!envKeystore.isNullOrBlank()) {
            create("release") {
                storeFile = file(envKeystore)
                storePassword = System.getenv("TUBOLSILLO_KEYSTORE_PASSWORD")
                keyAlias = System.getenv("TUBOLSILLO_KEY_ALIAS") ?: "androiddebugkey"
                keyPassword = System.getenv("TUBOLSILLO_KEY_PASSWORD")
            }
        }
    }

    defaultConfig {
        applicationId = "dev.tubolsillo.app"
        minSdk = 26
        targetSdk = 35
        versionCode = appVersionCode
        versionName = appVersionName
        testInstrumentationRunner = "androidx.test.runner.AndroidJUnitRunner"

        // MediaPipe LLM trae un .so de ~13 MB por ABI. El APK se distribuye
        // solo para arm64 (todos los teléfonos soportados por AICore lo son);
        // así no pesa 62 MB por incluir x86/armeabi-v7a que nadie usa.
        ndk {
            abiFilters += listOf("arm64-v8a")
        }
    }

    buildTypes {
        release {
            isMinifyEnabled = true
            isShrinkResources = true
            signingConfig = signingConfigs.findByName("release")
                ?: signingConfigs.getByName("debug")
            proguardFiles(
                getDefaultProguardFile("proguard-android-optimize.txt"),
                "proguard-rules.pro"
            )
        }
    }

    compileOptions {
        sourceCompatibility = JavaVersion.VERSION_17
        targetCompatibility = JavaVersion.VERSION_17
    }

    buildFeatures {
        compose = true
    }

    packaging {
        resources {
            excludes += "/META-INF/{AL2.0,LGPL2.1}"
        }
    }
}

kotlin {
    compilerOptions {
        jvmTarget.set(JvmTarget.JVM_17)
    }
}

dependencies {
    implementation(project(":shared"))

    // Compose mínimo para MainActivity (la UI completa vive en shared).
    implementation(platform(libs.compose.bom))
    implementation(libs.compose.ui)
    implementation(libs.compose.material3)
    implementation(libs.compose.ui.tooling.preview)
    debugImplementation(libs.compose.ui.tooling)

    implementation(libs.androidx.core.ktx)
    implementation(libs.androidx.activity.compose)
    implementation(libs.koin.android)
    implementation(libs.work.runtime.ktx)
    implementation(libs.kotlinx.coroutines.android)

    // Gemini Nano on-device vía AICore (revisión asistida de capturas).
    implementation("com.google.mlkit:genai-prompt:1.0.0-beta4")

    // LLM local (fallback sin AICore): MediaPipe LLM Inference.
    implementation("com.google.mediapipe:tasks-genai:0.10.27")
}
