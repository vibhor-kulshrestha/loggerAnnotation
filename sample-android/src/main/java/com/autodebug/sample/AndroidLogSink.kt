package com.autodebug.sample

import android.util.Log
import com.autodebug.runtime.AutoDebugSink

class AndroidLogSink : AutoDebugSink {
    override fun log(tag: String, message: String) {
        Log.d(tag, message)
    }
}
