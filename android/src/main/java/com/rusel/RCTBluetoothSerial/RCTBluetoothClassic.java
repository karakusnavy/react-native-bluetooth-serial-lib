package com.rusel.RCTBluetoothSerial;

import android.app.Activity;
import android.bluetooth.BluetoothAdapter;
import android.bluetooth.BluetoothDevice;
import android.bluetooth.BluetoothServerSocket;
import android.bluetooth.BluetoothSocket;
import android.content.Intent;
import android.os.Handler;
import android.os.Looper;
import android.os.Message;

import android.util.Log;

import com.facebook.react.bridge.Arguments;
import com.facebook.react.bridge.ReactApplicationContext;
import com.facebook.react.bridge.ReactContext;
import com.facebook.react.bridge.ReactContextBaseJavaModule;
import com.facebook.react.bridge.WritableMap;
import com.facebook.react.modules.core.DeviceEventManagerModule;

import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.util.Set;
import java.util.UUID;

import javax.annotation.Nonnull;

public class RCTBluetoothClassic extends ReactContextBaseJavaModule {
    BluetoothAdapter bluetoothAdapter;
    BluetoothDevice[] btArray;
    public Handler handler;

    SendReceive sendReceive;

    private static final UUID MY_UUID=UUID.fromString("8ce255c0-223a-11e0-ac64-0803450c9a66");

    public static final String TAG = "RCTBC";

    int REQUEST_ENABLE_BLUETOOTH = 1;
    int REQUEST_DISCOVERABLE = 2;


    int SERVER_TIMEOUT_SEC = 5000;

    static final int STATE_LISTENING = 1;
    static final int STATE_CONNECTING=2;
    static final int STATE_CONNECTED=3;
    static final int STATE_CONNECTION_FAILED=4;
    static final int STATE_MESSAGE_RECEIVED=5;
    static final int STATE_CONNECTION_LOST=6;

    private ClientClass clientTask;
    private ServerClass serverTask;


    RCTBluetoothClassic(ReactApplicationContext reactContext) {
        super(reactContext);
        bluetoothAdapter = BluetoothAdapter.getDefaultAdapter();
        if (bluetoothAdapter != null) {
            if (!bluetoothAdapter.isEnabled()) {
                Activity activity = getCurrentActivity();
                Intent intent = new Intent(BluetoothAdapter.ACTION_REQUEST_ENABLE);
                if (activity != null) {
                    activity.startActivityForResult(intent, REQUEST_ENABLE_BLUETOOTH);
                } else {
                    Exception e = new Exception("Cannot start activity");
                    Log.e(TAG, "Cannot start activity", e);
                }
            }
            handlerMessageThread();
        }
    }

    public void sendData(String message) {
        byte[] bytes = message.getBytes();
        try {
            sendReceive.write(bytes);
        } catch (Exception e) {
            Log.e(TAG, e.getMessage());
        }
    }

    public void startServer() {
        if (bluetoothAdapter != null) {
            serverTask = new ServerClass();
            serverTask.start();
        }
    }

    public void clientServer() {
        if (bluetoothAdapter != null) {
            Set<BluetoothDevice> bt = bluetoothAdapter.getBondedDevices();
            String[] strings = new String[bt.size()];
            btArray = new BluetoothDevice[bt.size()];
            int index = 0;

            if (bt.size() > 0) {
                for (BluetoothDevice device : bt) {
                    btArray[index] = device;
                    strings[index] = device.getName();
                    clientTask = new ClientClass(device);
                    clientTask.start();
                    index++;
                }
            }
        }
    }

    public void emitEvent (String tempMsg) {
        WritableMap payload = Arguments.createMap();
        payload.putString("tempMsg", tempMsg);
        ReactContext reactContext = getReactApplicationContext();
        reactContext
                .getJSModule(DeviceEventManagerModule.RCTDeviceEventEmitter.class)
                .emit("BluetoothResponseMessage", payload);
    }

    public void emitStatus (String status) {
        WritableMap payload = Arguments.createMap();
        payload.putString("status", status);
        ReactContext reactContext = getReactApplicationContext();
        reactContext
                .getJSModule(DeviceEventManagerModule.RCTDeviceEventEmitter.class)
                .emit("BluetoothConnectingStatus", payload);
    }

    public void handlerMessageThread () {
        Thread thread = new Thread(){
            public void run(){
                Looper.prepare();//Call looper.prepare()

                handler=new Handler(Looper.getMainLooper(), new Handler.Callback() {
                    @Override
                    public boolean handleMessage(Message msg) {

                        switch (msg.what)
                        {
                            case STATE_LISTENING:
                                Log.d(TAG,"Listening");
                                emitStatus("Listening");
                                break;
                            case STATE_CONNECTING:
                                Log.d(TAG,"Connecting");
                                emitStatus("Connecting");
                                break;
                            case STATE_CONNECTED:
                                Log.d(TAG,"Connected");
                                emitStatus("Connected");
                                break;
                            case STATE_CONNECTION_LOST:
                                Log.d(TAG,"Connection Lost");
                                emitStatus("Connection Lost");
                                break;
                            case STATE_CONNECTION_FAILED:
                                Log.d(TAG,"Connection Failed");
                                emitStatus("Connection Failed");
                                break;
                            case STATE_MESSAGE_RECEIVED:
                                byte[] readBuff= (byte[]) msg.obj;
                                String tempMsg=new String(readBuff,0,msg.arg1);
                                emitEvent(tempMsg);
                                Log.e(TAG, tempMsg);
                                break;
                        }
                        return true;
                    }
                });

                Looper.loop();
            }
        };
        thread.start();
    }

    @Nonnull
    @Override
    public String getName() {
        return null;
    }

    private class ServerClass extends Thread
    {
        private BluetoothServerSocket serverSocket;

        public ServerClass(){
            try {
                serverSocket=bluetoothAdapter.listenUsingRfcommWithServiceRecord("BTConnection",MY_UUID);
            } catch (IOException e) {
                e.printStackTrace();
            }
        }

        public void run()
        {
            BluetoothSocket socket=null;

            while (socket==null)
            {
                try {
                    Message message=Message.obtain();
                    message.what=STATE_LISTENING;
                    handler.sendMessage(message);

                    socket=serverSocket.accept();
                } catch (IOException e) {
                    e.printStackTrace();
                    Message message=Message.obtain();
                    message.what=STATE_CONNECTION_FAILED;
                    handler.sendMessage(message);
                }

                if(socket!=null)
                {
                    Message message=Message.obtain();
                    message.what=STATE_CONNECTED;
                    handler.sendMessage(message);

                    sendReceive=new SendReceive(socket);
                    sendReceive.start();

                    break;
                }
            }
        }
    }

    private class ClientClass extends Thread
    {
        private BluetoothDevice device;
        private BluetoothSocket socket;

        public ClientClass (BluetoothDevice device1)
        {
            device=device1;

            try {
                socket=device.createRfcommSocketToServiceRecord(MY_UUID);
                Message message=Message.obtain();
                message.what=STATE_CONNECTING;
                handler.sendMessage(message);
            } catch (IOException e) {
                e.printStackTrace();
            }
        }

        public void run()
        {
            try {
                socket.connect();
                Message message=Message.obtain();
                message.what=STATE_CONNECTED;
                handler.sendMessage(message);

                sendReceive=new SendReceive(socket);
                sendReceive.start();

            } catch (IOException e) {
                try {
                    socket.close();
                } catch (IOException ex) {
                    ex.printStackTrace();
                }
                e.printStackTrace();
                Message message=Message.obtain();
                message.what=STATE_CONNECTION_FAILED;
                handler.sendMessage(message);
            }
        }
    }

    private class SendReceive extends Thread
    {
        private final BluetoothSocket bluetoothSocket;
        private final InputStream inputStream;
        private final OutputStream outputStream;

        public SendReceive (BluetoothSocket socket)
        {
            bluetoothSocket=socket;
            InputStream tempIn=null;
            OutputStream tempOut=null;

            try {
                tempIn=bluetoothSocket.getInputStream();
                tempOut=bluetoothSocket.getOutputStream();
            } catch (IOException e) {
                e.printStackTrace();
            }

            inputStream=tempIn;
            outputStream=tempOut;
        }

        public void run()
        {
            byte[] buffer=new byte[1024];
            int bytes;

            while (true)
            {
                try {
                    bytes=inputStream.read(buffer);
                    handler.obtainMessage(STATE_MESSAGE_RECEIVED,bytes,-1,buffer).sendToTarget();
                    Log.d(TAG, "input");
                } catch (IOException e) {
                    try {
                        bluetoothSocket.close();
                    } catch (IOException ex) {
                        ex.printStackTrace();
                    }
                    Message message=Message.obtain();
                    message.what=STATE_CONNECTION_LOST;
                    handler.sendMessage(message);
                    e.printStackTrace();
                }
            }
        }

        public void write(byte[] bytes)
        {
            Log.d(TAG, bytes.toString());
            try {
                outputStream.write(bytes);
            } catch (IOException e) {
                e.printStackTrace();
            }
        }
    }
}

