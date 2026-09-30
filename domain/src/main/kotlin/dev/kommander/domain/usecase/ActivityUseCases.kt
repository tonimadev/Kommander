package dev.kommander.domain.usecase

import dev.kommander.domain.model.AgentActivity
import dev.kommander.domain.model.ListenerState
import dev.kommander.domain.repository.ActivityRepository
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.StateFlow

class ObserveActivitiesUseCase(private val repository: ActivityRepository) {
    operator fun invoke(): Flow<AgentActivity> = repository.activities
}

class ObserveListenerStateUseCase(private val repository: ActivityRepository) {
    operator fun invoke(): StateFlow<ListenerState> = repository.listenerState
}

class StartListeningUseCase(private val repository: ActivityRepository) {
    suspend operator fun invoke() = repository.start()
}

class StopListeningUseCase(private val repository: ActivityRepository) {
    suspend operator fun invoke() = repository.stop()
}
