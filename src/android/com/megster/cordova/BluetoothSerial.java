package com.megster.cordova;

import android.Manifest;
import android.content.pm.PackageManager;

import android.app.Activity;
import android.bluetooth.BluetoothAdapter;
import android.bluetooth.BluetoothDevice;
import android.content.BroadcastReceiver;
import android.content.Context;
import android.content.Intent;
import android.content.IntentFilter;
import android.os.Handler;
import android.os.Message;
import android.provider.Settings;
import android.util.Log;
import org.apache.cordova.CordovaArgs;
import org.apache.cordova.CordovaPlugin;
import org.apache.cordova.CallbackContext;
import org.apache.cordova.PluginResult;
import org.apache.cordova.LOG;
import org.json.JSONArray;
import org.json.JSONException;
import org.json.JSONObject;

import java.util.HashMap;
import java.util.Set;

/**
 * PhoneGap Plugin for Serial Communication over Bluetooth
 */
public class BluetoothSerial extends CordovaPlugin {



    private class ConnectionContext{

        // callbacks
        public CallbackContext connectCallback;
        public CallbackContext dataAvailableCallback;
        public CallbackContext rawDataAvailableCallback;


        public BluetoothSerialService bluetoothSerialService;


        StringBuffer buffer = new StringBuffer();
        private String delimiter;


        public ConnectionContext()
        {
            bluetoothSerialService = new BluetoothSerialService(mHandler);
        }

        private void notifyConnectionLost(String error) {
            if (connectCallback != null) {
                connectCallback.error(error);
                connectCallback = null;
            }
        }

        private void notifyConnectionSuccess() {
            if (connectCallback != null) {
                PluginResult result = new PluginResult(PluginResult.Status.OK);
                result.setKeepCallback(true);
                connectCallback.sendPluginResult(result);
            }
        }

        private void sendRawDataToSubscriber(byte[] data) {
            if (data != null && data.length > 0) {
                PluginResult result = new PluginResult(PluginResult.Status.OK, data);
                result.setKeepCallback(true);
                rawDataAvailableCallback.sendPluginResult(result);
            }
        }

        private void sendDataToSubscriber() {
            String data = readUntil(delimiter);
            if (data != null && data.length() > 0) {
                PluginResult result = new PluginResult(PluginResult.Status.OK, data);
                result.setKeepCallback(true);
                dataAvailableCallback.sendPluginResult(result);

                sendDataToSubscriber();
            }
        }

        private int available() {
            return buffer.length();
        }

        private String read() {
            int length = buffer.length();
            String data = buffer.substring(0, length);
            buffer.delete(0, length);
            return data;
        }

        private String readUntil(String c) {
            String data = "";
            int index = buffer.indexOf(c, 0);
            if (index > -1) {
                data = buffer.substring(0, index + c.length());
                buffer.delete(0, index + c.length());
            }
            return data;
        }

        // The Handler that gets information back from the BluetoothSerialService
        // Original code used handler for the because it was talking to the UI.
        // Consider replacing with normal callbacks
        private final Handler mHandler = new Handler() {

            public void handleMessage(Message msg) {
                switch (msg.what) {
                    case MESSAGE_READ:
                        buffer.append((String)msg.obj);

                        if (dataAvailableCallback != null) {
                            sendDataToSubscriber();
                        }

                        break;
                    case MESSAGE_READ_RAW:
                        if (rawDataAvailableCallback != null) {
                            byte[] bytes = (byte[]) msg.obj;
                            sendRawDataToSubscriber(bytes);
                        }
                        break;
                    case MESSAGE_STATE_CHANGE:

                        if(D) Log.i(TAG, "MESSAGE_STATE_CHANGE: " + msg.arg1);
                        switch (msg.arg1) {
                            case BluetoothSerialService.STATE_CONNECTED:
                                Log.i(TAG, "BluetoothSerialService.STATE_CONNECTED");
                                notifyConnectionSuccess();
                                break;
                            case BluetoothSerialService.STATE_CONNECTING:
                                Log.i(TAG, "BluetoothSerialService.STATE_CONNECTING");
                                break;
                            case BluetoothSerialService.STATE_LISTEN:
                                Log.i(TAG, "BluetoothSerialService.STATE_LISTEN");
                                break;
                            case BluetoothSerialService.STATE_NONE:
                                Log.i(TAG, "BluetoothSerialService.STATE_NONE");
                                break;
                        }
                        break;
                    case MESSAGE_WRITE:
                        //  byte[] writeBuf = (byte[]) msg.obj;
                        //  String writeMessage = new String(writeBuf);
                        //  Log.i(TAG, "Wrote: " + writeMessage);
                        break;
                    case MESSAGE_DEVICE_NAME:
                        Log.i(TAG, msg.getData().getString(DEVICE_NAME));
                        break;
                    case MESSAGE_TOAST:
                        String message = msg.getData().getString(TOAST);
                        notifyConnectionLost(message);
                        break;
                }
            }
        };
    }

    // actions
    private static final String LIST = "list";
    private static final String CONNECT = "connect";
    private static final String CONNECT_INSECURE = "connectInsecure";
    private static final String DISCONNECT = "disconnect";
    private static final String WRITE = "write";
    private static final String AVAILABLE = "available";
    private static final String READ = "read";
    private static final String READ_UNTIL = "readUntil";
    private static final String SUBSCRIBE = "subscribe";
    private static final String UNSUBSCRIBE = "unsubscribe";
    private static final String SUBSCRIBE_RAW = "subscribeRaw";
    private static final String UNSUBSCRIBE_RAW = "unsubscribeRaw";
    private static final String IS_ENABLED = "isEnabled";
    private static final String IS_CONNECTED = "isConnected";
    private static final String CLEAR = "clear";
    private static final String SETTINGS = "showBluetoothSettings";
    private static final String ENABLE = "enable";
    private static final String DISCOVER_UNPAIRED = "discoverUnpaired";
    private static final String SET_DEVICE_DISCOVERED_LISTENER = "setDeviceDiscoveredListener";
    private static final String CLEAR_DEVICE_DISCOVERED_LISTENER = "clearDeviceDiscoveredListener";
    private static final String SET_NAME = "setName";
    private static final String SET_DISCOVERABLE = "setDiscoverable";


    private BluetoothAdapter bluetoothAdapter;
    private HashMap<String,ConnectionContext> connections = new HashMap<String, ConnectionContext>();
    String defaultMac="";


    // callbacks
    public CallbackContext enableBluetoothCallback;
    public CallbackContext deviceDiscoveredCallback;

    // Debugging
    private static final String TAG = "BluetoothSerial";
    private static final boolean D = true;

    // Message types sent from the BluetoothSerialService Handler
    public static final int MESSAGE_STATE_CHANGE = 1;
    public static final int MESSAGE_READ = 2;
    public static final int MESSAGE_WRITE = 3;
    public static final int MESSAGE_DEVICE_NAME = 4;
    public static final int MESSAGE_TOAST = 5;
    public static final int MESSAGE_READ_RAW = 6;

    // Key names received from the BluetoothChatService Handler
    public static final String DEVICE_NAME = "device_name";
    public static final String TOAST = "toast";

    private static final int REQUEST_ENABLE_BLUETOOTH = 1;

    // Android 23 requires user to explicitly grant permission for location to discover unpaired
    private static final String ACCESS_COARSE_LOCATION = Manifest.permission.ACCESS_COARSE_LOCATION;
    private static final int CHECK_PERMISSIONS_REQ_CODE = 2;
    private CallbackContext permissionCallback;

    @Override
    public boolean execute(String action, CordovaArgs args, CallbackContext callbackContext) throws JSONException {

        LOG.d(TAG, "action = " + action);

        if (bluetoothAdapter == null) {
            bluetoothAdapter = BluetoothAdapter.getDefaultAdapter();
        }

        ConnectionContext cc=null;

        if(!defaultMac.isEmpty())
        {
            cc=connections.get(defaultMac);
        }
        else
        {
            //prevent crash if no connect has been called
            cc = new ConnectionContext();
        }

        boolean validAction = true;

        if (action.equals(LIST)) {

            listBondedDevices(callbackContext);

        } else if (action.equals(CONNECT)) {

            boolean secure = true;
            connect(args, secure, callbackContext);

        } else if (action.equals(CONNECT_INSECURE)) {

            // see Android docs about Insecure RFCOMM http://goo.gl/1mFjZY
            boolean secure = false;
            connect(args, secure, callbackContext);

        } else if (action.equals(DISCONNECT)) {
            if(!args.isNull(0))
            {
                cc=connections.get(args.getString(0));
                if(cc==null)
                {
                    callbackContext.error("Address " + args.getString(0)+" never used before");
                }
            }
            if(cc!=null)
            {
                cc.connectCallback = null;
                cc.bluetoothSerialService.stop("action.equals(DISCONNECT)");
                callbackContext.success();
            }

        } else if (action.equals(WRITE)) {
            if(!args.isNull(1))
            {
                cc=connections.get(args.getString(1));
                if(cc==null)
                {
                    callbackContext.error("Address " + args.getString(0)+" never used before");
                }
            }

            if(cc!=null) {
                byte[] data = args.getArrayBuffer(0);
                cc.bluetoothSerialService.write(data);
                callbackContext.success();
            }

        } else if (action.equals(AVAILABLE)) {
            if(!args.isNull(0))
            {
                cc=connections.get(args.getString(0));
                if(cc==null)
                {
                    callbackContext.error("Address " + args.getString(0)+" never used before");
                }
            }

            if(cc!=null) {
                callbackContext.success(cc.available());
            }

        } else if (action.equals(READ)) {
            if(!args.isNull(0))
            {
                cc=connections.get(args.getString(0));
                if(cc==null)
                {
                    callbackContext.error("Address " + args.getString(0)+" never used before");
                }
            }

            if(cc!=null) {
                callbackContext.success(cc.read());
            }

        } else if (action.equals(READ_UNTIL)) {
            if(!args.isNull(1))
            {
                cc=connections.get(args.getString(1));
                if(cc==null)
                {
                    callbackContext.error("Address " + args.getString(0)+" never used before");
                }
            }

            if(cc!=null) {
                String interesting = args.getString(0);
                callbackContext.success(cc.readUntil(interesting));
            }

        } else if (action.equals(SUBSCRIBE)) {
            if(!args.isNull(1))
            {
                cc=connections.get(args.getString(1));
                if(cc==null)
                {
                    callbackContext.error("Address " + args.getString(0)+" never used before");
                }
            }

            if(cc!=null) {
                cc.delimiter = args.getString(0);
                cc.dataAvailableCallback = callbackContext;

                PluginResult result = new PluginResult(PluginResult.Status.NO_RESULT);
                result.setKeepCallback(true);
                callbackContext.sendPluginResult(result);
            }

        } else if (action.equals(UNSUBSCRIBE)) {
            if(!args.isNull(0))
            {
                cc=connections.get(args.getString(0));
                if(cc==null)
                {
                    callbackContext.error("Address " + args.getString(0)+" never used before");
                }
            }

            if(cc!=null) {
                cc.delimiter = null;

                // send no result, so Cordova won't hold onto the data available callback anymore
                PluginResult result = new PluginResult(PluginResult.Status.NO_RESULT);
                cc.dataAvailableCallback.sendPluginResult(result);
                cc.dataAvailableCallback = null;

                callbackContext.success();
            }

        } else if (action.equals(SUBSCRIBE_RAW)) {
            if(!args.isNull(0))
            {
                cc=connections.get(args.getString(0));
                if(cc==null)
                {
                    callbackContext.error("Address " + args.getString(0)+" never used before");
                }
            }

            if(cc!=null) {
                cc.rawDataAvailableCallback = callbackContext;

                PluginResult result = new PluginResult(PluginResult.Status.NO_RESULT);
                result.setKeepCallback(true);
                callbackContext.sendPluginResult(result);
            }

        } else if (action.equals(UNSUBSCRIBE_RAW)) {
            if(!args.isNull(0))
            {
                cc=connections.get(args.getString(0));
                if(cc==null)
                {
                    callbackContext.error("Address " + args.getString(0)+" never used before");
                }
            }

            if(cc!=null) {
                cc.rawDataAvailableCallback = null;

                callbackContext.success();
            }

        } else if (action.equals(IS_ENABLED)) {

            if (bluetoothAdapter.isEnabled()) {
                callbackContext.success();
            } else {
                callbackContext.error("Bluetooth is disabled.");
            }

        } else if (action.equals(IS_CONNECTED)) {
            if(!args.isNull(0))
            {
                cc=connections.get(args.getString(0));

            }

            if (cc!=null && cc.bluetoothSerialService.getState() == BluetoothSerialService.STATE_CONNECTED) {
                callbackContext.success();
            } else {
                callbackContext.error("Not connected.");
            }

        } else if (action.equals(CLEAR)) {
            if(!args.isNull(0))
            {
                cc=connections.get(args.getString(0));
                if(cc==null)
                {
                    callbackContext.error("Address " + args.getString(0)+" never used before");
                }
            }

            if(cc!=null) {
                cc.buffer.setLength(0);
                callbackContext.success();
            }

        } else if (action.equals(SETTINGS)) {

            Intent intent = new Intent(Settings.ACTION_BLUETOOTH_SETTINGS);
            cordova.getActivity().startActivity(intent);
            callbackContext.success();

        } else if (action.equals(ENABLE)) {

            enableBluetoothCallback = callbackContext;
            Intent intent = new Intent(BluetoothAdapter.ACTION_REQUEST_ENABLE);
            cordova.startActivityForResult(this, intent, REQUEST_ENABLE_BLUETOOTH);

        } else if (action.equals(DISCOVER_UNPAIRED)) {
            callbackContext.error("Uncomment android.permission.ACCESS_COARSE_LOCATION in plugin.xml");
            if (cordova.hasPermission(ACCESS_COARSE_LOCATION)) {
                discoverUnpairedDevices(callbackContext);
            } else {
                permissionCallback = callbackContext;
                cordova.requestPermission(this, CHECK_PERMISSIONS_REQ_CODE, ACCESS_COARSE_LOCATION);
            }

        } else if (action.equals(SET_DEVICE_DISCOVERED_LISTENER)) {

            this.deviceDiscoveredCallback = callbackContext;

        } else if (action.equals(CLEAR_DEVICE_DISCOVERED_LISTENER)) {

            this.deviceDiscoveredCallback = null;

        } else if (action.equals(SET_NAME)) {

            String newName = args.getString(0);
            bluetoothAdapter.setName(newName);
            callbackContext.success();

        } else if (action.equals(SET_DISCOVERABLE)) {

            int discoverableDuration = args.getInt(0);
            Intent discoverIntent = new Intent(BluetoothAdapter.ACTION_REQUEST_DISCOVERABLE);
            discoverIntent.putExtra(BluetoothAdapter.EXTRA_DISCOVERABLE_DURATION, discoverableDuration);
            cordova.getActivity().startActivity(discoverIntent);

        } else {
            validAction = false;

        }

        return validAction;
    }

    @Override
    public void onActivityResult(int requestCode, int resultCode, Intent data) {

        if (requestCode == REQUEST_ENABLE_BLUETOOTH) {

            if (resultCode == Activity.RESULT_OK) {
                Log.d(TAG, "User enabled Bluetooth");
                if (enableBluetoothCallback != null) {
                    enableBluetoothCallback.success();
                }
            } else {
                Log.d(TAG, "User did *NOT* enable Bluetooth");
                if (enableBluetoothCallback != null) {
                    enableBluetoothCallback.error("User did not enable Bluetooth");
                }
            }

            enableBluetoothCallback = null;
        }
    }

    @Override
    public void onDestroy() {
        super.onDestroy();
        for(ConnectionContext conn : connections.values())
        {
            conn.bluetoothSerialService.stop("onDestroy");

        }
    }

    private void listBondedDevices(CallbackContext callbackContext) throws JSONException {
        JSONArray deviceList = new JSONArray();
        Set<BluetoothDevice> bondedDevices = bluetoothAdapter.getBondedDevices();

        for (BluetoothDevice device : bondedDevices) {
            deviceList.put(deviceToJSON(device));
        }
        callbackContext.success(deviceList);
    }

    private void discoverUnpairedDevices(final CallbackContext callbackContext) throws JSONException {

        final CallbackContext ddc = deviceDiscoveredCallback;

        final BroadcastReceiver discoverReceiver = new BroadcastReceiver() {

            private JSONArray unpairedDevices = new JSONArray();

            public void onReceive(Context context, Intent intent) {
                String action = intent.getAction();
                if (BluetoothDevice.ACTION_FOUND.equals(action)) {
                    BluetoothDevice device = intent.getParcelableExtra(BluetoothDevice.EXTRA_DEVICE);
                    try {
                        JSONObject o = deviceToJSON(device);
                        unpairedDevices.put(o);
                        if (ddc != null) {
                            PluginResult res = new PluginResult(PluginResult.Status.OK, o);
                            res.setKeepCallback(true);
                            ddc.sendPluginResult(res);
                        }
                    } catch (JSONException e) {
                        // This shouldn't happen, log and ignore
                        Log.e(TAG, "Problem converting device to JSON", e);
                    }
                } else if (BluetoothAdapter.ACTION_DISCOVERY_FINISHED.equals(action)) {
                    callbackContext.success(unpairedDevices);
                    cordova.getActivity().unregisterReceiver(this);
                }
            }
        };

        Activity activity = cordova.getActivity();
        activity.registerReceiver(discoverReceiver, new IntentFilter(BluetoothDevice.ACTION_FOUND));
        activity.registerReceiver(discoverReceiver, new IntentFilter(BluetoothAdapter.ACTION_DISCOVERY_FINISHED));
        bluetoothAdapter.startDiscovery();
    }

    private JSONObject deviceToJSON(BluetoothDevice device) throws JSONException {
        JSONObject json = new JSONObject();
        json.put("name", device.getName());
        json.put("address", device.getAddress());
        json.put("id", device.getAddress());
        if (device.getBluetoothClass() != null) {
            json.put("class", device.getBluetoothClass().getDeviceClass());
        }
        return json;
    }

    private void connect(CordovaArgs args, boolean secure, CallbackContext callbackContext) throws JSONException {
        String macAddress = args.getString(0);
        boolean tryFallback = args.getBoolean(1);
        BluetoothDevice device = bluetoothAdapter.getRemoteDevice(macAddress);
        defaultMac=macAddress;
        if (!connections.containsKey(macAddress)) {
            connections.put(macAddress, new ConnectionContext());
        }
        ConnectionContext cc=connections.get(macAddress);

        if (device != null) {
            cc.connectCallback = callbackContext;
            cc.bluetoothSerialService.connect(device, secure, tryFallback);
            cc.buffer.setLength(0);

            PluginResult result = new PluginResult(PluginResult.Status.NO_RESULT);
            result.setKeepCallback(true);
            callbackContext.sendPluginResult(result);

        } else {
            callbackContext.error("Could not connect to " + macAddress);
        }
    }

    @Override
    public void onRequestPermissionResult(int requestCode, String[] permissions,
                                          int[] grantResults) throws JSONException {

        for(int result:grantResults) {
            if(result == PackageManager.PERMISSION_DENIED) {
                LOG.d(TAG, "User *rejected* location permission");
                this.permissionCallback.sendPluginResult(new PluginResult(
                        PluginResult.Status.ERROR,
                        "Location permission is required to discover unpaired devices.")
                    );
                return;
            }
        }

        switch(requestCode) {
            case CHECK_PERMISSIONS_REQ_CODE:
                LOG.d(TAG, "User granted location permission");
                discoverUnpairedDevices(permissionCallback);
                break;
        }
    }
}
