import org.jetbrains.kotlin.gradle.dsl.JvmTarget

plugins {
    alias(libs.plugins.android.application)
    // KSP (Room y App Functions) todavía no convive con el Kotlin integrado
    // de AGP 9, por eso gradle.properties apaga builtInKotlin y este plugin
    // sigue siendo el que compila.
    alias(libs.plugins.kotlin.android)
    alias(libs.plugins.kotlin.compose)
    alias(libs.plugins.ksp)
}

// Versión: el workflow de release la inyecta desde el tag (`-PappVersionName=v0.2.0`);
// en local se cae a la de aquí. El versionCode se deriva del nombre
// (0.2.1 → 201, 1.0.0 → 10000) para que crezca junto con la versión y para que
// un build local y el de CI de la MISMA versión compartan código: si CI usara
// otro número, reinstalar en local exigiría bajar de versión.
val appVersionName = ((findProperty("appVersionName") as String?) ?: "0.1.1").removePrefix("v")
val appVersionCode = (findProperty("appVersionCode") as String?)?.toIntOrNull()
    ?: versionCodeOf(appVersionName)

fun versionCodeOf(name: String): Int {
    val parts = name.split('.', '-', '+').map { part -> part.takeWhile(Char::isDigit).toIntOrNull() ?: 0 }
    val major = parts.getOrElse(0) { 0 }
    val minor = parts.getOrElse(1) { 0 }
    val patch = parts.getOrElse(2) { 0 }
    return major * 10_000 + minor * 100 + patch
}

// Firma: CI pasa el keystore por variables de entorno (el MISMO que firma en
// local, para que la actualización se instale encima). Sin esa variable se
// firma con la clave de debug, que es la que usan los builds locales.
val envKeystore = System.getenv("TUBOLSILLO_KEYSTORE")

android {
    namespace = "dev.tubolsillo.app"
    compileSdk = 37

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
    // Bloqueo de la app con huella/PIN del sistema.
    implementation("androidx.biometric:biometric:1.1.0")
    implementation(libs.androidx.activity.compose)
    implementation(libs.koin.android)
    implementation(libs.work.runtime.ktx)
    implementation(libs.kotlinx.coroutines.android)

    // Gemini Nano on-device vía AICore (revisión asistida de capturas).
    implementation("com.google.mlkit:genai-prompt:1.0.0-beta4")

    // LLM local (fallback sin AICore): MediaPipe LLM Inference.
    implementation("com.google.mediapipe:tasks-genai:0.10.27")

    // App Functions: Gemini llama a funciones de la app desde el asistente.
    implementation(libs.appfunctions)
    ksp(libs.appfunctions.compiler)
}
