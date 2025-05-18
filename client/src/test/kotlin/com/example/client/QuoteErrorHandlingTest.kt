package com.example.client

import com.example.client.service.OrderRequest
import com.example.client.service.OrderService
import com.example.client.service.QuoteResponse
import io.temporal.serviceclient.WorkflowServiceStubs
import io.temporal.serviceclient.WorkflowServiceStubsOptions
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.DisplayName
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.Timeout
import org.junit.jupiter.api.Assertions
import org.mockito.Mockito
import org.slf4j.LoggerFactory
import org.springframework.beans.factory.annotation.Autowired
import org.springframework.boot.test.context.SpringBootTest
import org.springframework.boot.test.mock.mockito.MockBean
import org.springframework.boot.test.web.client.TestRestTemplate
import org.springframework.boot.test.web.server.LocalServerPort
import org.springframework.http.HttpEntity
import org.springframework.http.HttpHeaders
import org.springframework.http.HttpStatus
import org.springframework.http.MediaType
import workflow_api.OrderWorkflow
import workflow_api.PriceQuote
import java.util.UUID
import java.util.concurrent.TimeUnit

/**
 * This test verifies the error handling for quote acceptance
 */
@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT)
class QuoteErrorHandlingTest {

    private val logger = LoggerFactory.getLogger(QuoteErrorHandlingTest::class.java)

    @LocalServerPort
    private var port: Int = 0

    @Autowired
    private lateinit var restTemplate: TestRestTemplate
    
    @MockBean
    private lateinit var orderService: OrderService
    
    private val baseUrl get() = "http://localhost:$port/api"
    private val headers = HttpHeaders().apply {
        contentType = MediaType.APPLICATION_JSON
    }

    @Test
    @DisplayName("Should handle expired quotes properly")
    fun testExpiredQuoteHandling() {
        // Setup - mock an order with an expired quote
        val orderId = UUID.randomUUID().toString()
        val expiredQuote = QuoteResponse(
            orderId = orderId,
            price = 1000.0,
            expiresAt = System.currentTimeMillis() - 1000, // expired
            isExpired = true
        )
        
        // Mock OrderService responses
        Mockito.`when`(orderService.getOrderStatus(orderId)).thenReturn(
            com.example.client.service.OrderStatusResponse(
                orderId = orderId,
                workflowId = "order-$orderId",
                status = "IN_PROGRESS",
                quote = expiredQuote
            )
        )
        
        // Try to accept an expired quote
        val acceptResponse = restTemplate.postForEntity(
            "$baseUrl/orders/$orderId/accept",
            HttpEntity<Any>(headers),
            Map::class.java
        )
        
        // Verify response
        logger.info("Accept expired quote response: ${acceptResponse.statusCode} - ${acceptResponse.body}")
        Assertions.assertEquals(HttpStatus.BAD_REQUEST, acceptResponse.statusCode)
        Assertions.assertTrue(acceptResponse.body?.get("error").toString().contains("expired"))
    }
    
    @Test
    @DisplayName("Should handle non-existent orders properly")
    fun testNonExistentOrderHandling() {
        // Setup - mock service to return null for a non-existent order
        val nonExistentOrderId = UUID.randomUUID().toString()
        Mockito.`when`(orderService.getOrderStatus(nonExistentOrderId)).thenReturn(null)
        
        // Try to accept a quote for a non-existent order
        val acceptResponse = restTemplate.postForEntity(
            "$baseUrl/orders/$nonExistentOrderId/accept",
            HttpEntity<Any>(headers),
            Map::class.java
        )
        
        // Verify response
        logger.info("Accept non-existent order response: ${acceptResponse.statusCode} - ${acceptResponse.body}")
        Assertions.assertEquals(HttpStatus.NOT_FOUND, acceptResponse.statusCode)
        Assertions.assertTrue(acceptResponse.body?.get("error").toString().contains("not found"))
    }
    
    @Test
    @DisplayName("Should handle orders without quotes properly")
    fun testOrderWithoutQuoteHandling() {
        // Setup - mock an order without a quote
        val orderId = UUID.randomUUID().toString()
        
        // Mock OrderService responses
        Mockito.`when`(orderService.getOrderStatus(orderId)).thenReturn(
            com.example.client.service.OrderStatusResponse(
                orderId = orderId,
                workflowId = "order-$orderId",
                status = "IN_PROGRESS",
                quote = null
            )
        )
        
        // Try to accept a non-existent quote
        val acceptResponse = restTemplate.postForEntity(
            "$baseUrl/orders/$orderId/accept",
            HttpEntity<Any>(headers),
            Map::class.java
        )
        
        // Verify response
        logger.info("Accept non-existent quote response: ${acceptResponse.statusCode} - ${acceptResponse.body}")
        Assertions.assertEquals(HttpStatus.BAD_REQUEST, acceptResponse.statusCode)
        Assertions.assertTrue(acceptResponse.body?.get("error").toString().contains("No quote"))
    }
}
