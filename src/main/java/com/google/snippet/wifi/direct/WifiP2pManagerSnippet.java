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

package com.google.snippet.wifi.direct;

import android.Manifest;
import android.app.Instrumentation;
import android.content.BroadcastReceiver;
import android.content.Context;
import android.content.Intent;
import android.content.IntentFilter;
import android.content.pm.PackageManager;
import android.net.NetworkInfo;
import android.net.wifi.p2p.WifiP2pDevice;
import android.net.wifi.p2p.WifiP2pDeviceList;
import android.net.wifi.p2p.WifiP2pGroup;
import android.net.wifi.p2p.WifiP2pGroupList;
import android.net.wifi.p2p.WifiP2pInfo;
import android.net.wifi.p2p.WifiP2pManager;
import android.os.Build;
import android.os.Bundle;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.test.core.app.ApplicationProvider;
import androidx.test.platform.app.InstrumentationRegistry;
import androidx.test.uiautomator.By;
import androidx.test.uiautomator.UiDevice;
import androidx.test.uiautomator.UiObject2;
import androidx.test.uiautomator.Until;

import com.google.android.mobly.snippet.Snippet;
import com.google.android.mobly.snippet.event.EventCache;
import com.google.android.mobly.snippet.event.SnippetEvent;
import com.google.android.mobly.snippet.rpc.AsyncRpc;
import com.google.android.mobly.snippet.rpc.Rpc;
import com.google.android.mobly.snippet.rpc.RpcDefault;
import com.google.android.mobly.snippet.rpc.RpcOptional;
import com.google.android.mobly.snippet.util.Log;

import org.json.JSONException;
import org.json.JSONObject;

import java.util.ArrayList;
import java.util.UUID;
import java.util.concurrent.LinkedBlockingDeque;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.TimeoutException;
import java.util.regex.Pattern;


/**
 * Snippet class for WifiP2pManager.
 */
public class WifiP2pManagerSnippet implements Snippet {
    private static final int TIMEOUT_SHORT_MS = 10000;
    public static final String EVENT_CALLBACK_NAME = "callbackName";
    public static final String EVENT_VALUE_ON_SUCCESS = "onSuccess";
    public static final String EVENT_VALUE_ON_FAILURE = "onSuccess";

    private final Context mContext;
    private final IntentFilter mIntentFilter;
    private final WifiP2pManager mP2pManager;

    private Instrumentation mInstrumentation = InstrumentationRegistry.getInstrumentation();
    private UiDevice mUiDevice = UiDevice.getInstance(mInstrumentation);

    private WifiP2pManager.Channel mChannel = null;
    private WifiP2pStateChangedReceiver mStateChangedReceiver = null;


    private static class WifiP2pManagerException extends Exception {
        WifiP2pManagerException(String message) {
            super(message);
        }
    }

    public WifiP2pManagerSnippet() {
        if (Build.VERSION.SDK_INT >= 29) {
            Log.d("Elevating permission require to enable support for privileged operation in " +
                    "Android Q+");
            mInstrumentation.getUiAutomation().adoptShellPermissionIdentity();
        }

        mContext = ApplicationProvider.getApplicationContext();

        checkPermissions(mContext, Manifest.permission.ACCESS_WIFI_STATE,
                Manifest.permission.CHANGE_WIFI_STATE, Manifest.permission.ACCESS_FINE_LOCATION,
                Manifest.permission.NEARBY_WIFI_DEVICES);

        mP2pManager = mContext.getSystemService(WifiP2pManager.class);

        mIntentFilter = new IntentFilter();
        mIntentFilter.addAction(WifiP2pManager.WIFI_P2P_STATE_CHANGED_ACTION);
        mIntentFilter.addAction(WifiP2pManager.WIFI_P2P_PEERS_CHANGED_ACTION);
        mIntentFilter.addAction(WifiP2pManager.WIFI_P2P_CONNECTION_CHANGED_ACTION);
        mIntentFilter.addAction(WifiP2pManager.WIFI_P2P_THIS_DEVICE_CHANGED_ACTION);

        mStateChangedReceiver = new WifiP2pStateChangedReceiver();
        mContext.registerReceiver(mStateChangedReceiver, mIntentFilter,
                Context.RECEIVER_NOT_EXPORTED);
    }

    /**
     * Start capturing wifi p2p intents in Mobly event cache.
     */
    @AsyncRpc(description = "")
    public void wifiP2pCaptureP2pIntents(String callbackId) {
        mStateChangedReceiver.enableCapturingIntentToEventCache(callbackId);
    }

    // TODO: Rename all methods from starting with p2p to wifiP2p

    /**
     * Register the application with the Wi-Fi framework.
     */
    @Rpc(description = "Register the application with the Wi-Fi framework.")
    public void wifiP2pInitialize() throws WifiP2pManagerException {
        if (mChannel != null) {
            throw new WifiP2pManagerException(
                    "Channel has already created, please close current section before initliaze a" +
                            " " + "new one.");

        }
        checkP2pManager();
        mChannel = mP2pManager.initialize(mContext, mContext.getMainLooper(), null);
    }

    /**
     * Request the device information in the form of WifiP2pDevice.
     */
    @AsyncRpc(description = "Request the device information in the form of WifiP2pDevice.")
    public void wifiP2pRequestDeviceInfo(String callbackId) throws WifiP2pManagerException {
        checkChannel();
        mP2pManager.requestDeviceInfo(mChannel, new DeviceInfoListener(callbackId));
    }

    /**
     * Initiate peer discovery. A discovery process involves scanning for available Wi-Fi peers for
     * the purpose of establishing a connection.
     */
    @Rpc(description = "Initiate peer discovery. A discovery process involves scanning for " +
            "available Wi-Fi peers for the purpose of establishing a connection.")
    public void wifiP2pDiscoverPeers() throws Throwable {
        checkChannel();
        String callbackId = UUID.randomUUID().toString();
        mP2pManager.discoverPeers(mChannel, new ActionListener(callbackId));
        checkActionListenerSuccess(callbackId, false);
    }

    // TODO: move position of this function
    private void checkActionListenerSuccess(String callbackId, boolean ignoreError)
            throws Throwable {
        SnippetEvent event = waitForSnippetEvent(callbackId, ActionListener.CALLBACK_EVENT_NAME,
                TIMEOUT_SHORT_MS);
        if (ignoreError) {
            return;
        }
        String result = event.getData().getString(EVENT_CALLBACK_NAME);
        if (result != EVENT_VALUE_ON_SUCCESS) {
            throw new WifiP2pManagerException(
                    "discoverPeers failed with event: " + event.getData().toString());
        }
    }

    /**
     * Cancel any ongoing p2p group negotiation.
     */
    @Rpc(description = "Cancel any ongoing p2p group negotiation.")
    public void wifiP2pCancelConnect(@RpcDefault("false") Boolean ignoreError) throws Throwable {
        checkChannel();
        String callbackId = UUID.randomUUID().toString();
        mP2pManager.cancelConnect(mChannel, new ActionListener((callbackId)));
        checkActionListenerSuccess(callbackId, ignoreError);
    }

    /**
     * Stop current ongoing peer discovery.
     */
    @Rpc(description = "Stop current ongoing peer discovery.")
    public void wifiP2pStopPeerDiscovery(@RpcDefault("false") Boolean ignoreError)
            throws Throwable {
        checkChannel();
        String callbackId = UUID.randomUUID().toString();
        mP2pManager.stopPeerDiscovery(mChannel, new ActionListener(callbackId));
        checkActionListenerSuccess(callbackId, ignoreError);
    }

    /**
     * Close the current P2P connection and indicate to the P2P service that connections created by
     * the app can be removed.
     */
    @Rpc(description = "Close the current P2P connection and indicate to the P2P service that" +
            " connections created by the app can be removed.")
    public void p2pClose() {
        if (mChannel == null) {
            Log.d("Channel has already closed, skip" + " WifiP2pManager.Channel.close()");
            return;
        }
        mChannel.close();
        mChannel = null;
        mStateChangedReceiver.disableCapturingIntentToEventCache();
    }

    /**
     * Create a p2p group with the current device as the group owner.
     */
    @AsyncRpc(description = "Create a p2p group with the current device as the group owner.")
    public void wifiP2pCreateGroup(String callbackId, @RpcOptional JSONObject wifiP2pConfig)
            throws JSONException, WifiP2pManagerException {
        checkChannel();
        ActionListener actionListener = new ActionListener(callbackId);
        if (wifiP2pConfig == null) {
            mP2pManager.createGroup(mChannel, actionListener);
        } else {
            mP2pManager.createGroup(mChannel, JsonDeserializer.jsonToWifiP2pConfig(wifiP2pConfig),
                    actionListener);
        }
    }

    /**
     * Start a p2p connection to a device with the specified configuration.
     */
    @Rpc(description = "Start a p2p connection to a device with the specified configuration.")
    public void wifiP2pConnect(JSONObject wifiP2pConfig) throws Throwable {
        checkChannel();
        String callbackId = UUID.randomUUID().toString();
        mP2pManager.connect(mChannel, JsonDeserializer.jsonToWifiP2pConfig(wifiP2pConfig),
                new ActionListener(callbackId));
        checkActionListenerSuccess(callbackId, /* ignoreError */ false);
    }

    /**
     * Accept p2p connection invitation through clicking on UI.
     */
    @Rpc(description = "Accept p2p connection invitation through clicking on UI.")
    public void wifiP2pAcceptInvitation(String deviceName) throws WifiP2pManagerException {
        if (!mUiDevice.wait(Until.hasObject(By.text("Invitation to connect")), 30000)) {
            throw new WifiP2pManagerException(
                    "Expected connect invitation did not occur within timeout.");
        }
        if (!mUiDevice.wait(Until.hasObject(By.text(deviceName)), 5000)) {
            throw new WifiP2pManagerException(
                    "The connect invitation is not triggered by expected peer device.");
        }
        Pattern pattern = Pattern.compile("(ACCEPT|OK|Accept)");
        if (!mUiDevice.wait(Until.hasObject(By.text(pattern).clazz("android.widget.Button")),
                30000)) {
            throw new WifiP2pManagerException("Accept button did not occur within timeout.");
        }
        UiObject2 acceptButton =
                mUiDevice.findObject(By.text(pattern).clazz("android.widget.Button"));
        if (acceptButton == null) {
            throw new WifiP2pManagerException(
                    "There's no accept button for the connect " + "invitation.");
        }
        acceptButton.click();
    }

    /**
     * Remove the current group.
     */
    @Rpc(description = "Remove the current group.")
    public void wifiP2pRemoveGroup(@RpcDefault("false") Boolean ignoreError) throws Throwable {
        checkChannel();
        String callbackId = UUID.randomUUID().toString();
        mP2pManager.removeGroup(mChannel, new ActionListener(callbackId));
        checkActionListenerSuccess(callbackId, ignoreError);
    }

    /**
     * Request the number of persistent p2p group.
     */
    @AsyncRpc(description = "Request the number of persistent p2p group")
    public void wifiP2pRequestPersistentGroupInfo(String callbackId) throws Throwable {
        checkChannel();
        mP2pManager.requestPersistentGroupInfo(mChannel,
                new PersistentGroupInfoListener(callbackId));
    }

    /**
     * Delete the persistent p2p group with the given network ID.
     */
    @Rpc(description = "")
    public void wifiP2pDeletePersistentGroup(int networkId,
                                             @RpcDefault("false") Boolean ignoreError)
            throws Throwable {
        checkChannel();
        String callbackId = UUID.randomUUID().toString();
        mP2pManager.deletePersistentGroup(mChannel, networkId, new ActionListener(callbackId));
        checkActionListenerSuccess(callbackId, ignoreError);
    }

    private class WifiP2pStateChangedReceiver extends BroadcastReceiver {
        private @Nullable String mCallbackId;

        private WifiP2pStateChangedReceiver() {
            this.mCallbackId = null;
        }

        public void enableCapturingIntentToEventCache(String callbackID) {
            mCallbackId = callbackID;
        }

        public void disableCapturingIntentToEventCache() {
            mCallbackId = null;
        }

        @Override
        public void onReceive(Context mContext, Intent intent) {
            String action = intent.getAction();
            SnippetEvent event = null;
            if (mCallbackId != null) {
                event = new SnippetEvent(mCallbackId, action);
            }
            String logPrefix = "Got intent: action=" + action + ", ";
            switch (action) {
                case WifiP2pManager.WIFI_P2P_STATE_CHANGED_ACTION:
                    int wifiP2pState = intent.getIntExtra(WifiP2pManager.EXTRA_WIFI_STATE, 0);
                    Log.d(logPrefix + "wifiP2pState=" + wifiP2pState);
                    if (event != null) {
                        event.getData().putInt(WifiP2pManager.EXTRA_WIFI_STATE, wifiP2pState);
                    }
                    break;
                case WifiP2pManager.WIFI_P2P_PEERS_CHANGED_ACTION:
                    WifiP2pDeviceList peerList = (WifiP2pDeviceList) intent.getParcelableExtra(
                            WifiP2pManager.EXTRA_P2P_DEVICE_LIST);
                    Log.d(logPrefix + "p2pPeerList=" + peerList.toString());
                    if (event != null) {
                        event.getData().putParcelableArrayList("peers",
                                BundleUtils.fromWifiP2pDeviceList(peerList));
                    }
                    break;
                case WifiP2pManager.WIFI_P2P_CONNECTION_CHANGED_ACTION:
                    NetworkInfo networkInfo =
                            intent.getParcelableExtra(WifiP2pManager.EXTRA_NETWORK_INFO);
                    WifiP2pInfo p2pInfo = (WifiP2pInfo) intent.getParcelableExtra(
                            WifiP2pManager.EXTRA_WIFI_P2P_INFO);
                    WifiP2pGroup p2pGroup = (WifiP2pGroup) intent.getParcelableExtra(
                            WifiP2pManager.EXTRA_WIFI_P2P_GROUP);
                    Log.d(logPrefix + "networkInfo=" + String.valueOf(networkInfo) + ", p2pInfo=" +
                            String.valueOf(p2pInfo) + ", p2pGroup=" + String.valueOf(p2pGroup));
                    if (event != null) {
                        if (networkInfo != null) {
                            event.getData().putBoolean("isConnected", networkInfo.isConnected());
                        } else {
                            event.getData().putBoolean("isConnected", false);
                        }
                        event.getData()
                                .putBundle("wifiP2pInfo", BundleUtils.fromWifiP2pInfo(p2pInfo));
                        event.getData()
                                .putBundle("wifiP2pGroup", BundleUtils.fromWifiP2pGroup(p2pGroup));
                    }
                    break;
            }
            if (event != null) {
                EventCache.getInstance().postEvent(event);
            }
        }
    }

    private static class ActionListener implements WifiP2pManager.ActionListener {
        public static final String CALLBACK_EVENT_NAME = "WifiP2pManagerActionListenerCallback";

        private final String mCallbackId;

        ActionListener(String callbackId) {
            this.mCallbackId = callbackId;
        }

        @Override
        public void onSuccess() {
            SnippetEvent event = new SnippetEvent(mCallbackId, CALLBACK_EVENT_NAME);
            event.getData().putString("callbackName", EVENT_VALUE_ON_SUCCESS);
            EventCache.getInstance().postEvent(event);
        }

        @Override
        public void onFailure(int reason) {
            SnippetEvent event = new SnippetEvent(mCallbackId, CALLBACK_EVENT_NAME);
            event.getData().putString("callbackName", EVENT_VALUE_ON_FAILURE);
            event.getData().putInt("reason", reason);
            EventCache.getInstance().postEvent(event);
        }
    }

    private static class DeviceInfoListener implements WifiP2pManager.DeviceInfoListener {
        public static final String EVENT_NAME_ON_DEVICE_INFO = "WifiP2pOnDeviceInfoAvailable";

        private final String mCallbackId;

        DeviceInfoListener(String callbackId) {
            this.mCallbackId = callbackId;
        }

        @Override
        public void onDeviceInfoAvailable(WifiP2pDevice device) {
            if (device == null) {
                return;
            }
            SnippetEvent event = new SnippetEvent(mCallbackId, EVENT_NAME_ON_DEVICE_INFO);
            event.getData().putBundle("device", BundleUtils.fromWifiP2pDevice(device));
            EventCache.getInstance().postEvent(event);
        }
    }

    private static class WifiP2pPeerListListener implements WifiP2pManager.PeerListListener {
        private final String mCallbackId;

        WifiP2pPeerListListener(String callbackId) {
            this.mCallbackId = callbackId;
        }

        @Override
        public void onPeersAvailable(WifiP2pDeviceList newPeers) {
            Log.d("onPeersAvailable: " + newPeers.getDeviceList());
            ArrayList<Bundle> devices = BundleUtils.fromWifiP2pDeviceList(newPeers);
            SnippetEvent event = new SnippetEvent(mCallbackId, "WifiP2pOnPeersAvailable");
            event.getData().putParcelableArrayList("peers", devices);
            event.getData().putLong("timestampMs", System.currentTimeMillis());
            EventCache.getInstance().postEvent(event);
        }
    }

    public static class GroupInfoListener implements WifiP2pManager.GroupInfoListener {
        private final String mCallbackId;

        public GroupInfoListener(String callbackId) {
            this.mCallbackId = callbackId;
        }

        @Override
        public void onGroupInfoAvailable(WifiP2pGroup group) {
            SnippetEvent event = new SnippetEvent(mCallbackId, "WifiP2pOnGroupInfoAvailable");
            event.getData().putBundle("wifiP2pGroup", BundleUtils.fromWifiP2pGroup(group));
            event.getData().putLong("timestampMs", System.currentTimeMillis());
            EventCache.getInstance().postEvent(event);
        }

    }

    // private static class DeletePersistentGroupListener implements WifiP2pManager.ActionListener {
    //     public static final String CALLBACK_EVENT_NAME = "DeletePersistentGroupCallback";
    //     public static final String CALLBACK_ON_SUCCESS = "onSuccess";
    //     public static final String CALLBACK_ON_FAILURE = "onSuccess";
    //
    //     private final String mCallbackId;
    //     private final int mNetworkId;
    //
    //     DeletePersistentGroupListener(String callbackId, int networkId) {
    //         this.mCallbackId = callbackId;
    //         this.mNetworkId = networkId;
    //     }
    //
    //     @Override
    //     public void onSuccess() {
    //         SnippetEvent event = new SnippetEvent(mCallbackId, CALLBACK_EVENT_NAME);
    //         event.getData().putString("result", CALLBACK_ON_SUCCESS);
    //         event.getData().putInt("networkId", this.mNetworkId);
    //         EventCache.getInstance().postEvent(event);
    //     }
    //
    //     @Override
    //     public void onFailure(int reason) {
    //         SnippetEvent event = new SnippetEvent(mCallbackId, CALLBACK_EVENT_NAME);
    //         event.getData().putString("result", CALLBACK_ON_FAILURE);
    //         event.getData().putInt("networkId", this.mNetworkId);
    //         event.getData().putInt("reason", reason);
    //         EventCache.getInstance().postEvent(event);
    //     }
    // }

    private static class PersistentGroupInfoListener
            implements WifiP2pManager.PersistentGroupInfoListener {
        private final String mCallbackId;

        PersistentGroupInfoListener(String callbackId) {
            this.mCallbackId = callbackId;
        }

        @Override
        public void onPersistentGroupInfoAvailable(@NonNull WifiP2pGroupList groups) {
            Log.d("onPersistentGroupInfoAvailable: " + groups.toString());
            SnippetEvent event = new SnippetEvent(mCallbackId, "PersistentGroupInfoCallback");
            event.getData()
                    .putParcelableArrayList("groups", BundleUtils.fromWifiP2pGroupList(groups));
            EventCache.getInstance().postEvent(event);
        }
    }

    @Override
    public void shutdown() {
        p2pClose();
    }

    private void checkChannel() throws WifiP2pManagerException {
        if (mChannel == null) {
            throw new WifiP2pManagerException(
                    "Channel is not created, please call 'wifiP2pInitialize' first.");
        }
    }

    private void checkP2pManager() throws WifiP2pManagerException {
        if (mP2pManager == null) {
            throw new WifiP2pManagerException("Device does not support Wi-Fi Direct.");
        }
    }

    private static void checkPermissions(Context context, String... permissions) {
        for (String permission : permissions) {
            if (context.checkSelfPermission(permission) != PackageManager.PERMISSION_GRANTED) {
                throw new SecurityException(
                        "Permission denied (missing " + permission + " permission)");
            }
        }
    }

    private static SnippetEvent waitForSnippetEvent(String callbackId, String eventName,
                                                    Integer timeout) throws Throwable {
        String qId = EventCache.getQueueId(callbackId, eventName);
        LinkedBlockingDeque<SnippetEvent> q = EventCache.getInstance().getEventDeque(qId);
        SnippetEvent result;
        try {
            result = q.pollFirst(timeout, TimeUnit.MILLISECONDS);
        } catch (InterruptedException e) {
            throw e.getCause();
        }

        if (result == null) {
            throw new TimeoutException(
                    "Timed out waiting(" + timeout + " millis) for SnippetEvent: " + callbackId);
        }
        return result;
    }
}
