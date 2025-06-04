package com.example.client.service

import com.example.client.test.assertFailure
import com.example.client.test.assertOrderErrorOfType
import com.example.client.test.assertSuccess
import io.temporal.api.common.v1.WorkflowExecution
import io.temporal.client.WorkflowClient
import io.temporal.client.WorkflowOptions
import io.temporal.client.WorkflowStub
import io.temporal.common.converter.EncodedValues
import org.mockito.ArgumentMatchers
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.DisplayName
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.extension.ExtendWith
import org.mockito.ArgumentCaptor
import org.mockito.ArgumentMatchers.any
import org.mockito.ArgumentMatchers.anyLong
import org.mockito.ArgumentMatchers.anyString
import org.mockito.ArgumentMatchers.eq
import org.junit.jupiter.api.Assertions.assertNotNull
import org.mockito.Captor
import org.mockito.Mock
import org.mockito.Mockito.`when`
import org.mockito.Mockito.doReturn
import org.mockito.Mockito.doThrow
import org.mockito.Mockito.mock
import org.mockito.Mockito.verify
import org.mockito.junit.jupiter.MockitoExtension
import workflow_api.OrderWorkflow
import workflow_api.PriceQuote
import workflow_api.StructuredProductOrder as WorkflowStructuredProductOrder
import workflow_api.StructuredProductOrder
import java.util.UUID
import java.util.concurrent.ConcurrentHashMap
import java.util.concurrent.TimeUnit
import kotlin.reflect.jvm.javaField
import org.junit.jupiter.api.Assertions.*

@ExtendWith(MockitoExtension::class)
class OrderServiceTest {

    @Mock
    private lateinit var workflowClient: WorkflowClient
    
    @Mock
    private lateinit var orderWorkflow: OrderWorkflow
    
    @Mock
    private lateinit var workflowStub: WorkflowStub
    
    @Captor
    private lateinit var workflowOptionsCaptor: ArgumentCaptor<WorkflowOptions>
    
    private lateinit var orderService: OrderService
    
    private val taskQueueName = "TEST_TASK_QUEUE"
    private val workflowResultTimeoutSeconds = 2L
    
    @BeforeEach
    fun setup() {
        orderService = OrderService(
            workflowClient, 
            taskQueueName, 
            workflowResultTimeoutSeconds
        )
    }
    
    @Test
    @DisplayName("submitOrder should return success result when workflow starts successfully")
    fun submitOrderSuccess() {
        // Use a test subclass with overridden method to avoid static mocking issues
        val testOrderService = object : OrderService(workflowClient, taskQueueName, workflowResultTimeoutSeconds) {
            override fun startWorkflow(workflow: OrderWorkflow, order: WorkflowStructuredProductOrder): WorkflowExecution {
                val mockExecution = mock(WorkflowExecution::class.java)
                val mockWorkflowId = "order-${order.orderId}"
                `when`(mockExecution.workflowId).thenReturn(mockWorkflowId)
                return mockExecution
            }
        }
        
        val orderRequest = OrderRequest(
            productType = "Equity Swap",
            quantity = 100,
            client = "TestClient"
        )
        
        // Setup mocking
        `when`(workflowClient.newWorkflowStub(eq(OrderWorkflow::class.java), any(WorkflowOptions::class.java))).thenReturn(orderWorkflow)
        
        // Act
        val result = testOrderService.submitOrder(orderRequest)
        
        // Assert
        result.onFailure { 
            fail("Result should be a success but was a failure: ${it.message}")
        }
        
        val response = result.getOrNull()
        assertNotNull(response)
        assertNotNull(response?.orderId)
        assertTrue((response?.workflowId?.startsWith("order-") ?: false), "Workflow ID should start with 'order-'")
        assertEquals("SUBMITTED", response?.status)
        
        // Verify the workflowOptions were set correctly
        verify(workflowClient).newWorkflowStub(eq(OrderWorkflow::class.java), workflowOptionsCaptor.capture())
        val options = workflowOptionsCaptor.value
        assertEquals(taskQueueName, options.taskQueue)
        val capturedOrderId = response?.orderId
        if (capturedOrderId != null) {
            assertTrue(options.workflowId.contains(capturedOrderId))
        }
    }
    
    @Test
    @DisplayName("submitOrder should return WorkflowOperationFailed when workflow start fails")
    fun submitOrderFailure() {
        // Arrange
        val orderRequest = OrderRequest(
            productType = "Equity Swap",
            quantity = 100,
            client = "TestClient"
        )
        
        val exception = RuntimeException("Workflow start failed")
        `when`(workflowClient.newWorkflowStub(eq(OrderWorkflow::class.java), any(WorkflowOptions::class.java)))
            .thenThrow(exception)
        
        // Act
        val result = orderService.submitOrder(orderRequest)
        
        // Assert
        val error = result.exceptionOrNull() as OrderError.WorkflowOperationFailed
        assertEquals("Failed to submit order: Workflow start failed", error.errorMessage)
        assertEquals(exception, error.errorCause)
    }
    
    @Test
    @DisplayName("getQuote should return QuoteResponse when quote exists")
    fun getQuoteSuccess() {
        // Arrange
        val orderId = UUID.randomUUID().toString()
        val workflowId = "order-$orderId"
        
        // Setup the workflow execution map
        setWorkflowExecution(orderId, workflowId)
        
        val quote = PriceQuote(
            orderId = orderId,
            price = 100.0,
            expiryTimeMs = System.currentTimeMillis() + 60000 // 1 minute in the future
        )
        
        // Create a test subclass with overriding methods to avoid static mocking
        val testOrderService = object : OrderService(workflowClient, taskQueueName, workflowResultTimeoutSeconds) {
            override fun getQuoteFromWorkflow(workflowId: String): PriceQuote? {
                return quote
            }
        }
        
        // Set up the workflow execution map using reflection
        val field = OrderService::class.java.getDeclaredField("workflowExecutionMap")
        field.isAccessible = true
        val map = field.get(testOrderService) as ConcurrentHashMap<String, String>
        map[orderId] = workflowId
        
        // Act 
        val result = testOrderService.getQuote(orderId)
        
        // Assert
        val response = result.getOrNull()
        assertNotNull(response)
        assertEquals(orderId, response?.orderId)
        assertEquals(quote.price, response?.price)
        assertEquals(quote.expiryTimeMs, response?.expiresAt)
        assertFalse(response?.isExpired ?: true) // Should be false since we set expiry in the future
    }
    
    @Test
    @DisplayName("getQuote should return WorkflowNotFound when workflow ID doesn't exist")
    fun getQuoteWorkflowNotFound() {
        // Arrange
        val orderId = UUID.randomUUID().toString()
        
        // Map is empty, so no workflow ID will be found
        
        // Act
        val result = orderService.getQuote(orderId)
        
        // Assert
        val error = result.exceptionOrNull() as OrderError.WorkflowNotFound
        assertEquals(orderId, error.orderId)
        org.junit.jupiter.api.Assertions.assertTrue(error.errorMessage.contains(orderId as CharSequence))
    }
    
    @Test
    @DisplayName("getQuote should return QuoteNotFound when query returns null")
    fun getQuoteNotFound() {
        // Arrange
        val orderId = UUID.randomUUID().toString()
        val workflowId = "order-$orderId"
        
        // Create a test subclass with overriding methods to avoid static mocking
        val testOrderService = object : OrderService(workflowClient, taskQueueName, workflowResultTimeoutSeconds) {
            override fun getQuoteFromWorkflow(workflowId: String): PriceQuote? {
                return null
            }
        }
        
        // Set up the workflow execution map using reflection
        val field = OrderService::class.java.getDeclaredField("workflowExecutionMap")
        field.isAccessible = true
        val map = field.get(testOrderService) as ConcurrentHashMap<String, String>
        map[orderId] = workflowId
        
        // Act
        val result = testOrderService.getQuote(orderId)
        
        // Assert
        val error = result.exceptionOrNull() as OrderError.QuoteNotFound
        assertEquals(orderId, error.orderId)
        org.junit.jupiter.api.Assertions.assertTrue(error.errorMessage.contains(orderId))
    }
    
    @Test
    @DisplayName("acceptQuote should return success when quote is successfully accepted")
    fun acceptQuoteSuccess() {
        // Arrange
        val orderId = UUID.randomUUID().toString()
        val workflowId = "order-$orderId"
        
        val quote = PriceQuote(
            orderId = orderId,
            price = 100.0,
            expiryTimeMs = System.currentTimeMillis() + 60000 // 1 minute in the future
        )
        
        // Create a test subclass with overriding methods to avoid static mocking
        val testOrderService = object : OrderService(workflowClient, taskQueueName, workflowResultTimeoutSeconds) {
            override fun getQuoteFromWorkflow(workflowId: String): PriceQuote? {
                return quote
            }
            
            override fun signalWorkflow(workflowId: String, signalName: String) {
                // Signal is successful
            }
        }
        
        // Set up the workflow execution map using reflection
        val field = OrderService::class.java.getDeclaredField("workflowExecutionMap")
        field.isAccessible = true
        val map = field.get(testOrderService) as ConcurrentHashMap<String, String>
        map[orderId] = workflowId
        
        // Act
        val result = testOrderService.acceptQuote(orderId)
        
        // Assert
        val success = result.getOrNull()
        assertNotNull(success)
        org.junit.jupiter.api.Assertions.assertTrue(success ?: false)
    }
    
    @Test
    @DisplayName("acceptQuote should return QuoteExpired when quote has expired")
    fun acceptQuoteExpired() {
        // Arrange
        val orderId = UUID.randomUUID().toString()
        val workflowId = "order-$orderId"
        
        val expiredQuote = PriceQuote(
            orderId = orderId, 
            price = 100.0,
            expiryTimeMs = System.currentTimeMillis() - 1000 // Already expired
        )
        
        // Create a test subclass with overriding methods to avoid static mocking
        val testOrderService = object : OrderService(workflowClient, taskQueueName, workflowResultTimeoutSeconds) {
            override fun getQuoteFromWorkflow(workflowId: String): PriceQuote? {
                return expiredQuote
            }
        }
        
        // Set up the workflow execution map using reflection
        val field = OrderService::class.java.getDeclaredField("workflowExecutionMap")
        field.isAccessible = true
        val map = field.get(testOrderService) as ConcurrentHashMap<String, String>
        map[orderId] = workflowId
        
        // Act
        val result = testOrderService.acceptQuote(orderId)
        
        // Assert
        val error = result.exceptionOrNull() as OrderError.QuoteExpired
        assertEquals(orderId, error.orderId)
        assertEquals(expiredQuote.expiryTimeMs, error.expiryTime)
    }
    
    @Test
    @DisplayName("acceptQuote should return WorkflowNotFound when workflow ID doesn't exist")
    fun acceptQuoteWorkflowNotFound() {
        // Arrange
        val orderId = UUID.randomUUID().toString()
        
        // Map is empty, so no workflow ID will be found
        
        // Act
        val result = orderService.acceptQuote(orderId)
        
        // Assert
        val error = result.exceptionOrNull() as OrderError.WorkflowNotFound
        assertEquals(orderId, error.orderId)
        org.junit.jupiter.api.Assertions.assertTrue(error.errorMessage.contains(orderId))
    }
    
    @Test
    @DisplayName("getOrderStatus should return status response when workflow exists")
    fun getOrderStatusSuccess() {
        // Arrange
        val orderId = UUID.randomUUID().toString()
        val workflowId = "order-$orderId"
        
        // Mock quote result
        val quote = PriceQuote(
            orderId = orderId,
            price = 100.0,
            expiryTimeMs = System.currentTimeMillis() + 60000 // 1 minute in the future
        )
        
        // Create a test subclass with overriding methods to avoid static mocking
        val testOrderService = object : OrderService(workflowClient, taskQueueName, workflowResultTimeoutSeconds) {
            override fun getWorkflowStatusResult(workflowId: String): Result<String> {
                return Result.success("Order completed successfully")
            }
            
            override fun getQuoteFromWorkflow(workflowId: String): PriceQuote? {
                return quote
            }
        }
        
        // Set up the workflow execution map using reflection
        val field = OrderService::class.java.getDeclaredField("workflowExecutionMap")
        field.isAccessible = true
        val map = field.get(testOrderService) as ConcurrentHashMap<String, String>
        map[orderId] = workflowId
        
        // Act
        val result = testOrderService.getOrderStatus(orderId)
        
        // Assert
        val response = result.getOrNull()
        assertNotNull(response)
        assertEquals(orderId, response?.orderId)
        assertEquals(workflowId, response?.workflowId)
        assertEquals("COMPLETED", response?.status) // Should match the status extracted from "Order completed successfully"
        
        // Quote should also be included
        assertNotNull(response?.quote)
        assertEquals(orderId, response?.quote?.orderId)
        assertEquals(quote.price, response?.quote?.price)
    }
    
    @Test
    @DisplayName("getOrderStatus should return IN_PROGRESS when workflow hasn't completed")
    fun getOrderStatusInProgress() {
        // Arrange
        val orderId = UUID.randomUUID().toString()
        val workflowId = "order-$orderId"
        
        // Mock quote result
        val quote = PriceQuote(
            orderId = orderId,
            price = 100.0,
            expiryTimeMs = System.currentTimeMillis() + 60000
        )
        
        // Create a test subclass with overriding methods to avoid static mocking
        val testOrderService = object : OrderService(workflowClient, taskQueueName, workflowResultTimeoutSeconds) {
            override fun getWorkflowStatusResult(workflowId: String): Result<String> {
                // Simulate workflow not complete yet by returning a failure with WorkflowInProgress
                return Result.failure(OrderError.WorkflowInProgress)
            }
            
            override fun getQuoteFromWorkflow(workflowId: String): PriceQuote? {
                return quote
            }
        }
        
        // Set up the workflow execution map using reflection
        val field = OrderService::class.java.getDeclaredField("workflowExecutionMap")
        field.isAccessible = true
        val map = field.get(testOrderService) as ConcurrentHashMap<String, String>
        map[orderId] = workflowId
        
        // Act
        val result = testOrderService.getOrderStatus(orderId)
        
        // Assert
        val response = result.getOrNull()
        assertNotNull(response)
        assertEquals(orderId, response?.orderId)
        assertEquals(workflowId, response?.workflowId)
        assertEquals("IN_PROGRESS", response?.status)
    }
    
    @Test
    @DisplayName("getAllOrders should return list of order statuses")
    fun getAllOrdersSuccess() {
        // Create a test OrderService that overrides getOrderStatus to return simple test data
        val testOrderService = object : OrderService(workflowClient, taskQueueName, workflowResultTimeoutSeconds) {
            override fun getOrderStatus(orderId: String): Result<OrderStatusResponse> {
                // Just return dummy successful responses for testing getAllOrders
                val status = if (orderId.endsWith('1')) "COMPLETED" else "IN_PROGRESS"
                return Result.success(OrderStatusResponse(
                    orderId = orderId, 
                    workflowId = "order-$orderId", 
                    status = status,
                    quote = null
                ))
            }
        }
        
        val orderId1 = UUID.randomUUID().toString()
        val orderId2 = UUID.randomUUID().toString()
        val workflowId1 = "order-$orderId1"
        val workflowId2 = "order-$orderId2"
        
        // Setup the workflow execution map
        val field = OrderService::class.java.getDeclaredField("workflowExecutionMap")
        field.isAccessible = true
        val map = field.get(testOrderService) as ConcurrentHashMap<String, String>
        map[orderId1] = workflowId1
        map[orderId2] = workflowId2
        
        // Act
        val result = testOrderService.getAllOrders()
        
        // Assert
        assertEquals(2, result.size)
        assertTrue(result.any { it.orderId == orderId1 })
        assertTrue(result.any { it.orderId == orderId2 })
    }
    
    /**
     * Helper method to set the workflow execution map using reflection
     */
    private fun setWorkflowExecution(orderId: String, workflowId: String) {
        val field = OrderService::class.java.getDeclaredField("workflowExecutionMap")
        field.isAccessible = true
        val map = field.get(orderService) as ConcurrentHashMap<String, String>
        map[orderId] = workflowId
    }
    
    /**
     * Helper method for mocking static methods
     */
    private fun <T> mockStatic(method: java.lang.reflect.Method, block: () -> T): T {
        val originalAccessibility = method.isAccessible
        method.isAccessible = true
        try {
            return block()
        } finally {
            method.isAccessible = originalAccessibility
        }
    }
    
    // Remove the custom extension function as it's causing typing issues
}
