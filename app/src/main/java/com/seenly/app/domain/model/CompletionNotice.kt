package com.seenly.app.domain.model

/**
 * Emitted when a title transitions into COMPLETED. The UI shows it as an
 * in-app confirmation and the app posts a local notification for it.
 */
data class CompletionNotice(
    val mediaId: Long,
    val title: String,
    val completedAt: Long,
)
