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

import static java.nio.charset.StandardCharsets.UTF_8;

import android.Manifest;
import android.annotation.SuppressLint;
import android.content.Context;
import android.content.pm.PackageManager;
import android.net.ConnectivityManager;
import android.net.Network;
import android.net.NetworkCapabilities;
import android.net.NetworkRequest;
import android.net.wifi.aware.AttachCallback;
import android.net.wifi.aware.AwarePairingConfig;
import android.net.wifi.aware.Characteristics;
import android.net.wifi.aware.DiscoverySession;
import android.net.wifi.aware.DiscoverySessionCallback;
import android.net.wifi.aware.PeerHandle;
import android.net.wifi.aware.PublishConfig;
import android.net.wifi.aware.PublishDiscoverySession;
import android.net.wifi.aware.ServiceDiscoveryInfo;
import android.net.wifi.aware.SubscribeConfig;
import android.net.wifi.aware.SubscribeDiscoverySession;
import android.net.wifi.aware.WifiAwareManager;
import android.net.wifi.aware.WifiAwareSession;
import android.os.Handler;
import android.os.HandlerThread;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.test.core.app.ApplicationProvider;

import com.google.android.mobly.snippet.Snippet;
import com.google.android.mobly.snippet.event.EventCache;
import com.google.android.mobly.snippet.event.SnippetEvent;
import com.google.android.mobly.snippet.rpc.AsyncRpc;
import com.google.android.mobly.snippet.rpc.Rpc;
import com.google.android.mobly.snippet.rpc.RpcOptional;
import com.google.android.mobly.snippet.util.Log;

import java.io.IOException;
import java.net.ServerSocket;
import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.Executors;
import java.util.function.Consumer;

import org.json.JSONException;
import org.json.JSONObject;

/**
 * An example snippet class with a simple Rpc.
 */
public class WifiAwareSnippet implements Snippet {
    private final Context mContext;
    private final WifiAwareManager mWifiAwareManager;
    private final ConnectivityManager mConnectivityManager;
    private final Handler mHandler;

    private WifiAwareSession mWifiAwareSession;
    private DiscoverySession mDiscoverySession;

    private PeerHandle mPeerHandle;

    private AwarePairingConfig mPairingConfig;

    private static class WifiAwareSnippetException extends Exception {

        public WifiAwareSnippetException(String msg) {
            super(msg);
        }
    }

    public WifiAwareSnippet() {
        mContext = ApplicationProvider.getApplicationContext();
        mWifiAwareManager = mContext.getSystemService(WifiAwareManager.class);
        mConnectivityManager = mContext.getSystemService(ConnectivityManager.class);
        HandlerThread handlerThread = new HandlerThread("Snippet-Aware");
        handlerThread.start();
        mHandler = new Handler(handlerThread.getLooper());
    }

    @AsyncRpc(description = "Monitor Wi-Fi Aware network status")
    public void registerNetworkCallback(String callbackId, @RpcOptional JSONObject jsonObject) throws WifiAwareSnippetException, JSONException {
        checkConnectivityManager();
        checkDiscoverySession();
        checkPeerHandler();
        // refer
        // https://developer.android.com/develop/connectivity/wifi/wifi-aware#create_a_connection
        NetworkRequest request = CustomJsonDeserializer.jsonToNetworkRequest(mDiscoverySession, mPeerHandle, jsonObject);

        mConnectivityManager.registerNetworkCallback(request, new ConnectivityManager.NetworkCallback() {
            @Override
            public void onAvailable(Network network) {
                super.onAvailable(network);
                // Handle network available
                Log.i("registerNetworkCallback:Network available ");
                SnippetEvent event = new SnippetEvent(callbackId, "onAvailable");
                event.getData().putParcelable("network", network);
                EventCache.getInstance().postEvent(event);
            }

            @Override
            public void onCapabilitiesChanged(Network network, NetworkCapabilities networkCapabilities) {
                super.onCapabilitiesChanged(network, networkCapabilities);
                Log.i("registerNetworkCallback:Network capabilities changed ");
                SnippetEvent event = new SnippetEvent(callbackId, "onCapabilitiesChanged");
                event.getData().putParcelable("network", network);
                event.getData().putParcelable("networkCapabilities", networkCapabilities);
                EventCache.getInstance().postEvent(event);
            }

            @Override
            public void onLost(Network network) {
                super.onLost(network);
                // Handle network lost
                Log.i("registerNetworkCallback:Network lost");
                SnippetEvent event = new SnippetEvent(callbackId, "onLost");
                event.getData().putParcelable("network", network);
                EventCache.getInstance().postEvent(event);
            }
        });
        Log.i("registerNetworkCallback");
    }

    private void checkConnectivityManager() throws WifiAwareSnippetException {
        if (mConnectivityManager == null) {
            throw new WifiAwareSnippetException("not support ConnectivityManager");
        }
    }

    @AsyncRpc(description = "Execute attach.")
    public void attach(String callbackId) {
        AttachCallback attachCallback = new AttachCallback() {
            @Override
            public void onAttachFailed() {
                super.onAttachFailed();
                SnippetEvent event = new SnippetEvent(callbackId, "onAttachFailed");
                EventCache.getInstance().postEvent(event);
            }

            @Override
            public void onAttached(WifiAwareSession session) {
                super.onAttached(session);
                mWifiAwareSession = session;
                SnippetEvent event = new SnippetEvent(callbackId, "onAttached");
                EventCache.getInstance().postEvent(event);
            }

            @Override
            public void onAwareSessionTerminated() {
                super.onAwareSessionTerminated();
                SnippetEvent event = new SnippetEvent(callbackId, "onAwareSessionTerminated");
                EventCache.getInstance().postEvent(event);
            }
        };
        mWifiAwareManager.attach(attachCallback, mHandler);
    }

    private void checkLocationAndNearbyWifiPermissions() {
        checkPermissions(Manifest.permission.ACCESS_FINE_LOCATION, Manifest.permission.NEARBY_WIFI_DEVICES);
    }

    private void checkPermissions(String... permissions) {
        for (String permission : permissions) {
            if (mContext.checkSelfPermission(permission) != PackageManager.PERMISSION_GRANTED) {
                throw new SecurityException("Permission denied (missing " + permission + " permission)");
            }
        }
    }

    @SuppressLint("MissingPermission")
    @AsyncRpc(description = "Execute subscribe.")
    public void subscribe(String callbackId, @RpcOptional JSONObject jsonObject) throws JSONException, WifiAwareSnippetException {
        checkWifiAwareSession();
        SubscribeConfig subscribeConfig = CustomJsonDeserializer.jsonToSubscribeConfig(jsonObject);
        checkLocationAndNearbyWifiPermissions();

        MyDiscoverySessionCallback myDiscoverySessionCallback = new MyDiscoverySessionCallback(callbackId);
        mWifiAwareSession.subscribe(subscribeConfig, myDiscoverySessionCallback, mHandler);
    }

    @SuppressLint("MissingPermission")
    @AsyncRpc(description = "Create publish session.")
    public void publish(String callbackId, @RpcOptional JSONObject jsonObject) throws JSONException, WifiAwareSnippetException {
        checkWifiAwareSession();
        checkLocationAndNearbyWifiPermissions();
        PublishConfig config = CustomJsonDeserializer.jsonToPublishConfig(jsonObject);
        MyDiscoverySessionCallback myDiscoverySessionCallback = new MyDiscoverySessionCallback(callbackId);
        mWifiAwareSession.publish(config, myDiscoverySessionCallback, mHandler);
    }

    private void checkWifiAwareSession() throws WifiAwareSnippetException {
        if (mWifiAwareSession == null) {
            throw new WifiAwareSnippetException("wifiAwareSession can't be null,Please ensure call attach success");
        }
    }

    private void checkAccessWifiStatePermission() throws WifiAwareSnippetException {
        if (mContext.checkSelfPermission(Manifest.permission.ACCESS_WIFI_STATE) != PackageManager.PERMISSION_GRANTED) {
            throw new SecurityException("Permission denied (missing " + Manifest.permission.ACCESS_WIFI_STATE + " permission)");
        }
    }

    @Rpc(description = "Check if Wi-Fi aware is available")
    public Boolean isAvailable() throws WifiAwareSnippetException {
        checkWifiManager();
        checkAccessWifiStatePermission();
        boolean isAvailable = mWifiAwareManager.isAvailable();
        return isAvailable;
    }

    @Rpc(description = "Check if Wi-Fi aware pairing is available")
    public Boolean isAwarePairingSupported() throws WifiAwareSnippetException {
        checkWifiManager();
        checkAccessWifiStatePermission();
        Characteristics characteristics = mWifiAwareManager.getCharacteristics();
        if (characteristics == null) {
            throw new WifiAwareSnippetException("Characteristics is null");
        }
        boolean isAwarePairingSupported = characteristics.isAwarePairingSupported();
        return isAwarePairingSupported;
    }

    class MyDiscoverySessionCallback extends DiscoverySessionCallback {

        String mCallBackId = "";

        public MyDiscoverySessionCallback(String callBackId) {
            this.mCallBackId = callBackId;
        }

        @Override
        public void onPublishStarted(PublishDiscoverySession session) {
            mDiscoverySession = session;
            SnippetEvent event = new SnippetEvent(mCallBackId, "onPublishStarted");
            EventCache.getInstance().postEvent(event);
        }

        @Override
        public void onSubscribeStarted(SubscribeDiscoverySession session) {
            mDiscoverySession = session;
            SnippetEvent event = new SnippetEvent(mCallBackId, "onSubscribeStarted");
            EventCache.getInstance().postEvent(event);
        }

        @Override
        public void onSessionConfigUpdated() {
            SnippetEvent event = new SnippetEvent(mCallBackId, "onSessionConfigUpdated");
            EventCache.getInstance().postEvent(event);
        }

        @Override
        public void onSessionConfigFailed() {
            SnippetEvent event = new SnippetEvent(mCallBackId, "onSessionConfigFailed");
            EventCache.getInstance().postEvent(event);
        }

        @Override
        public void onSessionTerminated() {
            SnippetEvent event = new SnippetEvent(mCallBackId, "onSessionTerminated");
            EventCache.getInstance().postEvent(event);
        }


        @Override
        public void onServiceDiscovered(PeerHandle peerHandle, byte[] serviceSpecificInfo, List<byte[]> matchFilter) {
            mPeerHandle = peerHandle;
            SnippetEvent event = new SnippetEvent(mCallBackId, "onServiceDiscovered");
            event.getData().putByteArray("serviceSpecificInfo", serviceSpecificInfo);
            for (int i = 0; i < matchFilter.size(); i++) {
                event.getData().putByteArray("matchFilter" + i, matchFilter.get(i));
            }

            EventCache.getInstance().postEvent(event);
        }

        @Override
        public void onServiceDiscovered(ServiceDiscoveryInfo info) {
            mPeerHandle = info.getPeerHandle();
            List<byte[]> matchFilter = info.getMatchFilters();
            mPairingConfig = info.getPairingConfig();
            SnippetEvent event = new SnippetEvent(mCallBackId, "onServiceDiscovered");
            event.getData().putByteArray("serviceSpecificInfo", info.getServiceSpecificInfo());
            event.getData().putString("pairedAlias", info.getPairedAlias());
            for (int i = 0; i < matchFilter.size(); i++) {
                event.getData().putByteArray("matchFilter" + i, matchFilter.get(i));
            }
            EventCache.getInstance().postEvent(event);
        }

        @Override
        public void onServiceDiscoveredWithinRange(PeerHandle peerHandle, byte[] serviceSpecificInfo, List<byte[]> matchFilter, int distanceMm) {
            mPeerHandle = peerHandle;
            SnippetEvent event = new SnippetEvent(mCallBackId, "onServiceDiscoveredWithinRange");
            event.getData().putByteArray("serviceSpecificInfo", serviceSpecificInfo);
            event.getData().putInt("distanceMm", distanceMm);
            for (int i = 0; i < matchFilter.size(); i++) {
                event.getData().putByteArray("matchFilter" + i, matchFilter.get(i));
            }
            EventCache.getInstance().postEvent(event);
        }

        @Override
        public void onMessageSendSucceeded(int messageId) {
            SnippetEvent event = new SnippetEvent(mCallBackId, "onMessageSendSucceeded");
            event.getData().putInt("lastMessageId", messageId);
            EventCache.getInstance().postEvent(event);
        }

        @Override
        public void onMessageSendFailed(int messageId) {
            SnippetEvent event = new SnippetEvent(mCallBackId, "onMessageSendFailed");
            event.getData().putInt("lastMessageId", messageId);
            EventCache.getInstance().postEvent(event);
        }

        @Override
        public void onMessageReceived(PeerHandle peerHandle, byte[] message) {
            mPeerHandle = peerHandle;
            SnippetEvent event = new SnippetEvent(mCallBackId, "onMessageReceived");
            event.getData().putByteArray("receivedMessage", message);
            EventCache.getInstance().postEvent(event);
        }

        @Override
        public void onPairingSetupRequestReceived(PeerHandle peerHandle, int requestId) {
            mPeerHandle = peerHandle;
            SnippetEvent event = new SnippetEvent(mCallBackId, "onPairingSetupRequestReceived");
            event.getData().putInt("pairingRequestId", requestId);
            EventCache.getInstance().postEvent(event);
        }

        @Override
        public void onPairingSetupSucceeded(PeerHandle peerHandle, String alias) {
            mPeerHandle = peerHandle;
            SnippetEvent event = new SnippetEvent(mCallBackId, "onPairingSetupSucceeded");
            event.getData().putString("pairedAlias", alias);
            EventCache.getInstance().postEvent(event);
        }

        @Override
        public void onPairingSetupFailed(PeerHandle peerHandle) {
            mPeerHandle = peerHandle;
            SnippetEvent event = new SnippetEvent(mCallBackId, "onPairingSetupFailed");
            EventCache.getInstance().postEvent(event);
        }

        @Override
        public void onPairingVerificationSucceed(@NonNull PeerHandle peerHandle, @NonNull String alias) {
            super.onPairingVerificationSucceed(mPeerHandle, alias);
            mPeerHandle = peerHandle;
            SnippetEvent event = new SnippetEvent(mCallBackId, "onPairingVerificationSucceeded");
            event.getData().putString("pairedAlias", alias);
            EventCache.getInstance().postEvent(event);
        }

        @Override
        public void onPairingVerificationFailed(PeerHandle peerHandle) {
            mPeerHandle = peerHandle;
            SnippetEvent event = new SnippetEvent(mCallBackId, "onPairingVerificationFailed");
            EventCache.getInstance().postEvent(event);
        }

        @Override
        public void onBootstrappingSucceeded(PeerHandle peerHandle, int method) {
            mPeerHandle = peerHandle;
            SnippetEvent event = new SnippetEvent(mCallBackId, "onBootstrappingSucceeded");
            event.getData().putInt("bootstrappingMethod", method);
            EventCache.getInstance().postEvent(event);
        }

        @Override
        public void onBootstrappingFailed(PeerHandle peerHandle) {
            mPeerHandle = peerHandle;
            SnippetEvent event = new SnippetEvent(mCallBackId, "onBootstrappingFailed");
            EventCache.getInstance().postEvent(event);
        }
    }

    @Rpc(description = "Send message.")
    public void sendMessage(int messageId, String message) throws WifiAwareSnippetException {
        // 4. send message & wait for send status
        checkDiscoverySession();
        mDiscoverySession.sendMessage(mPeerHandle, messageId, message.getBytes(UTF_8));
    }

    private void checkDiscoverySession() throws WifiAwareSnippetException {
        if (mDiscoverySession == null) {
            throw new WifiAwareSnippetException("Please call publish or subscribe method");
        }
    }

    @Rpc(description = "Accept Pairing Request")
    public void acceptPairingRequest(int pairingRequestId, @NonNull String peerDeviceAlias, @Nullable String password) throws WifiAwareSnippetException {
        // 4. send message & wait for send status
        checkDiscoverySession();
        mDiscoverySession.acceptPairingRequest(pairingRequestId, mPeerHandle, peerDeviceAlias, Characteristics.WIFI_AWARE_CIPHER_SUITE_NCS_PK_PASN_128, password);
    }

    @Rpc(description = "Accept Pairing Request")
    public void rejectPairingRequest(int pairingRequestId) throws WifiAwareSnippetException {
        // 4. send message & wait for send status
        checkDiscoverySession();
        mDiscoverySession.rejectPairingRequest(pairingRequestId, mPeerHandle);
    }

    @Rpc(description = "Accept Pairing Request")
    public void initiatePairingRequest(@NonNull String peerDeviceAlias, @Nullable String password) throws WifiAwareSnippetException {
        // 4. send message & wait for send status
        checkDiscoverySession();
        checkPeerHandler();
        mDiscoverySession.initiatePairingRequest(mPeerHandle, peerDeviceAlias, Characteristics.WIFI_AWARE_CIPHER_SUITE_NCS_PK_PASN_128, password);
    }

    private void checkPeerHandler() throws WifiAwareSnippetException {
        if (mPeerHandle == null) {
            throw new WifiAwareSnippetException("Please call publish or subscribe method");
        }
    }

    @Rpc(description = "Initiate bootstrapping request")
    public void initiateBootstrappingRequest(int method) throws WifiAwareSnippetException {
        checkDiscoverySession();
        checkPeerHandler();
        mDiscoverySession.initiateBootstrappingRequest(mPeerHandle, method);
    }

    @Rpc(description = "Remove paired device")
    public void removePairedDevice(String alias) throws WifiAwareSnippetException {
        checkWifiManager();
        mWifiAwareManager.removePairedDevice(alias);
    }

    private void checkWifiManager() throws WifiAwareSnippetException {
        if (mWifiAwareManager == null) {
            throw new WifiAwareSnippetException("Please call publish or subscribe method");
        }
    }

    @AsyncRpc(description = "Get paired devices")
    public void getPairedDevices(String callBackId) throws WifiAwareSnippetException {
        checkWifiManager();
        SnippetEvent snippetEvent = new SnippetEvent(callBackId, "getPairedDevices");
        Consumer<List<String>> consumer = value -> {
            ArrayList<String> aliasList = new ArrayList<>(value);
            snippetEvent.getData().putStringArrayList("aliasList", aliasList);
            EventCache.getInstance().postEvent(snippetEvent);
        };
        mWifiAwareManager.getPairedDevices(Executors.newSingleThreadScheduledExecutor(), consumer);
    }

    /**
     * Get local port
     * https://developer.android.com/develop/connectivity/wifi/wifi-aware#create_a_connection
     */
    @Rpc(description = "Get local port")
    public int getLocalPort() throws WifiAwareSnippetException, IOException {
        ServerSocket ss = new ServerSocket(0);
        int port = ss.getLocalPort();
        return port;
    }

    @Rpc(description = "reset paired devices")
    public void resetPairedDevices() throws WifiAwareSnippetException {
        checkWifiManager();
        mWifiAwareManager.resetPairedDevices();
    }

    @Rpc(description = "close discover session")
    public void closeDiscoverSession() throws WifiAwareSnippetException {
        if (mDiscoverySession != null) {
            mDiscoverySession.close();
            mDiscoverySession = null;
        }
    }

    @Rpc(description = "close Wi-Fi aware Session")
    public void closeWifiAwareSession() throws WifiAwareSnippetException {
        if (mWifiAwareSession != null) {
            mWifiAwareSession.close();
            mWifiAwareSession = null;
        }
    }
}
