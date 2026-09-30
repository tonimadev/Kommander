package dev.kommander.app.ui.components

import androidx.compose.runtime.Composable
import androidx.compose.runtime.State
import androidx.compose.runtime.produceState
import kotlinx.coroutines.delay
import java.time.Duration
import java.time.Instant

/** "Relógio" compartilhado que recompõe os tempos relativos a cada segundo. */
@Composable
fun rememberNow(): State<Instant> = produceState(Instant.now()) {
    while (true) {
        delay(1_000)
        value = Instant.now()
    }
}

fun formatRelative(then: Instant, now: Instant): String {
    val seconds = Duration.between(then, now).seconds.coerceAtLeast(0)
    return when {
        seconds < 5 -> "agora"
        seconds < 60 -> "há ${seconds}s"
        seconds < 3_600 -> "há ${seconds / 60} min"
        seconds < 86_400 -> "há ${seconds / 3_600} h"
        else -> "há ${seconds / 86_400} d"
    }
}
