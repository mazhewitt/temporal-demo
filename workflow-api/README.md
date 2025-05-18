# Workflow API Module

This module defines the interfaces and data models for the structured product order workflow.

## Overview

The workflow API module contains:
- Data models for order processing
- Workflow interface definition
- Activity interface definitions
- Activity implementations (stub versions)

## Key Components

### Data Models

#### `StructuredProductOrder`

```kotlin
data class StructuredProductOrder(
    val orderId: String,
    val productType: String,
    val quantity: Int,
    val client: String
)
```

This class represents a structured product order with the following properties:
- `orderId`: Unique identifier for the order
- `productType`: Type of structured product (e.g., "Equity Swap")
- `quantity`: Number of units
- `client`: Client identifier

#### `OrderStepResult`

```kotlin
data class OrderStepResult(
    val success: Boolean,
    val message: String
)
```

This class represents the result of each workflow step with:
- `success`: Whether the step was successful
- `message`: A descriptive message about the result

### Workflow Interface

```kotlin
@WorkflowInterface
interface OrderWorkflow {
    @WorkflowMethod
    fun processOrder(order: StructuredProductOrder): String
}
```

The `OrderWorkflow` defines a single workflow method that processes an order and returns a result string.

### Activities Interface

```kotlin
@ActivityInterface
interface OrderActivities {
    @ActivityMethod
    fun validateOrder(order: StructuredProductOrder): OrderStepResult

    @ActivityMethod
    fun priceOrder(order: StructuredProductOrder): OrderStepResult

    @ActivityMethod
    fun executeOrder(order: StructuredProductOrder): OrderStepResult

    @ActivityMethod
    fun bookOrder(order: StructuredProductOrder): OrderStepResult
}
```

The `OrderActivities` interface defines four activities that represent the stages of order processing:
1. `validateOrder`: Validates the order details
2. `priceOrder`: Prices the order
3. `executeOrder`: Executes the order in the market
4. `bookOrder`: Books the order in the system

### Activity Implementation

The module includes a stub implementation of activities for testing purposes:

```kotlin
class OrderActivitiesImpl : OrderActivities {
    override fun validateOrder(order: StructuredProductOrder): OrderStepResult {
        // Stub: always succeed
        return OrderStepResult(true, "Order validated for ${order.productType}")
    }

    override fun priceOrder(order: StructuredProductOrder): OrderStepResult {
        // Stub: always succeed
        return OrderStepResult(true, "Order priced: $1000 for ${order.quantity} units")
    }

    override fun executeOrder(order: StructuredProductOrder): OrderStepResult {
        // Stub: always succeed
        return OrderStepResult(true, "Order executed for client ${order.client}")
    }

    override fun bookOrder(order: StructuredProductOrder): OrderStepResult {
        // Stub: always succeed
        return OrderStepResult(true, "Order booked with ID ${order.orderId}")
    }
}
```

## Building

```bash
./gradlew :workflow-api:build
```

## Usage

This module is used by both the worker and client modules:
- The client uses the interfaces to create workflow stubs
- The worker implements the workflow logic using these interfaces
