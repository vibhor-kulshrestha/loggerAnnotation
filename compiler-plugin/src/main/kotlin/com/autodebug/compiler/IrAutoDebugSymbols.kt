package com.autodebug.compiler

import org.jetbrains.kotlin.backend.common.extensions.IrPluginContext
import org.jetbrains.kotlin.ir.symbols.IrSimpleFunctionSymbol
import org.jetbrains.kotlin.name.CallableId
import org.jetbrains.kotlin.name.FqName
import org.jetbrains.kotlin.name.Name

class IrAutoDebugSymbols(val context: IrPluginContext) {
    private val runtimePackage = FqName("com.autodebug.runtime")
    private val autoDebugClass = FqName("AutoDebug")

    val logEnter: IrSimpleFunctionSymbol = context.referenceFunctions(
        CallableId(runtimePackage, autoDebugClass, Name.identifier("logEnter")),
    ).single()

    val logExit: IrSimpleFunctionSymbol = context.referenceFunctions(
        CallableId(runtimePackage, autoDebugClass, Name.identifier("logExit")),
    ).single()

    val logThrow: IrSimpleFunctionSymbol = context.referenceFunctions(
        CallableId(runtimePackage, autoDebugClass, Name.identifier("logThrow")),
    ).single()

    val logBranch: IrSimpleFunctionSymbol = context.referenceFunctions(
        CallableId(runtimePackage, autoDebugClass, Name.identifier("logBranch")),
    ).single()

    val describeArgs: IrSimpleFunctionSymbol = context.referenceFunctions(
        CallableId(runtimePackage, autoDebugClass, Name.identifier("describeArgs")),
    ).single()

    val currentTimeMillis: IrSimpleFunctionSymbol = context.referenceFunctions(
        CallableId(runtimePackage, autoDebugClass, Name.identifier("currentTimeMillis")),
    ).single()

    val arrayOf: IrSimpleFunctionSymbol = context.irBuiltIns.arrayOf

    val longMinus: IrSimpleFunctionSymbol = context.irBuiltIns.getBinaryOperator(
        Name.identifier("minus"),
        context.irBuiltIns.longType,
        context.irBuiltIns.longType,
    )
}
