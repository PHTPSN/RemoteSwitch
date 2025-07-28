package com.example.remoteswitch;

import android.Manifest;
import android.annotation.SuppressLint;
import android.bluetooth.BluetoothAdapter;
import android.content.BroadcastReceiver;
import android.content.Context;
import android.content.Intent;
import android.content.IntentFilter;
import android.content.SharedPreferences;
import android.content.pm.PackageManager;
import android.os.Build;
import android.os.Bundle;
import android.os.Handler;
import android.os.Looper;
import android.util.Log;
import android.widget.Button;
import android.widget.TextView;

import androidx.annotation.NonNull;
import androidx.appcompat.app.AppCompatActivity;
import androidx.core.app.ActivityCompat;

import android.bluetooth.BluetoothDevice;
import android.widget.Toast;

import java.lang.reflect.Method;
import java.util.Set;

@SuppressLint("SetTextI18n")
public class MainActivity extends AppCompatActivity
        implements BleScanManager.OnDeviceFoundListener,
                   BleConnectManager.OnDeviceConnectedListener {
    private static final String TAG = "MainActivity";
    private Button scanButton, connectButton, disconnectButton, onButton, offButton, resetButton, helpButton;
    private TextView statusTextView;

    private BleScanManager bleScanManager;
    private BleConnectManager bleConnectManager;
    private BluetoothAdapter bluetoothAdapter;
    private BluetoothDevice device;
    private final String DEVICE_NAME = "Remote Switch";
    private static final String DEVICE_ADDRESS_KEY_NAME = "device_address";
    private static final int BLUETOOTH_PERMISSION_REQUEST_CODE = 100;


    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_main);

        // Request permissions
        requestBluetoothPermissions();

        initializeStatus();

        // Set button listeners
        scanButton.setOnClickListener(v -> {
            if (checkSelfPermission(Manifest.permission.BLUETOOTH_SCAN) != PackageManager.PERMISSION_GRANTED) {
                requestBluetoothPermissions();
            }
            statusTextView.setText(getString(R.string.status_scanning));
            scanButton.setEnabled(false); // Disable button during scan
            bleScanManager.startScan(DEVICE_NAME);
        });



        resetButton.setOnClickListener(v -> {
            clearSavedDevice();
            // Delay 0.5s for disconnecting
            new Handler(Looper.getMainLooper()).postDelayed(this::initializeStatus, 500);
        });

        helpButton.setOnClickListener(v -> {
            Intent intent = new Intent(MainActivity.this, HelpActivity.class);
            startActivity(intent);
        });

        connectButton.setOnClickListener(v -> {
            if (device == null) {
                Toast.makeText(this, getString(R.string.no_device_to_connect), Toast.LENGTH_SHORT).show();
                return;
            }
            statusTextView.setText(getString(R.string.status_connecting));
            connectButton.setEnabled(false); // Disable button during connection attempt

            // Instantiate and connect
            bleConnectManager = new BleConnectManager(this, device, this);
            bleConnectManager.connect();
        });

        disconnectButton.setOnClickListener(v -> {
            if (bleConnectManager != null) {
                bleConnectManager.disconnect();
            }
        });

        onButton.setOnClickListener(v -> {
            if (bleConnectManager != null) {
                bleConnectManager.sendServoCommand("on");
            }
        });

        offButton.setOnClickListener(v -> {
            if (bleConnectManager != null) {
                bleConnectManager.sendServoCommand("off");
            }
        });
    }

    private void initializeStatus() {
        // Initialize UI elements
        statusTextView = findViewById(R.id.statusTextView);

        scanButton = findViewById(R.id.scanButton);
        connectButton = findViewById(R.id.connectButton);
        disconnectButton = findViewById(R.id.disconnectButton);
        onButton = findViewById(R.id.onButton);
        offButton = findViewById(R.id.offButton);
        resetButton = findViewById(R.id.resetButton);
        helpButton = findViewById(R.id.helpButton);

        IntentFilter filter = new IntentFilter(BluetoothDevice.ACTION_BOND_STATE_CHANGED);
        registerReceiver(bondStateReceiver, filter);
        restoreDevice();

        // Set initial button states
        if (device != null) {
            statusTextView.setText(getString(R.string.status_bonded_disconnected, DEVICE_NAME));
            scanButton.setEnabled(false);
        } else {
            statusTextView.setText(getString(R.string.status_not_bonded));
            scanButton.setEnabled(true);
            connectButton.setEnabled(false);
        }
        disconnectButton.setEnabled(false);
        onButton.setEnabled(false);
        offButton.setEnabled(false);

        bleScanManager = new BleScanManager(this, bluetoothAdapter, this);
        bleConnectManager = new BleConnectManager(this, device,this);
    }

    // Permissions request
    private void requestBluetoothPermissions() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) { // Android 12+
            if (checkSelfPermission(Manifest.permission.BLUETOOTH_SCAN) != PackageManager.PERMISSION_GRANTED ||
                    checkSelfPermission(Manifest.permission.BLUETOOTH_CONNECT) != PackageManager.PERMISSION_GRANTED) {
                requestPermissions(new String[]{
                        Manifest.permission.BLUETOOTH_SCAN,
                        Manifest.permission.BLUETOOTH_CONNECT
                }, BLUETOOTH_PERMISSION_REQUEST_CODE);
            }
        } else { // Android 11 and below
            if (checkSelfPermission(Manifest.permission.ACCESS_FINE_LOCATION) != PackageManager.PERMISSION_GRANTED) {
                requestPermissions(new String[]{
                        Manifest.permission.ACCESS_FINE_LOCATION
                }, BLUETOOTH_PERMISSION_REQUEST_CODE);
            }
        }
    }

    @Override
    public void onRequestPermissionsResult(int requestCode, @NonNull String[] permissions, @NonNull int[] grantResults) {
        super.onRequestPermissionsResult(requestCode, permissions, grantResults);
        if (requestCode == BLUETOOTH_PERMISSION_REQUEST_CODE) {
            boolean allGranted = true;
            for (int grantResult : grantResults) {
                if (grantResult != PackageManager.PERMISSION_GRANTED) {
                    allGranted = false;
                    break;
                }
            }
            if (!allGranted) {
                Toast.makeText(this, getString(R.string.need_permission), Toast.LENGTH_SHORT).show();
                onStop();
            }
        }
    }

    // Implement BleScannerManager.OnDeviceFoundListener methods
    public void onDeviceFound(BluetoothDevice device) {
        runOnUiThread(() -> {
            statusTextView.setText(getString(R.string.status_found_device, DEVICE_NAME));
            this.device = device;
            if (ActivityCompat.checkSelfPermission(this, Manifest.permission.BLUETOOTH_CONNECT) != PackageManager.PERMISSION_GRANTED) {
                requestBluetoothPermissions();
            }
            if (device.getBondState() == BluetoothDevice.BOND_BONDED) {
                statusTextView.setText(getString(R.string.status_bonded_disconnected, DEVICE_NAME));
                connectButton.setEnabled(true);
            } else {
                statusTextView.setText(getString(R.string.status_bonding, DEVICE_NAME));
                device.createBond();
            }
        });
    }
    public void onScanFailed(String errorMessage) {
        runOnUiThread(() -> {
            statusTextView.setText(getString(R.string.status_not_bonded));
            Toast.makeText(MainActivity.this, errorMessage, Toast.LENGTH_SHORT).show();
            scanButton.setEnabled(true); // Allow user to try again
        });
    }

    // Implement BleScannerManager.OnDeviceConnectedListener methods
    @Override
    public void onDeviceConnected(BluetoothDevice device) {
        runOnUiThread(() -> {
            statusTextView.setText(getString(R.string.status_connected));
            connectButton.setEnabled(false);
            disconnectButton.setEnabled(true);
            onButton.setEnabled(true);
            offButton.setEnabled(true);
            bleConnectManager.writeCurrentTime();
        });
    }

    @Override
    public void onDeviceDisconnected() {
        runOnUiThread(() -> {
            statusTextView.setText(getString(R.string.status_bonded_disconnected, DEVICE_NAME));
            connectButton.setEnabled(true);
            disconnectButton.setEnabled(false);
            onButton.setEnabled(false);
            offButton.setEnabled(false);
            this.bleConnectManager = null; // Clear the old manager instance
        });
    }

    @Override
    public void onConnectFailed(String errorMessage) {
        runOnUiThread(() -> {
            statusTextView.setText(getString(R.string.status_bonded_disconnected, DEVICE_NAME));
            Toast.makeText(MainActivity.this, errorMessage, Toast.LENGTH_SHORT).show();
            connectButton.setEnabled(true);
            disconnectButton.setEnabled(false);
            onButton.setEnabled(false);
            offButton.setEnabled(false);
            this.bleConnectManager = null;
        });
    }

    @Override
    public void onTimeSynced() {
        runOnUiThread(() -> {
            Toast.makeText(MainActivity.this, getString(R.string.time_synced), Toast.LENGTH_SHORT).show();
        });
    }

    private final BroadcastReceiver bondStateReceiver = new BroadcastReceiver() {
        @Override
        public void onReceive(Context context, Intent intent) {
            final String action = intent.getAction();

            if (BluetoothDevice.ACTION_BOND_STATE_CHANGED.equals(action)) {
                BluetoothDevice extraDevice = intent.getParcelableExtra(BluetoothDevice.EXTRA_DEVICE);   // Get the device object that changed the bond state
                int state = intent.getIntExtra(BluetoothDevice.EXTRA_BOND_STATE, BluetoothDevice.ERROR);

                // Ensure the broadcast is for our device
                if (extraDevice == null || device == null || !extraDevice.getAddress().equals(device.getAddress())) {
                    return;
                }

                switch (state) {
                    case BluetoothDevice.BOND_BONDING:
                        break;
                    case BluetoothDevice.BOND_BONDED:
                        statusTextView.setText(getString(R.string.status_bonded_disconnected, DEVICE_NAME));
                        saveDevice();
                        connectButton.setEnabled(true); // Enable connect button after bonding
                        scanButton.setEnabled(false);
                        break;
                    case BluetoothDevice.BOND_NONE:
                        statusTextView.setText(getString(R.string.status_not_bonded));
                        scanButton.setEnabled(true);
                        connectButton.setEnabled(false);
                        clearSavedDevice();
                        break;
                }
            }
        }

    };

    // Shared Preferences
    private void saveDevice() {
        if (device != null) {
            SharedPreferences prefs = getSharedPreferences("Bond Information", Context.MODE_PRIVATE);
            SharedPreferences.Editor editor = prefs.edit();
            editor.putString(DEVICE_ADDRESS_KEY_NAME, device.getAddress());
            editor.apply();
        }
    }

    private void restoreDevice() {
        bluetoothAdapter = BluetoothAdapter.getDefaultAdapter();

        SharedPreferences prefs = getSharedPreferences("Bond Information", Context.MODE_PRIVATE);
        String deviceAddress = prefs.getString(DEVICE_ADDRESS_KEY_NAME, null);

        if (deviceAddress != null && bluetoothAdapter != null) {
            // Try to obtain the BluetoothDevice object through the address.
            BluetoothDevice savedDevice = bluetoothAdapter.getRemoteDevice(deviceAddress);
            if (savedDevice != null) {
                if (ActivityCompat.checkSelfPermission(this, Manifest.permission.BLUETOOTH_CONNECT) != PackageManager.PERMISSION_GRANTED) {
                    requestBluetoothPermissions();
                }
                if (savedDevice.getBondState() == BluetoothDevice.BOND_BONDED) {
                    device = savedDevice;
                    Log.d(TAG, "Restored device (" + deviceAddress + ")");
                } else {
                    // The saved device not being bonded indicates a mismatch, so clear the saved device.
                    clearSavedDevice();
                }
            } else {
                // Considering the situation that the device it already bonded we haven't saved it, we need to search it by name for a second check.
                Set<BluetoothDevice> pairedDevices = bluetoothAdapter.getBondedDevices();
                if (pairedDevices != null && !pairedDevices.isEmpty()) {
                    for (BluetoothDevice device : pairedDevices) {
                        if (DEVICE_NAME.equals(device.getName())) {
                            this.device = device;
                            saveDevice();
                        }
                    }
                }
            }
        }
    }
    private void clearSavedDevice() {
        if (ActivityCompat.checkSelfPermission(this, Manifest.permission.BLUETOOTH_CONNECT) != PackageManager.PERMISSION_GRANTED) {
            requestBluetoothPermissions();
        }

        // Disconnect first if connected
        if (bleConnectManager != null) {
            bleConnectManager.disconnect();
        }

        if (device != null && device.getBondState() == BluetoothDevice.BOND_BONDED) {
            try {
                Method removeBondMethod = device.getClass().getMethod("removeBond");
                removeBondMethod.invoke(device);
            } catch (Exception e) {
                new Handler(Looper.getMainLooper()).postDelayed(() -> {
                    initializeStatus();
                    Toast.makeText(MainActivity.this, getString(R.string.unbound_failed), Toast.LENGTH_LONG).show();
                }, 2000);
                onDestroy();
            }
        }
        device = null;
        SharedPreferences prefs = getSharedPreferences("Bond Information", Context.MODE_PRIVATE);
        SharedPreferences.Editor editor = prefs.edit();
        editor.remove(DEVICE_ADDRESS_KEY_NAME);
        editor.apply();
        Toast.makeText(MainActivity.this, getString(R.string.device_cleared), Toast.LENGTH_SHORT).show();
    }

    // onStart and onStop
    @Override
    protected void onStart() {
        super.onStart();
    }

    @Override
    protected void onStop() {
        super.onStop();
    }

    @Override
    protected void onDestroy() {
        super.onDestroy();

        if (bleConnectManager != null) {
            bleConnectManager.disconnect();
        }
        unregisterReceiver(bondStateReceiver);
    }
}