package com.autodebug.compiler

import org.jetbrains.kotlin.backend.common.IrElementTransformerVoidWithContext
import org.jetbrains.kotlin.backend.common.lower.DeclarationIrBuilder
import org.jetbrains.kotlin.ir.IrStatement
import org.jetbrains.kotlin.ir.builders.IrBuilderWithScope
import org.jetbrains.kotlin.ir.builders.irBlock
import org.jetbrains.kotlin.ir.builders.irBlockBody
import org.jetbrains.kotlin.ir.builders.irCall
import org.jetbrains.kotlin.ir.builders.irGet
import org.jetbrains.kotlin.ir.builders.irReturn
import org.jetbrains.kotlin.ir.builders.irString
import org.jetbrains.kotlin.ir.builders.irTemporary
import org.jetbrains.kotlin.ir.builders.irTry
import org.jetbrains.kotlin.ir.builders.irUnit
import org.jetbrains.kotlin.ir.builders.irVararg
import org.jetbrains.kotlin.ir.builders.declarations.buildVariable
import org.jetbrains.kotlin.ir.declarations.IrClass
import org.jetbrains.kotlin.ir.declarations.IrDeclaration
import org.jetbrains.kotlin.ir.declarations.IrDeclarationOrigin
import org.jetbrains.kotlin.ir.declarations.IrFunction
import org.jetbrains.kotlin.ir.declarations.IrSimpleFunction
import org.jetbrains.kotlin.ir.declarations.IrVariable
import org.jetbrains.kotlin.ir.expressions.IrBlockBody
import org.jetbrains.kotlin.ir.expressions.IrBody
import org.jetbrains.kotlin.ir.expressions.IrExpression
import org.jetbrains.kotlin.ir.expressions.IrReturn
import org.jetbrains.kotlin.ir.expressions.impl.IrCatchImpl
import org.jetbrains.kotlin.ir.expressions.impl.IrThrowImpl
import org.jetbrains.kotlin.ir.util.file
import org.jetbrains.kotlin.ir.util.getAnnotation
import org.jetbrains.kotlin.ir.util.getAnnotationStringValue
import org.jetbrains.kotlin.ir.util.hasAnnotation
import org.jetbrains.kotlin.ir.util.implicitCastIfNeededTo
import org.jetbrains.kotlin.ir.util.isFileClass
import org.jetbrains.kotlin.ir.visitors.IrElementTransformerVoid
import org.jetbrains.kotlin.name.FqName
import org.jetbrains.kotlin.name.Name

/**
 * Injects BOUNDARY enter/exit/throw logging for functions annotated with @AutoDebug.
 */
class AutoDebugIrTransformer(
    private val symbols: IrAutoDebugSymbols,
) : IrElementTransformerVoidWithContext() {

    private val pluginContext get() = symbols.context
    private val autoDebugFqName = FqName("com.autodebug.AutoDebug")
    private val typeUnit = pluginContext.irBuiltIns.unitType
    private val typeThrowable = pluginContext.irBuiltIns.throwableType
    private val typeLong = pluginContext.irBuiltIns.longType
    private val typeAnyNullable = pluginContext.irBuiltIns.anyNType

    override fun visitSimpleFunction(declaration: IrSimpleFunction): IrStatement {
        if (!declaration.hasAnnotation(autoDebugFqName)) {
            return super.visitSimpleFunction(declaration)
        }
        val body = declaration.body
        if (body == null || declaration.isFakeOverride || declaration.isInline) {
            return super.visitSimpleFunction(declaration)
        }
        if (body !is IrBlockBody) {
            return super.visitSimpleFunction(declaration)
        }

        declaration.body = instrumentBody(declaration, body)
        return super.visitSimpleFunction(declaration)
    }

    private fun instrumentBody(function: IrSimpleFunction, body: IrBlockBody): IrBody {
        val tag = readTag(function)
        val methodName = function.name.asString()

        return DeclarationIrBuilder(pluginContext, function.symbol).irBlockBody {
            +irCall(symbols.logEnter).apply {
                putValueArgument(0, irString(tag))
                putValueArgument(1, irString(methodName))
                putValueArgument(2, buildArgsDescription(function))
            }

            val start = irTemporary(
                irCall(symbols.currentTimeMillis),
                nameHint = "autodebugStart",
                irType = typeLong,
            )

            val tryBlock = irBlock(resultType = function.returnType) {
                for (statement in body.statements) {
                    +statement
                }
                if (function.returnType == typeUnit) {
                    +irLogExit(function, tag, methodName, start, irUnit())
                }
            }.transform(ReturnLoggingTransformer(function, tag, methodName, start), null)

            val catchParam = buildVariable(
                scope.getLocalDeclarationParent(),
                startOffset,
                endOffset,
                IrDeclarationOrigin.CATCH_PARAMETER,
                Name.identifier("t"),
                typeThrowable,
            )

            val catchBlock = irBlock(resultType = typeUnit) {
                +irLogExitThrow(function, tag, methodName, start, irGet(catchParam))
                +IrThrowImpl(startOffset, endOffset, typeUnit, irGet(catchParam))
            }

            val catch = IrCatchImpl(startOffset, endOffset, catchParam, catchBlock, null)

            +irTry(typeUnit, tryBlock, listOf(catch), null)
        }
    }

    private fun IrBuilderWithScope.buildArgsDescription(function: IrSimpleFunction): IrExpression {
        val params = function.valueParameters
        if (params.isEmpty()) {
            return irString("")
        }
        val namesArray = irCall(symbols.arrayOfStrings).apply {
            putValueArgument(0, irVararg(pluginContext.irBuiltIns.stringType, params.map { irString(it.name.asString()) }))
        }
        val valuesArray = irCall(symbols.arrayOfAnyNullable).apply {
            putValueArgument(
                0,
                irVararg(typeAnyNullable, params.map { irGet(it).implicitCastIfNeededTo(typeAnyNullable) }),
            )
        }
        return irCall(symbols.describeArgs).apply {
            putValueArgument(0, namesArray)
            putValueArgument(1, valuesArray)
        }
    }

    private fun IrBuilderWithScope.irLogExit(
        function: IrSimpleFunction,
        tag: String,
        methodName: String,
        start: IrVariable,
        result: IrExpression,
    ): IrExpression = irCall(symbols.logExit).apply {
        putValueArgument(0, irString(tag))
        putValueArgument(1, irString(methodName))
        putValueArgument(2, result.implicitCastIfNeededTo(typeAnyNullable))
        putValueArgument(3, irDurationSince(start))
    }

    private fun IrBuilderWithScope.irLogExitThrow(
        function: IrSimpleFunction,
        tag: String,
        methodName: String,
        start: IrVariable,
        throwable: IrExpression,
    ): IrExpression = irCall(symbols.logThrow).apply {
        putValueArgument(0, irString(tag))
        putValueArgument(1, irString(methodName))
        putValueArgument(2, throwable)
        putValueArgument(3, irDurationSince(start))
    }

    private fun IrBuilderWithScope.irDurationSince(start: IrVariable): IrExpression =
        irCall(symbols.longMinus).apply {
            dispatchReceiver = irCall(symbols.currentTimeMillis)
            putValueArgument(0, irGet(start))
        }

    private fun readTag(declaration: IrSimpleFunction): String {
        val explicitTag = declaration.getAnnotation(autoDebugFqName)
            ?.getAnnotationStringValue("tag")
            .orEmpty()
        return explicitTag.ifEmpty { fallbackTag(declaration) }
    }

    private fun fallbackTag(declaration: IrSimpleFunction): String {
        var current = declaration.parent as? IrDeclaration
        while (current != null) {
            if (current is IrClass && !current.isFileClass) {
                return current.name.asString()
            }
            current = current.parent as? IrDeclaration
        }
        return declaration.file.fileEntry.name.removeSuffix(".kt")
    }

    private inner class ReturnLoggingTransformer(
        private val function: IrFunction,
        private val tag: String,
        private val methodName: String,
        private val start: IrVariable,
    ) : IrElementTransformerVoid() {
        override fun visitReturn(expression: IrReturn): IrExpression {
            if (expression.returnTargetSymbol != function.symbol) {
                return super.visitReturn(expression)
            }
            return DeclarationIrBuilder(pluginContext, function.symbol).irBlock(
                resultType = function.returnType,
            ) {
                val result = irTemporary(expression.value, nameHint = "autodebugResult")
                +irLogExit(function as IrSimpleFunction, tag, methodName, start, irGet(result))
                +irReturn(irGet(result))
            }
        }
    }
}
