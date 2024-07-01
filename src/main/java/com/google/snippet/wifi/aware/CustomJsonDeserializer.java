package com.google.snippet.wifi.aware;

import android.net.NetworkRequest;
import android.net.wifi.aware.AwarePairingConfig;
import android.net.wifi.aware.DiscoverySession;
import android.net.wifi.aware.PeerHandle;
import android.net.wifi.aware.PublishConfig;
import android.net.wifi.aware.SubscribeConfig;
import android.net.wifi.aware.WifiAwareNetworkSpecifier;

import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.List;

import org.json.JSONException;
import org.json.JSONObject;

/**
 * Deserializes JSONObject into data objects defined in Android API.
 */
public class CustomJsonDeserializer {

    private static final String SERVICE_NAME = "service_name";
    private static final String SERVICE_SPECIFIC_INFO = "service_specific_info";
    private static final String MATCH_FILTER = "match_filter";
    private static final String SUBSCRIBE_TYPE = "subscribe_type";
    private static final String TERMINATE_NOTIFICATION_ENABLED = "terminate_notification_enabled";
    private static final String MAX_DISTANCE_MM = "max_distance_mm";
    private static final String PAIRING_CONFIG = "pairing_config";

    // PublishConfig special
    private static final String PUBLISH_TYPE = "publish_type";
    private static final String RANGING_ENABLED = "ranging_enabled";

    // WifiAwareNetworkSpecifier special
    private static final String PSK_PASSPHRASE = "psk_passphrase";
    private static final String PORT = "port";
    private static final String TRANSPORT_TYPE = "transport_type";
    private static final String CAPABILITY = "capability";

    private CustomJsonDeserializer() {
    }

    /**
     * Converts Python dict to {@link SubscribeConfig}.
     *
     * @param jsonObject Look python at constants.py -> SubscribeConfig
     */
    public static SubscribeConfig jsonToSubscribeConfig(JSONObject jsonObject) throws JSONException {
        SubscribeConfig.Builder builder = new SubscribeConfig.Builder();

        if (jsonObject.has(SERVICE_NAME)) {
            String serviceName = jsonObject.getString(SERVICE_NAME);
            builder.setServiceName(serviceName);
        }
        if (jsonObject.has(SERVICE_SPECIFIC_INFO)) {
            byte[] serviceSpecificInfo = jsonObject.getString(SERVICE_SPECIFIC_INFO).getBytes(StandardCharsets.UTF_8);
            builder.setServiceSpecificInfo(serviceSpecificInfo);
        }
        if (jsonObject.has(MATCH_FILTER)) {
            List<byte[]> matchFilter = new ArrayList<>();
            for (int i = 0; i < jsonObject.getJSONArray(MATCH_FILTER).length(); i++) {
                matchFilter.add(jsonObject.getJSONArray(MATCH_FILTER).getString(i).getBytes(StandardCharsets.UTF_8));
            }
            builder.setMatchFilter(matchFilter);
        }
        if (jsonObject.has(SUBSCRIBE_TYPE)) {
            int subscribeType = jsonObject.getInt(SUBSCRIBE_TYPE);
            builder.setSubscribeType(subscribeType);
        }
        if (jsonObject.has(TERMINATE_NOTIFICATION_ENABLED)) {
            boolean terminateNotificationEnabled = jsonObject.getBoolean(TERMINATE_NOTIFICATION_ENABLED);
            builder.setTerminateNotificationEnabled(terminateNotificationEnabled);
        }
        if (jsonObject.has(MAX_DISTANCE_MM)) {
            int maxDistanceMm = jsonObject.getInt(MAX_DISTANCE_MM);
            if (maxDistanceMm > 0) {
                builder.setMaxDistanceMm(maxDistanceMm);
            }
        }
        if (jsonObject.has(PAIRING_CONFIG)) {
            JSONObject pairingConfigObject = jsonObject.getJSONObject(PAIRING_CONFIG);
            AwarePairingConfig pairingConfig = jsonToAwarePairingConfig(pairingConfigObject);
            builder.setPairingConfig(pairingConfig);
        }

        return builder.build();
    }


    /**
     * Converts JSONObject to {@link AwarePairingConfig}.
     */
    private static AwarePairingConfig jsonToAwarePairingConfig(JSONObject jsonObject) throws JSONException {
        AwarePairingConfig.Builder builder = new AwarePairingConfig.Builder();

        if (jsonObject.has("pairing_cache_enabled")) {
            boolean pairingCacheEnabled = jsonObject.getBoolean("pairing_cache_enabled");
            builder.setPairingCacheEnabled(pairingCacheEnabled);
        }
        if (jsonObject.has("pairing_setup_enabled")) {
            boolean pairingSetupEnabled = jsonObject.getBoolean("pairing_setup_enabled");
            builder.setPairingSetupEnabled(pairingSetupEnabled);
        }
        if (jsonObject.has("pairing_verification_enabled")) {
            boolean pairingVerificationEnabled = jsonObject.getBoolean("pairing_verification_enabled");
            builder.setPairingVerificationEnabled(pairingVerificationEnabled);
        }
        if (jsonObject.has("bootstrapping_methods")) {
            int bootstrappingMethods = jsonObject.getInt("bootstrapping_methods");
            builder.setBootstrappingMethods(bootstrappingMethods);
        }


        return builder.build();
    }

    /**
     * Converts Python dict to {@link PublishConfig}.
     *
     * @param jsonObject Look python at constants.py -> PublishConfig
     */
    public static PublishConfig jsonToPublishConfig(JSONObject jsonObject) throws JSONException {
        PublishConfig.Builder builder = new PublishConfig.Builder();

        if (jsonObject.has(SERVICE_NAME)) {
            String serviceName = jsonObject.getString(SERVICE_NAME);
            builder.setServiceName(serviceName);
        }
        if (jsonObject.has(SERVICE_SPECIFIC_INFO)) {
            byte[] serviceSpecificInfo = jsonObject.getString(SERVICE_SPECIFIC_INFO).getBytes(StandardCharsets.UTF_8);
            builder.setServiceSpecificInfo(serviceSpecificInfo);
        }
        if (jsonObject.has(MATCH_FILTER)) {
            List<byte[]> matchFilter = new ArrayList<>();
            for (int i = 0; i < jsonObject.getJSONArray(MATCH_FILTER).length(); i++) {
                matchFilter.add(jsonObject.getJSONArray(MATCH_FILTER).getString(i).getBytes(StandardCharsets.UTF_8));
            }
            builder.setMatchFilter(matchFilter);
        }
        if (jsonObject.has(PUBLISH_TYPE)) {
            int publishType = jsonObject.getInt(PUBLISH_TYPE);
            builder.setPublishType(publishType);
        }
        if (jsonObject.has(TERMINATE_NOTIFICATION_ENABLED)) {
            boolean terminateNotificationEnabled = jsonObject.getBoolean(TERMINATE_NOTIFICATION_ENABLED);
            builder.setTerminateNotificationEnabled(terminateNotificationEnabled);
        }
        if (jsonObject.has(RANGING_ENABLED)) {
            boolean rangingEnabled = jsonObject.getBoolean(RANGING_ENABLED);
            builder.setRangingEnabled(rangingEnabled);
        }
        if (jsonObject.has(PAIRING_CONFIG)) {
            JSONObject pairingConfigObject = jsonObject.getJSONObject(PAIRING_CONFIG);
            AwarePairingConfig pairingConfig = jsonToAwarePairingConfig(pairingConfigObject);
            builder.setPairingConfig(pairingConfig);
        }

        return builder.build();
    }

    public static NetworkRequest jsonToNetworkRequest(DiscoverySession discoverySession, PeerHandle peerHandle, JSONObject jsonObject) throws JSONException {
        WifiAwareNetworkSpecifier specifier = jsonToWifiAwareNetworkSpecifier(jsonObject, discoverySession, peerHandle);

        NetworkRequest.Builder requestBuilder = new NetworkRequest.Builder();
        if (specifier != null) {
            requestBuilder.setNetworkSpecifier(specifier);
        }
        if (jsonObject.has(TRANSPORT_TYPE)) {
            int transportType = jsonObject.getInt(TRANSPORT_TYPE);
            requestBuilder.addTransportType(transportType);
        }

        if (jsonObject.has(CAPABILITY)) {
            int capability = jsonObject.getInt(CAPABILITY);
            requestBuilder.addCapability(capability);
        }

        return requestBuilder.build();
    }

    /**
     * Converts Python dict to {@link WifiAwareNetworkSpecifier}.
     *
     * @param jsonObject Look python at constants.py -> WifiAwareNetworkSpecifier
     */
    public static WifiAwareNetworkSpecifier jsonToWifiAwareNetworkSpecifier(JSONObject jsonObject, DiscoverySession discoverySession, PeerHandle peerHandle) throws JSONException {
        WifiAwareNetworkSpecifier.Builder specifierBuilder = new WifiAwareNetworkSpecifier.Builder(discoverySession, peerHandle);

        if (jsonObject.has(PSK_PASSPHRASE)) {
            specifierBuilder.setPskPassphrase(jsonObject.getString(PSK_PASSPHRASE));
        }

        if (jsonObject.has(PORT)) {
            specifierBuilder.setPort(jsonObject.getInt(PORT));
        }

        WifiAwareNetworkSpecifier specifier = specifierBuilder.build();
        return specifier;
    }
}
