plugins {
    id("com.android.application")
    id("org.jetbrains.kotlin.android")
    id("org.jetbrains.kotlin.plugin.compose")
    id("com.google.devtools.ksp")
}

val versionNameProp = (project.findProperty("appVersionName") as String?) ?: "1.0.0"
val versionCodeProp = (project.findProperty("appVersionCode") as String?)?.toInt() ?: 1

android {
    namespace = "io.github.schnubbirunkel.boardshelf"
    compileSdk = 35

    defaultConfig {
        applicationId = "io.github.schnubbirunkel.boardshelf"
        minSdk = 26
        targetSdk = 35
        versionCode = versionCodeProp
        versionName = versionNameProp
    }

    signingConfigs {
        create("release") {
            // Own key from CI secrets if present, otherwise the public project key from the repo
            // (same key for every build, so updates install over the previous version).
            fun env(name: String) = System.getenv(name)?.takeIf { it.isNotBlank() }
            val ks = env("BOARDSHELF_KEYSTORE")
            if (ks != null && file(ks).exists()) {
                storeFile = file(ks)
                storePassword = env("BOARDSHELF_KEYSTORE_PASSWORD")
                keyAlias = env("BOARDSHELF_KEY_ALIAS")
                keyPassword = env("BOARDSHELF_KEY_PASSWORD")
            } else {
                storeFile = file("${rootDir}/keystore/boardshelf-public.jks")
                storePassword = "boardshelf-public"
                keyAlias = "boardshelf"
                keyPassword = "boardshelf-public"
            }
        }
    }

    buildTypes {
        release {
            isMinifyEnabled = true
            isShrinkResources = true
            proguardFiles(getDefaultProguardFile("proguard-android-optimize.txt"), "proguard-rules.pro")
            signingConfig = signingConfigs.getByName("release")
        }
        debug {
            applicationIdSuffix = ".debug"
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
    androidResources {
        generateLocaleConfig = true
    }
}

dependencies {
    val composeBom = platform("androidx.compose:compose-bom:2024.12.01")
    implementation(composeBom)
    implementation("androidx.compose.ui:ui")
    implementation("androidx.compose.ui:ui-graphics")
    implementation("androidx.compose.foundation:foundation")
    implementation("androidx.compose.material3:material3")
    implementation("androidx.compose.material:material-icons-extended")
    implementation("androidx.compose.ui:ui-tooling-preview")
    debugImplementation("androidx.compose.ui:ui-tooling")

    implementation("androidx.core:core-ktx:1.15.0")
    implementation("androidx.appcompat:appcompat:1.7.0")
    implementation("androidx.activity:activity-compose:1.9.3")
    implementation("androidx.lifecycle:lifecycle-runtime-compose:2.8.7")
    implementation("androidx.lifecycle:lifecycle-viewmodel-compose:2.8.7")
    implementation("androidx.navigation:navigation-compose:2.8.5")
    implementation("androidx.palette:palette-ktx:1.0.0")

    implementation("androidx.room:room-runtime:2.6.1")
    implementation("androidx.room:room-ktx:2.6.1")
    ksp("androidx.room:room-compiler:2.6.1")

    implementation("com.squareup.okhttp3:okhttp:4.12.0")
    implementation("io.coil-kt:coil-compose:2.7.0")
    implementation("org.jetbrains.kotlinx:kotlinx-coroutines-android:1.9.0")
}
