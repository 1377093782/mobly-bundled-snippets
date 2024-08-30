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
package com.google.snippet.wifi.aware;

import android.content.Context;
import android.net.ConnectivityManager;
import android.net.Network;
import android.net.NetworkCapabilities;
import android.net.NetworkRequest;
import android.net.TransportInfo;

import androidx.annotation.NonNull;
import androidx.test.core.app.ApplicationProvider;

import com.google.android.mobly.snippet.Snippet;
import com.google.android.mobly.snippet.event.EventCache;
import com.google.android.mobly.snippet.event.SnippetEvent;
import com.google.android.mobly.snippet.rpc.AsyncRpc;
import com.google.android.mobly.snippet.rpc.Rpc;

public class ConnectivityManagerSnippet implements Snippet {
    private final Context mContext;
    private final ConnectivityManager mConnectivityManager;
    private NetworkCallback mNetworkCallBack;

    class ConnectivityManagerSnippetSnippetException extends Exception {
        ConnectivityManagerSnippetSnippetException(String msg) {
            super(msg);
        }
    }

    private void checkConnectivityManager() throws ConnectivityManagerSnippetSnippetException {
        if (mConnectivityManager == null) {
            throw new ConnectivityManagerSnippetSnippetException("ConnectivityManager not "
                    + "available.");
        }
    }

    public ConnectivityManagerSnippet() throws ConnectivityManagerSnippetSnippetException {
        mContext = ApplicationProvider.getApplicationContext();
        mConnectivityManager = mContext.getSystemService(ConnectivityManager.class);
        checkConnectivityManager();
    }

    /**
     * Customized network monitoring method
     */
    public class NetworkCallback extends ConnectivityManager.NetworkCallback {

        String mCallBackId;

        NetworkCallback(String callBackId) {
            mCallBackId = callBackId;
        }

        @Override
        public void onUnavailable() {
            SnippetEvent event = new SnippetEvent(mCallBackId, "NetworkCallback");
            event.getData().putString("method", "onUnavailable");
            EventCache.getInstance().postEvent(event);
        }

        @Override
        public void onCapabilitiesChanged(
                @NonNull Network network, @NonNull NetworkCapabilities networkCapabilities) {

            SnippetEvent event = new SnippetEvent(mCallBackId, "NetworkCallback");
            event.getData().putString("method", "onCapabilitiesChanged");
            event.getData().putParcelable("network", network);
            event.getData().putParcelable("networkCapabilities", networkCapabilities);
            TransportInfo transportInfo = networkCapabilities.getTransportInfo();
            if (transportInfo != null) {
                event.getData()
                        .putString("transportInfoClassName", transportInfo.getClass().getName());
            } else {
                event.getData().putString("transportInfoClassName", "");
            }
            EventCache.getInstance().postEvent(event);

        }
    }

    /**
     * An object describing a network that the application is interested in.
     *
     * @param callBackId              Assigned automatically by mobly.
     * @param request                 The request object.
     * @param requestNetworkTimeoutMs The timeout in milliseconds.
     */
    @AsyncRpc(description = "Request a network.")
    public void connectivityRequestNetwork(String callBackId, NetworkRequest request,
                                           int requestNetworkTimeoutMs) {
        mNetworkCallBack = new NetworkCallback(callBackId);
        mConnectivityManager.requestNetwork(request, mNetworkCallBack, requestNetworkTimeoutMs);
    }

    /**
     * Unregister a network request.
     */
    @Rpc(description = "Unregister a network request")
    public void connectivityUnregisterNetwork() {
        if (mNetworkCallBack == null) {
            return;
        }
        mConnectivityManager.unregisterNetworkCallback(mNetworkCallBack);
    }
}
