package com.example.worker

import io.temporal.workflow.Workflow
import io.temporal.activity.ActivityOptions
import workflow_api.OrderWorkflow
import workflow_api.OrderActivities
import workflow_api.StructuredProductOrder
import workflow_api.OrderStepResult
import workflow_api.PriceQuote
import java.time.Duration

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
        
        // Wait for the client to accept or reject the quote, or for the quote to expire
        val quoteTimeout = Duration.ofMinutes(15) // Match the quote expiry time
        Workflow.await(quoteTimeout, {
            quoteAccepted || quoteRejected || isQuoteExpired()
        })
        
        // Handle quote expiration or rejection
        if (quoteRejected) {
            return "Quote rejected by client for order ${order.orderId}"
        }
        
        if (isQuoteExpired() && !quoteAccepted) {
            return "Quote expired for order ${order.orderId}"
        }
        
        if (!quoteAccepted) {
            return "Quote processing failed for order ${order.orderId}"
        }
        
        // Quote accepted, execute the order
        val quote = currentQuote!!
        val execution = activities.executeOrder(order, quote)
        if (!execution.success) return "Execution failed: ${execution.message}"

        // Book the order
        val booking = activities.bookOrder(order)
        if (!booking.success) return "Booking failed: ${booking.message}"

        return "Order workflow completed successfully: ${booking.message}"
    }
    
    override fun getQuoteStatus(): PriceQuote? {
        return currentQuote
    }
    
    override fun acceptQuote() {
        if (currentQuote != null && !isQuoteExpired()) {
            quoteAccepted = true
            Workflow.getLogger(this.javaClass).info("Quote accepted for order ${currentQuote?.orderId}")
        }
    }
    
    override fun rejectQuote() {
        if (currentQuote != null) {
            quoteRejected = true
            Workflow.getLogger(this.javaClass).info("Quote rejected for order ${currentQuote?.orderId}")
        }
    }
    
    private fun isQuoteExpired(): Boolean {
        val quote = currentQuote ?: return true
        return System.currentTimeMillis() > quote.expiryTimeMs
    }
}
