# Worker Module

This module implements the workflow logic and runs the worker process that executes workflow tasks.

## Overview

The worker module:
1. Implements the workflow logic defined in the workflow-api module
2. Registers the workflow and activity implementations with Temporal
3. Listens for tasks on the specified task queue

## Key Components

### Workflow Implementation

The `OrderWorkflowImpl` class implements the `OrderWorkflow` interface from the workflow-api module:

```kotlin
class OrderWorkflowImpl : OrderWorkflow {
    private val activities = Workflow.newActivityStub(
        OrderActivities::class.java
    )

    override fun processOrder(order: StructuredProductOrder): String {
        val validation = activities.validateOrder(order)
        if (!validation.success) return "Validation failed: ${validation.message}"

        val pricing = activities.priceOrder(order)
        if (!pricing.success) return "Pricing failed: ${pricing.message}"

        val execution = activities.executeOrder(order)
        if (!execution.success) return "Execution failed: ${execution.message}"

        val booking = activities.bookOrder(order)
        if (!booking.success) return "Booking failed: ${booking.message}"

        return "Order workflow completed successfully: ${booking.message}"
    }
}
```

The workflow implementation:
1. Creates activity stubs to execute the activities
2. Executes each activity in sequence: validate → price → execute → book
3. Returns immediately if any step fails
4. Returns a success message if all steps complete successfully

### Worker Application

The `WorkerApp` is the main entry point for the worker process:

```kotlin
fun main() {
    // Temporal service connection
    val service = WorkflowServiceStubs.newServiceStubs(
        WorkflowServiceStubsOptions.newBuilder()
            .setTarget("127.0.0.1:7233")
            .build()
    )
    val client = WorkflowClient.newInstance(service)
    val taskQueue = "ORDER_TASK_QUEUE"

    val factory = WorkerFactory.newInstance(client)
    val worker: Worker = factory.newWorker(taskQueue)

    worker.registerWorkflowImplementationTypes(OrderWorkflowImpl::class.java)
    worker.registerActivitiesImplementations(OrderActivitiesImpl())

    factory.start()
    println("Worker started for task queue: $taskQueue")
}
```

The worker application:
1. Connects to the Temporal service at `localhost:7233` (port-forwarded from Kubernetes)
2. Creates a worker factory and worker for the `ORDER_TASK_QUEUE`
3. Registers the workflow implementation and activity implementations
4. Starts the worker, which then polls for tasks

## Building and Running

### Build the Worker

```bash
./gradlew :worker:build
```

### Run the Worker

```bash
./gradlew :worker:run
```

The worker will start and listen for tasks on the `ORDER_TASK_QUEUE`. It will continue running until explicitly stopped.

### Worker Output

When running successfully, you should see output similar to:

```
Worker started for task queue: ORDER_TASK_QUEUE
```

When tasks are executed, additional log output will show the progress of each activity.

## Configuration

The worker connects to Temporal at `localhost:7233`, which should be port-forwarded to the Temporal service in Kubernetes (set up by the `install-temporal.sh` script).

If you need to change this or other configuration:

1. Edit the service target in `WorkerApp.kt`
2. Adjust activity timeouts or retry policies as needed

## Monitoring

You can monitor worker activities through the Temporal Web UI at http://localhost:8080
