package workflow_api

import workflow_api.StructuredProductOrder
import workflow_api.OrderStepResult
import workflow_api.OrderActivities
import workflow_api.PriceQuote
import java.util.concurrent.TimeUnit

class OrderActivitiesImpl : OrderActivities {
    override fun validateOrder(order: StructuredProductOrder): OrderStepResult {
        // Stub: always succeed
        return OrderStepResult(true, "Order validated for ${order.productType}")
    }

    override fun createQuote(order: StructuredProductOrder): PriceQuote {
        // Mock implementation - generates a quote with price based on quantity
        val basePrice = 100.0
        val price = basePrice * order.quantity
        
        // Quote expires in 15 minutes
        val expiryTimeMs = System.currentTimeMillis() + TimeUnit.MINUTES.toMillis(15)
        
        return PriceQuote(order.orderId, price, expiryTimeMs)
    }

    override fun executeOrder(order: StructuredProductOrder, quote: PriceQuote): OrderStepResult {
        // Stub: always succeed
        return OrderStepResult(true, "Order executed for client ${order.client} at price $${quote.price}")
    }

    override fun bookOrder(order: StructuredProductOrder): OrderStepResult {
        // Stub: always succeed
        return OrderStepResult(true, "Order booked with ID ${order.orderId}")
    }
}
