package com.example.client.controller

import com.example.client.service.OrderError
import com.example.client.service.OrderRequest
import com.example.client.service.OrderResponse
import com.example.client.service.OrderService
import com.example.client.service.OrderStatusResponse
import com.example.client.service.QuoteResponse
import org.slf4j.LoggerFactory
import org.springframework.http.HttpStatus
import org.springframework.http.ResponseEntity
import org.springframework.web.bind.annotation.GetMapping
import org.springframework.web.bind.annotation.PathVariable
import org.springframework.web.bind.annotation.PostMapping
import org.springframework.web.bind.annotation.RequestBody
import org.springframework.web.bind.annotation.RequestMapping
import org.springframework.web.bind.annotation.RestController

@RestController
@RequestMapping("/api")
class OrderController(private val orderService: OrderService) {

    private val logger = LoggerFactory.getLogger(OrderController::class.java)
    
    @PostMapping("/orders")
    fun submitOrder(@RequestBody orderRequest: OrderRequest): ResponseEntity<Any> {
        return orderService.submitOrder(orderRequest).fold(
            onSuccess = { ResponseEntity.ok(it) },
            onFailure = { handleOrderError(it) }
        )
    }

    @GetMapping("/orders")
    fun getAllOrders(): ResponseEntity<List<OrderStatusResponse>> {
        val orders = orderService.getAllOrders()
        return ResponseEntity.ok(orders)
    }

    @GetMapping("/orders/{orderId}")
    fun getOrderStatus(@PathVariable orderId: String): ResponseEntity<Any> {
        return orderService.getOrderStatus(orderId).fold(
            onSuccess = { ResponseEntity.ok(it) },
            onFailure = { handleOrderError(it) }
        )
    }

    @GetMapping("/orders/{orderId}/quote")
    fun getQuote(@PathVariable orderId: String): ResponseEntity<Any> {
        return orderService.getQuote(orderId).fold(
            onSuccess = { ResponseEntity.ok(it) },
            onFailure = { handleOrderError(it) }
        )
    }

    @PostMapping("/orders/{orderId}/accept")
    fun acceptQuote(@PathVariable orderId: String): ResponseEntity<Any> {
        // Check if the order exists first
        val orderStatusResult = orderService.getOrderStatus(orderId)
        
        if (orderStatusResult.isFailure) {
            // If order doesn't exist, handle the error
            val error = orderStatusResult.exceptionOrNull()
            if (error != null) {
                return handleOrderError(error)
            }
        }
        
        // Check if the order has a quote
        val statusResponse = orderStatusResult.getOrNull()
        if (statusResponse?.quote == null) {
            return ResponseEntity.badRequest()
                .body(mapOf("error" to "No quote available for order: $orderId"))
        }
        
        // Using the Result pattern directly for more concise code
        return orderService.acceptQuote(orderId).fold(
            onSuccess = { success ->
                ResponseEntity.ok(mapOf("message" to "Quote accepted successfully")) 
            },
            onFailure = { handleOrderError(it) }
        )
    }

    @PostMapping("/orders/{orderId}/reject")
    fun rejectQuote(@PathVariable orderId: String): ResponseEntity<Any> {
        // Using the Result pattern directly for more concise code
        return orderService.rejectQuote(orderId).fold(
            onSuccess = { 
                ResponseEntity.ok(mapOf("message" to "Quote rejected successfully")) 
            },
            onFailure = { handleOrderError(it) }
        )
    }
    
    /**
     * Helper method to convert OrderErrors to appropriate HTTP responses
     */
    private fun handleOrderError(error: Throwable): ResponseEntity<Any> {
        return when (error) {
            is OrderError.WorkflowNotFound -> {
                logger.debug("Workflow not found: ${error.orderId}")
                ResponseEntity.status(HttpStatus.NOT_FOUND)
                    .body(mapOf("error" to error.errorMessage, "orderId" to error.orderId))
            }
            is OrderError.QuoteNotFound -> {
                logger.debug("Quote not found: ${error.orderId}")
                ResponseEntity.status(HttpStatus.NOT_FOUND)
                    .body(mapOf("error" to error.errorMessage, "orderId" to error.orderId))
            }
            is OrderError.QuoteExpired -> {
                logger.debug("Quote expired: ${error.orderId}, expiry: ${error.expiryTime}")
                ResponseEntity.status(HttpStatus.BAD_REQUEST)
                    .body(mapOf(
                        "error" to error.errorMessage,
                        "orderId" to error.orderId,
                        "expiryTime" to error.expiryTime
                    ))
            }
            is OrderError.WorkflowOperationFailed -> {
                logger.error("Workflow operation failed: ${error.errorMessage}", error.errorCause)
                ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR)
                    .body(mapOf("error" to error.errorMessage))
            }
            is OrderError.UnknownError -> {
                logger.error("Unknown error: ${error.errorMessage}", error.errorCause)
                ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR)
                    .body(mapOf("error" to error.errorMessage))
            }
            else -> {
                logger.error("Unexpected error type: ${error.javaClass.name}", error)
                ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR)
                    .body(mapOf("error" to "An unexpected error occurred: ${error.message}"))
            }
        }
    }
}
