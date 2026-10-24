buildscript {
    dependencies {
        // AGP 9 built-in Kotlin defaults to KGP 2.2.10; Priv Kit 0.17.8 needs 2.4.x metadata
        classpath("org.jetbrains.kotlin:kotlin-gradle-plugin:2.4.20")
    }
}

plugins {
    alias(libs.plugins.android.application) apply false
}
