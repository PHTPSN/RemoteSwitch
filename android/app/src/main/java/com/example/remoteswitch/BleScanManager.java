/*
Perhaps needless to mention,
but make sure you do all your Bluetooth stuff outside of an Activity.
Activities get created and recreated many times by Android,
so if you do your scanning in an Activity the scan may be started several times.
Or worse, you connection may break because Android decided to recreate your Activity…you have been warned!
*/

package com.example.remoteswitch;

import android.Manifest;
import android.bluetooth.BluetoothAdapter;
import android.bluetooth.BluetoothDevice;
import android.bluetooth.le.BluetoothLeScanner;
import android.bluetooth.le.ScanCallback;
import android.bluetooth.le.ScanFilter;
import android.bluetooth.le.ScanResult;
import android.bluetooth.le.ScanSettings;
import android.content.Context;
import android.content.pm.PackageManager;
import android.os.Handler;
import android.os.Looper;
import android.util.Log;

import androidx.core.app.ActivityCompat;

import java.util.Collections;
import java.util.List;

public class BleScanManager {

    private static final String TAG = "BleScanManager";
    private static final long SCAN_PERIOD = 15000; // Stops scanning after 15 seconds.

    private final BluetoothLeScanner bluetoothLeScanner;
    private final Handler scanHandler = new Handler(Looper.getMainLooper());
    private final Context context;


    private final OnDeviceFoundListener listener;

    private boolean isScanning = false;

    // Listener interface for scan results
    public interface OnDeviceFoundListener {
        void onDeviceFound(BluetoothDevice device);
        void onScanFailed(String errorMessage);
    }

    public BleScanManager(Context context, BluetoothAdapter bluetoothAdapter, OnDeviceFoundListener listener) {
        this.context = context;
        this.listener = listener;
        if (bluetoothAdapter == null) {
            throw new IllegalStateException("Bluetooth not supported on this device.");
        }
        this.bluetoothLeScanner = bluetoothAdapter.getBluetoothLeScanner();
    }

    public void startScan(String DEVICE_NAME) {
        if (isScanning) {
            Log.d(TAG, "Scan already in progress.");
            return;
        }

        // Check for BLUETOOTH_SCAN permission before scanning
        if (ActivityCompat.checkSelfPermission(context, Manifest.permission.BLUETOOTH_SCAN) != PackageManager.PERMISSION_GRANTED) {
            listener.onScanFailed(context.getString(R.string.need_permission));
            return;
        }

        ScanFilter scanFilter = new ScanFilter.Builder()
                .setDeviceName(DEVICE_NAME)
                .build();
        List<ScanFilter> filters = Collections.singletonList(scanFilter);

        ScanSettings scanSettings = new ScanSettings.Builder()
                .setScanMode(ScanSettings.SCAN_MODE_LOW_LATENCY)
                .build();

        scanHandler.postDelayed(() -> {
            if (isScanning) {
                stopScan();
                listener.onScanFailed(context.getString(R.string.scan_timeout));
            }
        }, SCAN_PERIOD);

        isScanning = true;
        bluetoothLeScanner.startScan(filters, scanSettings, leScanCallback);
        Log.d(TAG, "Scan started.");
    }

    public void stopScan() {
        if (!isScanning) return;

        if (ActivityCompat.checkSelfPermission(context, Manifest.permission.BLUETOOTH_SCAN) != PackageManager.PERMISSION_GRANTED) {
            listener.onScanFailed(context.getString(R.string.need_permission));
            return;
        }
        isScanning = false;
        scanHandler.removeCallbacksAndMessages(null); // Remove timeout handler
        bluetoothLeScanner.stopScan(leScanCallback); // A BluetoothLeScanner may have several scans at one time, so we must indicate which one to stop.
        Log.d(TAG, "Scan stopped.");
    }

    private final ScanCallback leScanCallback = new ScanCallback() {

        @Override
        public void onScanResult(int callbackType, ScanResult result) {
            super.onScanResult(callbackType, result);
            Log.d(TAG, "Device found: " + result.getDevice()    // 你都能通过设备名称匹配了，`.getName()` 怎么还要 BLUETOOTH_CONNECT 权限啊？
                    + ", MAC: " + result.getDevice().getAddress()
                    + ", RSSI: " + result.getRssi() + "dBm");
            stopScan();
            listener.onDeviceFound(result.getDevice());
        }

        @Override
        public void onScanFailed(int errorCode) {
            super.onScanFailed(errorCode);
            Log.e(TAG, "Scan failed with error code: " + errorCode);
            isScanning = false;
            listener.onScanFailed(context.getString(R.string.scan_failed, String.valueOf(errorCode)));
        }
    };
}