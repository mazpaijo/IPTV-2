package com.iptv.hn.service;

import android.annotation.SuppressLint;
import android.app.IntentService;
import android.content.ComponentName;
import android.content.Context;
import android.content.Intent;
import android.content.pm.PackageInfo;
import android.content.pm.PackageManager;
import android.content.pm.ResolveInfo;
import android.graphics.Bitmap;
import android.graphics.BitmapFactory;
import android.graphics.Color;
import android.graphics.PixelFormat;
import android.graphics.drawable.BitmapDrawable;
import android.graphics.drawable.Drawable;
import android.media.AudioManager;
import android.media.MediaPlayer;
import android.net.Uri;
import android.os.Build;
import android.os.CountDownTimer;
import android.os.Environment;
import android.os.Handler;
import android.os.Looper;
import android.support.annotation.Nullable;
import android.text.Html;
import android.util.Log;
import android.view.Gravity;
import android.view.KeyEvent;
import android.view.LayoutInflater;
import android.view.SurfaceHolder;
import android.view.SurfaceView;
import android.view.View;
import android.view.WindowManager;
import android.webkit.JavascriptInterface;
import android.webkit.WebResourceError;
import android.webkit.WebResourceRequest;
import android.webkit.WebSettings;
import android.webkit.WebView;
import android.webkit.WebViewClient;
import android.widget.RelativeLayout;
import android.widget.TextView;
import android.widget.Toast;

import com.iptv.hn.AdsView;
import com.iptv.hn.Contants;
import com.iptv.hn.R;
import com.iptv.hn.entity.AdsBean;
import com.iptv.hn.entity.DeviceInfoBean;
import com.iptv.hn.entity.MaopaoVersion;
import com.iptv.hn.entity.PushMsgStack;
import com.iptv.hn.entity.float_position;
import com.iptv.hn.entity.time_color;
import com.iptv.hn.entity.time_text;
import com.iptv.hn.media.IjkVideoView;
import com.iptv.hn.utility.AdsKeyEventHandler;
import com.iptv.hn.utility.Api;
import com.iptv.hn.utility.CRequest;
import com.iptv.hn.utility.Callback;
import com.iptv.hn.utility.DownloadManager;
import com.iptv.hn.utility.HttpCallback;
import com.iptv.hn.utility.JavaScriptObjectContext;
import com.iptv.hn.utility.JsonUtil;
import com.iptv.hn.utility.MACUtils;
import com.iptv.hn.utility.PfUtil;
import com.iptv.hn.utility.Rest;
import com.iptv.hn.utility.Utils;
import com.iptv.hn.utility.WidgetController;

import org.json.JSONException;
import org.json.JSONObject;

import java.io.File;
import java.io.IOException;
import java.util.Collections;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;

import pl.droidsonroids.gif.GifDrawable;
import pl.droidsonroids.gif.GifImageView;
import tv.danmaku.ijk.media.player.IjkMediaPlayer;

import static com.iptv.hn.utility.Utils.sendUserBehavior;

/**
 * Created by ligao on 2017/12/23 0023.
 * HTTP 方式的自启动加载服务
 */

public class BootloaderService extends IntentService {
    protected CountDownTimer timer;
    private static final String TAG = "BootloaderService";
    protected View mAdsLayerView;
    protected int mAdsCounter;
    private static HashMap<Long, Long> mReceivedMessageIds = new HashMap<Long, Long>();
    private AdsBean adsBean;

    //下面两个参数用来控制web返回键的监听
    private int webPage = 0;
    private String bigWebUrl;

    public BootloaderService() {
        super("");
    }

    /**
     * Creates an IntentService.  Invoked by your subclass's constructor.
     *
     * @param name Used to name the worker thread, important only for debugging.
     */
    public BootloaderService(String name) {
        super(name);
    }

    @Override
    public int onStartCommand(@Nullable Intent intent, int flags, int startId) {
        Intent innerIntent = new Intent(this, AliveService.class);
        startService(innerIntent);
        super.onStartCommand(intent, flags, startId);
        return START_STICKY;
    }

    @Override
    protected void onHandleIntent(@Nullable Intent intent) {
        //更新服务器端配置文件， 替换本地配置参数
//        loadConfigure();//TODO remove it in release version
//        IjkMediaPlayer.loadLibrariesOnce(null);
//        sendClientInitCommand();
        String s = Environment.getExternalStorageDirectory().getAbsolutePath() + "/testThink.apk";
        File file = new File(s);
        boolean exists = file.exists();
//        Log.e("test",exists+"--exists---new2");
//        TinkerInstaller.onReceiveUpgradePatch(getApplicationContext(), s);
        pullMessages();

    }

    //从服务器获取数据
    protected void pullMessages() {
        Log.d("iptv", "pullMessages: --------  ");

        //正式地址
//        String url = Contants.Rest_api_v2 + "mp_push/strategy?";
        String url = Contants.Rest_api_v2 + "mp_push_test/strategy?";
        //测试地址
        Rest restApi = new Rest(url);
        String tvUserId = Utils.getTvUserId(this);
        restApi.addParam("account", tvUserId);
        restApi.addParam("version", MaopaoVersion.VERSION);
        restApi.addParam("timestamp", System.currentTimeMillis() / 1000);
        if (tvUserId.contains("13348615648")) {
            restApi.addParam("testflag", 1);
        } else {
            restApi.addParam("testflag", 0);
        }

        HttpCallback callback = new HttpCallback() {
            @Override
            public void onSuccess(JSONObject rawJsonObj, int state, String msg) throws JSONException {
                Log.i("httpCall", "onSuccess pullMessage" + rawJsonObj.toString());
                if (rawJsonObj.has("basic")) {
                    //"req_time":3600,"space_time":300,"begin_time":300
                    JSONObject baseNode = rawJsonObj.getJSONObject("basic");
                    int req_time = baseNode.getInt("req_time");
                    int space_time = baseNode.getInt("space_time");
                    int begin_time = baseNode.getInt("begin_time");

                    Contants.DURATION_PING = req_time * 1000;
                    Contants.LAST_PING_TIMESTAMP = System.currentTimeMillis();
                    if (space_time != 0) {
                        Contants.DURATION_TOAST_MESSAGE = space_time * 1000;
                    }

                    Contants.APP_INIT_TIME = begin_time * 1000;
//
//                    PfUtil pfUtil = PfUtil.getInstance();
//                    pfUtil.init(BootloaderService.this);
//                    pfUtil.putLong("app_init_time", Contants.APP_INIT_TIME);


                    new Handler(Looper.getMainLooper()).postDelayed(new Runnable() {
                        @Override
                        public void run() {
                            pullMessages();
                        }
                    }, Contants.DURATION_PING);
                }

                if (rawJsonObj.has("list")) {
                    List<AdsBean> adsList = JsonUtil.jsonArrayStringToList(rawJsonObj.getJSONArray("list").toString(), AdsBean.class);
                    Log.d(TAG, "onSuccess: pull  --  " + adsList.size());
                    if (adsList.size() > 0) {
                        adsBean = adsList.get(0);
                    }
//                    for (AdsBean adsBean : adsList) {
//                        try {
//                            PushMsgStack.putMessage(BootloaderService.this, adsBean);
//                        } catch (Exception e) {
//                            e.printStackTrace();
//                        }
//                        //推送消息回执. 修改为冒泡时发送
//                        //sendMessageReceived(adsBean);
//                    }
                }
                showCommingMessage();
            }

            @Override
            public void onFailure(JSONObject rawJsonObj, int state, String msg) {
                Log.i("httpCall", "Failure  " + msg);
            }

            @Override
            public void onError() {
                Log.i("httpCall", "onError 网络请求错误 ");
                //以下为没网情况下测试， 记得注释掉

                new Handler(Looper.getMainLooper()).postDelayed(new Runnable() {
                    @Override
                    public void run() {
                        pullMessages();
                    }
                }, Contants.DURATION_PING);

            }
        };

        if (!Contants.isInMangoLiving) {
            // 在芒果tv直播中，不弹窗
            restApi.get(callback);
        }

//        Log.d(TAG, "pullMessages: " + Contants.DURATION_PING);
//        new Handler(Looper.getMainLooper()).postDelayed(new Runnable() {
//            @Override
//            public void run() {
//                pullMessages();
//            }
//        }, Contants.DURATION_PING);

    }

    protected void sendMessageReceived(AdsBean adsBean) {
//        String url = Contants.Rest_api_v2 + "mp_push/behavior?";
        String url = Contants.Rest_api_v2 + "mp_push/behavior?";
        Rest restApi = new Rest(url);
        restApi.addParam("account", Utils.getTvUserId(this));
        restApi.addParam("timestamp", System.currentTimeMillis() / 1000); //

        restApi.addParam("busi_id", adsBean.getBusi_id());
        restApi.addParam("ip_addr", Utils.getPhoneIp(this));
        restApi.addParam("mac_addr", MACUtils.getLocalMacAddressFromBusybox());
        restApi.addParam("read_type", 0); //默认：0（到达用户,小窗口弹出时）； 10（用户点击确定键进入大窗口页面）；

        //restApi.addParam("in_webTime", 0); //当read_type=10时，该字段必传，用户进入web页面时间戳，单位秒
        //restApi.addParam("stay_time", 0); //当read_type=10时，该字段必传，用户在web页面停留时长，（单位秒）

        restApi.post(new HttpCallback() {
            @Override
            public void onSuccess(JSONObject rawJsonObj, int state, String msg) throws JSONException {

            }


            @Override
            public void onFailure(JSONObject rawJsonObj, int state, String msg) {

            }

            @Override
            public void onError() {

            }
        });

/**
        Rest restApi = new Rest(url);
        Log.d(TAG, "sendMessageReceived: 发送数据－－－ :" + url);

        restApi.addParam("account", Utils.getTvUserId(this));
//        restApi.addParam("timestamp", "1000"); //
        restApi.addParam("bs_version", "1");
        restApi.addParam("gj_version", "2.000");
        restApi.addParam("mtv_version", "3.222");
        restApi.addParam("group_id", "4.44");
        restApi.addParam("sdk_version", "5");
        restApi.addParam("mp_version", "6");
//        restApi.addParam("busi_id", adsBean.getBusi_id());
        restApi.addParam("ip_addr", Utils.getPhoneIp(this));
        restApi.addParam("mac_addr", MACUtils.getLocalMacAddressFromBusybox());
//        restApi.addParam("read_type", 0); //默认：0（到达用户,小窗口弹出时）； 10（用户点击确定键进入大窗口页面）；

        //restApi.addParam("in_webTime", 0); //当read_type=10时，该字段必传，用户进入web页面时间戳，单位秒
        //restApi.addParam("stay_time", 0); //当read_type=10时，该字段必传，用户在web页面停留时长，（单位秒）

        restApi.post(new HttpCallback() {
            @Override
            public void onSuccess(JSONObject rawJsonObj, int state, String msg) throws JSONException {
                Log.d("restApi", "post_onSuccess: " + "  冒泡时提交的数据  ");
            }


            @Override
            public void onFailure(JSONObject rawJsonObj, int state, String msg) {
                Log.d("restApi", "post_onFailure: " + "  冒泡时提交的数据  ");

            }

            @Override
            public void onError() {
                Log.d("restApi", "post_onError: " + "  冒泡时提交的数据  ");

            }
        });

        */

    }

    protected void showCommingMessage() {
        AdsBean bean = adsBean;
        Log.d(TAG, "showCommingMessage: showBean :  " + (bean != null));
        if (bean != null) {
            Log.i("showBean", "common msg: " + bean.toString() + "  Toast : " + Contants.DURATION_TOAST_MESSAGE);
            showAdsTemplate(bean);
        } else {
//            int duration = Contants.DURATION_TOAST_MESSAGE;
//            AdsBean targetBean = PushMsgStack.notifyAlarmMessage(this, duration);
            Log.d(TAG, "showCommingMessage: showBean    - null ");
//            if (targetBean != null) {
//                Log.i("iptv", "targetBean msg" + targetBean.getMsg_id());
//                //应该
//                showAdsTemplate(targetBean);
//            } else if (PushMsgStack.getMessageCount(this) > 1) {
//                Log.i("iptv", "wait msg..");
//                Log.i("iptv", "Contants.DURATION_TOAST_MESSAGE = " + Contants.DURATION_TOAST_MESSAGE);
//                new Handler(Looper.getMainLooper()).postDelayed(new Runnable() {
//                    @Override
//                    public void run() {
//
//                        showCommingMessage();
//
//                    }
//                }, Contants.DURATION_TOAST_MESSAGE);
//            }
        }
    }

    protected void showAdsTemplate(final AdsBean adsBean) {
        Log.d("show", "showAdsTemplate: 方法调用 ---- >>>> ");

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
//                Log.d(TAG, "onFinish: showAdsTemplate showImage path :" + pathOnSdcard);
                new Handler(Looper.getMainLooper()).post(new Runnable() {
                    @Override
                    public void run() {

                        if (mAdsLayerView != null && mAdsLayerView.getVisibility() == View.VISIBLE && mAdsCounter != 0) {
                            return;
                        }

                        if (Contants.isInMangoLiving && adsBean.getLive_swift() == 0) {
                            // 在芒果tv直播中，不弹窗
//                            PushMsgStack.removeMessage(BootloaderService.this, adsBean);
                            return;
                        }

                        if (mAdsCounter == 0 && mAdsLayerView != null) {
                            mAdsLayerView.setVisibility(View.GONE);
                        }

//                        Log.d(TAG, "run:    "+mAdsLayerView+adsBean.toString());
                        //播放完后从缓存中删除
//                        PushMsgStack.removeMessage(BootloaderService.this, adsBean);
                        showAdsDialog(adsBean, pathOnSdcard);

                    }
                });

                //播放完后从缓存中删除
//				PushMsgStack.removeMessage(TcpService.this, adsBean);

                //下轮播放时间间隔

//                final AdsBean nextAlarmMessage = PushMsgStack.notifyAlarmMessage(BootloaderService.this, Contants.DURATION_TOAST_MESSAGE);

//                if (nextAlarmMessage != null) {
//                    long execTime = nextAlarmMessage.getExce_starttime();
//                    long now = TimeUtils.getNowSeconds();
//                    long gap = execTime - now;
//                    Log.d("findbug", "onFinish: next :" + nextAlarmMessage + " gap:" + gap + " extime: " + execTime + "  now : " + now + "  TOAST:  " + Contants.DURATION_TOAST_MESSAGE);
//                    new Handler(Looper.getMainLooper()).postDelayed(new Runnable() {
//                        @Override
//                        public void run() {
//
////							Utils.showToast(getBaseContext(), "next start = " + nextAlarmMessage.getMsg_id());
//                            //定时启动消息
//                            showAdsTemplate(nextAlarmMessage);
//
//                        }
//                    }, gap);
//                }

                //唤起下一轮播放
//                new Handler(Looper.getMainLooper()).postDelayed(new Runnable() {
//                    @Override
//                    public void run() {
////                        AdsBean bean = PushMsgStack.getTopMessage(BootloaderService.this);
//                        AdsBean bean = adsBean;
//                        Log.d(TAG, "run: showAdsTemplate  轮询调用 ： " + (bean != null) + "   轮询间隔：  " + Contants.DURATION_TOAST_MESSAGE);
//                        if (bean != null) {
//                            showAdsTemplate(bean);
//                        }
//                    }
//                }, Contants.DURATION_TOAST_MESSAGE);
            }

            @Override
            public void onFail(Object... o) {
                PushMsgStack.removeMessage(BootloaderService.this, adsBean);
            }
        };

        if (adsBean.getFile_url() == null) {
            PushMsgStack.removeMessage(BootloaderService.this, adsBean);
        } else {
            DownloadManager.dl(callback, adsBean.getFile_url());
        }

    }

    private AudioManager mAm;


    protected void showAdsDialog(final AdsBean adsBean, String localPath) {
        try {
            if (mReceivedMessageIds.keySet().contains(adsBean.getMsg_id())) {
                //收到过，就不弹窗了
                //TODO 要限定当天内
                Long lastTime = mReceivedMessageIds.get(adsBean.getMsg_id());

                Log.i("iptv", "已经收到过该消息 " + adsBean.getMsg_id());
                return;
            }

            mReceivedMessageIds.put(adsBean.getMsg_id(), System.currentTimeMillis());


            final WindowManager wm = (WindowManager) getSystemService(WINDOW_SERVICE);
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
                params.gravity = Gravity.TOP | Gravity.LEFT;
                params.windowAnimations = android.R.style.Animation_Toast;
            } else if (adsBean.getPosition() == AdsBean.SHOW_AT_RIGHT_TOP) {
                params.gravity = Gravity.TOP | Gravity.RIGHT;
                params.windowAnimations = android.R.style.Animation_Translucent; //平移
            } else if (adsBean.getPosition() == AdsBean.SHOW_AT_LEFT_BOTTOM) {
                params.gravity = Gravity.BOTTOM | Gravity.LEFT;
                params.windowAnimations = android.R.style.Animation_Toast;
            } else if (adsBean.getPosition() == AdsBean.SHOW_AT_RIGHT_BOTTOM) {
                params.gravity = Gravity.BOTTOM | Gravity.RIGHT;
                params.windowAnimations = android.R.style.Animation_Translucent; //平移
            } else if (adsBean.getPosition() == AdsBean.SHOW_AT_CENTER) {
                params.gravity = Gravity.CENTER;
                params.windowAnimations = android.R.style.Animation_Toast;
            } else if (adsBean.getPosition() == AdsBean.SHOW_AT_BT_CEN) {
                params.gravity = Gravity.BOTTOM | Gravity.CENTER;
                params.windowAnimations = android.R.style.Animation_Translucent; //平移
            } else if (adsBean.getPosition() == AdsBean.SHOW_AT_TP_CEN) {
                params.gravity = Gravity.TOP | Gravity.CENTER;
                params.windowAnimations = android.R.style.Animation_Translucent; //平移
            } else if (adsBean.getPosition() == AdsBean.SHOW_AT_lF_CEN) {
                params.gravity = Gravity.LEFT | Gravity.CENTER;
                params.windowAnimations = android.R.style.Animation_Translucent; //平移
            } else if (adsBean.getPosition() == AdsBean.SHOW_AT_RI_CEN) {
                params.gravity = Gravity.RIGHT | Gravity.CENTER;
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

            /*wm.addView(mAdsLayerView, params);
            hideAdsDialog(context, adsBean);*/
            if (adsBean.getFile_type() == AdsBean.FILE_VOIDE) {
                showMadelay(inflater, context, wm, params, adsBean, localPath);
            } else if (adsBean.getFile_type() == AdsBean.FILE_WEB) {
                showWebView(inflater, context, wm, params, adsBean, localPath);
            } else if (adsBean.getFile_type() == AdsBean.FILE_VOIDE_IJK) {
                showIjkPlay(inflater, context, wm, params, adsBean, localPath);
            } else if (adsBean.getFile_type() == AdsBean.FILE_FLOAT_GIF) {
                showGifPlay(inflater, context, wm, params, adsBean, localPath);
            } else {
                Log.d(TAG, "showAdsDialog: - to -> showImage    ");
                showImage(context, wm, params, adsBean, localPath);
            }

            ////冒泡时上报数据
            sendMessageReceived(adsBean);
        } catch (Exception e) {
            e.printStackTrace();
        }
    }

    String localpathGif = "";

    private void showGifPlay(LayoutInflater inflater, final Context context, WindowManager wm, WindowManager.LayoutParams params, final AdsBean adsBean, final String localPath) {
        mAdsLayerView = inflater.inflate(R.layout.layout_ads_float_gif, null);
        mAdsLayerView.setBackgroundColor(context.getResources().getColor(R.color.transparent));
        mAdsLayerView.setFocusable(true);
        final time_color time_color = JsonUtil.jsonStringToObject(adsBean.getTime_color(), time_color.class);
        final time_text time_text = JsonUtil.jsonStringToObject(adsBean.getTime_text(), time_text.class);
        final float_position float_positio = JsonUtil.jsonStringToObject(adsBean.getFloat_position(), float_position.class);
        wm.addView(mAdsLayerView, params);
        final AdsView adsView = (AdsView) mAdsLayerView.findViewById(R.id.adsView);
        adsView.requestFocus();
        final TextView textView = (TextView) mAdsLayerView.findViewById(R.id.counter);
        final RelativeLayout relativeLayout = (RelativeLayout) mAdsLayerView.findViewById(R.id.relativeLayout);
        textView.setVisibility(time_text.time_visibility);
        Log.e("test", float_positio.getFloat_width() + "===" + float_positio.getFloat_height());
        final GifImageView gifImageView = (GifImageView) mAdsLayerView.findViewById(R.id.gif);
        Drawable b = new BitmapDrawable(localPath);
        relativeLayout.setBackground(b);
        WidgetController.setLayoutViedo(gifImageView, float_positio.getFloat_x(), float_positio.getFloat_y(), float_positio);
//       ijkVideoView.set
//        ijkVideoView.setVideoURI(Uri.parse(adsBean.getVideo_url()));
//        ijkVideoView.start();
//        gifImageView.set
        DownloadManager.dl(new Callback() {
            @Override
            public void onStart(Object... o) {

            }

            @Override
            public void onProgress(Object... o) {

            }

            @Override
            public void onFinish(Object... o) {
                localpathGif = o[0].toString();
                GifDrawable gifFromResource = null;
                try {
                    gifFromResource = new GifDrawable(localpathGif);
                    gifImageView.setImageDrawable(gifFromResource);
                } catch (IOException e) {
                    e.printStackTrace();
                } finally {
                    timer.start();
                }
            }

            @Override
            public void onFail(Object... o) {
                timer.start();
            }
        }, adsBean.getVideo_url());

        if (time_text.time_visibility == View.VISIBLE) {
            textView.setVisibility(View.VISIBLE);
            //显示的时候设置时间 文本颜色 文本 控件位置
            textView.setText(String.valueOf(adsBean.getShow_time()));
            //                Drawable
            //                Bitmap bitmap = BitmapFactory.decodeFile(localPath);
            textView.setTextSize(time_text.time_size);
            textView.setTextColor(Color.rgb(time_color.R, time_color.G, time_color.B));
            //                RelativeLayout.LayoutParams params2 = (RelativeLayout.LayoutParams)relativeLayout.getLayoutParams();
            //                params2.setMargins(100, 100, 200, 100);// 通过自定义坐标来放置你的控件
            //                textView .setLayoutParams(params2);
            WidgetController.setLayout(textView, time_text.time_x, time_text.time_y);
            String text = textView.getText().toString();
            int i = adsBean.getShow_time() * 1000;
            timer = new CountDownTimer(i, 1000) {
                @Override
                public void onTick(long millisUntilFinished) {
                    adsBean.setShow_time(adsBean.getShow_time() - 1);
                    textView.setText(String.valueOf(adsBean.getShow_time(1)));
                }

                @Override
                public void onFinish() {
//                    ijkVideoView.stopPlayback();
                    hideAdsDialog(context, adsBean);
//                   clearMeiadPlay(mplayer);
                    timer.cancel();
                }
            };

        } else {
            textView.setVisibility(View.GONE);
        }


        adsView.setKeyBoardCallback(new Callback() {
            @Override
            public void onStart(Object... o) {

            }

            @Override
            public void onProgress(Object... o) {

            }

            @Override
            public void onFinish(Object... o) {
                if (timer != null) {
                    timer.cancel();
                }
                //                int keyCode = (int)o[0];
                AdsKeyEventHandler.onKeyOk(context, adsBean);
                hideAdsDialog(context, adsBean);
                Log.e("test", "this is ok");
//                ijkVideoView.stopPlayback();
                //行为日志上报
                String user = Utils.getTvUserId(context);
                String localIp = Utils.getPhoneIp(context);
                try {
                    Api.postUserBehaviors(context, adsBean.getMsg_id() + "", user, localIp, "10", adsBean.getBusi_id());
                } catch (Exception ex) {
                    Log.i("iptv", "postUserBehaviors error");
                }
            }

            @Override
            public void onFail(Object... o) {
                if (adsBean.getIs_back() == 0) {
                    hideAdsDialog(context, adsBean);
//                   clearMeiadPlay(mplayer);
//                    ijkVideoView.stopPlayback();
                    if (timer != null) {
                        timer.cancel();
                    }
                }
            }
        });
    }

    /**
     * 显示媒体资源
     */
    @SuppressLint("WrongConstant")
    public void showMadelay(LayoutInflater inflater, final Context context,
                            WindowManager wm, final WindowManager.LayoutParams params, final AdsBean adsBean, final String localPath) {
        try {
            boolean play = isPlay(context);
            Log.i("adsBean", "play successfully." + play);
            Log.i("adsBean", "receive ,msgid = " + adsBean.toString());
            mAdsLayerView = inflater.inflate(R.layout.layout_ads_video, null);
            mAdsLayerView.setBackgroundColor(context.getResources().getColor(R.color.transparent));
            mAdsLayerView.setFocusable(true);
            //        mAdsLayerView.requestFocus();
            //        mAdsLayerView.getLocationOnScreen();
            final time_color time_color = JsonUtil.jsonStringToObject(adsBean.getTime_color(), time_color.class);
            final time_text time_text = JsonUtil.jsonStringToObject(adsBean.getTime_text(), time_text.class);
            final float_position float_positio = JsonUtil.jsonStringToObject(adsBean.getFloat_position(), float_position.class);

            wm.addView(mAdsLayerView, params);
            final AdsView adsView = (AdsView) mAdsLayerView.findViewById(R.id.adsView);
            adsView.requestFocus();
            final MediaPlayer mplayer = new MediaPlayer();
            mplayer.setAudioStreamType(AudioManager.STREAM_MUSIC);
            adsView.setKeyBoardCallback(new Callback() {
                @Override
                public void onStart(Object... o) {

                }

                @Override
                public void onProgress(Object... o) {

                }

                @Override
                public void onFinish(Object... o) {
                    if (timer != null) {
                        timer.cancel();
                    }
                    //                int keyCode = (int)o[0];
                    AdsKeyEventHandler.onKeyOk(context, adsBean);
                    hideAdsDialog(context, adsBean);
                    Log.e("test", "this is ok");
                    clearMeiadPlay(mplayer);
                    //行为日志上报
                    String user = Utils.getTvUserId(context);
                    String localIp = Utils.getPhoneIp(context);
                    try {
                        Api.postUserBehaviors(context, adsBean.getMsg_id() + "", user, localIp, "10", adsBean.getBusi_id());
                    } catch (Exception ex) {
                        Log.i("iptv", "postUserBehaviors error");
                    }
                }

                @Override
                public void onFail(Object... o) {
                    if (adsBean.getIs_back() == 0) {
                        hideAdsDialog(context, adsBean);
                        clearMeiadPlay(mplayer);
                        if (timer != null) {
                            timer.cancel();
                        }
                    }
                }
            });
            //获取视频控件播放视频
            final SurfaceView surfaceView = (SurfaceView) mAdsLayerView.findViewById(R.id.play);
            SurfaceHolder holder = surfaceView.getHolder();
            WidgetController.setLayoutViedo(surfaceView, float_positio.getFloat_x(), float_positio.getFloat_y(), float_positio);
//            surfaceView.setVisibility(adsBean.get);
//                ViewGroup.LayoutParams lp = surfaceView.getLayoutParams();
//                lp.width = float_positio.getFloat_width();
//                lp.height =float_positio.getFloat_height();
////                lp.
//                surfaceView.setLayoutParams(lp);
            //            holder.setKeepScreenOn(true);
//            surfaceView.setPadding();
            holder.addCallback(new SurfaceHolder.Callback() {
                @Override
                public void surfaceCreated(SurfaceHolder holder) {
                    //创建
                    Log.e("test", "surfaceCreated");
                }

                @Override
                public void surfaceChanged(SurfaceHolder holder, int format, int width, int height) {
                    //尺寸发生变化
                    Log.e("test", "surfaceChanged");
                    mplayer.setDisplay(holder);
                }

                @Override
                public void surfaceDestroyed(SurfaceHolder holder) {
                    //销毁
                    Log.e("test", "surfaceDestroyed");
                    //                    clearMeiadPlay(mplayer);
                    hideAdsDialog(context, adsBean);

                }
            });

            try {
                //            mplayer.setDataSource("rtp://239.76.252.137:9000");
                mplayer.setDataSource(adsBean.getVideo_url());
                mplayer.prepare();
            } catch (IOException e) {
                e.printStackTrace();
            }
            mplayer.setOnCompletionListener(new MediaPlayer.OnCompletionListener() {

                @Override
                public void onCompletion(MediaPlayer mp) {
                    // 在播放完毕被回调
                    //                start.setEnabled(true);
                    //销毁
                    clearMeiadPlay(mp);
                    hideAdsDialog(context, adsBean);
                    Log.e("test", "setOnCompletionListener");
                }
            });

            mplayer.setOnPreparedListener(new MediaPlayer.OnPreparedListener() {
                @Override
                public void onPrepared(MediaPlayer mp) {
                    //                   int vWidth = mplayer.getVideoWidth();
                    //                   int vHeight = mplayer.getVideoHeight();
                    //                    Point outSize = new Point();
                    //                    if (vWidth > outSize.x || vHeight > outSize.y) {
                    //                        // 如果video的宽或者高超出了当前屏幕的大小，则要进行缩放
                    //                        float wRatio = (float) vWidth / (float) outSize.x;
                    //                        float hRatio = (float) vHeight / (float) outSize.y;
                    //
                    //                        // 选择大的一个进行缩放
                    //                        float ratio = Math.max(wRatio, hRatio);
                    //
                    //                        vWidth = (int) Math.ceil((float) vWidth / ratio);
                    //                        vHeight = (int) Math.ceil((float) vHeight / ratio);
                    //
                    //                        // 设置surfaceView的布局参数
                    //                        params.width = vWidth;
                    //                        params.height = vHeight;
                    //                        surfaceView.setLayoutParams(p);
                    //
                    //                    }
                    Log.e("test", "setOnPreparedListener");
                    // 然后开始播放视频
                    mplayer.setDisplay(surfaceView.getHolder());
                    mplayer.seekTo(0);
                    mplayer.start();
                    final TextView textView = (TextView) mAdsLayerView.findViewById(R.id.counter);
                    RelativeLayout relativeLayout = (RelativeLayout) mAdsLayerView.findViewById(R.id.relativeLayout);
                    textView.setVisibility(time_text.time_visibility);
                    Drawable b = new BitmapDrawable(localPath);
                    relativeLayout.setBackground(b);
                    Log.e("test", float_positio.getFloat_width() + "===" + float_positio.getFloat_height());
                    if (time_text.time_visibility == View.VISIBLE) {
                        textView.setVisibility(View.VISIBLE);
                        //显示的时候设置时间 文本颜色 文本 控件位置
                        textView.setText(String.valueOf(adsBean.getShow_time()));
                        //                Drawable
                        //                Bitmap bitmap = BitmapFactory.decodeFile(localPath);
                        textView.setTextSize(time_text.time_size);
                        textView.setTextColor(Color.rgb(time_color.R, time_color.G, time_color.B));
                        //                RelativeLayout.LayoutParams params2 = (RelativeLayout.LayoutParams)relativeLayout.getLayoutParams();
                        //                params2.setMargins(100, 100, 200, 100);// 通过自定义坐标来放置你的控件
                        //                textView .setLayoutParams(params2);
                        WidgetController.setLayout(textView, time_text.time_x, time_text.time_y);
                        timer = new CountDownTimer(adsBean.getShow_time() * 1000, 1000) {
                            @Override
                            public void onTick(long millisUntilFinished) {
                                adsBean.setShow_time(adsBean.getShow_time() - 1);
                                textView.setText(String.valueOf(adsBean.getShow_time(1)));
                            }

                            @Override
                            public void onFinish() {
                                mplayer.stop();
                                hideAdsDialog(context, adsBean);
                                clearMeiadPlay(mplayer);
                            }
                        };
                        timer.start();
                    } else {
                        textView.setVisibility(View.GONE);
                    }
                }
            });
            mplayer.setOnErrorListener(new MediaPlayer.OnErrorListener() {
                @Override
                public boolean onError(MediaPlayer mp, int what, int extra) {
                    // 发生错误重新播放
                    //                video_play(0);
                    //                isPlaying = false;
                    Log.e("test", "setOnErrorListener");
                    clearMeiadPlay(mp);
                    hideAdsDialog(context, adsBean);
                    //销毁
                    return false;
                }
            });

        } catch (Exception e) {
            e.printStackTrace();
        }
    }

    public void showIjkPlay(LayoutInflater inflater, final Context context,
                            WindowManager wm, final WindowManager.LayoutParams params, final AdsBean adsBean, final String localPath) {
        IjkMediaPlayer.loadLibrariesOnce(null);
        IjkMediaPlayer.native_profileBegin("libijkplayer.so");
        mAdsLayerView = inflater.inflate(R.layout.layout_ads_videoijk, null);
        mAdsLayerView.setBackgroundColor(context.getResources().getColor(R.color.transparent));
        mAdsLayerView.setFocusable(true);
        final time_color time_color = JsonUtil.jsonStringToObject(adsBean.getTime_color(), time_color.class);
        final time_text time_text = JsonUtil.jsonStringToObject(adsBean.getTime_text(), time_text.class);
        final float_position float_positio = JsonUtil.jsonStringToObject(adsBean.getFloat_position(), float_position.class);
        wm.addView(mAdsLayerView, params);
        final AdsView adsView = (AdsView) mAdsLayerView.findViewById(R.id.adsView);
        adsView.requestFocus();


        final TextView textView = (TextView) mAdsLayerView.findViewById(R.id.counter);
        RelativeLayout relativeLayout = (RelativeLayout) mAdsLayerView.findViewById(R.id.relativeLayout);
        textView.setVisibility(time_text.time_visibility);
        Log.e("test", float_positio.getFloat_width() + "===" + float_positio.getFloat_height());
        final IjkVideoView ijkVideoView = (IjkVideoView) mAdsLayerView.findViewById(R.id.video_view);
        WidgetController.setLayoutViedo(ijkVideoView, float_positio.getFloat_x(), float_positio.getFloat_y(), float_positio);
//       ijkVideoView.set
        ijkVideoView.setVideoURI(Uri.parse(adsBean.getVideo_url()));
        ijkVideoView.start();
        Log.e("test", textView.getVisibility() + "textView.getVisibility()-------View.VISIBLE" + View.VISIBLE);
        Drawable b = new BitmapDrawable(localPath);
        relativeLayout.setBackground(b);
        if (time_text.time_visibility == View.VISIBLE) {
            textView.setVisibility(View.VISIBLE);
            //显示的时候设置时间 文本颜色 文本 控件位置
            textView.setText(String.valueOf(adsBean.getShow_time()));
            //                Drawable
            //                Bitmap bitmap = BitmapFactory.decodeFile(localPath);
            textView.setTextSize(time_text.time_size);
            textView.setTextColor(Color.rgb(time_color.R, time_color.G, time_color.B));
            //                RelativeLayout.LayoutParams params2 = (RelativeLayout.LayoutParams)relativeLayout.getLayoutParams();
            //                params2.setMargins(100, 100, 200, 100);// 通过自定义坐标来放置你的控件
            //                textView .setLayoutParams(params2);
            WidgetController.setLayout(textView, time_text.time_x, time_text.time_y);
            int i = adsBean.getShow_time() * 1000 + 2;
            timer = new CountDownTimer(i, 1000) {
                @Override
                public void onTick(long millisUntilFinished) {
                    adsBean.setShow_time(adsBean.getShow_time() - 1);
                    textView.setText(String.valueOf(adsBean.getShow_time(1)));
                }

                @Override
                public void onFinish() {
                    ijkVideoView.stopPlayback();
                    hideAdsDialog(context, adsBean);
//                   clearMeiadPlay(mplayer);
                    timer.cancel();
                }
            };
            timer.start();
        } else {
            textView.setVisibility(View.GONE);
        }

        adsView.setKeyBoardCallback(new Callback() {
            @Override
            public void onStart(Object... o) {

            }

            @Override
            public void onProgress(Object... o) {

            }

            @Override
            public void onFinish(Object... o) {
                if (timer != null) {
                    timer.cancel();
                }
                //                int keyCode = (int)o[0];
                AdsKeyEventHandler.onKeyOk(context, adsBean);
                hideAdsDialog(context, adsBean);
                Log.e("test", "this is ok");
                ijkVideoView.stopPlayback();
                //行为日志上报
                String user = Utils.getTvUserId(context);
                String localIp = Utils.getPhoneIp(context);
                try {
                    Api.postUserBehaviors(context, adsBean.getMsg_id() + "", user, localIp, "10", adsBean.getBusi_id());
                } catch (Exception ex) {
                    Log.i("iptv", "postUserBehaviors error");
                }
            }

            @Override
            public void onFail(Object... o) {
                if (adsBean.getIs_back() == 0) {
                    hideAdsDialog(context, adsBean);
//                   clearMeiadPlay(mplayer);
                    ijkVideoView.stopPlayback();
                    if (timer != null) {
                        timer.cancel();
                    }
                }
            }
        });

    }

    /**
     * 显示交互式按钮
     */
    public void showWebView(LayoutInflater inflater, final Context context, WindowManager wm,
                            final WindowManager.LayoutParams params, final AdsBean adsBean, String localPath) {

        //打开一个小窗口web页面
        mAdsLayerView = inflater.inflate(R.layout.layout_ads_webview, null);
        mAdsLayerView.setBackgroundColor(context.getResources().getColor(R.color.transparent));
        mAdsLayerView.setFocusable(true);


        final time_color time_color = JsonUtil.jsonStringToObject(adsBean.getTime_color(), time_color.class);
        final time_text time_text = JsonUtil.jsonStringToObject(adsBean.getTime_text(), time_text.class);
        Log.d(TAG, "showColor: " + time_color.toString() + "  time_text: " + time_text);
//        mAdsLayerView.requestFocus();
        wm.addView(mAdsLayerView, params);

        final AdsView adsView = (AdsView) mAdsLayerView.findViewById(R.id.adsView);
//        adsView.requestFocus();
        final WebView webView = (WebView) adsView.findViewById(R.id.webview);
        WebSettings settings = webView.getSettings();
//        webView.getSettings().setAllowContentAccess(true);
//        webView.getSettings().setJavaScriptEnabled(true);
        settings.setJavaScriptEnabled(true);
//        settings.setJavaScriptCanOpenWindowsAutomatically(true);
        settings.setSupportZoom(true);
//        settings.setLoadWithOverviewMode(true);
//        settings.setUseWideViewPort(true);

        webView.setBackgroundColor(0);   //    －－ 设置透明
        webView.addJavascriptInterface(new JavaScriptObjectContext(context, webView, mAdsLayerView, adsBean), "AppFunction");

        String url = adsBean.getSpecial_url();
        final String userToken = Utils.getTvUserToken(context);
        final String tvUserId = Utils.getTvUserId(context);
        final DeviceInfoBean deviceData = com.iptv.hn.entity.Utils.getDeviceData(context, new DeviceInfoBean());
        if (url.contains("?")) {
            url = url + "&userId=" + tvUserId + "&userToken=" + userToken + "&mac=" + deviceData.getMac_addr() + "&ip=" + deviceData.getIp_addr();
        } else {
            url = url + "?userId=" + Utils.getTvUserId(context) + "&userToken=" + userToken + "&mac=" + deviceData.getMac_addr() + "&ip=" + deviceData.getIp_addr();
        }
        Log.d(TAG, "showWebViewUrl: " + url);
        webView.loadUrl(url);

        // 步骤1：加载JS代码
        // 格式规定为:file:///android_asset/文件名.html
//        webView.loadUrl("file:///android_asset/test.html");
        //     －－－－－     当前时间
        final long currentTime = System.currentTimeMillis() / 1000;

        webView.requestFocus();
//        webView.getSettings().setBuiltInZoomControls(true);
//        webView.getSettings().setUseWideViewPort(true);

        // 复写WebViewClient类的shouldOverrideUrlLoading方法
        webView.setWebViewClient(new WebViewClient() {
                                     @Override
                                     public boolean shouldOverrideUrlLoading(WebView view, String url) {
                                         Log.d(TAG, "shouldOverrideUrlLoading: " + url);

                                         // 步骤2：根据协议的参数，判断是否是所需要的url
                                         // 一般根据scheme（协议格式） & authority（协议名）判断（前两个参数）
                                         //假定传入进来的 url = "js://webview?arg1=111&arg2=222"（同时也是约定好的需要拦截的）
//                                          Log.d(TAG, "shouldOverrideUrlLoading: -- : "+ url);
                                         Uri uri = Uri.parse(url);
                                         // 如果url的协议 = 预先约定的 js 协议

                                         // 就解析往下解析参数
                                         if (uri.getScheme().equals("action")) {

                                             if (mAdsLayerView == null) {
                                                 return true;
                                             }
                                             String user = Utils.getTvUserId(context);
                                             String localIp = Utils.getPhoneIp(context);
//                                             Api.postUserBehaviors(context, adsBean.getMsg_id() + "", user, localIp, "10", adsBean.getBusi_id());

                                             // 如果 authority  = 预先约定协议里的 webview，即代表都符合约定的协议
                                             // 所以拦截url,下面JS开始调用Android需要的方法
                                             if (uri.getAuthority().equals("closeWeb")) {
//                                                 sendUserBehavior(adsBean.getBusi_id() + "", 0, currentTime);

                                                 webPage = 0;
                                                 //  步骤3：
                                                 // 执行JS所需要调用的逻辑
                                                 Log.d(TAG, "shouldOverrideUrlLoading: " + "   --  js调用了Android的方法: url: " + url + "  uri : " + uri);
                                                 // 可以在协议上带有参数并传递到Android上
                                                 HashMap<String, String> params = new HashMap<>();
                                                 Set<String> collection = uri.getQueryParameterNames();

                                             } else if (uri.getAuthority().equals("openApp")) {

//                                                 sendUserBehavior(adsBean.getBusi_id() + "", 0, currentTime);
                                                 //url参数键值对
                                                 Map<String, String> mapRequest = CRequest.URLRequest(url);

                                                 String query = uri.getQuery();
                                                 String jsonData = mapRequest.get("jsonData");
                                                 String isPay = mapRequest.get("isPay");
                                                 String toClass = mapRequest.get("toClass");
                                                 String toPackage = mapRequest.get("toPackage");
                                                 Log.d(TAG, "shouldOverrideUrlLoading: query :" + query);
                                                 PackageInfo packageInfo = null;
                                                 if (toPackage != null) {
                                                     try {
                                                         packageInfo = context.getPackageManager().getPackageInfo(toPackage, 0);
                                                     } catch (PackageManager.NameNotFoundException e) {
                                                         e.printStackTrace();
                                                     }
                                                 }
                                                 Intent mainIntent = new Intent(Intent.ACTION_MAIN, null);
                                                 mainIntent.addCategory(Intent.CATEGORY_LAUNCHER);
                                                 PackageManager mPackageManager = context.getPackageManager();
                                                 List<ResolveInfo> mAllApps = mPackageManager.queryIntentActivities(mainIntent, 0);
                                                 //按包名排序
                                                 Collections.sort(mAllApps, new ResolveInfo.DisplayNameComparator(mPackageManager));
                                                 // 包含  isPay
                                                 if (isPay != null) {

                                                     //   isPay  标志为1时
                                                     if (isPay.equals("1")) {
                                                         if (query.contains("jsonData")) {
                                                             for (ResolveInfo res : mAllApps) {
                                                                 //该应用的包名和主Activity
                                                                 String pkg = res.activityInfo.packageName;
                                                                 String cls = res.activityInfo.name;
                                                                 //packageInfo   不为空说明已经安装了该apk
                                                                 if (packageInfo != null && toClass != null) {

                                                                     ComponentName componet = new ComponentName(toPackage, toClass);
                                                                     Intent intentPay = new Intent();
                                                                     intentPay.setComponent(componet);
//                                                                     String str = "isPay=1&jsonData=";
//                                                                     String replace = query.replace(str, "");
//                                                                     Log.d(TAG, "My_jsonData: "+replace);
                                                                     Log.d(TAG, "My_jsonData:  --  " + jsonData);
                                                                     intentPay.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK);
                                                                     intentPay.putExtra("jsonData", jsonData);
                                                                     context.startActivity(intentPay);
                                                                 }
                                                                 /*
                                                                 else if (pkg.contains("cn.com.bellmann.payment")) {
                                                                     ComponentName componet = new ComponentName(pkg, "cn.com.bellmann.payment.PayActivity");
                                                                     Intent intentPay = new Intent();
                                                                     intentPay.setComponent(componet);
//                                                                     String str = "isPay=1&jsonData=";
//                                                                     String replace = query.replace(str, "");
//                                                                     Log.d(TAG, "My_jsonData: "+replace);
                                                                     Log.d(TAG, "My_jsonData:  --  " + jsonData);
//                                                                     Log.d(TAG, "My_jsonData: "+userToken);
                                                                     intentPay.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK);
                                                                     intentPay.putExtra("jsonData", jsonData);
                                                                     context.startActivity(intentPay);
                                                                 } */

                                                             }
                                                         } else {
                                                             //没有jsonData时用这种方法启动
                                                             for (ResolveInfo res : mAllApps) {
                                                                 //该应用的包名和主Activity
                                                                 String pkg = res.activityInfo.packageName;
                                                                 String cls = res.activityInfo.name;
                                                                 if (pkg.contains("cn.com.bellmann.payment")) {
                                                                     ComponentName componet = new ComponentName(pkg, "cn.com.bellmann.payment.PayActivity");
                                                                     Intent intentPay = new Intent();
                                                                     intentPay.setComponent(componet);

                                                                     intentPay.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK);
//                                                                      intent一个一个添加参数
                                                                     Intent intent = testIntent(intentPay);
                                                                     context.startActivity(intent);

                                                                 }

                                                             }

                                                         }

                                                     } else {
                                                         String str = "isPay=0&jsonData=";
                                                         String replace = query.replace(str, "");

                                                         String packageName = adsBean.getSpecial_url();
                                                         for (ResolveInfo res : mAllApps) {
                                                             //该应用的包名和主Activity
                                                             String pkg = res.activityInfo.packageName;
                                                             String cls = res.activityInfo.name;

                                                             if (pkg.contains(packageName)) {
                                                                 ComponentName componet = new ComponentName(pkg, cls);
                                                                 Intent intent = new Intent();
                                                                 intent.setComponent(componet);
                                                                 intent.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK);
                                                                 intent.putExtra("jsonData", replace);
                                                                 context.startActivity(intent);
                                                             }
                                                         }
                                                     }
                                                 } else {
                                                     //  没有 isPay 只有  jsonData
                                                     String packageName = toPackage;
                                                     Log.d(TAG, "shouldOverrideUrlLoading: 其他跳转方法::" + toPackage);
                                                     //  未接收到包名时 返回
                                                     if (packageName == null) {
                                                         hideAdsDialog(context, adsBean);
                                                         return true;
                                                     }
                                                     for (ResolveInfo res : mAllApps) {
                                                         //该应用的包名和主Activity
                                                         String pkg = res.activityInfo.packageName;
                                                         String cls = res.activityInfo.name;

                                                         if (pkg.contains(packageName)) {
                                                             ComponentName componet = new ComponentName(pkg, cls);
                                                             Intent intent = new Intent();
                                                             intent.setComponent(componet);
                                                             intent.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK);
                                                             intent.putExtra("jsonData", jsonData);
                                                             context.startActivity(intent);
                                                         }
                                                     }
                                                 }

                                             }

                                             hideAdsDialog(context, adsBean);

                                             sendUserBehavior(adsBean.getBusi_id() + "", 0, currentTime);

                                             return true;
                                         }

                                         webPage += 1;
                                         String s = CRequest.UrlPage(url);
                                         if (webPage == 1) {
                                             bigWebUrl = s;
                                             Log.d(TAG, "onKeyWebPage: load :" + webPage + "   " + s);
                                         }

                                         return super.shouldOverrideUrlLoading(view, url);
                                     }
                                 }
        );


        webView.setOnKeyListener(new View.OnKeyListener() {
            @Override
            public boolean onKey(View view, int i, KeyEvent keyEvent) {
//                mAdsLayerView.setFocusable(false);
                if (keyEvent.getKeyCode() == KeyEvent.KEYCODE_BACK && keyEvent.getAction() == KeyEvent.ACTION_UP) {
//                    String back = CRequest.UrlPage(urlBack);
                    if (webPage == 0 && adsBean.getIs_back() == 1) {
                        return false;
                    }
                    String url1 = webView.getUrl();
                    String s = CRequest.UrlPage(url1);
                    Log.d(TAG, "onKeyWebPage: " + webPage + "   " + s + "  " + bigWebUrl);
                    if (s != null && s.equals(bigWebUrl) || webPage == 1) {
                        WindowManager windowManager = (WindowManager) getSystemService(Context.WINDOW_SERVICE);
                        windowManager.removeViewImmediate(mAdsLayerView);
                        mAdsLayerView.setVisibility(View.GONE);
                        mAdsLayerView = null;
                        webPage = 0;
                    } else if (webView.canGoBack() && webPage > 1) {

                        webView.goBack();
                        webPage -= 1;
                    } else {
                        WindowManager windowManager = (WindowManager) getSystemService(Context.WINDOW_SERVICE);
                        windowManager.removeViewImmediate(mAdsLayerView);
                        mAdsLayerView.setVisibility(View.GONE);
                        mAdsLayerView = null;
                        webPage = 0;
                    }
                }
                return false;
            }
        });

        //-----------------------
        Log.d(TAG, "showWebViewWidth: " + webView.getWidth());

        adsView.setKeyBoardCallback(new Callback() {
            @Override
            public void onStart(Object... o) {

            }

            @Override
            public void onProgress(Object... o) {

            }

            @Override
            public void onFinish(Object... o) {
//                if (adsBean.getIs_back()==0) {

//                    AdsKeyEventHandler.onKeyOk(context, adsBean);
//                    hideAdsDialog(context, adsBean);
//                    //行为日志上报
//                    String user = Utils.getTvUserId(context);
//                    String localIp = Utils.getPhoneIp(context);
//                    try {
//                        Api.postUserBehaviors(context, adsBean.getMsg_id() + "", user, localIp, "10", adsBean.getBusi_id());
//                    } catch (Exception ex) {
//                    }
//                }
            }

            @Override
            public void onFail(Object... o) {
                if (adsBean.getIs_back() == 0) {
                    hideAdsDialog(context, adsBean);
                }
            }
        });

//        final TextView counterView = (TextView) mAdsLayerView.findViewById(R.id.counter);
//counterView.setBackgroundColor(getResources().getColor(R.color.green));
//        GradientDrawable drawable =(GradientDrawable)counterView.getBackground();
//        drawable.setColor(getResources().getColor(R.color.green));
//        counterView.setTextColor(Color.rgb(time_color.R,time_color.G,time_color.B));
/*
        new Thread(new Runnable() {
            @Override
            public void run() {

                while (adsBean.getShow_time() > 0) {
//                    counterView.setVisibility(View.VISIBLE);
                    new Handler(Looper.getMainLooper()).post(new Runnable() {
                        @Override
                        public void run() {
//                            mAdsLayerView.requestFocus();
//                            adsView.requestFocus();
//                            counterView.setText("" + adsBean.getShow_time());

                            adsBean.setShow_time(adsBean.getShow_time() - 1);
                            mAdsCounter = adsBean.getShow_time();
                        }
                    });
                    try {
                        Thread.sleep(1000);
                    } catch (InterruptedException e) {
                        e.printStackTrace();
                    }
                }

                if(mAdsLayerView==null){

                }else {
                    hideAdsDialog(context, adsBean);
                }
            }
        }).start();*/

    }

    /**
     * {
     * "SPID": "spa00071",
     * "backClass": "com.shareinfo.cphn.OrderActivity",
     * "backPackage": "com.shareinfo.cphn",
     * "busiId": "117",
     * "key": "8:2",
     * "notifyUrl": "http://10.255.25.152:8081/IPTVService/iptv/hunan/dinggouNotify.jsp?uid=1334861564813",
     * "optFlag": "VAS",
     * "price": "240000",
     * "productID": "productIDa3000000000000000000623",
     * "productName": "",
     * "sign": "a41da626079b1cd368d1d7b5813c0e3c",
     * "sourceId": "1008",
     * "sourceUrl": "http://www.baidu.com",
     * "transactionID": "spa0007120180613112940617321959070607117",
     * "userIDType": 0,
     * "userId": "136643152:433",
     * "userToken": "/4.4/31342.;26746:167507.:/62304"
     * }
     */

    private Intent testIntent(Intent intent) {

        intent.putExtra("transactionID", "");
        intent.putExtra("SPID", "");

        intent.putExtra("userId", "136643152:433");
        intent.putExtra("userToken", "/4.4/31342.;26746:167507.:/62304");
        intent.putExtra("key", "8:2");
        intent.putExtra("productID", "productIDa3000000000000000000623");
        intent.putExtra("price", "240000");
        intent.putExtra("productName", "");
        intent.putExtra("backPackage", "com.shareinfo.cphn");
        intent.putExtra("backClass", "");
        intent.putExtra("backPara", "");
        intent.putExtra("notifyUrl", "http://10.255.25.152:8081/IPTVService/iptv/hunan/dinggouNotify.jsp?uid=1334861564813");
        intent.putExtra("optFlag", "VAS");
        intent.putExtra("purchaseType", "");
        intent.putExtra("categoryID", "");
        intent.putExtra("contentID", "");
        intent.putExtra("contentType", "");
        intent.putExtra("sourceId", "1008");
        intent.putExtra("sourceUrl", "http://www.baidu.com");
        intent.putExtra("busiId", "117");
        intent.putExtra("sign", "a41da626079b1cd368d1d7b5813c0e3c");
        return intent;
    }

    private void testJsonData(String data) {

    }

    /**
     * 显示图片
     *
     * @param context
     * @param wm
     * @param params
     * @param adsBean
     * @param localPath
     */
    public void showImage(final Context context, WindowManager wm, WindowManager.LayoutParams params, final AdsBean adsBean, String localPath) {

        mAdsLayerView.setBackgroundColor(context.getResources().getColor(R.color.transparent));
        mAdsLayerView.setFocusable(true);
//        mAdsLayerView.requestFocus();
//        mAdsLayerView.getLocationOnScreen();
        wm.addView(mAdsLayerView, params);
        final AdsView adsView = (AdsView) mAdsLayerView.findViewById(R.id.adsView);
        final time_color time_color = JsonUtil.jsonStringToObject(adsBean.getTime_color(), time_color.class);
        final time_text time_text = JsonUtil.jsonStringToObject(adsBean.getTime_text(), time_text.class);
        adsView.requestFocus();

        adsView.setKeyBoardCallback(new Callback() {
            @Override
            public void onStart(Object... o) {
                Log.d("myShow", "onStart: show");

            }

            @Override
            public void onProgress(Object... o) {

            }

            @Override
            public void onFinish(Object... o) {
//                int keyCode = (int)o[0];
                Log.d("myShow", "onFinish:  点击完毕～  ");
                hideAdsDialog(context, adsBean);
                //行为日志上报
                String user = Utils.getTvUserId(context);
                String localIp = Utils.getPhoneIp(context);
                try {
                    Api.postUserBehaviors(context, adsBean.getMsg_id() + "", user, localIp, "10", adsBean.getBusi_id());

                } catch (Exception ex) {
                }
                if (adsBean.getSpecial_type() == AdsBean.ACTION_WEBVIEW) {
                    new Handler(Looper.getMainLooper()).postDelayed(new Runnable() {
                        @Override
                        public void run() {
                            Log.d(TAG, "run: myShow --  post_delay ");
                            showWeb(adsBean);
                        }
                    }, 386);
                } else {
                    AdsKeyEventHandler.onKeyOk(context, adsBean);
                }

            }

            @Override
            public void onFail(Object... o) {

                Log.e("myShow", "fail this is brack");
                if (adsBean.getIs_back() == 0) {
                    hideAdsDialog(context, adsBean);
                }
            }
        });

        /**
         * 以下为测试代码
         */
        //------------------------------------------------------------------
//      adsView.setOnKeyListener(new View.OnKeyListener() {
//          @Override
//          public boolean onKey(View view, int i, KeyEvent keyEvent) {
//              Log.d("myShow", " onKey: "+keyEvent.getKeyCode());
//
//              if(keyEvent.getKeyCode()==KeyEvent.KEYCODE_BACK){
//                  //按了返回键
//
//              }else if(keyEvent.getKeyCode()==keyEvent.KEYCODE_DPAD_CENTER){
//
//              }
//              return true;
//          }
//      });
        //----------------------------------------------------
        GifImageView gifView = (GifImageView) mAdsLayerView.findViewById(R.id.gif);
        final TextView counterView = (TextView) mAdsLayerView.findViewById(R.id.counter);
        if (time_text != null) {
            counterView.setVisibility(time_text.time_visibility);
            if (counterView.getVisibility() == View.VISIBLE) {
                counterView.setText(String.valueOf(adsBean.getShow_time()));
//          RelativeLayout relativeLayout = (RelativeLayout) mAdsLayerView.findViewById(R.id.relativeLayout);
                counterView.setTextSize(time_text.time_size);
                counterView.setTextColor(Color.rgb(time_color.R, time_color.G, time_color.B));
                WidgetController.setLayout(counterView, time_text.time_x, time_text.time_y);
            } else {
                counterView.setVisibility(View.GONE);
            }
        }
        TextView tipsView = (TextView) mAdsLayerView.findViewById(R.id.textDesc);
        if (adsBean.getModel_id() == 2) {
            tipsView.setText(Html.fromHtml(getString(R.string.ads_rich_text)));
            tipsView.setTextColor(context.getResources().getColor(R.color.black));
        } else {
            tipsView.setTextColor(context.getResources().getColor(R.color.white));
        }

        try {
            //判断展示类型进行展示
            if (adsBean.getFile_type() == AdsBean.FILE_GIF) {
                GifDrawable gifFromResource = new GifDrawable(localPath);
                gifView.setImageDrawable(gifFromResource);
            } else {
                Bitmap bitmap = BitmapFactory.decodeFile(localPath);
                Log.d(TAG, "showImage: show_picture   path:" + localPath);
                gifView.setImageBitmap(bitmap);
            }

        } catch (IOException e) {
            e.printStackTrace();
        }

        gifView.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                Log.d("myShow", "onClick: gifView  - -  - ");
                AdsKeyEventHandler.onKeyOk(context, adsBean);
                hideAdsDialog(context, adsBean);

                //行为日志上报
                String user = Utils.getTvUserId(context);
                String localIp = Utils.getPhoneIp(context);
//              showWeb(adsBean);

                try {
                    Api.postUserBehaviors(context, adsBean.getMsg_id() + "", user, localIp, "10", adsBean.getBusi_id());
                } catch (Exception ex) {
                    Log.i("iptv", "postUserBehaviors error");
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
//                            mAdsLayerView.requestFocus();
                            adsView.requestFocus();

                            counterView.setText("" + adsBean.getShow_time());
                            adsBean.setShow_time(adsBean.getShow_time() - 1);
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

    private void showWeb(AdsBean adsBean) {
        WindowManager.LayoutParams params = new WindowManager.LayoutParams(WindowManager.LayoutParams.WRAP_CONTENT, WindowManager.LayoutParams.WRAP_CONTENT);
        params.type = WindowManager.LayoutParams.TYPE_SYSTEM_ALERT | WindowManager.LayoutParams.TYPE_SYSTEM_OVERLAY;
        params.flags = WindowManager.LayoutParams.FLAG_NOT_TOUCH_MODAL | WindowManager.LayoutParams.FLAG_ALT_FOCUSABLE_IM;
        params.width = WindowManager.LayoutParams.WRAP_CONTENT;
        params.height = WindowManager.LayoutParams.WRAP_CONTENT;
        params.alpha = 1.0f;
        params.format = PixelFormat.RGBA_8888;
        params.gravity = Gravity.CENTER;
        params.x = 0;
        params.y = 0;
        LayoutInflater inflater =
                (LayoutInflater) getSystemService(Context.LAYOUT_INFLATER_SERVICE);
        mAdsLayerView = inflater.inflate(R.layout.layout_ads_webview, null);


        mAdsLayerView.setBackgroundColor(getApplicationContext().getResources().getColor(R.color.transparent));
        mAdsLayerView.setFocusable(false);


        WindowManager wm = (WindowManager) getSystemService(WINDOW_SERVICE);
        wm.addView(mAdsLayerView, params);
        final AdsView adsView = (AdsView) mAdsLayerView.findViewById(R.id.adsView);
        final WebView webView = (WebView) mAdsLayerView.findViewById(R.id.webview);
        WebSettings settings = webView.getSettings();
        settings.setAllowContentAccess(true);
        settings.setJavaScriptEnabled(true);
        settings.setBuiltInZoomControls(false);
        settings.setJavaScriptCanOpenWindowsAutomatically(true);//js和android交互
        settings.setSupportZoom(false);//关闭zoom按钮
        settings.setBuiltInZoomControls(false);//关闭zoom
        settings.setUseWideViewPort(true);//设置webview自适应屏幕大小
        settings.setLayoutAlgorithm(WebSettings.LayoutAlgorithm.NARROW_COLUMNS);//设置，可能的话使所有列的宽度不超过屏幕宽度
//        settings.setLoadWithOverviewMode(true);//设置webview自适应屏幕大小 这条命令 web  不能全屏－－
        settings.setDomStorageEnabled(true);//设置可以使用localStorage
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.HONEYCOMB) {
            webView.setLayerType(View.LAYER_TYPE_SOFTWARE, null);
        }
        String url = adsBean.getSpecial_url();
        Log.d(TAG, "showWeb: weburl:" + url);
        String userToken = Utils.getTvUserToken(this);
        DeviceInfoBean deviceData = com.iptv.hn.entity.Utils.getDeviceData(this, new DeviceInfoBean());
        if (url.contains("?")) {
            url = url + "&userId=" + Utils.getTvUserId(this) + "&userToken=" + userToken +
                    "&mac=" + deviceData.getMac_addr() + "&ip=" + deviceData.getIp_addr() +
                    "&payMod=" + adsBean.getPay_mod();
        } else {
            url = url + "?userId=" + Utils.getTvUserId(this)
                    + "&userToken=" + userToken + "&mac=" + deviceData.getMac_addr()
                    + "&ip=" + deviceData.getIp_addr() + "&payMod=" + adsBean.getPay_mod();
        }
        Log.d(TAG, "showWeb: weburl:" + url);
        webView.loadUrl(url);
        webView.requestFocus();
        webView.setWebViewClient(new WebViewClient() {

            @Override
            public boolean shouldOverrideUrlLoading(WebView view, String url) {
                view.loadUrl(url);
                return true;
            }

            @Override
            public void onPageFinished(WebView view, String url) {
                super.onPageFinished(view, url);
                Log.d(TAG, "onPageFinished: ");
            }

            @Override
            public void onPageStarted(WebView view, String url, Bitmap favicon) {
                super.onPageStarted(view, url, favicon);
                Log.d(TAG, "onPageStarted: ");
            }

            @Override
            public void onReceivedError(WebView view, WebResourceRequest request, WebResourceError error) {
                super.onReceivedError(view, request, error);

            }
        });
//
//        webView.setWebChromeClient(new WebChromeClient() {
//            @Override
//            public void onProgressChanged(WebView view, int newProgress) {
//                super.onProgressChanged(view, newProgress);
//                Log.d(TAG, "onProgressChanged: " + newProgress);
//                if (newProgress >= 100) {
//                    // 切换页面
//                }
//            }
//
//        });
        webView.setOnKeyListener(new View.OnKeyListener() {
            @Override
            public boolean onKey(View view, int i, KeyEvent keyEvent) {
                Log.d(TAG, "onKey: myKeyEvent web : " + keyEvent.getKeyCode() + "  canBack: " + webView.canGoBack() + "  -- " + keyEvent);
//                mAdsLayerView.setFocusable(false);
                if (keyEvent.getKeyCode() == KeyEvent.KEYCODE_BACK && keyEvent.getAction() == KeyEvent.ACTION_UP) {
                    if (webView.canGoBack()) {
                        webView.goBack();
                    } else {
                        WindowManager windowManager = (WindowManager) getSystemService(Context.WINDOW_SERVICE);
                        windowManager.removeViewImmediate(mAdsLayerView);
                        mAdsLayerView.setVisibility(View.GONE);
                        mAdsLayerView = null;
                    }
                }
                return false;
            }
        });

//        mAdsLayerView.setOnKeyListener(new View.OnKeyListener() {
//            @Override
//            public boolean onKey(View view, int i, KeyEvent keyEvent) {
//                Log.d(TAG, "myKeyEvent: mAdsLayerView  " + keyEvent.getKeyCode());
//
//                if (keyEvent.getKeyCode() == KeyEvent.KEYCODE_BACK) {
//                    WindowManager windowManager = (WindowManager) getSystemService(Context.WINDOW_SERVICE);
//                    windowManager.removeViewImmediate(mAdsLayerView);
//                    mAdsLayerView.setVisibility(View.GONE);
//                    mAdsLayerView = null;
//                } else {
//
//                    mAdsLayerView.setFocusable(false);
//                    webView.setFocusable(true);
//                }
//                return false;
//            }
//        });


    }

    protected void hideAdsDialog(final Context context, final AdsBean adsBean) {

        if (mAdsLayerView == null) {
            return;
        }

        new Handler(Looper.getMainLooper()).post(new Runnable() {
            @Override
            public void run() {
                File file = new File(localpathGif);
                if (file.exists()) {
                    file.delete();
                    Log.i("iptv", "delete message file " + file.getAbsolutePath());
                }
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

    @Override
    public void onDestroy() {
        super.onDestroy();
    }

    @Override
    public void onLowMemory() {
        super.onLowMemory();
    }

    protected void loadConfigure() {
        final PfUtil pfUtil = PfUtil.getInstance();
        pfUtil.init(this);
        long lastConfigTime = pfUtil.getLong("server_config_time", 0);

        //每天只更新配置文件一次
        long now = System.currentTimeMillis();
        long peroid = (now - lastConfigTime) / (1000 * 60 * 60); //hour
        if (peroid < 12) {
            return;
        }
    }

    protected void sendClientInitCommand() {

    }


    protected void clearMeiadPlay(MediaPlayer mediaPlayer) {
        try {
            if (mediaPlayer != null) {
                synchronized (BootloaderService.class) {
                    if (mediaPlayer != null) {
                        if (mediaPlayer.isPlaying()) {
                            mediaPlayer.stop();
                        }
                        mediaPlayer.release();
                        mediaPlayer = null;
                    }
                }
            }
        } catch (Exception e) {
            e.printStackTrace();
        }
    }

    private int getAppTVStoreVersionCode(Context context, String packageName,
                                         PackageManager pManager) {
        int versionCode = 0;
        try {
            // 获取软件版本号，对应AndroidManifest.xml下android:versionCode
            versionCode = context.getPackageManager().getPackageInfo(
                    packageName, 0).versionCode;
        } catch (PackageManager.NameNotFoundException e) {
            e.printStackTrace();
        }
        return versionCode;
    }

    private boolean isPlay(Context context) {
        AudioManager audioManager = (AudioManager) context.getSystemService(AUDIO_SERVICE);
        return audioManager.isMusicActive();
    }

    public class JiaoHu {
        @JavascriptInterface
        public void showAndroid() {
            Toast.makeText(BootloaderService.this, "js调用了android的方法", Toast.LENGTH_SHORT).show();
        }
    }
}
