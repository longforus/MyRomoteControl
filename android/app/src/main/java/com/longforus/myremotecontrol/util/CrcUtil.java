package com.longforus.myremotecontrol.util;

public class CrcUtil {

    private CrcUtil() {

    }

    public static byte[] CRC8_TAB = new byte[] {
        0,94,-68,-30,97,63,-35,-125,-62,-100,126,32,-93,-3,31,65,-99,-61,33,127,-4,-94,64,30,95,1,-29,-67,62,96,-126,-36,35,125,-97,-63,66,
        28,-2,-96,-31,-65,93,3,-128,-34,60,98,-66,-32,2,92,-33,-127,99,61,124,34,-64,-98,29,67,-95,-1,70,24,-6,-92,39,121,-101,-59,-124,-38,
        56,102,-27,-69,89,7,-37,-123,103,57,-70,-28,6,88,25,71,-91,-5,120,38,-60,-102,101,59,-39,-121,4,90,-72,-26,-89,-7,27,69,-58,-104,
        122,36,-8,-90,68,26,-103,-57,37,123,58,100,-122,-40,91,5,-25,-71,-116,-46,48,110,-19,-77,81,15,78,16,-14,-84,47,113,-109,-51,17,79,
        -83,-13,112,46,-52,-110,-45,-115,111,49,-78,-20,14,80,-81,-15,19,77,-50,-112,114,44,109,51,-47,-113,12,82,-80,-18,50,108,-114,-48,
        83,13,-17,-79,-16,-82,76,18,-111,-49,45,115,-54,-108,118,40,-85,-11,23,73,8,86,-76,-22,105,55,-43,-117,87,9,-21,-75,54,104,-118,-44,
        -107,-53,41,119,-12,-86,72,22,-23,-73,85,11,-120,-42,52,106,43,117,-105,-55,74,20,-10,-88,116,42,-56,-106,21,75,-87,-9,-74,-24,10,
        84,-41,-119,107,53 };

    public static String byteToStr(int var0,byte[] var1) {
        StringBuilder var2 = new StringBuilder();
        for (int var3 = 0; var3 < var0; ++var3) {
            String var4 = Integer.toHexString(var1[var3] & 255);
            StringBuilder var5;
            if (var4.length() == 1) {
                var5 = new StringBuilder();
                var5.append("0");
                var5.append(var4);
                var4 = var5.toString();
            } else {
                var5 = new StringBuilder();
                var5.append(var4);
                var4 = var5.toString();
            }
            var2.append(var4);
        }
        return var2.toString();
    }

    public static byte calcCrc8(byte[] var0,int var1,int var2) {
        return calcCrc8(var0,var1,var2,(byte)0);
    }

    public static byte calcCrc8(byte[] var0,int var1,int var2,byte var3) {
        int var6 = var1;

        byte var4;
        byte var5;
        for (var5 = var3; var6 < var1 + var2; var5 = var4) {
            var4 = CRC8_TAB[(var0[var6] ^ var5) & 255];
            ++var6;
        }

        return var5;
    }

    public static byte[] HexToByteArr(String inHex) {
        int hexlen = inHex.length();
        byte[] result;
        if (isOdd(hexlen) == 1) {
            hexlen++;
            result = new byte[hexlen / 2];
            inHex = "0" + inHex;
        } else {
            result = new byte[hexlen / 2];
        }
        int j = 0;
        for (int i = 0; i < hexlen; i += 2) {
            result[j] = HexToByte(inHex.substring(i, i + 2));
            j++;
        }
        return result;
    }

    public static int isOdd(int num) {
        return num & 0x1;
    }

    public static byte HexToByte(String inHex) {
        return (byte) Integer.parseInt(inHex, 16);
    }
}
