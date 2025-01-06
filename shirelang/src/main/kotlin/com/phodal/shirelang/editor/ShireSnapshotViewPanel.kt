package com.phodal.shirelang.editor

import com.intellij.ui.JBColor
import com.intellij.ui.components.JBLabel
import com.intellij.ui.components.JBPanel
import com.intellij.ui.components.JBScrollPane
import com.intellij.ui.table.JBTable
import com.intellij.util.ui.JBUI
import com.phodal.shirelang.debugger.UserCustomVariableSnapshot
import java.awt.BorderLayout
import javax.swing.JPanel
import javax.swing.ScrollPaneConstants
import javax.swing.table.DefaultTableModel

class ShireSnapshotViewPanel : JPanel(BorderLayout()) {
    private val contentPanel = JBPanel<JBPanel<*>>(BorderLayout())
    private val tableModel = DefaultTableModel(arrayOf("Variable", "Operation", "Value", "Timestamp"), 0)

    init {
        val table = JBTable(tableModel).apply {
            tableHeader.reorderingAllowed = true
            tableHeader.resizingAllowed = true
            setShowGrid(true)
            gridColor = JBColor.PanelBackground
            intercellSpacing = JBUI.size(0, 0)

            val columnModel = columnModel
            columnModel.getColumn(0).preferredWidth = 150
            columnModel.getColumn(1).preferredWidth = 80
            columnModel.getColumn(2).preferredWidth = 300
            columnModel.getColumn(3).preferredWidth = 80

            autoResizeMode = JBTable.AUTO_RESIZE_LAST_COLUMN
        }

        val scrollPane = JBScrollPane(
            table,
            ScrollPaneConstants.VERTICAL_SCROLLBAR_AS_NEEDED,
            ScrollPaneConstants.HORIZONTAL_SCROLLBAR_AS_NEEDED
        ).apply {
            minimumSize = JBUI.size(0, 160)
            preferredSize = JBUI.size(0, 160)
        }

        setupPanel()
        add(scrollPane, BorderLayout.CENTER)
    }

    private fun setupPanel() {
        contentPanel.background = JBColor(0xF5F5F5, 0x2B2D30)

        val titleLabel = JBLabel("Custom Variable Snapshots").apply {
            font = JBUI.Fonts.label(14f).asBold()
            border = JBUI.Borders.empty(4, 8)
        }

        contentPanel.add(titleLabel, BorderLayout.NORTH)
        add(contentPanel, BorderLayout.NORTH)
    }

    fun updateSnapshots(snapshots: List<UserCustomVariableSnapshot>) {
        /// if size is 0, hide the panel
        if (snapshots.isEmpty()) {
            isVisible = false
            return
        }

        isVisible = true

        tableModel.rowCount = 0
        /// remove all rows
        tableModel.dataVector.removeAllElements()

        snapshots.forEach { snapshot ->
            tableModel.addRow(
                arrayOf(
                    snapshot.variableName,
                    snapshot.operations.firstOrNull()?.functionName ?: "",
                    snapshot.value.toString(),
                    snapshot.operations.firstOrNull()?.timestamp ?: ""
                )
            )
        }

        revalidate()
        repaint()
    }
}
