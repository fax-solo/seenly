package com.seenly.app.data.repository

import androidx.room.withTransaction
import com.seenly.app.core.common.AppClock
import com.seenly.app.core.common.AppDispatchers
import com.seenly.app.core.common.AppError
import com.seenly.app.core.common.Outcome
import com.seenly.app.core.database.SeenlyDatabase
import com.seenly.app.data.local.entity.GoalEntity
import com.seenly.app.data.local.mapper.toDomain
import com.seenly.app.domain.model.Goal
import com.seenly.app.domain.model.GoalMetric
import com.seenly.app.domain.model.GoalType
import com.seenly.app.domain.repository.GoalsRepository
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.flowOn
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.withContext

/**
 * Goals are local, private targets. At most one goal per period type is active:
 * saving a new weekly goal deactivates the previous one rather than silently
 * stacking targets. Past goals remain in the database for history.
 */
class GoalsRepositoryImpl(
    private val db: SeenlyDatabase,
    private val clock: AppClock,
    private val dispatchers: AppDispatchers,
) : GoalsRepository {

    private val goalDao = db.goalDao()

    override fun observeGoals(): Flow<List<Goal>> =
        goalDao.observeAll()
            .map { goals -> goals.map { it.toDomain() } }
            .flowOn(dispatchers.io)

    override suspend fun saveGoal(type: GoalType, metric: GoalMetric, target: Int): Outcome<Unit> =
        withContext(dispatchers.io) {
            if (target < 1 || target > 1_000_000) {
                return@withContext Outcome.Failure(AppError.Invalid("invalid_goal_target"))
            }
            db.withTransaction {
                val all = goalDao.getAll()
                val match = all.firstOrNull { it.type == type.name && it.metric == metric.name }
                all.filter { it.type == type.name && it.id != (match?.id ?: -1L) && it.active }
                    .forEach { goalDao.update(it.copy(active = false)) }
                if (match != null) {
                    goalDao.update(match.copy(target = target, active = true))
                } else {
                    goalDao.insert(
                        GoalEntity(
                            type = type.name,
                            metric = metric.name,
                            target = target,
                            active = true,
                            createdAt = clock.nowMillis(),
                        ),
                    )
                }
                Outcome.Success(Unit)
            }
        }

    override suspend fun deleteGoal(goalId: Long) {
        withContext(dispatchers.io) {
            goalDao.getById(goalId)?.let { goalDao.delete(it) }
        }
    }
}
