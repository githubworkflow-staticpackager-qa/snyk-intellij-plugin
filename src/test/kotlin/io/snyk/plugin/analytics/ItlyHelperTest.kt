package io.snyk.plugin.analytics

import io.snyk.plugin.services.SnykApplicationSettingsStateService
import org.hamcrest.core.IsEqual.equalTo
import org.junit.Assert.assertThat
import org.junit.Test
import snyk.analytics.AnalysisIsReady.AnalysisType

class ItlyHelperTest {

    @Test
    fun `empty selected products if nothing configured`() {
        val settings = SnykApplicationSettingsStateService()
        settings.ossScanEnable = false
        settings.snykCodeSecurityIssuesScanEnable = false
        settings.snykCodeQualityIssuesScanEnable = false

        val actualProducts = getSelectedProducts(settings)

        assertThat(actualProducts.size, equalTo(0))
    }

    @Test
    fun `one selected product if only one configured`() {
        val settings = SnykApplicationSettingsStateService()
        settings.ossScanEnable = true
        settings.snykCodeSecurityIssuesScanEnable = false
        settings.snykCodeQualityIssuesScanEnable = false

        val actualProducts = getSelectedProducts(settings)

        assertThat(actualProducts.size, equalTo(1))
        assertThat(actualProducts[0], equalTo(AnalysisType.SNYK_OPEN_SOURCE.analysisType))
    }
}
