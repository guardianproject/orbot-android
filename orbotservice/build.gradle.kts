plugins {
    alias(libs.plugins.android.library)
    alias(libs.plugins.kotlin.android)
    alias(libs.plugins.kotlin.serialization)
}

kotlin { jvmToolchain(21) }

android {
    namespace = "org.torproject.android.service"
    compileSdk = 36

    defaultConfig {
        minSdk = 24
        testOptions.targetSdk = 36
    }

    buildTypes {
        release {
            isMinifyEnabled = false
            proguardFiles(
                getDefaultProguardFile("proguard-android.txt"), "proguard-rules.pro"
            )
        }
    }

    compileOptions {
        sourceCompatibility = JavaVersion.VERSION_21
        targetCompatibility = JavaVersion.VERSION_21
    }

    packaging {
        resources {
            excludes += setOf("META-INF/androidx.localbroadcastmanager_localbroadcastmanager.version")
        }
    }

    lint {
        abortOnError = true
        checkReleaseBuilds = false
        disable + "InvalidPackage"
        htmlReport = true
        lintConfig = file("../lint.xml")
        textReport = false
        xmlReport = false
    }
}


dependencies {
    api(libs.tor.android)
    // local tor-android:
    // api(files("../../tor-android/tor-android-binary/build/outputs/aar/tor-android-binary-debug.aar"))

    api(project(":OrbotLib")) // Use locally built ipt_proxy+go_tun2socks
    api(libs.guardian.jtorctl)
    implementation(libs.android.shell)
    implementation(libs.androidx.core)
    implementation(libs.androidx.appcompat)
    implementation(libs.androidx.localbroadcast)
    implementation(libs.androidx.work)
    implementation(libs.pcap.core)
    implementation(libs.pcap.factory)
    implementation(files("../libs/geoip.jar"))
    implementation(libs.androidx.core.ktx)
    implementation(libs.kotlinx.serialization.json)
    implementation(libs.retrofit.converter)
    implementation(libs.retrofit.lib)
}
