package com.example.client

import io.temporal.client.WorkflowClient
import io.temporal.serviceclient.WorkflowServiceStubs
import io.temporal.serviceclient.WorkflowServiceStubsOptions
import io.temporal.client.WorkflowOptions
import workflow_api.OrderWorkflow
import workflow_api.StructuredProductOrder
import java.util.UUID

fun main() {
    // Connect to Temporal service with explicit target
    val service = WorkflowServiceStubs.newInstance(
        WorkflowServiceStubsOptions.newBuilder()
            .setTarget("127.0.0.1:7233")
            .build()
    )
    val client = WorkflowClient.newInstance(service)
    val taskQueue = "ORDER_TASK_QUEUE"

    // Create a sample order
    val order = StructuredProductOrder(
        orderId = UUID.randomUUID().toString(),
        productType = "Equity Swap",
        quantity = 10,
        client = "TestClient"
    )

    // Create workflow stub
    val workflow: OrderWorkflow = client.newWorkflowStub(
        OrderWorkflow::class.java,
        WorkflowOptions.newBuilder()
            .setTaskQueue(taskQueue)
            .build()
    )

    // Start workflow asynchronously and wait with a timeout
    println("Starting workflow execution...")
    val future = WorkflowClient.execute(workflow::processOrder, order)
    println("Workflow started. Waiting for result with a timeout of 60 seconds...")
    
    try {
        // Wait for the result with a timeout
        val result = future.get(60, java.util.concurrent.TimeUnit.SECONDS)
        println("Workflow execution completed successfully.")
        println("Workflow result: $result")
    } catch (e: java.util.concurrent.TimeoutException) {
        println("ERROR: Workflow execution timed out after 60 seconds.")
        println("The workflow may still be running in the background.")
        System.exit(1) // Exit with error code
    } catch (e: Exception) {
        println("ERROR: Workflow execution failed: ${e.message}")
        e.printStackTrace()
        System.exit(2) // Exit with different error code
    }
}
