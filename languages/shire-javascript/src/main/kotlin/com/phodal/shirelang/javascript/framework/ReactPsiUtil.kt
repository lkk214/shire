package com.phodal.shirelang.javascript.framework

import com.intellij.lang.ecmascript6.psi.ES6ExportDeclaration
import com.intellij.lang.ecmascript6.psi.ES6ExportDefaultAssignment
import com.intellij.lang.javascript.presentable.JSFormatUtil
import com.intellij.lang.javascript.psi.JSFile
import com.intellij.lang.javascript.psi.JSFunctionExpression
import com.intellij.lang.javascript.psi.JSReferenceExpression
import com.intellij.lang.javascript.psi.JSVariable
import com.intellij.lang.javascript.psi.ecma6.TypeScriptClass
import com.intellij.lang.javascript.psi.ecma6.TypeScriptFunction
import com.intellij.lang.javascript.psi.ecma6.TypeScriptSingleType
import com.intellij.lang.javascript.psi.ecma6.TypeScriptVariable
import com.intellij.lang.javascript.psi.resolve.JSResolveResult
import com.intellij.psi.PsiNameIdentifierOwner
import com.intellij.psi.util.PsiTreeUtil
import com.phodal.shirecore.variable.frontend.Component

object ReactPsiUtil {
    private fun getExportElements(file: JSFile): List<PsiNameIdentifierOwner> {
        val exportDeclarations =
            PsiTreeUtil.getChildrenOfTypeAsList(file, ES6ExportDeclaration::class.java)

        val map = exportDeclarations.map { exportDeclaration ->
            exportDeclaration.exportSpecifiers
                .asSequence()
                .mapNotNull {
                    it.resolve()?.originalElement ?: it.alias?.findAliasedElement()
                }
                .filterIsInstance<PsiNameIdentifierOwner>()
                .toList()
        }.flatten()

        val defaultAssignments = PsiTreeUtil.getChildrenOfTypeAsList(file, ES6ExportDefaultAssignment::class.java)
        val defaultAssignment = defaultAssignments.mapNotNull {
            val jsReferenceExpression = it.expression as? JSReferenceExpression ?: return@mapNotNull null
            val resolveReference = JSResolveResult.resolveReference(jsReferenceExpression)
            resolveReference.firstOrNull() as? PsiNameIdentifierOwner
        }

        return map + defaultAssignment
    }

//    fun tsxComponentToComponent(jsFile: JSFile): List<Component> = getExportElements(jsFile).map { psiElement ->
//        val name = psiElement.name ?: return@map null
//
//        val projectPath = jsFile.project.basePath ?: ""
//        val path = jsFile.virtualFile.path.removePrefix(projectPath)
//            .replace("\\", "/")
//            .removePrefix("/")
//
//        return@map when (psiElement) {
//            is TypeScriptFunction -> Component(name = name, path)
//            is TypeScriptClass -> Component(name = name, path)
//            is TypeScriptVariable, is JSVariable -> {
//                val funcExpr = PsiTreeUtil.findChildrenOfType(psiElement, JSFunctionExpression::class.java)
//                    .firstOrNull() ?: return@map null
//
//                val signature = JSFormatUtil.buildFunctionSignaturePresentation(funcExpr)
//                val props: List<String> = funcExpr.parameterList?.parameters?.mapNotNull { parameter ->
//                    val typeElement = parameter.typeElement ?: return@mapNotNull null
//                    when (typeElement) {
//                        is TypeScriptSingleType -> {
//                            val resolve = typeElement.referenceExpression?.resolve()
//                            resolve?.text
//                        }
//
//                        else -> null
//                    }
//                } ?: emptyList()
//
//                Component(name = name, path, props = props, signature = signature)
//            }
//
//            else -> null
//        }
//    }.filterNotNull()
}