import com.android.build.gradle.internal.api.ApkVariantOutputImpl
import java.io.FileInputStream
import java.util.*

plugins {
    alias(libs.plugins.kotlin.android)
    alias(libs.plugins.kotlin.serialization)
    alias(libs.plugins.android.application)
}

kotlin { jvmToolchain(21) }

val orbotBaseVersionCode = 1750300200
fun getVersionName(): String {
    // Gets the version name from the latest Git tag
    return providers.exec {
        commandLine("git", "describe", "--tags", "--always")
    }.standardOutput.asText.get().trim()
}

android {
    namespace = "org.torproject.android"
    compileSdk = 36

    defaultConfig {
        applicationId = namespace
        versionCode = orbotBaseVersionCode
        versionName = getVersionName()
        minSdk = 24
        targetSdk = 36
        multiDexEnabled = true
        testInstrumentationRunner = "androidx.test.runner.AndroidJUnitRunner"
        flavorDimensions += "free"
    }

    compileOptions {
        sourceCompatibility = JavaVersion.VERSION_21
        targetCompatibility = JavaVersion.VERSION_21
    }

    splits {
        abi {
            isEnable = true
            reset()
            include("x86", "armeabi-v7a", "x86_64", "arm64-v8a")
            isUniversalApk = true
        }
    }

    buildFeatures {
        buildConfig = true
        viewBinding = true
    }

    testOptions { execution = "ANDROIDX_TEST_ORCHESTRATOR" }

    signingConfigs {
        create("release") {
            val keystorePropertiesFile = rootProject.file("keystore.properties")
            val keystoreProperties = Properties()
            if (keystorePropertiesFile.canRead()) {
                keystoreProperties.load(FileInputStream(keystorePropertiesFile))
            }
            if (!keystoreProperties.stringPropertyNames().isEmpty()) {
                keyAlias = keystoreProperties["keyAlias"] as String
                keyPassword = keystoreProperties["keyPassword"] as String
                storeFile = file(keystoreProperties["storeFile"] as String)
                storePassword = keystoreProperties["storePassword"] as String
            }
        }
    }

    buildTypes {
        getByName("release") {
            isShrinkResources = false
            isMinifyEnabled = false
            proguardFiles(getDefaultProguardFile("proguard-android.txt"), "proguard-rules.txt")
            signingConfig = signingConfigs.getByName("release")
        }
        getByName("debug") {
            isDebuggable = true
            applicationIdSuffix = ".debug"
        }
    }

    productFlavors {
        create("fullperm") { dimension = "free" }
        create("nightly") {
            dimension = "free"
            // overwrites defaults from defaultConfig
            applicationId = "org.torproject.android.nightly"
            versionCode = (Date().time / 1000).toInt()
        }
    }

    packaging {
        resources {
            excludes += listOf("META-INF/androidx.localbroadcastmanager_localbroadcastmanager.version")
        }
    }

    lint {
        abortOnError = false
        checkReleaseBuilds = false
        disable += "InvalidPackage"
        htmlReport = true
        lintConfig = file("../lint.xml")
        textReport = false
        xmlReport = false
    }
}

// Increments versionCode by ABI type
android.applicationVariants.all {
    outputs.configureEach { ->
        if (versionCode == orbotBaseVersionCode) {
            val incrementMap =
                mapOf("armeabi-v7a" to 1, "arm64-v8a" to 2, "x86" to 4, "x86_64" to 5)
            val increment = incrementMap[filters.find { it.filterType == "ABI" }?.identifier] ?: 0
            (this as ApkVariantOutputImpl).versionCodeOverride = orbotBaseVersionCode + increment
        }
    }
}

dependencies {
    implementation(project(":OrbotLib"))
    implementation(project(":orbotservice"))
    implementation(libs.android.material)
    implementation(libs.android.volley)
    implementation(libs.androidx.activity)
    implementation(libs.androidx.core.ktx)
    implementation(libs.androidx.localbroadcast)
    implementation(libs.androidx.navigation.fragment.ktx)
    implementation(libs.androidx.navigation.ui.ktx)
    implementation(libs.androidx.preference)
    implementation(libs.androidx.appcompat)
    implementation(libs.androidx.biometric)
    implementation(libs.androidx.lifecycle.common)
    implementation(libs.androidx.lifecycle.process)
    implementation(libs.retrofit.converter)
    implementation(libs.retrofit.lib)
    implementation(libs.rootbeer.lib)
    implementation(libs.kotlinx.coroutines.core)
    implementation(libs.kotlinx.serialization.json)
    implementation(libs.appiconnamechanger)
    implementation(libs.androidx.work.kotlin)
    implementation(libs.upnp)
    

    testImplementation(libs.junit.jupiter)
    androidTestImplementation(libs.androidx.junit)
    androidTestImplementation(libs.androidx.espresso)
    androidTestImplementation(libs.androidx.rules)
    androidTestImplementation(libs.androidx.runner)
    androidTestImplementation(libs.screengrab)
    androidTestUtil(libs.androidx.orchestrator)

}

tasks.named("preBuild") { dependsOn("copyLicenseToAssets") }
tasks.register<Copy>("copyLicenseToAssets") {
    from(layout.projectDirectory.file("LICENSE"))
    into(layout.projectDirectory.dir("src/main/assets"))
}

