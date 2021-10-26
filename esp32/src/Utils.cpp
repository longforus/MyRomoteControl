#include "Utils.h"

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

String int_array_to_hex_string(uint8_t int_array[], int size_of_array)
{
    String str = "";
    for (int temp = 0; temp < size_of_array; temp++){
        if (temp!=0)
        {
           str.concat(" ");
        }
        uint8_t i = int_array[temp];
        if (i<10)
        {
             str.concat("0");
        }
        str.concat(String(i,16));
    }
    return str;
}