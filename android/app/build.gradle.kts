import java.util.Properties
import java.io.FileInputStream

plugins {
    id("com.android.application")
    // The Flutter Gradle Plugin must be applied after the Android and Kotlin Gradle plugins.
    id("dev.flutter.flutter-gradle-plugin")
}

// Release signing. On CI the workflow restores the keystore from repository
// secrets and exports the CM_* variables (same names the other Zero Trust apps
// use). Locally, put the keystore details in android/key.properties.
val keystoreProperties = Properties()
val keystorePropertiesFile = rootProject.file("key.properties")
if (keystorePropertiesFile.exists()) {
    keystoreProperties.load(FileInputStream(keystorePropertiesFile))
}

// The Offline build: `flutter build apk --android-project-arg zt.offline=true
// --dart-define=ZT_OFFLINE=true`. Same app and same id, without the internet
// permission (src/offline/AndroidManifest.xml), so no account and no backup.
val ztOffline = (project.findProperty("zt.offline") as String?) == "true"

android {
    namespace = "dev.sagron.zerotrustcamera"
    // CameraX 1.6 and androidx.core 1.19 (the camera screens, :capture) need
    // API 37 to compile against (targetSdk, the runtime behaviour, stays
    // Flutter's).
    compileSdk = maxOf(flutter.compileSdkVersion, 37)
    ndkVersion = flutter.ndkVersion

    compileOptions {
        sourceCompatibility = JavaVersion.VERSION_17
        targetCompatibility = JavaVersion.VERSION_17
    }

    defaultConfig {
        applicationId = "dev.sagron.zerotrustcamera"
        // 29: the camera screens save through MediaStore without a storage
        // permission (the shared core alone needs 28).
        minSdk = 29
        targetSdk = flutter.targetSdkVersion
        versionCode = flutter.versionCode
        versionName = flutter.versionName
        if (ztOffline) versionNameSuffix = "-offline"
    }

    if (ztOffline) {
        // Release only: debug and profile builds need the network for Flutter's tools.
        sourceSets.getByName("release").manifest.srcFile("src/offline/AndroidManifest.xml")
    }

    signingConfigs {
        create("release") {
            if (System.getenv("CM_BUILD_DIR") != null) {
                storeFile = file(System.getenv("CM_BUILD_DIR") + "/codemagic.keystore")
                storePassword = System.getenv("CM_KEYSTORE_PASSWORD")
                keyAlias = System.getenv("CM_KEY_ALIAS")
                keyPassword = System.getenv("CM_KEY_PASSWORD")
            } else if (keystorePropertiesFile.exists()) {
                keyAlias = keystoreProperties["keyAlias"] as String?
                keyPassword = keystoreProperties["keyPassword"] as String?
                storeFile = keystoreProperties["storeFile"]?.let { file(it as String) }
                storePassword = keystoreProperties["storePassword"] as String?
            }
        }
    }

    buildTypes {
        release {
            // Without a release keystore (a fresh local checkout), fall back to
            // the debug keys so `flutter run --release` still works.
            signingConfig = if (System.getenv("CM_BUILD_DIR") != null || keystorePropertiesFile.exists())
                signingConfigs.getByName("release")
            else
                signingConfigs.getByName("debug")
        }
    }
}

kotlin {
    compilerOptions {
        jvmTarget = org.jetbrains.kotlin.gradle.dsl.JvmTarget.JVM_17
    }
}

flutter {
    source = "../.."
}

dependencies {
    // The native camera screens: viewfinder, shutter, gallery, camera settings.
    implementation(project(":capture"))
    // The Private vault's photo picker (VaultChannel) and share file provider.
    implementation("androidx.activity:activity:1.9.3")
    implementation("androidx.core:core-ktx:1.19.1")
}
