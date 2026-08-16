package com.autodebug

@Target(AnnotationTarget.FUNCTION, AnnotationTarget.CLASS)
@Retention(AnnotationRetention.BINARY)
annotation class AutoDebug(
    val tag: String = "",
    val depth: DebugDepth = DebugDepth.BOUNDARY,
)
