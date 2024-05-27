package io.snyk.plugin

import ai.deepcode.javaclient.core.SuggestionForFile

class SnykResults(
    private val file2suggestions: Map<SnykFile, List<SuggestionForFile>> = emptyMap()
) {
    private fun suggestions(file: SnykFile): List<SuggestionForFile> = file2suggestions[file] ?: emptyList()

    private val files: Set<SnykFile> by lazy { file2suggestions.keys }

    val totalCount: Int by lazy { files.sumOf { getCount(it, null) } }

    val totalCriticalCount: Int by lazy { files.sumOf { criticalCount(it) } }

    val totalErrorsCount: Int by lazy { files.sumOf { errorsCount(it) } }

    val totalWarnsCount: Int by lazy { files.sumOf { warnsCount(it) } }

    val totalInfosCount: Int by lazy { files.sumOf { infosCount(it) } }

    fun cloneFiltered(filter: (SuggestionForFile) -> Boolean): SnykResults {
        return SnykResults(
            file2suggestions
                .mapValues { (_, suggestions) -> suggestions.filter(filter) }
                .filterValues { it.isNotEmpty() }
        )
    }

    // todo? also sort by line in file
    /** sort by Errors-Warnings-Infos */
    fun getSortedSuggestions(file: SnykFile): List<SuggestionForFile> =
        suggestions(file).sortedByDescending { it.getSeverityAsEnum() }

    /** sort by Errors-Warnings-Infos */
    fun getSortedFiles(): Collection<SnykFile> = files
        .sortedWith(Comparator { file1, file2 ->
            val file1Criticals by lazy { criticalCount(file1) }
            val file2Criticals by lazy { criticalCount(file2) }
            val file1Errors by lazy { errorsCount(file1) }
            val file2Errors by lazy { errorsCount(file2) }
            val file1Warns by lazy { warnsCount(file1) }
            val file2Warns by lazy { warnsCount(file2) }
            val file1Infos by lazy { infosCount(file1) }
            val file2Infos by lazy { infosCount(file2) }
            return@Comparator when {
                file1Criticals != file2Criticals -> file2Criticals - file1Criticals
                file1Errors != file2Errors -> file2Errors - file1Errors
                file1Warns != file2Warns -> file2Warns - file1Warns
                else -> file2Infos - file1Infos
            }
        })

    private fun criticalCount(file: SnykFile) = getCount(file, Severity.CRITICAL)

    private fun errorsCount(file: SnykFile) = getCount(file, Severity.HIGH)

    private fun warnsCount(file: SnykFile) = getCount(file, Severity.MEDIUM)

    private fun infosCount(file: SnykFile) = getCount(file, Severity.LOW)

    /** @params severity - if `NULL then accept all  */
    private fun getCount(file: SnykFile, severity: Severity?) =
        suggestions(file)
            .filter { severity == null || it.getSeverityAsEnum() == severity }
            .sumOf { it.ranges.size }

    override fun equals(other: Any?): Boolean {
        return other is SnykResults &&
            file2suggestions == other.file2suggestions
    }

    override fun hashCode(): Int {
        return file2suggestions.hashCode()
    }
}

fun SuggestionForFile.getSeverityAsEnum(): Severity = Severity.getFromIndex(this.severity)
