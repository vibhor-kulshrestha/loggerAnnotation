package com.autodebug.sample

import com.autodebug.AutoDebug

class Demo {
    @AutoDebug(tag = "Demo")
    fun greet(name: String): String = "Hello, $name"

    @AutoDebug(tag = "Demo")
    fun fail(message: String): String {
        error(message)
    }
}
