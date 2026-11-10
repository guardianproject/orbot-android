plugins {
    alias(libs.plugins.android.application) apply false
    alias(libs.plugins.compose.compiler) apply false
    alias(libs.plugins.kotlin.serialization) apply false
    alias(libs.plugins.version.catalog.update)
}

allprojects {
    tasks.withType<JavaCompile> {
        options.compilerArgs.addAll(
            arrayOf(
                "-parameters",
                "-Xlint:deprecation",
                "-Xlint:unchecked"
            )
        )
    }
}
