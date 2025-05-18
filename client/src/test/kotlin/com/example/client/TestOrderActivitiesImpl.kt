package com.example.client

import workflow_api.StructuredProductOrder
import workflow_api.OrderStepResult
import workflow_api.OrderActivities
import workflow_api.PriceQuote
import io.temporal.workflow.Workflow
import java.util.concurrent.TimeUnit

/**
 * A test-specific implementation of OrderActivities that uses Workflow.currentTimeMillis()
 * instead of System.currentTimeMillis() for proper testing with TestWorkflowEnvironment
 */
class TestOrderActivitiesImpl : OrderActivities {
    override fun validateOrder(order: StructuredProductOrder): OrderStepResult {
        // Stub: always succeed
        return OrderStepResult(true, "Order validated for ${order.productType}")
    }

    override fun createQuote(order: StructuredProductOrder): PriceQuote {
        // Mock implementation - generates a quote with price based on quantity
        val basePrice = 100.0
        val price = basePrice * order.quantity
        
        // Quote expires in 15 minutes - using System.currentTimeMillis() since activities run outside workflow context
        // The TestWorkflowEnvironment will handle time simulation properly for workflows
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
