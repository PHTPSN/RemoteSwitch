package com.example.remoteswitch;

import android.Manifest;
import android.bluetooth.BluetoothDevice;
import android.bluetooth.BluetoothGatt;
import android.bluetooth.BluetoothGattCallback;
import android.bluetooth.BluetoothGattCharacteristic;
import android.bluetooth.BluetoothGattDescriptor;
import android.bluetooth.BluetoothGattService;
import android.bluetooth.BluetoothProfile;
import android.content.Context;
import android.content.pm.PackageManager;
import android.util.Log;

import androidx.core.app.ActivityCompat;

import java.text.SimpleDateFormat;
import java.util.Date;
import java.util.Locale;
import java.util.UUID;

public class BleConnectManager {
    private static final String TAG = "BleConnectManager";

    private static UUID uuidFromShortUuid16(int uuid16) {
        // 128_bit_UUID = 16_bit_UUID * 2^96 + Bluetooth_Base_UUID(00000000-0000-1000-8000-00805F9B34FB)
        String uuidStr = String.format("%04X", uuid16);
        return UUID.fromString("0000" + uuidStr + "-0000-1000-8000-00805F9B34FB");
    }

    // UUIDs from the Arduino Sketch
    public static final UUID SERVICE_UUID_TIMESYNC = uuidFromShortUuid16(0x1805);
    public static final UUID CHARACTERISTIC_UUID_PHONETIME = uuidFromShortUuid16(0x2A2B);
    public static final UUID SERVICE_UUID_SERVOCONTROL = uuidFromShortUuid16(0x1815);
    public static final UUID CHARACTERISTIC_UUID_SERVOSIGNAL = uuidFromShortUuid16(0x2A56);

    // This UUID is standard for enabling notifications/indications
    public static final UUID CLIENT_CHARACTERISTIC_CONFIG_UUID = UUID.fromString("00002902-0000-1000-8000-00805f9b34fb");

    private final Context context;
    private final BluetoothDevice device;
    private final OnDeviceConnectedListener listener;
    private BluetoothGatt bluetoothGatt;

    private BluetoothGattCharacteristic phoneTimeCharacteristic;
    private BluetoothGattCharacteristic servoSignalCharacteristic;

    // Listener for connection events
    public interface OnDeviceConnectedListener {
        void onDeviceConnected(BluetoothDevice device);

        void onDeviceDisconnected();

        void onConnectFailed(String errorMessage);

        void onTimeSynced();
    }

    public BleConnectManager(Context context, BluetoothDevice device, OnDeviceConnectedListener listener) {
        this.context = context;
        this.device = device;
        this.listener = listener;
    }

    public void connect() {
        if (device == null) {
            listener.onConnectFailed("Device is null.");
            return;
        }
        if (ActivityCompat.checkSelfPermission(context, Manifest.permission.BLUETOOTH_CONNECT) != PackageManager.PERMISSION_GRANTED) {
            listener.onConnectFailed("Bluetooth Connect permission not granted.");
            return;
        }
        Log.d(TAG, "Connecting to GATT server.");
        // The third parameter 'false' means we are not using auto-connect.
        bluetoothGatt = device.connectGatt(context, false, gattCallback);
    }

    public void disconnect() {
        if (bluetoothGatt == null) {
            return;
        }
        if (ActivityCompat.checkSelfPermission(context, Manifest.permission.BLUETOOTH_CONNECT) != PackageManager.PERMISSION_GRANTED) {
            return; // Cannot disconnect without permission
        }
        Log.d(TAG, "Disconnecting from GATT server.");
        bluetoothGatt.disconnect();
    }

    // The main callback for GATT events
    private final BluetoothGattCallback gattCallback = new BluetoothGattCallback() {
        @Override
        public void onConnectionStateChange(BluetoothGatt gatt, int status, int newState) {
            if (ActivityCompat.checkSelfPermission(context, Manifest.permission.BLUETOOTH_CONNECT) != PackageManager.PERMISSION_GRANTED) {
                listener.onConnectFailed("Permissions missing for state change.");
                return;
            }

            if (newState == BluetoothProfile.STATE_CONNECTED) {
                Log.i(TAG, "Connected to GATT server.");
                // Discover services after a successful connection.
                gatt.discoverServices();
            } else if (newState == BluetoothProfile.STATE_DISCONNECTED) {
                Log.i(TAG, "Disconnected from GATT server.");
                listener.onDeviceDisconnected();
                close(); // Clean up resources
            }
        }

        @Override
        public void onServicesDiscovered(BluetoothGatt gatt, int status) {
            if (status == BluetoothGatt.GATT_SUCCESS) {
                Log.w(TAG, "Services discovered.");

                // 打印所有服务
                for (BluetoothGattService service : gatt.getServices()) {
                    Log.d(TAG, "Service UUID: " + service.getUuid());

                    // 打印每个服务的特征
                    for (BluetoothGattCharacteristic characteristic : service.getCharacteristics()) {
                        Log.d(TAG, "  Characteristic UUID: " + characteristic.getUuid());
                    }
                }

                // Get the servo control characteristic
                BluetoothGattService servoService = gatt.getService(SERVICE_UUID_SERVOCONTROL);
                if (servoService != null) {
                    servoSignalCharacteristic = servoService.getCharacteristic(CHARACTERISTIC_UUID_SERVOSIGNAL);
                    if (servoSignalCharacteristic == null)
                        Log.e(TAG, "Servo characteristic not found!");
                } else {
                    Log.e(TAG, "Servo service not found!");
                }

                // Get the time sync characteristic and enable notifications
                BluetoothGattService timeService = gatt.getService(SERVICE_UUID_TIMESYNC);
                if (timeService != null) {
                    phoneTimeCharacteristic = timeService.getCharacteristic(CHARACTERISTIC_UUID_PHONETIME);
                    if (phoneTimeCharacteristic == null) {
                        Log.e(TAG, "Time characteristic not found!");
                    }
                } else {
                    Log.e(TAG, "Time service not found!");
                }
            } else {
                Log.w(TAG, "onServicesDiscovered received: " + status);
            }

            // If both characteristics are found, notify onDeviceConnected listener.
            if (phoneTimeCharacteristic != null && servoSignalCharacteristic != null) {
                listener.onDeviceConnected(device);
            }
        }

        @Override
        public void onCharacteristicWrite(BluetoothGatt gatt, BluetoothGattCharacteristic characteristic, int status) {
            if (status == BluetoothGatt.GATT_SUCCESS) {
                if (CHARACTERISTIC_UUID_PHONETIME.equals(characteristic.getUuid())) {
                    Log.i(TAG, "Time successfully written to device.");
                    listener.onTimeSynced();
                }
                if (CHARACTERISTIC_UUID_SERVOSIGNAL.equals(characteristic.getUuid())) {
                    Log.i(TAG, "Servo command sent: " + new String(characteristic.getValue()));
                }
            }
        }
    };

    public void close() {
        if (bluetoothGatt == null) {
            return;
        }
        if (ActivityCompat.checkSelfPermission(context, Manifest.permission.BLUETOOTH_CONNECT) != PackageManager.PERMISSION_GRANTED) {
            return;
        }
        bluetoothGatt.close();
        bluetoothGatt = null;
    }

    public void writeCurrentTime() {
        if (phoneTimeCharacteristic == null || bluetoothGatt == null) {
            Log.e(TAG, "Cannot write time, characteristic or gatt is null.");
            return;
        }
        if (ActivityCompat.checkSelfPermission(context, Manifest.permission.BLUETOOTH_CONNECT) != PackageManager.PERMISSION_GRANTED) {
            return;
        }
        // Format time as "HH:MM:SS"
        SimpleDateFormat sdf = new SimpleDateFormat("HH:mm:ss", Locale.getDefault());
        String currentTime = sdf.format(new Date());

        phoneTimeCharacteristic.setValue(currentTime);
        phoneTimeCharacteristic.setWriteType(BluetoothGattCharacteristic.WRITE_TYPE_DEFAULT);
        bluetoothGatt.writeCharacteristic(phoneTimeCharacteristic);
    }

    public void sendServoCommand(String command) {
        if (servoSignalCharacteristic == null || bluetoothGatt == null) {
            Log.e(TAG, "Cannot send command, characteristic or gatt is null.");
            return;
        }
        if (ActivityCompat.checkSelfPermission(context, Manifest.permission.BLUETOOTH_CONNECT) != PackageManager.PERMISSION_GRANTED) {
            return;
        }

        if (command.equals("on")) {
            servoSignalCharacteristic.setValue("1");
        } else if (command.equals("off")) {
            servoSignalCharacteristic.setValue("0");
        } else return;

        servoSignalCharacteristic.setWriteType(BluetoothGattCharacteristic.WRITE_TYPE_DEFAULT);
        bluetoothGatt.writeCharacteristic(servoSignalCharacteristic);
    }
}

