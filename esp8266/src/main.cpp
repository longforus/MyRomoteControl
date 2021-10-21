
#include <Arduino.h>
#include <Wire.h>
#include <ESP8266WiFi.h>
#include <Ticker.h>
#include <time.h>
// Setup
const int UPDATE_INTERVAL_SECS = 5 * 60;
const int UPDATE_INTERVAL_SECS_DHT = 10;
#include <WiFiUdp.h>

unsigned long millisTimeUpdated = millis();
String lastUpdate = "--";

Ticker ledTiker;
Ticker dacPowerTiker;
Ticker printTiker;

/*
  SimpleMQTTClient.ino
  The purpose of this exemple is to illustrate a simple handling of MQTT and Wifi connection.
  Once it connects successfully to a Wifi network and a MQTT broker, it subscribe to a topic and send a message to it.
  It will also send a message delayed 5 seconds later.
*/

#include "EspMQTTClient.h"
#include "Account.h"

EspMQTTClient client(
    "XUNMIAO",
    WIFI_PWD,
    MQTT_SERVER_ADDRESS, // MQTT Broker server ip
    MQTT_USER_NAME,      // Can be omitted if not needed
    MQTT_USER_PWD,       // Can be omitted if not needed
    MQTT_CLIENT_NAME,    // Client name that uniquely identify your device
    1883                 // The MQTT port, default to 1883. this line can be omitted
);

static const uint8_t BLUE_LED_PIN = D3;

#include <IRremoteESP8266.h>
#include <IRsend.h>
#include <IRrecv.h>
#include <IRutils.h>
#include <ir_Gree.h>

const uint16_t kIrLed = D2; // ESP8266 GPIO pin to use. Recommended: 4 (D2).
IRsend irsend(kIrLed);      // Set the GPIO to be used to sending the message.
IRGreeAC ac(kIrLed);  // Set the GPIO to be used for sending messages.

// An IR detector/demodulator is connected to GPIO pin 14(D5 on a NodeMCU
// board).
// Note: GPIO 16 won't work on the ESP8266 as it does not have interrupts.
const uint16_t kRecvPin = D7;
IRrecv irrecv(kRecvPin);
decode_results results;

void fadeLed(int count, int delayMill);
void led_timer_toggle(int count);
void dac_power_timer_toggle();
void ac_remote_split_line_toggle();
void printACState();

constexpr std::uint32_t hash_str_to_uint32(const char *data)
{
  std::uint32_t h(0);
  for (int i = 0; data && ('\0' != data[i]); i++)
    h = (h << 6) ^ (h >> 26) ^ data[i];
  return h;
}

void setup(void)
{
  Serial.setRxBufferSize(1024);
  Serial.begin(115200);
  //led
  pinMode(BLUE_LED_PIN, OUTPUT);
  ledTiker.attach_ms(200, led_timer_toggle, -1);
  irsend.begin();
  irrecv.enableIRIn(); // Start the receiver
  while (!Serial)      // Wait for the serial connection to be establised.
    delay(50);
  Serial.println();
  Serial.print("IRrecv is now running and waiting for IR message on Pin ");
  Serial.println(kRecvPin);

  char chipID[8];
  snprintf(chipID, sizeof chipID, "%lu", (unsigned long)ESP.getChipId());
  Serial.printf("chipId = %s", chipID);
  // Optionnal functionnalities of EspMQTTClient :
  client.enableDebuggingMessages(); // Enable debugging messages sent to serial output
  client.setMaxPacketSize(1024);
  client.setKeepAlive(60);
  //client.enableHTTPWebUpdater();                                             // Enable the web updater. User and password default to values of MQTTUsername and MQTTPassword. These can be overrited with enableHTTPWebUpdater("user", "password").
  client.enableLastWillMessage("TestClient/lastwill", "I am going offline"); // You can activate the retain flag by setting the third parameter to true
  printACState();
}

int btnClickCount = 0;

int timeBegin = 0;
int ledDelay = 0;
int firstBoot = 1;
uint32_t irRecCount = 0;
String recMsg = "connecting to wifi...";

void loop(void)
{
  client.loop();
  //now = baseTime.operator + (TimeSpan((millis() - millisTimeUpdated) / 1000));
  if (WiFi.status() == WL_CONNECTED)
  {
    if (!timeBegin)
    {
      ledTiker.detach();
      timeBegin = 1;
      Serial.println("conncet success");
    }
  }

  if (irrecv.decode(&results))
  {
    // print() & println() can't handle printing long longs. (uint64_t)
    Serial.printf("irrecv %d -> value = %s addr = %d command = %d decode_type = ", irRecCount++, uint64ToString(results.value, HEX), results.address, results.command);
    Serial.println(results.decode_type);
    irrecv.resume(); // Receive the next value
    printTiker.detach();
    printTiker.once_ms(200,ac_remote_split_line_toggle);
  }
}

void fadeLed(int count, int delayMill)
{
  ledDelay = delayMill;
  led_timer_toggle(count);
}

void led_timer_toggle(int count)
{
  uint32 status = GPIO_INPUT_GET(BLUE_LED_PIN); //获取蓝灯管脚状态
  if (status == LOW)                            //取反实现蓝灯管脚电平反转，从而实现亮灭操作
  {
    analogWrite(BLUE_LED_PIN, 666);
  }
  else
  {
    analogWrite(BLUE_LED_PIN, LOW);
  }
  if (count > 0)
  {
    ledTiker.once_ms(ledDelay, led_timer_toggle, count - 1);
  }
  else if (count != -1)
  {
    analogWrite(BLUE_LED_PIN, 666);
  }
}

void dac_power_timer_toggle()
{
  irsend.sendNEC(0x486C807FUL);
}
void ac_remote_split_line_toggle()
{
  Serial.println();
}

void printACState() {
  // Display the settings.
  Serial.println("GREE A/C remote is in the following state:");
  Serial.printf("  %s\n", ac.toString().c_str());
  // Display the encoded IR sequence.
  unsigned char* ir_code = ac.getRaw();
  Serial.print("IR Code: 0x");
  for (uint8_t i = 0; i < kGreeStateLength; i++)
    Serial.printf("%02X", ir_code[i]);
  Serial.println();
}

// This function is called once everything is connected (Wifi and MQTT)
// WARNING : YOU MUST IMPLEMENT IT IF YOU USE EspMQTTClient
void onConnectionEstablished()
{

  client.subscribe("/ext/rrpc/#/home/dac", [](const String &topicStr, const String &message)
                   {
                     Serial.println(topicStr + "  " + message);
                     fadeLed(3, 88);
                     switch (hash_str_to_uint32(message.c_str()))
                     {
                     case hash_str_to_uint32("on"):
                     case hash_str_to_uint32("off"):
                     // value = 486C807F addr = 13842 command = 1 
                      //decode_type = 3
                      irsend.sendNEC(0x486C807FUL);
                       break;
                        // value = 486C609F addr = 13842 command = 6
                        // decode_type = 3
                     case hash_str_to_uint32("-"):
                       irsend.sendNEC(0x486C609FUL);
                       break;
                       // value = 486C40BF addr = 13842 command = 2
                     case hash_str_to_uint32("+"):
                       irsend.sendNEC(0x486C40BFUL);
                       break;
                       //value = 486CE01F addr = 13842 command = 7
                     case hash_str_to_uint32("input"):
                       irsend.sendNEC(0x486CE01FUL);
                       delay(300);
                       irsend.sendNEC(0x486CE01FUL);
                       break;
                     case hash_str_to_uint32("timerCancel"):
                       dacPowerTiker.detach();
                       break;
                     default:
                       if (message.startsWith("timer:"))
                       {
                         String m = message.substring(message.lastIndexOf(":")+1);
                         Serial.println("set dac timer : " + m);
                         dacPowerTiker.once(m.toInt()*60, dac_power_timer_toggle);
                       }
                       break;
                     }
                     client.publish(topicStr, message);
                   });

  client.subscribe("/ext/rrpc/#/home/ac", [](const String &topicStr, const String &message)
                   {
                     Serial.println(topicStr + "  " + message);
                     fadeLed(3, 88);
                      printACState();
                     switch (hash_str_to_uint32(message.c_str()))
                     {
                       /*
                        * irrecv 20 -> value = 250200939 addr = 0 command = 0 decode_type = 55
                          irrecv 21 -> value = 9DA32078 addr = 0 command = 0 decode_type = -1
                          irrecv 22 -> value = 270200939 addr = 0 command = 0 decode_type = 55
                          irrecv 23 -> value = B113BDC0 addr = 0 command = 0 decode_type = -1
                        */
                     case hash_str_to_uint32("on"):
                        ac.on();
                        irsend.sendNEC(0x00FF02FDUL);
                        break;
                     case hash_str_to_uint32("off"):
                       // Set up what we want to send. See ir_Gree.cpp for all the options.
                        // Most things default to off.
                        ac.off();
                        irsend.sendNEC(0x00FF02FDUL);
                        // ac.setFan(1);
                        // // kGreeAuto, kGreeDry, kGreeCool, kGreeFan, kGreeHeat
                        // ac.setMode(kGreeCool);
                        // ac.setTemp(20);  // 16-30C
                        // ac.setSwingVertical(true, kGreeSwingAuto);
                        // ac.setXFan(false);
                        // ac.setLight(false);
                        // ac.setSleep(false);
                        // ac.setTurbo(false);
                       break;
                     case hash_str_to_uint32("-"):
                       irsend.sendNEC(0x00FF02FDUL);
                       break;
                     case hash_str_to_uint32("+"):
                       irsend.sendNEC(0x00FF9867UL);
                       break;
                     case hash_str_to_uint32("model"):
                       irsend.sendNEC(0x00FF9867UL);
                       break;
                     default:
                       break;
                     }
                      ac.send();
                      client.publish(topicStr, ac.toString());
                   });

  // client.subscribe("volumio", [](const String &topicStr, const String &message)
  //                  {
  //                    Serial.println(payload);
  //                    recMsg = payload;
  //                  });

  // Subscribe to "mytopic/wildcardtest/#" and display received message to Serial
  // client.subscribe("mytopic/wildcardtest/#", [](const String &topic, const String &topicStr, const String &message)
  //                  { Serial.println("(From wildcard) topic: " + topic + ", payload: " + payload); });

  // Publish a message to "mytopic/test"
  // client.publish("/gbhp57tmeNa/officePC/user/mytopic/test", "This is a message"); // You can activate the retain flag by setting the third parameter to true

  // Execute delayed instructions
  // client.executeDelayed(5 * 1000, []()
  //                       { client.publish("mytopic/wildcardtest/test123", "This is a message sent 5 seconds later"); });
}
