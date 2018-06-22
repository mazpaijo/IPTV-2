package com.iptv.hn.service;

import android.app.IntentService;
import android.content.BroadcastReceiver;
import android.content.Context;
import android.content.Intent;
import android.content.IntentFilter;
import android.graphics.Bitmap;
import android.graphics.BitmapFactory;
import android.graphics.PixelFormat;
import android.net.ConnectivityManager;
import android.net.NetworkInfo;
import android.net.wifi.WifiInfo;
import android.net.wifi.WifiManager;
import android.os.Handler;
import android.os.IBinder;
import android.os.Looper;
import android.text.Html;
import android.text.TextUtils;
import android.util.Log;
import android.view.Gravity;
import android.view.LayoutInflater;
import android.view.View;
import android.view.WindowManager;
import android.widget.TextView;

import com.iptv.hn.AdsView;
import com.iptv.hn.Contants;
import com.iptv.hn.PacketManager;
import com.iptv.hn.R;
import com.iptv.hn.entity.AdsBean;
import com.iptv.hn.entity.PushMsgStack;
import com.iptv.hn.utility.AdsKeyEventHandler;
import com.iptv.hn.utility.Api;
import com.iptv.hn.utility.Callback;
import com.iptv.hn.utility.DownloadManager;
import com.iptv.hn.utility.JsonUtil;
import com.iptv.hn.utility.PfUtil;
import com.iptv.hn.utility.Utils;

import org.json.JSONException;
import org.json.JSONObject;

import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.net.InetSocketAddress;
import java.net.Socket;
import java.util.HashMap;
import java.util.List;

import pl.droidsonroids.gif.GifDrawable;
import pl.droidsonroids.gif.GifImageView;

/***
 * 联网服务：根据网络条件具备重连机制。
 */
public class TcpService extends IntentService {

	protected View mAdsLayerView;
	protected int mAdsCounter;

	public TcpService() {
		super("");
	}

	public TcpService(String name) {
		super(name);
	}

	private static final String TAG = TcpService.class.getSimpleName();

	Thread  initUdpChanelThread;

	Socket socket;
	InputStream inStream;
	OutputStream outStream;

	public static boolean tcpChannelIsReady;

	private static HashMap<Long, Long> mReceivedMessageIds = new HashMap<Long, Long>();

	@Override
	public int onStartCommand(Intent intent, int flags, int startId) {
        Log.i("iptv", "mobile ip = " + Utils.getPhoneIp(this));
		//      startTcpDeamon();
		//		registerBroadcastReceiver();
		return super.onStartCommand(intent, flags, startId);

//		return START_STICKY;

	}

	private void openTcpChannel() {
		//需要优化为 nio方式
		try {

			if (socket != null) {
				socket = null;
			}

			socket = new Socket();
			socket.connect(new InetSocketAddress(Contants.IPTV_UDP_IP, Contants.IPTV_UDP_PORT), 20000);
			socket.setReuseAddress(true);

			if (!socket.isConnected()) {
				socket.connect(new InetSocketAddress(Contants.IPTV_UDP_IP, Contants.IPTV_UDP_PORT), 20000);
				socket.setReuseAddress(true);
			}

			inStream = socket.getInputStream();
			outStream = socket.getOutputStream();

		} catch (IOException e) {
			e.printStackTrace();
		}
	}

	private void startTcpDeamon() {

		if (tcpChannelIsReady) {
			return;
		}

		InitUdpChanel();

	}

	/**
	 * 监听网络状态变化广播， 应对TCP的及时断开和连接
	 */
	private void registerBroadcastReceiver() {
		IntentFilter intentFilter = new IntentFilter();
		intentFilter.addAction(WifiManager.SUPPLICANT_CONNECTION_CHANGE_ACTION);
		intentFilter.addAction(WifiManager.NETWORK_STATE_CHANGED_ACTION);
		intentFilter.addAction(WifiManager.WIFI_STATE_CHANGED_ACTION);
		intentFilter.addAction(WifiManager.SUPPLICANT_STATE_CHANGED_ACTION);
		//AP配置主动关闭连接，避免再连接时连不上服务器  (Netty无法处理)
//		intentFilter.addAction(Config.BROADCAST_NETWORK_CLOSE);
		intentFilter.addAction("android.net.conn.CONNECTIVITY_CHANGE");

		getApplication().registerReceiver(broadcastReceiver, intentFilter);
	}

	private boolean isOnNet(Context context) {
		if (null == context) {
			Log.e("", "context is null");
			return false;
		}
		boolean isOnNet = false;
		ConnectivityManager connectivityManager = (ConnectivityManager) context
				.getSystemService(Context.CONNECTIVITY_SERVICE);
		NetworkInfo activeNetInfo = connectivityManager.getActiveNetworkInfo();
		if (null != activeNetInfo) {
			isOnNet = activeNetInfo.isConnected();
			Log.i(TAG, "active net info:" + activeNetInfo);
		}
		return isOnNet;
	}


	private void startUp(Context context) {
		boolean isNetworkOk = isOnNet(context);

		WifiManager myWifiManager = (WifiManager) context
				.getSystemService(Context.WIFI_SERVICE);
		WifiInfo wifiInfo = myWifiManager.getConnectionInfo();

		String unknownSsid = "<unknown ssid>";

		//如果连上的不是设备热点， 网络重新连接后，需要重新启动tcp服务
		if (wifiInfo.getSSID()!=null
				&& !TextUtils.isEmpty(wifiInfo.getSSID())
				&& !wifiInfo.getSSID().equals(unknownSsid)
				&& !tcpChannelIsReady
				&& isNetworkOk
				)
		{

			//为了防止电信网络故障引起的客户端集体重连，重连会在10倍心跳间隔内随机
//			int duration = Contants.DURATION_PING;
//			if (duration <= 10 * 1000) {
//				duration = 120 * 1000;
//			}
//
//			duration = duration*10;
//
//			int delay = 3000 + new Random().nextInt(duration);
//
//			new Handler(Looper.getMainLooper()).postDelayed(new Runnable() {
//				public void run(){
//
//					startTcpDeamon();
//
//				}
//			}, delay);


			//先不改了
			new Handler(Looper.getMainLooper()).postDelayed(new Runnable() {
				public void run(){

					startTcpDeamon();

				}
			}, 3000);
		}
	}


	/**
	 * wifi 信号变化监听；当wifi断开或重新连接上的时候， app要能感知，便于能重新连接设备。
	 * 这个页面是主要页面，在这个页面监听这些广播就好了
	 * @by ligao
	 *
	 */
	public BroadcastReceiver broadcastReceiver = new BroadcastReceiver() {

		@Override
		public void onReceive(Context context, Intent intent) {

			final String action = intent.getAction();

			if (action.equals(WifiManager.SUPPLICANT_CONNECTION_CHANGE_ACTION)) {

				if (!intent.getBooleanExtra(
						WifiManager.EXTRA_SUPPLICANT_CONNECTED, false)) {

				}
			}

			else if (action.equals("android.net.conn.CONNECTIVITY_CHANGE")) {

				startUp(context);

			}
		}
	};

	@Override
	public void onCreate() {
		super.onCreate();
	}

	@Override
	public void onStart(Intent intent, int startId) {
		super.onStart(intent, startId);

	}

	@Override
	public void onDestroy() {
//		Toast.makeText(this, "冒泡退出服务", Toast.LENGTH_SHORT).show();
	}

	@Override
	public void onLowMemory() {
		super.onLowMemory();
	}

	/**建立TCP通道*/
	private void InitUdpChanel() {

		initUdpChanelThread = 	new Thread(new Runnable() {

			@Override
			public void run() {

				openTcpChannel();

				if (socket==null) {
					return;
				}

				sendAppInitPacket();

				try {
					tcpChannelIsReady = true;

					byte[] buffer = new byte[1024*1];
					int len = 0;

					boolean flag = true;
					while(flag) {

						if (!tcpChannelIsReady) {
							Thread.sleep(500);
							continue;
						}

						try {
							len = inStream.read(buffer);
						} catch (IOException e) {
							e.printStackTrace();
							Thread.sleep(500);
							tcpChannelIsReady = false;

							flag = false;
						}

						if (len == Integer.MAX_VALUE) {
							Thread.sleep(500);
							continue;
						}

						if (len <= 0) {
							Thread.sleep(500);
							continue;
						}

						byte[] binary = new byte[len]; //创建临时缓冲区
						System.arraycopy(buffer, 0, binary, 0, len);

						String content = new String(binary, "UTF-8");

						receive(content);
					}

					//stopSelf();
				} catch (Exception ex) {
					ex.printStackTrace();
				}

				tcpChannelIsReady = false;

				try {
					//						socket.shutdownInput();
					//						socket.shutdownOutput();
					socket.close();
				} catch (IOException e) {
					// TODO Auto-generated catch block
					e.printStackTrace();
				}

				startUp(TcpService.this);

			}

		});

		initUdpChanelThread.start();
	}

	private void receive(String data) {
		Log.e("iptv",">>>>>>>>>> receive " + data);

		try {
			JSONObject json = new JSONObject(data);

			if (json.has("get_time")) {
				int pingDuration = json.getInt("get_time");
				Log.e("iptv","get_time = " + pingDuration);

				Contants.DURATION_PING = pingDuration*1000;

				Contants.LAST_PING_TIMESTAMP = System.currentTimeMillis();

			}

			if (!json.has("list")) {
				return; //非消息推送，不应答
			}

			List<AdsBean> adsList = JsonUtil.jsonArrayStringToList(json.getJSONArray("list").toString(), AdsBean.class);

			for (AdsBean adsBean : adsList) {
				Log.i("iptv", "receive ,msgid = " + adsBean.getMsg_id());
				try {
					PushMsgStack.putMessage(this, adsBean);
				} catch (Exception e) {
					e.printStackTrace();
				}
				//推送消息回执
				responsePush(adsBean);
			}

//            AdsBean adsBean = JsonUtil.jsonStringToObject(data, AdsBean.class);
//            try {
//                PushMsgStack.putMessage(this, adsBean);
//            } catch (Exception e) {
//                e.printStackTrace();
//            }
//            //推送消息回执
//            responsePush(adsBean);

		} catch (JSONException e) {
			e.printStackTrace();
		}

		showCommingMessage();

	}

	protected void showCommingMessage() {
		AdsBean bean = PushMsgStack.getTopMessage(this);
		if (bean != null) {
			Log.i("iptv", "common msg" + bean.getMsg_id());
			showAdsTemplate(bean);
		} else {
			int duration = Contants.DURATION_TOAST_MESSAGE;
			AdsBean targetBean = PushMsgStack.notifyAlarmMessage(this, duration);
			if (targetBean != null) {
				Log.i("iptv", "targetBean msg" + targetBean.getMsg_id());
				//应该
				showAdsTemplate(targetBean);
			} else if (PushMsgStack.getMessageCount(this) > 0) {
				Log.i("iptv", "wait msg.." );
				Log.i("iptv", "Contants.DURATION_TOAST_MESSAGE = "+Contants.DURATION_TOAST_MESSAGE);
				new Handler(Looper.getMainLooper()).postDelayed(new Runnable() {
					@Override
					public void run() {

						showCommingMessage();

					}
				}, Contants.DURATION_TOAST_MESSAGE);
			}
		}
	}

	/**
	 * 推送消息回执
	 */
	protected void responsePush(AdsBean adsBean) {

		String user = Utils.getTvUserId(this);
		String localIp = Utils.getPhoneIp(this);

		String responseData = PacketManager.getResponseUdpData(adsBean.getMsg_id(), user, localIp);
		sendTcpPacket(responseData);

		Log.i("iptv", "responseData =" + responseData);

		//行为日志上报
		try {
			Api.postUserBehaviors(this, adsBean.getMsg_id() + "", user, localIp, "0", adsBean.getBusi_id());
		} catch (Exception ex) {
			Log.i("iptv", "postUserBehaviors error");
//			ex.printStackTrace();
		}
	}

	private void sendTcpPacket(String data) {
		if(socket== null || socket.isClosed()) {
			tcpChannelIsReady = false;
			return;
		}

		try {
			byte[] dataArray = data.getBytes("UTF-8");
			outStream.write(dataArray, 0, dataArray.length);
			outStream.flush();
		} catch (IOException e) {
			e.printStackTrace();

			//TODO APP网络重连，ETS无法返回数据， ETS会主动关闭。APP需要重新连接。
			tcpChannelIsReady = false;

			new Handler(Looper.getMainLooper()).postDelayed(new Runnable() {
				public void run(){

					startTcpDeamon();

				}
			}, 5000);

		} catch (Exception ex) {
			ex.printStackTrace();
		}
	}

	@Override
	public IBinder onBind(Intent intent) {
		// TODO Auto-generated method stub
		return null;
	}

	protected void getServerConfigure() {

		final PfUtil pfUtil = PfUtil.getInstance();
		pfUtil.init(this);
		long lastConfigTime = pfUtil.getLong("server_config_time", 0);

		//每天只更新配置文件一次
		long now = System.currentTimeMillis();
		long peroid = (now - lastConfigTime)/(1000*60*60); //hour
		if (peroid < 12) {
			return;
		}

//		pfUtil.putLong("server_config_time", System.currentTimeMillis());

//		Api.getConfigure(this);
	}

	protected void hideAdsDialog(final Context context, final AdsBean adsBean) {

		if (mAdsLayerView == null) {
			return;
		}

		new Handler(Looper.getMainLooper()).post(new Runnable() {
			@Override
			public void run() {

				PushMsgStack.deleteResources(adsBean);

				if (mAdsLayerView == null) {
					return;
				}

				if (mAdsLayerView.getWindowToken() != null) {
					WindowManager windowManager = (WindowManager) getSystemService(Context.WINDOW_SERVICE);
					windowManager.removeViewImmediate(mAdsLayerView);
				}

				mAdsLayerView.setVisibility(View.GONE);
				mAdsLayerView = null;

			}
		});
	}

	protected void showAdsDialog(final AdsBean adsBean, String localPath) {

		if (mReceivedMessageIds.keySet().contains(adsBean.getMsg_id())) {
			//收到过，就不弹窗了
            //TODO 要限定当天内
			Long lastTime = mReceivedMessageIds.get(adsBean.getMsg_id());

			Log.i("iptv", "已经收到过该消息 " + adsBean.getMsg_id());
			return;
		}

		mReceivedMessageIds.put(adsBean.getMsg_id(), System.currentTimeMillis());


		final WindowManager wm = (WindowManager)getSystemService(WINDOW_SERVICE);
		WindowManager.LayoutParams params = new WindowManager.LayoutParams(WindowManager.LayoutParams.WRAP_CONTENT, WindowManager.LayoutParams.WRAP_CONTENT);

//        params.type = WindowManager.LayoutParams.TYPE_SYSTEM_ALERT | WindowManager.LayoutParams.TYPE_SYSTEM_OVERLAY;
		params.type = WindowManager.LayoutParams.TYPE_SYSTEM_ALERT | WindowManager.LayoutParams.TYPE_SYSTEM_OVERLAY;
		params.flags = WindowManager.LayoutParams.FLAG_NOT_TOUCH_MODAL | WindowManager.LayoutParams.FLAG_ALT_FOCUSABLE_IM;
//        params.flags =  WindowManager.LayoutParams.FLAG_ALT_FOCUSABLE_IM;

		params.width = WindowManager.LayoutParams.WRAP_CONTENT;
		params.height = WindowManager.LayoutParams.WRAP_CONTENT;
		params.alpha = 1.0f;
		params.format = PixelFormat.RGBA_8888;

		if (adsBean.getPosition() == AdsBean.SHOW_AT_LEFT_TOP) {
			params.gravity = Gravity.TOP|Gravity.LEFT;
			params.windowAnimations = android.R.style.Animation_Toast;
		} else if (adsBean.getPosition() == AdsBean.SHOW_AT_RIGHT_TOP) {
			params.gravity = Gravity.TOP|Gravity.RIGHT;
			params.windowAnimations = android.R.style.Animation_Translucent; //平移
		} else if (adsBean.getPosition() == AdsBean.SHOW_AT_LEFT_BOTTOM) {
			params.gravity = Gravity.BOTTOM|Gravity.LEFT;
			params.windowAnimations = android.R.style.Animation_Toast;
		} else if (adsBean.getPosition() == AdsBean.SHOW_AT_RIGHT_BOTTOM) {
			params.gravity = Gravity.BOTTOM|Gravity.RIGHT;
			params.windowAnimations = android.R.style.Animation_Translucent; //平移
		} else if (adsBean.getPosition() == AdsBean.SHOW_AT_CENTER) {
			params.gravity = Gravity.CENTER;
			params.windowAnimations = android.R.style.Animation_Toast;
		} else {
			params.gravity = Gravity.BOTTOM|Gravity.RIGHT;
			params.windowAnimations = android.R.style.Animation_Translucent; //平移
		}

		params.x = 0;
		params.y = 0;
//		params.windowAnimations = android.R.style.Animation_Translucent; //平移
//        params.windowAnimations = android.R.style.Animation_InputMethod;

		final Context context = this;

		LayoutInflater inflater =
				(LayoutInflater) getSystemService(Context.LAYOUT_INFLATER_SERVICE);

		if (adsBean.getModel_id() == 1) {
			mAdsLayerView = inflater.inflate(R.layout.layout_ads_layer3, null);
		} else if (adsBean.getModel_id() == 2) {
			mAdsLayerView = inflater.inflate(R.layout.layout_ads_layer3, null);
		} else {
			mAdsLayerView = inflater.inflate(R.layout.layout_ads_layer4, null);
		}

		mAdsLayerView.setBackgroundColor(context.getResources().getColor(R.color.transparent));
		mAdsLayerView.setFocusable(true);
//        mAdsLayerView.getLocationOnScreen();
		wm.addView(mAdsLayerView, params);



		AdsView adsView = (AdsView) mAdsLayerView.findViewById(R.id.adsView);
		adsView.setKeyBoardCallback(new Callback() {
			@Override
			public void onStart(Object... o) {

			}

			@Override
			public void onProgress(Object... o) {

			}

			@Override
			public void onFinish(Object... o) {
//                int keyCode = (int)o[0];
				AdsKeyEventHandler.onKeyOk(context, adsBean);
				hideAdsDialog(context, adsBean);

				//行为日志上报
				String user = Utils.getTvUserId(context);
				String localIp = Utils.getPhoneIp(context);
				try {
					Api.postUserBehaviors(context, adsBean.getMsg_id() + "", user, localIp, "10", adsBean.getBusi_id());
				} catch (Exception ex) {
					Log.i("iptv", "postUserBehaviors error");
//			ex.printStackTrace();
				}
			}

			@Override
			public void onFail(Object... o) {
				hideAdsDialog(context, adsBean);
			}
		});

		GifImageView gifView = (GifImageView) mAdsLayerView.findViewById(R.id.gif);
		final TextView counterView = (TextView) mAdsLayerView.findViewById(R.id.counter);
		TextView tipsView = (TextView) mAdsLayerView.findViewById(R.id.textDesc);
		counterView.setText(String.valueOf(adsBean.getShow_time()));

		if (adsBean.getModel_id() == 2) {
			tipsView.setText(Html.fromHtml(getString(R.string.ads_rich_text)));
			tipsView.setTextColor(context.getResources().getColor(R.color.black));
		} else {
			tipsView.setTextColor(context.getResources().getColor(R.color.white));
		}

		try {
			if (adsBean.getFile_type() == AdsBean.FILE_GIF) {
				GifDrawable gifFromResource = new GifDrawable( localPath);
				gifView.setImageDrawable(gifFromResource);
			} else {
				Bitmap bitmap = BitmapFactory.decodeFile(localPath);
				gifView.setImageBitmap(bitmap);
			}

		} catch (IOException e) {
			e.printStackTrace();
		}

		gifView.setOnClickListener(new View.OnClickListener() {
			@Override
			public void onClick(View v) {
				AdsKeyEventHandler.onKeyOk(context, adsBean);
				hideAdsDialog(context, adsBean);

				//行为日志上报
				String user = Utils.getTvUserId(context);
				String localIp = Utils.getPhoneIp(context);
				try {
					Api.postUserBehaviors(context, adsBean.getMsg_id() + "", user, localIp, "10", adsBean.getBusi_id());
				} catch (Exception ex) {
					Log.i("iptv", "postUserBehaviors error");
//			ex.printStackTrace();
				}
			}
		});

		new Thread(new Runnable() {
			@Override
			public void run() {

				while (adsBean.getShow_time() > 0) {
					new Handler(Looper.getMainLooper()).post(new Runnable() {
						@Override
						public void run() {
							counterView.setText("" + adsBean.getShow_time());
							adsBean.setShow_time(adsBean.getShow_time()-1);
							mAdsCounter = adsBean.getShow_time();
						}
					});
					try {
						Thread.sleep(1000);
					} catch (InterruptedException e) {
						e.printStackTrace();
					}
				}

				hideAdsDialog(context, adsBean);

			}
		}).start();

	}

	protected void showAdsTemplate(final AdsBean adsBean) {

		Callback callback = new Callback() {
			@Override
			public void onStart(Object... o) {

			}

			@Override
			public void onProgress(Object... o) {

			}

			@Override
			public void onFinish(Object... o) {
				final String pathOnSdcard = o[0].toString();

				new Handler(Looper.getMainLooper()).post(new Runnable() {
					@Override
					public void run() {

						if (mAdsLayerView!=null && mAdsLayerView.getVisibility()==View.VISIBLE && mAdsCounter!=0) {
							return;
						}

						if (Contants.isInMangoLiving) {
							// 在芒果tv直播中，不弹窗
							PushMsgStack.removeMessage(TcpService.this, adsBean);
							return;
						}

						if (mAdsCounter==0 && mAdsLayerView!=null) {
							mAdsLayerView.setVisibility(View.GONE);
						}

						//播放完后从缓存中删除
				        PushMsgStack.removeMessage(TcpService.this, adsBean);
				 		showAdsDialog(adsBean, pathOnSdcard);
					}
				});

				//播放完后从缓存中删除
//				PushMsgStack.removeMessage(TcpService.this, adsBean);

				//下轮播放时间间隔

				final AdsBean nextAlarmMessage = PushMsgStack.notifyAlarmMessage(TcpService.this, Contants.DURATION_TOAST_MESSAGE);

				if (nextAlarmMessage != null) {
					long execTime = nextAlarmMessage.getExce_starttime()*1000;
					long now = System.currentTimeMillis();
					long gap = execTime-now;
					new Handler(Looper.getMainLooper()).postDelayed(new Runnable() {
						@Override
						public void run() {

//							Utils.showToast(getBaseContext(), "next start = " + nextAlarmMessage.getMsg_id());
							//定时启动消息
							showAdsTemplate(nextAlarmMessage);

						}
					}, gap);
				}

				//唤起下一轮播放
				new Handler(Looper.getMainLooper()).postDelayed(new Runnable() {
					@Override
					public void run() {
						AdsBean bean = PushMsgStack.getTopMessage(TcpService.this);
						if (bean != null) {
							showAdsTemplate(bean);
						}
					}
				}, Contants.DURATION_TOAST_MESSAGE);
			}

			@Override
			public void onFail(Object... o) {

			}
		};

		if (adsBean.getFile_url() == null) {
			PushMsgStack.removeMessage(TcpService.this, adsBean);
		} else {
			DownloadManager.dl(callback, adsBean.getFile_url());
		}

	}

	protected void sendAppInitPacket() {

		String user = Utils.getTvUserId(this);
		String localIp = Utils.getPhoneIp(this);

		String initData = PacketManager.getAppInitData(user, localIp);
		sendTcpPacket(initData);

		Log.i("iptv", "send app init data packet = " + initData);

		sendHeartBeatPacket();
	}

	/**
	 * keep alive
	 */
	public void sendHeartBeatPacket() {
		final String pingData = PacketManager.getPingData();

		Log.i("iptv", "send ping packet = " + pingData);
		sendTcpPacket(pingData);

		Log.i("iptv", "wait ping times = " + Contants.DURATION_PING);

		long lastPing = Contants.LAST_PING_TIMESTAMP;
		long current = System.currentTimeMillis();
		long duration = current - lastPing;

		if (duration > Contants.DURATION_PING  * 3) {
//            Log.i("iptv", "超过3倍心跳时间未收到反馈数据包，采取重连策略");
			Contants.LAST_PING_TIMESTAMP = System.currentTimeMillis();
			reconn();

		}

		//Fixme
//		Looper.prepare();
//		new Handler().postDelayed(new Runnable() {
//			@Override
//			public void run() {
//				Log.i("iptv", "send ping again ");
//				sendHeartBeatPacket();
//			}
//		}, Contants.DURATION_PING);




//		Looper.prepare();
//		Contants.DURATION_PING = 10 * 1000;

		new Handler(Looper.getMainLooper()).postDelayed(new Runnable() {
			@Override
			public void run() {
				Log.i("iptv", "send ping again ");

				new Thread(new Runnable() {
					@Override
					public void run() {
						sendHeartBeatPacket();
					}
				}).start();
//				sendHeartBeatPacket();
			}
		}, Contants.DURATION_PING);

		Log.i("iptv", "ping again ");
	}

	protected void reconn() {
		if(socket== null && !socket.isClosed()) {
			try {
				socket.close();
			} catch (IOException e) {
				e.printStackTrace();
			}
		}

		tcpChannelIsReady = false;

		new Handler(Looper.getMainLooper()).postDelayed(new Runnable() {
			public void run(){

				startTcpDeamon();

			}
		}, 3000);
	}

	@Override
	protected void onHandleIntent(Intent intent) {

		//更新服务器端配置文件， 替换本地配置参数
        getServerConfigure();//TODO remove it in release version

		MockReceiver receiver = new MockReceiver();
		IntentFilter filter = new IntentFilter();
		filter.addAction("com.iptv.ads");
		getApplication().registerReceiver(receiver, filter);

		startTcpDeamon();
		registerBroadcastReceiver();

	}

	/**
	 * Mock to show ADS from Test Activity
	 */
	class MockReceiver extends BroadcastReceiver {

		@Override
		public void onReceive(Context context, Intent intent) {
			//Toast.makeText(context, intent.getAction(), Toast.LENGTH_SHORT).show();

			mockMessages(context);

//            Intent adsIntent = new Intent();
//            adsIntent.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK);
//            adsIntent.setClass(context, AdsDialogActivity.class);
//            startActivity(adsIntent);

		}
	}

	protected void mockMessages(Context context) {

		AdsBean ads2 = new AdsBean();
		ads2.setModel_id(2);
		ads2.setMsg_id(2);
		ads2.setSpecial_type(AdsBean.ACTION_WEBVIEW);
		ads2.setSpecial_url("http://www.baidu.com");
		ads2.setDown_type(AdsBean.DOWNLOAD_FROM_SERVER);
		ads2.setDown_url("http://ftp-apk.pconline.com.cn/80c6e0508dba2e354041a209265c10b3/pub/download/201010/pconline1482411644018.apk");
		ads2.setShow_time(6);
		ads2.setFile_type(AdsBean.FILE_GIF);
		ads2.setFile_url("http://images.17173.com/2014/news/2014/03/10/g0310if03.gif");
//		ads2.setPriority_level(2);
		try {
			PushMsgStack.putMessage(context, ads2);
		} catch (Exception e) {
			e.printStackTrace();
		}

		AdsBean bean = PushMsgStack.getTopMessage(context);
		if (bean != null) {
			showAdsTemplate(bean);
		}
		//"special_type":1,"show_time":6,"file_type":1,"msg_id":1,"special_url":"http://127.0.0.1","priority_level":2
	}

}
