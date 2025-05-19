package com.example.client

import com.example.client.service.OrderRequest
import com.example.client.service.OrderService
import com.example.worker.OrderWorkflowImpl
import io.temporal.client.WorkflowClient
import io.temporal.client.WorkflowOptions
import io.temporal.client.WorkflowStub
import io.temporal.testing.TestWorkflowEnvironment
import io.temporal.worker.Worker
import org.junit.jupiter.api.AfterEach
import org.junit.jupiter.api.Assertions
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.DisplayName
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.Timeout
import org.slf4j.LoggerFactory
import workflow_api.OrderActivitiesImpl
import workflow_api.OrderWorkflow
import workflow_api.StructuredProductOrder
import java.time.Duration
import java.util.concurrent.TimeUnit

class QuoteAcceptanceTest {

    private val logger = LoggerFactory.getLogger(QuoteAcceptanceTest::class.java)

    companion object {
        const val TASK_QUEUE = "TEST_ORDER_TASK_QUEUE"
    }

    // Use a simpler setup without the extension
    private lateinit var testEnv: TestWorkflowEnvironment
    private lateinit var worker: Worker
    private lateinit var workflowClient: WorkflowClient

    @BeforeEach
    fun setUp() {
        testEnv = TestWorkflowEnvironment.newInstance()
        worker = testEnv.newWorker(TASK_QUEUE)
        worker.registerWorkflowImplementationTypes(OrderWorkflowImpl::class.java)
        worker.registerActivitiesImplementations(OrderActivitiesImpl())
        testEnv.start()
        workflowClient = testEnv.workflowClient
    }
    
    @AfterEach
    fun tearDown() {
        testEnv.close()
    }

    @Test
    @Timeout(10)
    @DisplayName("Should be able to accept a quote after submitting an order using TestWorkflowEnvironment")
    fun testQuoteAcceptanceWithTestEnvironment() {
        // Use shorter timeout for OrderService in tests
        val orderService = OrderService(workflowClient, TASK_QUEUE, 1)
        
        // 1. SUBMIT ORDER
        logger.info("Submitting test order via OrderService")
        val orderRequest = OrderRequest(
            productType = "Equity Swap", 
            quantity = 10, 
            client = "TestClient"
        )
        
        val submitResponse = orderService.submitOrder(orderRequest)
        val orderId = submitResponse.orderId
        val workflowId = submitResponse.workflowId
        logger.info("Order submitted with ID: $orderId, Workflow ID: $workflowId")
        
        // 2. Wait for quote to be generated
        testEnv.sleep(Duration.ofSeconds(1))
        
        // 3. ACCEPT QUOTE
        logger.info("Accepting quote for order $orderId")
        val acceptSuccess = orderService.acceptQuote(orderId)
        Assertions.assertTrue(acceptSuccess, "Quote acceptance should be successful")
        
        // 4. Allow time for workflow to complete
        testEnv.sleep(Duration.ofSeconds(3))
        
        // 5. Check final status
        val statusResponse = orderService.getOrderStatus(orderId)
        logger.info("Final order status: ${statusResponse?.status}")
        
        // Should be BOOKED
        Assertions.assertNotNull(statusResponse, "Order status response should not be null")
        Assertions.assertEquals("BOOKED", statusResponse?.status, 
            "Order status should be BOOKED after acceptance")
    }
}
