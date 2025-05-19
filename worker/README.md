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
        OrderActivities::class.java,
        ActivityOptions.newBuilder()
            .setStartToCloseTimeout(Duration.ofSeconds(30))
            .build()
    )

    private var currentQuote: PriceQuote? = null
    private var quoteAccepted = false
    private var quoteRejected = false

    override fun processOrder(order: StructuredProductOrder): String {
        // Validate the order
        val validation = activities.validateOrder(order)
        if (!validation.success) return "Validation failed: ${validation.message}"
        
        // Create a quote for the order
        currentQuote = activities.createQuote(order)
        Workflow.getLogger(this.javaClass).info("Quote created: $${currentQuote?.price} for order ${order.orderId}")
        
        // Wait for client response or timeout
        val quoteTimeout = Duration.ofMinutes(15)
        Workflow.await(quoteTimeout, {
            quoteAccepted || quoteRejected || isQuoteExpired()
        })
        
        // Further processing based on client response
        // ...
    }
}
```

The workflow implementation:
1. Creates activity stubs with proper timeout configuration
2. Manages the RFQ (Request For Quote) workflow with the following steps:
   - Validate the order
   - Create a price quote
   - Wait for client acceptance/rejection or quote expiry
   - Execute and book the order if accepted
3. Uses Temporal signals (`acceptQuote`/`rejectQuote`) for client interactions
4. Provides query capabilities to check quote status

### Key Temporal Patterns in the Workflow

The `OrderWorkflowImpl` demonstrates several important Temporal patterns for trading applications:

#### 1. Quote Expiry with Temporal Timers

```kotlin
private fun isQuoteExpired(): Boolean {
    if (currentQuote == null) return false
    return System.currentTimeMillis() > currentQuote!!.expiresAt
}

// Using temporal's await with a condition that checks for expiry
Workflow.await(
    quoteTimeout, 
    { quoteAccepted || quoteRejected || isQuoteExpired() }
)

// After await completes, handling the different outcomes
if (quoteAccepted) {
    // Process accepted quote
} else if (quoteRejected) {
    return "Quote rejected by client"
} else if (isQuoteExpired()) {
    return "Quote expired without client action"
}
```

#### 2. Signal Handling for Client Interactions

```kotlin
// Signal methods
@Override
fun acceptQuote() {
    if (currentQuote == null || isQuoteExpired()) {
        throw IllegalStateException("Cannot accept: quote is null or expired")
    }
    quoteAccepted = true
}

@Override
fun rejectQuote() {
    if (currentQuote == null) {
        throw IllegalStateException("Cannot reject: no quote exists")
    }
    quoteRejected = true
}

// Main workflow method listens for these signals using Workflow.await
```

#### 3. Query Methods for Status Checks

```kotlin
@Override
@QueryMethod
fun getQuoteStatus(): PriceQuote? {
    // Update expiry status before returning
    if (currentQuote != null) {
        currentQuote = currentQuote!!.copy(
            isExpired = isQuoteExpired()
        )
    }
    return currentQuote
}
```

#### 4. Activity Execution with Proper Error Handling

```kotlin
// Activities with error handling in workflow
try {
    val executionResult = activities.executeOrder(order, currentQuote!!)
    if (!executionResult.success) {
        return "Order execution failed: ${executionResult.message}"
    }
    
    val bookingResult = activities.bookOrder(executionResult)
    if (!bookingResult.success) {
        return "Order booking failed: ${bookingResult.message}"
    }
    
    return "Order completed successfully"
} catch (e: Exception) {
    return "Error processing order: ${e.message}"
}
```

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

## Testing the Worker

To test the worker implementations without relying on the actual Temporal service, the project provides test implementations:

```kotlin
// Test workflow implementation
class TestOrderWorkflowImpl : OrderWorkflow {
    private val activities = Workflow.newActivityStub(
        OrderActivities::class.java,
        ActivityOptions.newBuilder()
            .setStartToCloseTimeout(Duration.ofSeconds(5))
            .build()
    )
    
    private var currentQuote: PriceQuote? = null
    private var quoteAccepted = false
    private var quoteRejected = false
    
    override fun processOrder(order: StructuredProductOrder): String {
        // Simplified test implementation that focuses on the essential flow
        currentQuote = activities.createQuote(order)
        
        // Wait for signal
        Workflow.await(Duration.ofSeconds(5), { quoteAccepted || quoteRejected })
        
        return if (quoteAccepted) "Order completed successfully" else "Quote rejected"
    }
    
    // Signal and query implementations...
}

// Test activity implementation
class TestOrderActivitiesImpl : OrderActivities {
    override fun validateOrder(order: StructuredProductOrder): ValidationResult {
        return ValidationResult(true, "")
    }
    
    override fun createQuote(order: StructuredProductOrder): PriceQuote {
        return PriceQuote(
            orderId = order.orderId,
            price = 500.0, 
            expiresAt = System.currentTimeMillis() + 900000, // 15 minutes
            isExpired = false
        )
    }
    
    // Other activity method implementations...
}
```

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
