package dev.kommander.presentation.dashboard

import dev.kommander.domain.model.ActivityStatus
import dev.kommander.domain.model.ListenerState
import dev.kommander.domain.usecase.ObserveActivitiesUseCase
import dev.kommander.domain.usecase.ObserveListenerStateUseCase
import dev.kommander.domain.usecase.StartListeningUseCase
import dev.kommander.domain.usecase.StopListeningUseCase
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.test.TestScope
import kotlinx.coroutines.test.UnconfinedTestDispatcher
import kotlinx.coroutines.test.runTest
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertIs

@OptIn(ExperimentalCoroutinesApi::class)
class DashboardViewModelTest {

    private fun TestScope.viewModel(repository: FakeActivityRepository) = DashboardViewModel(
        observeActivities = ObserveActivitiesUseCase(repository),
        observeListenerState = ObserveListenerStateUseCase(repository),
        startListening = StartListeningUseCase(repository),
        stopListening = StopListeningUseCase(repository),
        scope = TestScope(UnconfinedTestDispatcher(testScheduler)),
    )

    @Test
    fun `start intent starts listener and reflects its state`() = runTest {
        val repository = FakeActivityRepository()
        val vm = viewModel(repository)

        vm.onIntent(DashboardIntent.StartListening)

        assertEquals(1, repository.starts)
        assertIs<ListenerState.Listening>(vm.state.value.listener)
    }

    @Test
    fun `emitted activities flow into the state`() = runTest {
        val repository = FakeActivityRepository()
        val vm = viewModel(repository)

        repository.emitter.emit(activity(1, ActivityStatus.OPENING_PR))

        assertEquals(ActivityStatus.OPENING_PR, vm.state.value.sessions.single().latest.status)
        assertEquals(1, vm.state.value.totalEvents)
    }

    @Test
    fun `retry restarts the listener and shutdown stops it`() = runTest {
        val repository = FakeActivityRepository()
        val vm = viewModel(repository)

        vm.onIntent(DashboardIntent.RetryListening)
        assertEquals(1, repository.stops)
        assertEquals(1, repository.starts)

        vm.shutdown()
        assertEquals(2, repository.stops)
        assertEquals(ListenerState.Stopped, vm.state.value.listener)
    }
}
