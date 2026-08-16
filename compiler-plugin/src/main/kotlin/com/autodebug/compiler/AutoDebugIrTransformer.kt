package com.autodebug.compiler

import org.jetbrains.kotlin.ir.IrStatement
import org.jetbrains.kotlin.ir.declarations.IrSimpleFunction
import org.jetbrains.kotlin.ir.util.hasAnnotation
import org.jetbrains.kotlin.ir.visitors.IrElementTransformerVoid
import org.jetbrains.kotlin.name.FqName

/**
 * Phase 0: detect @AutoDebug and leave the body unchanged.
 * Phase 1 will inject enter/exit logging here.
 */
class AutoDebugIrTransformer(
    private val enabled: Boolean,
) : IrElementTransformerVoid() {

    private val autoDebugFqName = FqName("com.autodebug.AutoDebug")

    override fun visitSimpleFunction(declaration: IrSimpleFunction): IrStatement {
        if (enabled && declaration.hasAnnotation(autoDebugFqName)) {
            // Identity: intentionally no body mutation in Phase 0.
        }
        return super.visitSimpleFunction(declaration)
    }
}
