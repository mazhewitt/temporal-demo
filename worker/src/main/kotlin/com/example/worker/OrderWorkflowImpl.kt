package com.example.worker

import io.temporal.workflow.Workflow
import workflow_api.OrderWorkflow
import workflow_api.OrderActivities
import workflow_api.StructuredProductOrder
import workflow_api.OrderStepResult

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
