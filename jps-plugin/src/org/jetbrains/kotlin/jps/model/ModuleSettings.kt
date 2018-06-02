/*
 * Copyright 2010-2018 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license
 * that can be found in the license/LICENSE.txt file.
 */

package org.jetbrains.kotlin.jps.model

import org.jetbrains.jps.model.ex.JpsElementBase
import org.jetbrains.jps.model.ex.JpsElementChildRoleBase
import org.jetbrains.jps.model.module.JpsModule
import org.jetbrains.kotlin.cli.common.arguments.*
import org.jetbrains.kotlin.config.CompilerSettings
import org.jetbrains.kotlin.config.KotlinFacetSettings
import org.jetbrains.kotlin.config.TargetPlatformKind

val JpsModule.kotlinFacet: JpsKotlinFacetModuleExtension?
    get() = container.getChild(JpsKotlinFacetModuleExtension.KIND)

val JpsModule.targetPlatform: TargetPlatformKind<*>?
    get() = kotlinFacet?.settings?.targetPlatformKind

val JpsModule.productionOutputFilePath: String?
    get() {
        val facetSettings = kotlinFacet?.settings ?: return null
        if (facetSettings.useProjectSettings) return null
        return facetSettings.productionOutputPath
    }

val JpsModule.testOutputFilePath: String?
    get() {
        val facetSettings = kotlinFacet?.settings ?: return null
        if (facetSettings.useProjectSettings) return null
        return facetSettings.testOutputPath
    }

val JpsModule.kotlinCompilerSettings: CompilerSettings
    get() {
        val defaultSettings = copyBean(project.kotlinCompilerSettings)
        val facetSettings = kotlinFacet?.settings ?: return defaultSettings
        if (facetSettings.useProjectSettings) return defaultSettings
        return facetSettings.compilerSettings ?: defaultSettings
    }

val JpsModule.kotlinCompilerArguments
    get() = getCompilerArguments<CommonCompilerArguments>()

val JpsModule.k2MetadataCompilerArguments
    get() = getCompilerArguments<K2MetadataCompilerArguments>()

val JpsModule.k2JsCompilerArguments
    get() = getCompilerArguments<K2JSCompilerArguments>()

val JpsModule.k2JvmCompilerArguments
    get() = getCompilerArguments<K2JVMCompilerArguments>()

private inline fun <reified T : CommonCompilerArguments> JpsModule.getCompilerArguments(): T {
    val projectSettings = project.kotlinCompilerSettingsContainer[T::class.java]
    val projectSettingsCopy = copyBean(projectSettings)

    val facetSettings = kotlinFacet?.settings ?: return projectSettingsCopy
    if (facetSettings.useProjectSettings) return projectSettingsCopy
    return facetSettings.compilerArguments as? T ?: projectSettingsCopy
}

class JpsKotlinFacetModuleExtension(settings: KotlinFacetSettings) : JpsElementBase<JpsKotlinFacetModuleExtension>() {
    var settings = settings
        private set

    companion object {
        val KIND = JpsElementChildRoleBase.create<JpsKotlinFacetModuleExtension>("kotlin facet extension")
        // These must be changed in sync with KotlinFacetType.TYPE_ID and KotlinFacetType.NAME
        val FACET_TYPE_ID = "kotlin-language"
        val FACET_NAME = "Kotlin"
    }

    override fun createCopy() = JpsKotlinFacetModuleExtension(settings)

    override fun applyChanges(modified: JpsKotlinFacetModuleExtension) {
        this.settings = modified.settings
    }
}