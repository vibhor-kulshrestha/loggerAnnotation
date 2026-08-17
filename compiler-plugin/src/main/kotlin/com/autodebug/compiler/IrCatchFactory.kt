package com.autodebug.compiler

import org.jetbrains.kotlin.ir.declarations.IrVariable
import org.jetbrains.kotlin.ir.expressions.IrCatch
import org.jetbrains.kotlin.ir.expressions.IrExpression
import java.lang.reflect.Method

object IrCatchFactory {
    private val buildersClass: Class<*> by lazy {
        Class.forName("org.jetbrains.kotlin.ir.expressions.impl.BuildersKt")
    }

    private val factoryMethod: Method by lazy {
        val methods = buildersClass.methods
        methods.firstOrNull { method ->
            method.name == "IrCatchImpl" && method.parameterCount == 5
        } ?: methods.firstOrNull { method ->
            method.name == "IrCatchImpl" && method.parameterCount == 4
        } ?: throw IllegalStateException("Could not find BuildersKt.IrCatchImpl factory method")
    }

    fun createCatch(
        startOffset: Int,
        endOffset: Int,
        catchParameter: IrVariable,
        result: IrExpression
    ): IrCatch {
        return if (factoryMethod.parameterCount == 5) {
            factoryMethod.invoke(null, startOffset, endOffset, catchParameter, result, null) as IrCatch
        } else {
            factoryMethod.invoke(null, startOffset, endOffset, catchParameter, result) as IrCatch
        }
    }
}
