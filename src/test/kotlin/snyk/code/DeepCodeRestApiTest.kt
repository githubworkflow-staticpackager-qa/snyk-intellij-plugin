@file:Suppress("TestFunctionName")

package snyk.code

import ai.deepcode.javaclient.DeepCodeRestApi
import ai.deepcode.javaclient.DeepCodeRestApiImpl
import ai.deepcode.javaclient.core.AnalysisDataBase
import ai.deepcode.javaclient.requests.ExtendBundleWithContentRequest
import ai.deepcode.javaclient.requests.ExtendBundleWithHashRequest
import ai.deepcode.javaclient.requests.FileContentRequest
import ai.deepcode.javaclient.requests.FileHash2ContentRequest
import ai.deepcode.javaclient.requests.FileHashRequest
import ai.deepcode.javaclient.responses.CreateBundleResponse
import ai.deepcode.javaclient.responses.EmptyResponse
import ai.deepcode.javaclient.responses.GetAnalysisResponse
import ai.deepcode.javaclient.responses.GetFiltersResponse
import io.mockk.every
import io.mockk.mockk
import io.mockk.mockkStatic
import io.mockk.unmockkAll
import io.snyk.plugin.pluginSettings
import io.snyk.plugin.services.SnykApplicationSettingsStateService
import io.snyk.plugin.snykcode.newCodeRestApi
import org.junit.After
import org.junit.Assert
import org.junit.Test
import snyk.common.toSnykCodeApiUrl
import snyk.pluginInfo
import java.io.File
import java.io.FileOutputStream
import java.io.IOException
import java.net.URL
import java.nio.charset.StandardCharsets
import java.nio.file.Files
import java.nio.file.Paths
import java.security.MessageDigest
import java.security.NoSuchAlgorithmException
import java.util.Objects

/*
* This Java source file was generated by the Gradle 'init' task.
*/
class DeepCodeRestApiImplTest {

    @After
    fun tearDown() {
        unmockkAll()
    }

    @Throws(NoSuchAlgorithmException::class, IOException::class)
    private fun createBundleFromSource(orgName: String?): CreateBundleResponse {
        val testFile =
            File(javaClass.classLoader.getResource(TEST_FILE)!!.file)
        val absolutePath = testFile.absolutePath
        val deepCodedPath = if (absolutePath.startsWith("/")) absolutePath else "/$absolutePath"
        println("Abs File:$absolutePath")
        println("Deepcoded File:$deepCodedPath")
        println("-----------------")
        val fileContent = FileContentRequest()
        val fileText = String(Files.readAllBytes(testFile.toPath()))
        fileContent[deepCodedPath] = FileHash2ContentRequest(getHash(fileText), fileText)
        return restApiClient!!.createBundle(
            orgName,
            fileContent
        )
    }

    private fun createFileHashRequest(fakeFileName: String?): FileHashRequest {
        val testFile =
            File(javaClass.classLoader.getResource(TEST_FILE)!!.file)
        val absolutePath = testFile.absolutePath
        val deepCodedPath = ((if (absolutePath.startsWith("/")) "" else "/")
            + if (fakeFileName == null) absolutePath else absolutePath.replace(
            TEST_FILE,
            fakeFileName
        ))
        System.out.printf("\nFile: %1\$s\n", deepCodedPath)
        println("-----------------")

        // Append with System.currentTimeMillis() to make new Hash.
        try {
            FileOutputStream(absolutePath, true).use { fos ->
                fos.write(
                    System.currentTimeMillis().toString().toByteArray()
                )
            }
        } catch (e: IOException) {
            println(e.message)
        }
        val fileText: String
        return try {
            fileText = String(Files.readAllBytes(Paths.get(absolutePath)))
            val hash = getHash(fileText)
            System.out.printf("File hash: %1\$s\n", hash)
            val fileHashRequest = FileHashRequest()
            fileHashRequest[deepCodedPath] = hash
            fileHashRequest
        } catch (e: IOException) {
            throw RuntimeException(e)
        } catch (e: NoSuchAlgorithmException) {
            throw RuntimeException(e)
        }
    }

    private fun createBundleWithHash(): CreateBundleResponse {
        val files = createFileHashRequest(null)
        val response: CreateBundleResponse =
            restApiClient!!.createBundle(
                loggedOrgName,
                files
            )
        Assert.assertNotNull(response)
        System.out.printf(
            "Create Bundle call return:\nStatus code [%1\$d] %3\$s \n bundleId: %2\$s\n missingFiles: %4\$s\n",
            response.statusCode,
            response.bundleHash,
            response.statusDescription,
            response.missingFiles
        )
        return response
    }

    private fun filtersAndAssert() {
        println("\n--------------Get Filters----------------\n")
        val filtersResponse: GetFiltersResponse = restApiClient!!.filters
        Assert.assertNotNull(filtersResponse)
        val errorMsg = ("Get Filters return status code: ["
            + filtersResponse.statusCode
            + "] "
            + filtersResponse.statusDescription
            + "\n")
        Assert.assertEquals(errorMsg, 200, filtersResponse.statusCode.toLong())
        System.out.printf(
            "Get Filters call returns next filters: \nextensions: %1\$s \nconfigFiles: %2\$s\n",
            filtersResponse.extensions, filtersResponse.configFiles
        )
    }

    private fun checkBundleAndAssert() {
        println("\n--------------Check Bundle----------------\n")
        val fileHashRequest = createFileHashRequest(null)

        val checkBundleCreateBundleResponse: CreateBundleResponse =
            restApiClient!!.createBundle(
                loggedOrgName,
                fileHashRequest
            )
        Assert.assertNotNull(checkBundleCreateBundleResponse)
        System.out.printf(
            "\nCreate Bundle call return:\nStatus code [%1\$d] %3\$s \n bundleId: %2\$s\n missingFiles: %4\$s\n",
            checkBundleCreateBundleResponse.statusCode,
            checkBundleCreateBundleResponse.bundleHash,
            checkBundleCreateBundleResponse.statusDescription,
            checkBundleCreateBundleResponse.missingFiles
        )
        Assert.assertEquals(200, checkBundleCreateBundleResponse.statusCode.toLong())
        Assert.assertFalse("List of missingFiles is empty.", checkBundleCreateBundleResponse.missingFiles.isEmpty())

        val checkBundleResponse: CreateBundleResponse =
            restApiClient!!.checkBundle(
                loggedOrgName,
                checkBundleCreateBundleResponse.bundleHash
            )
        Assert.assertNotNull(checkBundleResponse)
        System.out.printf(
            "\nCheck Bundle call return:\nStatus code [%1\$d] %3\$s \n bundleId: %2\$s\n missingFiles: %4\$s\n",
            checkBundleResponse.statusCode,
            checkBundleResponse.bundleHash,
            checkBundleResponse.statusDescription,
            checkBundleResponse.missingFiles
        )
        Assert.assertEquals(200, checkBundleResponse.statusCode.toLong())
        Assert.assertFalse("List of missingFiles is empty.", checkBundleResponse.missingFiles.isEmpty())
        Assert.assertEquals(
            "Checked and returned bundleId's are different.",
            checkBundleCreateBundleResponse.bundleHash,
            checkBundleResponse.bundleHash
        )

        val uploadFileResponse = doUploadFile(checkBundleCreateBundleResponse, fileHashRequest)
        Assert.assertNotNull(uploadFileResponse)
        System.out.printf(
            "\nUpload Files call for file %3\$s \nStatus code [%1\$d] %2\$s\n",
            uploadFileResponse.statusCode,
            uploadFileResponse.statusDescription,
            checkBundleCreateBundleResponse.missingFiles[0]
        )
        Assert.assertEquals(200, uploadFileResponse.statusCode.toLong())
        val checkBundleResponseNew: CreateBundleResponse =
            restApiClient!!.checkBundle(
                loggedOrgName,
                checkBundleCreateBundleResponse.bundleHash
            )
        Assert.assertNotNull(checkBundleResponseNew)
        System.out.printf(
            "\nCheck Bundle call return:\nStatus code [%1\$d] %3\$s \n bundleId: %2\$s\n missingFiles: %4\$s\n",
            checkBundleResponseNew.statusCode,
            checkBundleResponseNew.bundleHash,
            checkBundleResponseNew.statusDescription,
            checkBundleResponseNew.missingFiles
        )
        Assert.assertEquals(200, checkBundleResponseNew.statusCode.toLong())
        Assert.assertTrue(
            "List of missingFiles is NOT empty.", checkBundleResponseNew.missingFiles.isEmpty()
        )
        Assert.assertEquals(
            "Checked and returned bundleId's are different.",
            checkBundleCreateBundleResponse.bundleHash,
            checkBundleResponseNew.bundleHash
        )
    }

    private fun createBundleWithHashAndAssert() {
        println("\n--------------Create Bundle with Hash----------------\n")
        val createBundleWithHash = createBundleWithHash()
        Assert.assertEquals(200, createBundleWithHash.statusCode.toLong())
    }

    private fun createBundleAndAssert(): String? {
        println("\n--------------Create Bundle from Source----------------\n")
        val createBundleResponse = createBundleFromSource(loggedOrgName)

        Assert.assertNotNull(createBundleResponse)
        System.out.printf(
            "Create Bundle call return:\nStatus code [%1\$d] %3\$s \nBundleId: [%2\$s]\n",
            createBundleResponse.statusCode, createBundleResponse.bundleHash, createBundleResponse.statusDescription
        )
        Assert.assertEquals(200, createBundleResponse.statusCode.toLong())
        return createBundleResponse.bundleHash
    }

    @Throws(NoSuchAlgorithmException::class)
    private fun getHash(fileText: String): String {
        val digest = MessageDigest.getInstance("SHA-256")
        println("-----------------")
        val encodedhash = digest.digest(fileText.toByteArray(StandardCharsets.UTF_8))
        return bytesToHex(encodedhash)
    }

    private fun extendBundleAndAssert() {
        println("\n--------------Extend Bundle----------------\n")
        val createBundleResponse = createBundleWithHash()
        Assert.assertEquals(200, createBundleResponse.statusCode.toLong())
        Assert.assertFalse("List of missingFiles is empty.", createBundleResponse.missingFiles.isEmpty())
        val newFileHashRequest = createFileHashRequest("test2.js")
        val extendBundleWithHashRequest = ExtendBundleWithHashRequest(newFileHashRequest, emptyList())
        val extendBundleResponse: CreateBundleResponse =
            restApiClient!!.extendBundle<ExtendBundleWithHashRequest>(
                loggedOrgName,
                createBundleResponse.bundleHash,
                extendBundleWithHashRequest
            )
        Assert.assertNotNull(extendBundleResponse)
        System.out.printf(
            "Extend Bundle call return:\nStatus code [%1\$d] %3\$s \n bundleId: %2\$s\n missingFiles: %4\$s\n",
            extendBundleResponse.statusCode,
            extendBundleResponse.bundleHash,
            extendBundleResponse.statusDescription,
            extendBundleResponse.missingFiles
        )
        Assert.assertEquals(200, extendBundleResponse.statusCode.toLong())
        Assert.assertFalse("List of missingFiles is empty.", extendBundleResponse.missingFiles.isEmpty())
    }

    private fun doUploadFile(
        createBundleResponse: CreateBundleResponse, fileHashRequest: FileHashRequest
    ): EmptyResponse {
        val testFile = File(
            Objects.requireNonNull<URL>(javaClass.classLoader.getResource(TEST_FILE))
                .file
        )
        val absolutePath = testFile.absolutePath
        val fileText: String
        fileText = try {
            // ?? com.intellij.openapi.util.io.FileUtil#loadFile(java.io.File, java.nio.charset.Charset)
            String(Files.readAllBytes(Paths.get(absolutePath)))
        } catch (e: IOException) {
            throw RuntimeException(e)
        }
        val filePath = createBundleResponse.missingFiles[0]
        val fileHash = fileHashRequest[filePath]
        val map = FileContentRequest()
        map[filePath] = FileHash2ContentRequest(fileHash, fileText)
        val ebr = ExtendBundleWithContentRequest(map, emptyList())
        return restApiClient!!.extendBundle<ExtendBundleWithContentRequest>(
            loggedOrgName,
            createBundleResponse.bundleHash,
            ebr
        )
    }

    private fun uploadFilesAndAssert() {
        println("\n--------------Upload Files by Hash----------------\n")
        val fileHashRequest = createFileHashRequest(null)
        val createBundleResponse: CreateBundleResponse =
            restApiClient!!.createBundle(
                loggedOrgName,
                fileHashRequest
            )
        Assert.assertNotNull(createBundleResponse)
        System.out.printf(
            "Create Bundle call return:\nStatus code [%1\$d] %3\$s \n bundleId: %2\$s\n missingFiles: %4\$s\n",
            createBundleResponse.statusCode,
            createBundleResponse.bundleHash,
            createBundleResponse.statusDescription,
            createBundleResponse.missingFiles
        )
        Assert.assertEquals(200, createBundleResponse.statusCode.toLong())
        Assert.assertFalse("List of missingFiles is empty.", createBundleResponse.missingFiles.isEmpty())
        val response = doUploadFile(createBundleResponse, fileHashRequest)
        Assert.assertNotNull(response)
        System.out.printf(
            "\nUpload Files call for file %3\$s \nStatus code [%1\$d] %2\$s\n",
            response.statusCode,
            response.statusDescription,
            createBundleResponse.missingFiles[0]
        )
        Assert.assertEquals(200, response.statusCode.toLong())
    }

    private fun getAnalysisAndAssert(bundleId: String) {
        println("\n--------------Get Analysis----------------\n")
        Assert.assertNotNull(
            "`bundleId` should be initialized at `_030_createBundle_from_source()`",
            bundleId
        )
        val deepcodedFilePath = createFileHashRequest(null).keys.stream().findFirst().orElseThrow {
            RuntimeException(
                "No files to analyse"
            )
        }
        val analysedFiles = listOf(deepcodedFilePath)
        var response = doAnalysisAndWait(bundleId, analysedFiles, null)
        assertAndPrintGetAnalysisResponse(response)
        val resultsEmpty = response.suggestions == null || response.suggestions.isEmpty()
        Assert.assertFalse("Analysis results must not be empty", resultsEmpty)
        println("\n---- With `severity=2` param:\n")
        response = doAnalysisAndWait(bundleId, analysedFiles, 2)
        assertAndPrintGetAnalysisResponse(response)
    }

    @Throws(InterruptedException::class)
    private fun doAnalysisAndWait(bundleId: String, analysedFiles: List<String>, severity: Int?): GetAnalysisResponse {
        var response: GetAnalysisResponse? = null
        for (i in 0..119) {
            response = restApiClient!!.getAnalysis(
                loggedOrgName,
                bundleId,
                severity,
                analysedFiles,
                bundleId,
                "test-java-client-ide"
            )
            if (response.status == "COMPLETE") break
            Thread.sleep(1000)
        }
        return response!!
    }

    private fun assertAndPrintGetAnalysisResponse(response: GetAnalysisResponse) {
        Assert.assertNotNull(response)
        System.out.printf(
            "Get Analysis call for test file: \n-----------\n %1\$s \n-----------\nreturns Status code: %2\$s \n%3\$s\n",
            TEST_FILE, response.statusCode, response
        )
        Assert.assertEquals(AnalysisDataBase.COMPLETE, response.status)
        Assert.assertEquals("Get Analysis request not succeed", 200, response.statusCode.toLong())
    }

    @Test
    fun snykCodeAnalysis_smoke_test() {
        // This test will fail on your local machine, unless you have a valid prod `SNYK_TOKEN`
        unmockkAll()
        mockkStatic("io.snyk.plugin.UtilsKt")
        val settings = mockk<SnykApplicationSettingsStateService>(relaxed = true)
        every { pluginSettings() } returns settings
        every { pluginSettings().token } returns loggedToken
        every { pluginSettings().localCodeEngineEnabled } returns false
        every { pluginSettings().ignoreUnknownCA } returns false

        mockkStatic("snyk.PluginInformationKt")
        every { pluginInfo } returns mockk(relaxed = true)

        restApiClient = deepCodeRestApi
        filtersAndAssert()
        var bundleId = createBundleAndAssert()
        getAnalysisAndAssert(bundleId!!)

        createBundleWithHashAndAssert()
        checkBundleAndAssert()
        extendBundleAndAssert()
        uploadFilesAndAssert()

    }

    companion object {
        const val TEST_FILE = "test-fixtures/code-test.js"

        // !!! Will works only with already logged sessionToken
        private val loggedToken = System.getenv("SNYK_TOKEN")
        private val loggedOrgName: String? = System.getenv("SNYK_ORG_NAME")
        private val baseUrl = System.getenv("DEEPROXY_API_URL")
        private var restApiClient: DeepCodeRestApi? = null
        private val deepCodeRestApi: DeepCodeRestApi
            get() {
                return if (baseUrl != null && !baseUrl.isEmpty()) {
                    newCodeRestApi(baseUrl)
                } else newCodeRestApi(toSnykCodeApiUrl(DeepCodeRestApiImpl.API_URL))
            }

        // in this test we explicitly allow it to test that hashing works
        private fun bytesToHex(hash: ByteArray): String {
            val hexString = StringBuilder()
            for (b in hash) {
                val hex = Integer.toHexString(0xff and b.toInt())
                if (hex.length == 1) hexString.append('0')
                hexString.append(hex)
            }
            return hexString.toString()
        }
    }
}
