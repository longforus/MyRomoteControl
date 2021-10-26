#include <Arduino.h>

class UartCommand
{

public:
    int action;
    int board;
    int locker;
    u_long time;
     char * command;
    String topic;
    char * result;
};
