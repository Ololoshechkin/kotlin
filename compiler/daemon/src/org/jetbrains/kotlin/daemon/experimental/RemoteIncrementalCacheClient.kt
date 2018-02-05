/*
 * Copyright 2000-2018 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license
 * that can be found in the license/LICENSE.txt file.
 */

package org.jetbrains.kotlin.daemon.experimental

import org.jetbrains.kotlin.daemon.common.experimental.CompilerCallbackServicesFacade
import org.jetbrains.kotlin.daemon.common.experimental.DummyProfiler
import org.jetbrains.kotlin.daemon.common.experimental.Profiler
import org.jetbrains.kotlin.load.kotlin.incremental.components.IncrementalCache
import org.jetbrains.kotlin.load.kotlin.incremental.components.JvmPackagePartProto
import org.jetbrains.kotlin.modules.TargetId

class RemoteIncrementalCacheClient(val facade: CompilerCallbackServicesFacade, val target: TargetId, val profiler: Profiler = DummyProfiler()): IncrementalCache {

    override fun getObsoletePackageParts(): Collection<String> = profiler.withMeasure(this) { facade.incrementalCache_getObsoletePackageParts(target) }

    override fun getObsoleteMultifileClasses(): Collection<String> = profiler.withMeasure(this) { facade.incrementalCache_getObsoleteMultifileClassFacades(target) }

    override fun getStableMultifileFacadeParts(facadeInternalName: String): Collection<String>? = profiler.withMeasure(this) { facade.incrementalCache_getMultifileFacadeParts(target, facadeInternalName) }

    override fun getPackagePartData(partInternalName: String): JvmPackagePartProto? = profiler.withMeasure(this) { facade.incrementalCache_getPackagePartData(target, partInternalName) }

    override fun getModuleMappingData(): ByteArray? = profiler.withMeasure(this) { facade.incrementalCache_getModuleMappingData(target) }

    override fun getClassFilePath(internalClassName: String): String = profiler.withMeasure(this) { facade.incrementalCache_getClassFilePath(target,internalClassName) }

    override fun close(): Unit = profiler.withMeasure(this) { facade.incrementalCache_close(target) }
}