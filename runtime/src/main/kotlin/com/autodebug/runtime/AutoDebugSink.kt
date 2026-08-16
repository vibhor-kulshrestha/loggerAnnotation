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
}
