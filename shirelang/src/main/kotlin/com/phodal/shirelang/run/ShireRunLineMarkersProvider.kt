package com.phodal.shirelang.run

import com.phodal.shirelang.actions.ShireRunFileAction
import com.intellij.execution.lineMarker.RunLineMarkerContributor
import com.intellij.icons.AllIcons
import com.intellij.openapi.actionSystem.ActionManager
import com.intellij.openapi.actionSystem.AnAction
import com.intellij.openapi.project.DumbAware
import com.intellij.openapi.project.DumbService
import com.intellij.psi.PsiElement
import com.phodal.shirelang.ShireBundle
import com.phodal.shirelang.ShireLanguage
import com.phodal.shirelang.compiler.ast.hobbit.HobbitHole
import com.phodal.shirelang.psi.ShireFile

class ShireRunLineMarkersProvider : RunLineMarkerContributor(), DumbAware {
    override fun getInfo(element: PsiElement): Info? {
        if (DumbService.isDumb(element.project)) return null
        if (element.language !is ShireLanguage) return null
        val psiFile = element as? ShireFile ?: return null

        val actions = arrayOf<AnAction>(ActionManager.getInstance().getAction(ShireRunFileAction.ID))

        val displayName = HobbitHole.from(psiFile)?.name ?: psiFile.name

        return Info(
            AllIcons.RunConfigurations.TestState.Run,
            { ShireBundle.message("shire.line.marker.run.0", displayName) },
            *actions
        )
    }
}
