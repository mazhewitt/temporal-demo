package com.example.client

import io.temporal.client.WorkflowClient
import io.temporal.client.WorkflowOptions
import io.temporal.client.WorkflowStub
import io.temporal.testing.TestWorkflowEnvironment
import io.temporal.worker.Worker
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.Timeout
import org.junit.jupiter.api.Assertions
import org.junit.jupiter.api.DisplayName
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.AfterEach
import org.junit.jupiter.api.Tag
import workflow_api.OrderWorkflow
import workflow_api.StructuredProductOrder
import workflow_api.PriceQuote
import com.example.client.TestOrderWorkflowImpl
import com.example.client.TestOrderActivitiesImpl
import java.util.UUID
import java.util.concurrent.TimeUnit

@Tag("e2e")
@DisplayName("Order Workflow E2E Tests")
class OrderWorkflowE2ETest {

    private lateinit var testEnv: TestWorkflowEnvironment
    private lateinit var client: WorkflowClient
    private val taskQueue = "ORDER_TASK_QUEUE"

    @BeforeEach
    fun setUp() {
        // Create the test workflow environment
        testEnv = TestWorkflowEnvironment.newInstance()
        client = testEnv.workflowClient
        
        // Create a worker for the task queue
        val worker = testEnv.newWorker(taskQueue)
        
        // Register workflow and activities with test implementations that use Workflow.currentTimeMillis()
        worker.registerWorkflowImplementationTypes(TestOrderWorkflowImpl::class.java)
        worker.registerActivitiesImplementations(TestOrderActivitiesImpl())
        
        // Start the worker
        testEnv.start()
    }
    
    @AfterEach
    fun tearDown() {
        testEnv.close()
    }

    @Test
    @DisplayName("Should process RFQ order successfully when client accepts quote")
    @Timeout(value = 10, unit = TimeUnit.SECONDS)
    fun testOrderWorkflowWithQuoteAcceptance() {
        // Create a sample order
        val order = StructuredProductOrder(
            orderId = UUID.randomUUID().toString(),
            productType = "Equity Swap",
            quantity = 10,
            client = "TestClient"
        )

        // Create workflow stub
        val workflow: OrderWorkflow = client.newWorkflowStub(
            OrderWorkflow::class.java,
            WorkflowOptions.newBuilder()
                .setTaskQueue(taskQueue)
                .build()
        )

        // Start workflow execution asynchronously
        println("Starting RFQ workflow execution...")
        val workflowExecution = WorkflowClient.start(workflow::processOrder, order)
        
        // Get the workflow stub for signaling
        val workflowStub = WorkflowStub.fromTyped(workflow)
        
        try {
            // In TestWorkflowEnvironment, we need to ensure the workflow has had time to execute
            // the initial activities and set up the state
            testEnv.sleep(java.time.Duration.ofMillis(500))
            
            // Query the workflow to get the current quote
            val quote = workflow.getQuoteStatus()
            println("Quote received: $${quote?.price} for order ${quote?.orderId}")
            
            // Simulate client reviewing the quote
            Assertions.assertNotNull(quote, "Quote should not be null")
            Assertions.assertEquals(order.orderId, quote?.orderId, "Quote should have correct order ID")
            Assertions.assertTrue(quote?.price ?: 0.0 > 0.0, "Quote price should be greater than zero")
            
            // Simulate client accepting the quote
            println("Client accepting the quote...")
            workflow.acceptQuote()
            
            // Wait for workflow completion
            val result = workflowStub.getResult(5, TimeUnit.SECONDS, String::class.java)
            
            // Verify the result
            Assertions.assertNotNull(result, "Workflow result should not be null")
            Assertions.assertTrue(result.contains("Order workflow completed successfully"), 
                "Result should indicate workflow completed successfully")
            Assertions.assertTrue(result.contains("Order booked with ID ${order.orderId}"), 
                "Result should indicate the order was booked with the correct order ID")
                
            println("RFQ Workflow execution completed successfully.")
            println("Workflow result: $result")
            
        } catch (e: Exception) {
            println("Error during workflow execution: ${e.message}")
            println("Order details: $order")
            throw e
        }
    }
    
    @Test
    @DisplayName("Should handle quote rejection by the client")
    @Timeout(value = 10, unit = TimeUnit.SECONDS)
    fun testOrderWorkflowWithQuoteRejection() {
        // Create a sample order
        val order = StructuredProductOrder(
            orderId = UUID.randomUUID().toString(),
            productType = "Fixed Income Swap",
            quantity = 5,
            client = "TestClient"
        )

        // Create workflow stub
        val workflow: OrderWorkflow = client.newWorkflowStub(
            OrderWorkflow::class.java,
            WorkflowOptions.newBuilder()
                .setTaskQueue(taskQueue)
                .build()
        )

        // Start workflow execution asynchronously
        println("Starting RFQ workflow execution for rejection test...")
        val workflowExecution = WorkflowClient.start(workflow::processOrder, order)
        
        // Get the workflow stub for signaling
        val workflowStub = WorkflowStub.fromTyped(workflow)
        
        try {
            // In TestWorkflowEnvironment, we need to ensure the workflow has had time to execute
            // the initial activities and set up the state
            testEnv.sleep(java.time.Duration.ofMillis(500))
            
            // Query the workflow to get the current quote
            val quote = workflow.getQuoteStatus()
            println("Quote received: $${quote?.price} for order ${quote?.orderId}")
            
            // Simulate client reviewing the quote
            Assertions.assertNotNull(quote, "Quote should not be null")
            
            // Simulate client rejecting the quote
            println("Client rejecting the quote...")
            workflow.rejectQuote()
            
            // Wait for workflow completion
            val result = workflowStub.getResult(5, TimeUnit.SECONDS, String::class.java)
            
            // Verify the result indicates rejection
            Assertions.assertNotNull(result, "Workflow result should not be null")
            Assertions.assertTrue(result.contains("rejected"), 
                "Result should indicate quote was rejected")
                
            println("RFQ Workflow with rejection completed.")
            println("Workflow result: $result")
            
        } catch (e: Exception) {
            println("Error during workflow execution: ${e.message}")
            println("Order details: $order")
            throw e
        }
    }

    @Test
    @DisplayName("Should handle quote expiry automatically")
    @Timeout(value = 10, unit = TimeUnit.SECONDS)
    fun testOrderWorkflowWithQuoteExpiry() {
        // Create a sample order
        val order = StructuredProductOrder(
            orderId = UUID.randomUUID().toString(),
            productType = "Structured Note",
            quantity = 3,
            client = "TestClient"
        )

        // Create workflow stub
        val workflow: OrderWorkflow = client.newWorkflowStub(
            OrderWorkflow::class.java,
            WorkflowOptions.newBuilder()
                .setTaskQueue(taskQueue)
                .build()
        )

        // Start workflow execution asynchronously
        println("Starting RFQ workflow execution for quote expiry test...")
        val workflowExecution = WorkflowClient.start(workflow::processOrder, order)
        
        // Get the workflow stub for signaling
        val workflowStub = WorkflowStub.fromTyped(workflow)
        
        try {
            // In TestWorkflowEnvironment, we need to ensure the workflow has had time to execute
            // the initial activities and set up the state
            testEnv.sleep(java.time.Duration.ofMillis(500))
            
            // Query the workflow to get the current quote
            val quote = workflow.getQuoteStatus()
            println("Quote received: $${quote?.price} for order ${quote?.orderId}")
            
            // Simulate client reviewing the quote
            Assertions.assertNotNull(quote, "Quote should not be null")
            
            // Fast forward time by 16 minutes to simulate quote expiry (quote expires in 15 minutes)
            testEnv.sleep(java.time.Duration.ofMinutes(16))
            
            // Wait for workflow completion
            val result = workflowStub.getResult(5, TimeUnit.SECONDS, String::class.java)
            
            // Verify the result indicates expiry
            Assertions.assertNotNull(result, "Workflow result should not be null")
            Assertions.assertTrue(result.contains("expired"), 
                "Result should indicate quote was expired")
                
            println("RFQ Workflow with quote expiry completed.")
            println("Workflow result: $result")
            
        } catch (e: Exception) {
            println("Error during workflow execution: ${e.message}")
            println("Order details: $order")
            throw e
        }
    }
}
