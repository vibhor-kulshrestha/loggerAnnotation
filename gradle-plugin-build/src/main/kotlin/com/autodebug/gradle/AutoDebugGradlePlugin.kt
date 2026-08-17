package com.autodebug.gradle

import org.gradle.api.Project
import org.gradle.api.provider.Provider
import org.jetbrains.kotlin.gradle.plugin.KotlinCompilation
import org.jetbrains.kotlin.gradle.plugin.KotlinCompilerPluginSupportPlugin
import org.jetbrains.kotlin.gradle.plugin.SubpluginArtifact
import org.jetbrains.kotlin.gradle.plugin.SubpluginOption

class AutoDebugGradlePlugin : KotlinCompilerPluginSupportPlugin {
    override fun apply(target: Project) {
        // Extension point for future: autodebug { enabled.set(true) }
    }

    override fun isApplicable(kotlinCompilation: KotlinCompilation<*>): Boolean = true

    override fun getCompilerPluginId(): String = "com.autodebug"

    override fun getPluginArtifact(): SubpluginArtifact =
        SubpluginArtifact(
            groupId = "io.github.vibhor-kulshrestha",
            artifactId = "autodebug-compiler-plugin",
            version = "0.1.3",
        )

    override fun applyToCompilation(
        kotlinCompilation: KotlinCompilation<*>,
    ): Provider<List<SubpluginOption>> {
        val project = kotlinCompilation.target.project
        return project.provider {
            val name = kotlinCompilation.name.lowercase()
            val enabled = name.contains("debug") && !name.contains("release")
            listOf(SubpluginOption(key = "enabled", value = enabled.toString()))
        }
    }
}
