package com.longforus.myremotecontrol.util;

import java.util.ArrayList;
import java.util.List;
	
	/**
	 * Copyright  : huangr
	 * Created by huangr on 2019/8/8.
	 * ClassName  : NecPattern
	 * Description  : 构造NEC协议的pattern编码
	 */
	
	public class NecPattern {
	    //引导码
	    private static final int startH = 9000;
	    private static final int startL = 4500;
	
	    //结束码
	    private static final int endL = 560;
	    private static final int endH = 2000;
	
	    //高电平
	    private static final int high8 = 560;
	    //低电平0：1125
	    private static final int low0 = 565;
	    //低电平1：2250
	    private static final int low1 = 1690;
	
	    private static int[] pattern;
	    private static List<Integer> list = new ArrayList<>();
	
	
	    /**
	     * 正常发码：引导码（9ms+4.5ms）+用户编码（高八位）+用户编码（低八位）+键数据码+键数据反码+结束码
	     */
	    public static int[] buildPattern(int userCodeH, int userCodeL, int keyCode) {
	        //用户编码高八位00
	        String userH = constructBinaryCode(userCodeH);
	        //用户编码低八位DF
	        String userL = constructBinaryCode(userCodeL);
	        //数字码
	        String key = constructBinaryCode(keyCode);
	        //数字反码
	        String keyReverse = constructBinaryCode(~keyCode);
	
	        list.clear();
	        //引导码
	        list.add(startH);
	        list.add(startL);
	        //用户编码
	        changeAdd(userH);
	        changeAdd(userL);
	        //键数据码
	        changeAdd(key);
	        //键数据反码
	        changeAdd(keyReverse);
	        //结束码
	        list.add(endL);
	        list.add(endH);
	
	        int size = list.size();
	        pattern = new int[size];
	        for (int i = 0; i < size; i++) {
	            pattern[i] = list.get(i);
	        }
	        return pattern;
	    }
	
	    /**
	     * 十六进制键值转化为二进制串，并逆转编码
	     * @param keyCode
	     * @return
	     */
	    private static String constructBinaryCode(int keyCode) {
	        String binaryStr = convertToBinary(keyCode);
	        char[] chars = binaryStr.toCharArray();
	        StringBuffer sb = new StringBuffer();
	        for (int i = 7; i >= 4; i--) {
	            sb.append(chars[i]);
	        }
	
	        for (int i = 3; i >= 0; i--) {
	            sb.append(chars[i]);
	        }
	        return sb.toString();
	    }
	
	    /**
	     * 数字转换为长度为8位的二进制字符串
	     * @return
	     */
	    private static String convertToBinary(int num) {
	        String binary = Integer.toBinaryString(num);
	        StringBuffer sb8 = new StringBuffer();
	        //每个元素长度为8位，不够前面补充0
	        if (binary.length() < 8) {
	            for (int i = 0; i < 8 - binary.length(); i++) {
	                sb8.append("0");
	            }
	            String binaryStr8 = sb8.append(binary).toString();
	            return binaryStr8;
	        }else{
	            String binaryStr8 = binary.substring(binary.length() - 8);
	            return binaryStr8;
	        }
	    }
	
	    /**
	     * 二进制转成电平
	     *
	     * @param code
	     */
	    public static void changeAdd(String code) {
	        int len = code.length();
	        String part;
	        for (int i = 0; i < len; i++) {
	            list.add(high8);
	            part = code.substring(i, i + 1);
                if (part.equals("0")) {
                    list.add(low0);
                } else {
                    list.add(low1);
                }
	        }
	    }
	}
