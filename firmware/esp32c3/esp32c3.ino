#include <BLEDevice.h>
#include <BLEServer.h>
#include <ESP32Servo.h>
#include <esp_sleep.h>
#include <driver/rtc_io.h>

/*
In our example, the switch is on when its top is pressed and servos are on the right of the switch.
*/

#define SERVO_A_PIN 4 // lower servo, control off
#define SERVO_B_PIN 2 // upper servo, control on

// BLE UUID
#define DEVICE_NAME       "Remote Switch"
#define SERVICE_UUID_TIMESYNC       (uint16_t)0x1805
#define CHARACTERISTIC_UUID_PHONETIME (uint16_t)0x2A2B
#define SERVICE_UUID_SERVOCONTROL   (uint16_t)0x1815
#define CHARACTERISTIC_UUID_SERVOSIGNAL (uint16_t)0x2A56

Servo servoA, servoB; // Servo object
bool isActivated = false; // A thread lock, avoid the two servos from working simultanously
unsigned long lastOperationTime = 0; // ms
unsigned long connectStartTime = 0; // ms

// Time Manage
RTC_DATA_ATTR bool timeSynced = false;
RTC_DATA_ATTR long localTime = 0; // s, mod 86400
unsigned long localTime_millis = 0; // ms
const long SLEEP_WINDOW_START = 10 * 3600; // 10am
const long SLEEP_WINDOW_END = 22 * 3600; // 10pm
const long SLEEP_WINDOW_START_MIDNIGHT = 2 * 3600; // 2am
const long SLEEP_WINDOW_END_MIDNIGHT = 7 * 3600; // 7am
const long DISCONNECT_TIME = 1 * 60 * 1000; // 1 mins

class BLEController {
public:
  BLEServer *pServer = nullptr;
  BLEService *pService_TimeSync = nullptr;
  BLEService *pService_ServoControl = nullptr;
  BLECharacteristic *pChar_PhoneTime = nullptr;
  BLECharacteristic *pChar_ServoSignal = nullptr;
  uint16_t connId = 0;

  void init() {
    BLEDevice::init(DEVICE_NAME);
    BLEDevice::setPower(ESP_PWR_LVL_N9); // Set Tx power to -9dBm (lower than default)
    pServer = BLEDevice::createServer();
    pServer->setCallbacks(new MyServerCallbacks(this));
    
    // Start Time Sync
    pService_TimeSync = pServer->createService(SERVICE_UUID_TIMESYNC);
    pChar_PhoneTime = pService_TimeSync->createCharacteristic(
      CHARACTERISTIC_UUID_PHONETIME,
      BLECharacteristic::PROPERTY_WRITE
    );
    pChar_PhoneTime->setCallbacks(new PhoneTimeCallbacks(this));
    pService_TimeSync->start();

    // Start Servo Control
    pService_ServoControl = pServer->createService(SERVICE_UUID_SERVOCONTROL);
    pChar_ServoSignal = pService_ServoControl->createCharacteristic(
      CHARACTERISTIC_UUID_SERVOSIGNAL,
      BLECharacteristic::PROPERTY_WRITE
    );
    pChar_ServoSignal->setCallbacks(new ServoSignalCallbacks(this));
    pService_ServoControl->start();

    BLESecurity *pSecurity = new BLESecurity();
    pSecurity->setAuthenticationMode(ESP_LE_AUTH_BOND);     // Allow bonding
    
    BLEAdvertising *pAdvertising = BLEDevice::getAdvertising();
    pAdvertising->setScanResponse(true);  // Allow to be discovered by scanning
    pAdvertising->setMinInterval(0x0600);  // 1536*0.625ms=960ms
    pAdvertising->setMaxInterval(0x0800); // 2048*0.625ms=1280ms
    BLEDevice::startAdvertising();
    /**/
  }
private:
  class PhoneTimeCallbacks : public BLECharacteristicCallbacks {
  public:
    PhoneTimeCallbacks(BLEController* parent) : parent(parent) {}

    void onWrite(BLECharacteristic *pChar) {
      String value = pChar->getValue(); // The phone send an "HH:MM:SS" time 
      localTime_millis = millis();
      localTime = parseTime(value);
      timeSynced = true;
      Serial.println("Time synced: " + value);
    }
  private:
    BLEController* parent;

    long parseTime(String value) {
      long hour = value.substring(0, 2).toInt();
      long minute = value.substring(3, 5).toInt();
      long second = value.substring(6, 8).toInt();

      return hour * 3600 + minute * 60 + second;
    }
  };

  class ServoSignalCallbacks: public BLECharacteristicCallbacks {
  public:
    ServoSignalCallbacks(BLEController* parent) : parent(parent) {}

    void onWrite(BLECharacteristic *pChar) {
      String value = pChar->getValue();
      
      if(value == "1") {
        Serial.println("Received '1'");
        activateServo(SERVO_B_PIN, servoB, 0, 30); // Turn on, the upper servo rotate 30°
      } 
      else if(value == "0") {
        Serial.println("Received '0'");
        activateServo(SERVO_A_PIN, servoA, 30, 0);  // Turn off, the lower servo rotate 30° 
      }
    }

  private:
    BLEController* parent;

    void activateServo(int servoPin, Servo &servo, int angle1, int angle2) {
      if(!isActivated){
        isActivated = true;
        delay(30);  // Waiting for stablity
        
        servo.attach(servoPin);
        servo.write(angle1);
        delay(1000);
        servo.write(angle2);  // restore
        delay(1000);
        servo.detach();
        
        isActivated = false;
      } 
    }
  };

  class MyServerCallbacks: public BLEServerCallbacks {
  public:
    MyServerCallbacks(BLEController* parent) : parent(parent) {}

    void onConnect(BLEServer* pServer) {
      Serial.println("Device connected");
      parent->connId = pServer->getConnId();

      lastOperationTime = millis();
      connectStartTime = millis();
    }

    void onDisconnect(BLEServer* pServer) {
      Serial.println("Device disconnected");
      BLEDevice::startAdvertising();
    }

  private:
    BLEController* parent;
  };
} *MyBLEController;

void setup() {
  Serial.begin(115200);
  lastOperationTime = millis();

  MyBLEController = new BLEController();
  MyBLEController->init();

  Serial.println("BLE Ready. Send '0' or '1' to control.");
}

void loop() {
  delay(5000);
  //Serial.println(String(localTime / 3600) + String((localTime % 3600) / 60) + String((localTime % 60)));
  // Auto dinconnect after about 1 mins of connecting
  if(connectStartTime > 0 && millis() - connectStartTime >= DISCONNECT_TIME && MyBLEController->pServer) {
    if (MyBLEController->pServer->getConnectedCount() > 0) {
      MyBLEController->pServer->disconnect(MyBLEController->connId);
    }
  }

  unsigned long localTime_millis_new = millis();
  localTime += (localTime_millis_new - localTime_millis) / 1000;
  localTime_millis = localTime_millis_new;

  if(timeSynced && 
  localTime % 86400 >= SLEEP_WINDOW_START && localTime % 86400 <= SLEEP_WINDOW_END && 
  millis() - lastOperationTime >= 5 * 60 * 1000) {
    BLEDevice::deinit();
    Serial.println("Ready to enter deep sleep.");

    SLEEP_DURATION = SLEEP_WINDOW_END - localTime % 86400;
    localTime += SLEEP_DURATION;

    esp_sleep_enable_timer_wakeup(SLEEP_DURATION * 1000000ULL); // microsecond
    esp_deep_sleep_start();
  }

  if(timeSynced && 
  localTime % 86400 >= SLEEP_WINDOW_START_MIDNIGHT && localTime % 86400 <= SLEEP_WINDOW_END_MIDNIGHT && 
  millis() - lastOperationTime >= 5 * 60 * 1000) {
    BLEDevice::deinit();
    Serial.println("Ready to enter deep sleep.");

    SLEEP_DURATION = SLEEP_WINDOW_END_MIDNIGHT - localTime % 86400;
    localTime += SLEEP_DURATION;

    esp_sleep_enable_timer_wakeup(SLEEP_DURATION * 1000000ULL); // microsecond
    esp_deep_sleep_start();
  }
}