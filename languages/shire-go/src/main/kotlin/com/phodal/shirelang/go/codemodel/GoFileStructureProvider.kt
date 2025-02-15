package com.phodal.shirelang.go.codemodel

import com.goide.psi.GoFile
import com.goide.psi.GoFunctionOrMethodDeclaration
import com.goide.psi.GoType
import com.intellij.psi.PsiFile
import com.intellij.psi.util.PsiTreeUtil
import com.phodal.shirecore.provider.codemodel.FileStructureProvider
import com.phodal.shirecore.provider.codemodel.model.FileStructure
import com.phodal.shirecore.relativePath

class GoFileStructureProvider : FileStructureProvider {
    override fun build(psiFile: PsiFile): FileStructure? {
        if (psiFile !is GoFile) return null

        val packageString = psiFile.packageName
        val path = psiFile.virtualFile.relativePath(psiFile.project)
        val imports = psiFile.imports
        val classes = PsiTreeUtil.getChildrenOfTypeAsList(psiFile, GoType::class.java)
        val methods = PsiTreeUtil.getChildrenOfTypeAsList(psiFile, GoFunctionOrMethodDeclaration::class.java)

        return FileStructure(psiFile, psiFile.name, path, packageString, imports, classes, methods)
    }
}
