package com.example.worker

import io.temporal.client.WorkflowClient
import io.temporal.serviceclient.WorkflowServiceStubs
import io.temporal.serviceclient.WorkflowServiceStubsOptions
import io.temporal.worker.WorkerFactory
import io.temporal.worker.Worker
import workflow_api.OrderWorkflow
import workflow_api.OrderActivitiesImpl

fun main() {
    // Temporal service connection (localhost port-forwarded to Kubernetes)
    val service = WorkflowServiceStubs.newServiceStubs(
        WorkflowServiceStubsOptions.newBuilder()
            .setTarget("127.0.0.1:7233")
            .build()
    )
    val client = WorkflowClient.newInstance(service)
    val taskQueue = "ORDER_TASK_QUEUE"

    val factory = WorkerFactory.newInstance(client)
    val worker: Worker = factory.newWorker(taskQueue)

    worker.registerWorkflowImplementationTypes(com.example.worker.OrderWorkflowImpl::class.java)
    worker.registerActivitiesImplementations(OrderActivitiesImpl())

    factory.start()
    println("Worker started for task queue: $taskQueue")
}
