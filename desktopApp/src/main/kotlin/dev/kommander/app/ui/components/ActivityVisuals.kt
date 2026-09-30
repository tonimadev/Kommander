package dev.kommander.app.ui.components

import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.CheckCircle
import androidx.compose.material.icons.rounded.CloudUpload
import androidx.compose.material.icons.rounded.Code
import androidx.compose.material.icons.rounded.Error
import androidx.compose.material.icons.rounded.Hub
import androidx.compose.material.icons.rounded.NotificationsActive
import androidx.compose.material.icons.rounded.PauseCircle
import androidx.compose.material.icons.rounded.Psychology
import androidx.compose.material.icons.rounded.Science
import androidx.compose.material.icons.rounded.Terminal
import androidx.compose.material.icons.rounded.TravelExplore
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import dev.kommander.domain.model.ActivityCategory
import dev.kommander.domain.model.ActivityStatus

/** Identidade visual de cada categoria: ícone, cor de destaque e rótulo. */
data class CategoryVisual(val icon: ImageVector, val accent: Color, val label: String)

val ActivityCategory.visual: CategoryVisual
    get() = when (this) {
        ActivityCategory.PLANNING -> CategoryVisual(Icons.Rounded.Psychology, Color(0xFF5C6BC0), "Planejando")
        ActivityCategory.RESEARCH -> CategoryVisual(Icons.Rounded.TravelExplore, Color(0xFF00897B), "Explorando")
        ActivityCategory.CODING -> CategoryVisual(Icons.Rounded.Code, Color(0xFF1E88E5), "Código")
        ActivityCategory.COMMAND -> CategoryVisual(Icons.Rounded.Terminal, Color(0xFF546E7A), "Comando")
        ActivityCategory.TESTING -> CategoryVisual(Icons.Rounded.Science, Color(0xFFF9A825), "Testes")
        ActivityCategory.GITHUB -> CategoryVisual(Icons.Rounded.Hub, Color(0xFF8957E5), "GitHub")
        ActivityCategory.DEPLOY -> CategoryVisual(Icons.Rounded.CloudUpload, Color(0xFFF57C00), "Deploy")
        ActivityCategory.WAITING -> CategoryVisual(Icons.Rounded.NotificationsActive, Color(0xFFEC407A), "Aguardando você")
        ActivityCategory.SUCCESS -> CategoryVisual(Icons.Rounded.CheckCircle, Color(0xFF43A047), "Concluído")
        ActivityCategory.ERROR -> CategoryVisual(Icons.Rounded.Error, Color(0xFFE53935), "Erro")
        ActivityCategory.IDLE -> CategoryVisual(Icons.Rounded.PauseCircle, Color(0xFF78909C), "Ocioso")
    }

val ActivityStatus.label: String
    get() = when (this) {
        ActivityStatus.SESSION_STARTED -> "Sessão iniciada"
        ActivityStatus.THINKING -> "Pensando"
        ActivityStatus.PLANNING -> "Planejando"
        ActivityStatus.EXPLORING -> "Explorando"
        ActivityStatus.CODING -> "Escrevendo código"
        ActivityStatus.RUNNING_COMMAND -> "Executando"
        ActivityStatus.TESTING -> "Testando"
        ActivityStatus.GITHUB -> "GitHub"
        ActivityStatus.COMMITTING -> "Commit / push"
        ActivityStatus.OPENING_ISSUE -> "Abrindo issue"
        ActivityStatus.OPENING_PR -> "Abrindo PR"
        ActivityStatus.REVIEWING -> "Revisando"
        ActivityStatus.BUILDING -> "Build"
        ActivityStatus.DEPLOYING -> "Deploy"
        ActivityStatus.WAITING_INPUT -> "Aguardando você"
        ActivityStatus.DONE, ActivityStatus.SUCCESS -> "Concluído"
        ActivityStatus.FAILED, ActivityStatus.ERROR -> "Falhou"
        ActivityStatus.SESSION_ENDED -> "Sessão encerrada"
        ActivityStatus.IDLE -> "Ocioso"
        ActivityStatus.UNKNOWN -> "Desconhecido"
    }
