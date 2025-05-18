package workflow_api

import io.temporal.workflow.WorkflowInterface
import io.temporal.workflow.WorkflowMethod
import io.temporal.activity.ActivityInterface
import io.temporal.activity.ActivityMethod

// Data class representing a simple structured product order
data class StructuredProductOrder(
    val orderId: String,
    val productType: String,
    val quantity: Int,
    val client: String
)

// Data class representing the result of each workflow step
data class OrderStepResult(
    val success: Boolean,
    val message: String
)

@WorkflowInterface
interface OrderWorkflow {
    @WorkflowMethod
    fun processOrder(order: StructuredProductOrder): String
}

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
