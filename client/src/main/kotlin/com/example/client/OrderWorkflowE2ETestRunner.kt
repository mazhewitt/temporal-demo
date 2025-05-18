package com.example.client

import io.temporal.client.WorkflowClient
import io.temporal.serviceclient.WorkflowServiceStubs
import io.temporal.serviceclient.WorkflowServiceStubsOptions
import io.temporal.client.WorkflowOptions
import workflow_api.OrderWorkflow
import workflow_api.StructuredProductOrder
import java.util.UUID

/**
 * DEPRECATED: This main function has been refactored into a proper JUnit test.
 * Please use the OrderWorkflowE2ETest class in the test directory instead.
 * 
 * @see com.example.client.OrderWorkflowE2ETest
 */
fun main() {
    println("This runner has been deprecated. Please use the OrderWorkflowE2ETest class instead.")
    println("Run with: ./gradlew :client:test --tests \"com.example.client.OrderWorkflowE2ETest\"")
    
    // The implementation has been moved to the test class
}
