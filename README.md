# Remote Switch

![Platform](https://img.shields.io/badge/Platform-Android-brightgreen)
![Hardware](https://img.shields.io/badge/Hardware-ESP32--C3-orange)
[![License: MIT](https://img.shields.io/badge/License-MIT-blue.svg)](https://opensource.org/licenses/MIT)
[![GitHub release (latest by date)](https://img.shields.io/github/v/release/PHTPSN/RemoteSwitch)](https://github.com/PHTPSN/RemoteSwitch/releases)

[中文版使用说明](中文版使用说明.md)

**Tired of getting off and on the ladder when you finally put down your phone and ready to fall into a sweet dream?** This is a project to remotely control a physical switch using an Android application via Bluetooth Low Energy (BLE). The system uses an ESP32-C3 microcontroller to operate two servo motors and features power-saving deep sleep functionality.

## Features

- **BLE Remote Control:** Securely turn a switch ON or OFF in one tap from the Android app using standard BLE services.
- **Automatic Time Sync:** The ESP32's internal clock is synchronized with the phone's time upon connection to manage sleep schedules.
- **Power Saving:** Under a 3000mA lithium battery, the device can operate for 1 to 2 months.
  - The device enters a deep sleep mode during inactive hours (e.g., 10am - 10pm and 2am - 7am) to conserve battery. You will not able to control the device by the phone during this period. But don't worry, you can press the **RST** button **on ESP32** to erase time memory. The device won't enter deep sleep before fetching the time from a phone, nor in the first several minutes of the last operation.
  - The device will automatically disconnect from the phone after a fixed time of connection.

- **Persistent Bonding:** The Android app remembers the bonded device, allowing for quick reconnections without needing to scan every time, even when the **ESP32** is reset. But you can unbond from the device by **reset** on the android app or do it manually in phone settings.

### Repository Structure

This project is a monorepo containing all necessary code and firmware:

```tree
/
├── android/          # Contains the Android Studio project
│   ├── app/
│   ├── gradle/
│   ├── ANDROID_APP_DOC.md  # Detailed documentation for the app
│   └── ...
└── firmware/     # Contains the Arduino sketch for the ESP32
|   └── esp32c3
│       └── esp32c3.ino
└── README.md             # You are here
```

## Hardware Setup

### Components

- ESP32-C3 Development Board (e.g., AirM2M_CORE_ESP32C3)
- 2x Micro Servos (e.g., SG90 180&deg;)
- Power source (e.g., LiPo battery with Type-C port)
- Dupont wires, nano paste, etc.

### Connections

- The **lower servo** (controls OFF) connects to **GPIO 4**.
- The **upper servo** (controls ON) connects to **GPIO 2**.

Remark: It's just an example of the case that the switch is on when its top is pressed and servos are on the right of the switch. You may adjust some details in `firmware/Remote_Switch_ESP32.ino` according to your own demand referring to [Adjustments](#adjustments). You can also flash directly by
`
esptool.py --port <serial_port> write_flash 0x1000 esp32c3.ino.bin
`.

## Firmware Setup (ESP32)

### Standard Steps

1. **Prerequisites:**
    - Install the [Arduino IDE](https://www.arduino.cc/en/software).
    - Add ESP32 board support to the Arduino IDE. Follow this [guide](https://docs.espressif.com/projects/arduino-esp32/en/latest/installing.html). Version: esp32 **3.2.0**. (Don't install newer version because BLESecurity is totally different then!)
    - Select the proper board.
2. **Install Libraries:**
    - Open the Library Manager (`Sketch > Include Library > Manage Libraries...`).
    - Install `ESP32Servo`.
3. **Adjust the Code**
4. **Flash the Firmware:**
    - Open `firmware/esp32ce.ino` in the Arduino IDE.
    - Select the correct COM port.
    - Click **Upload**.

### Adjustments

Here's the position of some common setup `firmware/Remote_Switch_ESP32.ino` you may use:

1. **Servo Pins:**

    ```cpp
    #define SERVO_A_PIN 4 // lower servo, control off
    #define SERVO_B_PIN 2 // upper servo, control on
    ```

1. **Sleep Period:**

    ```cpp
    const long SLEEP_WINDOW_START = 10 * 3600; // 10am
    const long SLEEP_WINDOW_END = 22 * 3600; // 10pm
    const long SLEEP_WINDOW_START_MIDNIGHT = 2 * 3600; // 2am
    const long SLEEP_WINDOW_END_MIDNIGHT = 7 * 3600; // 7am
    ```

1. **Auto-disconnect Time:**

    ```cpp
    const long DISCONNECT_TIME = 1 * 60 * 1000; // 1 mins
    ```

1. **Rotation Angle:** The first angle is the rotation angle, and the second one is the restoration position angle.

    ```cpp
    if(value == "1") {
        Serial.println("Received '1'");
        activateServo(SERVO_B_PIN, servoB, 0, 30); // Turn on, the upper servo rotate 30°
    } 
    else if(value == "0") {
        Serial.println("Received '0'");
        activateServo(SERVO_A_PIN, servoA, 30, 0);  // Turn off, the lower servo rotate 30°
    }
    ```

## How to Use the App

1. **Scan**: The first time you use the app, allow the asked permissions. Tap **Scan**. The app will look for a BLE device named "Remote Switch". If you can't find your device, please try enabling location permissions manually in your system settings.
1. **Bond:** The app will automatically initiate a bonding request. Accept the request on your phone. Once bonded, the app will remember the device. We set a big advertising interval to conserve battery. If you can find the device but fail to bond with it, you can try to again by tapping **scan**.
1. **Connect:** Tap **Connect**. The app will establish a connection and sync the time with the device automatically.
1. **Control:** Once the status is "Connected", use the **ON** and **OFF** buttons to control the switch. Tap **Disconnect** after using to save battery as long as you remember, althought the device will automatically do this a few minutes later.
1. **Reset:** Tap **Reset** to un-bond the device. You will need to scan again after a reset. But it will not delete the permissions.
1. **Other Instructions:** If the app is stopped because of "Need Permission", please allow all bluetooth permissions manually. You are not able to find or connect to the device when another phone is connected to it.

Remark: Android 10+ should not need location permission according to official instruction, but my Android 11 and 13 does, which took me the whole 2 days to figure out the scanning problem! (AZhe)w

## Android App Setup (For Developers)

1. **Prerequisites:**
    - Install [Android Studio](https://developer.android.com/studio).

1. **Get the Code (Sparse Checkout):**
    To avoid downloading the firmware files, clone the repository using Git Sparse Checkout. This is the recommended method.

    ```bash
    # Clone the repository without checking out any files
    git clone --filter=blob:none --no-checkout https://github.com/PHTPSN/RemoteSwitch.git
    cd RemoteSwitch

    # Configure sparse checkout to only pull the android-app directory
    git sparse-checkout init --cone
    git sparse-checkout set android
    ```

1. **Build and Run:**
    - Open Android Studio.
    - Select **File > Open** and navigate to the `android` folder inside your cloned repository.
    - Let Gradle sync the project.
    - Connect your Android device and click the **Run** button.

## Development & Contribution

We welcome contributions! Please follow this workflow:

1. **Branching:** Create a new branch from `main` for any new feature or fix (e.g., `feature/new-button`, `fix/connection-bug`).
1. **Code:** Make your changes to the app and/or firmware. A single commit should represent a consistent state of the entire project.
1. **Pull Request:** Push your branch to GitHub and open a Pull Request against the `main` branch.

For more detailed information on the Android app's architecture, see [**`android/ANDROID_APP_DOC.md`**](./android/ANDROID_APP_DOC.md).

## License

This project is licensed under the MIT License. See the [LICENSE](LICENSE) file for details.
