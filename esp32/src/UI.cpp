#include "UI.h"
String recMsg = "connecting to wifi...";

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
    int lineCount = (int)ceil(title.length() / 20.0);
    int startX = 0;
    int startY = 0;

    for (size_t i = 0; i < lineCount; i++)
    {
        int startIndex = i * 20;
        String line = recMsg.substring(startIndex, min(startIndex + 20, (int)title.length()));
        display->drawString(startX + x, startY + y, line);
        startY += 13;
    }

    if (WiFi.status() == WL_CONNECTED)
    {
        String ipstr = "IP : ";
        ipstr += WiFi.localIP().toString();
        display->drawString(0 + x, startY + y, ipstr);
    }
}
