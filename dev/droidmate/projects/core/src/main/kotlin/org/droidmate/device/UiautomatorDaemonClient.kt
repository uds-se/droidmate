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
package org.droidmate.device

import org.droidmate.android_sdk.DeviceException
import org.droidmate.android_sdk.IAdbWrapper
import org.droidmate.uiautomator_daemon.DeviceCommand
import org.droidmate.uiautomator_daemon.DeviceResponse
import org.droidmate.uiautomator_daemon.UiautomatorDaemonConstants

class UiautomatorDaemonClient constructor(private val adbWrapper: IAdbWrapper,
                                          private val deviceSerialNumber: String,
                                          private val port: Int,
                                          socketTimeout: Int,
                                          private val serverStartTimeout: Int,
                                          private val serverStartQueryDelay: Int) : IUiautomatorDaemonClient {

    companion object {
        @JvmStatic
        private fun startUiaDaemonThread(adbWrapper: IAdbWrapper, deviceSerialNumber: String, port: Int): Thread {
            val thread = Thread(UiAutomatorDaemonThread(adbWrapper, deviceSerialNumber, port))
            thread.isDaemon = true
            thread.start()
            return thread
        }
    }

    private val client = TcpClientBase<DeviceCommand, DeviceResponse>(socketTimeout)

    private var uiaDaemonThread: Thread? = null

    override fun sendCommandToUiautomatorDaemon(deviceCommand: DeviceCommand): DeviceResponse =
            this.client.queryServer(deviceCommand, this.port)

    override fun forwardPort() {
        this.adbWrapper.forwardPort(this.deviceSerialNumber, this.port)
    }

    override fun startUiaDaemon() {
        this.uiaDaemonThread = startUiaDaemonThread(this.adbWrapper, this.deviceSerialNumber, this.port)

        validateUiaDaemonServerStartLogcatMessages()

        assert(this.getUiaDaemonThreadIsAlive())

    }

    private fun validateUiaDaemonServerStartLogcatMessages() {
        val msgs = this.adbWrapper.waitForMessagesOnLogcat(
                this.deviceSerialNumber,
                UiautomatorDaemonConstants.UIADAEMON_SERVER_START_TAG,
                1,
                this.serverStartTimeout,
                this.serverStartQueryDelay)

        assert(msgs.isNotEmpty())
        // On Huawei devices many logs are disabled by default to increase performance,
        // if this message appears it's ok, the relevant informaiton will still be logged.
        //     int logctl_get(): open '/dev/hwlog_switch' fail -1, 13. Permission denied
        //     Note: log switch off, only log_main and log_events will have logs!
        var nrMessages = 1
        if (msgs.joinToString(System.lineSeparator()).contains("Note: log switch off, only log_main and log_events will have logs!"))
            nrMessages = 3

        assert(msgs.size == nrMessages, {
            "Expected exactly one message on logcat (with tag ${UiautomatorDaemonConstants.UIADAEMON_SERVER_START_MSG}) " +
                    "confirming that uia-daemon server has started. Instead, got ${msgs.size} messages. Msgs:\n${msgs.joinToString(System.lineSeparator())}"
        })
        assert(msgs.last().contains(UiautomatorDaemonConstants.UIADAEMON_SERVER_START_MSG))
    }

    override fun getUiaDaemonThreadIsNull(): Boolean = this.uiaDaemonThread == null

    override fun getUiaDaemonThreadIsAlive(): Boolean {
        assert(this.uiaDaemonThread != null)
        return this.uiaDaemonThread!!.isAlive
    }

    override fun waitForUiaDaemonToClose() {
        assert(uiaDaemonThread != null)
        try {
            uiaDaemonThread?.join()
            assert(!this.getUiaDaemonThreadIsAlive())
        } catch (e: InterruptedException) {
            throw DeviceException(e)
        }
    }
}