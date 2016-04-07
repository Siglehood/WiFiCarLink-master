package com.gec.wificarlink.util;

import android.graphics.Point;

public class MathUtil {
	/**
	 * 获取两点间直线距离(勾股定理)
	 * 
	 * @param x0
	 * @param y0
	 * @param x
	 * @param y
	 * @return
	 */
	public static int getLength(float x0, float y0, float x, float y) {
		return (int) Math.sqrt(Math.pow(x0 - x, 2) + Math.pow(y0 - y, 2));
	}

	/**
	 * 获取边缘上某个点的坐标
	 * 
	 * @param a
	 * @param b
	 * @param cutRadius
	 * @return
	 */
	public static Point getBorderPoint(Point a, Point b, int cutRadius) {
		float radian = getRadian(a, b);
		return new Point(a.x + (int) (cutRadius * Math.cos(radian)), a.x + (int) (cutRadius * Math.sin(radian)));
	}

	/**
	 * 获取水平线夹角弧度
	 * 
	 * @param a
	 * @param b
	 * @return
	 */
	public static float getRadian(Point a, Point b) {
		float lenA = b.x - a.x;
		float lenB = b.y - a.y;
		float lenC = (float) Math.sqrt(lenA * lenA + lenB * lenB);
		float ang = (float) Math.acos(lenA / lenC);
		ang = ang * (b.y < a.y ? -1 : 1);
		return ang;
	}

	/**
	 * 获取摇杆偏移角度
	 * 
	 * @param radian
	 * @return
	 */
	public static int getAngleConvert(float radian) {
		int tmp = (int) Math.round(radian / Math.PI * 180);
		if (tmp < 0) {
			return -tmp;
		} else {
			return 180 + (180 - tmp);
		}
	}
}
