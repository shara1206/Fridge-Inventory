import java.util.Properties

plugins {
    alias(libs.plugins.android.application)
    alias(libs.plugins.kotlin.android)
    alias(libs.plugins.kotlin.compose)
    alias(libs.plugins.ksp)
    alias(libs.plugins.ktlint)
}

/*
 * One signing key for every debug build, wherever it is built, so a new APK installs over the
 * old one instead of failing with a signature conflict (and forcing an uninstall that wipes
 * the kitchen). The key itself never goes in git. It is read from keystore.properties at the
 * repo root (gitignored) or, in CI, from environment variables filled from repository
 * secrets. With neither, the build falls back to the machine's default debug key.
 */
val signingProperties = Properties().apply {
    rootProject.file("keystore.properties").takeIf { it.exists() }
        ?.inputStream()?.use { load(it) }
}

fun signingValue(key: String, env: String): String? =
    signingProperties.getProperty(key) ?: System.getenv(env)?.takeIf { it.isNotBlank() }

val sharedKeystore = signingValue("storeFile", "FRIDGE_KEYSTORE_FILE")

android {
    namespace = "com.sharawang.fridge"
    compileSdk = 35

    defaultConfig {
        applicationId = "com.sharawang.fridge"
        minSdk = 26
        targetSdk = 35
        versionCode = 1
        versionName = "0.1.0"
        testInstrumentationRunner = "androidx.test.runner.AndroidJUnitRunner"
        vectorDrawables { useSupportLibrary = true }
    }

    signingConfigs {
        if (sharedKeystore != null) {
            create("shared") {
                storeFile = rootProject.file(sharedKeystore)
                storePassword = signingValue("storePassword", "FRIDGE_KEYSTORE_PASSWORD")
                keyAlias = signingValue("keyAlias", "FRIDGE_KEY_ALIAS")
                keyPassword = signingValue("keyPassword", "FRIDGE_KEY_PASSWORD")
            }
        }
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
            applicationIdSuffix = ".debug"
            signingConfigs.findByName("shared")?.let { signingConfig = it }
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
    }
    // Lists English and Chinese to the system, so Android 13+ offers a per-app language
    // under Settings > Apps > Fridge > Language. The web app has its own language switch.
    androidResources {
        generateLocaleConfig = true
    }
    packaging {
        resources {
            excludes += "/META-INF/{AL2.0,LGPL2.1}"
        }
    }

    testOptions {
        // Robolectric needs the merged resources to inflate anything, including Room's
        // schema assets, so DAO tests can run on the JVM instead of an emulator.
        unitTests.isIncludeAndroidResources = true
    }
}

ksp {
    arg("room.schemaLocation", "$projectDir/schemas")
}

ktlint {
    // Pinned so the CLI, the plugin and .editorconfig all agree on the rule set.
    version.set("1.0.1")
    android.set(true)
    // The baseline is clean, so a style violation now fails ktlintCheck. Run
    // `./gradlew ktlintFormat` to fix most of them automatically.
    ignoreFailures.set(false)
    filter {
        // Generated sources are not ours to format.
        exclude { it.file.path.contains("/build/") }
    }
}

dependencies {
    implementation(libs.androidx.core.ktx)
    implementation(libs.androidx.lifecycle.runtime.ktx)
    implementation(libs.androidx.lifecycle.viewmodel.compose)
    implementation(libs.androidx.lifecycle.runtime.compose)
    implementation(libs.androidx.activity.compose)

    implementation(platform(libs.androidx.compose.bom))
    implementation(libs.androidx.ui)
    implementation(libs.androidx.ui.graphics)
    implementation(libs.androidx.ui.tooling.preview)
    implementation(libs.androidx.material3)
    implementation(libs.androidx.material.icons.extended)
    debugImplementation(libs.androidx.ui.tooling)

    implementation(libs.androidx.navigation.compose)

    implementation(libs.androidx.room.runtime)
    implementation(libs.androidx.room.ktx)
    ksp(libs.androidx.room.compiler)

    implementation(libs.androidx.work.runtime.ktx)
    implementation(libs.androidx.datastore.preferences)

    implementation(libs.mlkit.text.recognition)
    implementation(libs.kotlinx.coroutines.android)

    testImplementation(libs.junit)
    testImplementation(libs.kotlinx.coroutines.test)
    testImplementation(libs.robolectric)
    testImplementation(libs.androidx.test.core)
    testImplementation(libs.androidx.room.testing)

    androidTestImplementation(libs.androidx.junit)
    androidTestImplementation(libs.androidx.espresso.core)
    androidTestImplementation(platform(libs.androidx.compose.bom))
    androidTestImplementation(libs.androidx.ui.test.junit4)
    debugImplementation(libs.androidx.ui.test.manifest)
}
