# Temporal Workflow Example - Trading System

This project demonstrates a structured product order processing workflow using [Temporal](https://temporal.io/), a microservice orchestration platform. The workflow processes a structured product order through multiple stages: validation, pricing, execution, and booking, showcasing how to use Temporal for trading use cases.

## Project Structure

- **[workflow-api](./workflow-api/README.md)**: Contains the API interfaces and data models for the workflow
- **[worker](./worker/README.md)**: Implements the workflow logic and activities
- **[client](./client/README.md)**: Web application with Spring Boot and React frontend for submitting and managing orders


## Prerequisites

- JDK 21 or higher
- Kubernetes cluster (local like Minikube, Docker Desktop, or Kind)
- Helm

## Getting Started

### 1. Install Temporal

The project includes a setup script to install Temporal in your Kubernetes cluster:

```bash
# Make the script executable if needed
chmod +x ./temporal_setup/install-temporal.sh

# Run the setup script
./temporal_setup/install-temporal.sh
```

This script will:
- Install Temporal via Helm in the `temporal` namespace
- Set up port forwarding:
  - Temporal Frontend: `localhost:7233`
  - Temporal Web UI: `http://localhost:8080`

### 2. Build the Project

Build all components with Gradle:

```bash
./gradlew build
```

### 3. Running the Web Application

You can run the complete web application (both Spring Boot backend and React frontend) with a single command using our convenience script:

```bash
./start-application.sh
```

This script will:
1. Check if Temporal is running and try to start it if needed
2. Start the worker process in the background
3. Start the Spring Boot web application

Once running, you can access:
- Web UI: http://localhost:8081
- Temporal UI: http://localhost:8080
- REST API: http://localhost:8081/api

## How to Use Temporal for Trading Workflows

This project demonstrates several key Temporal concepts in the context of a trading application:

### 1. Workflow Definition

The workflow API (`OrderWorkflow` interface) defines the contract for the trading workflow:

```kotlin
interface OrderWorkflow {
    // Main workflow method - processes an order from submission to completion
    fun processOrder(order: StructuredProductOrder): String
    
    // Signal methods for client interactions
    @SignalMethod
    fun acceptQuote()
    
    @SignalMethod
    fun rejectQuote()
    
    // Query method to check the current quote status
    @QueryMethod
    fun getQuoteStatus(): PriceQuote?
}
```

### 2. Workflow Implementation

The implementation (`OrderWorkflowImpl`) shows how to:

- Create activity stubs with timeout configurations
- Implement a multi-step workflow with decision points
- Use signals for client interactions (accept/reject quotes)
- Use queries to expose workflow state (quote status)
- Handle timeouts and expirations

Key features:
```kotlin
// Creating activity stubs with timeout configuration
private val activities = Workflow.newActivityStub(
    OrderActivities::class.java,
    ActivityOptions.newBuilder()
        .setStartToCloseTimeout(Duration.ofSeconds(30))
        .build()
)

// Waiting for client decision or timeout
Workflow.await(quoteTimeout, {
    quoteAccepted || quoteRejected || isQuoteExpired()
})

// Signal method implementation
override fun acceptQuote() {
    quoteAccepted = true
}
```

### 3. Activities Definition

Activities represent the actual business logic executed by the workflow:

```kotlin
interface OrderActivities {
    fun validateOrder(order: StructuredProductOrder): ValidationResult
    fun createQuote(order: StructuredProductOrder): PriceQuote
    fun executeOrder(order: StructuredProductOrder, quote: PriceQuote): ExecutionResult
    fun bookOrder(executionResult: ExecutionResult): BookingResult
}
```

### 4. Client Integration

The client (`OrderService`) shows how to:

- Connect to Temporal service
- Start workflows with options
- Signal running workflows
- Query workflow state
- Handle workflow errors with the Result pattern

Example:
```kotlin
// Starting a workflow
val workflow = workflowClient.newWorkflowStub(
    OrderWorkflow::class.java,
    WorkflowOptions.newBuilder()
        .setTaskQueue(taskQueueName)
        .setWorkflowId("order-${order.orderId}")
        .build()
)
val execution = WorkflowClient.start(workflow::processOrder, workflowOrder)

// Querying workflow status
val workflow = workflowClient.newWorkflowStub(OrderWorkflow::class.java, workflowId)
val quote = workflow.getQuoteStatus()

// Signaling a workflow
val workflow = workflowClient.newWorkflowStub(OrderWorkflow::class.java, workflowId)
workflow.acceptQuote()
```

## Testing Temporal Workflows

This project showcases different testing approaches for Temporal workflows:

### 1. Unit Testing with Test Environment

The `OrderServiceTest` demonstrates how to:

- Create mock objects for Temporal clients
- Use inheritance and overriding for testability
- Test error conditions with custom error types
- Use the Result pattern to handle and assert errors

Example of testable service design:
```kotlin
// Protected helper methods for better testability
protected open fun getQuoteFromWorkflow(workflowId: String): PriceQuote? {
    val workflow = workflowClient.newWorkflowStub(OrderWorkflow::class.java, workflowId)
    val workflowStubImpl = WorkflowStub.fromTyped(workflow)
    return workflowStubImpl.query("getQuoteStatus", PriceQuote::class.java, 5, TimeUnit.SECONDS)
}

// In tests, create a subclass that overrides these methods
val testOrderService = object : OrderService(workflowClient, taskQueueName, timeoutSeconds) {
    override fun getQuoteFromWorkflow(workflowId: String): PriceQuote? {
        return sampleQuote
    }
}
```

### 2. Integration Testing with TestWorkflowEnvironment

The `OrderWorkflowE2ETest` shows how to:

- Create a test workflow environment
- Register test implementations of workflows and activities
- Test the entire workflow execution including signals
- Use time skipping for testing time-dependent logic
- Assert workflow results

Example:
```kotlin
// Setup test environment
testEnv = TestWorkflowEnvironment.newInstance()
client = testEnv.workflowClient
val worker = testEnv.newWorker(taskQueue)
worker.registerWorkflowImplementationTypes(TestOrderWorkflowImpl::class.java)
worker.registerActivitiesImplementations(TestOrderActivitiesImpl())
testEnv.start()

// Start workflow and test signals
val workflow: OrderWorkflow = client.newWorkflowStub(OrderWorkflow::class.java, options)
WorkflowClient.start(workflow::processOrder, order)
testEnv.sleep(java.time.Duration.ofMillis(500)) // Ensure workflow has processed initial steps
workflow.acceptQuote() // Send signal
val result = workflowStub.getResult(5, TimeUnit.SECONDS, String::class.java)
```

### 3. Error Handling Testing with Spring

The `QuoteErrorHandlingTest` demonstrates:

- Testing expired quotes and error conditions
- Using Spring's test infrastructure for API endpoint testing
- Mocking service responses to simulate errors
- Testing API responses for different error types

Example:
```kotlin
// Mock expired quote scenario
val expiredQuote = QuoteResponse(
    orderId = orderId,
    price = 1000.0,
    expiresAt = System.currentTimeMillis() - 1000,
    isExpired = true
)

Mockito.`when`(orderService.acceptQuote(orderId)).thenReturn(
    Result.failure(OrderError.QuoteExpired("Quote expired", orderId, expiredQuote.expiresAt))
)

// Test API response
val acceptResponse = restTemplate.postForEntity(
    "$baseUrl/orders/$orderId/accept",
    HttpEntity<Any>(headers),
    Map::class.java
)
Assertions.assertEquals(HttpStatus.BAD_REQUEST, acceptResponse.statusCode)
```

### 4. Result Pattern for Error Handling

The application uses the Kotlin `Result` type to improve null safety and error handling:

```kotlin
// Using Result in service layer
return try {
    val workflow = workflowClient.newWorkflowStub(OrderWorkflow::class.java, workflowId)
    workflow.acceptQuote()
    Result.success(true)
} catch (e: Exception) {
    Result.failure(mapWorkflowException(e, workflowId))
}

// Using fold for concise handling in controller
return orderService.acceptQuote(orderId).fold(
    onSuccess = {
        ResponseEntity.ok(mapOf("message" to "Quote accepted successfully"))
    },
    onFailure = { error ->
        handleOrderError(error)
    }
)
```

## Running the Tests

To run all tests:
```bash
./gradlew test
```

To run a specific test class:
```bash
./gradlew test --tests "com.example.client.OrderWorkflowE2ETest"
```

For detailed information on testing approaches and patterns used in this project, see the [Testing Documentation](./TESTING.md).

## Best Practices Demonstrated

1. **Separation of API from Implementation**: Using the workflow-api module to define interfaces used by both client and worker.

2. **Error Handling with Result**: Using Kotlin's Result type for improved error handling and null safety.

3. **Testability**: Using protected methods and inheritance to make services more testable.

4. **End-to-End Testing**: Using TestWorkflowEnvironment for complete workflow tests.

5. **Signals and Queries**: Using Temporal's signal and query features for client interactions.

6. **Timeouts and Retries**: Configuring proper timeouts for activities and workflows.

7. **Web Frontend Integration**: Integrating with a React frontend for a complete trading application experience.

For more details on specific modules, refer to their individual README files:
- [Client Module Documentation](./client/README.md)
- [Worker Module Documentation](./worker/README.md)
- [Workflow API Documentation](./workflow-api/README.md)
   - Verifies that the workflow returns a non-null result
   - Confirms that the result contains "Order workflow completed successfully"
   - Validates that the order was successfully booked with the correct order ID
   - Checks that important order data (client information, order ID) appears in the result
   - Handles timeouts gracefully for long-running workflows

The test has an explicit 10-second timeout annotation using JUnit's `@Timeout`. If the workflow takes longer than 8 seconds to complete, the test will handle the timeout gracefully rather than failing.

## RFQ Workflow Implementation

The RFQ (Request for Quote) workflow implemented in this project follows a standard client-server negotiation pattern common in financial trading systems. Here's how it works:

### Workflow Steps

1. **Order Submission**
   - Client submits a structured product order with details (product type, quantity, client info)
   - The workflow instance starts with this order as input

2. **Order Validation**
   - The workflow first validates the order using the `validateOrder` activity
   - Basic checks ensure the order meets business requirements

3. **Quote Generation**
   - If validation passes, the workflow creates a price quote using the `createQuote` activity
   - Quote includes: price based on quantity, order ID, and expiration time (15 minutes)

4. **Client Decision Wait Period**
   - The workflow enters a waiting state where it:
     - Responds to query requests with `getQuoteStatus()`
     - Listens for acceptance/rejection signals
     - Has a timeout for quote expiration

5. **Quote Resolution**
   - The workflow can end in three ways:
     - Client accepts quote via `acceptQuote()` signal → proceed to execution
     - Client rejects quote via `rejectQuote()` signal → end workflow
     - Quote expires without client action → end workflow

6. **Order Execution and Booking** (only if quote accepted)
   - If quote is accepted, workflow calls `executeOrder()` with the original order and quote
   - After execution, the order is booked with `bookOrder()` 
   - Workflow completes with success message

### Key Components

1. **Data Classes**:
   - `StructuredProductOrder`: Represents client order details
   - `PriceQuote`: Contains price, order ID, and expiration time
   - `OrderStepResult`: Standard result format for activities

2. **Workflow Interface**:
   - `processOrder`: Main workflow method
   - `getQuoteStatus`: Query method to check current quote
   - `acceptQuote` and `rejectQuote`: Signal methods for client decisions

3. **Activity Interface**:
   - Activities for each step, properly isolated for independent scaling

### Client Interaction Pattern

1. Client starts workflow with order details
2. Client polls `getQuoteStatus()` to get quote information
3. Client makes decision:
   - Calls `acceptQuote()` to accept
   - Calls `rejectQuote()` to reject
4. Client can check final result with workflow completion

## Key Implementation Details

### Data Class Serialization

When using Kotlin data classes with Temporal workflows, proper serialization is crucial. This project uses Jackson annotations to ensure correct serialization:

```kotlin
data class StructuredProductOrder @JsonCreator constructor(
    @JsonProperty("orderId") val orderId: String,
    @JsonProperty("productType") val productType: String,
    @JsonProperty("quantity") val quantity: Int,
    @JsonProperty("client") val client: String
)
```

All data classes that are passed between workflow and activities need these annotations to ensure proper serialization/deserialization.

### Activity Timeout Configuration

Always configure timeouts for activities to ensure they terminate properly:

```kotlin
private val activities = Workflow.newActivityStub(
    OrderActivities::class.java,
    ActivityOptions.newBuilder()
        .setStartToCloseTimeout(Duration.ofSeconds(30))
        .build()
)
```

## Monitoring

You can monitor workflow executions through the Temporal Web UI at http://localhost:8080

## Troubleshooting

If you encounter issues with port forwarding or connecting to Temporal:

1. Verify that Temporal is running in your Kubernetes cluster:
   ```bash
   kubectl -n temporal get pods
   ```

2. Check port forwarding status:
   ```bash
   ps aux | grep "port-forward"
   ```

3. Common workflow errors and solutions:
   - **Serialization errors**: Ensure all data classes have appropriate Jackson annotations
   - **Activity timeout errors**: Configure proper timeout settings for all activities
   - **Worker not processing tasks**: Verify that the worker is running and targeting the correct task queue

3. Restart port forwarding if needed:
   ```bash
   # Kill existing port forwards
   pkill -f "kubectl -n temporal port-forward"
   
   # Restart port forwarding
   kubectl -n temporal port-forward svc/temporaltest-frontend 7233:7233 &
   kubectl -n temporal port-forward svc/temporaltest-web 8080:8080 &
   ```

## License

This project is for demonstration purposes.


## Detailed Setup Guide

For detailed setup instructions and development workflows, see [SETUP.md](./SETUP.md).
