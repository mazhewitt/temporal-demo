plugins {
    kotlin("jvm") version "2.0.0"
}

repositories {
    mavenCentral()
}

val temporalVersion = "1.23.0"

dependencies {
    implementation("io.temporal:temporal-sdk:$temporalVersion")
    implementation("org.jetbrains.kotlinx:kotlinx-coroutines-core:1.7.3")
}