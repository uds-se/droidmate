// DroidMate, an automated execution generator for Android apps.
// Copyright (C) 2012-2016 Konrad Jamrozik
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
// email: jamrozik@st.cs.uni-saarland.de
// web: www.droidmate.org

package org.droidmate.monitor

import com.google.gson.JsonObject
import com.google.gson.JsonParser
import org.droidmate.apis.ApiMethodSignature
import org.slf4j.LoggerFactory
import java.nio.file.Files

class MonitorGeneratorFrontend {
    companion object {
        private val log = LoggerFactory.getLogger(MonitorGeneratorFrontend::class.java)

        @JvmStatic
        fun main(args: Array<String>) {
            try {
                val res = MonitorGeneratorResources(args)

                generateMonitorSrc(res)

            } catch (e: Exception) {
                handleException(e)
            }
        }

        @JvmStatic
        private fun generateMonitorSrc(res: MonitorGeneratorResources): MonitorSrcFile {
            val monitorGenerator = MonitorGenerator(RedirectionsGenerator(res.androidApi),
                    MonitorSrcTemplate(res.monitorSrcTemplatePath, res.androidApi))


//    List<ApiMethodSignature> signatures = getLegacyMethodSignatures(res)
            val signatures = getMethodSignatures(res)

            val monitorSrc = monitorGenerator.generate(signatures)

            return MonitorSrcFile(res.monitorSrcOutPath, monitorSrc)
        }

        @JvmStatic
        private fun readMonitoredApisJSON(res: MonitorGeneratorResources): List<ApiMethodSignature> {
            val fileData = Files.readAllLines(res.monitoredApis).joinToString(System.lineSeparator())
            val apiList: MutableList<ApiMethodSignature> = ArrayList()
            val jsonApiList = JsonParser().parse(fileData).asJsonObject

            val apis = jsonApiList.get("apis").asJsonArray

            apis.forEach { item ->
                val objApi = item as JsonObject
                val className = objApi.get("className").asString
                val hookedMethod = objApi.get("hookedMethod").asString
                val signature = objApi.get("signature").asString
                val invokeAPICode = objApi.get("invokeAPICode").asString
                val defaultReturnValue = objApi.get("defaultReturnValue").asString
                val exceptionType = objApi.get("exceptionType").asString
                val logID = objApi.get("logID").asString
                val methodName = objApi.get("methodName").asString
                val paramList = objApi.get("paramList").asJsonArray.map { it.asString }
                val returnType = objApi.get("returnType").asString
                val isStatic = objApi.get("isStatic").asBoolean
                val platformVersion = objApi.get("platformVersion").asString

                if ((res.androidApi == AndroidAPI.API_23) ||
                        ((res.androidApi != AndroidAPI.API_23) && platformVersion.startsWith("!API23"))) {
                    val api = ApiMethodSignature.fromDescriptor(className, returnType, methodName, paramList,
                            isStatic, hookedMethod, signature, logID, invokeAPICode, defaultReturnValue, exceptionType)
                    apiList.add(api)
                }
            }

            return apiList
        }

        @JvmStatic
        private fun getMethodSignatures(res: MonitorGeneratorResources): List<ApiMethodSignature> {
            return readMonitoredApisJSON(res)
        }

        @JvmStatic
        var handleException: (Exception) -> Any =
                {
                    log.error("Exception was thrown and propagated to the frontend.", it)
                    System.exit(1)
                }
    }
}