package io.snyk.plugin.services

import com.intellij.openapi.application.ApplicationManager
import com.intellij.openapi.components.Service
import com.intellij.openapi.diagnostic.logger
import com.intellij.openapi.fileEditor.FileDocumentManager
import com.intellij.openapi.progress.BackgroundTaskQueue
import com.intellij.openapi.progress.ProgressIndicator
import com.intellij.openapi.progress.Task
import com.intellij.openapi.project.Project
import io.snyk.plugin.cancelOssIndicator
import io.snyk.plugin.events.SnykCliDownloadListener
import io.snyk.plugin.events.SnykScanListener
import io.snyk.plugin.events.SnykSettingsListener
import io.snyk.plugin.events.SnykTaskQueueListener
import io.snyk.plugin.getContainerService
import io.snyk.plugin.getIacService
import io.snyk.plugin.getSnykCachedResults
import io.snyk.plugin.getSnykCliDownloaderService
import io.snyk.plugin.getSnykToolWindowPanel
import io.snyk.plugin.getSyncPublisher
import io.snyk.plugin.isCliDownloading
import io.snyk.plugin.isCliInstalled
import io.snyk.plugin.isOssRunning
import io.snyk.plugin.isSnykCodeRunning
import io.snyk.plugin.isSnykIaCLSEnabled
import io.snyk.plugin.pluginSettings
import io.snyk.plugin.refreshAnnotationsForOpenFiles
import io.snyk.plugin.ui.SnykBalloonNotificationHelper
import org.jetbrains.annotations.TestOnly
import snyk.common.SnykError
import snyk.common.lsp.LanguageServerWrapper
import snyk.trust.confirmScanningAndSetWorkspaceTrustedStateIfNeeded

@Service(Service.Level.PROJECT)
class SnykTaskQueueService(val project: Project) {
    private val logger = logger<SnykTaskQueueService>()
    private val taskQueue = BackgroundTaskQueue(project, "Snyk")
    private val taskQueueIac = BackgroundTaskQueue(project, "Snyk: Iac")
    private val taskQueueContainer = BackgroundTaskQueue(project, "Snyk: Container")

    private val settings
        get() = pluginSettings()

    private val scanPublisher
        get() = getSyncPublisher(project, SnykScanListener.SNYK_SCAN_TOPIC)

    private val cliDownloadPublisher
        get() = ApplicationManager.getApplication().messageBus.syncPublisher(SnykCliDownloadListener.CLI_DOWNLOAD_TOPIC)

    private val taskQueuePublisher
        get() = getSyncPublisher(project, SnykTaskQueueListener.TASK_QUEUE_TOPIC)

    var ossScanProgressIndicator: ProgressIndicator? = null
        private set

    var iacScanProgressIndicator: ProgressIndicator? = null
        private set

    var containerScanProgressIndicator: ProgressIndicator? = null
        private set

    @TestOnly
    fun getTaskQueue() = taskQueue

    fun connectProjectToLanguageServer(project: Project) {
        // subscribe to the settings changed topic
        val languageServerWrapper = LanguageServerWrapper.getInstance()
        getSnykToolWindowPanel(project)?.let {
            project.messageBus.connect(it)
                .subscribe(
                    SnykSettingsListener.SNYK_SETTINGS_TOPIC,
                    object : SnykSettingsListener {
                        override fun settingsChanged() {
                            languageServerWrapper.updateConfiguration()
                        }
                    }
                )
        }
        // Try to connect project for up to 30s
        for (tries in 1..300) {
            if (!languageServerWrapper.isInitialized) {
                Thread.sleep(100)
                continue
            }

            languageServerWrapper.addContentRoots(project)
            break
        }
    }

    fun scan(isStartup: Boolean) {
        taskQueue.run(object : Task.Backgroundable(project, "Snyk: initializing...", true) {
            override fun run(indicator: ProgressIndicator) {
                if (!confirmScanningAndSetWorkspaceTrustedStateIfNeeded(project)) return

                ApplicationManager.getApplication().invokeAndWait {
                    FileDocumentManager.getInstance().saveAllDocuments()
                }
                indicator.checkCanceled()
                waitUntilCliDownloadedIfNeeded()
                indicator.checkCanceled()

                if (!isStartup) {
                    LanguageServerWrapper.getInstance().sendScanCommand(project)
                }

                if (settings.iacScanEnabled) {
                    if (!isSnykIaCLSEnabled()) {
                        scheduleIacScan()
                    }
                }
                if (settings.containerScanEnabled) {
                    scheduleContainerScan()
                }
            }
        })
    }

    fun waitUntilCliDownloadedIfNeeded() {
        downloadLatestRelease()
        do {
            Thread.sleep(WAIT_FOR_DOWNLOAD_MILLIS)
        } while (isCliDownloading())
    }

    private fun scheduleContainerScan() {
        taskQueueContainer.run(object : Task.Backgroundable(project, "Snyk Container is scanning...", true) {
            override fun run(indicator: ProgressIndicator) {
                if (!isCliInstalled()) return
                val snykCachedResults = getSnykCachedResults(project) ?: return
                if (snykCachedResults.currentContainerResult?.rescanNeeded == false) return
                logger.debug("Starting Container scan")
                containerScanProgressIndicator = indicator
                scanPublisher?.scanningStarted()

                snykCachedResults.currentContainerResult = null
                val containerResult = try {
                    getContainerService(project)?.scan()
                } finally {
                    containerScanProgressIndicator = null
                }
                if (containerResult == null || project.isDisposed) return

                if (indicator.isCanceled) {
                    logger.debug("cancel container scan")
                    taskQueuePublisher?.stopped(wasContainerRunning = true)
                } else {
                    if (containerResult.isSuccessful()) {
                        logger.debug("Container result: ->")
                        containerResult.allCliIssues?.forEach {
                            logger.debug("  ${it.imageName}, ${it.vulnerabilities.size} issues")
                        }
                        scanPublisher?.scanningContainerFinished(containerResult)
                    } else {
                        scanPublisher?.scanningContainerError(containerResult.getFirstError()!!)
                    }
                }
                logger.debug("Container scan completed")
                refreshAnnotationsForOpenFiles(project)
            }
        })
    }

    private fun scheduleIacScan() {
        taskQueueIac.run(object : Task.Backgroundable(project, "Snyk Infrastructure as Code is scanning", true) {
            override fun run(indicator: ProgressIndicator) {
                if (!isCliInstalled()) return
                val snykCachedResults = getSnykCachedResults(project) ?: return
                if (snykCachedResults.currentIacResult?.iacScanNeeded == false) return
                logger.debug("Starting IaC scan")
                iacScanProgressIndicator = indicator
                scanPublisher?.scanningStarted()

                snykCachedResults.currentIacResult = null
                val iacResult = try {
                    getIacService(project)?.scan()
                } finally {
                    iacScanProgressIndicator = null
                }
                if (iacResult == null || project.isDisposed) return

                if (indicator.isCanceled) {
                    logger.debug("cancel IaC scan")
                    taskQueuePublisher?.stopped(wasIacRunning = true)
                } else {
                    if (iacResult.isSuccessful()) {
                        logger.debug("IaC result: ->")
                        iacResult.allCliIssues?.forEach {
                            logger.debug("  ${it.targetFile}, ${it.infrastructureAsCodeIssues.size} issues")
                        }
                        scanPublisher?.scanningIacFinished(iacResult)
                    } else {
                        val error = iacResult.getFirstError()
                        if (error == null) {
                            SnykError("unknown IaC error", project.basePath ?: "")
                        } else {
                            scanPublisher?.scanningIacError(error)
                        }
                    }
                }
                logger.debug("IaC scan completed")
                refreshAnnotationsForOpenFiles(project)
            }
        })
    }

    fun downloadLatestRelease(force: Boolean = false) {
        // abort even before submitting a task
        if (project.isDisposed || ApplicationManager.getApplication().isDisposed) return
        val cliDownloader = getSnykCliDownloaderService()
        if (!pluginSettings().manageBinariesAutomatically) {
            if (!isCliInstalled()) {
                val msg =
                    "The plugin cannot scan without Snyk CLI, but automatic download is disabled. " +
                        "Please put a Snyk CLI executable in ${pluginSettings().cliPath} and retry."
                SnykBalloonNotificationHelper.showError(msg, project)
                // no need to cancel the indicator here, as isCLIDownloading() will return false
            }
            // no need to cancel the indicator here, as isCliInstalled() will return false
            cliDownloader.stopCliDownload()
            return
        }

        taskQueue.run(object : Task.Backgroundable(project, "Check Snyk CLI presence", true) {
            override fun run(indicator: ProgressIndicator) {
                cliDownloadPublisher.checkCliExistsStarted()
                if (project.isDisposed) return

                if (!isCliInstalled()) {
                    cliDownloader.downloadLatestRelease(indicator, project)
                } else {
                    cliDownloader.cliSilentAutoUpdate(indicator, project, force)
                }
                cliDownloadPublisher.checkCliExistsFinished()
            }
        })
    }

    fun stopScan() {
        val wasOssRunning = isOssRunning(project)
        cancelOssIndicator(project)

        val wasSnykCodeRunning = isSnykCodeRunning(project)

        val wasIacRunning = iacScanProgressIndicator?.isRunning == true
        iacScanProgressIndicator?.cancel()

        val wasContainerRunning = containerScanProgressIndicator?.isRunning == true
        containerScanProgressIndicator?.cancel()

        taskQueuePublisher?.stopped(wasOssRunning, wasSnykCodeRunning, wasIacRunning, wasContainerRunning)
    }

    companion object {
        private const val WAIT_FOR_DOWNLOAD_MILLIS = 1000L
    }
}
