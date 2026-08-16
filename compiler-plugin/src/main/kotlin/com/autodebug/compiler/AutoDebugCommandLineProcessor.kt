package com.autodebug.compiler

import org.jetbrains.kotlin.compiler.plugin.AbstractCliOption
import org.jetbrains.kotlin.compiler.plugin.CliOption
import org.jetbrains.kotlin.compiler.plugin.CommandLineProcessor
import org.jetbrains.kotlin.compiler.plugin.ExperimentalCompilerApi
import org.jetbrains.kotlin.config.CompilerConfiguration
import org.jetbrains.kotlin.config.CompilerConfigurationKey

object AutoDebugConfigurationKeys {
    val ENABLED: CompilerConfigurationKey<Boolean> =
        CompilerConfigurationKey.create("com.autodebug.enabled")
}

@OptIn(ExperimentalCompilerApi::class)
class AutoDebugCommandLineProcessor : CommandLineProcessor {
    override val pluginId: String = "com.autodebug"

    override val pluginOptions: Collection<AbstractCliOption> = listOf(
        CliOption(
            optionName = "enabled",
            valueDescription = "<true|false>",
            description = "Enable AutoDebug IR instrumentation",
            required = false,
        ),
    )

    override fun processOption(
        option: AbstractCliOption,
        value: String,
        configuration: CompilerConfiguration,
    ) {
        when (option.optionName) {
            "enabled" -> configuration.put(AutoDebugConfigurationKeys.ENABLED, value.toBooleanStrict())
            else -> error("Unknown plugin option: ${option.optionName}")
        }
    }
}
