package workflow_api

import workflow_api.StructuredProductOrder
import workflow_api.OrderStepResult
import workflow_api.OrderActivities

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
