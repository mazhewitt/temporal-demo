package com.example.client.controller

import com.example.client.service.OrderRequest
import com.example.client.service.OrderResponse
import com.example.client.service.OrderService
import com.example.client.service.OrderStatusResponse
import com.example.client.service.QuoteResponse
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

    @PostMapping("/orders")
    fun submitOrder(@RequestBody orderRequest: OrderRequest): ResponseEntity<OrderResponse> {
        val response = orderService.submitOrder(orderRequest)
        return ResponseEntity.ok(response)
    }

    @GetMapping("/orders")
    fun getAllOrders(): ResponseEntity<List<OrderStatusResponse>> {
        val orders = orderService.getAllOrders()
        return ResponseEntity.ok(orders)
    }

    @GetMapping("/orders/{orderId}")
    fun getOrderStatus(@PathVariable orderId: String): ResponseEntity<OrderStatusResponse> {
        val status = orderService.getOrderStatus(orderId)
            ?: return ResponseEntity.notFound().build()
        return ResponseEntity.ok(status)
    }

    @GetMapping("/orders/{orderId}/quote")
    fun getQuote(@PathVariable orderId: String): ResponseEntity<QuoteResponse> {
        val quote = orderService.getQuote(orderId)
            ?: return ResponseEntity.notFound().build()
        return ResponseEntity.ok(quote)
    }

    @PostMapping("/orders/{orderId}/accept")
    fun acceptQuote(@PathVariable orderId: String): ResponseEntity<Map<String, String>> {
        val result = orderService.acceptQuote(orderId)
        return if (result) {
            ResponseEntity.ok(mapOf("message" to "Quote accepted successfully"))
        } else {
            ResponseEntity.notFound().build()
        }
    }

    @PostMapping("/orders/{orderId}/reject")
    fun rejectQuote(@PathVariable orderId: String): ResponseEntity<Map<String, String>> {
        val result = orderService.rejectQuote(orderId)
        return if (result) {
            ResponseEntity.ok(mapOf("message" to "Quote rejected successfully"))
        } else {
            ResponseEntity.notFound().build()
        }
    }
}
