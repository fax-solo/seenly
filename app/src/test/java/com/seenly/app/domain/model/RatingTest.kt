package com.seenly.app.domain.model

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class RatingTest {

    @Test
    fun `accepts half point steps within range`() {
        listOf(0.0, 0.5, 5.0, 7.5, 10.0).forEach { value ->
            assertTrue("$value should be valid", Rating.isValid(value))
            assertEquals(value, Rating.ofOrNull(value)?.value ?: Double.NaN, 0.0)
        }
    }

    @Test
    fun `rejects values outside range or off the half point grid`() {
        listOf(-0.5, 10.5, 7.25, 0.1, Double.NaN, Double.POSITIVE_INFINITY).forEach { value ->
            assertFalse("$value should be invalid", Rating.isValid(value))
            assertNull(Rating.ofOrNull(value))
        }
    }

    @Test
    fun `null remains unrated`() {
        assertNull(Rating.ofOrNull(null))
    }

    @Test
    fun `display value drops trailing zero for whole ratings`() {
        assertEquals("8", Rating.ofOrNull(8.0)?.displayValue)
        assertEquals("8.5", Rating.ofOrNull(8.5)?.displayValue)
    }
}
