package com.seenly.app.domain.model

import kotlin.math.abs
import kotlin.math.round

/**
 * Personal rating in the range [0, 10] with half-point increments.
 * Constructed through [of] so invalid values cannot enter the domain.
 */
@JvmInline
value class Rating private constructor(val value: Double) {

    /** Rating formatted with at most one decimal place, e.g. "7.5". */
    val displayValue: String
        get() = if (value % 1.0 == 0.0) value.toInt().toString() else value.toString()

    companion object {
        const val MIN = 0.0
        const val MAX = 10.0
        const val STEP = 0.5

        fun ofOrNull(value: Double?): Rating? {
            if (value == null) return null
            return if (isValid(value)) Rating(value) else null
        }

        fun isValid(value: Double): Boolean {
            if (value.isNaN() || value.isInfinite()) return false
            if (value < MIN || value > MAX) return false
            val doubled = value / STEP
            return abs(doubled - round(doubled)) < 1e-6
        }

        fun isValidOrNull(value: Double?): Boolean = value == null || isValid(value)
    }
}
