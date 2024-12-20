package com.phodal.shirecore.ui

import com.intellij.icons.AllIcons
import com.intellij.ide.IdeTooltip
import com.intellij.ide.IdeTooltipManager
import com.intellij.openapi.Disposable
import com.intellij.openapi.actionSystem.*
import com.intellij.openapi.actionSystem.impl.ActionButton
import com.intellij.openapi.editor.event.DocumentEvent
import com.intellij.openapi.editor.event.DocumentListener
import com.intellij.openapi.editor.ex.EditorEx
import com.intellij.openapi.project.DumbAwareAction
import com.intellij.openapi.project.Project
import com.intellij.openapi.ui.ComponentValidator
import com.intellij.openapi.ui.popup.Balloon.Position
import com.intellij.openapi.wm.IdeFocusManager
import com.intellij.openapi.wm.impl.InternalDecorator
import com.intellij.ui.HintHint
import com.intellij.util.EventDispatcher
import com.intellij.util.ui.*
import com.intellij.util.ui.components.BorderLayoutPanel
import com.phodal.shirecore.ShireCoreBundle
import java.awt.*
import java.awt.event.MouseAdapter
import java.awt.event.MouseEvent
import java.util.*
import javax.swing.Box
import javax.swing.JComponent
import javax.swing.JPanel
import kotlin.math.max
import kotlin.math.min

enum class ShireInputTrigger {
    Button,
    Key
}

interface ShireInputListener : EventListener {
    fun editorAdded(editor: EditorEx) {}
    fun onSubmit(component: ShireInputSection, trigger: ShireInputTrigger) {}
    fun onStop(component: ShireInputSection) {}
}

class ShireInputSection(private val project: Project, val disposable: Disposable?) : BorderLayoutPanel() {
    private val input: ShireInputTextField
    private val documentListener: DocumentListener
    private val sendButtonPresentation: Presentation
    private val stopButtonPresentation: Presentation
    private val sendButton: ActionButton
    private val stopButton: ActionButton
    private val buttonPanel = JPanel(CardLayout())

    val editorListeners = EventDispatcher.create(ShireInputListener::class.java)

    var text: String
        get() {
            return input.text
        }
        set(text) {
            input.recreateDocument()
            input.text = text
        }

    init {
        val sendButtonPresentation = Presentation(ShireCoreBundle.message("chat.panel.send"))
        sendButtonPresentation.icon = AllIcons.Actions.Execute
        this.sendButtonPresentation = sendButtonPresentation

        val stopButtonPresentation = Presentation("Stop")
        stopButtonPresentation.icon = AllIcons.Actions.Suspend
        this.stopButtonPresentation = stopButtonPresentation

        sendButton = ActionButton(
            DumbAwareAction.create {
                object : DumbAwareAction("") {
                    override fun actionPerformed(e: AnActionEvent) {
                        editorListeners.multicaster.onSubmit(this@ShireInputSection, ShireInputTrigger.Button)
                    }
                }.actionPerformed(it)
            },
            this.sendButtonPresentation, "", Dimension(20, 20)
        )

        stopButton = ActionButton(
            DumbAwareAction.create {
                object : DumbAwareAction("") {
                    override fun actionPerformed(e: AnActionEvent) {
                        editorListeners.multicaster.onStop(this@ShireInputSection)
                    }
                }.actionPerformed(it)
            },
            this.stopButtonPresentation, "", Dimension(20, 20)
        )

        input = ShireInputTextField(project, listOf(), disposable, this)

        documentListener = object : DocumentListener {
            override fun documentChanged(event: DocumentEvent) {
                val i = input.preferredSize?.height
                if (i != input.height) {
                    revalidate()
                }
            }
        }

        input.addDocumentListener(documentListener)
        input.recreateDocument()

        input.border = JBEmptyBorder(4)

        addToCenter(input)
        val layoutPanel = BorderLayoutPanel()
        val horizontalGlue = Box.createHorizontalGlue()
        horizontalGlue.addMouseListener(object : MouseAdapter() {
            override fun mouseClicked(e: MouseEvent?) {
                IdeFocusManager.getInstance(project).requestFocus(input, true)
                input.caretModel.moveToOffset(input.text.length - 1)
            }
        })
        layoutPanel.setOpaque(false)

        buttonPanel.add(sendButton, "Send")
        buttonPanel.add(stopButton, "Stop")

        layoutPanel.addToCenter(horizontalGlue)
        layoutPanel.addToRight(buttonPanel)
        addToBottom(layoutPanel)

        ComponentValidator(disposable!!).installOn((this)).revalidate()

        addListener(object : ShireInputListener {
            override fun editorAdded(editor: EditorEx) {
                this@ShireInputSection.initEditor()
            }
        })
    }

    fun showStopButton() {
        (buttonPanel.layout as? CardLayout)?.show(buttonPanel, "Stop")
        stopButton.isEnabled = true
    }

    fun showTooltip(text: String) {
        showTooltip(input, Position.above, text)
    }

    fun showTooltip(component: JComponent, position: Position, text: String) {
        val point = Point(component.x, component.y)
        val tipComponent = IdeTooltipManager.initPane(
            text, HintHint(component, point).setAwtTooltip(true).setPreferredPosition(position), null
        )
        val tooltip = IdeTooltip(component, point, tipComponent)
        IdeTooltipManager.getInstance().show(tooltip, true)
    }

    fun showSendButton() {
        (buttonPanel.layout as? CardLayout)?.show(buttonPanel, "Send")
        buttonPanel.isEnabled = true
    }

    fun initEditor() {
        val editorEx = this.input.editor as? EditorEx ?: return
        setBorder(ShireCoolBorder(editorEx, this))
        UIUtil.setOpaqueRecursively(this, false)
        this.revalidate()
    }

    override fun getPreferredSize(): Dimension {
        val result = super.getPreferredSize()
        result.height = max(min(result.height, maxHeight), minimumSize.height)
        return result
    }

    /**
     * Set the content of the input field.
     */
    fun setContent(trimMargin: String) {
        val focusManager = IdeFocusManager.getInstance(project)
        focusManager.requestFocus(input, true)
        this.input.recreateDocument()
        this.input.text = trimMargin
    }

    override fun getBackground(): Color? {
        // it seems that the input field is not ready when this method is called
        if (this.input == null) return super.getBackground()

        val editor = input.editor ?: return super.getBackground()
        return editor.colorsScheme.defaultBackground
    }

    override fun setBackground(bg: Color?) {}

    fun addListener(listener: ShireInputListener) {
        editorListeners.addListener(listener)
    }

    fun moveCursorToStart() {
        input.caretModel.moveToOffset(0)
    }

    private val maxHeight: Int
        get() {
            val decorator = UIUtil.getParentOfType(InternalDecorator::class.java, this)
            val contentManager = decorator?.contentManager ?: return JBUI.scale(200)
            return contentManager.component.height / 2
        }

    val focusableComponent: JComponent get() = input
}

