plugins {
    id("com.android.application")
    id("org.jetbrains.kotlin.android")
    id("com.google.devtools.ksp")
}

// Windows Türkçe karakterli (Masaüstü) dizinlerde JUnit Worker ClassLoader hatasını önlemek için
// derleme çıktısını ASCII güvenli dizine yönlendirip üretilen APK'yı proje klasörüne senkronize eder.
val asciiSafeBuildDir = file("C:/Users/ygttc/.gradle/apexpulse_build/app")
layout.buildDirectory.set(asciiSafeBuildDir)

// google-services.json indirildiginde Firebase Google Services ve Crashlytics eklentilerini otomatik aktif et
if (file("google-services.json").exists()) {
    apply(plugin = "com.google.gms.google-services")
    apply(plugin = "com.google.firebase.crashlytics")
}

android {
    namespace = "com.wheelcharm.telemetry"
    compileSdk = 34

    defaultConfig {
        applicationId = "com.wheelcharm.apexpulse"
        minSdk = 26
        targetSdk = 34
        versionCode = 1
        versionName = "1.0.0-apex"

        testInstrumentationRunner = "androidx.test.runner.AndroidJUnitRunner"
        vectorDrawables {
            useSupportLibrary = true
        }

        manifestPlaceholders["MAPS_API_KEY"] = project.findProperty("MAPS_API_KEY") as String? ?: ""
    }

    buildTypes {
        release {
            isMinifyEnabled = true
            isShrinkResources = true
            proguardFiles(
                getDefaultProguardFile("proguard-android-optimize.txt"),
                "proguard-rules.pro"
            )
        }
        debug {
            isMinifyEnabled = false
            versionNameSuffix = "-debug"
        }
    }

    compileOptions {
        sourceCompatibility = JavaVersion.VERSION_17
        targetCompatibility = JavaVersion.VERSION_17
    }

    kotlinOptions {
        jvmTarget = "17"
    }

    buildFeatures {
        compose = true
        buildConfig = true
    }

    composeOptions {
        kotlinCompilerExtensionVersion = "1.5.14"
    }

    testOptions {
        unitTests.isReturnDefaultValues = true
    }

    packaging {
        resources {
            excludes += "/META-INF/{AL2.0,LGPL2.1}"
        }
    }
}

tasks.withType<Test>().configureEach {
    jvmArgs("-Dfile.encoding=UTF-8")
}

tasks.matching { it.name == "assembleDebug" }.configureEach {
    doLast {
        val srcApk = File(asciiSafeBuildDir, "outputs/apk/debug/app-debug.apk")
        val destDir = File(projectDir, "build/outputs/apk/debug")
        if (srcApk.exists()) {
            destDir.mkdirs()
            srcApk.copyTo(File(destDir, "app-debug.apk"), overwrite = true)
        }
    }
}

dependencies {
    val composeBom = platform("androidx.compose:compose-bom:2024.06.00")
    implementation(composeBom)
    androidTestImplementation(composeBom)

    implementation("androidx.core:core-ktx:1.13.1")
    implementation("androidx.lifecycle:lifecycle-runtime-ktx:2.8.3")
    implementation("androidx.lifecycle:lifecycle-viewmodel-compose:2.8.3")
    implementation("androidx.lifecycle:lifecycle-service:2.8.3")
    implementation("androidx.activity:activity-compose:1.9.0")

    // Jetpack Compose UI & Material 3 (Dark Mode + Icons)
    implementation("androidx.compose.ui:ui")
    implementation("androidx.compose.ui:ui-graphics")
    implementation("androidx.compose.ui:ui-tooling-preview")
    implementation("androidx.compose.material3:material3")
    implementation("androidx.compose.material:material-icons-extended")

    // Room Database (Chunked Telemetry & Hierarchical Garage)
    val roomVersion = "2.6.1"
    implementation("androidx.room:room-runtime:$roomVersion")
    implementation("androidx.room:room-ktx:$roomVersion")
    ksp("androidx.room:room-compiler:$roomVersion")

    // Google Play Services: Fused Location & Activity Recognition (Autopilot IN_VEHICLE)
    implementation("com.google.android.gms:play-services-location:21.3.0")

    // Firebase BoM: Cloud Firestore, Authentication, Crashlytics & Analytics
    implementation(platform("com.google.firebase:firebase-bom:33.1.2"))
    implementation("com.google.firebase:firebase-firestore")
    implementation("com.google.firebase:firebase-auth")
    implementation("com.google.firebase:firebase-crashlytics")
    implementation("com.google.firebase:firebase-analytics")

    // Coroutines & Play Services Tasks
    implementation("org.jetbrains.kotlinx:kotlinx-coroutines-android:1.8.1")
    implementation("org.jetbrains.kotlinx:kotlinx-coroutines-play-services:1.8.1")

    // Unit Testing & JSON Serialization Verification
    testImplementation("junit:junit:4.13.2")
    testImplementation("org.json:json:20240303")
    testImplementation("org.jetbrains.kotlinx:kotlinx-coroutines-test:1.8.1")
}
