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
import android.net.wifi.p2p.nsd.WifiP2pServiceInfo;
import android.net.wifi.p2p.nsd.WifiP2pServiceRequest;
import android.net.wifi.p2p.nsd.WifiP2pUpnpServiceRequest;

import org.json.JSONException;
import org.json.JSONObject;

/** Deserializes JSONObject into data objects defined in Android API. */
public class JsonDeserializer {
    private static final String PERSISTENT_MODE = "persistent_mode";
    private static final String DEVICE_ADDRESS = "device_address";
    private static final String GROUP_CLIENT_IP_PROVISIONING_MODE =
            "group_client_ip_provisioning_mode";
    private static final String GROUP_OPERATING_BAND = "group_operating_band";
    private static final String GROUP_OPERATING_FREQUENCY = "group_operating_frequency";
    private static final String NETWORK_NAME = "network_name";
    private static final String PASSPHRASE = "passphrase";

    private static final String SERVICE_TYPE = "service_type";
    private static final String REQUEST_TYPE = "request_type";

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
        if (jsonObject.has(GROUP_CLIENT_IP_PROVISIONING_MODE)) {
            builder.setGroupClientIpProvisioningMode(
                    jsonObject.getInt(GROUP_CLIENT_IP_PROVISIONING_MODE));
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
     * Converts Python dict to {@link WifiP2pServiceInfo}.
     */
    public static WifiP2pServiceInfo jsonToWifiP2pServiceInfo(JSONObject jsonObject)
            throws JSONException {
        WifiP2pServiceInfo wifiP2pServiceInfo = null;
        if (jsonObject.has(SERVICE_TYPE)) {
            int serverType = jsonObject.getInt(SERVICE_TYPE);
            switch (serverType) {
                case 1:
                    wifiP2pServiceInfo = LocalServices.createIppService();
                    break;
                case 2:
                    wifiP2pServiceInfo = LocalServices.createAfpService();
                    break;
                default:
                    wifiP2pServiceInfo = LocalServices.createRendererService();
                    break;
            }
        }
        return wifiP2pServiceInfo;
    }

    /**
     * Converts Python dict to {@link WifiP2pUpnpServiceRequest}.
     */
    public static WifiP2pServiceRequest jsonToWifiP2pServiceRequest(JSONObject jsonObject)
            throws JSONException {
        WifiP2pServiceRequest wifiP2pServiceRequest = null;
        if (jsonObject.has(REQUEST_TYPE)) {
            int requestType = jsonObject.getInt(REQUEST_TYPE);
            switch (requestType) {
                default:
                    wifiP2pServiceRequest = WifiP2pUpnpServiceRequest.newInstance();
                    break;
            }
        }
        return wifiP2pServiceRequest;
    }

}
