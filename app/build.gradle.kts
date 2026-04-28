import com.android.build.api.dsl.ApplicationExtension
import java.io.FileInputStream
import java.util.Date
import java.util.Properties

plugins {
    alias(libs.plugins.kotlin.serialization)
    alias(libs.plugins.android.application)
}

kotlin { jvmToolchain(25) }

val orbotBaseVersionCode = 1793300100
fun getVersionName(): Provider<String> {
    // Gets the version name from the latest Git tag
    return providers.exec {
        commandLine("git", "describe", "--tags", "--always")
    }.standardOutput.asText.map { it.trim() }
}

configure<ApplicationExtension> {
    namespace = "org.torproject.android"
    compileSdk = 37

    defaultConfig {
        applicationId = namespace
        versionCode = orbotBaseVersionCode
        versionName = getVersionName().get()
        minSdk = 24
        targetSdk = 37
        multiDexEnabled = true
        testInstrumentationRunner = "androidx.test.runner.AndroidJUnitRunner"
        flavorDimensions += "free"
    }

    compileOptions {
        sourceCompatibility = JavaVersion.VERSION_25
        targetCompatibility = JavaVersion.VERSION_25
    }

    splits {
        abi {
            isEnable = true
            reset()

            // https://github.com/guardianproject/orbot-android/issues/1565
            // include("armeabi-v7a", "arm64-v8a")

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
            proguardFiles(
                getDefaultProguardFile("proguard-android-optimize.txt"), "proguard-rules.txt"
            )
            signingConfig = signingConfigs.getByName("release")
        }
        getByName("debug") {
            isDebuggable = true
            applicationIdSuffix = ".debug"
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
    tasks.named { it == "mergeNightlyDebugAssets" || it == "mergeFullpermDebugAssets" }
        .configureEach {
            mustRunAfter(updateBuiltinBridges)
        }
}

val copyLicenseToAssets by tasks.registering(Copy::class) {
    from(rootProject.file("LICENSE"))
    into(layout.projectDirectory.dir("src/main/assets"))
}
