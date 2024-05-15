package io.snyk.plugin.ui.toolwindow.panels

import com.intellij.uiDesigner.core.GridLayoutManager
import com.intellij.util.ui.JBUI
import io.snyk.plugin.pluginSettings
import io.snyk.plugin.SnykFile
import io.snyk.plugin.ui.DescriptionHeaderPanel
import io.snyk.plugin.ui.SnykBalloonNotificationHelper
import io.snyk.plugin.ui.baseGridConstraintsAnchorWest
import io.snyk.plugin.ui.descriptionHeaderPanel
import io.snyk.plugin.ui.jcef.JCEFUtils
import io.snyk.plugin.ui.jcef.OpenFileLoadHandlerGenerator
import io.snyk.plugin.ui.panelGridConstraints
import io.snyk.plugin.ui.toolwindow.SnykToolWindowPanel
import io.snyk.plugin.ui.wrapWithScrollPane
import snyk.common.ProductType
import snyk.common.lsp.ScanIssue
import java.awt.BorderLayout
import java.awt.Font
import javax.swing.JLabel
import javax.swing.JPanel

class SuggestionDescriptionPanelFromLS(
    snykFile: SnykFile,
    private val issue: ScanIssue
) : IssueDescriptionPanelBase(
    title = issue.title(),
    severity = issue.getSeverityAsEnum()
) {
    val project = snykFile.project
    private val unexpectedErrorMessage =
        "Snyk encountered an issue while rendering the vulnerability description. Please try again, or contact support if the problem persists. We apologize for any inconvenience caused."

    init {
        if (
            pluginSettings().isGlobalIgnoresFeatureEnabled &&
            issue.canLoadSuggestionPanelFromHTML()
        ) {
            val openFileLoadHandlerGenerator = OpenFileLoadHandlerGenerator(snykFile)
            val jbCefBrowserComponent = JCEFUtils.getJBCefBrowserComponentIfSupported(issue.details()) {
                openFileLoadHandlerGenerator.generate(it)
            }
            if (jbCefBrowserComponent == null) {
                val statePanel = StatePanel(SnykToolWindowPanel.SELECT_ISSUE_TEXT)
                this.add(wrapWithScrollPane(statePanel), BorderLayout.CENTER)
                SnykBalloonNotificationHelper.showError(unexpectedErrorMessage, null)
            } else {
                val lastRowToAddSpacer = 5
                val panel = JPanel(
                    GridLayoutManager(lastRowToAddSpacer + 1, 1, JBUI.insets(0, 10, 20, 10), -1, 20)
                ).apply {
                    this.add(
                        jbCefBrowserComponent,
                        panelGridConstraints(1)
                    )
                }
                this.add(
                    wrapWithScrollPane(panel),
                    BorderLayout.CENTER
                )
                this.add(panel)
            }
        } else {
            createUI()
        }
    }

    override fun secondRowTitlePanel(): DescriptionHeaderPanel = descriptionHeaderPanel(
        issueNaming = issue.issueNaming(),
        cwes = issue.cwes(),
        cvssScore = issue.cvssScore(),
        cvssV3 = issue.cvssV3(),
        cves = issue.cves(),
        id = issue.id(),
    )

    override fun createMainBodyPanel(): Pair<JPanel, Int> {
        val lastRowToAddSpacer = 5
        val panel = JPanel(
            GridLayoutManager(lastRowToAddSpacer + 1, 1, JBUI.insets(0, 10, 20, 10), -1, 20)
        ).apply {
            if (issue.additionalData.getProductType() == ProductType.CODE_SECURITY || issue.additionalData.getProductType() == ProductType.CODE_QUALITY) {
                this.add(
                    SnykCodeOverviewPanel(issue.additionalData),
                    panelGridConstraints(2)
                )
                this.add(
                    SnykCodeDataflowPanel(project, issue.additionalData),
                    panelGridConstraints(3)
                )
                this.add(
                    SnykCodeExampleFixesPanel(issue.additionalData),
                    panelGridConstraints(4)
                )
            } else if (issue.additionalData.getProductType() == ProductType.OSS) {
                this.add(
                    SnykOSSIntroducedThroughPanel(issue.additionalData),
                    baseGridConstraintsAnchorWest(1, indent = 0)
                )
                this.add(
                   SnykOSSDetailedPathsPanel(issue.additionalData),
                    panelGridConstraints(2)
                )
                this.add(
                    SnykOSSOverviewPanel(issue.additionalData),
                    panelGridConstraints(3)
                )
            } else {
                TODO()
            }
        }
        return Pair(panel, lastRowToAddSpacer)
    }
}

fun defaultFontLabel(labelText: String, bold: Boolean = false): JLabel {
    return JLabel().apply {
        val titleLabelFont: Font? = io.snyk.plugin.ui.getFont(if (bold) Font.BOLD else -1, 14, font)
        titleLabelFont?.let { font = it }
        text = labelText
    }
}
