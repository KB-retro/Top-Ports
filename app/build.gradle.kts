import java.util.Properties
import org.jetbrains.kotlin.gradle.dsl.JvmTarget

plugins {
    id("com.android.application")
    id("org.jetbrains.kotlin.android")
    id("org.jetbrains.kotlin.plugin.compose")
}

val localProps = Properties().apply {
    rootProject.file("local.properties").takeIf { it.exists() }?.inputStream()?.use { load(it) }
}

/** Lee un ajuste de -P, variable de entorno o local.properties (en ese orden). */
fun config(name: String, default: String = ""): String =
    listOf(project.findProperty(name) as String?, System.getenv(name), localProps.getProperty(name))
        .firstOrNull { !it.isNullOrBlank() }?.trim() ?: default

fun quoted(value: String) = "\"" + value.replace("\\", "\\\\").replace("\"", "\\\"") + "\""

val tdlibDir = rootProject.file("tdlib")

android {
    namespace = "com.topports.app"
    compileSdk = 36

    defaultConfig {
        applicationId = "com.topports.app"
        minSdk = 26
        targetSdk = 35
        // En CI se usa el número de ejecución para que cada APK sea una actualización del anterior.
        versionCode = config("VERSION_CODE", "1").toInt()
        versionName = "0.1." + config("VERSION_CODE", "0")

        // Credenciales de la app de Telegram: https://my.telegram.org → API development tools.
        buildConfigField("int", "TELEGRAM_API_ID", (config("TELEGRAM_API_ID").toIntOrNull() ?: 0).toString())
        buildConfigField("String", "TELEGRAM_API_HASH", quoted(config("TELEGRAM_API_HASH")))

        // Canal de los ports. El id es el de Telegram sin el prefijo -100.
        buildConfigField("long", "CHANNEL_ID", "${config("TOPPORTS_CHANNEL_ID", "4399047221").toLong()}L")
        buildConfigField("String", "CHANNEL_USERNAME", quoted(config("TOPPORTS_CHANNEL_USERNAME")))
        buildConfigField("String", "CHANNEL_INVITE_LINK", quoted(config("TOPPORTS_CHANNEL_INVITE_LINK")))
        // Contacto para reclamaciones (enlace, @usuario de Telegram o email). Vacío: el canal.
        buildConfigField("String", "LEGAL_CONTACT", quoted(config("TOPPORTS_LEGAL_CONTACT")))

        ndk {
            abiFilters += listOf("arm64-v8a", "armeabi-v7a")
        }
    }

    // Clave fija: así Android acepta cada APK nuevo como actualización y conserva los datos.
    val keystorePath = config("SIGNING_KEYSTORE_FILE")
    signingConfigs {
        if (keystorePath.isNotBlank()) {
            create("release") {
                storeFile = file(keystorePath)
                storeType = "PKCS12"
                storePassword = config("SIGNING_STORE_PASSWORD")
                keyAlias = config("SIGNING_KEY_ALIAS", "topports")
                keyPassword = config("SIGNING_KEY_PASSWORD").ifBlank { config("SIGNING_STORE_PASSWORD") }
            }
        }
    }

    buildTypes {
        release {
            isMinifyEnabled = false
            signingConfig = signingConfigs.findByName("release") ?: signingConfigs.getByName("debug")
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

    sourceSets["main"].java.srcDir(File(tdlibDir, "java"))
    sourceSets["main"].jniLibs.srcDir(File(tdlibDir, "libs"))

    packaging {
        resources.excludes += "/META-INF/{AL2.0,LGPL2.1}"
        // Las .so se extraen al instalar: TDLib y 7-Zip se cargan con System.loadLibrary.
        jniLibs.useLegacyPackaging = true
    }
}

kotlin {
    compilerOptions { jvmTarget.set(JvmTarget.JVM_17) }
}

val checkTdlib by tasks.registering {
    doLast {
        if (!File(tdlibDir, "java/org/drinkless/tdlib/TdApi.java").exists()) {
            throw GradleException(
                "Falta TDLib en ${tdlibDir.path}. Ejecuta scripts/build-tdlib.sh " +
                    "o descarga el artefacto 'tdlib' del workflow de GitHub Actions.",
            )
        }
    }
}
tasks.named("preBuild") { dependsOn(checkTdlib) }

dependencies {
    implementation(project(":core"))

    val composeBom = platform("androidx.compose:compose-bom:2025.07.00")
    implementation(composeBom)
    implementation("androidx.compose.ui:ui")
    implementation("androidx.compose.foundation:foundation")
    implementation("androidx.compose.material3:material3")
    implementation("androidx.compose.ui:ui-tooling-preview")
    debugImplementation("androidx.compose.ui:ui-tooling")

    implementation("androidx.core:core-ktx:1.16.0")
    implementation("androidx.activity:activity-compose:1.10.1")
    implementation("androidx.lifecycle:lifecycle-viewmodel-compose:2.9.1")
    implementation("androidx.lifecycle:lifecycle-runtime-compose:2.9.1")
    implementation("androidx.lifecycle:lifecycle-service:2.9.1")
    implementation("org.jetbrains.kotlinx:kotlinx-coroutines-android:1.10.2")

    implementation("io.coil-kt:coil-compose:2.7.0")
    // Reproductor de vídeo integrado (tutoriales de instalación del canal).
    implementation("androidx.media3:media3-exoplayer:1.6.1")
    implementation("androidx.media3:media3-ui:1.6.1")
    implementation("com.github.omicronapps:7-Zip-JBinding-4Android:Release-16.02-2.03")
}
