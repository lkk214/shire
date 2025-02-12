package com.phodal.shirelang.compiler.parser

import com.intellij.lang.parser.GeneratedParserUtilBase.DUMMY_BLOCK
import com.intellij.openapi.application.runReadAction
import com.intellij.openapi.diagnostic.logger
import com.intellij.openapi.editor.Document
import com.intellij.openapi.editor.Editor
import com.intellij.openapi.project.Project
import com.intellij.openapi.project.guessProjectDir
import com.intellij.psi.PsiDocumentManager
import com.intellij.psi.PsiElement
import com.intellij.psi.TokenType.WHITE_SPACE
import com.intellij.psi.util.PsiTreeUtil
import com.intellij.psi.util.elementType
import com.phodal.shirecore.agent.CustomAgent
import com.phodal.shirelang.compiler.execute.command.*
import com.phodal.shirelang.compiler.template.TemplateCompiler
import com.phodal.shirelang.compiler.variable.VariableTable
import com.phodal.shirelang.completion.dataprovider.BuiltinCommand
import com.phodal.shirelang.completion.dataprovider.CustomCommand
import com.phodal.shirelang.parser.CodeBlockElement
import com.phodal.shirelang.psi.*
import com.phodal.shirelang.psi.ShireTypes.MARKDOWN_HEADER
import kotlinx.coroutines.runBlocking
import java.util.*


val CACHED_COMPILE_RESULT = mutableMapOf<String, ShireParsedResult>()

/**
 * ShireCompiler class is responsible for compiling Shire files by processing different elements such as text segments, newlines, code blocks, used commands, comments, agents, variables, and builtin commands.
 * It takes a Project, ShireFile, Editor, and PsiElement as input parameters.
 * The compile() function processes the elements in the ShireFile and generates a ShireCompiledResult object containing the compiled output.
 * The processUsed() function handles the processing of used commands, agents, and variables within the ShireFile.
 * The processingCommand() function executes the specified builtin command with the provided properties and updates the output accordingly.
 * The lookupNextCode() function looks up the next code block element following a used command.
 * The lookupNextTextSegment() function looks up the next text segment following a used command.
 */
class ShireSyntaxAnalyzer(
    private val myProject: Project,
    private val file: ShireFile,
    private val editor: Editor? = null,
    private val element: PsiElement? = null,
) {
    private var skipNextCode: Boolean = false
    private val logger = logger<ShireSyntaxAnalyzer>()
    private val result = ShireParsedResult()
    private val output: StringBuilder = StringBuilder()

    private val variableTable = VariableTable()

    companion object {
        const val FLOW_FALG = "[flow]:"
    }

    /**
     * @return ShireCompiledResult object containing the compiled result
     */
    fun parseAndExecuteLocalCommand(): ShireParsedResult {
        result.sourceCode = file.text
        val iterator = file.children.iterator()

        while (iterator.hasNext()) {
            val psiElement = iterator.next()

            when (psiElement.elementType) {
                ShireTypes.TEXT_SEGMENT -> output.append(psiElement.text)
                ShireTypes.NEWLINE -> output.append("\n")
                ShireTypes.CODE -> {
                    if (skipNextCode) {
                        skipNextCode = false
                        continue
                    }

                    output.append(psiElement.text)
                }

                ShireTypes.USED -> processUsed(psiElement as ShireUsed)
                ShireTypes.COMMENTS -> {
                    if (psiElement.text.startsWith(FLOW_FALG)) {
                        val fileName = psiElement.text.substringAfter(FLOW_FALG).trim()
                        val content =
                            myProject.guessProjectDir()?.findFileByRelativePath(fileName)?.let { virtualFile ->
                                virtualFile.inputStream.bufferedReader().use { reader -> reader.readText() }
                            }

                        if (content != null) {
                            val shireFile = ShireFile.fromString(myProject, content)
                            result.nextJob = shireFile
                        }
                    }
                }

                ShireTypes.FRONTMATTER_START -> {
                    val nextElement = PsiTreeUtil.findChildOfType(
                        psiElement.parent, ShireFrontMatterHeader::class.java
                    ) ?: continue
                    result.config = HobbitHoleParser.parse(nextElement)
                }

                ShireTypes.FRONT_MATTER_HEADER -> {
                    result.config = HobbitHoleParser.parse(psiElement as ShireFrontMatterHeader)
                }

                WHITE_SPACE, DUMMY_BLOCK -> output.append(psiElement.text)
                ShireTypes.VELOCITY_EXPR -> {
                    processVelocityExpr(psiElement as ShireVelocityExpr)
                    logger.info("Velocity expression found: ${psiElement.text}")
                }

                MARKDOWN_HEADER -> {
                    output.append("#[[${psiElement.text}]]#")
                }

                else -> {
                    output.append(psiElement.text)
                    logger.warn("Unknown element type: ${psiElement.elementType}, text: ${psiElement.text}")
                }
            }
        }

        result.shireOutput = output.toString()
        result.variableTable = variableTable

        CACHED_COMPILE_RESULT[file.name] = result
        return result
    }

    private fun processVelocityExpr(velocityExpr: ShireVelocityExpr) {
        handleNextSiblingForChild(velocityExpr) { next ->
            if (next is ShireIfExpr) {
                handleNextSiblingForChild(next) {
                    when (it) {
                        is ShireIfClause, is ShireElseifClause, is ShireElseClause -> {
                            handleNextSiblingForChild(it, ::processIfClause)
                        }

                        else -> output.append(it.text)
                    }
                }
            } else {
                output.append(next.text)
            }
        }
    }

    private fun processIfClause(clauseContent: PsiElement) {
        when (clauseContent) {
            is ShireExpr -> {
                addVariable(clauseContent)
                if (!result.hasError) output.append(clauseContent.text)
            }

            is ShireVelocityBlock -> {
                ShireFile.fromString(myProject, clauseContent.text).let { file ->
                    ShireSyntaxAnalyzer(myProject, file).parseAndExecuteLocalCommand().let {
                        output.append(it.shireOutput)
                        variableTable.addVariable(it.variableTable)
                        result.hasError = it.hasError
                    }
                }

            }

            else -> {
                output.append(clauseContent.text)
            }
        }
    }

    private fun addVariable(psiElement: PsiElement?) {
        if (psiElement == null) return
        val queue = LinkedList<PsiElement>()
        queue.push(psiElement)
        while (!queue.isEmpty() && !result.hasError) {
            val e = queue.pop()
            if (e.firstChild.elementType == ShireTypes.VARIABLE_START) {
                processVariable(e.firstChild)
            } else {
                e.children.forEach {
                    queue.push(it)
                }
            }
        }
    }

    private fun handleNextSiblingForChild(element: PsiElement?, handle: (PsiElement) -> Unit) {
        var child: PsiElement? = element?.firstChild
        while (child != null && !result.hasError) {
            handle(child)
            child = child.nextSibling
        }
    }

    private fun processUsed(used: ShireUsed) {
        val firstChild = used.firstChild
        val id = firstChild.nextSibling

        when (firstChild.elementType) {
            ShireTypes.COMMAND_START -> {
                val command = BuiltinCommand.fromString(id?.text ?: "")
                if (command == null) {
                    CustomCommand.fromString(myProject, id?.text ?: "")?.let { cmd ->
                        ShireFile.fromString(myProject, cmd.content).let { file ->
                            ShireSyntaxAnalyzer(myProject, file).parseAndExecuteLocalCommand().let {
                                output.append(it.shireOutput)
                                result.hasError = it.hasError
                            }
                        }

                        return
                    }


                    output.append(used.text)
                    logger.warn("Unknown command: ${id?.text}")
                    result.hasError = true
                    return
                }

                if (!command.requireProps) {
                    processingCommand(command, "", used, fallbackText = used.text)
                    return
                }

                val propElement = id.nextSibling?.nextSibling
                val isProp = (propElement.elementType == ShireTypes.COMMAND_PROP)
                if (!isProp) {
                    output.append(used.text)
                    logger.warn("No command prop found: ${used.text}")
                    result.hasError = true
                    return
                }

                processingCommand(command, propElement!!.text, used, fallbackText = used.text)
            }

            ShireTypes.AGENT_START -> {
                val shireAgentId = id as ShireAgentId
                val configs = CustomAgent.loadFromProject(myProject).filter {
                    it.name == shireAgentId.quoteString?.text?.removeSurrounding("\"")?.removeSurrounding("'")
                            || it.name == shireAgentId.identifier?.text
                }

                if (configs.isNotEmpty()) {
                    result.executeAgent = configs.first()
                }
            }

            ShireTypes.VARIABLE_START -> {
                processVariable(firstChild)
                if (!result.hasError) output.append(used.text)
            }

            else -> {
                logger.warn("Unknown [cc.unitmesh.devti.language.psi.ShireUsed] type: ${firstChild.elementType}")
                output.append(used.text)
            }
        }
    }

    private fun processVariable(variableStart: PsiElement) {
        if (variableStart.elementType != ShireTypes.VARIABLE_START) {
            logger.warn("Illegal type: ${variableStart.elementType}")
            return
        }
        val variableId = variableStart.nextSibling?.text

        val currentEditor = editor ?: TemplateCompiler.defaultEditor(myProject)
        val currentElement = element ?: TemplateCompiler.defaultElement(myProject, currentEditor)

        if (currentElement == null) {
            output.append("$SHIRE_ERROR No element found for variable: ${variableStart.text}")
            result.hasError = true
            return
        }

        val lineNo = try {
            runReadAction {
                val containingFile = currentElement.containingFile
                val document: Document? =     PsiDocumentManager.getInstance(variableStart.project).getDocument(containingFile)
                document?.getLineNumber(variableStart.textRange.startOffset) ?: 0
            }
        } catch (e: Exception) {
            0
        }

        variableTable.addVariable(variableId ?: "", VariableTable.VariableType.String, lineNo)
    }

    private fun processingCommand(commandNode: BuiltinCommand, prop: String, used: ShireUsed, fallbackText: String) {
        val command: ShireCommand = when (commandNode) {
            BuiltinCommand.FILE -> {
                FileShireCommand(myProject, prop)
            }

            BuiltinCommand.REV -> {
                RevShireCommand(myProject, prop)
            }

            BuiltinCommand.SYMBOL -> {
                result.isLocalCommand = true
                SymbolShireCommand(myProject, prop)
            }

            BuiltinCommand.WRITE -> {
                result.isLocalCommand = true
                val shireCode: CodeBlockElement? = lookupNextCode(used)
                if (shireCode == null) {
                    PrintShireCommand("/" + commandNode.commandName + ":" + prop)
                } else {
                    WriteShireCommand(myProject, prop, shireCode.codeText(), used)
                }
            }

            BuiltinCommand.PATCH -> {
                result.isLocalCommand = true
                val shireCode: CodeBlockElement? = lookupNextCode(used)
                if (shireCode == null) {
                    PrintShireCommand("/" + commandNode.commandName + ":" + prop)
                } else {
                    PatchShireCommand(myProject, prop, shireCode.codeText())
                }
            }

            BuiltinCommand.COMMIT -> {
                result.isLocalCommand = true
                val shireCode: CodeBlockElement? = lookupNextCode(used)
                if (shireCode == null) {
                    PrintShireCommand("/" + commandNode.commandName + ":" + prop)
                } else {
                    CommitShireCommand(myProject, shireCode.codeText())
                }
            }

            BuiltinCommand.RUN -> {
                result.isLocalCommand = true
                RunShireCommand(myProject, prop)
            }

            BuiltinCommand.FILE_FUNC -> {
                result.isLocalCommand = true
                FileFuncShireCommand(myProject, prop)
            }

            BuiltinCommand.SHELL -> {
                result.isLocalCommand = true
                ShellShireCommand(myProject, prop)
            }

            BuiltinCommand.BROWSE -> {
                result.isLocalCommand = true
                BrowseShireCommand(myProject, prop)
            }

            BuiltinCommand.REFACTOR -> {
                result.isLocalCommand = true
                val nextTextSegment = lookupNextTextSegment(used)
                RefactorShireCommand(myProject, prop, nextTextSegment)
            }

            BuiltinCommand.GOTO -> {
                result.isLocalCommand = true
                GotoShireCommand(myProject, prop, used)
            }

            BuiltinCommand.STRUCTURE -> {
                result.isLocalCommand = true
                StructureShireCommand(myProject, prop)
            }

            BuiltinCommand.DATABASE -> {
                result.isLocalCommand = true
                val shireCode: String? = lookupNextCode(used)?.text
                DatabaseShireCommand(myProject, prop, shireCode)
            }

            BuiltinCommand.DIR -> {
                result.isLocalCommand = true
                DirShireCommand(myProject, prop)
            }

            BuiltinCommand.LOCAL_SEARCH -> {
                result.isLocalCommand = true
                val shireCode: String? = lookupNextCode(used)?.text
                LocalSearchShireCommand(myProject, prop, shireCode)
            }

            BuiltinCommand.RELATED -> {
                result.isLocalCommand = true
                RelatedSymbolInsCommand(myProject, prop)
            }

            BuiltinCommand.OPEN -> {
                result.isLocalCommand = true
                OpenShireCommand(myProject, prop)
            }

            BuiltinCommand.RIPGREP_SEARCH -> {
                result.isLocalCommand = true
                val shireCode: String? = lookupNextCode(used)?.text
                RipgrepSearchShireCommand(myProject, prop, shireCode)
            }

        }

        val execResult = runBlocking {
            command.doExecute()
        }

        val isSucceed = execResult?.contains(SHIRE_ERROR) == false
        val result = if (isSucceed) {
            val hasReadCodeBlock = commandNode in listOf(
                BuiltinCommand.WRITE,
                BuiltinCommand.PATCH,
                BuiltinCommand.COMMIT,
                BuiltinCommand.DATABASE,
            )

            if (hasReadCodeBlock) {
                skipNextCode = true
            }

            execResult
        } else {
            execResult ?: fallbackText
        }

        output.append(result)
    }

    private fun lookupNextCode(used: ShireUsed): CodeBlockElement? {
        val shireCode: CodeBlockElement?
        var next: PsiElement? = used
        while (true) {
            next = next?.nextSibling
            if (next == null) {
                shireCode = null
                break
            }

            if (next.elementType == ShireTypes.CODE) {
                shireCode = next as CodeBlockElement
                break
            }
        }

        return shireCode
    }

    private fun lookupNextTextSegment(used: ShireUsed): String {
        val textSegment: StringBuilder = StringBuilder()
        var next: PsiElement? = used
        while (true) {
            next = next?.nextSibling
            if (next == null) {
                break
            }

            if (next.elementType == ShireTypes.TEXT_SEGMENT) {
                textSegment.append(next.text)
                break
            }
        }

        return textSegment.toString()
    }
}
