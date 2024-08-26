package com.phodal.shirelang.compiler.hobbit.execute

import com.intellij.openapi.application.ReadAction
import com.intellij.openapi.project.Project
import com.intellij.openapi.vfs.readText
import com.intellij.psi.PsiFile
import com.intellij.psi.PsiManager
import com.phodal.shirecore.provider.http.HttpHandler
import com.phodal.shirecore.provider.http.HttpHandlerType
import com.phodal.shirecore.provider.shire.FileRunService
import com.phodal.shirelang.actions.ShireRunFileAction
import com.phodal.shirelang.psi.ShireFile
import com.phodal.shirelang.utils.lookupFile

object ThreadProcessor {
    fun execute(
        myProject: Project, fileName: String, variables: Array<String>, variableTable: MutableMap<String, Any?>,
    ): String {
        val file = myProject.lookupFile(fileName) ?: return "File not found: $fileName"

        val filename = file.name.lowercase()
        val content = file.readText()

        // if ends with .cURL.sh, try call cURL service
        if (filename.endsWith(".curl.sh")) {
            val execute = HttpHandler.provide(HttpHandlerType.CURL)?.execute(myProject, content)
            if (execute != null) {
                return execute
            }
        }

        val psiFile = ReadAction.compute<PsiFile, Throwable> {
            PsiManager.getInstance(myProject).findFile(file)
        } ?: return "Failed to find PSI file for $fileName"

        when (psiFile) {
            is ShireFile -> {
                return when (val output = variableTable["output"]) {
                    is List<*> -> {
                        val results = output.map {
                            variableTable["output"] = it
                            executeTask(myProject, variables, variableTable, psiFile)
                        }

                        results.joinToString("\n")
                    }

                    is Array<*> -> {
                        output.joinToString("\n") {
                            variableTable["output"] = it
                            executeTask(myProject, variables, variableTable, psiFile)
                        }
                    }

                    else -> {
                        return executeTask(myProject, variables, variableTable, psiFile)
                    }
                }
            }

            else -> return FileRunService.provider(myProject, file)?.runFile(myProject, file, psiFile)
                ?: "No run service found"
        }
    }

    private fun executeTask(
        myProject: Project,
        variables: Array<String>,
        variableTable: MutableMap<String, Any?>,
        psiFile: ShireFile,
    ): String {
        val executeResult = ShireRunFileAction.suspendExecuteFile(myProject, variables, variableTable, psiFile)
        return executeResult ?: "No run service found"
    }
}
