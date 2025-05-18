package com.example.client

/**
 * DEPRECATED: This file has been moved to the proper test directory structure.
 * Please use the OrderWorkflowE2ETest class in src/test/kotlin/com/example/client/ instead.
 * 
 * @see com.example.client.OrderWorkflowE2ETest
 */

fun main() {
    println("This test has been moved to the proper test directory structure.")
    println("Please use the OrderWorkflowE2ETest class in src/test/kotlin/com/example/client/ instead.")
    println("Run with: ./gradlew :client:test --tests \"com.example.client.OrderWorkflowE2ETest\"")
}
        println("ERROR: Workflow execution failed: ${e.message}")
        e.printStackTrace()
        System.exit(2) // Exit with different error code
    }
}
