package com.google.snippet.wifi.aware;

import android.Manifest;
import android.content.Context;
import android.net.ConnectivityManager;
import android.net.Network;
import android.net.NetworkCapabilities;
import android.net.NetworkRequest;
import android.os.HandlerThread;

import androidx.annotation.NonNull;
import androidx.test.core.app.ApplicationProvider;

import com.google.android.mobly.snippet.Snippet;
import com.google.android.mobly.snippet.event.EventCache;
import com.google.android.mobly.snippet.event.SnippetEvent;
import com.google.android.mobly.snippet.rpc.AsyncRpc;

public class ConnectivityManagerSnippet implements Snippet {
    private final Context mContext;
    private final ConnectivityManager mConnectivityManager;
    private Network mNetwork = null;
    private NetworkCapabilities mNetworkCapabilities = null;
    private CustomNetworkCallback mNetworkCallBack;

    private static class ConnectivityManagerSnippetSnippetException extends Exception {
        ConnectivityManagerSnippetSnippetException(String msg) {
            super(msg);
        }
    }


    private void checkConnectivityManager() throws ConnectivityManagerSnippetSnippetException {
        if (mConnectivityManager == null) {
            throw new ConnectivityManagerSnippetSnippetException("not support ConnectivityManager");
        }
    }


    public ConnectivityManagerSnippet() throws ConnectivityManagerSnippetSnippetException {
        mContext = ApplicationProvider.getApplicationContext();
        PermissionUtils.checkPermissions(mContext, Manifest.permission.ACCESS_WIFI_STATE,
                Manifest.permission.CHANGE_WIFI_STATE, Manifest.permission.ACCESS_FINE_LOCATION,
                Manifest.permission.NEARBY_WIFI_DEVICES);
        mConnectivityManager = mContext.getSystemService(ConnectivityManager.class);
        checkConnectivityManager();
        HandlerThread handlerThread = new HandlerThread("Snippet-Aware");
        handlerThread.start();
//        mHandler = new Handler(handlerThread.getLooper());
    }


    /**
     * Customized network monitoring method
     */
    public class CustomNetworkCallback extends ConnectivityManager.NetworkCallback {

        SnippetEvent event;

        CustomNetworkCallback(String callBackId) {
            event=new SnippetEvent(callBackId, "onNetWorkCallback");
        }

        @Override
        public void onUnavailable() {
            mNetworkCapabilities = null;
            event.getData().putString("method", "onUnavailable");
            EventCache.getInstance().postEvent(event);
        }

        @Override
        public void onCapabilitiesChanged(@NonNull Network network,
                                          @NonNull NetworkCapabilities networkCapabilities) {
            mNetwork = network;
            mNetworkCapabilities = networkCapabilities;
            event.getData().putString("method", "onCapabilitiesChanged");
            event.getData().putParcelable("network", network);
            event.getData().putParcelable("networkCapabilities", networkCapabilities);
            EventCache.getInstance().postEvent(event);

        }
    }

    /**
     * An object describing a network that the application is interested in.
     *
     * @param callBackId Assigned automatically by mobly.
     * @param request    The request object.
     * @param timeoutMs  The timeout in milliseconds.
     */
    @AsyncRpc(description = "Request a network.")
    public void requestNetwork(String callBackId, NetworkRequest request, int timeoutMs) {
        if (mNetworkCallBack == null) {
            mNetworkCallBack = new CustomNetworkCallback(callBackId);
        }
        mConnectivityManager.requestNetwork(request, mNetworkCallBack, timeoutMs);
    }


}
