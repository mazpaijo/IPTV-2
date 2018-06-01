package com.iptv.hn.service;

import android.app.IntentService;
import android.content.BroadcastReceiver;
import android.content.Intent;
import android.content.Context;
import android.content.IntentFilter;
import android.graphics.Bitmap;
import android.graphics.BitmapFactory;
import android.os.Handler;
import android.os.Looper;
import android.text.Html;
import android.util.Log;
import android.view.Gravity;
import android.view.LayoutInflater;
import android.view.View;
import android.view.WindowManager;
import android.widget.TextView;

import com.iptv.hn.AdsView;
import com.iptv.hn.Contants;
import com.iptv.hn.PacketManager;
import com.iptv.hn.entity.AdsBean;
import com.iptv.hn.entity.PushMsgStack;
import com.iptv.hn.utility.AdsKeyEventHandler;
import com.iptv.hn.utility.Api;
import com.iptv.hn.utility.Callback;
import com.iptv.hn.utility.DownloadManager;
import com.iptv.hn.R;
import com.iptv.hn.utility.JsonUtil;
import com.iptv.hn.utility.PfUtil;
import com.iptv.hn.utility.Utils;

import org.json.JSONException;
import org.json.JSONObject;

import java.io.IOException;
import java.net.DatagramSocket;
import java.net.InetSocketAddress;
import java.net.SocketAddress;
import java.nio.ByteBuffer;
import java.nio.channels.ClosedChannelException;
import java.nio.channels.DatagramChannel;
import java.nio.channels.SelectionKey;
import java.nio.channels.Selector;
import java.util.Iterator;
import java.util.List;
import java.util.Set;

import pl.droidsonroids.gif.GifDrawable;
import pl.droidsonroids.gif.GifImageView;

/**
 * UDP vesion
 */
public class BootService extends IntentService {

    protected View mAdsLayerView;
    protected int mAdsCounter;

    private Thread initUdpChanelThread;

    private DatagramChannel udpChannel;
    private Selector selector;

    private UdpDataReceiver mReceiver;

    /**
     * 接收从其他地方广播过来的数据， 发送UDP数据包
     * @author ligao
     *
     */
    private class UdpDataReceiver extends BroadcastReceiver {

        @Override
        public void onReceive(Context context, Intent intent) {

//            String datagram = intent.getStringExtra("data");
//            String ip = intent.getStringExtra("ip");
//            int port = intent.getIntExtra("port", 10000);
//
//            sendUdpPacket(datagram, ip, port);

        }

    }

    public BootService() {
        super("BootService");
    }

    @Override
    protected void onHandleIntent(Intent intent) {

    }

    //开机自启测试
    protected void startUpTest() {
        new Handler(Looper.getMainLooper()).postDelayed(new Runnable() {
            @Override
            public void run() {
                AdsBean bean = PushMsgStack.getTopMessage(BootService.this);
                if (bean != null) {
                    showAdsTemplate(bean);
                }
            }
        }, 1000*10);
    }

    @Override
    public int onStartCommand(Intent intent, int flags, int startId) {


        //更新服务器端配置文件， 替换本地配置参数
//        getServerConfigure();//TODO remove it in release version
        MockReceiver receiver = new MockReceiver();
        IntentFilter filter = new IntentFilter();
        filter.addAction("com.iptv.ads");
        getApplication().registerReceiver(receiver, filter);

        try {
            udpChannel = DatagramChannel.open();
            udpChannel.configureBlocking(false);

            DatagramSocket socket = udpChannel.socket();
            socket.setReuseAddress(true);

            //TODO 这行代码会导致获取从机列表， 发送 30#07 命令收不到反馈
//            socket.bind(new InetSocketAddress(30303));

            //udpChannel.connect(new InetSocketAddress(packet.getAddress(), port));

            selector = Selector.open();
        } catch (IOException e) {
            e.printStackTrace();
        }


        InitUdpChanel();


        // TODO Auto-generated method stub
        mReceiver = new UdpDataReceiver();
        IntentFilter filter2 = new IntentFilter();
//        filter.addAction(Config.BROADCAST_DATAGRAM_SEND);
        registerReceiver(mReceiver, filter2);


        //TODO 模拟消息测试service弹窗
//        mockMessages(this);


        //启动后就检查是否有缓存的推送消息可以显示
//        AdsBean bean = PushMsgStack.getTopMessage(this);
//        if (bean != null) {
//            showAdsTemplate(bean);
//        }

        return super.onStartCommand(intent, flags, startId);
    }



    /**
     * 检查apk版本
     */
    protected void getApkVersion() {

    }

    protected void refreshAdsShowtime() {
         if (mAdsLayerView == null) {
             return;
         }

         if (mAdsCounter == 0) {
             WindowManager wm = (WindowManager)getApplicationContext().getSystemService(WINDOW_SERVICE);
             wm.removeViewImmediate(mAdsLayerView);
             return;
         }

         mAdsCounter--;
         GifImageView gifView = (GifImageView) mAdsLayerView.findViewById(R.id.gif);
         TextView counterView = (TextView) mAdsLayerView.findViewById(R.id.counter);
         counterView.setText(mAdsCounter);
         counterView.postInvalidate();
    }

    protected void showAdsDialog(final AdsBean adsBean, String localPath) {
        final WindowManager wm = (WindowManager)getSystemService(WINDOW_SERVICE);
        WindowManager.LayoutParams params = new WindowManager.LayoutParams(WindowManager.LayoutParams.WRAP_CONTENT, WindowManager.LayoutParams.WRAP_CONTENT);

//        params.type = WindowManager.LayoutParams.TYPE_SYSTEM_ALERT | WindowManager.LayoutParams.TYPE_SYSTEM_OVERLAY;
        params.type = WindowManager.LayoutParams.TYPE_SYSTEM_ALERT | WindowManager.LayoutParams.TYPE_SYSTEM_OVERLAY;
        params.flags = WindowManager.LayoutParams.FLAG_NOT_TOUCH_MODAL | WindowManager.LayoutParams.FLAG_ALT_FOCUSABLE_IM;
//        params.flags =  WindowManager.LayoutParams.FLAG_ALT_FOCUSABLE_IM;

        params.width = WindowManager.LayoutParams.WRAP_CONTENT;
        params.height = WindowManager.LayoutParams.WRAP_CONTENT;
        params.alpha = 80;

        params.gravity = Gravity.BOTTOM|Gravity.RIGHT;
        //以屏幕左上角为原点，设置x、y初始值
//        params.gravity = Gravity.NO_GRAVITY;
        params.x = 0;
        params.y = 0;
        params.windowAnimations = android.R.style.Animation_Translucent; //平移
//        params.windowAnimations = android.R.style.Animation_InputMethod;

        //       params.windowAnimations = R.anim.j_anim_enter_bottom;

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
                new Handler(Looper.getMainLooper()).post(new Runnable() {
                    @Override
                    public void run() {

                        if (mAdsLayerView.getWindowToken() != null) {
                            WindowManager windowManager = (WindowManager) getSystemService(Context.WINDOW_SERVICE);
                            windowManager.removeViewImmediate(mAdsLayerView);
                        }

                    }
                });
            }

            @Override
            public void onFail(Object... o) {
                new Handler(Looper.getMainLooper()).post(new Runnable() {
                    @Override
                    public void run() {

                        if (mAdsLayerView.getWindowToken() != null) {
                            WindowManager windowManager = (WindowManager) getSystemService(Context.WINDOW_SERVICE);
                            windowManager.removeViewImmediate(mAdsLayerView);
                        }

                    }
                });
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
                new Handler(Looper.getMainLooper()).post(new Runnable() {
                    @Override
                    public void run() {

                        if (mAdsLayerView.getWindowToken() != null) {
                            WindowManager windowManager = (WindowManager) getSystemService(Context.WINDOW_SERVICE);
                            windowManager.removeViewImmediate(mAdsLayerView);
                        }

                    }
                });
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
                        }
                    });
                    try {
                        Thread.sleep(1000);
                    } catch (InterruptedException e) {
                        e.printStackTrace();
                    }
                }

                new Handler(Looper.getMainLooper()).post(new Runnable() {
                    @Override
                    public void run() {

                        if (mAdsLayerView.getWindowToken() != null) {
                            WindowManager windowManager = (WindowManager) getSystemService(Context.WINDOW_SERVICE);
                            windowManager.removeViewImmediate(mAdsLayerView);
                        }

                    }
                });


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
                        showAdsDialog(adsBean, pathOnSdcard);
                    }
                });

                //播放完后从缓存中删除
                PushMsgStack.removeMessage(BootService.this, adsBean);

                //下轮播放时间间隔
                long duration = 1000*1*30;

                final AdsBean nextAlarmMessage = PushMsgStack.notifyAlarmMessage(BootService.this, duration);
                if (nextAlarmMessage != null) {
                    long execTime = nextAlarmMessage.getExce_starttime()*1000;
                    long now = System.currentTimeMillis();
                    long gap = execTime-now;
                    new Handler(Looper.getMainLooper()).postDelayed(new Runnable() {
                        @Override
                        public void run() {
                             //定时启动消息
                             showAdsTemplate(nextAlarmMessage);

                        }
                    }, gap);
                }

                //唤起下一轮播放
                new Handler(Looper.getMainLooper()).postDelayed(new Runnable() {
                    @Override
                    public void run() {
                        AdsBean bean = PushMsgStack.getTopMessage(BootService.this);
                        if (bean != null) {
                            showAdsTemplate(bean);
                        }
                    }
                }, duration);
            }

            @Override
            public void onFail(Object... o) {

            }
        };

        if (adsBean.getFile_url() == null) {
            PushMsgStack.removeMessage(BootService.this, adsBean);
        } else {
            DownloadManager.dl(callback, adsBean.getFile_url());
        }


//         String path = "/storage/emulated/0/593516542b79f80aeba007540fa63857";
//        showAdsDialog(adsBean, path);

//        gifView.setImageResource(R.mipmap.bitmap);

//        TextView tv = new TextView(this);
//        tv.setText("hello world");
//        tv.setOnKeyListener(new View.OnKeyListener() {
//            @Override
//            public boolean onKey(View v, int keyCode, KeyEvent event) {
//                if(keyCode == KeyEvent.KEYCODE_9) {
//                    Toast.makeText(context, "9", Toast.LENGTH_SHORT).show();
//                } else if(keyCode == KeyEvent.KEYCODE_0) {
//                    Toast.makeText(context, "0", Toast.LENGTH_SHORT).show();
//                } else if(keyCode == KeyEvent.KEYCODE_BACK) {
//                    Toast.makeText(context, "back", Toast.LENGTH_SHORT).show();
//                }
//                return false;
//            }
//        });
//
//        wm.addView(tv, params);
    }

    @Override
    public void onDestroy() {

//        unregisterReceiver(mReceiver);
//
//        if(selector != null) {
//            try {
//                selector.close();
//            } catch (IOException e) {
//                e.printStackTrace();
//            }
//        }
//        if(udpChannel != null) {
//            try {
//                udpChannel.close();
//            } catch (IOException e) {
//                e.printStackTrace();
//            }
//        }
//
//        initUdpChanelThread = null;
//
//        selector = null;
//        udpChannel = null;

        super.onDestroy();
    }

    protected void sendAppInitPacket() {
        String user = Utils.getTvUserId(this);
        String localIp = Utils.getPhoneIp(this);
//        Utils.showToast(this, "user = " + user);
        String initData = PacketManager.getAppInitData(user, localIp);
        sendUdpPacket(initData, Contants.IPTV_UDP_IP, Contants.IPTV_UDP_PORT);

        sendHeartBeatPacket();
    }

    /**
     * keep alive
     */
    public void sendHeartBeatPacket() {
        String pingData = PacketManager.getPingData();
        sendUdpPacket(pingData, Contants.IPTV_UDP_IP, Contants.IPTV_UDP_PORT);

        new Handler(Looper.getMainLooper()).postDelayed(new Runnable() {
            @Override
            public void run() {
                sendHeartBeatPacket();
            }
        }, 1000*10);
    }

    /**建立UDP通道*/
    private void InitUdpChanel() {
        initUdpChanelThread = 	new Thread(new Runnable() {

            @Override
            public void run() {
                try {

                    if (udpChannel==null || selector==null) {
                        return;
                    }

                    //udpChannel.connect(new java.net.InetSocketAddress(Constans.SERVICE_IP, Constans.SERVICE_PORT));

                    udpChannel.register(selector, SelectionKey.OP_READ, SelectionKey.OP_WRITE);

                    try {

                        if (udpChannel==null || selector==null) {
                            System.out.println("=========null========");
                        }

                        sendAppInitPacket();

                        while (udpChannel.isOpen() && selector.select() > 0) {
                            Set<SelectionKey> readyKeys = selector.selectedKeys();
                            Iterator<SelectionKey> it = readyKeys.iterator();
                            while (it.hasNext()) {
                                SelectionKey key = null;
                                try {
                                    key = (SelectionKey) it.next();
                                    it.remove();
                                    if (key.isReadable()) {
                                        receive(key);
                                    }  else if (key.isAcceptable()) {
                                        receive(key);
                                    }
                                } catch (IOException e) {
                                    e.printStackTrace();
                                    try {
                                        if (key != null) {
                                            key.cancel();
                                            key.channel().close();
                                        }
                                    } catch (IOException o) {
                                        o.printStackTrace();
                                    }
                                }
                            }
                            readyKeys.clear();
                        }

                        selector.close();
                        udpChannel.close();

                        stopSelf();
                    } catch (IOException e) {
                        e.printStackTrace();
                    }

                } catch (IOException e) {
                    e.printStackTrace();
                } catch (Exception e) {
                    e.printStackTrace();
                }
            }
        });

        initUdpChanelThread.start();
    }


    /**接收数据包*/
    private void receive(SelectionKey key) throws IOException {
        DatagramChannel datagramChannel = (DatagramChannel) key.channel();
        ByteBuffer bb = ByteBuffer.allocate(3000);
        SocketAddress sd = null;
        if (datagramChannel.isConnected()) {
            datagramChannel.read(bb);
        } else {
            sd = datagramChannel.receive(bb);
        }
        bb.flip();

//        datagramChannel.read(bb);
        //收到数据
        //
        byte[] array = new byte[bb.limit()];
        System.arraycopy(bb.array(), 0, array, 0, bb.limit());
        String data = new String(array, "utf-8");
        Log.e("iptv",">>>>>>>>>> receive " + data);

        try {
            JSONObject json = new JSONObject(data);

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





        //TODO
        //有新消息立即显示？
//            showAdsTemplate(adsBean);
        AdsBean bean = PushMsgStack.getTopMessage(this);
        if (bean != null) {
            showAdsTemplate(bean);
        }

    }

    /**
     * 推送消息回执
     */
    protected void responsePush(AdsBean adsBean) {

        String user = Utils.getTvUserId(this);
        String localIp = Utils.getPhoneIp(this);

        String responseData = PacketManager.getResponseUdpData(adsBean.getMsg_id(), user, localIp);
        sendUdpPacket(responseData, Contants.IPTV_UDP_IP, Contants.IPTV_UDP_PORT);

        Log.i("iptv", "responseData =" + responseData);

        //行为日志上报
        Api.postUserBehaviors(BootService.this, adsBean.getMsg_id()+"", user, localIp, "0", adsBean.getBusi_id());
    }

    /**发送数据包*/
    private void sendUdpPacket(final String data, final String ip, final int port) {
        if(udpChannel == null) {
            return;
        }

        new Thread() {
            public void run() {
                try {

                    int sent = udpChannel.send(ByteBuffer.wrap(data.getBytes("UTF-8")),
                            new InetSocketAddress(ip, port));

                    Log.i("iptv", "send udp " + sent + "bytes");
                } catch (IOException e) {
                    e.printStackTrace();
                }
            }
        }.start();

    }


    protected void mockMessages(Context context) {
        //http://images.17173.com/2014/news/2014/03/10/g0310if03.gif

//        AdsBean ads = new AdsBean();
//        ads.setModel_id(2);
//        ads.setMsg_id(1);
////            ads.setSpecial_type(AdsBean.ACTION_APP);
////            ads.setSpecial_url("com.haodou.recipe");
////            ads.setSpecial_url("com.netease.railwayticket");
////            ads.setDown_type(AdsBean.DOWNLOAD_FROM_STORE);
//        ads.setSpecial_type(AdsBean.ACTION_WEBVIEW);
//        ads.setSpecial_url("httpa://www.baidu.com");
//
//        ads.setDown_type(AdsBean.DOWNLOAD_FROM_SERVER);
//        ads.setDown_url("http://ftp-apk.pconline.com.cn/80c6e0508dba2e354041a209265c10b3/pub/download/201010/pconline1482411644018.apk");
//http://gdown.baidu.com/data/wisegame/be8c6ca5302a288e/haodou_112.apk
//        ads.setShow_time(6);
//        ads.setFile_type(AdsBean.FILE_GIF);
////            ads.setFile_url("http://images.17173.com/2014/news/2014/03/10/g0310if03.gif");
//        ads.setFile_url("http://img5.duitang.com/uploads/item/201412/08/20141208143150_2hPWW.gif");
//        ads.setPriority_level(1);
//        try {
//            PushMsgStack.putMessage(context, ads);
//        } catch (Exception e) {
//            e.printStackTrace();
//        }


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
        ads2.setPriority_level(2);
        try {
            PushMsgStack.putMessage(context, ads2);
        } catch (Exception e) {
            e.printStackTrace();
        }


//        AdsBean ads3 = new AdsBean();
//        ads3.setModel_id(2);
//        ads3.setMsg_id(3);
//        ads3.setSpecial_type(AdsBean.ACTION_APP);
//        ads3.setSpecial_url("com.haodou.recipe");
//        ads3.setDown_type(AdsBean.DOWNLOAD_FROM_STORE);
//        ads3.setShow_time(6);
//        ads3.setFile_type(AdsBean.FILE_GIF);
//        ads3.setFile_url("http://images.17173.com/2014/news/2014/03/10/g0310if03.gif");
//        ads3.setPriority_level(3);
//        try {
//            PushMsgStack.putMessage(context, ads3);
//        } catch (Exception e) {
//            e.printStackTrace();
//        }

//            showAdsTemplate(ads);
        AdsBean bean = PushMsgStack.getTopMessage(context);
        if (bean != null) {
            showAdsTemplate(bean);
        }
        //"special_type":1,"show_time":6,"file_type":1,"msg_id":1,"special_url":"http://127.0.0.1","priority_level":2
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
}
