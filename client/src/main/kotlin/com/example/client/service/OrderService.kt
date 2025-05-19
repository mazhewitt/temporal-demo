package com.example.client.service

import io.temporal.client.WorkflowClient
import io.temporal.client.WorkflowOptions
import io.temporal.client.WorkflowStub
import org.springframework.stereotype.Service
import workflow_api.OrderWorkflow
import workflow_api.PriceQuote
import workflow_api.StructuredProductOrder
import java.util.UUID
import java.util.concurrent.ConcurrentHashMap
import java.util.concurrent.TimeUnit

@Service
class OrderService(private val workflowClient: WorkflowClient) {
    
    private val logger = org.slf4j.LoggerFactory.getLogger(OrderService::class.java)
    private val taskQueue = "ORDER_TASK_QUEUE"
    
    // Store of active workflow IDs to their order IDs for tracking
    private val workflowExecutionMap = ConcurrentHashMap<String, String>()

    /**
     * Submit a new order to the Temporal workflow
     */
    fun submitOrder(orderRequest: OrderRequest): OrderResponse {
        // Generate a unique order ID
        val orderId = UUID.randomUUID().toString()
        
        // Create the order object
        val order = StructuredProductOrder(
            orderId = orderId,
            productType = orderRequest.productType,
            quantity = orderRequest.quantity,
            client = orderRequest.client
        )
        
        // Create workflow options
        val workflowOptions = WorkflowOptions.newBuilder()
            .setTaskQueue(taskQueue)
            .setWorkflowId("order-$orderId")
            .build()
        
        // Create workflow stub
        val workflow = workflowClient.newWorkflowStub(
            OrderWorkflow::class.java, 
            workflowOptions
        )
        
        // Start workflow execution asynchronously
        val workflowExecution = WorkflowClient.start(workflow::processOrder, order)
        
        // Store the workflow execution ID for later reference
        workflowExecutionMap[orderId] = workflowExecution.workflowId
        
        return OrderResponse(
            orderId = orderId,
            workflowId = workflowExecution.workflowId,
            status = "SUBMITTED"
        )
    }
    
    /**
     * Get the quote status for an order
     */
    fun getQuote(orderId: String): QuoteResponse? {
        val workflowId = workflowExecutionMap[orderId] ?: return null
        
        try {
            // Use a typed stub with timeout options
            val workflow = workflowClient.newWorkflowStub(
                OrderWorkflow::class.java,
                workflowId
            )
            
            // Execute query with a timeout
            val workflowStubImpl = WorkflowStub.fromTyped(workflow)
            val quote = workflowStubImpl.query("getQuoteStatus", PriceQuote::class.java, 5, TimeUnit.SECONDS) ?: return null
            
            return QuoteResponse(
                orderId = quote.orderId,
                price = quote.price,
                expiresAt = quote.expiryTimeMs,
                isExpired = isExpired(quote)
            )
        } catch (e: Exception) {
            logger.error("Error getting quote for order=$orderId: ${e.message}", e)
            return null
        }
    }
    
    /**
     * Accept a quote for an order
     */
    fun acceptQuote(orderId: String): Boolean {
        val workflowId = workflowExecutionMap[orderId] ?: run {
            logger.error("Quote acceptance failed: WorkflowId not found for orderId=$orderId")
            return false
        }
        
        return try {
            logger.debug("Accepting quote for order=$orderId, workflow=$workflowId")
            
            // Create a workflow stub with proper error handling
            val workflowStub = workflowClient.newUntypedWorkflowStub(workflowId)
            
            // Get quote with a timeout
            val quote = try {
                val workflow = workflowClient.newWorkflowStub(OrderWorkflow::class.java, workflowId)
                val stub = WorkflowStub.fromTyped(workflow)
                stub.query("getQuoteStatus", PriceQuote::class.java, 5, TimeUnit.SECONDS)
            } catch (e: Exception) {
                logger.error("Failed to query quote status: ${e.message}", e)
                null
            }
            
            if (quote == null) {
                logger.error("Quote acceptance failed: No quote found for orderId=$orderId")
                return false
            }
            
            if (isExpired(quote)) {
                logger.error("Quote acceptance failed: Quote expired for orderId=$orderId")
                return false
            }
            
            // Send accept signal to the workflow with timeout
            try {
                workflowStub.signal("acceptQuote")
                logger.info("Quote successfully accepted for orderId=$orderId, price=${quote.price}")
                true
            } catch (e: Exception) {
                logger.error("Error sending acceptQuote signal: ${e.message}", e)
                false
            }
        } catch (e: Exception) {
            logger.error("Error accepting quote for order=$orderId: ${e.message}", e)
            false
        }
    }
    
    /**
     * Reject a quote for an order
     */
    fun rejectQuote(orderId: String): Boolean {
        val workflowId = workflowExecutionMap[orderId] ?: run {
            logger.error("Quote rejection failed: WorkflowId not found for orderId=$orderId")
            return false
        }
        
        return try {
            logger.debug("Rejecting quote for order=$orderId, workflow=$workflowId")
            
            val workflow = workflowClient.newWorkflowStub(
                OrderWorkflow::class.java,
                workflowId
            )
            
            // Check if quote exists
            val quote = workflow.getQuoteStatus()
            if (quote == null) {
                logger.error("Quote rejection failed: No quote found for orderId=$orderId")
                return false
            }
            
            // Send reject signal to the workflow
            workflow.rejectQuote()
            logger.info("Quote successfully rejected for orderId=$orderId")
            true
        } catch (e: Exception) {
            logger.error("Error rejecting quote for order=$orderId: ${e.message}", e)
            false
        }
    }
    
    /**
     * Get the status of an order workflow
     */
    fun getOrderStatus(orderId: String): OrderStatusResponse? {
        val workflowId = workflowExecutionMap[orderId] ?: return null
        
        val workflowStub = workflowClient.newUntypedWorkflowStub(workflowId)
        
        // Check if workflow has completed
        val isComplete = try {
            workflowStub.getResult(0, TimeUnit.MILLISECONDS, String::class.java)
            true
        } catch (e: Exception) {
            false
        }
        
        // Get the current status
        val status = if (isComplete) {
            try {
                val result = workflowStub.getResult(0, TimeUnit.MILLISECONDS, String::class.java)
                when {
                    result.contains("completed successfully") -> "COMPLETED"
                    result.contains("rejected") -> "REJECTED"
                    result.contains("expired") -> "EXPIRED"
                    else -> "UNKNOWN"
                }
            } catch (e: Exception) {
                "ERROR"
            }
        } else {
            "IN_PROGRESS"
        }
        
        return OrderStatusResponse(
            orderId = orderId,
            workflowId = workflowId,
            status = status,
            quote = getQuote(orderId)
        )
    }
    
    /**
     * Get all orders currently tracked by the service
     */
    fun getAllOrders(): List<OrderStatusResponse> {
        return workflowExecutionMap.keys.mapNotNull { orderId ->
            getOrderStatus(orderId)
        }
    }
    
    /**
     * Helper method to check if a quote is expired
     */
    private fun isExpired(quote: PriceQuote): Boolean {
        // Using System time is appropriate here since this is in the service, not the workflow
        // The workflow uses Workflow.currentTimeMillis() for deterministic behavior
        val currentTime = System.currentTimeMillis()
        val isExpired = currentTime > quote.expiryTimeMs
        if (isExpired) {
            logger.debug("Quote expired: current=$currentTime, expiry=${quote.expiryTimeMs}, diff=${currentTime - quote.expiryTimeMs}ms")
        }
        return isExpired
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
    val quote: QuoteResponse?
)
