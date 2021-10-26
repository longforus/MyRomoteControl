#include <Arduino.h>

class UartCommand
{

public:
    int action;
    int board;
    int locker;
    double time;
    char *command;
    String topic;
    char *result;
};
