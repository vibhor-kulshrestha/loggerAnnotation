package com.autodebug.compiler

import org.jetbrains.kotlin.backend.common.extensions.IrPluginContext
import org.jetbrains.kotlin.ir.symbols.IrSimpleFunctionSymbol
import org.jetbrains.kotlin.name.CallableId
import org.jetbrains.kotlin.name.FqName
import org.jetbrains.kotlin.name.Name

class IrAutoDebugSymbols(val context: IrPluginContext) {
    private val owner = FqName("com.autodebug.runtime.AutoDebug")
    private val kotlinPackage = FqName("kotlin")

    val logEnter: IrSimpleFunctionSymbol = context.referenceFunctions(
        CallableId(owner, Name.identifier("logEnter")),
    ).single()

    val logExit: IrSimpleFunctionSymbol = context.referenceFunctions(
        CallableId(owner, Name.identifier("logExit")),
    ).single()

    val logThrow: IrSimpleFunctionSymbol = context.referenceFunctions(
        CallableId(owner, Name.identifier("logThrow")),
    ).single()

    val describeArgs: IrSimpleFunctionSymbol = context.referenceFunctions(
        CallableId(owner, Name.identifier("describeArgs")),
    ).single()

    val currentTimeMillis: IrSimpleFunctionSymbol = context.referenceFunctions(
        CallableId(FqName("java.lang.System"), Name.identifier("currentTimeMillis")),
    ).single { it.owner.valueParameters.isEmpty() }

    val arrayOfStrings: IrSimpleFunctionSymbol = context.referenceFunctions(
        CallableId(kotlinPackage, Name.identifier("arrayOf")),
    ).single {
        val param = it.owner.valueParameters.singleOrNull() ?: return@single false
        param.varargElementType == context.irBuiltIns.stringType
    }

    val arrayOfAnyNullable: IrSimpleFunctionSymbol = context.referenceFunctions(
        CallableId(kotlinPackage, Name.identifier("arrayOf")),
    ).single {
        val param = it.owner.valueParameters.singleOrNull() ?: return@single false
        param.varargElementType == context.irBuiltIns.anyNType
    }

    val longMinus: IrSimpleFunctionSymbol = context.referenceFunctions(
        CallableId(FqName("kotlin.Long"), Name.identifier("minus")),
    ).single { it.owner.valueParameters.size == 1 }
}
