package com.example.client

import com.example.client.service.OrderRequest
import com.example.client.service.OrderService
import io.temporal.client.WorkflowClient
import io.temporal.serviceclient.WorkflowServiceStubs
import io.temporal.serviceclient.WorkflowServiceStubsOptions
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.DisplayName
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.Timeout
import org.junit.jupiter.api.Assertions
import org.slf4j.LoggerFactory
import org.springframework.beans.factory.annotation.Autowired
import org.springframework.boot.test.context.SpringBootTest
import org.springframework.boot.test.web.client.TestRestTemplate
import org.springframework.boot.test.web.server.LocalServerPort
import org.springframework.http.HttpEntity
import org.springframework.http.HttpHeaders
import org.springframework.http.HttpMethod
import org.springframework.http.MediaType
import workflow_api.OrderWorkflow
import java.util.concurrent.TimeUnit

/**
 * This test reproduces the issue where a user cannot accept a quote after submitting an order.
 * The test uses the full Spring application context and makes real HTTP requests to simulate
 * the user journey in the UI.
 */
@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT)
class QuoteAcceptanceTest {

    private val logger = LoggerFactory.getLogger(QuoteAcceptanceTest::class.java)

    @LocalServerPort
    private var port: Int = 0

    @Autowired
    private lateinit var restTemplate: TestRestTemplate
    
    @Autowired
    private lateinit var orderService: OrderService
    
    private lateinit var workflowClient: WorkflowClient
    
    private val baseUrl get() = "http://localhost:$port/api"
    private val headers = HttpHeaders().apply {
        contentType = MediaType.APPLICATION_JSON
    }

    @BeforeEach
    fun setUp() {
        // Connect to Temporal service directly for verification
        val service = WorkflowServiceStubs.newServiceStubs(
            WorkflowServiceStubsOptions.newBuilder()
                .setTarget("127.0.0.1:7233")
                .build()
        )
        workflowClient = WorkflowClient.newInstance(service)
    }

    @Test
    @DisplayName("Should be able to accept a quote after submitting an order")
    @Timeout(value = 30, unit = TimeUnit.SECONDS)
    fun testQuoteAcceptance() {
        // Enable debug logging
        System.setProperty("logging.level.com.example.client", "DEBUG")
        
        // 1. SUBMIT ORDER - simulate what the UI would do
        logger.info("Submitting test order")
        val orderRequest = OrderRequest(
            productType = "Equity Swap",
            quantity = 10,
            client = "TestClient"
        )
        
        val responseEntity = restTemplate.postForEntity(
            "$baseUrl/orders", 
            HttpEntity(orderRequest, headers), 
            Map::class.java
        )
        
        Assertions.assertTrue(responseEntity.statusCode.is2xxSuccessful)
        val orderId = responseEntity.body?.get("orderId") as String
        val workflowId = responseEntity.body?.get("workflowId") as String
        
        logger.info("Order submitted with ID: $orderId, Workflow ID: $workflowId")
        
        // 2. WAIT FOR QUOTE - the quote creation takes a moment
        logger.info("Waiting for quote to be generated")
        Thread.sleep(2000)
        
        // 3. GET QUOTE - simulate polling for quote in UI
        logger.info("Getting quote for order")
        val quoteEntity = restTemplate.getForEntity(
            "$baseUrl/orders/$orderId/quote", 
            Map::class.java
        )
        
        Assertions.assertTrue(quoteEntity.statusCode.is2xxSuccessful)
        val quote = quoteEntity.body
        Assertions.assertNotNull(quote)
        logger.info("Quote received: $quote")
        
        // 4. CHECK INTERNAL STATE - debug the workflowExecutionMap to see if mapping exists
        logger.info("Verifying internal OrderService state")
        val serviceField = OrderService::class.java.getDeclaredField("workflowExecutionMap")
        serviceField.isAccessible = true
        val workflowMap = serviceField.get(orderService)
        logger.info("WorkflowExecutionMap: $workflowMap")
        
        // 5. DIRECTLY CHECK WORKFLOW - see if the workflow is reachable
        val workflowStub = try {
            workflowClient.newWorkflowStub(OrderWorkflow::class.java, workflowId)
            true
        } catch (e: Exception) {
            logger.error("Error getting workflow stub: ${e.message}")
            false
        }
        logger.info("Direct workflow stub creation successful: $workflowStub")
        
        // 6. ACCEPT QUOTE - simulate the "Accept Quote" button in UI
        logger.info("Accepting quote for order $orderId")
        val acceptResponse = restTemplate.postForEntity(
            "$baseUrl/orders/$orderId/accept",
            HttpEntity<Any>(headers), 
            Map::class.java
        )
        
        // Log the actual response for debugging
        logger.info("Accept quote response: ${acceptResponse.statusCode} - ${acceptResponse.body}")
        
        // 7. ASSERT - verify the acceptance was successful
        Assertions.assertTrue(acceptResponse.statusCode.is2xxSuccessful)
        
        // 8. VERIFY ORDER STATUS - check that order status updated after acceptance
        Thread.sleep(1000) // Give the workflow a moment to process the signal
        val statusEntity = restTemplate.getForEntity(
            "$baseUrl/orders/$orderId", 
            Map::class.java
        )
        
        Assertions.assertTrue(statusEntity.statusCode.is2xxSuccessful)
        val status = statusEntity.body?.get("status") as String
        logger.info("Final order status: $status")
    }
}
