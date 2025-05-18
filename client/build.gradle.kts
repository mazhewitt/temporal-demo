plugins {
    kotlin("jvm") version "2.0.0"
    application
}

val temporalVersion = "1.23.0"

dependencies {
    implementation(project(":workflow-api"))
    implementation("io.temporal:temporal-sdk:$temporalVersion")
    implementation("org.jetbrains.kotlinx:kotlinx-coroutines-core:1.7.3")
    implementation("org.slf4j:slf4j-simple:2.0.7")
}

application {
    mainClass.set("com.example.client.ClientAppKt")
}