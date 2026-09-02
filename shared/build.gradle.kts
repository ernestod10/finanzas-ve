import org.jetbrains.kotlin.gradle.dsl.JvmTarget

plugins {
    alias(libs.plugins.kotlin.multiplatform)
    alias(libs.plugins.android.library)
    alias(libs.plugins.kotlin.serialization)
    alias(libs.plugins.ksp)
}

/**
 * Módulo compartido TuBolsillo (migración KMP).
 * M1: solo androidTarget con el código puro en commonMain y la capa de datos
 * (Room 2.7 JVM, Retrofit, kotlin-csv) temporalmente en androidMain.
 * M2+: Room KMP a commonMain, Koin, Ktor, CMP y targets iOS.
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
        }
        androidMain.dependencies {
            implementation(libs.room.runtime)
            implementation(libs.room.ktx)
            implementation(libs.kotlinx.coroutines.android)
            implementation(libs.retrofit)
            implementation(libs.retrofit.kotlinx.serialization)
            implementation(libs.okhttp)
            implementation(libs.okhttp.logging)
            implementation(libs.security.crypto)
            implementation(libs.datastore.preferences)
            implementation(libs.kotlin.csv)
            implementation(libs.androidx.core.ktx)
            // Anotaciones de inyección de las clases movidas (Hilt se queda en :app).
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

ksp {
    arg("room.schemaLocation", "$projectDir/schemas")
}

// Room compiler para el target android (M2 lo cambia por el plugin androidx.room KMP).
dependencies {
    add("kspAndroid", libs.room.compiler)
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
