/*
 * Copyright 2000-2018 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license
 * that can be found in the license/LICENSE.txt file.
 */

package org.jetbrains.kotlin.idea.compiler

import com.intellij.openapi.project.Project
import com.intellij.psi.search.GlobalSearchScope
import getAnnotationsOnContainingJsModule
import org.jetbrains.kotlin.descriptors.DeclarationDescriptor
import org.jetbrains.kotlin.idea.caches.resolve.IDEPackagePartProvider
import org.jetbrains.kotlin.load.kotlin.getJvmModuleName
import org.jetbrains.kotlin.name.ClassId
import org.jetbrains.kotlin.resolve.ModuleAnnotationsResolver

class IdeModuleAnnotationsResolver(private val project: Project) : ModuleAnnotationsResolver {
    override fun getAnnotationsOnContainingModule(descriptor: DeclarationDescriptor): List<ClassId> {
        getAnnotationsOnContainingJsModule(descriptor)?.let { return it }

        val moduleName = getJvmModuleName(descriptor) ?: return emptyList()
        // TODO: allScope is incorrect here, need to look only in the root where this element comes from
        return IDEPackagePartProvider(GlobalSearchScope.allScope(project)).getAnnotationsOnBinaryModule(moduleName)
    }
}
