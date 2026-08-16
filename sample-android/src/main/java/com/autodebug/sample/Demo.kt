package com.autodebug.sample

import com.autodebug.AutoDebug
import com.autodebug.DebugDepth

class Demo {
    @AutoDebug(tag = "Demo", depth = DebugDepth.BOUNDARY)
    fun greet(name: String): String = "Hello, $name"

    @AutoDebug(tag = "Demo", depth = DebugDepth.BRANCHES)
    fun classify(x: Int): String = if (x >= 0) "non-negative" else "negative"

    @AutoDebug(tag = "Demo", depth = DebugDepth.BRANCHES)
    fun pick(code: Int): String = when (code) {
        1 -> "one"
        2 -> "two"
        else -> "other"
    }

    @AutoDebug(tag = "Demo")
    fun fail(message: String): String {
        error(message)
    }
}

class Accumulator {
    var total = 0

    @AutoDebug(tag = "Demo", depth = DebugDepth.VARS)
    fun bump(n: Int): Int {
        var step = 0
        step = n
        total = total + step
        return total
    }
}
