package com.autodebug.sample

import android.app.Activity
import android.os.Bundle
import android.widget.TextView
import com.autodebug.runtime.AutoDebug

class MainActivity : Activity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        AutoDebug.sink = AndroidLogSink()

        val demo = Demo()
        val greeting = demo.greet("AutoDebug")
        try {
            demo.fail("sample failure")
        } catch (_: Throwable) {
            // expected — demo logs throw via @AutoDebug
        }

        val text = TextView(this)
        text.text = greeting
        setContentView(text)
    }
}
