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

import android.net.MacAddress;
import android.net.wifi.p2p.WifiP2pConfig;
import android.net.wifi.p2p.nsd.WifiP2pDnsSdServiceRequest;
import android.net.wifi.p2p.nsd.WifiP2pServiceRequest;
import android.net.wifi.p2p.nsd.WifiP2pUpnpServiceRequest;

import org.json.JSONException;
import org.json.JSONObject;

/** Deserializes JSONObject into data objects defined in Android API. */
public class JsonDeserializer {
    private static final String PERSISTENT_MODE = "persistent_mode";
    private static final String DEVICE_ADDRESS = "device_address";
    private static final String GROUP_CLIENT_IP = "group_client_ip_provisioning_mode";
    private static final String GROUP_OPERATING_BAND = "group_operating_band";
    private static final String GROUP_OPERATING_FREQUENCY = "group_operating_frequency";
    private static final String NETWORK_NAME = "network_name";
    private static final String PASSPHRASE = "passphrase";
    private static final String PROTOCOL_TYPE = "protocol_type";
    private static final String INSTANCE_CREATE_TYPE = "instance_create_type";


    /** Converts Python dict to android.net.wifi.p2p.WifiP2pConfig. */
    public static WifiP2pConfig jsonToWifiP2pConfig(JSONObject jsonObject) throws JSONException {
        if (jsonObject.has("wps_setup")) {
            // Create WifiP2pConfig directly.
            WifiP2pConfig config = new WifiP2pConfig();
            config.wps.setup = jsonObject.getInt("wps_setup");
            if (jsonObject.has(DEVICE_ADDRESS)) {
                config.deviceAddress = jsonObject.getString(DEVICE_ADDRESS);
            }
            return config;
        }

        // Create WifiP2pConfig through builder.
        WifiP2pConfig.Builder builder = new WifiP2pConfig.Builder();
        if (jsonObject.has(PERSISTENT_MODE)) {
            builder.enablePersistentMode(jsonObject.getBoolean(PERSISTENT_MODE));
        }
        if (jsonObject.has(DEVICE_ADDRESS)) {
            builder.setDeviceAddress(MacAddress.fromString(jsonObject.getString(DEVICE_ADDRESS)));
        }
        if (jsonObject.has(GROUP_CLIENT_IP)) {
            builder.setGroupClientIpProvisioningMode(jsonObject.getInt(GROUP_CLIENT_IP));
        }
        if (jsonObject.has(GROUP_OPERATING_BAND)) {
            builder.setGroupOperatingBand(jsonObject.getInt(GROUP_OPERATING_BAND));
        }
        if (jsonObject.has(GROUP_OPERATING_FREQUENCY)) {
            builder.setGroupOperatingFrequency(jsonObject.getInt(GROUP_OPERATING_FREQUENCY));
        }
        if (jsonObject.has(NETWORK_NAME)) {
            builder.setNetworkName(jsonObject.getString(NETWORK_NAME));
        }
        if (jsonObject.has(PASSPHRASE)) {
            builder.setPassphrase(jsonObject.getString(PASSPHRASE));
        }
        return builder.build();
    }

    /**
     * Converts Python dict to android.net.wifi.p2p.nsd.WifiP2pServiceRequest.
     */
    public static WifiP2pServiceRequest jsonToWifiP2pServiceRequest(JSONObject jsonObject)
            throws JSONException {
        if (jsonObject == null) {
            throw new JSONException("jsonObject is null,please call jsonToWifiP2pServiceRequest"
                    + " with a valid JSONObject");
        }
        // If both are included, an error is returned.
        if (jsonObject.has(INSTANCE_CREATE_TYPE) && jsonObject.has(PROTOCOL_TYPE)) {
            throw new JSONException("Both instance_create_type and protocol_type are included.");
        }

        if (jsonObject.has(INSTANCE_CREATE_TYPE)) {
            String instanceCreateType = jsonObject.getString(INSTANCE_CREATE_TYPE);
            if (instanceCreateType.equals("WifiP2pUpnpServiceRequest")) {
                return WifiP2pUpnpServiceRequest.newInstance();
            } else if (instanceCreateType.equals("WifiP2pDnsSdServiceRequest")) {
                return WifiP2pDnsSdServiceRequest.newInstance();
            } else {
                throw new JSONException("instance_create_type is not valid.");
            }
        } else if (jsonObject.has(PROTOCOL_TYPE)) {
            return WifiP2pServiceRequest.newInstance(jsonObject.getInt(PROTOCOL_TYPE));
        } else {
            throw new JSONException("instance_create_type or protocol_type is not included.");
        }
    }
}
