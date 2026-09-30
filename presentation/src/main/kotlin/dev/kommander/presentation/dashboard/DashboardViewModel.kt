package dev.kommander.presentation.dashboard

import dev.kommander.domain.usecase.ObserveActivitiesUseCase
import dev.kommander.domain.usecase.ObserveListenerStateUseCase
import dev.kommander.domain.usecase.StartListeningUseCase
import dev.kommander.domain.usecase.StopListeningUseCase
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.cancel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch

/**
 * ViewModel MVI/UDF: a UI envia [DashboardIntent]s via [onIntent] e observa um único
 * [StateFlow] de [DashboardState]. Toda alteração de estado passa pelo [DashboardReducer].
 *
 * Não depende de androidx.lifecycle: o escopo é injetado, o que mantém a classe
 * testável com `TestScope` e com ciclo de vida controlado pelo `AppContainer`.
 */
class DashboardViewModel(
    observeActivities: ObserveActivitiesUseCase,
    observeListenerState: ObserveListenerStateUseCase,
    private val startListening: StartListeningUseCase,
    private val stopListening: StopListeningUseCase,
    private val scope: CoroutineScope,
) {
    private val _state = MutableStateFlow(DashboardState())
    val state: StateFlow<DashboardState> = _state.asStateFlow()

    init {
        scope.launch {
            observeActivities().collect { dispatch(DashboardMutation.ActivityReceived(it)) }
        }
        scope.launch {
            observeListenerState().collect { dispatch(DashboardMutation.ListenerChanged(it)) }
        }
    }

    fun onIntent(intent: DashboardIntent) {
        when (intent) {
            DashboardIntent.StartListening -> scope.launch { startListening() }
            DashboardIntent.RetryListening -> scope.launch {
                stopListening()
                startListening()
            }
            DashboardIntent.ClearHistory -> dispatch(DashboardMutation.HistoryCleared)
            is DashboardIntent.DismissSession -> dispatch(DashboardMutation.SessionDismissed(intent.key))
            is DashboardIntent.FilterByRepository -> dispatch(DashboardMutation.RepositoryFilterChanged(intent.slug))
        }
    }

    /** Para o servidor e cancela o escopo. Chamado ao fechar a janela. */
    suspend fun shutdown() {
        stopListening()
        scope.cancel()
    }

    private fun dispatch(mutation: DashboardMutation) {
        _state.update { DashboardReducer.reduce(it, mutation) }
    }
}
