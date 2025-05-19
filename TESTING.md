# Testing Temporal Workflows

This document describes the testing strategies and patterns used in this project for testing Temporal workflows in a trading application context.

## Testing Approaches

The project uses several complementary approaches to test Temporal workflows:

1. **Unit Testing**: Testing individual components in isolation with mocks
2. **Integration Testing**: Testing component interactions with TestWorkflowEnvironment
3. **End-to-End Testing**: Testing the entire system with real Temporal service
4. **Error Handling Testing**: Testing error conditions and edge cases

## Unit Testing with Mocks

Unit testing focuses on testing the service layer that interacts with Temporal. The key challenges are:
- Mocking Temporal client interactions
- Testing error conditions
- Ensuring the service correctly handles the Result pattern

### Example: OrderServiceTest

```kotlin
@ExtendWith(MockitoExtension::class)
class OrderServiceTest {
    @Mock
    private lateinit var workflowClient: WorkflowClient
    
    @Mock
    private lateinit var orderWorkflow: OrderWorkflow
    
    private lateinit var orderService: OrderService
    
    @BeforeEach
    fun setup() {
        orderService = OrderService(workflowClient, "TEST_TASK_QUEUE", 2L)
    }
    
    @Test
    fun submitOrderSuccess() {
        // Create a test subclass to avoid static mocking
        val testOrderService = object : OrderService(workflowClient, "TEST_TASK_QUEUE", 2L) {
            override fun startWorkflow(workflow: OrderWorkflow, order: WorkflowStructuredProductOrder): WorkflowExecution {
                val mockExecution = mock(WorkflowExecution::class.java)
                `when`(mockExecution.workflowId).thenReturn("order-${order.orderId}")
                return mockExecution
            }
        }
        
        // Test the service
        val result = testOrderService.submitOrder(OrderRequest(/*...*/))
        
        // Assert success result
        result.assertSuccess()
    }
    
    @Test
    fun getQuoteSuccess() {
        // Given
        val orderId = UUID.randomUUID().toString()
        val workflowId = "order-$orderId"
        val quote = PriceQuote(
            orderId = orderId, 
            price = 500.0, 
            expiresAt = System.currentTimeMillis() + 900000,
            isExpired = false
        )
        
        // Use test subclass with overridden methods
        val testOrderService = object : OrderService(workflowClient, "TEST_TASK_QUEUE", 2L) {
            override fun getQuoteFromWorkflow(workflowId: String): PriceQuote? = quote
        }
        
        // When
        val result = testOrderService.getOrderStatus(orderId)
        
        // Then
        val status = result.assertSuccess()
        assertEquals(orderId, status.orderId)
        assertNotNull(status.quote)
        assertEquals(500.0, status.quote?.price)
    }
}
```

### Key Unit Testing Patterns

1. **Testable Design**: The service classes use protected methods that can be overridden in tests:

   ```kotlin
   protected open fun getQuoteFromWorkflow(workflowId: String): PriceQuote? {
       val workflow = workflowClient.newWorkflowStub(OrderWorkflow::class.java, workflowId)
       val workflowStubImpl = WorkflowStub.fromTyped(workflow)
       return workflowStubImpl.query("getQuoteStatus", PriceQuote::class.java, 5, TimeUnit.SECONDS)
   }
   ```

2. **Test Subclassing**: Using anonymous objects to override methods:

   ```kotlin
   val testOrderService = object : OrderService(workflowClient, taskQueueName, timeout) {
       override fun getQuoteFromWorkflow(workflowId: String): PriceQuote? = mockQuote
   }
   ```

3. **Result Assertions**: Custom assertions for the Result type:

   ```kotlin
   fun <T> Result<T>.assertSuccess(): T {
       assertTrue(isSuccess, { 
           "Expected Result to be successful but was failure with: ${exceptionOrNull()?.message}" 
       })
       return getOrThrow()
   }
   ```

## Integration Testing with TestWorkflowEnvironment

The `TestWorkflowEnvironment` class from Temporal's testing library provides a way to test workflows without connecting to a real Temporal service.

### Example: OrderWorkflowE2ETest

```kotlin
class OrderWorkflowE2ETest {
    private lateinit var testEnv: TestWorkflowEnvironment
    private lateinit var client: WorkflowClient
    private val taskQueue = "ORDER_TASK_QUEUE"

    @BeforeEach
    fun setUp() {
        // Create the test workflow environment
        testEnv = TestWorkflowEnvironment.newInstance()
        client = testEnv.workflowClient
        
        // Register workflow and activities
        val worker = testEnv.newWorker(taskQueue)
        worker.registerWorkflowImplementationTypes(TestOrderWorkflowImpl::class.java)
        worker.registerActivitiesImplementations(TestOrderActivitiesImpl())
        testEnv.start()
    }
    
    @Test
    @Timeout(value = 10, unit = TimeUnit.SECONDS)
    fun testOrderWorkflowWithQuoteAcceptance() {
        // Create sample order
        val order = StructuredProductOrder(
            orderId = UUID.randomUUID().toString(),
            productType = "Equity Swap",
            quantity = 10,
            client = "TestClient"
        )
        
        // Start workflow
        val workflow = client.newWorkflowStub(OrderWorkflow::class.java, options)
        WorkflowClient.start(workflow::processOrder, order)
        
        // Test query
        val quote = workflow.getQuoteStatus()
        
        // Test signal
        workflow.acceptQuote()
        
        // Verify result
        val result = WorkflowStub.fromTyped(workflow).getResult(String::class.java)
        assertTrue(result.contains("completed successfully"))
    }
}
```

### Key Integration Testing Patterns

1. **Test Environment**: Using `TestWorkflowEnvironment` to simulate Temporal:

   ```kotlin
   testEnv = TestWorkflowEnvironment.newInstance()
   client = testEnv.workflowClient
   ```

2. **Test Implementations**: Using simplified workflow and activity implementations:

   ```kotlin
   worker.registerWorkflowImplementationTypes(TestOrderWorkflowImpl::class.java)
   worker.registerActivitiesImplementations(TestOrderActivitiesImpl())
   ```

3. **Time Manipulation**: Controlling time in tests:

   ```kotlin
   // Skip ahead in time to test timeouts or expiry
   testEnv.sleep(Duration.ofMinutes(20))
   ```

4. **Testing Signals and Queries**: Verifying workflow interaction points:

   ```kotlin
   // Test a query
   val quote = workflow.getQuoteStatus()
   assertNotNull(quote)
   
   // Test a signal
   workflow.acceptQuote()
   ```

## Error Handling Testing

The `QuoteErrorHandlingTest` demonstrates how to test error conditions in the API layer:

```kotlin
@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT)
class QuoteErrorHandlingTest {
    @LocalServerPort
    private var port: Int = 0

    @Autowired
    private lateinit var restTemplate: TestRestTemplate
    
    @MockBean
    private lateinit var orderService: OrderService
    
    @Test
    @DisplayName("Should handle expired quotes properly")
    fun testExpiredQuoteHandling() {
        // Setup expired quote
        val orderId = UUID.randomUUID().toString()
        val expiredQuote = QuoteResponse(
            orderId = orderId,
            price = 1000.0,
            expiresAt = System.currentTimeMillis() - 1000, // expired
            isExpired = true
        )
        
        // Mock OrderService responses
        Mockito.`when`(orderService.getOrderStatus(orderId)).thenReturn(
            Result.success(OrderStatusResponse(
                orderId = orderId,
                workflowId = "order-$orderId",
                status = "IN_PROGRESS",
                quote = expiredQuote
            ))
        )
        
        Mockito.`when`(orderService.acceptQuote(orderId)).thenReturn(
            Result.failure(OrderError.QuoteExpired("Quote expired", orderId, expiredQuote.expiresAt))
        )
        
        // Try to accept an expired quote
        val response = restTemplate.postForEntity(
            "http://localhost:$port/api/orders/$orderId/accept",
            HttpEntity<Any>(HttpHeaders().apply { contentType = MediaType.APPLICATION_JSON }),
            Map::class.java
        )
        
        // Verify API error response
        assertEquals(HttpStatus.BAD_REQUEST, response.statusCode)
        assertTrue(response.body?.get("error").toString().contains("expired"))
    }
}
```

### Key Error Handling Patterns

1. **Custom Error Types**: Using sealed classes for error modeling:

   ```kotlin
   sealed class OrderError(
       message: String,
       val orderId: String
   ) : Exception(message) {
       class WorkflowNotFound(message: String, orderId: String) : OrderError(message, orderId)
       class QuoteExpired(message: String, orderId: String, val expiryTime: Long) : OrderError(message, orderId)
       // Other error types...
   }
   ```

2. **Result Pattern**: Using Kotlin's Result class for error handling:

   ```kotlin
   fun acceptQuote(orderId: String): Result<Boolean> {
       return try {
           // Logic that might throw exceptions
           Result.success(true)
       } catch (e: Exception) {
           Result.failure(mapWorkflowException(e, orderId))
       }
   }
   ```

3. **Error Mapping**: Converting workflow exceptions to domain errors:

   ```kotlin
   private fun mapWorkflowException(e: Exception, orderId: String): OrderError {
       return when (e) {
           is WorkflowNotFoundException -> OrderError.WorkflowNotFound(e.message ?: "Workflow not found", orderId)
           is WorkflowExecutionAlreadyCompletedException -> OrderError.WorkflowCompleted(e.message ?: "Workflow already completed", orderId)
           // Other mappings
           else -> OrderError.Unknown("Unexpected error: ${e.message}", orderId)
       }
   }
   ```

## Testing Best Practices

1. **Avoid Static Mocking**: Use testable design patterns instead of tools like PowerMock or Mockito static mocks.

2. **Test Isolation**: Ensure tests don't depend on each other or on external systems.

3. **Test Subclasses**: Use anonymous objects to override protected methods for testing.

4. **Custom Assertions**: Create domain-specific assertions like `assertSuccess()` and `assertFailure()`.

5. **TestWorkflowEnvironment**: Use Temporal's test environment for workflow testing.

6. **Time Control**: Use the test environment's time control features to test time-dependent logic.

7. **Error Scenarios**: Test both success and error paths thoroughly.

8. **API Testing**: Test error handling at the API level with Spring's test tools.

## Running the Tests

To run all tests:
```bash
./gradlew test
```

To run specific test classes:
```bash
./gradlew test --tests "com.example.client.OrderWorkflowE2ETest"
```

To run tests with a specific tag:
```bash
./gradlew test -Dtags="e2e"
```
