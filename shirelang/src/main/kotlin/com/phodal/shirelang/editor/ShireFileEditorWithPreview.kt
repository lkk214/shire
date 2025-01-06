package com.phodal.shirelang.editor

import com.intellij.icons.AllIcons
import com.intellij.ide.BrowserUtil
import com.intellij.openapi.actionSystem.*
import com.intellij.openapi.actionSystem.ex.ActionUtil
import com.intellij.openapi.editor.event.VisibleAreaEvent
import com.intellij.openapi.editor.event.VisibleAreaListener
import com.intellij.openapi.editor.ex.util.EditorUtil
import com.intellij.openapi.editor.impl.EditorImpl
import com.intellij.openapi.fileEditor.TextEditor
import com.intellij.openapi.fileEditor.TextEditorWithPreview
import com.intellij.openapi.fileEditor.impl.text.TextEditorProvider
import com.intellij.openapi.project.DumbService
import com.intellij.openapi.project.Project
import com.intellij.openapi.vfs.VirtualFile

class ShireFileEditorWithPreview(
    private val ourEditor: TextEditor,
    @JvmField var preview: ShirePreviewEditor,
    private val project: Project,
) : TextEditorWithPreview(
    ourEditor, preview,
    "Shire Split Editor",
    Layout.SHOW_EDITOR_AND_PREVIEW,
) {
    val virtualFile: VirtualFile = ourEditor.file

    init {
        // allow launching actions while in preview mode;
        preview.setMainEditor(ourEditor.editor)
        ourEditor.editor.scrollingModel.addVisibleAreaListener(MyVisibleAreaListener(), this)
    }

    override fun dispose() {
        TextEditorProvider.getInstance().disposeEditor(ourEditor)
    }

    inner class MyVisibleAreaListener() : VisibleAreaListener {
        private var previousLine = 0

        override fun visibleAreaChanged(event: VisibleAreaEvent) {
            val editor = event.editor
            val y = editor.scrollingModel.verticalScrollOffset
            val currentLine = if (editor is EditorImpl) editor.yToVisualLine(y) else y / editor.lineHeight
            if (currentLine == previousLine) {
                return
            }

            previousLine = currentLine
            preview.scrollToSrcOffset(EditorUtil.getVisualLineEndOffset(editor, currentLine))
        }
    }

    override fun createToolbar(): ActionToolbar {
        return ActionManager.getInstance()
            .createActionToolbar(ActionPlaces.EDITOR_TOOLBAR, createActionGroup(project), true)
            .also {
                it.targetComponent = editor.contentComponent
            }
    }

    private fun createActionGroup(project: Project): ActionGroup {
        return DefaultActionGroup(
            object : AnAction("Show Preview", "Show Shire Prompt Preview", AllIcons.Actions.Preview) {
                override fun getActionUpdateThread(): ActionUpdateThread = ActionUpdateThread.BGT
                override fun update(e: AnActionEvent) {
                    e.presentation.isEnabled = project != null && !DumbService.isDumb(project)
                }

                override fun actionPerformed(e: AnActionEvent) {
                    DumbService.getInstance(project).runWhenSmart {
                        preview.component.isVisible = true
                        preview.rerenderShire()
                    }
                }
            },
            object : AnAction("Refresh Preview", "Refresh Preview", AllIcons.Actions.Refresh) {
                override fun getActionUpdateThread(): ActionUpdateThread = ActionUpdateThread.BGT
                override fun update(e: AnActionEvent) {
                    e.presentation.isEnabled = project != null && !DumbService.isDumb(project)
                }

                override fun actionPerformed(e: AnActionEvent) {
                    DumbService.getInstance(project).runWhenSmart {
                        preview.updateDisplayedContent()
                    }
                }
            },
            Separator(),
            object : AnAction("Help", "Help", AllIcons.Actions.Help) {
                override fun getActionUpdateThread(): ActionUpdateThread = ActionUpdateThread.BGT
                override fun actionPerformed(e: AnActionEvent) {
                    val url = "https://shire.phodal.com/"
                    BrowserUtil.browse(url)
                }
            }
        )
    }

}