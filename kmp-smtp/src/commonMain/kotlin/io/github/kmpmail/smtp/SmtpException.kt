package io.github.kmpmail.smtp

/**
 * Thrown when the SMTP server returns an unexpected response code,
 * or when a protocol-level error occurs.
 */
class SmtpException(
    val code: Int,
    message: String,
) : Exception("SMTP $code: $message")
