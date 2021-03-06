/*
 * Copyright 2000-2018 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license
 * that can be found in the license/LICENSE.txt file.
 */

package org.jetbrains.kotlin.idea.core.script

import com.intellij.openapi.application.ApplicationManager
import com.intellij.openapi.application.Result
import com.intellij.openapi.application.WriteAction
import com.intellij.openapi.editor.Document
import com.intellij.openapi.editor.EditorFactory
import com.intellij.openapi.editor.event.DocumentEvent
import com.intellij.openapi.editor.event.DocumentListener
import com.intellij.openapi.externalSystem.service.project.manage.ExternalProjectsManager
import com.intellij.openapi.externalSystem.util.ExternalSystemUtil
import com.intellij.openapi.fileEditor.FileDocumentManager
import com.intellij.openapi.fileEditor.impl.FileDocumentManagerImpl
import com.intellij.openapi.project.Project
import com.intellij.openapi.roots.ProjectRootManager
import com.intellij.openapi.vfs.VirtualFile
import com.intellij.openapi.vfs.VirtualFileManager
import com.intellij.openapi.vfs.newvfs.BulkFileListener
import com.intellij.openapi.vfs.newvfs.events.VFileEvent
import com.intellij.psi.PsiDocumentManager
import com.intellij.util.ui.update.MergingUpdateQueue
import com.intellij.util.ui.update.MergingUpdateQueue.ANY_COMPONENT
import com.intellij.util.ui.update.Update
import org.jetbrains.plugins.gradle.service.project.GradleAutoImportAware

class ScriptModificationListener(private val project: Project) {
    private val changedDocuments = HashSet<Document>()
    private val changedDocumentsQueue = MergingUpdateQueue("ScriptModificationListener: Scripts queue", 1000, false, ANY_COMPONENT, project)

    init {
        showNotificationIfScriptChangedListener()

        saveScriptAfterModificationListener()
    }

    private fun showNotificationIfScriptChangedListener() {
        project.messageBus.connect().subscribe(VirtualFileManager.VFS_CHANGES, object : BulkFileListener.Adapter() {
            override fun after(events: List<VFileEvent>) {
                if (ApplicationManager.getApplication().isUnitTestMode) return

                val modifiedScripts = events.mapNotNull {
                    it.file?.takeIf { isGradleScript(it) }
                }

                // Workaround for IDEA-182367 (fixed in IDEA 181.3666)
                if (modifiedScripts.isNotEmpty()) {
                    if (modifiedScripts.any {
                            GradleAutoImportAware().getAffectedExternalProjectPath(it.path, project) != null
                        }) {
                        return
                    }
                    ExternalProjectsManager.getInstance(project).externalProjectsWatcher.markDirty(project.basePath)
                }
            }
        })
    }

    private fun saveScriptAfterModificationListener() {
        // partially copied from ExternalSystemProjectsWatcherImpl before fix will be implemented in IDEA:
        // "Gradle projects need to be imported" notification should be shown when kotlin script is modified
        val busConnection = project.messageBus.connect(changedDocumentsQueue)
        changedDocumentsQueue.activate()

        EditorFactory.getInstance().eventMulticaster.addDocumentListener(object : DocumentListener {
            override fun documentChanged(event: DocumentEvent) {
                if (project.isDisposed) return

                val doc = event.document
                val file = FileDocumentManager.getInstance().getFile(doc) ?: return

                if (isGradleScript(file) && event.newFragment.isNotBlank()) {
                    synchronized(changedDocuments) {
                        changedDocuments.add(doc)
                    }

                    changedDocumentsQueue.queue(object : Update(this) {
                        override fun run() {
                            var copy: Array<Document> = emptyArray()

                            synchronized(changedDocuments) {
                                copy = changedDocuments.toTypedArray()
                                changedDocuments.clear()
                            }

                            ExternalSystemUtil.invokeLater(project) {
                                object : WriteAction<Any>() {
                                    override fun run(result: Result<Any>) {
                                        for (each in copy) {
                                            PsiDocumentManager.getInstance(project).commitDocument(each)
                                            (FileDocumentManager.getInstance() as? FileDocumentManagerImpl)?.saveDocument(each, false)
                                        }
                                    }
                                }.execute()
                            }
                        }
                    })
                }
            }
        }, busConnection)
    }

    private fun isGradleScript(file: VirtualFile): Boolean {
        if (!ProjectRootManager.getInstance(project).fileIndex.isInContent(file)) return false

        val contributor = ScriptDefinitionContributor.find<GradleScriptDefinitionsContributor>(project) ?: return false
        return ScriptDefinitionsManager.getInstance(project).getDefinitionsBy(contributor).any {
            it.isScript(file.name)
        }
    }
}
