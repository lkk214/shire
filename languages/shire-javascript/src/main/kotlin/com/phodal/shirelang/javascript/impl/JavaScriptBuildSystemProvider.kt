package com.phodal.shirelang.javascript.impl

import com.intellij.lang.javascript.buildTools.npm.NpmScriptsUtil
import com.intellij.lang.javascript.buildTools.npm.PackageJsonUtil
import com.intellij.openapi.application.runReadAction
import com.intellij.openapi.project.Project
import com.intellij.openapi.project.guessProjectDir
import com.phodal.shirecore.provider.context.BuildSystemProvider
import com.phodal.shirecore.variable.toolchain.buildsystem.BuildSystemContext
import com.phodal.shirelang.javascript.util.JsDependenciesSnapshot
import com.phodal.shirelang.javascript.variable.JsWebFrameworks

open class JavaScriptBuildSystemProvider : BuildSystemProvider() {
    override fun collect(project: Project): BuildSystemContext? {
        val snapshot = JsDependenciesSnapshot.create(project, null)
        if (snapshot.packageJsonFiles.isEmpty()) {
            return null
        }

        var language = "JavaScript"
        var languageVersion = "ES5"
        val buildTool = "NPM"

        val packageJson = snapshot.packages["typescript"]
        val tsVersion = packageJson?.parseVersion()
        if (tsVersion != null) {
            language = "TypeScript"
            languageVersion = tsVersion.rawVersion
        }

        JsWebFrameworks.entries.forEach { framework ->
            if (snapshot.packages[framework.packageName] != null) {
                language += " with ${framework.presentation}"
            }
        }

        var taskString = ""
        runReadAction {
            val root = PackageJsonUtil.findChildPackageJsonFile(project.guessProjectDir()) ?: return@runReadAction
            NpmScriptsUtil.listTasks(project, root).scripts.forEach { task ->
                taskString += task.name + " "
            }
        }

        return BuildSystemContext(
            buildToolName = buildTool,
            buildToolVersion = "",
            languageName = language,
            languageVersion = languageVersion,
            taskString = taskString,
            libraries = snapshot.mostPopularFrameworks()
        )
    }
}
