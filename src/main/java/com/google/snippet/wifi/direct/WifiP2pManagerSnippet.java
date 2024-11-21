/*
 * Copyright (C) 2023 The Android Open Source Project
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
import android.os.Bundle;

import androidx.annotation.NonNull;
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


/** Snippet class for WifiP2pManager. */
public class WifiP2pManagerSnippet implements Snippet {
    private final Context mContext;
    private final IntentFilter mIntentFilter;
    private final WifiP2pManager mP2pManager;

    private Instrumentation mInstrumentation =
            InstrumentationRegistry.getInstrumentation();
    private UiDevice mUiDevice = UiDevice.getInstance(mInstrumentation);

    private WifiP2pManager.Channel mChannel = null;
    private WifiP2pStateChangedReceiver mStateChangedReceiver = null;


    private static class WifiP2pManagerException extends Exception {
        WifiP2pManagerException(String message) {
            super(message);
        }
    }

    public WifiP2pManagerSnippet() {
        mContext = ApplicationProvider.getApplicationContext();

        checkPermissions(mContext, Manifest.permission.ACCESS_WIFI_STATE,
                Manifest.permission.CHANGE_WIFI_STATE, Manifest.permission.ACCESS_FINE_LOCATION,
                Manifest.permission.NEARBY_WIFI_DEVICES
                // We cannot get following permissions which are mentioned in some WifiP2pManager
                // system APIs.
                // Manifest.permission.NETWORK_SETTINGS,
                // Manifest.permission.NETWORK_STACK,
                // Manifest.permission.OVERRIDE_WIFI_CONFIG,
                // Manifest.permission.READ_WIFI_CREDENTIAL
        );

        mP2pManager = mContext.getSystemService(WifiP2pManager.class);

        mIntentFilter = new IntentFilter();
        mIntentFilter.addAction(WifiP2pManager.WIFI_P2P_STATE_CHANGED_ACTION);
        mIntentFilter.addAction(WifiP2pManager.WIFI_P2P_PEERS_CHANGED_ACTION);
        mIntentFilter.addAction(WifiP2pManager.WIFI_P2P_CONNECTION_CHANGED_ACTION);
        mIntentFilter.addAction(WifiP2pManager.WIFI_P2P_THIS_DEVICE_CHANGED_ACTION);
    }

    /** Register the application with the Wi-Fi framework. */
    @AsyncRpc(description = "Register the application with the Wi-Fi framework.")
    public void p2pInitialize(String callbackId) throws WifiP2pManagerException {
        if (mChannel != null) {
            throw new WifiP2pManagerException(
                    "Channel has already created, please close current section before initliaze a "
                            + "new one.");
        }
        mChannel = mP2pManager.initialize(mContext, mContext.getMainLooper(), null);
        mStateChangedReceiver = new WifiP2pStateChangedReceiver(callbackId, mIntentFilter);
    }

    /** Request the device information in the form of WifiP2pDevice. */
    @AsyncRpc(description = "Request the device information in the form of WifiP2pDevice.")
    public void p2pRequestDeviceInfo(String callbackId) throws WifiP2pManagerException {
        checkChannel();
        checkP2pManager();
        mP2pManager.requestDeviceInfo(mChannel, new DeviceInfoListener(callbackId));
    }

    /**
     * Initiate peer discovery. A discovery process involves scanning for available Wi-Fi peers for
     * the purpose of establishing a connection.
     */
    @AsyncRpc(
            description = "Initiate peer discovery. A discovery process involves scanning for "
                    + "available Wi-Fi peers for the purpose of establishing a connection.")
    public void p2pDiscoverPeers(String callbackId) throws WifiP2pManagerException {
        checkChannel();
        checkP2pManager();
        mP2pManager.discoverPeers(mChannel, new ActionListener(callbackId));
    }

    /** Request p2p group information. */
    @AsyncRpc(description = "Request p2p group information.")
    public void p2pRequestGroupInfo(String callbackId) throws WifiP2pManagerException {
        checkChannel();
        checkP2pManager();
        mP2pManager.requestGroupInfo(mChannel, new GroupInfoListener(callbackId));
    }

    /** Cancel any ongoing p2p group negotiation. */
    @AsyncRpc(description = "Cancel any ongoing p2p group negotiation.")
    public void p2pCancelConnect(String callbackId) throws WifiP2pManagerException {
        checkChannel();
        checkP2pManager();
        ActionListener listener = new ActionListener(callbackId);
        mP2pManager.cancelConnect(mChannel, listener);
    }

    /** Stop current ongoing peer discovery. */
    @AsyncRpc(description = "Stop current ongoing peer discovery.")
    public void p2pStopPeerDiscovery(String callbackId) throws WifiP2pManagerException {
        checkChannel();
        checkP2pManager();
        ActionListener listener = new ActionListener(callbackId);
        mP2pManager.stopPeerDiscovery(mChannel, listener);
    }

    /**
     * Close the current P2P connection and indicate to the P2P service that connections created by
     * the app can be removed.
     */
    @Rpc(
            description =
                    "Close the current P2P connection and indicate to the P2P service that"
                            + " connections created by the app can be removed.")
    public void p2pClose() {
        if (mChannel == null) {
            Log.d("Channel has already closed, skip" + " WifiP2pManager.Channel.close()");
            return;
        }
        mChannel.close();
        mChannel = null;
        mStateChangedReceiver.close();
        mStateChangedReceiver = null;
    }

    /** Request the current list of peers. */
    @AsyncRpc(description = "Request the current list of peers.")
    public void p2pRequestPeers(String callbackId) throws WifiP2pManagerException {
        checkChannel();
        checkP2pManager();
        mP2pManager.requestPeers(mChannel, new WifiP2pPeerListListener(callbackId));
    }

    /** Create a p2p group with the current device as the group owner. */
    @AsyncRpc(description = "Create a p2p group with the current device as the group owner.")
    public void p2pCreateGroup(String callbackId, @RpcOptional JSONObject wifiP2pConfig)
            throws JSONException, WifiP2pManagerException {
        checkChannel();
        checkP2pManager();
        ActionListener actionListener = new ActionListener(callbackId);
        if (wifiP2pConfig == null) {
            mP2pManager.createGroup(mChannel, actionListener);
        } else {
            mP2pManager.createGroup(
                    mChannel, JsonDeserializer.jsonToWifiP2pConfig(wifiP2pConfig), actionListener);
        }
    }

    /** Start a p2p connection to a device with the specified configuration. */
    @AsyncRpc(description = "Start a p2p connection to a device with the specified configuration.")
    public void p2pConnect(String callbackId, JSONObject wifiP2pConfig)
            throws JSONException, WifiP2pManagerException {
        checkChannel();
        checkP2pManager();
        mP2pManager.connect(mChannel, JsonDeserializer.jsonToWifiP2pConfig(wifiP2pConfig),
                new ActionListener(callbackId));
    }

    /** Accept p2p connection invitation through clicking on UI. */
    @Rpc(description = "Accept p2p connection invitation through clicking on UI.")
    public void acceptInvitation(String deviceName) throws WifiP2pManagerException {
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
        UiObject2 acceptButton = mUiDevice.findObject(
                By.text(pattern).clazz("android.widget.Button"));
        if (acceptButton == null) {
            throw new WifiP2pManagerException(
                    "There's no accept button for the connect invitation.");
        }
        acceptButton.click();
    }

    /** Remove the current group. */
    @AsyncRpc(description = "Remove the current group.")
    public void p2pRemoveGroup(String callbackId) throws WifiP2pManagerException {
        checkChannel();
        checkP2pManager();
        mP2pManager.removeGroup(mChannel, new ActionListener(callbackId));
    }

    /** Removes all saved p2p groups. */
    @AsyncRpc(description = "")
    public void factoryReset(String callbackId) throws WifiP2pManagerException {
        checkChannel();
        checkP2pManager();
        mP2pManager.factoryReset(mChannel, new ActionListener(callbackId));
    }

    /** Requests the number of persistent p2p group. */
    @Rpc(description = "")
    public int requestPersistentGroupNum() throws Throwable {
        checkChannel();
        checkP2pManager();
        String callbackId = UUID.randomUUID().toString();
        mP2pManager.requestPersistentGroupInfo(
                mChannel, new PersistentGroupInfoListener(callbackId));

        SnippetEvent event = waitForSnippetEvent(
                callbackId, PersistentGroupInfoListener.CALLBACK_EVENT_NAME, 10000);
        return event.getData().getInt("groupNum");
    }

    /** Try to delete any persistent group. Ignore any delete errors. */
    @Rpc(description = "Try to delete any persistent group. Ignore any delete errors.")
    public void p2pDeletePersistentGroup() throws Throwable {
        String callbackId = UUID.randomUUID().toString();
        DeletePersistentGroupListener listener = null;
        try {
            for (int netid = 0; netid < 32; netid++) {
                Log.d("Try deleting persistent group with netid: " + netid);
                listener = new DeletePersistentGroupListener(callbackId, netid);
                mP2pManager.deletePersistentGroup(mChannel, netid, listener);
            }

            for (int netid = 0; netid < 32; netid++) {
                SnippetEvent event = waitForSnippetEvent(callbackId,
                        DeletePersistentGroupListener.CALLBACK_EVENT_NAME, 10000);
                Log.d("Delete group result event: " + event.getData().toString());
            }
        } catch (Exception e) {
            Log.d("Ignoring the exception thrown by deletePersistentGroup: ");
            e.printStackTrace();
        }
    }

    private class WifiP2pStateChangedReceiver extends BroadcastReceiver {
        public final String mCallbackId;

        private WifiP2pStateChangedReceiver(String callbackId, IntentFilter mIntentFilter) {
            this.mCallbackId = callbackId;
            mContext.registerReceiver(this, mIntentFilter, Context.RECEIVER_NOT_EXPORTED);
        }

        private void close() {
            mContext.unregisterReceiver(this);
        }

        @Override
        public void onReceive(Context mContext, Intent intent) {
            String action = intent.getAction();
            SnippetEvent event = new SnippetEvent(mCallbackId, action);
            switch (action) {
                case WifiP2pManager.WIFI_P2P_STATE_CHANGED_ACTION:
                    event
                            .getData()
                            .putInt(
                                    WifiP2pManager.EXTRA_WIFI_STATE,
                                    intent.getIntExtra(
                                            WifiP2pManager.EXTRA_WIFI_STATE, 0));
                    EventCache.getInstance().postEvent(event);
                    break;
                case WifiP2pManager.WIFI_P2P_PEERS_CHANGED_ACTION:
                    Log.d("Wifi P2p peers changed. Peer list in intent: "
                            + intent.getParcelableExtra(
                                    WifiP2pManager.EXTRA_P2P_DEVICE_LIST).toString());
                    mP2pManager.requestPeers(mChannel, new WifiP2pPeerListListener(mCallbackId));
                    break;
                case WifiP2pManager.WIFI_P2P_CONNECTION_CHANGED_ACTION:
                    // TODO: Check with Nate: Whether we should directly get info from intent, or we
                    // should call methods like requestConnectionInfo. Or both are OK? Both of them
                    // are mentioned in developer doc.
                    // Doc 1: https://developer.android.com/develop/connectivity/wifi/wifip2p#broadcast-receiver
                    // Doc 2: https://developer.android.com/reference/android/net/wifi/p2p/WifiP2pManager#WIFI_P2P_CONNECTION_CHANGED_ACTION
                    NetworkInfo networkInfo = intent.getParcelableExtra(
                            WifiP2pManager.EXTRA_NETWORK_INFO);
                    WifiP2pInfo p2pInfo = (WifiP2pInfo) intent.getParcelableExtra(
                            WifiP2pManager.EXTRA_WIFI_P2P_INFO);
                    WifiP2pGroup p2pGroup = (WifiP2pGroup) intent.getParcelableExtra(
                            WifiP2pManager.EXTRA_WIFI_P2P_GROUP);
                    event.getData().putBoolean(
                            "isConnected", networkInfo.isConnected());
                    event.getData().putBundle(
                            "wifiP2pInfo", BundleUtils.fromWifiP2pInfo(p2pInfo));
                    event.getData().putBundle(
                            "wifiP2pGroup", BundleUtils.fromWifiP2pGroup(p2pGroup));
                    EventCache.getInstance().postEvent(event);
                    break;
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
            event.getData().putString("callbackName", "onSuccess");
            EventCache.getInstance().postEvent(event);
        }

        @Override
        public void onFailure(int reason) {
            SnippetEvent event = new SnippetEvent(mCallbackId, CALLBACK_EVENT_NAME);
            event.getData().putString("callbackName", "onFailure");
            event.getData().putInt("reason", reason);
            EventCache.getInstance().postEvent(event);
        }
    }

    private static class DeviceInfoListener implements WifiP2pManager.DeviceInfoListener {
        private final String mCallbackId;

        DeviceInfoListener(String callbackId) {
            this.mCallbackId = callbackId;
        }

        @Override
        public void onDeviceInfoAvailable(WifiP2pDevice device) {
            if (device == null) {
                return;
            }
            SnippetEvent event = new SnippetEvent(mCallbackId, "WifiP2pOnDeviceInfoAvailable");
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

    private static class DeletePersistentGroupListener implements WifiP2pManager.ActionListener {
        public static final String CALLBACK_EVENT_NAME = "DeletePersistentGroupCallback";

        private final String mCallbackId;
        private final int mNetworkId;

        DeletePersistentGroupListener(String callbackId, int networkId) {
            this.mCallbackId = callbackId;
            this.mNetworkId = networkId;
        }

        @Override
        public void onSuccess() {
            SnippetEvent event = new SnippetEvent(mCallbackId, CALLBACK_EVENT_NAME);
            event.getData().putString("result", "onSuccess");
            event.getData().putInt("networkId", this.mNetworkId);
            EventCache.getInstance().postEvent(event);
        }

        @Override
        public void onFailure(int reason) {
            SnippetEvent event = new SnippetEvent(mCallbackId, CALLBACK_EVENT_NAME);
            event.getData().putString("result", "onFailure");
            event.getData().putInt("networkId", this.mNetworkId);
            event.getData().putInt("reason", reason);
            EventCache.getInstance().postEvent(event);
        }
    }

    private static class PersistentGroupInfoListener implements
            WifiP2pManager.PersistentGroupInfoListener {
        public static final String CALLBACK_EVENT_NAME = "PersistentGroupInfoCallback";

        private final String mCallbackId;

        PersistentGroupInfoListener(String callbackId) {
            this.mCallbackId = callbackId;
        }

        @Override
        public void onPersistentGroupInfoAvailable(@NonNull WifiP2pGroupList groups) {
            Log.d("onPersistentGroupInfoAvailable: " + groups.toString());
            SnippetEvent event = new SnippetEvent(mCallbackId, CALLBACK_EVENT_NAME);
            event.getData().putInt("groupNum", groups.getGroupList().size());
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
                "Channel is not created, please call 'p2pInitialize' first.");
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

    private static SnippetEvent waitForSnippetEvent(
            String callbackId, String eventName, Integer timeout) throws Throwable {
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
