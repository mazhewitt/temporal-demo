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
    
    // Test dependencies
    testImplementation("org.junit.jupiter:junit-jupiter:5.10.0")  // Use the BOM instead of individual artifacts
    testImplementation("org.assertj:assertj-core:3.24.2")
    // Add explicit test framework implementation dependency to avoid deprecation warning
    testRuntimeOnly("org.junit.platform:junit-platform-launcher:1.10.0")
}

application {
    mainClass.set("com.example.client.OrderWorkflowE2ETestRunnerKt")
}

tasks.test {
    useJUnitPlatform()
    testLogging {
        events("passed", "skipped", "failed")
    }
}