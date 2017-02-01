package com.tejaswikurella.nsdchat;

import android.bluetooth.BluetoothAdapter;
import android.content.Context;
import android.content.DialogInterface;
import android.net.nsd.NsdManager;
import android.net.nsd.NsdServiceInfo;
import android.net.wifi.WifiManager;
import android.support.v7.app.AlertDialog;
import android.support.v7.app.AppCompatActivity;
import android.os.Bundle;
import android.text.format.Formatter;
import android.util.Log;
import android.view.View;
import android.widget.EditText;
import android.widget.ScrollView;
import android.widget.TextView;
import android.widget.Toast;

import org.json.JSONException;
import org.json.JSONObject;

import java.io.ByteArrayOutputStream;
import java.io.DataInputStream;
import java.io.DataOutputStream;
import java.io.IOException;
import java.net.InetAddress;
import java.net.InetSocketAddress;
import java.net.ServerSocket;
import java.net.Socket;
import java.net.UnknownHostException;
import java.util.HashMap;
import java.util.Iterator;

public class MainActivity extends AppCompatActivity {

    static final int SocketServerPORT = 8080;  // Port should be fetched dynamically in real systems.// NSD Manager and service registration code
    private String SERVICE_NAME = BluetoothAdapter.getDefaultAdapter().getName();
    private String SERVICE_TYPE = "_nsdchat._tcp.";
    private int SERVICE_PORT = 0;

    String REQUEST_CLIENT_DATA = "request_client_data";
    String REQUEST_SERVER_ACK = "request_server_ack";

    private NsdManager mNsdManager;
    NsdManager.RegistrationListener mRegistrationListener;
    NsdManager.DiscoveryListener mDiscoveryListener;
    CustomResolveListener mResolveListener;

    ServerThread serverThread = null;

    HashMap<String, NsdServiceInfo> mServiceMap = new HashMap<String, NsdServiceInfo>();

    ScrollView scrollView;
    TextView tvChat;
    EditText etMessage;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_main);

        scrollView = (ScrollView) findViewById(R.id.scroll);
        tvChat = (TextView) findViewById(R.id.chat);
        etMessage = (EditText) findViewById(R.id.message);

        mNsdManager = (NsdManager) getSystemService(Context.NSD_SERVICE);
        //registerService(SocketServerPORT);
        initializeResolveListener();

    }

    public void registerService() {
        initializeServerSocket();
        NsdServiceInfo serviceInfo  = new NsdServiceInfo();
        serviceInfo.setServiceName(SERVICE_NAME);
        serviceInfo.setServiceType(SERVICE_TYPE);
        serviceInfo.setPort(SERVICE_PORT);

        initializeRegistrationListener();
        mNsdManager.registerService(
                serviceInfo, NsdManager.PROTOCOL_DNS_SD, mRegistrationListener);
    }

    public void initializeRegistrationListener() {
        mRegistrationListener = new NsdManager.RegistrationListener() {

            @Override
            public void onServiceRegistered(NsdServiceInfo NsdServiceInfo) {
                // Save the service name.  Android may have changed it in order to
                // resolve a conflict, so update the name you initially requested
                // with the name Android actually used.
                String mServiceName = NsdServiceInfo.getServiceName();
                SERVICE_NAME = mServiceName;
                Toast.makeText(MainActivity.this, "Successfully registered",
                        Toast.LENGTH_SHORT).show();
                Log.d("NsdserviceOnRegister", "Registered name : " + mServiceName);
            }

            @Override
            public void onRegistrationFailed(NsdServiceInfo serviceInfo, int errorCode) {
                // Registration failed!  Put debugging code here to determine why.
                Toast.makeText(MainActivity.this, "registration failed",
                        Toast.LENGTH_LONG).show();
                Log.d("NsdserviceRegisterFail", "Registration failed");
            }

            @Override
            public void onServiceUnregistered(NsdServiceInfo serviceInfo) {
                // Service has been unregistered.  This only happens when you call
                // NsdManager.unregisterService() and pass in this listener.
                Log.d("NsdserviceOnUnregister",
                        "Service Unregistered : " + serviceInfo.getServiceName());
            }

            @Override
            public void onUnregistrationFailed(NsdServiceInfo serviceInfo, int errorCode) {
                // Unregistration failed.  Put debugging code here to determine why.
            }
        };
    }

    public void initializeDiscoveryListener() {

        // Instantiate a new DiscoveryListener
        mDiscoveryListener = new NsdManager.DiscoveryListener() {

            //  Called as soon as service discovery begins.
            @Override
            public void onDiscoveryStarted(String regType) {
                Toast.makeText(MainActivity.this, "Discovering devices", Toast.LENGTH_SHORT).show();
                Log.d("nsdservice", "Service discovery started");
            }

            @Override
            public void onServiceFound(NsdServiceInfo service) {
                // A service was found!  Do something with it.
                Log.d("nsdservice", "Service discovery success : " + service);
                Log.d("nsdservice", "Host = "+ service.getServiceName());
                Log.d("nsdservice", "port = " + String.valueOf(service.getPort()));

                //Toast.makeText(MainActivity.this, "service found "+ service.getServiceName() + ":" + service.getPort() , Toast.LENGTH_SHORT).show();
                if (!service.getServiceType().equals(SERVICE_TYPE)) {
                    // Service type is the string containing the protocol and
                    // transport layer for this service.
                    Log.d("nsdservice", "Unknown Service Type: " + service.getServiceType());
                } else if (service.getServiceName().equals(SERVICE_NAME)) {
                    // The name of the service tells the user what they'd be
                    // connecting to. It could be "Bob's Chat App".
                    Log.d("nsdservice", "Same machine: " + SERVICE_NAME);
                } else {
                    Log.d("nsdservice", "Diff Machine : " + service.getServiceName());
                    //mNsdManager.resolveService(service, mResolveListener);
                    mServiceMap.put(service.getServiceName(), service);
                }
            }

            @Override
            public void onServiceLost(NsdServiceInfo service) {
                // When the network service is no longer available.
                // Internal bookkeeping code goes here.
                //Toast.makeText(MainActivity.this, "Service lost", Toast.LENGTH_SHORT).show();
                Log.e("nsdserviceLost", "service lost" + service);
                mServiceMap.remove(service.getServiceName());
            }

            @Override
            public void onDiscoveryStopped(String serviceType) {
                Toast.makeText(MainActivity.this, "Discovering devices stopped", Toast.LENGTH_SHORT).show();
                Log.i("nsdserviceDstopped", "Discovery stopped: " + serviceType);
            }

            @Override
            public void onStartDiscoveryFailed(String serviceType, int errorCode) {
                Log.e("nsdServiceSrartDfailed", "Discovery failed: Error code:" + errorCode);
                Toast.makeText(MainActivity.this, "Discover start failed", Toast.LENGTH_SHORT).show();
                mNsdManager.stopServiceDiscovery(this);
            }

            @Override
            public void onStopDiscoveryFailed(String serviceType, int errorCode) {
                Log.e("nsdserviceStopDFailed", "Discovery failed: Error code:" + errorCode);
                Toast.makeText(MainActivity.this, "Discover stop failed", Toast.LENGTH_SHORT).show();
                mNsdManager.stopServiceDiscovery(this);
            }
        };
    }

    public void initializeResolveListener() {
        mResolveListener = new CustomResolveListener();
    }

    public class CustomResolveListener implements NsdManager.ResolveListener {
        private String request;
        private String message;

        public void setRequest(String request) {
            this.request =  request;
        }

        public void setMessage(String message) {
            this.message =  message;
        }

        @Override
        public void onResolveFailed(NsdServiceInfo serviceInfo, int errorCode) {
            // Called when the resolve fails. Use the error code to debug.
            Log.e("nsdservicetag", "Resolve failed " + errorCode);
            Log.e("nsdservicetag", "serivce = " + serviceInfo);

            Toast.makeText(MainActivity.this, "Resolve failed", Toast.LENGTH_SHORT).show();
        }

        @Override
        public void onServiceResolved(NsdServiceInfo serviceInfo) {
            Log.d("nsdservicetag", "Resolve Succeeded. " + serviceInfo);

            if (serviceInfo.getServiceName().equals(SERVICE_NAME)) {
                Log.d("nsdservicetag", "Same IP.");
                return;
            }

            final NsdServiceInfo mService = serviceInfo;
            int hostPort = mService.getPort();
            InetAddress hostAddress = mService.getHost();

            ClientThread clientThread = new ClientThread(
                    mService.getServiceName(), hostAddress.getHostAddress(), hostPort, request, message);
            clientThread.start();
        }
    }

    public void initializeServerSocket() {
        serverThread = new ServerThread();
        serverThread.start();
    }

    @Override
    protected void onResume() {
        super.onResume();
        if (mNsdManager != null) {
            registerService();

            initializeDiscoveryListener();
            mNsdManager.discoverServices(
                    SERVICE_TYPE, NsdManager.PROTOCOL_DNS_SD, mDiscoveryListener);
        }
    }

    @Override
    protected void onPause() {
        if (mNsdManager != null) {
            serverThread.close();

            mNsdManager.unregisterService(mRegistrationListener);
            mNsdManager.stopServiceDiscovery(mDiscoveryListener);
        }
        mServiceMap.clear();
        super.onPause();
    }

    @Override
    protected void onDestroy() {
        super.onDestroy();
    }

    private class ClientThread extends Thread {
        String dstName;
        String dstAddress;
        int dstPort;
        String request;
        String message;

        ClientThread(String name, String address, int port, String request, String message) {
            dstName = name;
            dstAddress = address;
            dstPort = port;
            this.request = request;
            this.message = message;
        }

        @Override
        public void run() {
            Socket socket = null;

            //Getting Local address
            WifiManager wm = (WifiManager) getSystemService(WIFI_SERVICE);
            String clientIP = Formatter.formatIpAddress(wm.getConnectionInfo().getIpAddress());

            JSONObject jsonData = new JSONObject();
            try {
                jsonData.put("request", request);
                jsonData.put("ipAddress", clientIP);
                jsonData.put("name", SERVICE_NAME);


                jsonData.put("message", message);
            } catch (JSONException e) {
                e.printStackTrace();
                Log.e("nsdServicejsontag", "can't put request");
                return;
            }

            DataOutputStream dataOutputStream = null;

            try {
                socket = new Socket(dstAddress, dstPort);
                dataOutputStream = new DataOutputStream(
                        socket.getOutputStream());
                dataOutputStream.writeBytes(jsonData.toString());
                dataOutputStream.flush();

                if (request.equalsIgnoreCase(REQUEST_CLIENT_DATA)) {
                    MainActivity.this.runOnUiThread(new Runnable() {
                        @Override
                        public void run() {
                            tvChat.append("Me (to " + dstName + "): " + etMessage.getText().toString() + "\n");
                            etMessage.setText("");
                            scrollView.fullScroll(ScrollView.FOCUS_DOWN);
                        }
                    });
                }

            } catch (IOException e) {
                e.printStackTrace();
                final String eString = e.toString();
                MainActivity.this.runOnUiThread(new Runnable() {

                    @Override
                    public void run() {
                        Toast.makeText(MainActivity.this, eString, Toast.LENGTH_LONG).show();
                    }

                });
            } finally {
                if (socket != null) {
                    try {
                        socket.close();
                    } catch (IOException e) {
                        e.printStackTrace();
                    }
                }

                if (dataOutputStream != null) {
                    try {
                        dataOutputStream.close();
                    } catch (IOException e) {
                        e.printStackTrace();
                    }
                }
            }

        }

    }

    private class ServerThread extends Thread {
        ServerSocket serverSocket;

        ServerThread() {
            // Initialize a server socket on the next available port.
            try {
                serverSocket = new ServerSocket(0);
                serverSocket.setReuseAddress(true);
                //serverSocket.bind(new InetSocketAddress(serverSocket.getLocalPort()));
            } catch (IOException e) {
                e.printStackTrace();
            }
            // Store the chosen port.
            SERVICE_PORT =  serverSocket.getLocalPort();
        }

        @Override
        public void run() {
            Socket socket = null;
            DataInputStream dataInputStream = null;

            while (!serverSocket.isClosed()) {
                try {
                    socket = serverSocket.accept();
                    dataInputStream = new DataInputStream(socket.getInputStream());
                    ByteArrayOutputStream byteOutputStream = new ByteArrayOutputStream();
                    byte[] buffer = new byte[1024];
                    int size;

                    String message = "";
                    while ((size = dataInputStream.read(buffer)) != -1) {
                        byteOutputStream.write(buffer, 0, size);
                        //message = dataInputStream.readUTF();
                    }
                    message = byteOutputStream.toString();

                    final HashMap<String, String> responseMap = new HashMap<String, String>();
                    try {
                        JSONObject jsonObject = new JSONObject(message);
                        Iterator<String> keysItr = jsonObject.keys();
                        while (keysItr.hasNext()) {
                            String key = keysItr.next();
                            String value = "";
                            try {
                                value = (String) jsonObject.get(key);
                            } catch (Exception e) {}
                            responseMap.put(key, value);
                        }
                    } catch (JSONException e) {
                        e.printStackTrace();
                    }

                    if (responseMap.get("request").equalsIgnoreCase(REQUEST_CLIENT_DATA)) {
                        MainActivity.this.runOnUiThread(new Runnable() {
                            @Override
                            public void run() {
                                tvChat.append(responseMap.get("name") + ": " + responseMap.get("message") + "\n");
                                scrollView.fullScroll(ScrollView.FOCUS_DOWN);
                            }
                        });
                        sendMessage(responseMap.get("name"), REQUEST_SERVER_ACK, "ok");
                    } else if (responseMap.get("request").equalsIgnoreCase(REQUEST_SERVER_ACK)) {
                        if (responseMap.get("message").equalsIgnoreCase("ok")) {
                            MainActivity.this.runOnUiThread(new Runnable() {
                                @Override
                                public void run() {
                                    Toast.makeText(MainActivity.this, "Message delivered.", Toast.LENGTH_SHORT).show();
                                }
                            });
                        }
                    }


                } catch (IOException e) {
                    e.printStackTrace();
                    /*final String eString = e.toString();
                    MainActivity.this.runOnUiThread(new Runnable() {

                        @Override
                        public void run() {
                            Toast.makeText(MainActivity.this, eString, Toast.LENGTH_LONG).show();
                        }

                    });*/
                } finally {
                    if (socket != null) {
                        try {
                            socket.close();
                        } catch (IOException e) {
                            e.printStackTrace();
                        }
                    }

                    if (dataInputStream != null) {
                        try {
                            dataInputStream.close();
                        } catch (IOException e) {
                            e.printStackTrace();
                        }
                    }
                }
            }

        }

        public void close() {
            try {
                serverSocket.close();
            } catch (IOException e) {
                e.printStackTrace();
            }
        }

    }

    /** UI click handler */
    public void onClick(View clikedView) {
        switch (clikedView.getId()) {
            case R.id.send:
                if (mServiceMap.size() == 0) {
                    Toast.makeText(MainActivity.this, "No device found in the network.", Toast.LENGTH_LONG).show();
                    return;
                }
                final String[] serviceNamesArray = new String[mServiceMap.size()];
                mServiceMap.keySet().toArray(serviceNamesArray);
                new AlertDialog.Builder(this)
                        .setTitle("Available Devices")
                        .setSingleChoiceItems(serviceNamesArray, -1, new DialogInterface.OnClickListener() {
                            @Override
                            public void onClick(DialogInterface dialog, int which) {
                            }
                        })
                        .setPositiveButton("Send", new DialogInterface.OnClickListener() {
                            @Override
                            public void onClick(DialogInterface dialog, int which) {
                                int index = ((AlertDialog) dialog).getListView().getCheckedItemPosition();
                                if (index != -1) {
                                    sendMessage(serviceNamesArray[index], REQUEST_CLIENT_DATA, etMessage.getText().toString());
                                }
                            }
                        })
                        .setNegativeButton("Cancel", new DialogInterface.OnClickListener() {
                            @Override
                            public void onClick(DialogInterface dialog, int which) {

                            }
                        })
                        .create().show();
                break;
        }
    }

    public void sendMessage(String serviceName, String request, String message) {
        NsdServiceInfo service = mServiceMap.get(serviceName);
        if (service != null) {
            mResolveListener.setRequest(request);
            mResolveListener.setMessage(message);
            mNsdManager.resolveService(service, mResolveListener);
        }
    }
}
