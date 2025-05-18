package com.example.client

import io.temporal.client.WorkflowClient
import io.temporal.serviceclient.WorkflowServiceStubs
import io.temporal.serviceclient.WorkflowServiceStubsOptions
import io.temporal.client.WorkflowOptions
import workflow_api.OrderWorkflow
import workflow_api.StructuredProductOrder
import java.util.UUID

fun main() {
    // Connect to Temporal service (localhost port-forwarded to Kubernetes)
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

    // Start workflow and get result
    val result = workflow.processOrder(order)
    println("Workflow result: $result")
}
