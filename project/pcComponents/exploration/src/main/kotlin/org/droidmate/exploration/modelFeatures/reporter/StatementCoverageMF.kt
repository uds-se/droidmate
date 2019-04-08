// DroidMate, an automated execution generator for Android apps.
// Copyright (C) 2012-2018. Saarland University
//
// This program is free software: you can redistribute it and/or modify
// it under the terms of the GNU General Public License as published by
// the Free Software Foundation, either version 3 of the License, or
// (at your option) any later version.
//
// This program is distributed in the hope that it will be useful,
// but WITHOUT ANY WARRANTY; without even the implied warranty of
// MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE.  See the
// GNU General Public License for more details.
//
// You should have received a copy of the GNU General Public License
// along with this program.  If not, see <http://www.gnu.org/licenses/>.
//
// Current Maintainers:
// Nataniel Borges Jr. <nataniel dot borges at cispa dot saarland>
// Jenny Hotzkow <jenny dot hotzkow at cispa dot saarland>
//
// Former Maintainers:
// Konrad Jamrozik <jamrozik at st dot cs dot uni-saarland dot de>
//
// web: www.droidmate.org

package org.droidmate.exploration.modelFeatures.reporter

import com.natpryce.konfig.PropertyGroup
import com.natpryce.konfig.booleanType
import com.natpryce.konfig.getValue
import com.natpryce.konfig.stringType
import kotlinx.coroutines.*
import kotlinx.coroutines.sync.Mutex
import org.droidmate.exploration.ExplorationContext
import org.droidmate.exploration.modelFeatures.ModelFeature
import org.droidmate.explorationModel.config.ModelConfig
import org.droidmate.explorationModel.interaction.Interaction
import org.droidmate.explorationModel.interaction.State
import org.droidmate.misc.deleteDir
import org.json.JSONArray
import org.json.JSONObject
import org.slf4j.Logger
import org.slf4j.LoggerFactory
import java.io.IOException
import java.nio.file.Files
import java.nio.file.Path
import java.text.SimpleDateFormat
import java.time.Duration
import java.util.*
import java.util.concurrent.ConcurrentHashMap
import kotlin.coroutines.CoroutineContext
import kotlin.streams.toList

/**
 * Model feature to monitor the statement coverage by processing an optional instrumentation file and fetching
 * the statement data from the device.
 */
class StatementCoverageMF(private val statementsLogOutputDir: Path,
                          private val config: ModelConfig,
                          private val readStatements: ()-> List<List<String>>,
                          private val appName: String,
                          private val resourceDir: Path,
                          private val fileName: String = "coverage.txt") : ModelFeature() {

    private val log: Logger by lazy { LoggerFactory.getLogger(StatementCoverageMF::class.java) }
    private val dateFormat = SimpleDateFormat("yyyy-MM-dd HH:mm:ss.SSS", Locale.getDefault())

    override val coroutineContext: CoroutineContext = CoroutineName("StatementCoverageMF") + Job()

    private val executedStatementsMap: ConcurrentHashMap<String, Date> = ConcurrentHashMap()
    private val instrumentationMap: Map<String, String>

    private val mutex = Mutex()

    private var counter = 0

    init {
        assert(statementsLogOutputDir.deleteDir())
        Files.createDirectories(statementsLogOutputDir)

        instrumentationMap = getInstrumentation(appName)
    }

    override suspend fun onNewAction(traceId: UUID, interactions: List<Interaction>, prevState: State, newState: State) {
        // Prevent concurrent problems
        try {
            mutex.lock()
            updateCoverage()
        } finally {
            mutex.unlock()
        }
    }

    /**
     * Returns a map which is used for the coverage calculation.
     */
    private fun getInstrumentation(apkName: String): Map<String, String> {
        return if (!Files.exists(resourceDir)) {
            log.warn("Provided Dir does not exist: $resourceDir." +
                "DroidMate will monitor coverage, but won't be able to calculate the coverage.")
            emptyMap()
        } else {
            val instrumentationFile = getInstrumentationFile(apkName, resourceDir)
            if (instrumentationFile != null)
                readInstrumentationFile(instrumentationFile)
            else {
                log.warn("Provided directory ($resourceDir) does not contain " +
                    "the corresponding instrumentation file. DroidMate will monitor coverage, but won't be able" +
                    "to calculate the coverage.")
                emptyMap()
            }

        }
    }

    /**
     * Returns the the given instrumentation file corresponding to the passed [apkName].
     * Returns null, if the instrumentation file is not present.
     *
     * Example:
     * apkName = a2dp.Vol_137.apk
     * return: instrumentation-a2dp.Vol.json
     */
    private fun getInstrumentationFile(apkName: String, targetDir: Path): Path? {
        return Files.list(targetDir)
            .toList()
            .find {
                it.fileName.toString().contains(apkName)
                    && it.fileName.toString().endsWith(".apk$INSTRUMENTATION_FILE_SUFFIX")
            }
    }

    @Throws(IOException::class)
    private fun readInstrumentationFile(instrumentationFile: Path?): Map<String, String> {
        val jsonData = String(Files.readAllBytes(instrumentationFile))
        val jObj = JSONObject(jsonData)

        val jArr = JSONArray(jObj.getJSONArray(INSTRUMENTATION_FILE_METHODS_PROP).toString())
        val statements: MutableMap<String, String> = mutableMapOf()

        (0 until jArr.length()).forEach { idx ->
            val method = jArr[idx]

            val parts = method.toString().split(ID_STR.toRegex(), 2).toTypedArray()
            val id = parts.last()

            assert(id.toLong() >= 0) { "Invalid id: $id" }

            statements[id] = method.toString()
        }

        return statements
    }

    /**
     * Returns the current measured coverage.
     * Note: Returns 0 if [instrumentationMap] is empty.
     */
    @Suppress("MemberVisibilityCanBePrivate")
    fun getCurrentCoverage(): Double {
        return if (instrumentationMap.isEmpty()) {
            0.0
        } else {
            assert(executedStatementsMap.size <= instrumentationMap.size)
            executedStatementsMap.size / instrumentationMap.size.toDouble()
        }
    }

    /**
     * Fetch the statement data form the device. Afterwards, it parses the data and updates [executedStatementsMap].
     */
    private fun updateCoverage() {
        // Fetch the statement data from the device
        val readStatements = readStatements()

        readStatements
            .forEach { statement ->
                val timestamp = statement[0]
                val tms = dateFormat.parse(timestamp)

                val id = statement[1]
                assert(id.toLong() >= 0) { "Invalid id: $id" }

                // Add the statement if it wasn't executed before
                val found = executedStatementsMap.containsKey(id)

                if (!found /*&& instrumentationMap.containsKey(id)*/)
                    executedStatementsMap[id] = tms
            }

        log.info("Current statement coverage: ${getCurrentCoverage()}. Encountered statements: ${executedStatementsMap.size}")

        // Write the received content into a file
        if (readStatements.isNotEmpty()) {
            val file = getLogFilename(counter)
            Files.write(file, readStatements.map { "${it[1]};${it[0]}" })

            counter++
        }
    }

    /**
     * Returns the logfile name depending on the [counter] in which the content is written into.
     */
    private fun getLogFilename(counter: Int): Path {
        return statementsLogOutputDir.resolve("$appName-statements-%04d".format(counter))
    }

    override suspend fun onAppExplorationFinished(context: ExplorationContext) {
        this.join()

        val sb = StringBuilder()
        sb.appendln(header)

        if (executedStatementsMap.isNotEmpty()) {
            val sortedStatements = executedStatementsMap.entries
                .sortedBy { it.value }
            val initialDate = sortedStatements.first().value

            sortedStatements
                .forEach {
                    sb.appendln("${it.key};${Duration.between(initialDate.toInstant(), it.value.toInstant()).toMillis() / 1000}")
                }
        }

        val outputFile = context.getModel().config.baseDir.resolve(fileName)
        Files.write(outputFile, sb.lines())
    }

    companion object {
        private const val header = "Statement(id);Time(Duration in sec till first occurrence)"

        // Use something for unique than 'id='
        const val ID_STR = "dmid="
        const val INSTRUMENTATION_FILE_SUFFIX = ".json"
        const val INSTRUMENTATION_FILE_METHODS_PROP = "allMethods"

        object StatementCoverage : PropertyGroup() {
            val enableCoverage by booleanType
            val onlyCoverAppPackageName by booleanType
            val coverageDir by stringType
        }

    }

}