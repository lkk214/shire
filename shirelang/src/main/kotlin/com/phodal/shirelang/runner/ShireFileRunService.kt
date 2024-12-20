package com.phodal.shirelang.runner

import com.intellij.execution.RunManager
import com.intellij.execution.RunnerAndConfigurationSettings
import com.intellij.execution.configurations.RunConfiguration
import com.intellij.execution.configurations.RunProfile
import com.intellij.openapi.actionSystem.impl.SimpleDataContext
import com.intellij.openapi.application.runReadAction
import com.intellij.openapi.project.Project
import com.intellij.openapi.vfs.VirtualFile
import com.intellij.psi.PsiElement
import com.intellij.psi.PsiManager
import com.intellij.testFramework.LightVirtualFile
import com.phodal.shirecore.provider.shire.FileRunService
import com.phodal.shirelang.psi.ShireFile
import com.phodal.shirelang.run.ShireConfiguration
import com.phodal.shirelang.run.ShireConfigurationType
import com.phodal.shirelang.run.ShireRunConfigurationProfileState

class ShireFileRunService : FileRunService {
    override fun isApplicable(project: Project, file: VirtualFile): Boolean {
        return PsiManager.getInstance(project).findFile(file) is ShireFile
    }

    override fun runConfigurationClass(project: Project): Class<out RunProfile> = ShireConfiguration::class.java

    override fun createConfiguration(project: Project, virtualFile: VirtualFile): RunConfiguration? {
        val configurationSetting = runReadAction {
            val psiFile =
                PsiManager.getInstance(project).findFile(virtualFile) as? ShireFile ?: return@runReadAction null
            RunManager.getInstance(project)
                .createConfiguration(psiFile.name, ShireConfigurationType.getInstance())
        } ?: return null

        val shireConfiguration = configurationSetting.configuration as ShireConfiguration
        shireConfiguration.name = virtualFile.nameWithoutExtension
        shireConfiguration.setScriptPath(virtualFile.path)

        return shireConfiguration
    }

    override fun createRunSettings(
        project: Project,
        virtualFile: VirtualFile,
        testElement: PsiElement?,
    ): RunnerAndConfigurationSettings? {
        val runManager = RunManager.getInstance(project)

        val psiFile = runReadAction {
            PsiManager.getInstance(project).findFile(virtualFile) as? ShireFile ?: return@runReadAction null
        } ?: return null

        val setting = runReadAction {
            runManager.createConfiguration(psiFile.name, ShireConfigurationType.getInstance())
        }

        val shireConfiguration = setting.configuration as ShireConfiguration
        shireConfiguration.name = virtualFile.nameWithoutExtension
        shireConfiguration.setScriptPath(virtualFile.path)

        runManager.setTemporaryConfiguration(setting)

        setting.isTemporary = true
        runManager.selectedConfiguration = setting

        return setting
    }
}