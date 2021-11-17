package io.snyk.plugin.ui.toolwindow

import UIComponentFinder
import com.intellij.openapi.actionSystem.ActionManager
import com.intellij.openapi.components.service
import com.intellij.openapi.util.registry.Registry
import com.intellij.testFramework.HeavyPlatformTestCase
import com.intellij.testFramework.PlatformTestUtil
import com.intellij.testFramework.TestActionEvent
import com.intellij.util.ui.tree.TreeUtil
import io.mockk.every
import io.mockk.mockk
import io.mockk.mockkStatic
import io.mockk.unmockkAll
import io.mockk.unmockkStatic
import io.snyk.plugin.Severity
import io.snyk.plugin.cli.ConsoleCommandRunner
import io.snyk.plugin.getCliFile
import io.snyk.plugin.getIacService
import io.snyk.plugin.pluginSettings
import io.snyk.plugin.removeDummyCliFile
import io.snyk.plugin.resetSettings
import io.snyk.plugin.services.SnykTaskQueueService
import io.snyk.plugin.setupDummyCliFile
import io.snyk.plugin.ui.actions.SnykTreeMediumSeverityFilterAction
import org.junit.Test
import snyk.common.SnykError
import snyk.iac.IacIssue
import snyk.iac.IacResult
import snyk.iac.ui.toolwindow.IacIssueTreeNode
import javax.swing.JTextArea
import javax.swing.tree.TreeNode

class SnykToolWindowPanelIntegTest : HeavyPlatformTestCase() {

    private val iacGoofJson = getResourceAsString("iac-test-results/infrastructure-as-code-goof.json")

    private fun getResourceAsString(resourceName: String): String = javaClass.classLoader
        .getResource(resourceName)!!.readText(Charsets.UTF_8)

    override fun setUp() {
        super.setUp()
        unmockkAll()
        resetSettings(project)
        setupDummyCliFile()
        // restore modified Registry value
        isIacEnabledRegistryValue.setValue(isIacEnabledDefaultValue)
        unmockkStatic("io.snyk.plugin.UtilsKt")
    }

    override fun tearDown() {
        unmockkAll()
        resetSettings(project)
        removeDummyCliFile()
        // restore modified Registry value
        isIacEnabledRegistryValue.setValue(isIacEnabledDefaultValue)
        unmockkStatic("io.snyk.plugin.UtilsKt")
        super.tearDown()
    }

    private val isIacEnabledRegistryValue = Registry.get("snyk.preview.iac.enabled")
    private val isIacEnabledDefaultValue: Boolean by lazy { isIacEnabledRegistryValue.asBoolean() }

    private fun setUpIacTest() {
        val settings = pluginSettings()
        settings.ossScanEnable = false
        settings.snykCodeSecurityIssuesScanEnable = false
        settings.snykCodeQualityIssuesScanEnable = false
        settings.iacScanEnabled = true

        isIacEnabledRegistryValue.setValue(true)
    }

    @Test
    fun testSeverityFilterForIacResult() {
        // pre-test setup
        setUpIacTest()

        // mock IaC results
        val mockRunner = mockk<ConsoleCommandRunner>()
        every {
            mockRunner.execute(
                listOf(getCliFile().absolutePath, "iac", "test", "--json"),
                project.basePath!!,
                project = project
            )
        } returns (iacGoofJson)

        getIacService(project).setConsoleCommandRunner(mockRunner)

        // actual test run
        val toolWindowPanel = project.service<SnykToolWindowPanel>()

        project.service<SnykTaskQueueService>().scan()

        PlatformTestUtil.waitWhileBusy(toolWindowPanel.getTree())

        val rootIacIssuesTreeNode = toolWindowPanel.getRootIacIssuesTreeNode()
        fun isMediumSeverityShown(): Boolean = rootIacIssuesTreeNode.children().asSequence()
            .flatMap { (it as TreeNode).children().asSequence() }
            .any {
                it is IacIssueTreeNode &&
                    it.userObject is IacIssue &&
                    (it.userObject as IacIssue).severity == Severity.MEDIUM
            }

        assertTrue("Medium severity IaC results should be shown by default", isMediumSeverityShown())

        val mediumSeverityFilterAction =
            ActionManager.getInstance().getAction("io.snyk.plugin.ui.actions.SnykTreeMediumSeverityFilterAction")
                as SnykTreeMediumSeverityFilterAction
        mediumSeverityFilterAction.setSelected(TestActionEvent(), false)

        PlatformTestUtil.waitWhileBusy(toolWindowPanel.getTree())

        assertFalse("Medium severity IaC results should NOT be shown after filtering", isMediumSeverityShown())
    }

    @Test
    fun testIacErrorShown() {
        // pre-test setup
        setUpIacTest()

        // mock IaC results
        val iacError = SnykError("fake error", "fake path")
        val iacResultWithError = IacResult(null, iacError)

        mockkStatic("io.snyk.plugin.UtilsKt")
        every { getIacService(project).isCliInstalled() } returns true
        every { getIacService(project).scan() } returns iacResultWithError

        // actual test run
        project.service<SnykTaskQueueService>().scan()

        val toolWindowPanel = project.service<SnykToolWindowPanel>()
        PlatformTestUtil.waitWhileBusy(toolWindowPanel.getTree())

        assertEquals(iacError, toolWindowPanel.currentIacError)

        TreeUtil.selectNode(toolWindowPanel.getTree(), toolWindowPanel.getRootIacIssuesTreeNode())
        PlatformTestUtil.waitWhileBusy(toolWindowPanel.getTree())

        val descriptionComponents = toolWindowPanel.getDescriptionPanel().components.toList()
        val errorPanel = descriptionComponents.find { it is SnykErrorPanel } as SnykErrorPanel?

        assertNotNull(errorPanel)

        val errorMessageTextArea = UIComponentFinder.getComponentByName(errorPanel!!, JTextArea::class, "errorMessageTextArea")
        val pathTextArea = UIComponentFinder.getComponentByName(errorPanel!!, JTextArea::class, "pathTextArea")

        assertTrue(errorMessageTextArea?.text == iacError.message)
        assertTrue(pathTextArea?.text == iacError.path)
    }
}
