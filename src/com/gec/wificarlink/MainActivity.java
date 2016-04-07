package com.gec.wificarlink;

import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.net.Socket;
import java.util.Locale;

import com.gec.wificarlink.util.Logger;
import com.gec.wificarlink.util.Toaster;
import com.gec.wificarlink.widget.Direction;
import com.gec.wificarlink.widget.Rudder;
import com.gec.wificarlink.widget.Rudder.RudderListener;

import android.app.Activity;
import android.content.Context;
import android.content.Intent;
import android.net.ConnectivityManager;
import android.net.NetworkInfo.State;
import android.os.Bundle;
import android.os.Handler;
import android.provider.Settings;
import android.view.Window;
import android.view.WindowManager;
import android.view.animation.Animation;
import android.view.animation.AnimationUtils;
import android.view.animation.LinearInterpolator;
import android.widget.ImageView;
import android.widget.TextView;

public class MainActivity extends Activity implements RudderListener {
	// 调试用
	private static final String TAG = MainActivity.class.getCanonicalName();
	// 服务器IP地址
	private static final String IP = "192.168.4.1";

	// 开启WiFi请求码
	private static final int REQUEST_WIFI = 0x01;
	// 服务器端口
	private static final int PORT = 333;

	// 协议常量
	private static byte[] data = new byte[] { 0x00 };

	// 提示内容
	private TextView mTextView = null;
	// 虚拟摇杆
	private Rudder mRudder = null;

	// 加载动画
	private ImageView mWheelView = null;
	private Animation mAnimation = null;

	private Socket mSocket = null;
	// 输出流
	private OutputStream mOutS = null;

	@Override
	protected void onCreate(Bundle savedInstanceState) {
		super.onCreate(savedInstanceState);

		getWindow().addFlags(WindowManager.LayoutParams.FLAG_FULLSCREEN);
		this.requestWindowFeature(Window.FEATURE_NO_TITLE);
		setContentView(R.layout.activity_main);

		init();
	}

	private void init() {
		mTextView = (TextView) this.findViewById(R.id.text_view);
		mRudder = (Rudder) this.findViewById(R.id.rudder);
		mWheelView = (ImageView) this.findViewById(R.id.wheel_view);

		mRudder.setOnRudderListener(this);

		// 加载动画
		mAnimation = AnimationUtils.loadAnimation(this, R.anim.rotate);

		// 设置匀速旋转速率
		mAnimation.setInterpolator(new LinearInterpolator());
	}

	@Override
	protected void onStart() {
		super.onStart();

		checkNetworkInfo();
	}

	@Override
	protected void onStop() {
		close();

		super.onStop();
	}

	@Override
	protected void onActivityResult(int requestCode, int resultCode, Intent data) {
		if (requestCode == REQUEST_WIFI) {
			// Handler延时3s后再执行UDP广播线程，防止程序崩溃退出
			new Handler().postDelayed(new Runnable() {

				@Override
				public void run() {
					new TcpThread().start();
				}
			}, 3 * 1000);
		}
		super.onActivityResult(requestCode, resultCode, data);
	}

	@Override
	public void onBackPressed() {
		close();

		super.onBackPressed();
	}

	/**
	 * 检测网络状态
	 */
	@SuppressWarnings("deprecation")
	private void checkNetworkInfo() {
		// 获取系统服务
		ConnectivityManager conn = (ConnectivityManager) getSystemService(Context.CONNECTIVITY_SERVICE);
		// 获取WiFi信号的状态
		State wifiState = conn.getNetworkInfo(ConnectivityManager.TYPE_WIFI).getState();
		Logger.d(TAG, "[9] --> WiFi state:" + wifiState.toString());

		if (wifiState == State.CONNECTED || wifiState == State.CONNECTING) { // 判断连接状态
			// 执行UDP广播线程
			new TcpThread().start();
			return;
		}

		Toaster.shortToastShow(this, "都什么年代了，还塞网络o(╯□╰)o");
		// 启动WiFi设置页面并回调onActivityResult()方法
		startActivityForResult(new Intent(Settings.ACTION_WIFI_SETTINGS), REQUEST_WIFI);
	}

	/**
	 * TCP连接线程
	 */
	private class TcpThread extends Thread {

		@Override
		public void run() {
			InputStream inS = null;

			try {
				mSocket = new Socket(IP, PORT);
				mOutS = mSocket.getOutputStream();
				inS = mSocket.getInputStream();

				byte[] data = new byte[512];
				int len;
				while ((len = inS.read(data)) > 0) {
					String tcpResponse = byteArray2HexStr(data, 0, len);
					Logger.d(TAG, "[10] -->" + tcpResponse);
				}
			} catch (IOException e) {
				// 屏蔽Log错误信息
				// throw new RuntimeException("输入输出异常", e);
			}
		}
	}

	/**
	 * 字节数组转化为十六进制字符串
	 * 
	 * @param data
	 * @param offset
	 * @param byteCount
	 * @return
	 */
	private String byteArray2HexStr(byte[] data, int offset, int byteCount) {
		String ret = "";

		for (int i = offset; i < byteCount; i++) {
			String hex = Integer.toHexString(data[i] & 0xFF);
			String newHex = String.format(Locale.getDefault(), "%02s", hex);
			ret += newHex.toUpperCase(Locale.getDefault());
		}
		return ret;
	}

	/**
	 * 关闭Socket
	 */
	private void close() {
		if (mSocket != null) {
			try {
				mSocket.close();
			} catch (IOException e) {
				throw new RuntimeException("输入输出异常", e);
			}
		}
	}

	/**
	 * 发送数据
	 * 
	 * @param data
	 */
	private void writeStream(byte[] data) {
		try {
			if (mOutS != null) {
				// 写数据到输出流
				mOutS.write(data);
				// 调用write()之后数据依然留在缓存中，必须调用flush()，才能将数据真正发送出去
				mOutS.flush();
			}
		} catch (IOException e) {
			// 涉及UI操作的必须在主线程中运行，runOnUiThread()的原理即为Handler消息处理机制
			runOnUiThread(new Runnable() {

				@Override
				public void run() {
					Toaster.shortToastShow(MainActivity.this, "连接超时，被服务器君抛弃了::>_<::");
					// 结束程序
					MainActivity.this.finish();
				}
			});
		}
	}

	@Override
	public void onSteeringWheelChanged(int action, Direction direction) {
		if (action == Rudder.ACTION_RUDDER) {
			switch (direction) {

			case LEFT_DOWN_DIR:
				Logger.d(TAG, "[1] --> 左下拐...");
				mTextView.setText("左下拐...");
				data[0] = 0x07;
				break;

			case LEFT_DIR:
				Logger.d(TAG, "[2] --> 左拐...");
				mTextView.setText("左拐...");
				data[0] = 0x03;
				break;

			case LEFT_UP_DIR:
				Logger.d(TAG, "[3] --> 左上拐...");
				mTextView.setText("左上拐...");
				data[0] = 0x05;
				break;

			case UP_DIR:
				Logger.d(TAG, "[4] --> 向前突进...");
				mTextView.setText("向前突进...");
				data[0] = 0x01;
				break;

			case RIGHT_UP_DIR:
				Logger.d(TAG, "[5] --> 右上拐...");
				mTextView.setText("右上拐...");
				data[0] = 0x06;
				break;

			case RIGHT_DIR:
				Logger.d(TAG, "[6] --> 右拐...");
				mTextView.setText("右拐...");
				data[0] = 0x04;
				break;

			case RIGHT_DOWN_DIR:
				Logger.d(TAG, "[7] --> 右下拐...");
				mTextView.setText("右下拐...");
				data[0] = 0x08;
				break;

			case DOWN_DIR:
				Logger.d(TAG, "[8] --> 向后撤退...");
				mTextView.setText("向后撤退...");
				data[0] = 0x02;
				break;

			default:
				break;
			}
			// 延时300ms
			new Handler().postDelayed(new Runnable() {

				@Override
				public void run() {
					writeStream(data);
				}
			}, 300);
		}
	}

	@Override
	public void onAnimated(boolean isAnim) {
		if (isAnim) {
			mWheelView.startAnimation(mAnimation);
		} else {
			mWheelView.clearAnimation();
		}
	}
}
