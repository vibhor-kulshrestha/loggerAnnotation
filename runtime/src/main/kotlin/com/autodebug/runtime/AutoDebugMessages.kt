package com.autodebug.runtime

object AutoDebugMessages {
    const val DEFAULT_MAX_CHARS: Int = 300

    fun truncate(text: String, maxChars: Int = DEFAULT_MAX_CHARS): String {
        if (text.length <= maxChars) return text
        return text.take(maxChars) + "..."
    }

    fun formatValue(value: Any?): String {
        if (value == null) return "null"
        return try {
            truncate(value.toString())
        } catch (t: Throwable) {
            truncate("<toString failed: ${t.javaClass.simpleName}>")
        }
    }

    fun enter(method: String, argsDescription: String): String =
        if (argsDescription.isEmpty()) "⇢ $method()" else "⇢ $method($argsDescription)"

    fun exit(method: String, resultDescription: String, durationMs: Long): String =
        "⇠ $method = $resultDescription [${durationMs}ms]"

    fun thrown(method: String, throwable: Throwable, durationMs: Long): String {
        val type = throwable.javaClass.simpleName
        val msg = truncate(throwable.message ?: "")
        return "⇠ $method threw $type: $msg [${durationMs}ms]"
    }
}
