package com.autodebug.runtime

fun interface AutoDebugSink {
    fun log(tag: String, message: String)
}

object AutoDebug {
    @JvmStatic
    var sink: AutoDebugSink = AutoDebugSink { tag, message ->
        println("$tag: $message")
    }

    @JvmStatic
    fun log(tag: String, message: String) {
        if (!AutoDebugConfig.enabled) return
        try {
            sink.log(tag, message)
        } catch (_: Throwable) {
            // never break app logic because of debug logging
        }
    }

    @JvmStatic
    fun currentTimeMillis(): Long = System.currentTimeMillis()

    @JvmStatic
    fun logEnter(tag: String, method: String, argsDescription: String) {
        log(tag, AutoDebugMessages.enter(method, argsDescription))
    }

    @JvmStatic
    fun logExit(tag: String, method: String, result: Any?, durationMs: Long) {
        log(tag, AutoDebugMessages.exit(method, AutoDebugMessages.formatValue(result), durationMs))
    }

    @JvmStatic
    fun logThrow(tag: String, method: String, throwable: Throwable, durationMs: Long) {
        log(tag, AutoDebugMessages.thrown(method, throwable, durationMs))
    }

    @JvmStatic
    fun logBranch(tag: String, method: String, label: String) {
        log(tag, AutoDebugMessages.branch(method, label))
    }

    @JvmStatic
    fun describeArgs(names: Array<String>, values: Array<out Any?>): String {
        require(names.size == values.size)
        return names.indices.joinToString { i ->
            "${names[i]}=${AutoDebugMessages.formatValue(values[i])}"
        }
    }
}
