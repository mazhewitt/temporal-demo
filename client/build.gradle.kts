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
    testImplementation("org.junit.jupiter:junit-jupiter-api:5.10.0")
    testRuntimeOnly("org.junit.jupiter:junit-jupiter-engine:5.10.0")
    testImplementation("org.assertj:assertj-core:3.24.2")
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