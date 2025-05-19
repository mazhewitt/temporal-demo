package com.example.client.service

/**
 * Extension functions for Result<T> to make working with Results more convenient
 */

/**
 * Maps a Result<T> to Result<R> using transform functions for both success and failure cases
 *
 * @param transform Function to transform the success value
 * @param transformError Function to transform the error 
 * @return A new Result with transformed success or error value
 */
fun <T, R> Result<T>.mapBoth(
    transform: (T) -> R,
    transformError: (Throwable) -> Throwable = { it }
): Result<R> = when {
    isSuccess -> Result.success(transform(getOrThrow()))
    else -> Result.failure(transformError(exceptionOrNull()!!))
}

/**
 * Maps a Result<T> to a new Result<R> only if the original result was a success
 * 
 * @param transform Function that produces a new Result from the success value
 * @return A new Result from the transform function or the original failure
 */
inline fun <T, R> Result<T>.flatMap(transform: (T) -> Result<R>): Result<R> {
    return when {
        isSuccess -> transform(getOrThrow()) 
        else -> Result.failure(exceptionOrNull()!!)
    }
}

/**
 * Convenient function to convert a nullable value to a Result
 * 
 * @param errorMessage Message for the error if the value is null
 * @return Result.success(this) if not null, or Result.failure(exception) if null
 */
inline fun <T : Any, E : Throwable> T?.toResult(
    errorSupplier: () -> E
): Result<T> = when (this) {
    null -> Result.failure(errorSupplier())
    else -> Result.success(this)
}

/**
 * Convert a throwable to a specific error type if it matches the given predicate
 *
 * @param predicate Function to test if a throwable should be converted
 * @param transform Function to convert the throwable to a specific error type
 * @return Result with converted error or original error if predicate doesn't match
 */
inline fun <T, R : Throwable> Result<T>.mapError(
    predicate: (Throwable) -> Boolean,
    transform: (Throwable) -> R
): Result<T> {
    if (isFailure) {
        val error = exceptionOrNull()!!
        if (predicate(error)) {
            return Result.failure(transform(error))
        }
    }
    return this
}

/**
 * Convert an error of one type to another type of error
 * 
 * @param transform Function to transform one error type to another
 * @return Result with transformed error or original result if successful
 */
inline fun <T, reified E : Throwable, R : Throwable> Result<T>.mapErrorOfType(
    transform: (E) -> R
): Result<T> {
    if (isFailure) {
        val error = exceptionOrNull()
        if (error is E) {
            return Result.failure(transform(error))
        }
    }
    return this
}

/**
 * Helper function to simplify testing by handling exceptions in tests
 * 
 * @param block The code block to execute safely
 * @return The result of the block wrapped in Result or Result.failure if an exception was thrown
 */
inline fun <T> runCatchingTest(block: () -> T): Result<T> {
    return try {
        Result.success(block())
    } catch (e: Throwable) {
        Result.failure(e)
    }
}
