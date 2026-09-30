package dev.kommander.domain.model

/**
 * Estados que o agente pode reportar. Cada estado pertence a uma [ActivityCategory],
 * que a apresentação usa para escolher ícone/cor.
 */
enum class ActivityStatus(
    val category: ActivityCategory,
    val isInProgress: Boolean,
) {
    SESSION_STARTED(ActivityCategory.IDLE, isInProgress = false),
    THINKING(ActivityCategory.PLANNING, isInProgress = true),
    PLANNING(ActivityCategory.PLANNING, isInProgress = true),
    EXPLORING(ActivityCategory.RESEARCH, isInProgress = true),
    CODING(ActivityCategory.CODING, isInProgress = true),
    RUNNING_COMMAND(ActivityCategory.CODING, isInProgress = true),
    TESTING(ActivityCategory.TESTING, isInProgress = true),
    GITHUB(ActivityCategory.GITHUB, isInProgress = true),
    COMMITTING(ActivityCategory.GITHUB, isInProgress = true),
    OPENING_ISSUE(ActivityCategory.GITHUB, isInProgress = true),
    OPENING_PR(ActivityCategory.GITHUB, isInProgress = true),
    REVIEWING(ActivityCategory.GITHUB, isInProgress = true),
    BUILDING(ActivityCategory.DEPLOY, isInProgress = true),
    DEPLOYING(ActivityCategory.DEPLOY, isInProgress = true),
    WAITING_INPUT(ActivityCategory.WAITING, isInProgress = false),
    DONE(ActivityCategory.SUCCESS, isInProgress = false),
    SUCCESS(ActivityCategory.SUCCESS, isInProgress = false),
    FAILED(ActivityCategory.ERROR, isInProgress = false),
    ERROR(ActivityCategory.ERROR, isInProgress = false),
    SESSION_ENDED(ActivityCategory.IDLE, isInProgress = false),
    IDLE(ActivityCategory.IDLE, isInProgress = false),
    UNKNOWN(ActivityCategory.IDLE, isInProgress = false),
    ;

    companion object {
        /** Tolerante a caixa, espaços e hífens: "deploying", "Opening-PR" etc. */
        fun parse(raw: String?): ActivityStatus {
            if (raw.isNullOrBlank()) return UNKNOWN
            val normalized = raw.trim().uppercase().replace('-', '_').replace(' ', '_')
            return entries.firstOrNull { it.name == normalized } ?: UNKNOWN
        }
    }
}

enum class ActivityCategory { PLANNING, RESEARCH, CODING, TESTING, GITHUB, DEPLOY, WAITING, SUCCESS, ERROR, IDLE }
