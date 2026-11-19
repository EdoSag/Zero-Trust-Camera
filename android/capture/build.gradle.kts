// The camera screens of ZT Camera: viewfinder, shutter, modes, in-app gallery and camera
// settings, in Kotlin on CameraX. Started from GrapheneOS Camera (MIT, see LICENSE.upstream and
// README.md here). Kept apart from the Flutter `app` module so the camera opens without waiting
// for a Flutter engine.
plugins {
    id("com.android.library")
    id("org.jetbrains.kotlin.android")
}

android {
    namespace = "dev.sagron.zerotrustcamera.capture"
    compileSdk = 37

    defaultConfig {
        // 29: scoped storage (MediaStore without a storage permission), as upstream.
        minSdk = 29
        consumerProguardFiles("consumer-rules.pro")
    }

    compileOptions {
        sourceCompatibility = JavaVersion.VERSION_17
        targetCompatibility = JavaVersion.VERSION_17
    }

    buildFeatures {
        viewBinding = true
        buildConfig = true
    }
}

kotlin {
    compilerOptions {
        jvmTarget = org.jetbrains.kotlin.gradle.dsl.JvmTarget.JVM_17
    }
}

dependencies {
    // Same versions as upstream at the imported commit.
    implementation("androidx.appcompat:appcompat:1.8.0")
    implementation("com.google.android.material:material:1.14.0")
    implementation("androidx.constraintlayout:constraintlayout:2.2.2")
    implementation("androidx.core:core-ktx:1.19.1")

    val camerax = "1.6.2"
    implementation("androidx.camera:camera-core:$camerax")
    implementation("androidx.camera:camera-camera2:$camerax")
    implementation("androidx.camera:camera-lifecycle:$camerax")
    implementation("androidx.camera:camera-video:$camerax")
    implementation("androidx.camera:camera-view:$camerax")
    implementation("androidx.camera:camera-extensions:$camerax")

    // QR and barcode decoding, on the phone.
    implementation("com.google.zxing:core:3.5.4")

    // ZT Camera Private vault: X25519 and HKDF in plain Java (Android's own X25519 needs
    // Android 13), and the fingerprint, face or screen lock prompt that opens the vault.
    implementation("com.google.crypto.tink:tink-android:1.18.0")
    implementation("androidx.biometric:biometric:1.1.0")

    // ZT Camera document scanner and "Copy text": Tesseract OCR, on the phone. The language
    // files are bundled at build time (tool/fetch_tessdata.sh).
    implementation("cz.adaptech.tesseract4android:tesseract4android:4.9.0")

    testImplementation("junit:junit:4.13.2")
    testImplementation("com.google.crypto.tink:tink:1.18.0")
}
