# Order Management System Setup Guide

This guide provides detailed instructions for setting up and running the Order Management System, which consists of a Spring Boot backend, React frontend, and Temporal workflows.

## Prerequisites

- JDK 21 or higher
- Node.js 16+ and npm
- Docker for running Temporal (or a Kubernetes cluster)
- Git (for version control)

## Step 1: Clone the Repository

```bash
git clone <repository-url>
cd temporal_exp
```

## Step 2: Set Up Temporal

### Option 1: Using Docker (Recommended for Development)

```bash
docker run -d --name temporal \
  -p 7233:7233 \
  -p 8080:8080 \
  temporalio/auto-setup:latest
```

### Option 2: Using Kubernetes

```bash
# Make the script executable
chmod +x ./temporal_setup/install-temporal.sh

# Run the setup script
./temporal_setup/install-temporal.sh

# Forward ports
kubectl port-forward service/temporal-frontend 7233:7233 -n temporal &
kubectl port-forward service/temporal-web 8080:8080 -n temporal &
```

## Step 3: Build the Project

```bash
./gradlew build
```

## Step 4: Start the Application

### Option 1: Using the Convenience Script (Recommended)

```bash
./start-application.sh
```

### Option 2: Starting Components Manually

In separate terminal windows:

#### Start the Worker
```bash
./gradlew :worker:run
```

#### Start the Backend API
```bash
./gradlew :client:bootRun
```

#### Start the React Frontend (Development Mode)
```bash
cd client/frontend
npm install
npm run dev
```

## Accessing the Application

- Web UI: http://localhost:8081 (production) or http://localhost:5173 (development)
- Temporal UI: http://localhost:8080
- REST API: http://localhost:8081/api

## API Endpoints

| Method | Endpoint | Description |
|--------|----------|-------------|
| POST | /api/orders | Submit a new order |
| GET | /api/orders | List all orders |
| GET | /api/orders/{orderId} | Get order status |
| GET | /api/orders/{orderId}/quote | Get quote for an order |
| POST | /api/orders/{orderId}/accept | Accept a quote |
| POST | /api/orders/{orderId}/reject | Reject a quote |
| GET | /api/health | Health check |

## Development Workflow

### Backend Development

1. Make changes to Kotlin files
2. Restart the Spring Boot application (`./gradlew :client:bootRun`)
3. Test your changes using the REST API or the frontend

### Frontend Development

1. Make changes to React files in `client/frontend/src/`
2. The development server will automatically reload with your changes
3. For production builds, run `npm run build` in the frontend directory

## Running Tests

```bash
# Run all tests
./gradlew test

# Run specific tests
./gradlew :client:test --tests "com.example.client.OrderWorkflowE2ETest"
```

## Troubleshooting

### Common Issues

1. **Temporal connection issues**
   - Check if Temporal is running: `docker ps` or `kubectl get pods -n temporal`
   - Verify port forwarding: `curl http://localhost:8080`

2. **Backend startup failures**
   - Check application logs
   - Verify required environment variables are set

3. **Frontend build errors**
   - Ensure Node.js and npm are installed
   - Run `npm install` to update dependencies

## Architecture Overview

This application demonstrates a full-stack implementation of a workflow-based order processing system:

1. The frontend allows users to submit orders and manage quotes
2. The Spring Boot backend provides REST APIs for order management
3. Temporal workflows handle the business logic for order processing, quoting, and fulfillment
4. The system provides real-time updates on order status and quotes

## Development Best Practices

### Using the Result Pattern

This project uses Kotlin's Result pattern to handle errors in a type-safe way when interacting with Temporal workflows. Here's how to use it:

1. **Service methods should return Result<T>**:

```kotlin
fun getOrderStatus(orderId: String): Result<OrderStatusResponse> {
    val workflowId = getWorkflowIdForOrder(orderId) ?: return Result.failure(
        OrderError.OrderNotFound("Order not found with ID: $orderId", orderId)
    )
    
    return try {
        val workflow = workflowClient.newWorkflowStub(OrderWorkflow::class.java, workflowId)
        val status = getWorkflowStatus(workflowId)
        val quote = getQuoteFromWorkflow(workflowId)
        
        Result.success(OrderStatusResponse(
            orderId = orderId,
            workflowId = workflowId,
            status = status,
            quote = quote?.let { mapToQuoteResponse(it) }
        ))
    } catch (e: Exception) {
        Result.failure(mapWorkflowException(e, orderId))
    }
}
```

2. **Controllers should use fold for handling success/failure**:

```kotlin
@GetMapping("/orders/{orderId}")
fun getOrderStatus(@PathVariable orderId: String): ResponseEntity<Any> {
    return orderService.getOrderStatus(orderId).fold(
        onSuccess = { statusResponse -> 
            ResponseEntity.ok(statusResponse) 
        },
        onFailure = { error -> 
            handleOrderError(error)
        }
    )
}
```

3. **Create custom error types with a sealed class**:

```kotlin
sealed class OrderError(
    message: String,
    val orderId: String
) : Exception(message) {
    class OrderNotFound(message: String, orderId: String) : OrderError(message, orderId)
    class WorkflowNotFound(message: String, orderId: String) : OrderError(message, orderId)
    class QuoteExpired(message: String, orderId: String, val expiryTime: Long) : OrderError(message, orderId)
    // Other specific error types...
    class Unknown(message: String, orderId: String) : OrderError(message, orderId)
}
```

4. **Map external exceptions to domain errors**:

```kotlin
private fun mapWorkflowException(e: Exception, orderId: String): OrderError {
    return when (e) {
        is WorkflowNotFoundException -> OrderError.WorkflowNotFound(
            "Workflow not found for order ID: $orderId", orderId
        )
        is WorkflowExecutionAlreadyCompletedException -> OrderError.WorkflowCompleted(
            "Workflow already completed for order ID: $orderId", orderId
        )
        is IllegalStateException -> {
            if (e.message?.contains("expired", ignoreCase = true) == true) {
                OrderError.QuoteExpired(e.message ?: "Quote expired", orderId, System.currentTimeMillis())
            } else {
                OrderError.Unknown(e.message ?: "Unknown error", orderId)
            }
        }
        else -> OrderError.Unknown("Unexpected error: ${e.message}", orderId)
    }
}
```

5. **Use custom assertions in tests**:

```kotlin
fun <T> Result<T>.assertSuccess(): T {
    assertTrue(isSuccess, {
        val error = exceptionOrNull()
        "Expected Result to be successful but was failure with: ${error?.message ?: error}"
    })
    return getOrThrow()
}

fun <T> Result<T>.assertFailure(): Throwable {
    assertTrue(isFailure, "Expected Result to be failure but was success with: ${getOrNull()}")
    return exceptionOrNull()!!
}
```

For more details on testing with the Result pattern, see the [Testing Documentation](./TESTING.md).
