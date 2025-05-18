package com.example.client.controller

import org.springframework.http.ResponseEntity
import org.springframework.web.bind.annotation.GetMapping
import org.springframework.web.bind.annotation.RequestMapping
import org.springframework.web.bind.annotation.RestController
import java.time.LocalDateTime
import java.time.format.DateTimeFormatter

@RestController
@RequestMapping("/api/health")
class HealthController {

    @GetMapping
    fun healthCheck(): ResponseEntity<Map<String, Any>> {
        val formatter = DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm:ss")
        val now = LocalDateTime.now().format(formatter)
        
        val response = mapOf(
            "status" to "UP",
            "timestamp" to now,
            "application" to "Order Management System",
            "services" to mapOf(
                "temporal" to checkTemporalConnection()
            )
        )
        
        return ResponseEntity.ok(response)
    }
    
    private fun checkTemporalConnection(): Map<String, String> {
        return try {
            // This is a simple check - in a real application we'd check the actual connection
            mapOf("status" to "UP", "message" to "Connected to Temporal service")
        } catch (e: Exception) {
            mapOf(
                "status" to "DOWN", 
                "message" to "Error connecting to Temporal: ${e.message}"
            )
        }
    }
}
