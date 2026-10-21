package com.seenly.app.ui.calendar

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.seenly.app.AppContainer
import com.seenly.app.core.common.AppError
import com.seenly.app.domain.model.CalendarData
import java.time.LocalDate
import java.time.YearMonth
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.catch
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.stateIn

data class CalendarUiState(
    val loading: Boolean = true,
    val data: CalendarData = CalendarData(),
    val month: YearMonth = YearMonth.now(),
    val selectedDate: LocalDate = LocalDate.now(),
    val today: LocalDate = LocalDate.now(),
    val error: AppError? = null,
)

class CalendarViewModel(private val container: AppContainer) : ViewModel() {

    private val month = MutableStateFlow(YearMonth.from(container.clock.today()))
    private val selected = MutableStateFlow(container.clock.today())

    val state = combine(
        container.statisticsRepository.observeCalendar(),
        month,
        selected,
    ) { data, currentMonth, selectedDate ->
        CalendarUiState(
            loading = false,
            data = data,
            month = currentMonth,
            selectedDate = selectedDate,
            today = container.clock.today(),
        )
    }.catch { failure ->
        emit(
            CalendarUiState(
                loading = false,
                today = container.clock.today(),
                error = AppError.Unknown(failure),
            ),
        )
    }.stateIn(
        scope = viewModelScope,
        started = SharingStarted.WhileSubscribed(5_000),
        initialValue = CalendarUiState(today = container.clock.today()),
    )

    fun previousMonth() {
        val previous = month.value.minusMonths(1)
        month.value = previous
        selected.value = previous.atDay(1)
    }

    fun nextMonth() {
        val next = month.value.plusMonths(1)
        month.value = next
        selected.value = next.atDay(1)
    }

    fun selectDate(date: LocalDate) {
        selected.value = date
    }

    fun goToToday() {
        val today = container.clock.today()
        month.value = YearMonth.from(today)
        selected.value = today
    }
}
