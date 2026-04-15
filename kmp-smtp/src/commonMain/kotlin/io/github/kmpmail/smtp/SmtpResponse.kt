package io.github.kmpmail.smtp

/**
 * A parsed SMTP server response.
 *
 * Multi-line responses (NNN-text continuation, NNN text final) are collapsed
 * into a single object. [lines] preserves every text fragment in order.
 */
data class SmtpResponse(
    val code: Int,
    val lines: List<String>,
) {
    val message: String get() = lines.lastOrNull() ?: ""
    val isPositive: Boolean get() = code in 200..399
    val isTransientFailure: Boolean get() = code in 400..499
    val isPermanentFailure: Boolean get() = code in 500..599
}
