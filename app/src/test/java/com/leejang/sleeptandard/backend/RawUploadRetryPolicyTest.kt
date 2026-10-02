package com.leejang.sleeptandard.backend

import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class RawUploadRetryPolicyTest {
    @Test
    fun serverRetryableFalseStopsPermanentConflictImmediately() {
        assertFalse(
            RawUploadRetryPolicy.shouldRetry(
                code = 409,
                serverRetryable = false
            )
        )
    }

    @Test
    fun serverRetryableTrueRetriesMissingPartConflict() {
        assertTrue(
            RawUploadRetryPolicy.shouldRetry(
                code = 409,
                serverRetryable = true
            )
        )
    }

    @Test
    fun expiredPresignedUrlCanBeRetried() {
        assertTrue(
            RawUploadRetryPolicy.shouldRetry(
                code = 403,
                serverRetryable = null,
                presignedS3Request = true
            )
        )
    }

    @Test
    fun fallbackRetriesOnlyTransientHttpFailures() {
        assertTrue(RawUploadRetryPolicy.shouldRetry(503, null))
        assertFalse(RawUploadRetryPolicy.shouldRetry(400, null))
    }
}
