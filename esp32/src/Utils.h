
#include <Arduino.h>

int hex_char_value(char c);
int hex_to_decimal(const char *szHex, int len);
String int_array_to_hex_string(uint8_t int_array[], int size_of_array);