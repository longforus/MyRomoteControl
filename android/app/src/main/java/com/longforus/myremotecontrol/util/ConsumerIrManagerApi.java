package com.longforus.myremotecontrol.util;

import android.content.Context;

public class ConsumerIrManagerApi {

	    private static ConsumerIrManagerApi instance;
	    private static android.hardware.ConsumerIrManager service;
	
	    private ConsumerIrManagerApi(Context context) {
	        //Android4.4才开始支持红外功能
			// 获取系统的红外遥控服务
			service = (android.hardware.ConsumerIrManager) context.getApplicationContext().getSystemService(Context.CONSUMER_IR_SERVICE);
		}
	
	    public static ConsumerIrManagerApi getConsumerIrManager(Context context){
	        if(instance == null){
	            instance = new ConsumerIrManagerApi(context);
	        }
	        return instance;
	    }
	
	    /**
	     * 手机是否有红外功能
	     * @return
	     */
	    public static boolean hasIrEmitter() {
	        //android4.4及以上版本&有红外功能
	        if(service!=null){
	            return service.hasIrEmitter();
	        }
	        //android4.4以下及4.4以上没红外功能
	        return false;
	    }
	
	    /**
	     * 发射红外信号
	     * @param carrierFrequency 红外频率
	     * @param pattern
	     */
	    public static void transmit(int carrierFrequency, int[] pattern) {
	        if(service!=null){
	            service.transmit(carrierFrequency, pattern);
	        }
	    }
	
	    /**
	     * 获取可支持的红外信号频率
	     * @return
	     */
	    public static android.hardware.ConsumerIrManager.CarrierFrequencyRange[] getCarrierFrequencies() {
	        if(service!=null){
	            return service.getCarrierFrequencies();
	        }
	        return null;
	    }




	/**
	 * 将16进制字符串转换为适合发送的NEC模式
	 *
	 * @param hexString 要转换的16进制字符串
	 * @return 适合发送的NEC模式
	 */
	public static int[] convertHexStringToPattern(String hexString) {
		// 将16进制字符串转换为长整型数字
		long code = Long.parseLong(hexString, 16);

		// 将长整型数字转换为NEC红外编码模式
		int[] pattern = new int[68]; // NEC红外编码模式的长度为68，数组索引从0开始，最大索引是67
		for (int i = 0; i < 67; i++) {
			if ((code & (1L << i)) != 0) {
				if (i < 32) {
					pattern[i * 2] = 560; // 高电平时间为560us
					pattern[i * 2 + 1] = 1690; // 低电平时间为1690us
				} else {
					pattern[(i - 32) * 2 + 64] = 560; // 高电平时间为560us，加上64是因为前面已经使用了0-31
					pattern[(i - 32) * 2 + 65] = 560; // 低电平时间为560us
				}
			} else {
				if (i < 32) {
					pattern[i * 2] = 560; // 高电平时间为560us
					pattern[i * 2 + 1] = 560; // 低电平时间为560us
				} else {
					pattern[(i - 32) * 2 + 64] = 560; // 高电平时间为560us，加上64是因为前面已经使用了0-31
					pattern[(i - 32) * 2 + 65] = 1690; // 低电平时间为1690us
				}
			}
		}

		// 返回转换后的模式
		return pattern;
	}
}

