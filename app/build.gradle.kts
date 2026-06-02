import com.android.build.api.dsl.LibraryExtension

plugins {
    alias(libs.plugins.kotlin.serialization)
    alias(libs.plugins.android.library)
}

kotlin { jvmToolchain(25) }

configure<LibraryExtension> {
    namespace = "org.torproject.android"
    compileSdk {
        version = release(37)
    }

    defaultConfig {
        // applicationId = namespace  // not applicable for library modules
        // versionCode  // not applicable for library modules
        // versionName  // not applicable for library modules
        minSdk = 24
        targetSdk = 36
        multiDexEnabled = true
        testInstrumentationRunner = "androidx.test.runner.AndroidJUnitRunner"
        flavorDimensions += "free"
    }

    compileOptions {
        sourceCompatibility = JavaVersion.VERSION_25
        targetCompatibility = JavaVersion.VERSION_25
    }

    buildFeatures {
        buildConfig = true
        viewBinding = true
    }

    testOptions { execution = "ANDROIDX_TEST_ORCHESTRATOR" }

    buildTypes {
        getByName("release") {
            isShrinkResources = false
            isMinifyEnabled = false
            proguardFiles(
                getDefaultProguardFile("proguard-android-optimize.txt"), "proguard-rules.txt"
            )
            // signingConfig removed — library modules don't sign APKs
        }
        getByName("debug") {
            isDebuggable = true
            // applicationIdSuffix removed — not applicable for library modules
        }
    }

    ndkVersion = "29.0.14206865"
    externalNativeBuild {
        ndkBuild {
            path = file("src/main/jni/Android.mk")
        }
    }

    productFlavors {
        create("fullperm") {
            dimension = "free"
        }
        create("nightly") {
            dimension = "free"
            // applicationId removed — not applicable for library modules
            // versionCode removed — not applicable for library modules
        }
    }

    packaging {
        resources {
            excludes += listOf("META-INF/androidx.localbroadcastmanager_localbroadcastmanager.version")
        }
        jniLibs {
            // Needed for shadowsocks-rust client to be available to execute.
            useLegacyPackaging = true
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

/*
 * UpdateBridgeConfig block commented out — not needed when embedding orbot as a library.
 * UpdateBridgeConfig is defined in buildSrc/ but djibVPN's build does not include that buildSrc.
 *
val updateBuiltinBridges = tasks.register<UpdateBridgeConfig>("updateBuiltinBridges") {
    onlyIf { enabledForVariant.getOrElse(false) }

    assetsDir.set(layout.projectDirectory.dir("src/main/assets"))

    val dateStr: String = providers.exec {
        commandLine("git", "log", "-n", "1", "--date=unix", assetsDir.get().asFile.path)
    }.standardOutput.asText.get().trim().split("\n").filter { it.contains("Date:") }[0]
    gitLogUnixTimestamp.set(dateStr.substring("Date:".length).trim().split(" ")[0])

    gitStatusOutput.set(providers.exec {
        commandLine("git", "status", "--porcelain")
    }.standardOutput.asText.map { it.trim() }.orElse(""))

}

androidComponents {
    onVariants { variant ->
        variant.outputs.forEach { output ->
            if (output.versionCode.get() == orbotBaseVersionCode) {
                val incrementMap =
                    mapOf("armeabi-v7a" to 1, "arm64-v8a" to 2, "x86" to 4, "x86_64" to 5)
                val increment =
                    incrementMap[output.filters.find { it.filterType.name == "ABI" }?.identifier]
                        ?: 0
                output.versionCode = (orbotBaseVersionCode) + increment
            }
        }
        base {
            archivesName.set("Orbot-${android.defaultConfig.versionName}")
        }
        if (variant.buildType == "release") {
            updateBuiltinBridges.configure {
                enabledForVariant.set(true)
            }
            variant.sources.assets?.addGeneratedSourceDirectory(
                updateBuiltinBridges,
                UpdateBridgeConfig::assetsDir
            )
        }
    }
}
*/

dependencies {
    implementation(libs.androidx.navigation.fragment.ktx)
    implementation(libs.androidx.navigation.ui.ktx)
    implementation(libs.androidx.preference)
    implementation(libs.androidx.appcompat)
    implementation(libs.androidx.biometric)
    implementation(libs.androidx.lifecycle.process)
    implementation(libs.androidx.localbroadcast)
    implementation(libs.androidx.window)
    implementation(libs.retrofit.converter)
    implementation(libs.rootbeer.lib)
    implementation(libs.kotlinx.coroutines.core)
    implementation(libs.kotlinx.serialization.json)
    implementation(libs.androidx.work.kotlin)
    implementation(libs.upnp)
    implementation(libs.quickie)

    // IPtProxy (for Snowflake, obfs4, dnstt and all other pluggable transports)
    implementation(libs.iptproxy)
    // uncomment to use a local build of IPtProxy:
    // implementation(files("../../IPtProxy/IPtProxy.aar"))


    // Tor
    implementation(files("../libs/geoip.jar"))
    api(libs.guardian.jtorctl)
    api(libs.tor.android)
    // uncomment to use a local build of tor-android:
    // api(files("../../tor-android/tor-android-binary/build/outputs/aar/tor-android-binary-debug.aar"))

    androidTestImplementation(libs.androidx.junit)
    androidTestImplementation(libs.androidx.espresso)
    androidTestImplementation(libs.androidx.rules)
    androidTestImplementation(libs.androidx.runner)
    androidTestImplementation(libs.screengrab)
    androidTestImplementation(libs.androidx.espresso.contrib)
    androidTestUtil(libs.androidx.orchestrator)
}

afterEvaluate {
    tasks.named("preBuild") {
        dependsOn(copyLicenseToAssets)
    }
    /*
    tasks.named { it == "mergeNightlyDebugAssets" || it == "mergeFullpermDebugAssets" }
        .configureEach {
            mustRunAfter(updateBuiltinBridges)
        }
    */
}

val copyLicenseToAssets by tasks.registering(Copy::class) {
    from(rootProject.file("LICENSE"))
    into(layout.projectDirectory.dir("src/main/assets"))
}
