plugins {
    alias(libs.plugins.android.application)
    alias(libs.plugins.kotlin.android)
    alias(libs.plugins.kotlin.compose)
}

android {
    namespace = "com.dromaniak.ingephoto"
    compileSdk = 36

    defaultConfig {
        applicationId = "com.dromaniak.ingephoto"
        applicationId = ProjectInfo.applicationId
        minSdk = 29
        targetSdk = 36
        group = ProjectInfo.group
        version = ProjectInfo.versionName
        versionCode = ProjectInfo.versionCode
        versionName = ProjectInfo.versionName + "-${ProjectInfo.buildNumber}"

        testInstrumentationRunner = "androidx.test.runner.AndroidJUnitRunner"
    }

    buildTypes {
        release {
            isMinifyEnabled = false
            proguardFiles(
                getDefaultProguardFile("proguard-android-optimize.txt"),
                "proguard-rules.pro"
            )
        }
    }
    compileOptions {
        sourceCompatibility = JavaVersion.VERSION_11
        targetCompatibility = JavaVersion.VERSION_11
    }
    kotlinOptions {
        jvmTarget = "11"
    }
    buildFeatures {
        compose = true
    }

    applicationVariants.all {
        outputs
            .map { it as com.android.build.gradle.internal.api.BaseVariantOutputImpl }
            .forEach { output ->
                output.outputFileName = "${ProjectInfo.name}-${versionName}-${this.name}.apk"
            }
    }
}

dependencies {
    implementation(fileTree(mapOf("dir" to "libs", "include" to listOf("*.jar", "*.aar"))))
    implementation(libs.androidx.core.ktx)
    implementation(libs.androidx.lifecycle.runtime.ktx)
    implementation(libs.androidx.activity.compose)
    implementation(platform(libs.androidx.compose.bom))
    implementation(libs.androidx.ui)
    implementation(libs.androidx.ui.graphics)
    implementation(libs.androidx.ui.tooling.preview)
    implementation(libs.androidx.material3)

    // Accompanist Pager
    implementation("com.google.accompanist:accompanist-pager:0.30.1")
    implementation("com.google.accompanist:accompanist-pager-indicators:0.30.1")

    testImplementation(libs.junit)
    androidTestImplementation(libs.androidx.junit)
    androidTestImplementation(libs.androidx.espresso.core)
    androidTestImplementation(platform(libs.androidx.compose.bom))
    androidTestImplementation(libs.androidx.ui.test.junit4)
    debugImplementation(libs.androidx.ui.tooling)
    debugImplementation(libs.androidx.ui.test.manifest)
}

object ProjectInfo {
    const val group = "com.dromaniak"
    const val name = "ingephoto"
    const val applicationId = "$group.$name"
    const val majorVersion = 1
    const val minorVersion = 0
    const val patchVersion = 0
    const val buildNumber = 0
    const val versionName = "$majorVersion.$minorVersion.$patchVersion"
    const val versionCode = 1_000_00_00 * majorVersion + 1_00_00 * minorVersion + 1_00 * patchVersion + buildNumber
}