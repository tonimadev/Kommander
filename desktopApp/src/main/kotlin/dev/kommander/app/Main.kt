package dev.kommander.app

import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.graphics.painter.Painter
import androidx.compose.ui.graphics.toPainter
import androidx.compose.ui.unit.DpSize
import androidx.compose.ui.unit.dp
import androidx.compose.ui.window.Window
import androidx.compose.ui.window.application
import androidx.compose.ui.window.rememberWindowState
import dev.kommander.app.di.AppContainer
import dev.kommander.app.ui.DashboardScreen
import dev.kommander.app.ui.theme.KommanderTheme
import kotlinx.coroutines.runBlocking
import kotlinx.coroutines.withTimeoutOrNull
import java.awt.Toolkit
import javax.imageio.ImageIO

fun main(args: Array<String>) {
    setLinuxWindowClass("kommander")
    val container = AppContainer(AppContainer.configFrom(args))
    val viewModel = container.dashboardViewModel
    val appIcon = loadAppIcon()

    application {
        var alwaysOnTop by remember { mutableStateOf(false) }

        Window(
            onCloseRequest = {
                runBlocking { withTimeoutOrNull(2_000) { viewModel.shutdown() } }
                exitApplication()
            },
            title = "Kommander",
            icon = appIcon,
            alwaysOnTop = alwaysOnTop,
            state = rememberWindowState(size = DpSize(520.dp, 820.dp)),
        ) {
            KommanderTheme {
                DashboardScreen(
                    viewModel = viewModel,
                    alwaysOnTop = alwaysOnTop,
                    onToggleAlwaysOnTop = { alwaysOnTop = !alwaysOnTop },
                )
            }
        }
    }
}

/** Ícone "K" da janela e da barra de tarefas (substitui o ícone padrão do Java). */
private fun loadAppIcon(): Painter? =
    object {}.javaClass.getResourceAsStream("/kommander.png")
        ?.use { ImageIO.read(it) }
        ?.toPainter()

/**
 * No Linux (X11/XWayland) o AWT usa como WM_CLASS o nome da classe que abriu a janela,
 * que no Compose sai `java-lang-Thread`. Com um nome fixo, o menu de aplicativos e a barra
 * de tarefas associam a janela ao `kommander.desktop` (`StartupWMClass=kommander`).
 * Precisa de `--add-opens java.desktop/sun.awt.X11` (ver `jvmArgs` no build).
 */
private fun setLinuxWindowClass(name: String) {
    runCatching {
        val toolkit = Toolkit.getDefaultToolkit()
        toolkit.javaClass.getDeclaredField("awtAppClassName").apply {
            isAccessible = true
            set(toolkit, name)
        }
    }
}
