package com.example.client.controller

import com.example.client.service.OrderError
import com.example.client.service.OrderRequest
import com.example.client.service.OrderService
import com.fasterxml.jackson.databind.ObjectMapper
import org.junit.jupiter.api.Test
import org.mockito.Mockito.*
import org.springframework.beans.factory.annotation.Autowired
import org.springframework.boot.test.autoconfigure.web.servlet.WebMvcTest
import org.springframework.boot.test.mock.mockito.MockBean
import org.springframework.http.MediaType
import org.springframework.test.web.servlet.MockMvc
import org.springframework.test.web.servlet.request.MockMvcRequestBuilders.*
import org.springframework.test.web.servlet.result.MockMvcResultMatchers.*
import java.util.UUID

@WebMvcTest(OrderController::class)
class OrderControllerTest {

    @Autowired
    private lateinit var mockMvc: MockMvc
    
    @Autowired
    private lateinit var objectMapper: ObjectMapper
    
    @MockBean
    private lateinit var orderService: OrderService
    
    @Test
    fun `should submit a new order`() {
        // Given
        val orderRequest = OrderRequest(
            productType = "Equity Swap",
            quantity = 10,
            client = "TestClient"
        )
        
        val orderId = UUID.randomUUID().toString()
        val workflowId = "order-$orderId"
        
        `when`(orderService.submitOrder(orderRequest)).thenReturn(
            Result.success(com.example.client.service.OrderResponse(
                orderId = orderId,
                workflowId = workflowId,
                status = "SUBMITTED"
            ))
        )
        
        // When & Then
        mockMvc.perform(
            post("/api/orders")
                .contentType(MediaType.APPLICATION_JSON)
                .content(objectMapper.writeValueAsString(orderRequest))
        )
            .andExpect(status().isOk)
            .andExpect(jsonPath("$.orderId").exists())
            .andExpect(jsonPath("$.workflowId").value(workflowId))
            .andExpect(jsonPath("$.status").value("SUBMITTED"))
    }
    
    @Test
    fun `should get all orders`() {
        // Given
        val orderList = listOf(
            com.example.client.service.OrderStatusResponse(
                orderId = UUID.randomUUID().toString(),
                workflowId = "workflow-1",
                status = "IN_PROGRESS",
                quote = null
            ),
            com.example.client.service.OrderStatusResponse(
                orderId = UUID.randomUUID().toString(),
                workflowId = "workflow-2",
                status = "COMPLETED",
                quote = null
            )
        )
        
        // getAllOrders likely doesn't return Result since it's just fetching from memory
        `when`(orderService.getAllOrders()).thenReturn(orderList)
        
        // When & Then
        mockMvc.perform(get("/api/orders"))
            .andExpect(status().isOk)
            .andExpect(jsonPath("$[0].orderId").exists())
            .andExpect(jsonPath("$[0].status").value("IN_PROGRESS"))
            .andExpect(jsonPath("$[1].status").value("COMPLETED"))
    }
    
    @Test
    fun `should get order status by id`() {
        // Given
        val orderId = UUID.randomUUID().toString()
        val orderStatus = com.example.client.service.OrderStatusResponse(
            orderId = orderId,
            workflowId = "workflow-3",
            status = "IN_PROGRESS",
            quote = com.example.client.service.QuoteResponse(
                orderId = orderId,
                price = 500.0,
                expiresAt = System.currentTimeMillis() + 900000, // 15 minutes from now
                isExpired = false
            )
        )
        
        `when`(orderService.getOrderStatus(orderId)).thenReturn(Result.success(orderStatus))
        
        // When & Then
        mockMvc.perform(get("/api/orders/{orderId}", orderId))
            .andExpect(status().isOk)
            .andExpect(jsonPath("$.orderId").value(orderId))
            .andExpect(jsonPath("$.status").value("IN_PROGRESS"))
            .andExpect(jsonPath("$.quote").exists())
            .andExpect(jsonPath("$.quote.price").value(500.0))
    }
    
    @Test
    fun `should return 404 when order not found`() {
        // Given
        val nonExistentOrderId = UUID.randomUUID().toString()
        `when`(orderService.getOrderStatus(nonExistentOrderId)).thenReturn(
            Result.failure(OrderError.WorkflowNotFound("Workflow not found for order ID: $nonExistentOrderId", nonExistentOrderId))
        )
        
        // When & Then
        mockMvc.perform(get("/api/orders/{orderId}", nonExistentOrderId))
            .andExpect(status().isNotFound)
    }
    
    @Test
    fun `should accept quote`() {
        // Given
        val orderId = UUID.randomUUID().toString()
        val orderStatus = com.example.client.service.OrderStatusResponse(
            orderId = orderId,
            workflowId = "workflow-1",
            status = "IN_PROGRESS",
            quote = com.example.client.service.QuoteResponse(
                orderId = orderId,
                price = 500.0,
                expiresAt = System.currentTimeMillis() + 900000, // 15 minutes from now
                isExpired = false
            )
        )
        
        `when`(orderService.getOrderStatus(orderId)).thenReturn(Result.success(orderStatus))
        `when`(orderService.acceptQuote(orderId)).thenReturn(Result.success(true))
        
        // When & Then
        mockMvc.perform(post("/api/orders/{orderId}/accept", orderId))
            .andExpect(status().isOk)
            .andExpect(jsonPath("$.message").value("Quote accepted successfully"))
    }
    
    @Test
    fun `should reject quote`() {
        // Given
        val orderId = UUID.randomUUID().toString()
        val orderStatus = com.example.client.service.OrderStatusResponse(
            orderId = orderId,
            workflowId = "workflow-1",
            status = "IN_PROGRESS",
            quote = com.example.client.service.QuoteResponse(
                orderId = orderId,
                price = 500.0,
                expiresAt = System.currentTimeMillis() + 900000, // 15 minutes from now
                isExpired = false
            )
        )
        
        `when`(orderService.getOrderStatus(orderId)).thenReturn(Result.success(orderStatus))
        `when`(orderService.rejectQuote(orderId)).thenReturn(Result.success(true))
        
        // When & Then
        mockMvc.perform(post("/api/orders/{orderId}/reject", orderId))
            .andExpect(status().isOk)
            .andExpect(jsonPath("$.message").value("Quote rejected successfully"))
    }
}
