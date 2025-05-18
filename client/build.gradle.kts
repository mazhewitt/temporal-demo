plugins {
    kotlin("jvm") version "2.0.0"
    kotlin("plugin.spring") version "2.0.0"
    id("org.springframework.boot") version "3.2.3"
    id("io.spring.dependency-management") version "1.1.4"
    application
}

val temporalVersion = "1.23.0"

dependencies {
    implementation(project(":workflow-api"))
    implementation("io.temporal:temporal-sdk:$temporalVersion")
    implementation("org.jetbrains.kotlinx:kotlinx-coroutines-core:1.7.3")
    
    // Spring Boot dependencies
    implementation("org.springframework.boot:spring-boot-starter-web")
    implementation("org.springframework.boot:spring-boot-starter-validation")
    implementation("com.fasterxml.jackson.module:jackson-module-kotlin")
    runtimeOnly("org.springframework.boot:spring-boot-devtools")
    
    // Use logback with Spring instead of slf4j-simple
    implementation("ch.qos.logback:logback-classic:1.4.14")
    
    // Test dependencies
    testImplementation("org.springframework.boot:spring-boot-starter-test")
    testImplementation("org.junit.jupiter:junit-jupiter:5.10.0")
    testImplementation("org.assertj:assertj-core:3.24.2")
    testRuntimeOnly("org.junit.platform:junit-platform-launcher:1.10.0")
    
    // Temporal testing dependencies
    testImplementation("io.temporal:temporal-testing:$temporalVersion")
    
    // Worker module for the workflow implementation
    implementation(project(":worker"))
}

application {
    mainClass.set("com.example.client.ApplicationKt")
}

tasks.test {
    useJUnitPlatform()
    testLogging {
        events("passed", "skipped", "failed")
    }
}

// Tasks for the React frontend
tasks.register<Exec>("npmInstall") {
    workingDir = file("${projectDir}/frontend")
    commandLine = listOf("npm", "install")
    group = "frontend"
    description = "Install npm dependencies for the React frontend"
    
    // Make the task optional, continue on error
    isIgnoreExitValue = true
    
    doFirst {
        println("Attempting to run npm install. This is optional and will be skipped if npm is not available.")
    }
    
    // Check if npm exists before running
    onlyIf {
        try {
            val process = ProcessBuilder("which", "npm").start()
            process.waitFor() == 0
        } catch (e: Exception) {
            println("npm not found. Skipping frontend build.")
            false
        }
    }
}

tasks.register<Exec>("npmBuild") {
    workingDir = file("${projectDir}/frontend")
    commandLine = listOf("npm", "run", "build")
    group = "frontend"
    description = "Build the React frontend"
    dependsOn("npmInstall")
    
    // Make the task optional, continue on error
    isIgnoreExitValue = true
    
    // Check if npm exists before running
    onlyIf {
        try {
            val process = ProcessBuilder("which", "npm").start()
            process.waitFor() == 0
        } catch (e: Exception) {
            println("npm not found. Skipping frontend build.")
            false
        }
    }
}

// Make the dependency on npmBuild conditional
val processResourcesTask = tasks.named("processResources")
tasks.findByName("npmBuild")?.let { npmBuild ->
    processResourcesTask.configure {
        dependsOn(npmBuild)
    }
}