@file:OptIn(org.jetbrains.kotlin.ir.symbols.UnsafeDuringIrConstructionAPI::class)

package com.autodebug.compiler

import org.jetbrains.kotlin.backend.common.IrElementTransformerVoidWithContext
import org.jetbrains.kotlin.backend.common.lower.DeclarationIrBuilder
import org.jetbrains.kotlin.ir.IrStatement
import org.jetbrains.kotlin.ir.builders.IrBuilderWithScope
import org.jetbrains.kotlin.ir.builders.irBlock
import org.jetbrains.kotlin.ir.builders.irBlockBody
import org.jetbrains.kotlin.ir.builders.irCall
import org.jetbrains.kotlin.ir.builders.irGet
import org.jetbrains.kotlin.ir.builders.irGetField
import org.jetbrains.kotlin.ir.builders.irGetObjectValue
import org.jetbrains.kotlin.ir.builders.irNull
import org.jetbrains.kotlin.ir.builders.irReturn
import org.jetbrains.kotlin.ir.builders.irSet
import org.jetbrains.kotlin.ir.builders.irSetField
import org.jetbrains.kotlin.ir.builders.irString
import org.jetbrains.kotlin.ir.builders.irTemporary
import org.jetbrains.kotlin.ir.builders.irTry
import org.jetbrains.kotlin.ir.builders.irUnit
import org.jetbrains.kotlin.ir.builders.irVararg
import org.jetbrains.kotlin.ir.builders.declarations.buildVariable
import org.jetbrains.kotlin.ir.declarations.IrClass
import org.jetbrains.kotlin.ir.declarations.IrDeclaration
import org.jetbrains.kotlin.ir.declarations.IrDeclarationOrigin
import org.jetbrains.kotlin.ir.declarations.IrField
import org.jetbrains.kotlin.ir.declarations.IrFunction
import org.jetbrains.kotlin.ir.declarations.IrParameterKind
import org.jetbrains.kotlin.ir.declarations.IrProperty
import org.jetbrains.kotlin.ir.declarations.IrSimpleFunction
import org.jetbrains.kotlin.ir.declarations.IrValueParameter
import org.jetbrains.kotlin.ir.declarations.IrVariable
import org.jetbrains.kotlin.ir.expressions.IrBlockBody
import org.jetbrains.kotlin.ir.expressions.IrBody
import org.jetbrains.kotlin.ir.expressions.IrCall
import org.jetbrains.kotlin.ir.expressions.IrExpression
import org.jetbrains.kotlin.ir.expressions.IrFunctionAccessExpression
import org.jetbrains.kotlin.ir.expressions.IrGetEnumValue
import org.jetbrains.kotlin.ir.expressions.IrGetValue
import org.jetbrains.kotlin.ir.expressions.IrReturn
import org.jetbrains.kotlin.ir.expressions.IrSetField
import org.jetbrains.kotlin.ir.expressions.IrSetValue
import org.jetbrains.kotlin.ir.expressions.IrWhen
import org.jetbrains.kotlin.ir.symbols.IrSimpleFunctionSymbol
import org.jetbrains.kotlin.ir.util.isTrueConst
import org.jetbrains.kotlin.ir.expressions.impl.IrThrowImpl
import org.jetbrains.kotlin.ir.util.defaultType
import org.jetbrains.kotlin.ir.util.file
import org.jetbrains.kotlin.ir.util.getAnnotation
import org.jetbrains.kotlin.ir.util.getAnnotationStringValue
import org.jetbrains.kotlin.ir.util.getValueArgument
import org.jetbrains.kotlin.ir.util.hasAnnotation
import org.jetbrains.kotlin.ir.util.implicitCastIfNeededTo
import org.jetbrains.kotlin.ir.util.isFileClass
import org.jetbrains.kotlin.ir.util.parentAsClass
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

    private enum class EffectiveDepth { BOUNDARY, BRANCHES, VARS }

    private val typeUnit = pluginContext.irBuiltIns.unitType
    private val typeThrowable = pluginContext.irBuiltIns.throwableType
    private val typeLong = pluginContext.irBuiltIns.longType
    private val typeAnyNullable = pluginContext.irBuiltIns.anyNType

    override fun visitSimpleFunction(declaration: IrSimpleFunction): IrStatement {
        if (!declaration.hasAnnotation(autoDebugFqName)) {
            return super.visitSimpleFunction(declaration)
        }
        val body = declaration.body
        if (body == null || declaration.isFakeOverride || declaration.isInline || declaration.isSuspend) {
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
        val depth = readDepth(function)
        val wantBranches = depth == EffectiveDepth.BRANCHES || depth == EffectiveDepth.VARS
        val wantVars = depth == EffectiveDepth.VARS
        val branchTransformer = if (wantBranches) {
            BranchLoggingTransformer(function, tag, methodName)
        } else {
            null
        }
        val varsTransformer = if (wantVars) {
            VarsLoggingTransformer(function, tag, methodName)
        } else {
            null
        }

        return DeclarationIrBuilder(pluginContext, function.symbol).irBlockBody {
            +irAutoDebugCall(symbols.logEnter).apply {
                putRegularArgument(0, irString(tag))
                putRegularArgument(1, irString(methodName))
                putRegularArgument(2, buildArgsDescription(function))
            }

            val start = irTemporary(
                irAutoDebugCall(symbols.currentTimeMillis),
                nameHint = "autodebugStart",
                irType = typeLong,
            )

            val tryBlock = irBlock(resultType = function.returnType) {
                for (statement in body.statements) {
                    var transformed: IrStatement = statement
                    if (branchTransformer != null) {
                        transformed = transformed.transform(branchTransformer, null) as IrStatement
                    }
                    if (varsTransformer != null) {
                        transformed = transformed.transform(varsTransformer, null) as IrStatement
                    }
                    +transformed
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

            val catch = IrCatchFactory.createCatch(startOffset, endOffset, catchParam, catchBlock)

            +irTry(typeUnit, tryBlock, listOf(catch), null)
        }
    }

    private fun IrBuilderWithScope.buildArgsDescription(function: IrSimpleFunction): IrExpression {
        val params = function.parameters.filter {
            it.kind == IrParameterKind.Regular || it.kind == IrParameterKind.Context
        }
        if (params.isEmpty()) {
            return irString("")
        }
        val namesArray = irCall(symbols.arrayOf).apply {
            putRegularArgument(
                0,
                irVararg(pluginContext.irBuiltIns.stringType, params.map { irString(it.name.asString()) }),
            )
        }
        val valuesArray = irCall(symbols.arrayOf).apply {
            putRegularArgument(
                0,
                irVararg(typeAnyNullable, params.map { irGet(it).implicitCastIfNeededTo(typeAnyNullable) }),
            )
        }
        return irAutoDebugCall(symbols.describeArgs).apply {
            putRegularArgument(0, namesArray)
            putRegularArgument(1, valuesArray)
        }
    }

    private fun IrBuilderWithScope.irLogExit(
        function: IrSimpleFunction,
        tag: String,
        methodName: String,
        start: IrVariable,
        result: IrExpression,
    ): IrExpression = irAutoDebugCall(symbols.logExit).apply {
        putRegularArgument(0, irString(tag))
        putRegularArgument(1, irString(methodName))
        putRegularArgument(2, result.implicitCastIfNeededTo(typeAnyNullable))
        putRegularArgument(3, irDurationSince(start))
    }

    private fun IrBuilderWithScope.irLogExitThrow(
        function: IrSimpleFunction,
        tag: String,
        methodName: String,
        start: IrVariable,
        throwable: IrExpression,
    ): IrExpression = irAutoDebugCall(symbols.logThrow).apply {
        putRegularArgument(0, irString(tag))
        putRegularArgument(1, irString(methodName))
        putRegularArgument(2, throwable)
        putRegularArgument(3, irDurationSince(start))
    }

    private fun IrBuilderWithScope.irDurationSince(start: IrVariable): IrExpression =
        irCall(symbols.longMinus).apply {
            val dispatch = symbol.owner.parameters.first { it.kind == IrParameterKind.DispatchReceiver }
            arguments[dispatch] = irAutoDebugCall(symbols.currentTimeMillis)
            putRegularArgument(0, irGet(start))
        }

    private fun IrBuilderWithScope.irAutoDebugReceiver(): IrExpression {
        val klass = symbols.logEnter.owner.parentAsClass
        return irGetObjectValue(klass.defaultType, klass.symbol)
    }

    private fun IrBuilderWithScope.irAutoDebugCall(callee: IrSimpleFunctionSymbol): IrCall =
        irCall(callee).apply {
            val dispatch = callee.owner.parameters.firstOrNull { it.kind == IrParameterKind.DispatchReceiver }
            if (dispatch != null) {
                arguments[dispatch] = irAutoDebugReceiver()
            }
        }

    private fun IrFunctionAccessExpression.putRegularArgument(index: Int, value: IrExpression?) {
        val regular = symbol.owner.parameters.filter {
            it.kind == IrParameterKind.Regular || it.kind == IrParameterKind.Context
        }
        arguments[regular[index]] = value
    }

    private fun IrFunctionAccessExpression.getRegularArgument(index: Int): IrExpression? {
        val regular = symbol.owner.parameters.filter {
            it.kind == IrParameterKind.Regular || it.kind == IrParameterKind.Context
        }
        return arguments[regular[index]]
    }

    private fun readDepth(function: IrSimpleFunction): EffectiveDepth {
        val annotation = function.getAnnotation(autoDebugFqName) ?: return EffectiveDepth.BOUNDARY
        val depthExpr = annotation.getValueArgument(Name.identifier("depth")) ?: return EffectiveDepth.BOUNDARY
        val enumEntryName = when (depthExpr) {
            is IrGetEnumValue -> depthExpr.symbol.owner.name.asString()
            else -> return EffectiveDepth.BOUNDARY
        }
        return when (enumEntryName) {
            "BRANCHES" -> EffectiveDepth.BRANCHES
            "VARS" -> EffectiveDepth.VARS
            else -> EffectiveDepth.BOUNDARY
        }
    }

    private fun isDispatchThis(receiver: IrExpression?, function: IrSimpleFunction): Boolean {
        if (receiver == null) return false
        val thisParam = function.dispatchReceiverParameter ?: return false
        return receiver is IrGetValue && receiver.symbol == thisParam.symbol
    }

    private fun isLocalMutableVariable(setValue: IrSetValue): Boolean {
        val owner = setValue.symbol.owner
        return owner is IrVariable && owner !is IrValueParameter
    }

    private fun isThisPropertySetterCall(call: IrCall, function: IrSimpleFunction): Boolean {
        val callee = call.symbol.owner as? IrSimpleFunction ?: return false
        val property = callee.correspondingPropertySymbol?.owner as? IrProperty ?: return false
        if (property.setter?.symbol != callee.symbol) return false
        return isDispatchThis(call.dispatchReceiver, function)
    }

    private fun isLateinitField(field: IrField): Boolean {
        val property = field.correspondingPropertySymbol?.owner as? IrProperty ?: return false
        return property.isLateinit
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

    private fun isConstantTrue(condition: IrExpression): Boolean = condition.isTrueConst()

    private fun isBinaryIf(whenExpr: IrWhen): Boolean =
        whenExpr.branches.size == 2 && isConstantTrue(whenExpr.branches.last().condition)

    private fun branchLabel(whenExpr: IrWhen, index: Int): String {
        val binaryIf = isBinaryIf(whenExpr)
        return if (binaryIf) {
            if (index == 0) "if#$index-then" else "if#$index-else"
        } else {
            val branch = whenExpr.branches[index]
            if (index == whenExpr.branches.lastIndex && isConstantTrue(branch.condition)) {
                "when#$index-else"
            } else {
                "when#$index"
            }
        }
    }

    private inner class VarsLoggingTransformer(
        private val function: IrSimpleFunction,
        private val tag: String,
        private val methodName: String,
    ) : IrElementTransformerVoid() {
        override fun visitSetValue(expression: IrSetValue): IrExpression {
            val visited = super.visitSetValue(expression) as IrSetValue
            if (!isLocalMutableVariable(visited)) {
                return visited
            }
            val variable = visited.symbol.owner as IrVariable
            return DeclarationIrBuilder(pluginContext, function.symbol).irBlock(resultType = visited.type) {
                val oldValue = irGet(variable).implicitCastIfNeededTo(typeAnyNullable)
                val newTemp = irTemporary(visited.value, nameHint = "autodebugNew")
                +irAutoDebugCall(symbols.logAssignment).apply {
                    putRegularArgument(0, irString(tag))
                    putRegularArgument(1, irString(methodName))
                    putRegularArgument(2, irString(variable.name.asString()))
                    putRegularArgument(3, oldValue)
                    putRegularArgument(4, irGet(newTemp).implicitCastIfNeededTo(typeAnyNullable))
                }
                +irSet(visited.symbol, irGet(newTemp))
            }
        }

        override fun visitSetField(expression: IrSetField): IrExpression {
            val visited = super.visitSetField(expression) as IrSetField
            if (!isDispatchThis(visited.receiver, function)) {
                return visited
            }
            val field = visited.symbol.owner as IrField
            val receiver = visited.receiver!!
            return DeclarationIrBuilder(pluginContext, function.symbol).irBlock(resultType = visited.type) {
                val oldValue = if (isLateinitField(field)) {
                    irNull(typeAnyNullable)
                } else {
                    irGetField(receiver, field).implicitCastIfNeededTo(typeAnyNullable)
                }
                val newTemp = irTemporary(visited.value, nameHint = "autodebugNew")
                +irAutoDebugCall(symbols.logAssignment).apply {
                    putRegularArgument(0, irString(tag))
                    putRegularArgument(1, irString(methodName))
                    putRegularArgument(2, irString(field.name.asString()))
                    putRegularArgument(3, oldValue)
                    putRegularArgument(4, irGet(newTemp).implicitCastIfNeededTo(typeAnyNullable))
                }
                +irSetField(receiver, field, irGet(newTemp))
            }
        }

        override fun visitCall(expression: IrCall): IrExpression {
            val visited = super.visitCall(expression) as IrCall
            if (!isThisPropertySetterCall(visited, function)) {
                return visited
            }
            val property = (visited.symbol.owner as IrSimpleFunction)
                .correspondingPropertySymbol!!
                .owner as IrProperty
            val getter = property.getter ?: return visited
            val receiver = visited.dispatchReceiver!!
            val newValue = visited.getRegularArgument(0) ?: return visited
            return DeclarationIrBuilder(pluginContext, function.symbol).irBlock(resultType = visited.type) {
                val oldValue = if (property.isLateinit) {
                    irNull(typeAnyNullable)
                } else {
                    irCall(getter.symbol).apply {
                        val dispatch = getter.parameters.first { it.kind == IrParameterKind.DispatchReceiver }
                        arguments[dispatch] = receiver
                    }.implicitCastIfNeededTo(typeAnyNullable)
                }
                val newTemp = irTemporary(newValue, nameHint = "autodebugNew")
                +irAutoDebugCall(symbols.logAssignment).apply {
                    putRegularArgument(0, irString(tag))
                    putRegularArgument(1, irString(methodName))
                    putRegularArgument(2, irString(property.name.asString()))
                    putRegularArgument(3, oldValue)
                    putRegularArgument(4, irGet(newTemp).implicitCastIfNeededTo(typeAnyNullable))
                }
                +irCall(visited.symbol).apply {
                    val dispatch = visited.symbol.owner.parameters.first {
                        it.kind == IrParameterKind.DispatchReceiver
                    }
                    arguments[dispatch] = receiver
                    putRegularArgument(0, irGet(newTemp))
                }
            }
        }
    }

    private inner class BranchLoggingTransformer(
        private val function: IrSimpleFunction,
        private val tag: String,
        private val methodName: String,
    ) : IrElementTransformerVoid() {
        override fun visitWhen(expression: IrWhen): IrExpression {
            val visited = super.visitWhen(expression) as IrWhen
            for (i in visited.branches.indices) {
                val branch = visited.branches[i]
                val label = branchLabel(visited, i)
                branch.result = wrapBranchResult(branch.result, label)
            }
            return visited
        }

        private fun wrapBranchResult(result: IrExpression, label: String): IrExpression =
            DeclarationIrBuilder(pluginContext, function.symbol).irBlock(resultType = result.type) {
                +irAutoDebugCall(symbols.logBranch).apply {
                    putRegularArgument(0, irString(tag))
                    putRegularArgument(1, irString(methodName))
                    putRegularArgument(2, irString(label))
                }
                +result
            }
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
