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
import android.net.wifi.aware.WifiAwareNetworkInfo;

import androidx.annotation.NonNull;
import androidx.test.core.app.ApplicationProvider;

import com.google.android.mobly.snippet.Snippet;
import com.google.android.mobly.snippet.event.EventCache;
import com.google.android.mobly.snippet.event.SnippetEvent;
import com.google.android.mobly.snippet.rpc.AsyncRpc;
import com.google.android.mobly.snippet.rpc.Rpc;
import com.google.android.mobly.snippet.util.Log;

import org.json.JSONException;

import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.net.Inet6Address;
import java.net.ServerSocket;
import java.net.Socket;
import java.nio.charset.StandardCharsets;

public class ConnectivityManagerSnippet implements Snippet {
    private static final String EVENT_KEY_CB_NAME = "callbackName";
    private static final String EVENT_KEY_NETWORK = "network";
    private static final String EVENT_KEY_NETWORK_CAP = "networkCapabilities";
    private static final String EVENT_KEY_TRANSPORT_INFO_CLASS = "transportInfoClassName";

    private static final int TRANSPORT_PROTOCOL_TCP = 6;

    private final Context mContext;
    private final ConnectivityManager mConnectivityManager;
    private NetworkCallback mNetworkCallBack;
    private ServerSocket mServerSocket;
    private Socket mSocket;
    private NetworkCapabilities mNetworkCapabilities;
    private Network mNetwork;
    private OutputStream mOutputStream;
    private Thread mSocketThread;
    private final int mCloseSocketTimeout = 15 * 1000;
    private final int mAcceptTimeout = 30 * 1000;
    private final int mSocketSoTimeout = 30 * 1000;
    private final Object mSocketLock = new Object();


    class ConnectivityManagerSnippetSnippetException extends Exception {
        ConnectivityManagerSnippetSnippetException(String msg) {
            super(msg);
        }
    }

    public ConnectivityManagerSnippet() throws ConnectivityManagerSnippetSnippetException {
        mContext = ApplicationProvider.getApplicationContext();
        mConnectivityManager = mContext.getSystemService(ConnectivityManager.class);
        if (mConnectivityManager == null) {
            throw new ConnectivityManagerSnippetSnippetException(
                    "ConnectivityManager not " + "available.");
        }
    }

    public class NetworkCallback extends ConnectivityManager.NetworkCallback {

        String mCallBackId;

        NetworkCallback(String callBackId) {
            mCallBackId = callBackId;
        }

        @Override
        public void onUnavailable() {
            SnippetEvent event = new SnippetEvent(mCallBackId, "NetworkCallback");
            event.getData().putString(EVENT_KEY_CB_NAME, "onUnavailable");
            EventCache.getInstance().postEvent(event);
        }

        @Override
        public void onCapabilitiesChanged(
                @NonNull Network network, @NonNull NetworkCapabilities networkCapabilities
        ) {
            SnippetEvent event = new SnippetEvent(mCallBackId, "NetworkCallback");
            event.getData().putString(EVENT_KEY_CB_NAME, "onCapabilitiesChanged");
            event.getData().putParcelable(EVENT_KEY_NETWORK, network);
            event.getData().putParcelable(EVENT_KEY_NETWORK_CAP, networkCapabilities);
            mNetworkCapabilities = networkCapabilities;
            mNetwork = network;
            TransportInfo transportInfo = networkCapabilities.getTransportInfo();
            String transportInfoClassName = "";
            if (transportInfo != null) {
                transportInfoClassName = transportInfo.getClass().getName();
            }
            event.getData().putString(EVENT_KEY_TRANSPORT_INFO_CLASS, transportInfoClassName);
            EventCache.getInstance().postEvent(event);
        }
    }

    /**
     * Requests a network with given network request.
     *
     * @param callBackId              Assigned automatically by mobly.
     * @param request                 The request object.
     * @param requestNetworkTimeoutMs The timeout in milliseconds.
     */
    @AsyncRpc(description = "Request a network.")
    public void connectivityRequestNetwork(
            String callBackId, NetworkRequest request, int requestNetworkTimeoutMs
    ) {
        Log.v("Requesting network with request: " + request.toString());
        mNetworkCallBack = new NetworkCallback(callBackId);
        mConnectivityManager.requestNetwork(request, mNetworkCallBack, requestNetworkTimeoutMs);
    }

    /**
     * Unregisters the registered network callback and possibly releases requested networks.
     */
    @Rpc(description = "Unregister a network request")
    public void connectivityUnregisterNetwork() {
        if (mNetworkCallBack == null || mConnectivityManager == null) {
            return;
        }
        mConnectivityManager.unregisterNetworkCallback(mNetworkCallBack);
    }

    /**
     * Returns the local port of a server socket.
     *
     * @return The local port of a server socket.
     */
    @Rpc(description = "Get the local port of a server socket.")
    public int connectivityInitServerSocket() throws ConnectivityManagerSnippetSnippetException {
        int port = 0;
        try {
            mServerSocket = new ServerSocket(0);
            // https://developer.android.com/reference/java/net/ServerSocket#setSoTimeout(int)
            // A call to accept() for this ServerSocket will block for only this amount of time.
            mServerSocket.setSoTimeout(mAcceptTimeout);
        } catch (IOException e) {
            throw new ConnectivityManagerSnippetSnippetException(
                    "Failed to create a server " + "socket");
        }
        port = mServerSocket.getLocalPort();
        return port;
    }

    /**
     * Starts a server socket to accept incoming connections.
     */
    @Rpc(description = "Start a server socket to accept incoming connections.")
    public void connectivityServerSocketAccept() throws ConnectivityManagerSnippetSnippetException {
        checkServerSocket();
        mSocketThread = new Thread(() -> {
            try {
                //This will block the execution of the program. Usually we will put it into a child
                // thread to run
                synchronized (mSocketLock) {
                    mSocket = mServerSocket.accept();
                }
            } catch (IOException e) {
                Log.e("Socket accept error", e);
            }
        });
        mSocketThread.start();
    }

    /**
     * Check if the server socket thread is alive.
     *
     * @return True if the server socket thread is alive.
     */
    public boolean connectivityIsSocketThreadAlive() {
        return mSocketThread != null && mSocketThread.isAlive();
    }

    /**
     * Stops the server socket thread if it's running.
     */
    @Rpc(description = "Stop the server socket thread if it's running.")
    public void connectivityStopAcceptThread() throws IOException {
        if (connectivityIsSocketThreadAlive()) {
            try {
                connectivityCloseServerSocket();
                mSocketThread.join(mCloseSocketTimeout);  // Wait for the thread to terminate
            } catch (InterruptedException e) {
                throw new RuntimeException("Error stopping server socket thread", e);
            } finally {
                connectivityCloseSocket();
            }
        }
    }

    /**
     * Reads from a socket.
     *
     * @param len The number of bytes to read.
     */
    @Rpc(description = "Reads from a socket.")
    public String connectivityReadSocket(int len)
            throws ConnectivityManagerSnippetSnippetException, JSONException, IOException {
        checkSocket();
        synchronized (mSocketLock) {
            InputStream is = mSocket.getInputStream();

            // Read the specified number of bytes from the input stream
            byte[] buffer = new byte[len];
            int bytesReadLength = is.read(buffer, 0, len); // Read up to len bytes
            if (bytesReadLength == -1) { // End of stream reached unexpectedly
                throw new ConnectivityManagerSnippetSnippetException(
                        "End of stream reached before reading expected bytes.");
            }
            // Convert the bytes read to a String
            String receiveStrMsg = new String(buffer, 0, bytesReadLength, StandardCharsets.UTF_8);
            return receiveStrMsg;
        }
    }

    /**
     * Writes to a socket.
     *
     * @param message The message to send.
     * @throws ConnectivityManagerSnippetSnippetException
     */
    @Rpc(description = "Writes to a socket.")
    public Boolean connectivityWriteSocket(String message)
            throws ConnectivityManagerSnippetSnippetException, IOException {
        checkSocket();
        synchronized (mSocketLock) {
            mOutputStream = mSocket.getOutputStream();
        }
        byte[] bytes = message.getBytes(StandardCharsets.UTF_8);
        // Write the message to the output stream
        mOutputStream.write(bytes, 0, bytes.length);
        mOutputStream.flush();
        return true;


    }

    /**
     * Closes the socket.
     *
     * @throws ConnectivityManagerSnippetSnippetException
     */
    public void connectivityCloseSocket() throws IOException {
        synchronized (mSocketLock) {
            if (mSocket != null && !mSocket.isClosed()) {
                mSocket.close();
            }
            mSocket = null;
        }


    }

    /**
     * Closes the server socket.
     *
     * @throws IOException
     */
    public void connectivityCloseServerSocket() throws IOException {
        if (mServerSocket != null && !mServerSocket.isClosed()) {
            mServerSocket.close();
        }
        mServerSocket = null;


    }

    /**
     * Closes the outputStream.
     *
     * @throws ConnectivityManagerSnippetSnippetException
     */
    @Rpc(description = "Close the outputStream.")
    public void connectivityCloseWrite()
            throws IOException, ConnectivityManagerSnippetSnippetException {
        checkOutputStream();
        mOutputStream.close();
        mOutputStream = null;
    }

    private void checkOutputStream() throws ConnectivityManagerSnippetSnippetException {
        if (mOutputStream == null) {
            throw new ConnectivityManagerSnippetSnippetException("Output stream is not created.");
        }
    }

    /**
     * Create a socket.
     *
     * @throws ConnectivityManagerSnippetSnippetException
     */
    @Rpc(description = "Create to a socket.")
    public void connectivityCreateSocket()
            throws ConnectivityManagerSnippetSnippetException, IOException {
        synchronized (mSocketLock) {
            if (mSocket != null) {
                throw new ConnectivityManagerSnippetSnippetException("Socket is already created"
                        + ".Please call connectivityCloseSocket() or connectivityStopAcceptThread"
                        + "() " + "first.");
            }
        }
        checkNetwork();
        checkNetworkCapabilities();
        WifiAwareNetworkInfo peerAwareInfo =
                (WifiAwareNetworkInfo) mNetworkCapabilities.getTransportInfo();
        if (peerAwareInfo == null) {
            throw new ConnectivityManagerSnippetSnippetException("PeerAwareInfo is null.");
        }
        Inet6Address peerIpv6Addr = peerAwareInfo.getPeerIpv6Addr();
        int peerPort = peerAwareInfo.getPort();
        int transportProtocol = peerAwareInfo.getTransportProtocol();
        if (transportProtocol != TRANSPORT_PROTOCOL_TCP) {
            throw new ConnectivityManagerSnippetSnippetException(
                    "Only support TCP transport protocol.");
        }
        if (peerPort <= 0) {
            throw new ConnectivityManagerSnippetSnippetException("Invalid port number.");
        }
        synchronized (mSocketLock) {
            mSocket = mNetwork.getSocketFactory().createSocket(peerIpv6Addr, peerPort);
            mSocket.setSoTimeout(mSocketSoTimeout);
        }

    }

    /**
     * Check if the network capabilities is created.
     *
     * @throws ConnectivityManagerSnippetSnippetException
     */
    private void checkNetworkCapabilities() throws ConnectivityManagerSnippetSnippetException {
        if (mNetworkCapabilities == null) {
            throw new ConnectivityManagerSnippetSnippetException(
                    "Network capabilities is not " + "created.");
        }
    }

    /**
     * Check if the network is created.
     *
     * @throws ConnectivityManagerSnippetSnippetException
     */
    private void checkNetwork() throws ConnectivityManagerSnippetSnippetException {
        if (mNetwork == null) {
            throw new ConnectivityManagerSnippetSnippetException("Network is not created.");
        }
    }

    /**
     * Check if the socket is created.
     *
     * @throws ConnectivityManagerSnippetSnippetException
     */
    private void checkSocket() throws ConnectivityManagerSnippetSnippetException {
        synchronized (mSocketLock) {
            if (mSocket == null) {
                throw new ConnectivityManagerSnippetSnippetException("Socket is not created.");
            }
        }
    }

    /**
     * Check if the server socket is created.
     *
     * @throws ConnectivityManagerSnippetSnippetException
     */
    private void checkServerSocket() throws ConnectivityManagerSnippetSnippetException {
        if (mServerSocket == null) {
            throw new ConnectivityManagerSnippetSnippetException("Server socket is not created.");
        }
    }

    /**
     * Close all sockets.
     *
     * @throws IOException
     */
    @Rpc(description = "Close all sockets.")
    public void connectivityCloseAllSocket() throws IOException {
        connectivityStopAcceptThread();
        connectivityCloseSocket();
        connectivityCloseServerSocket();
    }

    @Override
    public void shutdown() throws Exception {
        try {
            if (mNetworkCallBack != null) {
                mConnectivityManager.unregisterNetworkCallback(mNetworkCallBack);
            }
        } catch (Exception e) {
            Log.e("Error unregistering network callback", e);
        }
        try {
            connectivityCloseAllSocket();
        } catch (Exception e) {
            Log.e("Error closing sockets", e);
        }
        Snippet.super.shutdown();
    }
}
