# Android App Documentation - Remote Switch

This document provides a technical overview of the Android application for the Remote Switch project.

## Technical Specifications

| Item              | Specification                        |
| ----------------- | ------------------------------------ |
| **Language**      | Java                                 |
| **Minimum SDK**   | API 29 (Android 10.0 Q)     |
| **Build Tool**    | Gradle                               |
| **Permissions**   | `BLUETOOTH_SCAN`, `BLUETOOTH_CONNECT`, `ACCESS_FINE_LOCATION`  |

## Code Architecture

The app is designed with a modular approach, separating responsibilities into different manager classes that are orchestrated by the main activity.

### Core Components

- **`MainActivity.java`**
  The central hub of the application. Its primary responsibilities are:
  - Managing the UI and handling user input from buttons.
  - Requesting necessary Bluetooth permissions at runtime.
  - Orchestrating the `BleScanManager` and `BleConnectManager`.
  - Implementing listener interfaces to receive callbacks from the managers and update the UI state.
  - Managing the overall application state (e.g., scanning, connected).
  - Managing the bonding status.
  - Dealing with reset logic.

- **`BleScanManager.java`**
  A dedicated class for handling BLE scanning.
  - **Responsibility:** To scan for a BLE device with the specific name "Remote Switch".
  - **Mechanism:** It uses the `BluetoothLeScanner` and is configured with a `ScanFilter` to find the target device efficiently. It notifies `MainActivity` of results via the `OnDeviceFoundListener` interface.

- **`BleConnectManager.java`**
  Handles all aspects of the GATT connection and data transfer.
  - **Responsibility:** Connect to a given `BluetoothDevice`, discover its services and characteristics, enable notifications, and provide methods for writing data.
  - **Mechanism:** It implements the `BluetoothGattCallback` to handle all asynchronous BLE events like connection state changes, service discovery, and characteristic writes.

- **`AndroidManifest.xml`**
  Declares the fundamental properties of the app.
  - **Permissions:** Crucially, it requests `BLUETOOTH_SCAN` and `BLUETOOTH_CONNECT` for Android 12+, and legacy `BLUETOOTH`, `BLUETOOTH_ADMIN`, and `ACCESS_FINE_LOCATION` for older versions.
  - **Features:** Declares that the app requires `android.hardware.bluetooth_le`, preventing it from being installed on devices without BLE support.

### Key Functional Flows

#### 1. Scanning and Bonding

1. User taps the **Scan** button.
1. `MainActivity` calls `bleScanManager.startScan()` which:
   - Checks for `BLUETOOTH_SCAN` permission
   - Sets up a scan filter for the device name "Remote Switch"
   - Starts BLE scanning with low latency mode
   - Sets a 15-second timeout handler
1. When the device is found, `bleScanManager`'s `leScanCallback` triggers:
   - Logs device details (MAC, RSSI)
   - Stops scanning immediately
   - Calls the `onDeviceFound()` callback in `MainActivity`
1. In `MainActivity.onDeviceFound()`:
   - Updates UI to show found status
   - Checks bond state via `device.getBondState()`
   - If not bonded (`BOND_NONE`):
     - Calls `device.createBond()` to initiate pairing
     - Shows "Bonding..." status
   - If already bonded (`BOND_BONDED`):
     - Enables Connect button
     - Shows "Bonded" status
1. Bonding process:
   - System handles the actual pairing dialog
   - `MainActivity`'s `BroadcastReceiver` listens for `ACTION_BOND_STATE_CHANGED`
   - On successful bonding (`BOND_BONDED`):
     - Calls `saveDevice()` to persist MAC address
     - Updates UI to show bonded status
     - Enables Connect button
     - Disables Scan button
   - On bond removal (`BOND_NONE`):
     - Calls `clearSavedDevice()`
     - Resets UI to initial state
1. Device persistence:
   - `saveDevice()` stores MAC address in `SharedPreferences` under "Bond Information"
   - `restoreDevice()` (called at startup):
     - Retrieves MAC from `SharedPreferences`
     - Gets device reference via `bluetoothAdapter.getRemoteDevice()`
     - Verifies bond state matches
     - Falls back to searching bonded devices by name
   - `clearSavedDevice()`:
     - Removes bond via reflection (`removeBond()`)
     - Clears `SharedPreferences` entry
     - Resets device reference
     - Updates UI
1. Reset process:
   - User taps **Reset** button
   - Calls `clearSavedDevice()`
   - After 500ms delay, calls `initializeStatus()` to:
     - Reset all UI elements
     - Re-enable scanning
     - Prepare for new device connection

#### 2. Connection and Time Sync

1. User taps the **Connect** button.
1. `MainActivity` instantiates `BleConnectManager` and calls `bleConnectManager.connect()` which:
   - Checks for `BLUETOOTH_CONNECT` permission
   - Initiates GATT connection with `autoConnect=false`
1. The `onConnectionStateChange` callback in the manager is triggered:
   - On `STATE_CONNECTED`: Calls `gatt.discoverServices()`
   - On `STATE_DISCONNECTED`: Cleans up resources and notifies UI
1. `onServicesDiscovered` is called:
   - Locates both required services (`SERVICE_UUID_TIMESYNC` and `SERVICE_UUID_SERVOCONTROL`)
   - Retrieves characteristics (`phoneTimeCharacteristic` and `servoSignalCharacteristic`)
   - Validates both characteristics exist before proceeding
1. Upon successful discovery:
   - Calls `listener.onDeviceConnected()`
   - `MainActivity` enables control buttons and calls `writeCurrentTime()`
1. Time sync process:
   - Formats current time as "HH:mm:ss"
   - Writes to `phoneTimeCharacteristic` with `WRITE_TYPE_DEFAULT`
1. When write completes:
   - `onCharacteristicWrite` logs success
   - Calls `listener.onTimeSynced()`
   - `MainActivity` shows sync confirmation toast

#### 3. On/Off Control

1. User taps **ON** or **OFF** button
1. `MainActivity` calls `bleConnectManager.sendServoCommand()` with:
   - "on" → sends "1" to characteristic
   - "off" → sends "0" to characteristic
1. In `BleConnectManager`:
   - Validates GATT connection and characteristic availability
   - Sets characteristic value based on command
   - Uses `WRITE_TYPE_DEFAULT` for reliable delivery
1. On write completion:
   - `onCharacteristicWrite` logs the sent command
   - ESP32 receives value and activates corresponding servo

#### 5. Help Page

1. User taps **Help** button in `MainActivity`
1. `MainActivity` launches `HelpActivity` via explicit Intent
1. `HelpActivity.onCreate()`:
   - Sets up back button in action bar
   - Loads help text from `assets/help_text.txt`
   - Sets click listener for settings button
1. Help text loading:
   - Reads file line-by-line via `BufferedReader`
   - Falls back to error message if file missing
1. Settings button:
   - Opens app-specific system settings
   - Uses `Settings.ACTION_APPLICATION_DETAILS_SETTINGS` Intent
1. Back navigation:
   - Handled automatically via action bar back button
   - Returns to `MainActivity` preserving state

#### 6. Permission Request

1. Initial permission check in `MainActivity.onCreate()`:
   - Calls `requestBluetoothPermissions()`
1. Permission handling:
   - Android 12+ (`S`+): Requests `BLUETOOTH_SCAN` and `BLUETOOTH_CONNECT`
   - Android 11-: Requests `ACCESS_FINE_LOCATION` (legacy requirement)
1. Permission results:
   - Handled in `onRequestPermissionsResult()`
   - Verifies all requested permissions granted
   - Shows toast and stops app if denied
1. Runtime checks:
   - Before scanning: Verifies `BLUETOOTH_SCAN`
   - Before connecting: Verifies `BLUETOOTH_CONNECT`
   - Gracefully handles denial with error messages
1. Fallback behavior:
   - Critical operations disabled without permissions
   - User prompted to grant via system dialog
   - Settings button in HelpActivity provides alternative access