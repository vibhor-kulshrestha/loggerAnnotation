package com.autodebug.sample

import com.autodebug.AutoDebug

class Demo {
    @AutoDebug(tag = "Demo")
    fun greet(name: String): String = "Hello, $name"
}
