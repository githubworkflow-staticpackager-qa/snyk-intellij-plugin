package snyk.common.lsp

import com.github.benmanes.caffeine.cache.Cache
import com.github.benmanes.caffeine.cache.Caffeine
import com.github.benmanes.caffeine.cache.RemovalListener
import com.google.gson.Gson
import com.intellij.ide.impl.ProjectUtil
import com.intellij.openapi.Disposable
import com.intellij.openapi.actionSystem.AnAction
import com.intellij.openapi.actionSystem.AnActionEvent
import com.intellij.openapi.application.ApplicationManager
import com.intellij.openapi.application.ReadAction
import com.intellij.openapi.command.WriteCommandAction
import com.intellij.openapi.components.service
import com.intellij.openapi.diagnostic.Logger
import com.intellij.openapi.progress.ProgressIndicator
import com.intellij.openapi.progress.ProgressManager
import com.intellij.openapi.progress.Task
import com.intellij.openapi.project.Project
import com.intellij.openapi.project.ProjectLocator
import com.intellij.openapi.project.ProjectManager
import com.intellij.openapi.util.Disposer
import com.intellij.openapi.util.io.toNioPathOrNull
import com.intellij.openapi.vfs.VfsUtilCore
import io.snyk.plugin.SnykFile
import io.snyk.plugin.events.SnykScanListenerLS
import io.snyk.plugin.getContentRootVirtualFiles
import io.snyk.plugin.getSyncPublisher
import io.snyk.plugin.pluginSettings
import io.snyk.plugin.refreshAnnotationsForOpenFiles
import io.snyk.plugin.toVirtualFile
import io.snyk.plugin.ui.SnykBalloonNotificationHelper
import io.snyk.plugin.ui.toolwindow.SnykPluginDisposable
import org.eclipse.lsp4j.ApplyWorkspaceEditParams
import org.eclipse.lsp4j.ApplyWorkspaceEditResponse
import org.eclipse.lsp4j.LogTraceParams
import org.eclipse.lsp4j.MessageActionItem
import org.eclipse.lsp4j.MessageParams
import org.eclipse.lsp4j.MessageType
import org.eclipse.lsp4j.ProgressParams
import org.eclipse.lsp4j.PublishDiagnosticsParams
import org.eclipse.lsp4j.ShowMessageRequestParams
import org.eclipse.lsp4j.WorkDoneProgressBegin
import org.eclipse.lsp4j.WorkDoneProgressCreateParams
import org.eclipse.lsp4j.WorkDoneProgressEnd
import org.eclipse.lsp4j.WorkDoneProgressKind.begin
import org.eclipse.lsp4j.WorkDoneProgressKind.end
import org.eclipse.lsp4j.WorkDoneProgressKind.report
import org.eclipse.lsp4j.WorkDoneProgressNotification
import org.eclipse.lsp4j.WorkDoneProgressReport
import org.eclipse.lsp4j.jsonrpc.services.JsonNotification
import org.eclipse.lsp4j.services.LanguageClient
import org.jetbrains.concurrency.runAsync
import snyk.common.ProductType
import snyk.trust.WorkspaceTrustService
import java.util.concurrent.ArrayBlockingQueue
import java.util.concurrent.CompletableFuture
import java.util.concurrent.TimeUnit

/**
 * Processes Language Server requests and notifications from the server to the IDE
 */
class SnykLanguageClient :
    LanguageClient,
    Disposable {
    val logger = Logger.getInstance("Snyk Language Server")
    val gson = Gson()

    private var disposed = false
        get() {
            return ApplicationManager.getApplication().isDisposed || field
        }

    fun isDisposed() = disposed

    private val progresses: Cache<String, ProgressIndicator> =
        Caffeine
            .newBuilder()
            .expireAfterAccess(10, TimeUnit.SECONDS)
            .removalListener(
                RemovalListener<String, ProgressIndicator> { _, indicator, _ ->
                    indicator?.cancel()
                },
            ).build()
    private val progressReportMsgCache: Cache<String, MutableList<WorkDoneProgressReport>> =
        Caffeine.newBuilder().expireAfterWrite(10, TimeUnit.SECONDS).build()
    private val progressEndMsgCache: Cache<String, WorkDoneProgressEnd> =
        Caffeine.newBuilder().expireAfterWrite(10, TimeUnit.SECONDS).build()


    override fun telemetryEvent(`object`: Any?) {
        // do nothing
    }

    override fun publishDiagnostics(diagnosticsParams: PublishDiagnosticsParams?) {
        if (diagnosticsParams == null) {
            return
        }

        val filePath = diagnosticsParams.uri

        try {
            getScanPublishersFor(filePath.toVirtualFile().path).forEach { (project, scanPublisher) ->
                updateCache(project, filePath, diagnosticsParams, scanPublisher)
            }
        } catch (e: Exception) {
            logger.error("Error publishing the new diagnostics", e)
        }
    }

    private fun updateCache(
        project: Project,
        filePath: String,
        diagnosticsParams: PublishDiagnosticsParams,
        scanPublisher: SnykScanListenerLS
    ) {
        val snykFile = SnykFile(project, filePath.toVirtualFile())
        val firstDiagnostic = diagnosticsParams.diagnostics.firstOrNull()
        val product = firstDiagnostic?.source

        //If the diagnostics for the file is empty, clear the cache.
        if (firstDiagnostic == null) {
            scanPublisher.onPublishDiagnostics("code", snykFile, emptyList())
            scanPublisher.onPublishDiagnostics("oss", snykFile, emptyList())
            return
        }

        val issueList = diagnosticsParams.diagnostics
            .filter { it.data != null }
            .map {
                val issue = gson.fromJson(it.data.toString(), ScanIssue::class.java)
                // load textrange for issue so it doesn't happen in UI thread
                issue.textRange
                issue
            }.toList()

        when (product) {
            LsProductConstants.OpenSource.value -> {
                scanPublisher.onPublishDiagnostics(product, snykFile, issueList)
            }

            LsProductConstants.Code.value -> {
                scanPublisher.onPublishDiagnostics(product, snykFile, issueList)
            }

            LsProductConstants.InfrastructureAsCode.value -> {
                // TODO implement
            }

            LsProductConstants.Container.value -> {
                // TODO implement
            }
        }
        return
    }

    override fun applyEdit(params: ApplyWorkspaceEditParams?): CompletableFuture<ApplyWorkspaceEditResponse> {
        val falseFuture = CompletableFuture.completedFuture(ApplyWorkspaceEditResponse(false))
        if (disposed) return falseFuture
        val project =
            params
                ?.edit
                ?.changes
                ?.keys
                ?.firstNotNullOfOrNull {
                    ProjectLocator.getInstance().guessProjectForFile(it.toVirtualFile())
                }
                ?: ProjectUtil.getActiveProject()
                ?: return falseFuture

        WriteCommandAction.runWriteCommandAction(project) {
            params?.edit?.changes?.forEach {
                DocumentChanger.applyChange(it)
            }
        }

        refreshUI()
        return CompletableFuture.completedFuture(ApplyWorkspaceEditResponse(true))
    }

    override fun refreshCodeLenses(): CompletableFuture<Void> = refreshUI()

    override fun refreshInlineValues(): CompletableFuture<Void> = refreshUI()

    private fun refreshUI(): CompletableFuture<Void> {
        val completedFuture: CompletableFuture<Void> = CompletableFuture.completedFuture(null)
        if (disposed) return completedFuture
        runAsync {
            ProjectManager
                .getInstance()
                .openProjects
                .filter { !it.isDisposed }
                .forEach { project ->
                    ReadAction.run<RuntimeException> {
                        if (!project.isDisposed) refreshAnnotationsForOpenFiles(project)
                    }
                }
        }
        return completedFuture
    }

    @JsonNotification(value = "$/snyk.folderConfigs")
    fun folderConfig(folderConfigParam: FolderConfigsParam?) {
        val folderConfigs = folderConfigParam?.folderConfigs ?: emptyList()
        runAsync {
            service<FolderConfigSettings>().addAll(folderConfigs)
        }
    }

    @JsonNotification(value = "$/snyk.scan")
    fun snykScan(snykScan: SnykScanParams) {
        if (disposed) return
        try {
            getScanPublishersFor(snykScan.folderPath).forEach { (_, scanPublisher) ->
                processSnykScan(snykScan, scanPublisher)
            }
        } catch (e: Exception) {
            logger.error("Error processing snyk scan", e)
        }
    }

    private fun processSnykScan(
        snykScan: SnykScanParams,
        scanPublisher: SnykScanListenerLS,
    ) {
        val product =
            when (snykScan.product) {
                "code" -> ProductType.CODE_SECURITY
                "oss" -> ProductType.OSS
                else -> return
            }
        val key = ScanInProgressKey(snykScan.folderPath.toVirtualFile(), product)
        when (snykScan.status) {
            "inProgress" -> {
                if (ScanState.scanInProgress[key] == true) return
                ScanState.scanInProgress[key] = true
                scanPublisher.scanningStarted(snykScan)
            }

            "success" -> {
                ScanState.scanInProgress[key] = false
                processSuccessfulScan(snykScan, scanPublisher)
            }

            "error" -> {
                ScanState.scanInProgress[key] = false
                scanPublisher.scanningError(snykScan)
            }
        }
    }


    private fun processSuccessfulScan(
        snykScan: SnykScanParams,
        scanPublisher: SnykScanListenerLS,
    ) {
        logger.info("Scan completed")

        when (snykScan.product) {
            "oss" -> {
                scanPublisher.scanningOssFinished()
            }

            "code" -> {
                scanPublisher.scanningSnykCodeFinished()
            }

            "iac" -> {
                // TODO implement
            }

            "container" -> {
                // TODO implement
            }
        }
    }

    /**
     * Get all the scan publishers for the given scan. As the folder path could apply to different projects
     * containing that content root, we need to notify all of them.
     */
    private fun getScanPublishersFor(path: String): Set<Pair<Project, SnykScanListenerLS>> =
        getProjectsForFolderPath(path)
            .mapNotNull { p ->
                getSyncPublisher(p, SnykScanListenerLS.SNYK_SCAN_TOPIC)?.let { scanListenerLS ->
                    Pair(p, scanListenerLS)
                }
            }.toSet()

    private fun getProjectsForFolderPath(folderPath: String) =
        ProjectManager.getInstance().openProjects.filter {
            it
                .getContentRootVirtualFiles().any { ancestor ->
                    val folder = folderPath.toVirtualFile()
                    VfsUtilCore.isAncestor(ancestor, folder, true) || ancestor == folder
                }
        }

    @JsonNotification(value = "$/snyk.hasAuthenticated")
    fun hasAuthenticated(param: HasAuthenticatedParam) {
        if (disposed) return
        if (pluginSettings().token == param.token) return
        pluginSettings().token = param.token
        ApplicationManager.getApplication().saveSettings()

        if (pluginSettings().token?.isNotEmpty() == true && pluginSettings().scanOnSave) {
            val wrapper = LanguageServerWrapper.getInstance()
            // retrieve global ignores feature flag status after auth
            LanguageServerWrapper.getInstance().refreshFeatureFlags()

            ProjectManager.getInstance().openProjects.forEach {
                wrapper.sendScanCommand(it)
            }
        }
    }

    @JsonNotification(value = "$/snyk.addTrustedFolders")
    fun addTrustedPaths(param: SnykTrustedFoldersParams) {
        if (disposed) return
        val trustService = service<WorkspaceTrustService>()
        param.trustedFolders.forEach { it.toNioPathOrNull()?.let { path -> trustService.addTrustedPath(path) } }
    }

    override fun createProgress(params: WorkDoneProgressCreateParams?): CompletableFuture<Void> =
        CompletableFuture.completedFuture(null)

    private fun createProgressInternal(
        token: String,
        begin: WorkDoneProgressBegin,
    ) {
        ProgressManager
            .getInstance()
            .run(
                object : Task.Backgroundable(ProjectUtil.getActiveProject(), "Snyk: ${begin.title}", true) {
                    override fun run(indicator: ProgressIndicator) {
                        logger.debug(
                            "Creating progress indicator for: $token, title: ${begin.title}, message: ${begin.message}",
                        )
                        indicator.isIndeterminate = false
                        indicator.text = begin.title
                        indicator.text2 = begin.message
                        indicator.fraction = 0.1
                        progresses.put(token, indicator)
                        while (!indicator.isCanceled && !disposed) {
                            Thread.sleep(1000)
                        }
                        logger.debug("Progress indicator canceled for token: $token")
                    }
                },
            )
    }

    override fun notifyProgress(params: ProgressParams) {
        if (disposed) return
        // first: check if progress has begun
        runAsync {
            val token = params.token?.left ?: return@runAsync
            if (progresses.getIfPresent(token) != null) {
                processProgress(params)
            } else {
                when (val progressNotification = params.value.left) {
                    is WorkDoneProgressEnd -> {
                        progressEndMsgCache.put(token, progressNotification)
                    }

                    is WorkDoneProgressReport -> {
                        val list = progressReportMsgCache.get(token) { mutableListOf() }
                        list.add(progressNotification)
                    }

                    else -> {
                        processProgress(params)
                    }
                }
                return@runAsync
            }
        }
    }

    private fun processProgress(params: ProgressParams?) {
        val token = params?.token?.left ?: return
        val workDoneProgressNotification = params.value.left ?: return
        when (workDoneProgressNotification.kind) {
            begin -> {
                val begin: WorkDoneProgressBegin = workDoneProgressNotification as WorkDoneProgressBegin
                createProgressInternal(token, begin)
                // wait until the progress indicator is created in the background thread
                while (progresses.getIfPresent(token) == null) {
                    Thread.sleep(100)
                }

                // process previously reported progress and end messages for token
                processCachedProgressReports(token)
                processCachedEndReport(token)
            }

            report -> {
                progressReport(token, workDoneProgressNotification)
            }

            end -> {
                progressEnd(token, workDoneProgressNotification)
            }

            null -> {}
        }
    }

    private fun processCachedEndReport(token: String) {
        val endReport = progressEndMsgCache.getIfPresent(token)
        if (endReport != null) {
            progressEnd(token, endReport)
        }
        progressEndMsgCache.invalidate(token)
    }

    private fun processCachedProgressReports(token: String) {
        val reportParams = progressReportMsgCache.getIfPresent(token)
        if (reportParams != null) {
            reportParams.forEach { report ->
                progressReport(token, report)
            }
            progressReportMsgCache.invalidate(token)
        }
    }

    private fun progressReport(
        token: String,
        workDoneProgressNotification: WorkDoneProgressNotification,
    ) {
        logger.debug("Received progress report notification for token: $token")
        progresses.getIfPresent(token)?.let {
            val report: WorkDoneProgressReport = workDoneProgressNotification as WorkDoneProgressReport
            logger.debug("Token: $token, progress: ${report.percentage}%, message: ${report.message}")
            it.text = report.message
            it.isIndeterminate = false
            it.fraction = report.percentage / 100.0
        }
        return
    }

    private fun progressEnd(
        token: String,
        workDoneProgressNotification: WorkDoneProgressNotification,
    ) {
        logger.debug("Received progress end notification for token: $token")
        progresses.getIfPresent(token)?.let {
            val workDoneProgressEnd = workDoneProgressNotification as WorkDoneProgressEnd
            it.text = workDoneProgressEnd.message
            progresses.invalidate(token)
        }
        return
    }

    override fun logTrace(params: LogTraceParams?) {
        if (disposed) return
        logger.info(params?.message)
    }

    override fun showMessage(messageParams: MessageParams?) {
        if (disposed) return
        val project = ProjectUtil.getActiveProject()
        if (project == null) {
            logger.info(messageParams?.message)
            return
        }
        when (messageParams?.type) {
            MessageType.Error -> SnykBalloonNotificationHelper.showError(messageParams.message, project)
            MessageType.Warning -> SnykBalloonNotificationHelper.showWarn(messageParams.message, project)
            MessageType.Info -> {
                val notification = SnykBalloonNotificationHelper.showInfo(messageParams.message, project)
                ApplicationManager.getApplication().executeOnPooledThread {
                    Thread.sleep(10000)
                    notification.expire()
                }
            }

            MessageType.Log -> logger.info(messageParams.message)
            null -> {}
        }
    }

    override fun showMessageRequest(requestParams: ShowMessageRequestParams): CompletableFuture<MessageActionItem> {
        val completedFuture = CompletableFuture.completedFuture(MessageActionItem(""))
        if (disposed) return completedFuture
        val project = ProjectUtil.getActiveProject() ?: return completedFuture

        showMessageRequestFutures.clear()
        val actions =
            requestParams.actions
                .map {
                    object : AnAction(it.title) {
                        override fun actionPerformed(p0: AnActionEvent) {
                            showMessageRequestFutures.put(MessageActionItem(it.title))
                        }
                    }
                }.toSet()
                .toTypedArray()

        val notification = SnykBalloonNotificationHelper.showInfo(requestParams.message, project, *actions)
        val messageActionItem = showMessageRequestFutures.poll(10, TimeUnit.SECONDS)
        notification.expire()
        return CompletableFuture.completedFuture(messageActionItem ?: MessageActionItem(""))
    }

    override fun logMessage(message: MessageParams?) {
        message?.let {
            when (it.type) {
                MessageType.Error -> logger.error(it.message)
                MessageType.Warning -> logger.warn(it.message)
                MessageType.Info -> logger.info(it.message)
                MessageType.Log -> logger.debug(it.message)
                null -> logger.info(it.message)
            }
        }
    }

    companion object {
        // we only allow one message request at a time
        val showMessageRequestFutures = ArrayBlockingQueue<MessageActionItem>(1)
    }

    override fun dispose() {
        disposed = true
    }

    init {
        Disposer.register(SnykPluginDisposable.getInstance(), this)
    }
}
