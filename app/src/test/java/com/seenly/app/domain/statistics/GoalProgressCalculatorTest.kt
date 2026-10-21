package com.seenly.app.domain.statistics

import com.seenly.app.domain.model.Goal
import com.seenly.app.domain.model.GoalMetric
import com.seenly.app.domain.model.GoalType
import com.seenly.app.domain.model.MediaType
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test
import java.time.LocalDate

class GoalProgressCalculatorTest {

    private val today = LocalDate.of(2026, 5, 20) // Wednesday

    private val input = StatisticsInput(
        viewings = listOf(
            CompletedViewingTestFactory.viewing(1, MediaType.ANIME_SERIES, 101, LocalDate.of(2026, 5, 18), 25),
            CompletedViewingTestFactory.viewing(
                1, MediaType.ANIME_SERIES, 101, LocalDate.of(2026, 5, 19), 25,
                com.seenly.app.domain.model.EventType.REWATCH,
            ),
            CompletedViewingTestFactory.viewing(1, MediaType.ANIME_SERIES, 102, LocalDate.of(2026, 5, 20), 25),
            CompletedViewingTestFactory.viewing(1, MediaType.ANIME_SERIES, 103, LocalDate.of(2026, 5, 17), 25),
            CompletedViewingTestFactory.viewing(200, MediaType.MOVIE, null, LocalDate.of(2026, 5, 19), 120),
            // Future-dated events are never counted.
            CompletedViewingTestFactory.viewing(1, MediaType.ANIME_SERIES, 104, LocalDate.of(2026, 5, 21), 30),
        ),
    )

    private fun goal(
        type: GoalType,
        metric: GoalMetric,
        target: Int,
        active: Boolean = true,
        id: Long = 1,
    ) = Goal(id = id, type = type, metric = metric, target = target, active = active)

    @Test
    fun `weekly period starts on Monday and ends on Sunday`() {
        val period = GoalProgressCalculator.periodFor(GoalType.WEEKLY, today)
        assertEquals(LocalDate.of(2026, 5, 18), period.start)
        assertEquals(LocalDate.of(2026, 5, 24), period.endInclusive)

        val monthly = GoalProgressCalculator.periodFor(GoalType.MONTHLY, today)
        assertEquals(LocalDate.of(2026, 5, 1), monthly.start)
        assertEquals(LocalDate.of(2026, 5, 31), monthly.endInclusive)

        val yearly = GoalProgressCalculator.periodFor(GoalType.YEARLY, today)
        assertEquals(LocalDate.of(2026, 1, 1), yearly.start)
        assertEquals(LocalDate.of(2026, 12, 31), yearly.endInclusive)
    }

    @Test
    fun `episode goals count distinct episodes and rewatches do not inflate`() {
        val progress = GoalProgressCalculator.calculate(
            listOf(goal(GoalType.WEEKLY, GoalMetric.EPISODES, 2)),
            input,
            today,
        ).single()

        assertEquals(2, progress.current)
        assertEquals(1.0f, progress.fraction, 0.0f)
    }

    @Test
    fun `movie and title goals use different counting rules`() {
        val progress = GoalProgressCalculator.calculate(
            listOf(
                goal(GoalType.WEEKLY, GoalMetric.MOVIES, 4, id = 1),
                goal(GoalType.WEEKLY, GoalMetric.TITLES, 2, id = 2),
            ),
            input,
            today,
        )
        val movie = progress.first { it.goal.metric == GoalMetric.MOVIES }
        val titles = progress.first { it.goal.metric == GoalMetric.TITLES }

        assertEquals(1, movie.current)
        assertEquals(0.25f, movie.fraction, 0.0f)
        assertEquals(2, titles.current)
        assertEquals(1.0f, titles.fraction, 0.0f)
    }

    @Test
    fun `minutes goals sum only known runtime inside the period`() {
        val progress = GoalProgressCalculator.calculate(
            listOf(goal(GoalType.WEEKLY, GoalMetric.MINUTES, 100)),
            input,
            today,
        ).single()

        assertEquals(195, progress.current)
        assertEquals(1.0f, progress.fraction, 0.0f)
    }

    @Test
    fun `monthly and yearly goals include older dates but still exclude the future`() {
        val progress = GoalProgressCalculator.calculate(
            listOf(
                goal(GoalType.MONTHLY, GoalMetric.EPISODES, 5, id = 1),
                goal(GoalType.YEARLY, GoalMetric.TITLES, 10, id = 2),
            ),
            input,
            today,
        )

        assertEquals(3, progress.first { it.goal.type == GoalType.MONTHLY }.current)
        assertEquals(2, progress.first { it.goal.type == GoalType.YEARLY }.current)
    }

    @Test
    fun `inactive goals are omitted`() {
        val progress = GoalProgressCalculator.calculate(
            listOf(goal(GoalType.WEEKLY, GoalMetric.EPISODES, 1, active = false)),
            input,
            today,
        )
        assertTrue(progress.isEmpty())
    }

    @Test
    fun `fraction is capped for reporting and zero target is safe`() {
        val exceeded = GoalProgressCalculator.calculate(
            listOf(goal(GoalType.WEEKLY, GoalMetric.EPISODES, 1)),
            input,
            today,
        ).single()
        assertEquals(1.0f, exceeded.fraction, 0.0f)

        val zero = com.seenly.app.domain.model.GoalProgress(
            goal = goal(GoalType.WEEKLY, GoalMetric.EPISODES, 0),
            current = 4,
            periodStart = today,
            periodEnd = today,
        )
        assertEquals(0.0f, zero.fraction, 0.0f)
        assertFalse(zero.fraction > 0f)
    }
}
