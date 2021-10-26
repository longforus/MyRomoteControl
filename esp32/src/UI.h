#include "WiFi.h"
#include <RTClib.h>


#ifdef i2cOLED
#include "SSD1306Wire.h"
#endif
#include "OLEDDisplayUi.h"

#ifdef i2cOLED
// Pin definitions for I2C OLED
const int I2C_DISPLAY_ADDRESS = 0x3C;
const int SDA_PIN = 23;
const int SCL_PIN = 22;
#endif

#ifdef i2cOLED
SSD1306Wire display(I2C_DISPLAY_ADDRESS, SDA_PIN, SCL_PIN); // I2C OLED
OLEDDisplayUi ui(&display);
#endif

//declaring prototypes
void drawProfile(OLEDDisplay *display, OLEDDisplayUiState *state, int16_t x, int16_t y);
void drawHardwareInfo(OLEDDisplay *display, OLEDDisplayUiState *state, int16_t x, int16_t y);
void drawSplash(OLEDDisplay *display, String label);

// this array keeps function pointers to all frames
// frames are the single views that slide from right to left
static FrameCallback frames[] = {drawProfile, drawHardwareInfo};
static const int numberOfFrames = 2;

// static char monName[12][4] = {"Jan", "Feb", "Mar", "Apr", "May", "Jun", "Jul", "Aug", "Sep", "Oct", "Nov", "Dec"};
static char wdayName[7][4] = {"Sun", "Mon", "Tue", "Wed", "Thu", "Fri", "Sat"};


static unsigned long millisTimeUpdated = millis();
extern DateTime now;
extern String recMsg;


