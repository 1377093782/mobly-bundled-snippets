/*
 * Copyright (C) 2024 The Android Open Source Project
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *      http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */

package com.google.snippet.wifi.direct.utils;

import android.net.wifi.p2p.WifiP2pDevice;
import android.net.wifi.p2p.WifiP2pManager.DnsSdServiceResponseListener;

import com.google.android.mobly.snippet.event.EventCache;
import com.google.android.mobly.snippet.event.SnippetEvent;
import com.google.android.mobly.snippet.util.Log;

/**
 * The utility class for testing android.net.wifi.p2p.WifiP2pManager.DnsSdServiceResponseListener
 * callback function.
 *
 * <p>reference:
 * https://cs.android.com/android/platform/superproject/main/+/main:cts/apps/CtsVerifier/src/com
 * /android/cts/verifier/p2p/testcase/DnsSdResponseListenerTest.java
 */
public class DnsSdResponseListenerTest implements DnsSdServiceResponseListener {

    /**
     * The target device address.
     */
    private String mTargetAddr;

    private SnippetEvent mEvent = null;

    public DnsSdResponseListenerTest(String callbackId, String targetAddr) {
        mEvent = new SnippetEvent(callbackId, "setDnsSdResponseListeners");
        this.mTargetAddr = targetAddr;
    }

    @Override
    public void onDnsSdServiceAvailable(
            String instanceName, String registrationType, WifiP2pDevice srcDevice
    ) {
        Log.d(instanceName + " " + registrationType + " received from " + srcDevice.deviceAddress);

        /*
         * Check only the response from the target device.
         * The response from other devices are ignored.
         */
        if (srcDevice.deviceAddress.equalsIgnoreCase(this.mTargetAddr)) {
            mEvent.getData().putString("instanceName", instanceName);
            mEvent.getData().putString("registrationType", registrationType);
            EventCache.getInstance().postEvent(mEvent);
        }
    }
}
