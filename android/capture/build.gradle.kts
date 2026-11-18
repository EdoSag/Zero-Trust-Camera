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

    testImplementation("junit:junit:4.13.2")
}
