package com.autodebug.runtime

fun interface AutoDebugSink {
    fun log(tag: String, message: String)
}

object AutoDebug {
    var sink: AutoDebugSink = AutoDebugSink { tag, message ->
        println("$tag: $message")
    }

    fun log(tag: String, message: String) {
        if (!AutoDebugConfig.enabled) return
        try {
            sink.log(tag, message)
        } catch (_: Throwable) {
            // never break app logic because of debug logging
        }
    }

    fun currentTimeMillis(): Long = System.currentTimeMillis()

    fun logEnter(tag: String, method: String, argsDescription: String) {
        log(tag, AutoDebugMessages.enter(method, argsDescription))
    }

    fun logExit(tag: String, method: String, result: Any?, durationMs: Long) {
        log(tag, AutoDebugMessages.exit(method, AutoDebugMessages.formatValue(result), durationMs))
    }

    fun logThrow(tag: String, method: String, throwable: Throwable, durationMs: Long) {
        log(tag, AutoDebugMessages.thrown(method, throwable, durationMs))
    }

    fun logBranch(tag: String, method: String, label: String) {
        log(tag, AutoDebugMessages.branch(method, label))
    }

    fun logAssignment(tag: String, method: String, name: String, oldValue: Any?, newValue: Any?) {
        log(tag, AutoDebugMessages.assignment(method, name, oldValue, newValue))
    }

    fun describeArgs(names: Array<String>, values: Array<out Any?>): String {
        require(names.size == values.size)
        return names.indices.joinToString { i ->
            "${names[i]}=${AutoDebugMessages.formatValue(values[i])}"
        }
    }
}
