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
package org.droidmate.report.apk

import org.droidmate.exploration.ExplorationContext
import org.droidmate.deviceInterface.guimodel.isLaunchApp
import org.droidmate.deviceInterface.guimodel.isPressBack
import java.nio.file.Files
import java.nio.file.Path

class ApiActionTrace @JvmOverloads constructor(private val fileName: String = "apiActionTrace.txt") : ApkReport() {

	override fun safeWriteApkReport(data: ExplorationContext, apkReportDir: Path, resourceDir: Path) {
		val sb = StringBuilder()
		val header = "actionNr\tactivity\taction\tapi\tuniqueStr\n"
		sb.append(header)

		var lastActivity = ""
		var currActivity = data.apk.launchableMainActivityName

		data.actionTrace.getActions().forEachIndexed { actionNr, record ->

			if (record.actionType .isPressBack())
				currActivity = lastActivity
			else if (record.actionType.isLaunchApp())
				currActivity = data.apk.launchableMainActivityName

			val logs = record.deviceLogs.apiLogs

			logs.forEach { log ->
				if (log.methodName.toLowerCase().startsWith("startactivit")) {
					val intent = log.getIntents()
					// format is: [ '[data=, component=<HERE>]', 'package ]
					if (intent.isNotEmpty()) {
						lastActivity = currActivity
						currActivity = intent[0].substring(intent[0].indexOf("component=") + 10).replace("]", "")
					}
				}

				sb.appendln("$actionNr\t$currActivity\t${record.actionType}\t${log.objectClass}->${log.methodName}\t${log.uniqueString}")
			}
		}

		val reportFile = apkReportDir.resolve(fileName)
		Files.write(reportFile, sb.toString().toByteArray())
	}
}