import org.jetbrains.kotlin.gradle.dsl.JvmTarget

plugins {
    alias(libs.plugins.kotlin.multiplatform)
    alias(libs.plugins.android.library)
    alias(libs.plugins.kotlin.compose)
    alias(libs.plugins.kotlin.serialization)
    alias(libs.plugins.ksp)
    alias(libs.plugins.room)
    alias(libs.plugins.compose.multiplatform)
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
            implementation(libs.ktor.client.core)
            implementation(libs.ktor.client.content.negotiation)
            implementation(libs.ktor.serialization.kotlinx.json)
            implementation(libs.ktor.client.logging)

            // Compose Multiplatform + UI común (Android e iOS).
            implementation(compose.runtime)
            implementation(compose.foundation)
            implementation(compose.material3)
            implementation(compose.ui)
            implementation(libs.material.icons.extended)
            implementation(libs.navigation.compose.kmp)
            implementation(libs.lifecycle.viewmodel.compose.kmp)
            implementation(libs.lifecycle.runtime.compose.kmp)
            implementation(libs.koin.compose)
            implementation(libs.koin.compose.viewmodel)
            implementation(libs.koin.compose.viewmodel.navigation)
        }
        androidMain.dependencies {
            implementation(libs.kotlinx.coroutines.android)
            implementation(libs.ktor.client.okhttp)
            implementation(libs.security.crypto)
            implementation(libs.androidx.core.ktx)
            implementation(libs.koin.android)
            implementation(libs.androidx.activity.compose)
        }
        commonTest.dependencies {
            implementation(libs.kotlin.test)
            implementation(libs.kotlinx.coroutines.test)
            implementation(libs.turbine)
            implementation(libs.ktor.client.mock)
        }
        androidUnitTest.dependencies {
            implementation(libs.kotlin.test)
            implementation(libs.junit)
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
