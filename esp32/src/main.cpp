
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

#define i2cOLED
#ifdef i2cOLED
#include "SSD1306Wire.h"
#endif
#include "OLEDDisplayUi.h"

#include "driver/uart.h"
#ifdef i2cOLED
// Pin definitions for I2C OLED
const int I2C_DISPLAY_ADDRESS = 0x3C;
const int SDA_PIN = 23;
const int SCL_PIN = 22;
#endif

#ifdef i2cOLED
SSD1306Wire display(I2C_DISPLAY_ADDRESS, SDA_PIN, SCL_PIN); // I2C OLED
#endif

OLEDDisplayUi ui(&display);

//declaring prototypes
void drawProfile(OLEDDisplay *display, OLEDDisplayUiState *state, int16_t x, int16_t y);
void drawHardwareInfo(OLEDDisplay *display, OLEDDisplayUiState *state, int16_t x, int16_t y);
// this array keeps function pointers to all frames
// frames are the single views that slide from right to left
FrameCallback frames[] = {drawProfile, drawHardwareInfo};
int numberOfFrames = 2;

char monName[12][4] = {"Jan", "Feb", "Mar", "Apr", "May", "Jun", "Jul", "Aug", "Sep", "Oct", "Nov", "Dec"};
char wdayName[7][4] = {"Sun", "Mon", "Tue", "Wed", "Thu", "Fri", "Sat"};

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

void drawSplash(OLEDDisplay *display, String label);
void fadeLed(int count, int delayMill);
void led_timer_toggle(int count);
void switchRelay();
String getRelayStatus();
void connMQTT(String ssid, String pwd);
int hex_char_value(char c);
int hex_to_decimal(const char *szHex, int len);

String recMsg = "connecting to wifi...";
int ledDelay = 0;

#define ECHO_TXD2 (GPIO_NUM_17)
#define ECHO_RXD2 (GPIO_NUM_16)
#define ECHO_TEST_RTS (UART_PIN_NO_CHANGE)
#define ECHO_TEST_CTS (UART_PIN_NO_CHANGE)

#define BUF_SIZE (1024)

/* Configure parameters of an UART driver,
     * communication pins and install the driver */
uart_config_t uart_config = {
    .baud_rate = 9600,
    .data_bits = UART_DATA_8_BITS,
    .parity = UART_PARITY_DISABLE,
    .stop_bits = UART_STOP_BITS_1,
    .flow_ctrl = UART_HW_FLOWCTRL_DISABLE};

//三极管电流从P到N, 电流大的是E
void setup(void)
{
  Serial.setRxBufferSize(BUF_SIZE);
  Serial.begin(115200);

  uart_param_config(UART_NUM_2, &uart_config);
  uart_set_pin(UART_NUM_2, ECHO_TXD2, ECHO_RXD2, ECHO_TEST_RTS, ECHO_TEST_CTS);
  uart_driver_install(UART_NUM_2, BUF_SIZE * 2, 0, 0, NULL, 0);

  esp_log_level_set("*", ESP_LOG_DEBUG); // set all components to ERROR level
 // initialize dispaly
  display.init();
  display.clear();
  display.display();
  //display.flipScreenVertically();
  display.flipScreenVertically(); // Comment out to flip display 180deg
  display.setFont(ArialMT_Plain_10);
  display.setTextAlignment(TEXT_ALIGN_CENTER);
  display.setContrast(255);
  drawSplash(&display, "long for us");
  delay(1000);
  char chipID[8];
  snprintf(chipID, sizeof chipID, "%lu", (unsigned long)ESP.getEfuseMac());
  Serial.printf("chipId = %s\n", chipID);
  drawSplash(&display, chipID);
  delay(1000);

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
    recMsg = "Waiting for SmartConfig.";
    Serial.println(recMsg);
    drawSplash(&display, recMsg);
    delay(500);
    while (!WiFi.smartConfigDone())
    {
      delay(500);
      Serial.print(".");
    }

    Serial.println("");
    recMsg = "SmartConfig received.";
    Serial.println(recMsg);
    drawSplash(&display, recMsg);
    delay(1000);
    //Wait for WiFi to connect to AP
    recMsg = "Waiting for WiFi";
    Serial.println(recMsg);
    drawSplash(&display, recMsg);
    delay(1000);
    while (WiFi.status() != WL_CONNECTED)
    {
      delay(500);
      Serial.print(".");
    }
    recMsg = "WiFi Connected.";
    Serial.println(recMsg);
    drawSplash(&display, recMsg);
    delay(1000);
    Serial.println();
    Serial.print("IP Address: ");
    Serial.println(WiFi.localIP());
    ssid = WiFi.SSID();
    recMsg = "SmartConfig OK : " + ssid;
    drawSplash(&display, recMsg);
    delay(1500);
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
    recMsg = "Connecting WiFi to " + ssid + " ...";
    drawSplash(&display, recMsg);
    delay(1500);
    connMQTT(ssid, wifipwd);
  }

  //led
  pinMode(BLUE_LED_PIN, OUTPUT);
  pinMode(RELAY_PIN, OUTPUT);
  digitalWrite(RELAY_PIN, LOW);
  ledTiker.attach_ms(200, led_timer_toggle, -1);

  ui.setTargetFPS(30);
  ui.setTimePerFrame(5000);
  // You can change the transition that is used
  // SLIDE_LEFT, SLIDE_RIGHT, SLIDE_TOP, SLIDE_DOWN
  ui.setFrameAnimation(SLIDE_UP);
  // Add frames
  ui.setFrames(frames, numberOfFrames);
  ui.disableAllIndicators();
  // Inital UI takes care of initalising the display too.
  //ui.init();
  ui.enableAutoTransition();
}

void drawSplash(OLEDDisplay *display, String label)
{
  display->clear();
  display->setTextAlignment(TEXT_ALIGN_CENTER_BOTH);
  display->setFont(ArialMT_Plain_10);
  display->drawString(64, 32, label);
  display->display();
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
  int remainingTimeBudget = ui.update();
  if (remainingTimeBudget > 0)
  {
    // You can do some work here
    // Don't do stuff if you are below your
    // time budget.
    delay(remainingTimeBudget);
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

  client->subscribe("/ext/rrpc/#/iot/serial", [](const String &topicStr, const String &message)
                    {
                      Serial.println(topicStr + "  " + message.c_str());
                      recMsg = "iot/serial -> " + message;
                      int len = message.length();
                      char arr[len / 2];
                      char str[2];
                      for (size_t i = 0; i < len; i = i + 2)
                      {
                        str[0] = message.charAt(i);
                        str[1] = message.charAt(i + 1);
                        int dec = hex_to_decimal(str, 2);
                        Serial.printf("str = %s ,dec = %d \n", str, dec);
                        arr[i / 2] = (char)dec;
                      }
                      len /= 2;
                      Serial.printf("arr len = %d \n", len);
                      uart_write_bytes(UART_NUM_2, arr, len);
                      client->publish(topicStr, message.c_str());
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

void drawProfile(OLEDDisplay *display, OLEDDisplayUiState *state, int16_t x, int16_t y)
{
  //now = DateTime(millis());

  char date_str[40];

  display->setTextAlignment(TEXT_ALIGN_CENTER);
  display->setFont(ArialMT_Plain_10);

  display->drawString(64 + x, 0 + y, "long for us");
  display->drawHorizontalLine(0 + x, 13 + y, 128);

  display->setFont(ArialMT_Plain_24);
  display->setTextAlignment(TEXT_ALIGN_LEFT);

  sprintf(date_str, "%02d:%02d:%02d", now.hour(), now.minute(), now.second());
  display->drawString(x + 16, 16 + y, date_str);

  snprintf_P(date_str,
             sizeof(date_str),
             PSTR("%04u-%02u-%02u (%03s)"),
             now.year(), now.month(), now.day(), wdayName[now.dayOfTheWeek()]);

  display->setTextAlignment(TEXT_ALIGN_CENTER);
  display->setFont(ArialMT_Plain_10);
  display->drawString(64 + x, 41 + y, date_str);

  String uptime = "Uptime ";

  int days = 0;
  long up_time = (millis() - millisTimeUpdated) / 1000;
  if (up_time > 3600 * 24)
  {
    days = up_time / (3600 * 24);
    up_time = up_time % (3600 * 24);
    uptime = uptime + (String)(days) + "days ";
  }
  int hours = 0;
  int mins = 0;
  hours = up_time / 3600;
  up_time = up_time % 3600;
  uptime = uptime + ((hours < 10) ? "0" : "") + (String)(hours) + ":";
  mins = up_time / 60;
  uptime = uptime + ((mins < 10) ? "0" : "") + (String)(mins);

  display->setTextAlignment(TEXT_ALIGN_CENTER);
  display->drawString(64 + x, 52 + y, uptime);
}

void drawHardwareInfo(OLEDDisplay *display, OLEDDisplayUiState *state, int16_t x, int16_t y)
{

  display->setTextAlignment(TEXT_ALIGN_LEFT);
  display->setFont(ArialMT_Plain_10);

  String title = recMsg;
  display->drawString(0 + x, 0 + y, title);

  if (WiFi.status() == WL_CONNECTED)
  {
    String ipstr = "IP : ";
    ipstr += WiFi.localIP().toString();
    display->drawString(0 + x, 13 + y, ipstr);
  }
}

int hex_char_value(char c)
{
  if (c >= '0' && c <= '9')
    return c - '0';
  else if (c >= 'a' && c <= 'f')
    return (c - 'a' + 10);
  else if (c >= 'A' && c <= 'F')
    return (c - 'A' + 10);
  return 0;
}
int hex_to_decimal(const char *szHex, int len)
{
  int result = 0;
  for (int i = 0; i < len; i++)
  {
    result += (int)pow((float)16, (int)len - i - 1) * hex_char_value(szHex[i]);
  }
  return result;
}
