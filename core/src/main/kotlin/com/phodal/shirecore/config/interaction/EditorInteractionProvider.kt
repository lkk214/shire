package com.phodal.shirecore.config.interaction

import com.intellij.execution.ui.ConsoleViewContentType
import com.intellij.openapi.application.invokeLater
import com.intellij.openapi.application.runReadAction
import com.intellij.openapi.diagnostic.logger
import com.intellij.openapi.progress.ProgressManager
import com.intellij.openapi.progress.impl.BackgroundableProcessIndicator
import com.intellij.openapi.wm.ToolWindowManager
import com.intellij.ui.content.impl.ContentManagerImpl
import com.phodal.shirecore.ShireCoroutineScope
import com.phodal.shirecore.ShirelangNotifications
import com.phodal.shirecore.config.InteractionType
import com.phodal.shirecore.config.interaction.dto.CodeCompletionRequest
import com.phodal.shirecore.config.interaction.task.ChatCompletionTask
import com.phodal.shirecore.config.interaction.task.FileGenerateTask
import com.phodal.shirecore.config.interaction.task.cancelWithConsole
import com.phodal.shirecore.diff.DiffStreamHandler
import com.phodal.shirecore.runner.console.cancelWithConsole
import com.phodal.shirecore.llm.LlmProvider
import com.phodal.shirecore.provider.ide.LocationInteractionContext
import com.phodal.shirecore.provider.ide.LocationInteractionProvider
import com.phodal.shirecore.runner.console.cancelHandler
import com.phodal.shirecore.ui.ShirePanelView
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.cancellable
import kotlinx.coroutines.launch

class EditorInteractionProvider : LocationInteractionProvider {
    override fun isApplicable(context: LocationInteractionContext): Boolean {
        return true
    }

    override fun execute(context: LocationInteractionContext, postExecute: PostFunction) {
        val targetFile = context.editor?.virtualFile

        when (context.interactionType) {
            InteractionType.AppendCursor,
            InteractionType.AppendCursorStream,
                -> {
                val task = createTask(
                    context,
                    context.prompt,
                    isReplacement = false,
                    postExecute = postExecute,
                    false
                )?.cancelWithConsole(context.console)

                if (task == null) {
                    ShirelangNotifications.error(context.project, "Failed to create code completion task.")
                    postExecute.invoke("", null)
                    return
                }

                ProgressManager.getInstance()
                    .runProcessWithProgressAsynchronously(task, BackgroundableProcessIndicator(task))
            }

            InteractionType.OutputFile -> {
                val fileName = targetFile?.name
                val task = FileGenerateTask(
                    context.project,
                    context.prompt,
                    fileName,
                    postExecute = postExecute
                ).cancelWithConsole(context.console)
                ProgressManager.getInstance()
                    .runProcessWithProgressAsynchronously(task, BackgroundableProcessIndicator(task))
            }

            InteractionType.ReplaceSelection -> {
                val task =
                    createTask(context, context.prompt, true, postExecute, false)?.cancelWithConsole(context.console)

                if (task == null) {
                    ShirelangNotifications.error(context.project, "Failed to create code completion task.")
                    postExecute.invoke("", null)
                    return
                }

                ProgressManager.getInstance()
                    .runProcessWithProgressAsynchronously(task, BackgroundableProcessIndicator(task))
            }

            InteractionType.ReplaceCurrentFile -> {
                val fileName = targetFile?.name
                val task = FileGenerateTask(
                    context.project,
                    context.prompt,
                    fileName,
                    postExecute = postExecute
                ).cancelWithConsole(context.console)

                ProgressManager.getInstance()
                    .runProcessWithProgressAsynchronously(task, BackgroundableProcessIndicator(task))
            }

            InteractionType.InsertBeforeSelection -> {
                val task =
                    createTask(context, context.prompt, false, postExecute, isInsertBefore = true)?.cancelWithConsole(
                        context.console
                    )

                if (task == null) {
                    ShirelangNotifications.error(context.project, "Failed to create code completion task.")
                    postExecute.invoke("", null)
                    return
                }

                ProgressManager.getInstance()
                    .runProcessWithProgressAsynchronously(task, BackgroundableProcessIndicator(task))
            }

            InteractionType.RunPanel -> {
                val flow: Flow<String>? = LlmProvider.provider(context.project)?.stream(context.prompt, "", false)
                ShireCoroutineScope.scope(context.project).launch {
                    val suggestion = StringBuilder()

                    flow?.cancelWithConsole(context.console)?.cancellable()?.collect { char ->
                        suggestion.append(char)

                        invokeLater {
                            context.console?.print(char, ConsoleViewContentType.NORMAL_OUTPUT)
                        }
                    }

                    postExecute.invoke(suggestion.toString(), null)
                }
            }

            InteractionType.RightPanel -> {
                val toolWindowManager =
                    ToolWindowManager.getInstance(context.project).getToolWindow("ShireToolWindow") ?: run {
                        logger<EditorInteractionProvider>().warn("Tool window not found")
                        return
                    }

                val contentManager = toolWindowManager.contentManager
                val panelView = ShirePanelView(context.project)
                contentManager.factory.createContent(panelView, "Shire RightPanel Run", false).let {
                    // Default is 3, configurable in the future.
                    if (contentManager.contentCount >= 3) {
                        contentManager.getContent(0)?.let { content ->
                            contentManager.removeContent(content, false)
                            (content.component as? ShirePanelView)?.cancel("This content is removed")
                        }
                    }
                    contentManager.addContent(it)
                    (contentManager as ContentManagerImpl).setSelectedContent(it)
                }


                toolWindowManager.activate(null)

                val flow: Flow<String>? = LlmProvider.provider(context.project)?.stream(context.prompt, "", false)
                ShireCoroutineScope.scope(context.project).launch {
                    val suggestion = StringBuilder()
                    panelView.onStart()
                    panelView.addRequestPrompt(context.prompt)

                    flow?.cancelHandler { panelView.handleCancel = it }?.cancellable()?.collect { char ->
                        suggestion.append(char)

                        invokeLater {
                            panelView.onUpdate(suggestion.toString())
                        }
                    }

                    panelView.onFinish(suggestion.toString())
                    postExecute.invoke(suggestion.toString(), null)
                }
            }

            InteractionType.OnPaste -> {
                /**
                 *  already handle in [com.phodal.shirelang.actions.copyPaste.ShireCopyPastePreProcessor]
                 */
            }

            InteractionType.StreamDiff -> {
                if (context.editor == null) {
                    ShirelangNotifications.error(context.project, "Editor is null, please open a file to continue.")
                    return
                }

                val code = context.editor.document.text
                val diffStreamHandler = DiffStreamHandler(
                    context.project,
                    editor = context.editor,
                    0,
                    code.lines().size,
                    onClose = {
                    },
                    onFinish = {
                        postExecute.invoke(it, null)
                        ShirelangNotifications.info(context.project, "Patch Applied")
                    })

                diffStreamHandler.streamDiffLinesToEditor(code, context.prompt)
            }
        }
    }

    private fun createTask(
        context: LocationInteractionContext,
        userPrompt: String,
        isReplacement: Boolean,
        postExecute: PostFunction,
        isInsertBefore: Boolean,
    ): ChatCompletionTask? {
        if (context.editor == null) {
            ShirelangNotifications.error(context.project, "Editor is null, please open a file to continue.")
            return null
        }

        val editor = context.editor

        val offset = if (isInsertBefore) {
            editor.selectionModel.selectionStart
        } else {
            editor.caretModel.offset
        }

        val request = runReadAction {
            CodeCompletionRequest.create(
                editor,
                offset,
                userPrompt = userPrompt,
                isReplacement = isReplacement,
                postExecute = postExecute,
                isInsertBefore = isInsertBefore,
            )
        } ?: return null

        val task = ChatCompletionTask(request)
        return task
    }
}