package workflow_api

import io.temporal.workflow.WorkflowInterface
import io.temporal.workflow.WorkflowMethod
import io.temporal.activity.ActivityInterface
import io.temporal.activity.ActivityMethod
import io.temporal.workflow.QueryMethod
import io.temporal.workflow.SignalMethod
import com.fasterxml.jackson.annotation.JsonCreator
import com.fasterxml.jackson.annotation.JsonProperty

// Data class representing a simple structured product order
data class StructuredProductOrder @JsonCreator constructor(
    @JsonProperty("orderId") val orderId: String,
    @JsonProperty("productType") val productType: String,
    @JsonProperty("quantity") val quantity: Int,
    @JsonProperty("client") val client: String
)

// Data class representing the result of each workflow step
data class OrderStepResult @JsonCreator constructor(
    @JsonProperty("success") val success: Boolean,
    @JsonProperty("message") val message: String
)

// Data class representing a price quote
data class PriceQuote @JsonCreator constructor(
    @JsonProperty("orderId") val orderId: String,
    @JsonProperty("price") val price: Double,
    @JsonProperty("expiryTimeMs") val expiryTimeMs: Long
)

@WorkflowInterface
interface OrderWorkflow {
    @WorkflowMethod
    fun processOrder(order: StructuredProductOrder): String
    
    @QueryMethod
    fun getQuoteStatus(): PriceQuote?
    
    @SignalMethod
    fun acceptQuote()
    
    @SignalMethod
    fun rejectQuote()
}

@ActivityInterface
interface OrderActivities {
    @ActivityMethod
    fun validateOrder(order: StructuredProductOrder): OrderStepResult

    @ActivityMethod
    fun createQuote(order: StructuredProductOrder): PriceQuote

    @ActivityMethod
    fun executeOrder(order: StructuredProductOrder, quote: PriceQuote): OrderStepResult

    @ActivityMethod
    fun bookOrder(order: StructuredProductOrder): OrderStepResult
}
