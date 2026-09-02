import org.jetbrains.kotlin.gradle.dsl.JvmTarget

plugins {
    alias(libs.plugins.kotlin.multiplatform)
    alias(libs.plugins.android.library)
    alias(libs.plugins.kotlin.serialization)
    alias(libs.plugins.ksp)
    alias(libs.plugins.room)
}

/**
 * Módulo compartido TuBolsillo (migración KMP).
 * M2: Room KMP (2.8.2 + sqlite-bundled) y Koin en commonMain; Retrofit,
 * kotlin-csv y SecretStore siguen temporalmente en androidMain (M3/M4/M7).
 */
kotlin {
    androidTarget {
        compilerOptions {
            jvmTarget.set(JvmTarget.JVM_17)
        }
    }

    sourceSets {
        commonMain.dependencies {
            implementation(libs.kotlinx.coroutines.core)
            implementation(libs.kotlinx.serialization.json)
            implementation(libs.kotlinx.datetime)
            implementation(libs.okio)
            implementation(libs.room.runtime)
            implementation(libs.sqlite.bundled)
            implementation(libs.datastore.preferences)
            implementation(libs.koin.core)
        }
        androidMain.dependencies {
            implementation(libs.kotlinx.coroutines.android)
            implementation(libs.retrofit)
            implementation(libs.retrofit.kotlinx.serialization)
            implementation(libs.okhttp)
            implementation(libs.okhttp.logging)
            implementation(libs.security.crypto)
            implementation(libs.kotlin.csv)
            implementation(libs.androidx.core.ktx)
            implementation(libs.koin.android)
            // Anotaciones de inyección (Hilt se queda en :app hasta M4).
            implementation("javax.inject:javax.inject:1")
            implementation(libs.hilt.android)
        }
        commonTest.dependencies {
            implementation(libs.kotlin.test)
            implementation(libs.kotlinx.coroutines.test)
            implementation(libs.turbine)
        }
        androidUnitTest.dependencies {
            implementation(libs.kotlin.test)
            implementation(libs.junit)
            implementation(libs.mockwebserver)
            implementation(libs.room.testing)
        }
    }
}

// Compilador de Room para KMP (el plugin androidx.room gestiona KSP por target).
dependencies {
    add("kspCommonMainMetadata", libs.room.compiler)
}

room {
    schemaDirectory("$projectDir/schemas")
}

android {
    namespace = "dev.tubolsillo.app.shared"
    compileSdk = 35

    defaultConfig {
        minSdk = 26
    }

    compileOptions {
        sourceCompatibility = JavaVersion.VERSION_17
        targetCompatibility = JavaVersion.VERSION_17
    }
}
