package com.seenly.app.domain.statistics

import com.seenly.app.domain.model.DatePeriod
import com.seenly.app.domain.model.Goal
import com.seenly.app.domain.model.GoalMetric
import com.seenly.app.domain.model.GoalProgress
import com.seenly.app.domain.model.GoalType
import java.time.DayOfWeek
import java.time.LocalDate
import java.time.temporal.TemporalAdjusters

/**
 * Goal progress uses the same completed-viewing records as the statistics
 * engine. Weekly periods start on Monday; monthly and yearly periods are local
 * calendar periods. Future-dated events never count.
 */
object GoalProgressCalculator {

    fun periodFor(type: GoalType, today: LocalDate): DatePeriod = when (type) {
        GoalType.WEEKLY -> {
            val start = today.with(TemporalAdjusters.previousOrSame(DayOfWeek.MONDAY))
            DatePeriod(start, start.plusDays(6))
        }
        GoalType.MONTHLY -> {
            val start = today.withDayOfMonth(1)
            DatePeriod(start, start.with(TemporalAdjusters.lastDayOfMonth()))
        }
        GoalType.YEARLY -> DatePeriod(
            LocalDate.of(today.year, 1, 1),
            LocalDate.of(today.year, 12, 31),
        )
    }

    fun calculate(
        goals: List<Goal>,
        input: StatisticsInput,
        today: LocalDate,
    ): List<GoalProgress> {
        val eligible = input.viewings.filter { !it.localDate.isAfter(today) }
        return goals.filter { it.active }.map { goal ->
            val period = periodFor(goal.type, today)
            val inPeriod = eligible.filter { it.localDate in period }
            val current = when (goal.metric) {
                GoalMetric.EPISODES -> inPeriod.filter { it.episodeId != null }.distinctBy { it.episodeId }.size
                GoalMetric.MOVIES -> inPeriod.count { it.episodeId == null }
                GoalMetric.TITLES -> inPeriod.map { it.mediaId }.distinct().size
                GoalMetric.MINUTES -> inPeriod.sumOf { (it.minutes ?: 0).toLong() }.toInt()
            }
            GoalProgress(
                goal = goal,
                current = current,
                periodStart = period.start,
                periodEnd = period.endInclusive,
            )
        }
    }
}
