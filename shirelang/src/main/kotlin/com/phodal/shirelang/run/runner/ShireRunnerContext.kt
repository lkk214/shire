package com.phodal.shirelang.run.runner

import com.intellij.openapi.editor.Editor
import com.phodal.shirelang.compiler.parser.ShireParsedResult
import com.phodal.shirelang.compiler.ast.hobbit.HobbitHole

class ShireRunnerContext(
    val hole: HobbitHole?,
    val editor: Editor?,
    val compileResult: ShireParsedResult,
    val finalPrompt: String = "",
    val hasError: Boolean,
    val compiledVariables: Map<String, Any>,
)
