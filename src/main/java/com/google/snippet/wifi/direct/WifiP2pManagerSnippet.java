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
import android.net.wifi.p2p.nsd.WifiP2pServiceInfo;
import android.net.wifi.p2p.nsd.WifiP2pServiceRequest;
import android.os.Build;
import android.os.Bundle;
import android.widget.Button;

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
import com.google.snippet.wifi.direct.utils.DnsSdResponseListenerTest;
import com.google.snippet.wifi.direct.utils.DnsSdTxtRecordListenerTest;
import com.google.snippet.wifi.direct.utils.UPnPServiceResponseListenerTest;

import org.json.JSONObject;

import java.util.ArrayList;
import java.util.Objects;
import java.util.UUID;
import java.util.concurrent.LinkedBlockingDeque;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.TimeoutException;
import java.util.regex.Pattern;


/** Snippet class for WifiP2pManager. */
public class WifiP2pManagerSnippet implements Snippet {
    private static final int TIMEOUT_SHORT_MS = 10000;
    private static final String EVENT_KEY_CALLBACK_NAME = "callbackName";
    private static final String EVENT_KEY_REASON = "reason";
    private static final String EVENT_KEY_REASON_MESSAGE = "errorMessage";
    private static final String EVENT_KEY_P2P_DEVICE = "p2pDevice";
    private static final String EVENT_KEY_P2P_INFO = "p2pInfo";
    private static final String EVENT_KEY_P2P_GROUP = "p2pGroup";
    private static final String EVENT_KEY_PEER_LIST = "peerList";
    private static final String ACTION_LISTENER_ON_SUCCESS = "onSuccess";
    public static final String ACTION_LISTENER_ON_FAILURE = "onFailure";
    public static final String  PIN_VALUE_RESOURCE_ID = "com.google.android.wifi"
            + ".resources:id/value";

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
        if (Build.VERSION.SDK_INT >= 29) {
            Log.d("Elevating permission require to enable support for privileged operation in "
                    + "Android Q+");
            mInstrumentation.getUiAutomation().adoptShellPermissionIdentity();
        }

        mContext = ApplicationProvider.getApplicationContext();

        checkPermissions(mContext, Manifest.permission.ACCESS_WIFI_STATE,
                Manifest.permission.CHANGE_WIFI_STATE, Manifest.permission.ACCESS_FINE_LOCATION,
                Manifest.permission.NEARBY_WIFI_DEVICES
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
    public void wifiP2pInitialize(String callbackId) throws WifiP2pManagerException {
        if (mChannel != null) {
            throw new WifiP2pManagerException(
                    "Channel has already created, please close current section before initliaze a "
                            + "new one.");

        }
        checkP2pManager();
        mStateChangedReceiver = new WifiP2pStateChangedReceiver(callbackId);
        mContext.registerReceiver(
                mStateChangedReceiver, mIntentFilter, Context.RECEIVER_NOT_EXPORTED);
        mChannel = mP2pManager.initialize(mContext, mContext.getMainLooper(), null);
    }

    /** Request the device information in the form of WifiP2pDevice. */
    @AsyncRpc(description = "Request the device information in the form of WifiP2pDevice.")
    public void wifiP2pRequestDeviceInfo(String callbackId) throws WifiP2pManagerException {
        checkChannel();
        mP2pManager.requestDeviceInfo(mChannel, new DeviceInfoListener(callbackId));
    }

    /**
     * Initiate peer discovery. A discovery process involves scanning for available Wi-Fi peers for
     * the purpose of establishing a connection.
     *
     * @throws Throwable If this failed to initiate discovery, or the action timed out.
     */
    @AsyncRpc(
            description = "Initiate peer discovery. A discovery process involves scanning for "
                    + "available Wi-Fi peers for the purpose of establishing a connection.")
    public void wifiP2pDiscoverPeers(String callbackId) throws Throwable {
        checkChannel();
        mP2pManager.discoverPeers(mChannel, new WifiP2pActionListener(callbackId));
    }

    /**
     * Check if the service request is null.
     */
    private void checkServiceRequest(WifiP2pServiceRequest servRequest) throws Throwable {
        if (servRequest == null) {
            throw new WifiP2pManagerException("servRequest info is null");
        }
    }

    /**
     * Add service request to the device. {@link
     * WifiP2pManager#addServiceRequest(WifiP2pManager.Channel, WifiP2pServiceRequest,
     * WifiP2pManager.ActionListener)}
     */
    @AsyncRpc(description = "Get p2p service request")
    public void p2pAddServiceRequest(
            String callbackId, @RpcOptional JSONObject wifiP2pServiceRequest
    ) throws Throwable {
        checkLocationAndNearbyWifiPermissions();
        checkChannel();
        WifiP2pServiceRequest servRequest = JsonDeserializer.jsonToWifiP2pServiceRequest(
                wifiP2pServiceRequest);
        checkServiceRequest(servRequest);
        mP2pManager.addServiceRequest(mChannel, servRequest, new ActionListener(callbackId));
        verifyActionListenerSucceed(callbackId);
    }

    /**
     * Method to set UPnP service response listener
     */
    @AsyncRpc(description = "Set UPnP service response listener.")
    public void setUpnpServiceResponseListener(String callbackId, String targetAddress)
            throws Throwable {
        checkLocationAndNearbyWifiPermissions();
        checkChannel();
        UPnPServiceResponseListenerTest upnpListener = new UPnPServiceResponseListenerTest(
                callbackId, targetAddress);
        mP2pManager.setUpnpServiceResponseListener(mChannel, upnpListener);
    }

    /**
     * Method to set DNS-SD service response listeners
     */
    @AsyncRpc(description = "Set DNS-SD service response listeners.")
    public void setDnsSdResponseListeners(String callbackId, String targetAddress)
            throws Throwable {
        checkLocationAndNearbyWifiPermissions();
        checkChannel();
        DnsSdResponseListenerTest dnsListener = new DnsSdResponseListenerTest(
                callbackId, targetAddress);
        DnsSdTxtRecordListenerTest txtListener = new DnsSdTxtRecordListenerTest(
                callbackId, targetAddress);

        mP2pManager.setDnsSdResponseListeners(mChannel, dnsListener, txtListener);
    }


    /**
     * Cancel any ongoing p2p group negotiation.
     *
     * @return The event posted by the callback methods of {@link ActionListener}.
     */
    @Rpc(description = "Cancel any ongoing p2p group negotiation.")
    public Bundle wifiP2pCancelConnect() throws Throwable {
        checkChannel();
        String callbackId = UUID.randomUUID().toString();
        mP2pManager.cancelConnect(mChannel, new ActionListener((callbackId)));
        return waitActionListenerResult(callbackId);
    }

    /**
     * Stop current ongoing peer discovery.
     *
     * @return The event posted by the callback methods of {@link ActionListener}.
     */
    @Rpc(description = "Stop current ongoing peer discovery.")
    public Bundle wifiP2pStopPeerDiscovery() throws Throwable {
        checkChannel();
        String callbackId = UUID.randomUUID().toString();
        mP2pManager.stopPeerDiscovery(mChannel, new ActionListener(callbackId));
        return waitActionListenerResult(callbackId);
    }

    private void checkLocationAndNearbyWifiPermissions() {
        checkPermissions(mContext, Manifest.permission.ACCESS_FINE_LOCATION,
                Manifest.permission.NEARBY_WIFI_DEVICES
        );
    }

    /**
     * Check if the service info is null.
     */
    private void checkServiceInfo(WifiP2pServiceInfo servInfo) throws Throwable {
        if (servInfo == null) {
            throw new WifiP2pManagerException("Service info is null");
        }
    }


    /**
     * Add local service to the device.
     *
     * @param wifiServiceParams Look at constants.py -> WifiP2pServiceType
     */
    @AsyncRpc(description = "Add local service")
    public void wifiP2pAddLocalService(String callbackId, @RpcOptional JSONObject wifiServiceParams)
            throws Throwable {
        checkLocationAndNearbyWifiPermissions();
        checkChannel();
        WifiP2pServiceInfo servInfo = JsonDeserializer.jsonToWifiP2pServiceInfo(wifiServiceParams);
        checkServiceInfo(servInfo);
        mP2pManager.addLocalService(mChannel, servInfo, new WifiP2pActionListener(callbackId));

    }


    /**
     * Create a p2p group with the current device as the group owner.
     *
     * @throws Throwable If this failed to initiate discovery, or the action timed out.
     */
    @AsyncRpc(description = "Create a p2p group with the current device as the group owner.")
    public void wifiP2pCreateGroup(String callbackId, @RpcOptional JSONObject wifiP2pConfig)
            throws Throwable {
        checkLocationAndNearbyWifiPermissions();
        checkChannel();
        WifiP2pActionListener actionListener = new WifiP2pActionListener(callbackId);
        if (wifiP2pConfig == null) {
            mP2pManager.createGroup(mChannel, actionListener);
        } else {
            mP2pManager.createGroup(
                    mChannel, JsonDeserializer.jsonToWifiP2pConfig(wifiP2pConfig), actionListener);
        }
    }

    /**
     * Start a p2p connection to a device with the specified configuration.
     *
     * @throws Throwable If this failed to initiate discovery, or the action timed out.
     */
    @Rpc(description = "Start a p2p connection to a device with the specified configuration.")
    public void wifiP2pConnect(JSONObject wifiP2pConfig) throws Throwable {
        checkChannel();
        String callbackId = UUID.randomUUID().toString();
        mP2pManager.connect(mChannel, JsonDeserializer.jsonToWifiP2pConfig(wifiP2pConfig),
                new ActionListener(callbackId));
        verifyActionListenerSucceed(callbackId);
    }

    /** Accept p2p connection invitation through clicking on UI. */
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
                30000
        )) {
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

    /**
     * Generates a WPS PIN for use in P2P connection.
     * @param deviceName The device name to accept the connection invitation.
     * @return The generated PIN as a String.
     */
    @Rpc(description = "Get the PIN code for Wi-Fi p2p connection.")
    public String wifiP2pGetPinCode(String deviceName) throws Throwable {
        // Wait for the 'Invitation sent' dialog to appear
        if (!mUiDevice.wait(Until.hasObject(By.text("Invitation sent")), 30000)) {
            throw new WifiP2pManagerException(
                    "Invitation sent dialog did not appear within timeout.");
        }
        if (!mUiDevice.wait(Until.hasObject(By.text(deviceName)), 5000)) {
            throw new WifiP2pManagerException(
                    "The connect invitation is " + "not triggered by expected peer device.");
        }
        // Find the 'PIN:' label
        UiObject2 pinLabel = mUiDevice.findObject(By.text("PIN:"));
        if (pinLabel == null) {
            throw new WifiP2pManagerException("PIN label not found.");
        }
        // Get the sibling UI element that contains the PIN code
        UiObject2 pinValue = pinLabel.getParent().findObject(By.res(PIN_VALUE_RESOURCE_ID));
        String pinCode = pinValue.getText();
        Log.d("Retrieved PIN code: " + pinCode);
        // Click 'OK' to close the PIN code alert
        UiObject2 okButton = mUiDevice.findObject(By.text("OK").clazz(Button.class));

        if (okButton == null) {
            throw new WifiP2pManagerException("OK button not found to close the PIN code alert.");
        }
        okButton.click();
        Log.d("Clicked 'OK' to close the PIN code alert.");
        Log.d("Got WPS PIN: " + pinCode);
        return pinCode;
    }

    /**
     * Enters the given WPS PIN to accept a P2P connection invitation.
     *
     * @param pinCode The WPS PIN to enter.
     * @param deviceName The device name to accept the connection invitation.
     */
    @Rpc(description = "Enter the WPS PIN to accept a P2P connection invitation.")
    public void wifiP2pEnterPin(String pinCode, String deviceName) throws WifiP2pManagerException {
        // Wait for the 'Invitation to connect' dialog to appear
        if (!mUiDevice.wait(Until.hasObject(By.textContains("Invitation to connect")), 30000)) {
            throw new WifiP2pManagerException(
                    "Invitation to connect dialog did not appear within timeout.");
        }
        if (!mUiDevice.wait(Until.hasObject(By.text(deviceName)), 5000)) {
            throw new WifiP2pManagerException(
                    "The connect invitation is not triggered by expected peer device.");
        }
        // Find the PIN entry field
        UiObject2 pinEntryField = mUiDevice.findObject(By.focused(true));
        if (pinEntryField == null) {
            throw new WifiP2pManagerException("PIN entry field not found.");
        }
        // Enter the PIN code
        pinEntryField.setText(pinCode);
        Log.d("Entered PIN code: " + pinCode);
        // Find and click the 'ACCEPT' or 'OK' button
        Pattern acceptPattern = Pattern.compile("(ACCEPT|OK|Accept)", Pattern.CASE_INSENSITIVE);
        UiObject2 acceptButton = mUiDevice.findObject(By.clazz(Button.class).text(acceptPattern));
        if (acceptButton == null) {
            throw new WifiP2pManagerException("Accept button not found.");
        }
        acceptButton.click();
        Log.d("Accepted the connection.");
    }

    /**
     * Remove the current p2p group.
     *
     * @return The event posted by the callback methods of {@link ActionListener}.
     */
    @Rpc(description = "Remove the current p2p group.")
    public Bundle wifiP2pRemoveGroup() throws Throwable {
        checkChannel();
        String callbackId = UUID.randomUUID().toString();
        mP2pManager.removeGroup(mChannel, new ActionListener(callbackId));
        return waitActionListenerResult(callbackId);
    }

    /**
     * Request the number of persistent p2p group.
     */
    @AsyncRpc(description = "Request the number of persistent p2p group")
    public void wifiP2pRequestPersistentGroupInfo(String callbackId) throws Throwable {
        checkChannel();
        mP2pManager.requestPersistentGroupInfo(
                mChannel, new PersistentGroupInfoListener(callbackId));
    }

    /**
     * Delete the persistent p2p group with the given network ID.
     *
     * @return The event posted by the callback methods of {@link ActionListener}.
     */
    @Rpc(description = "Delete the persistent p2p group with the given network ID.")
    public Bundle wifiP2pDeletePersistentGroup(int networkId) throws Throwable {
        checkChannel();
        String callbackId = UUID.randomUUID().toString();
        mP2pManager.deletePersistentGroup(mChannel, networkId, new ActionListener(callbackId));
        return waitActionListenerResult(callbackId);
    }

    /**
     * Close the current P2P connection and indicate to the P2P service that connections created by
     * the app can be removed.
     */
    @Rpc(
            description = "Close the current P2P connection and indicate to the P2P service that"
                    + " connections created by the app can be removed."
    )
    public void p2pClose() {
        if (mChannel == null) {
            Log.d("Channel has already closed, skip WifiP2pManager.Channel.close()");
            return;
        }
        mChannel.close();
        mChannel = null;
        if (mStateChangedReceiver != null) {
            mContext.unregisterReceiver(mStateChangedReceiver);
            mStateChangedReceiver = null;
        }
    }

    @Override
    public void shutdown() {
        p2pClose();
    }

    private class WifiP2pStateChangedReceiver extends BroadcastReceiver {
        private String mCallbackId;

        private WifiP2pStateChangedReceiver(@NonNull String callbackId) {
            this.mCallbackId = callbackId;
        }

        @Override
        public void onReceive(Context mContext, Intent intent) {
            String action = intent.getAction();
            SnippetEvent event = new SnippetEvent(mCallbackId, action);
            String logPrefix = "Got intent: action=" + action + ", ";
            switch (action) {
                case WifiP2pManager.WIFI_P2P_STATE_CHANGED_ACTION:
                    int wifiP2pState = intent.getIntExtra(WifiP2pManager.EXTRA_WIFI_STATE, 0);
                    Log.d(logPrefix + "wifiP2pState=" + wifiP2pState);
                    event.getData().putInt(WifiP2pManager.EXTRA_WIFI_STATE, wifiP2pState);
                    break;
                case WifiP2pManager.WIFI_P2P_PEERS_CHANGED_ACTION:
                    WifiP2pDeviceList peerList = (WifiP2pDeviceList) intent.getParcelableExtra(
                            WifiP2pManager.EXTRA_P2P_DEVICE_LIST);
                    Log.d(logPrefix + "p2pPeerList=" + peerList.toString());
                    event.getData().putParcelableArrayList(
                            EVENT_KEY_PEER_LIST, BundleUtils.fromWifiP2pDeviceList(peerList));
                    break;
                case WifiP2pManager.WIFI_P2P_CONNECTION_CHANGED_ACTION:
                    NetworkInfo networkInfo = intent.getParcelableExtra(
                            WifiP2pManager.EXTRA_NETWORK_INFO);
                    WifiP2pInfo p2pInfo = (WifiP2pInfo) intent.getParcelableExtra(
                            WifiP2pManager.EXTRA_WIFI_P2P_INFO);
                    WifiP2pGroup p2pGroup = (WifiP2pGroup) intent.getParcelableExtra(
                            WifiP2pManager.EXTRA_WIFI_P2P_GROUP);
                    Log.d(logPrefix + "networkInfo=" + String.valueOf(networkInfo) + ", p2pInfo="
                            + String.valueOf(p2pInfo) + ", p2pGroup=" + String.valueOf(p2pGroup)
                    );
                    if (networkInfo != null) {
                        event.getData().putBoolean(
                                "isConnected", networkInfo.isConnected());
                    } else {
                        event.getData().putBoolean("isConnected", false);
                    }
                    event.getData().putBundle(
                            EVENT_KEY_P2P_INFO, BundleUtils.fromWifiP2pInfo(p2pInfo));
                    event.getData().putBundle(
                            EVENT_KEY_P2P_GROUP, BundleUtils.fromWifiP2pGroup(p2pGroup));
                    break;
            }
            EventCache.getInstance().postEvent(event);
        }
    }

    /**
     * Gets the current method name.
     *
     * @return the current method name
     */
    private static String getCurrentMethodName() {
        StackTraceElement[] stackTrace = Thread.currentThread().getStackTrace();

        for (int i = 0; i < stackTrace.length; i++) {
            Log.d(i + "getCurrentMethodName: " + stackTrace[i].getMethodName());
        }
        // This number can be obtained by logging the i on the
        int methodNameIndex = 5;
        return stackTrace[methodNameIndex].getMethodName(); // method name form debug
    }

    private static class WifiP2pActionListener implements WifiP2pManager.ActionListener {
        private final String actionName;
        private SnippetEvent event = null;

        WifiP2pActionListener(String callbackId) {
            this.actionName = getCurrentMethodName();
            event = new SnippetEvent(callbackId, actionName);
        }

        @Override
        public void onSuccess() {
            event.getData().putString(EVENT_KEY_CALLBACK_NAME, ACTION_LISTENER_ON_SUCCESS);
            EventCache.getInstance().postEvent(event);
            Log.d("WifiP2pActionListener:" + this.actionName + "onSuccess");
        }

        @Override
        public void onFailure(int reason) {
            String errorMessage;
            switch (reason) {
                case WifiP2pManager.BUSY:
                    errorMessage = "BUSY";
                    break;
                case WifiP2pManager.P2P_UNSUPPORTED:
                    errorMessage = "P2P_UNSUPPORTED";
                    break;
                case WifiP2pManager.ERROR:
                    errorMessage = "ERROR";
                    break;
                case WifiP2pManager.NO_SERVICE_REQUESTS:
                    errorMessage = "NO_SERVICE_REQUESTS";
                    break;
                default:
                    errorMessage = "Unhandled error";
                    break;
            }
            Log.e("WifiP2pActionListener:" + this.actionName + "onFailure: " + errorMessage);
            event.getData().putString(EVENT_KEY_CALLBACK_NAME, ACTION_LISTENER_ON_FAILURE);
            event.getData().putInt(EVENT_KEY_REASON, reason);
            event.getData().putString(EVENT_KEY_REASON_MESSAGE, errorMessage);

            EventCache.getInstance().postEvent(event);
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
            event.getData().putString(EVENT_KEY_CALLBACK_NAME, ACTION_LISTENER_ON_SUCCESS);
            EventCache.getInstance().postEvent(event);
        }

        @Override
        public void onFailure(int reason) {
            SnippetEvent event = new SnippetEvent(mCallbackId, CALLBACK_EVENT_NAME);
            event.getData().putString(EVENT_KEY_CALLBACK_NAME, ACTION_LISTENER_ON_FAILURE);
            event.getData().putInt(EVENT_KEY_REASON, reason);
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
            Log.d("onDeviceInfoAvailable: " + device.toString());
            SnippetEvent event = new SnippetEvent(mCallbackId, EVENT_NAME_ON_DEVICE_INFO);
            event.getData().putBundle(EVENT_KEY_P2P_DEVICE, BundleUtils.fromWifiP2pDevice(device));
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
            event.getData().putParcelableArrayList(EVENT_KEY_PEER_LIST, devices);
            event.getData().putLong("timestampMs", System.currentTimeMillis());
            EventCache.getInstance().postEvent(event);
        }
    }

    private static class PersistentGroupInfoListener implements
            WifiP2pManager.PersistentGroupInfoListener {
        private final String mCallbackId;

        PersistentGroupInfoListener(String callbackId) {
            this.mCallbackId = callbackId;
        }

        @Override
        public void onPersistentGroupInfoAvailable(@NonNull WifiP2pGroupList groups) {
            Log.d("onPersistentGroupInfoAvailable: " + groups.toString());
            SnippetEvent event = new SnippetEvent(mCallbackId, "onPersistentGroupInfoAvailable");
            event.getData().putParcelableArrayList(
                    "groupList", BundleUtils.fromWifiP2pGroupList(groups));
            EventCache.getInstance().postEvent(event);
        }
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

    /** Wait until any callback of {@link ActionListener} is triggered. */
    private Bundle waitActionListenerResult(String callbackId) throws Throwable {
        SnippetEvent event = waitForSnippetEvent(
                callbackId, ActionListener.CALLBACK_EVENT_NAME, TIMEOUT_SHORT_MS);
        Log.d("Got action listener result event: " + event.getData().toString());
        return event.getData();
    }

    /** Wait until any callback of {@link ActionListener} is triggered and verify it succeeded. */
    private void verifyActionListenerSucceed(String callbackId) throws Throwable {
        Bundle eventData = waitActionListenerResult(callbackId);
        String result = eventData.getString(EVENT_KEY_CALLBACK_NAME);
        if (Objects.equals(result, ACTION_LISTENER_ON_SUCCESS)) {
            return;
        }
        if (Objects.equals(result, ACTION_LISTENER_ON_FAILURE)) {
            throw new WifiP2pManagerException(
                    "Action failed with reason code: " + eventData.getInt(EVENT_KEY_REASON)
            );
        }
        throw new WifiP2pManagerException("Action got unknown event: " + eventData.toString());
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
