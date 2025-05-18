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
    implementation("com.fasterxml.jackson.core:jackson-annotations:2.15.3")
    implementation("com.fasterxml.jackson.module:jackson-module-kotlin:2.15.3")
}