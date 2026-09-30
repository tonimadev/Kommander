package dev.kommander.data.remote

data class WebhookServerConfig(
    /** Loopback por padrão: só processos da própria máquina podem enviar eventos. */
    val host: String = "127.0.0.1",
    val port: Int = 8080,
    val path: String = "/events",
)
