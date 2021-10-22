
#include <Arduino.h>
#include <Wire.h>
#include <Ticker.h>
#include <time.h>
#include <WiFiUdp.h>
#include "WiFi.h"
#include "EspMQTTclient.h"
#include "Account.h"
#include <Preferences.h>
#include <NTPClient.h>
#include <RTClib.h>

#define LOG_LOCAL_LEVEL ESP_LOG_DEBUG
#include "esp_log.h"
Preferences prefs;
Ticker ledTiker;
Ticker btnTiker;

WiFiUDP ntpUDP;
NTPClient timeClient(ntpUDP, "ntp1.aliyun.com", 60 * 60 * 8, 30 * 60 * 1000);
DateTime now;

EspMQTTClient *client;

unsigned long millisTimeUpdated = millis();

static const uint8_t BLUE_LED_PIN = 15;
static const uint8_t RELAY_PIN = 14;

void fadeLed(int count, int delayMill);
void led_timer_toggle(int count);
void switchRelay();
String getRelayStatus();
void connMQTT(String ssid, String pwd);

String recMsg = "connecting to wifi...";
int ledDelay = 0;

//三极管电流从P到N, 电流大的是E
void setup(void)
{
  Serial.setRxBufferSize(1024);
  Serial.begin(115200);
  char chipID[8];
  snprintf(chipID, sizeof chipID, "%lu", (unsigned long)ESP.getEfuseMac());
  Serial.printf("chipId = %s\n", chipID);

  esp_log_level_set("*", ESP_LOG_DEBUG); // set all components to ERROR level

  prefs.begin("settings");
  String ssid = prefs.getString("ssid", "");
  String wifipwd = prefs.getString("wifipwd", "");
  Serial.printf("read ssid = %s pwd = %s\n", ssid, wifipwd.c_str());
  if (ssid.isEmpty() || wifipwd.isEmpty()) //未组网
  {

    //Init WiFi as Station, start SmartConfig
    WiFi.mode(WIFI_AP_STA);
    WiFi.beginSmartConfig();
    //Wait for SmartConfig packet from mobile
    Serial.println("Waiting for SmartConfig.");
    while (!WiFi.smartConfigDone())
    {
      delay(500);
      Serial.print(".");
    }

    Serial.println("");
    Serial.println("SmartConfig received.");

    //Wait for WiFi to connect to AP
    Serial.println("Waiting for WiFi");
    while (WiFi.status() != WL_CONNECTED)
    {
      delay(500);
      Serial.print(".");
    }
    Serial.println("WiFi Connected.");
    Serial.print("IP Address: ");
    Serial.println(WiFi.localIP());
    ssid = WiFi.SSID();
    wifipwd = WiFi.psk();
    Serial.print("ssid: ");
    Serial.println(ssid);
    Serial.print("pwd: ");
    Serial.println(wifipwd);
    prefs.putString("ssid", ssid);
    prefs.putString("wifipwd", wifipwd);
    prefs.end();
    connMQTT(ssid, wifipwd);
  }
  else
  {
    connMQTT(ssid, wifipwd);
  }

  //led
  pinMode(BLUE_LED_PIN, OUTPUT);
  pinMode(RELAY_PIN, OUTPUT);
  digitalWrite(RELAY_PIN, LOW);

  ledTiker.attach_ms(200, led_timer_toggle, -1);
}

void connMQTT(String ssid, String pwd)
{
  //不这么转化一下的话是乱码
  char *charSSID = new char[strlen(ssid.c_str()) + 1];
  strcpy(charSSID, ssid.c_str());
  char *charPWD = new char[strlen(pwd.c_str()) + 1];
  strcpy(charPWD, pwd.c_str());
  //注意这里的static关键字,用static声明的局部变量在这个方法运行完后还能保存不被释放,不加的话这里运行完就释放掉了,后面调用client就会出错
  static EspMQTTClient cc(
      charSSID,
      charPWD,
      MQTT_SERVER_ADDRESS, // MQTT Broker server ip
      MQTT_USER_NAME,      // Can be omitted if not needed
      MQTT_USER_PWD,       // Can be omitted if not needed
      MQTT_CLIENT_NAME,    // Client name that uniquely identify your device
      1883                 // The MQTT port, default to 1883. this line can be omitted
  );

  client = &cc;
  // Optionnal functionnalities of EspMQTTClient :
  client->enableDebuggingMessages(); // Enable debugging messages sent to serial output
  client->setMaxPacketSize(1024);
  client->setKeepAlive(60);
  //client ->enableHTTPWebUpdater();                                             // Enable the web updater. User and password default to values of MQTTUsername and MQTTPassword. These can be overrited with enableHTTPWebUpdater("user", "password").
  client->enableLastWillMessage("TestClient/lastwill", "I am going offline"); // You can activate the retain flag by setting the third parameter to true
  // client->loop();
  Serial.printf("client inited %p\n", client);
}

int initFlag = 0;

void loop(void)
{
  client->loop();
  //now = baseTime.operator + (TimeSpan((millis() - millisTimeUpdated) / 1000));
  if (WiFi.status() == WL_CONNECTED)
  {
    if (!initFlag)
    {
      timeClient.begin();
      ledTiker.detach();
      initFlag = 1;
      Serial.println("conncet success");
    }
    timeClient.update();
    now = DateTime(timeClient.getEpochTime());
    char date_str[40];
    sprintf(date_str, "%02d:%02d:%02d", now.hour(), now.minute(), now.second());
    // Serial.println(date_str);
    if (now.second() % 2 == 0)
    {
      digitalWrite(BLUE_LED_PIN, HIGH);
    }
    else
    {
      digitalWrite(BLUE_LED_PIN, LOW);
    }
  }
}

void switchRelay()
{
  if (digitalRead(RELAY_PIN) == LOW)
  {
    digitalWrite(RELAY_PIN, HIGH);
  }
  else
  {
    digitalWrite(RELAY_PIN, LOW);
  }
}

String getRelayStatus()
{
  String result;
  if (digitalRead(RELAY_PIN) == LOW)
  {
    result = "off";
  }
  else
  {
    result = "on";
  }
  Serial.printf("load relay status %s as pin%d\n", result, RELAY_PIN);
  return result;
}

void fadeLed(int count, int delayMill)
{
  ledDelay = delayMill;
  led_timer_toggle(count);
}

void led_timer_toggle(int count)
{
  int status = GPIO_INPUT_GET(BLUE_LED_PIN); //获取蓝灯管脚状态
  if (status == LOW)                         //取反实现蓝灯管脚电平反转，从而实现亮灭操作
  {
    digitalWrite(BLUE_LED_PIN, HIGH);
  }
  else
  {
    digitalWrite(BLUE_LED_PIN, LOW);
  }
  if (count > 0)
  {
    ledTiker.once_ms(ledDelay, led_timer_toggle, count - 1);
  }
  else if (count != -1)
  {
    digitalWrite(BLUE_LED_PIN, HIGH);
  }
}

// This function is called once everything is connected (Wifi and MQTT)
// WARNING : YOU MUST IMPLEMENT IT IF YOU USE EspMQTTClient
void onConnectionEstablished()
{

  client->subscribe("/ext/rrpc/#/iot/relay", [](const String &topicStr, const String &message)
                    {
                      Serial.println(topicStr + "  " + message);
                      recMsg = "iot/relay -> " + message;
                      fadeLed(3, 88);
                      if (message.equals("on"))
                      {
                        digitalWrite(RELAY_PIN, HIGH);
                        Serial.printf("set pin %d to %s\n", RELAY_PIN, "high");
                      }
                      else if (message.equals("off"))
                      {
                        digitalWrite(RELAY_PIN, LOW);
                        Serial.printf("set pin %d to %s\n", RELAY_PIN, "low");
                      }
                      else
                      {
                        switchRelay();
                      }
                      client->publish(
                          topicStr, getRelayStatus());
                    });

  client->subscribe("/ext/rrpc/#/iot/power", [](const String &topicStr, const String &message)
                    {
                      Serial.println(topicStr + "  " + message);
                      recMsg = "iot/power -> " + message;
                      client->publish(topicStr, message);
                    });
  client->subscribe("/ext/rrpc/#/settings", [](const String &topicStr, const String &message)
                    {
                      Serial.println(topicStr + "  " + message);
                      recMsg = "settings -> " + message;

                      if (message.equals("clearPrefs"))
                      {
                        prefs.begin("settings");
                        if (prefs.clear())
                        {
                          Serial.printf("prefs cleared\n");
                          client->publish(topicStr, message);
                          delay(200);
                          ESP.restart();
                        }
                        else
                        {
                          Serial.printf("prefs clear fail\n");
                          client->publish(topicStr, "clearPrefsFail");
                        }
                        prefs.end();
                      }
                    });

  // client ->subscribe("volumio", [](const String &topicStr, const String &message)
  //                  {
  //                    Serial.println(payload);
  //                    recMsg = payload;
  //                  });

  // Subscribe to "mytopic/wildcardtest/#" and display received message to Serial
  // client ->subscribe("mytopic/wildcardtest/#", [](const String &topic, const String &topicStr, const String &message)
  //                  { Serial.println("(From wildcard) topic: " + topic + ", payload: " + payload); });

  // Publish a message to "mytopic/test"
  // client ->publish("/gbhp57tmeNa/officePC/user/mytopic/test", "This is a message"); // You can activate the retain flag by setting the third parameter to true

  // Execute delayed instructions
  // client ->executeDelayed(5 * 1000, []()
  //                       { client ->publish("mytopic/wildcardtest/test123", "This is a message sent 5 seconds later"); });
}
