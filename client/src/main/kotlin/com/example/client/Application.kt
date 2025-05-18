package com.example.client

import org.slf4j.LoggerFactory
import org.springframework.boot.autoconfigure.SpringBootApplication
import org.springframework.boot.runApplication
import org.springframework.core.env.Environment
import org.springframework.boot.CommandLineRunner
import org.springframework.context.annotation.Bean

@SpringBootApplication
class OrderServiceApplication {
    private val logger = LoggerFactory.getLogger(OrderServiceApplication::class.java)
    
    @Bean
    fun commandLineRunner(environment: Environment) = CommandLineRunner {
        val port = environment.getProperty("server.port", "8081")
        logger.info("Application started successfully!")
        logger.info("Web UI available at: http://localhost:$port")
        logger.info("REST API available at: http://localhost:$port/api")
        logger.info("Temporal UI available at: http://localhost:8080")
    }
}

fun main(args: Array<String>) {
    runApplication<OrderServiceApplication>(*args)
}
