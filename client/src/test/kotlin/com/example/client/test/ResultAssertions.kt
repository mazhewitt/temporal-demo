package com.example.client.test

import com.example.client.service.OrderError
import org.junit.jupiter.api.Assertions
import kotlin.contracts.ExperimentalContracts
import kotlin.contracts.contract
import kotlin.reflect.KClass

/**
 * Test utilities for working with Result<T> in tests
 */

/**
 * Asserts that a Result is successful and returns the enclosed value
 * 
 * @return The success value if the Result is a success
 * @throws AssertionError if the Result is a failure
 */
@OptIn(ExperimentalContracts::class)
fun <T> Result<T>.assertSuccess(): T {
    contract {
        returns() implies (this@assertSuccess is Result<T>)
    }
    
    Assertions.assertTrue(isSuccess, {
        val error = exceptionOrNull()
        "Expected Result to be successful but was failure with: ${error?.message ?: error}"
    })
    
    return getOrThrow()
}

/**
 * Asserts that a Result is a failure and returns the exception
 * 
 * @return The exception if the Result is a failure
 * @throws AssertionError if the Result is a success
 */
@OptIn(ExperimentalContracts::class)
fun <T> Result<T>.assertFailure(): Throwable {
    contract {
        returns() implies (this@assertFailure is Result<T>)
    }
    
    Assertions.assertTrue(isFailure, {
        "Expected Result to be failure but was success with: ${getOrNull()}"
    })
    
    return exceptionOrNull()!!
}

/**
 * Asserts that a Result contains a failure of a specific type and returns that exception
 * 
 * @param message Optional message to display if the assertion fails
 * @return The exception of type E if present
 * @throws AssertionError if the Result is a success or the exception is not of type E
 */
@OptIn(ExperimentalContracts::class)
inline fun <T, reified E : Throwable> Result<T>.assertFailureOfType(message: String? = null): E {
    contract {
        returns() implies (this@assertFailureOfType is Result<T>)
    }
    
    val exception = assertFailure()
    
    Assertions.assertTrue(exception is E, {
        message ?: "Expected failure of type ${E::class.simpleName} but got ${exception::class.simpleName}"
    })
    
    return exception as E
}

/**
 * Specifically for OrderError subclasses - asserts that a Result contains a specific OrderError subtype
 * 
 * @param errorClass The expected OrderError subclass
 * @return The OrderError instance if it matches the expected type
 * @throws AssertionError if the Result is a success or contains the wrong type of error
 */
inline fun <T, reified E : OrderError> Result<T>.assertOrderErrorOfType(): E {
    val exception = assertFailure()
    
    Assertions.assertTrue(exception is E, {
        "Expected OrderError of type ${E::class.simpleName} but got ${exception::class.simpleName}"
    })
    
    return exception as E
}

/**
 * Helper function to verify Result values with custom assertions
 *
 * @param assertions The assertions to run on the success value
 * @throws AssertionError if the Result is a failure or if any assertion fails
 */
inline fun <T> Result<T>.assertAll(assertions: (T) -> Unit) {
    val value = assertSuccess()
    assertions(value)
}

/**
 * Helper function to verify that an OrderError contains the expected information
 * 
 * @param orderId The expected orderId in the error
 * @param messageContains Optional substring that should be present in the error message
 * @return The OrderError for further assertions
 */
fun <T> Result<T>.assertOrderError(orderId: String, messageContains: String? = null): OrderError {
    val error = assertFailure()
    
    Assertions.assertTrue(error is OrderError, {
        "Expected OrderError but got ${error::class.simpleName}"
    })
    
    error as OrderError
    
    when (error) {
        is OrderError.WorkflowNotFound -> Assertions.assertEquals(orderId, error.orderId)
        is OrderError.QuoteNotFound -> Assertions.assertEquals(orderId, error.orderId)
        is OrderError.QuoteExpired -> Assertions.assertEquals(orderId, error.orderId)
        is OrderError.WorkflowOperationFailed -> {}
        is OrderError.UnknownError -> {}
        is OrderError.WorkflowInProgress -> {} // Workflow in progress, no specific validation needed
    }
    
    if (messageContains != null) {
        // For OrderError subclasses, we need to check the specific error message field
        val actualMessage = when (error) {
            is OrderError.WorkflowNotFound -> error.errorMessage
            is OrderError.QuoteNotFound -> error.errorMessage
            is OrderError.QuoteExpired -> error.errorMessage
            is OrderError.WorkflowOperationFailed -> error.errorMessage
            is OrderError.UnknownError -> error.errorMessage
            is OrderError.WorkflowInProgress -> error.message // Use the base message for WorkflowInProgress
        }
        
        Assertions.assertTrue(actualMessage.contains(messageContains), {
            "Expected error message to contain '$messageContains' but was '$actualMessage'"
        })
    }
    
    return error
}
