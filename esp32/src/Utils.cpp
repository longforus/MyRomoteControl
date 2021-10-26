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
