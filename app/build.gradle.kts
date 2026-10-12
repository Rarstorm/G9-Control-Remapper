import java.util.Properties

plugins {
    id("com.android.application")
    id("org.jetbrains.kotlin.android")
    id("org.jetbrains.kotlin.plugin.compose")
}

android {
    namespace = "io.github.sudodiego.g9control"
    compileSdk = 35

    defaultConfig {
        applicationId = "io.github.sudodiego.g9control"
        minSdk = 29
        targetSdk = 35
        versionCode = 25
                versionName = "1.3.19-diag"
    }

    // Firma de las versiones publicadas: keystore.properties (no va a git) apunta a la clave.
    // Sin ese archivo (por ejemplo, al compilar desde GitHub) se firma con la clave de depuración.
    val keyProps = rootProject.file("keystore.properties").takeIf { it.exists() }
        ?.let { f -> Properties().apply { f.inputStream().use { load(it) } } }
    signingConfigs {
        if (keyProps != null) create("release") {
            storeFile = file(keyProps.getProperty("storeFile"))
            storePassword = keyProps.getProperty("storePassword")
            keyAlias = keyProps.getProperty("keyAlias")
            keyPassword = keyProps.getProperty("keyPassword")
            // Tipo de almacén si viene en keystore.properties (JKS por defecto, como la clave del proyecto).
            keyProps.getProperty("storeType")?.let { storeType = it }
        }
    }

    buildTypes {
        release {
            isMinifyEnabled = true
            isShrinkResources = true
            proguardFiles(getDefaultProguardFile("proguard-android-optimize.txt"), "proguard-rules.pro")
            signingConfig = signingConfigs.findByName("release") ?: signingConfigs.getByName("debug")
        }
    }
    compileOptions {
        sourceCompatibility = JavaVersion.VERSION_17
        targetCompatibility = JavaVersion.VERSION_17
    }
    kotlinOptions { jvmTarget = "17" }
    buildFeatures { compose = true }
}

dependencies {
    val bom = platform("androidx.compose:compose-bom:2024.10.01")
    implementation(bom)
    implementation("androidx.compose.ui:ui")
    implementation("androidx.compose.foundation:foundation")
    implementation("androidx.compose.material3:material3")
    implementation("androidx.compose.material:material-icons-extended")
    implementation("androidx.activity:activity-compose:1.9.3")
    implementation("androidx.lifecycle:lifecycle-runtime-ktx:2.8.7")
    implementation("org.jetbrains.kotlinx:kotlinx-coroutines-android:1.9.0")
    // Shizuku: comandos de shell para mostrar u ocultar Game Assistant y leer su modo de rendimiento.
    implementation("dev.rikka.shizuku:api:13.1.5")
    implementation("dev.rikka.shizuku:provider:13.1.5")
    // Pantalla 16:9 sin Shizuku: IWindowManager (oculta) con WRITE_SECURE_SETTINGS dado una vez por adb.
    implementation("org.lsposed.hiddenapibypass:hiddenapibypass:4.3")
}
