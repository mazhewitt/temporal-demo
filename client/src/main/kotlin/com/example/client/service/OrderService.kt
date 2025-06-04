package com.example.client.service

import io.temporal.client.WorkflowClient
import io.temporal.client.WorkflowOptions
import io.temporal.client.WorkflowStub
import io.temporal.api.common.v1.WorkflowExecution
import org.springframework.stereotype.Service
import workflow_api.OrderWorkflow
import workflow_api.PriceQuote
import workflow_api.StructuredProductOrder
import java.util.UUID
import java.util.concurrent.ConcurrentHashMap
import java.util.concurrent.TimeUnit

/**
 * Sealed class hierarchy for order operation errors
 */
sealed class OrderError(override val message: String, cause: Throwable? = null) : Exception(message, cause) {
    
    data class WorkflowNotFound(
        val errorMessage: String, 
        val orderId: String
    ) : OrderError(errorMessage)
    
    data class QuoteNotFound(
        val errorMessage: String, 
        val orderId: String
    ) : OrderError(errorMessage)
    
    data class QuoteExpired(
        val errorMessage: String, 
        val orderId: String, 
        val expiryTime: Long
    ) : OrderError(errorMessage)
    
    data class WorkflowOperationFailed(
        val errorMessage: String, 
        val errorCause: Throwable? = null
    ) : OrderError(errorMessage, errorCause)
    
    data class UnknownError(
        val errorMessage: String, 
        val errorCause: Throwable? = null
    ) : OrderError(errorMessage, errorCause)
    
    /**
     * Exception to indicate workflow is still in progress (not an actual error)
     */
    object WorkflowInProgress : OrderError("Workflow is still in progress")
}

@Service
class OrderService(
    private val workflowClient: WorkflowClient,
    private val taskQueueName: String = "ORDER_TASK_QUEUE", // Modified: taskQueue is now a constructor param with a default
    private val workflowResultTimeoutSeconds: Long = 5 // Add configurable timeout with default of 5 seconds
) {
    
    private val logger = org.slf4j.LoggerFactory.getLogger(OrderService::class.java)
    
    // Store of active workflow IDs to their order IDs for tracking
    private val workflowExecutionMap = ConcurrentHashMap<String, String>()

    /**
     * Submit a new order to the Temporal workflow
     * 
     * @param orderRequest The order details to submit
     * @return Result containing OrderResponse on success or an OrderError on failure
     */
    fun submitOrder(orderRequest: OrderRequest): Result<OrderResponse> = runCatching {
        // Generate a unique order ID
        val orderId = UUID.randomUUID().toString()
        
        // Create the order object
        val order = StructuredProductOrder(
            orderId = orderId,
            productType = orderRequest.productType,
            quantity = orderRequest.quantity,
            client = orderRequest.client
        )
        
        try {
            // Create workflow options
            val workflowOptions = WorkflowOptions.newBuilder()
                .setTaskQueue(this.taskQueueName)
                .setWorkflowId("order-$orderId")
                .build()
            
            // Create workflow stub
            val workflow = workflowClient.newWorkflowStub(
                OrderWorkflow::class.java, 
                workflowOptions
            )
            
            // Start workflow execution asynchronously
            val workflowExecution = startWorkflow(workflow, order)
            
            // Store the workflow execution ID for later reference
            workflowExecutionMap[orderId] = workflowExecution.workflowId
            
            OrderResponse(
                orderId = orderId,
                workflowId = workflowExecution.workflowId,
                status = "SUBMITTED"
            )
        } catch (e: Exception) {
            logger.error("Error submitting order: ${e.message}", e)
            throw OrderError.WorkflowOperationFailed("Failed to submit order: ${e.message}", e)
        }
    }
    
    /**
     * Get the quote status for an order
     * 
     * @param orderId The ID of the order to get the quote for
     * @return Result containing either QuoteResponse or an OrderError
     */
    fun getQuote(orderId: String): Result<QuoteResponse> = runCatching {
        val workflowId = workflowExecutionMap[orderId] 
            ?: throw OrderError.WorkflowNotFound("Workflow not found for order ID: $orderId", orderId)
        
        try {
            // Use a typed stub with timeout options
            val quote = getQuoteFromWorkflow(workflowId) 
                ?: throw OrderError.QuoteNotFound("No quote found for order ID: $orderId", orderId)
            
            QuoteResponse(
                orderId = quote.orderId,
                price = quote.price,
                expiresAt = quote.expiryTimeMs,
                isExpired = isExpired(quote)
            )
        } catch (e: OrderError) {
            throw e
        } catch (e: Exception) {
            logger.error("Error getting quote for order=$orderId: ${e.message}", e)
            throw OrderError.WorkflowOperationFailed("Error getting quote for order: $orderId", e)
        }
    }
    
    /**
     * Accept a quote for an order
     * 
     * @param orderId The ID of the order to accept the quote for
     * @return Result containing Boolean success indicator or an OrderError
     */
    fun acceptQuote(orderId: String): Result<Boolean> = runCatching {
        val workflowId = workflowExecutionMap[orderId] ?: 
            throw OrderError.WorkflowNotFound("Quote acceptance failed: WorkflowId not found for orderId=$orderId", orderId)
        
        logger.debug("Accepting quote for order=$orderId, workflow=$workflowId")
            
        // Get quote with a timeout
        val quote = try {
            getQuoteFromWorkflow(workflowId)
        } catch (e: Exception) {
            logger.error("Failed to query quote status: ${e.message}", e)
            throw OrderError.QuoteNotFound("Failed to query quote status for orderId=$orderId", orderId)
        } ?: throw OrderError.QuoteNotFound("No quote found for orderId=$orderId", orderId)
            
        if (isExpired(quote)) {
            throw OrderError.QuoteExpired("Quote expired for orderId=$orderId", orderId, quote.expiryTimeMs)
        }
            
        // Send accept signal to the workflow with timeout
        try {
            signalWorkflow(workflowId, "acceptQuote")
            logger.info("Quote successfully accepted for orderId=$orderId, price=${quote.price}")
            true
        } catch (e: Exception) {
            logger.error("Error sending acceptQuote signal: ${e.message}", e)
            throw OrderError.WorkflowOperationFailed("Error sending acceptQuote signal for orderId=$orderId", e)
        }
    }
    
    /**
     * Reject a quote for an order
     * 
     * @param orderId The ID of the order to reject the quote for
     * @return Result containing Boolean success indicator or an OrderError
     */
    fun rejectQuote(orderId: String): Result<Boolean> = runCatching {
        val workflowId = workflowExecutionMap[orderId] ?: 
            throw OrderError.WorkflowNotFound("Quote rejection failed: WorkflowId not found for orderId=$orderId", orderId)
        
        logger.debug("Rejecting quote for order=$orderId, workflow=$workflowId")
        
        try {
            val quote = getQuoteFromWorkflow(workflowId) ?: 
                throw OrderError.QuoteNotFound("Quote rejection failed: No quote found for orderId=$orderId", orderId)
            
            // Send reject signal to the workflow
            signalWorkflow(workflowId, "rejectQuote")
            logger.info("Quote successfully rejected for orderId=$orderId")
            true
        } catch (e: OrderError) {
            throw e
        } catch (e: Exception) {
            logger.error("Error rejecting quote for order=$orderId: ${e.message}", e)
            throw OrderError.WorkflowOperationFailed("Error rejecting quote for order=$orderId", e)
        }
    }
    
    /**
     * Get the status of an order workflow
     * 
     * @param orderId The ID of the order to get the status for
     * @return Result containing OrderStatusResponse or an OrderError
     */
    open fun getOrderStatus(orderId: String): Result<OrderStatusResponse> = runCatching {
        val workflowId = workflowExecutionMap[orderId] ?: 
            throw OrderError.WorkflowNotFound("Workflow not found for order ID: $orderId", orderId)
        
        val workflowResult = try {
            getWorkflowStatusResult(workflowId)
        } catch (e: io.temporal.client.WorkflowException) {
            Result.failure(OrderError.WorkflowInProgress)
        } catch (e: Exception) {
            logger.error("Unexpected error getting workflow result for orderId=$orderId, workflowId=$workflowId: ${e.message}", e)
            Result.failure(OrderError.WorkflowInProgress)
        }

        val status = workflowResult.fold(
            onSuccess = { result ->
                when {
                    result.contains("Order booked with ID") -> "BOOKED"
                    result.contains("completed successfully") -> "COMPLETED"
                    result.contains("rejected") -> "REJECTED"
                    result.contains("expired") -> "EXPIRED"
                    else -> {
                        logger.warn("Workflow for orderId=$orderId completed with unexpected message: '$result'")
                        "UNKNOWN" 
                    }
                }
            },
            onFailure = { exception ->
                when (exception) {
                    is OrderError.WorkflowInProgress -> "IN_PROGRESS"
                    else -> {
                        logger.error("Workflow error for orderId=$orderId: ${exception.message}", exception)
                        "FAILED"
                    }
                }
            }
        )
        
        val quoteResult = getQuote(orderId)
        val quote = quoteResult.getOrNull()
        
        OrderStatusResponse(
            orderId = orderId,
            workflowId = workflowId,
            status = status,
            quote = quote
        )
    }
    
    /**
     * Get all orders currently tracked by the service
     * 
     * @return List of OrderStatusResponses, filtering out any that couldn't be retrieved
     */
    fun getAllOrders(): List<OrderStatusResponse> {
        return workflowExecutionMap.keys.mapNotNull { orderId ->
            getOrderStatus(orderId).getOrNull()
        }
    }
    
    /**
     * Get all orders with detailed results including any errors
     *
     * @return Map of order IDs to their Result<OrderStatusResponse>
     */
    fun getAllOrdersWithResults(): Map<String, Result<OrderStatusResponse>> {
        return workflowExecutionMap.keys.associateWith { orderId ->
            getOrderStatus(orderId)
        }
    }
    
    /**
     * Helper method to check if a quote is expired
     */
    private fun isExpired(quote: PriceQuote): Boolean {
        val currentTime = System.currentTimeMillis()
        val isExpired = currentTime > quote.expiryTimeMs
        if (isExpired) {
            logger.debug("Quote expired: current=$currentTime, expiry=${quote.expiryTimeMs}, diff=${currentTime - quote.expiryTimeMs}ms")
        }
        return isExpired
    }
    
    /**
     * Start a workflow execution
     * Protected for testing purposes so it can be overridden in tests
     */
    protected open fun startWorkflow(workflow: OrderWorkflow, order: StructuredProductOrder): WorkflowExecution {
        val workflowExecution = WorkflowClient.start(workflow::processOrder, order)
        return workflowExecution
    }

    /**
     * Helper method to get quote from workflow
     * Protected for testing purposes so it can be overridden in tests
     */
    protected open fun getQuoteFromWorkflow(workflowId: String): PriceQuote? {
        val workflow = workflowClient.newWorkflowStub(OrderWorkflow::class.java, workflowId)
        val workflowStubImpl = WorkflowStub.fromTyped(workflow)
        return workflowStubImpl.query("getQuoteStatus", PriceQuote::class.java, 5, TimeUnit.SECONDS)
    }
    
    /**
     * Helper method to get workflow status
     * Protected for testing purposes so it can be overridden in tests
     */
    protected open fun getWorkflowStatus(workflowId: String): String {
        val workflowStub = workflowClient.newUntypedWorkflowStub(workflowId)
        
        return try {
            // Try to get the result with the configured timeout
            workflowStub.getResult(workflowResultTimeoutSeconds, TimeUnit.SECONDS, String::class.java)
        } catch (e: java.util.concurrent.TimeoutException) {
            // TimeoutException means the workflow is still running
            "IN_PROGRESS"
        } catch (e: Exception) {
            // Other exceptions indicate workflow failure or other issues
            "FAILED: ${e.message}"
        }
    }
    
    /**
     * Helper method to get workflow status as Result
     * Protected for testing purposes so it can be overridden in tests
     */
    protected open fun getWorkflowStatusResult(workflowId: String): Result<String> {
        val workflowStub = workflowClient.newUntypedWorkflowStub(workflowId)
        
        return try {
            // Try to get the result with the configured timeout
            val result = workflowStub.getResult(workflowResultTimeoutSeconds, TimeUnit.SECONDS, String::class.java)
            Result.success(result)
        } catch (e: java.util.concurrent.TimeoutException) {
            // TimeoutException means the workflow is still running
            Result.failure(OrderError.WorkflowInProgress)
        } catch (e: Exception) {
            // Other exceptions indicate workflow failure or other issues
            Result.failure(OrderError.WorkflowOperationFailed("Workflow operation failed: ${e.message}", e))
        }
    }
    
    /**
     * Helper method to send signal to workflow
     * Protected for testing purposes so it can be overridden in tests
     */
    protected open fun signalWorkflow(workflowId: String, signalName: String) {
        val workflowStub = workflowClient.newUntypedWorkflowStub(workflowId)
        workflowStub.signal(signalName)
    }
}

// Request and response data classes
data class OrderRequest(
    val productType: String,
    val quantity: Int,
    val client: String
)

data class OrderResponse(
    val orderId: String,
    val workflowId: String,
    val status: String
)

data class QuoteResponse(
    val orderId: String,
    val price: Double,
    val expiresAt: Long,
    val isExpired: Boolean
)

data class OrderStatusResponse(
    val orderId: String,
    val workflowId: String,
    val status: String,
    val quote: QuoteResponse? = null
) {
    companion object {
        fun fromError(error: OrderError, orderId: String): OrderStatusResponse {
            return OrderStatusResponse(
                orderId = orderId,
                workflowId = "",
                status = when (error) {
                    is OrderError.WorkflowNotFound -> "NOT_FOUND"
                    is OrderError.QuoteNotFound -> "QUOTE_NOT_FOUND"
                    is OrderError.QuoteExpired -> "QUOTE_EXPIRED"
                    is OrderError.WorkflowOperationFailed -> "OPERATION_FAILED"
                    is OrderError.UnknownError -> "UNKNOWN_ERROR"
                    is OrderError.WorkflowInProgress -> "IN_PROGRESS"
                }
            )
        }
    }
}
