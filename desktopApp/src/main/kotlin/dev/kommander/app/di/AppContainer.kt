package dev.kommander.app.di

import dev.kommander.presentation.dashboard.DashboardViewModel
import dev.kommander.data.remote.WebhookServerConfig
import dev.kommander.data.repository.WebhookActivityRepository
import dev.kommander.domain.repository.ActivityRepository
import dev.kommander.domain.usecase.ObserveActivitiesUseCase
import dev.kommander.domain.usecase.ObserveListenerStateUseCase
import dev.kommander.domain.usecase.StartListeningUseCase
import dev.kommander.domain.usecase.StopListeningUseCase
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob

/**
 * Composition root (DI manual). Único ponto do app que conhece a implementação
 * concreta da camada de dados.
 */
class AppContainer(config: WebhookServerConfig) {

    private val repository: ActivityRepository = WebhookActivityRepository(config)

    val dashboardViewModel: DashboardViewModel = DashboardViewModel(
        observeActivities = ObserveActivitiesUseCase(repository),
        observeListenerState = ObserveListenerStateUseCase(repository),
        startListening = StartListeningUseCase(repository),
        stopListening = StopListeningUseCase(repository),
        scope = CoroutineScope(SupervisorJob() + Dispatchers.Main.immediate),
    )

    companion object {
        /**
         * Lê a configuração de `--port=9090` / `--host=0.0.0.0` ou das variáveis
         * `KOMMANDER_PORT` / `KOMMANDER_HOST`. Padrão: 127.0.0.1:8080/events.
         */
        fun configFrom(args: Array<String>, env: Map<String, String> = System.getenv()): WebhookServerConfig {
            fun arg(name: String) = args.firstOrNull { it.startsWith("--$name=") }?.substringAfter('=')
            val defaults = WebhookServerConfig()
            return WebhookServerConfig(
                host = arg("host") ?: env["KOMMANDER_HOST"] ?: defaults.host,
                port = (arg("port") ?: env["KOMMANDER_PORT"])?.toIntOrNull() ?: defaults.port,
                path = defaults.path,
            )
        }
    }
}
