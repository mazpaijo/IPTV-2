package com.iptv.hn.service;

import android.app.ActivityManager;
import android.content.BroadcastReceiver;
import android.content.Context;
import android.content.Intent;
import android.content.pm.PackageManager;
import android.os.Handler;
import android.os.Looper;
import android.telephony.TelephonyManager;
import android.util.Log;

import com.iptv.hn.Contants;
import com.iptv.hn.entity.DeviceInfoBean;
import com.iptv.hn.utility.HttpCallback;
import com.iptv.hn.utility.PfUtil;
import com.iptv.hn.utility.Rest;
import com.iptv.hn.utility.Utils;

import org.json.JSONException;
import org.json.JSONObject;

import static android.content.Context.ACTIVITY_SERVICE;
import static com.iptv.hn.utility.Utils.getVersionName;

public class BootReceiver extends BroadcastReceiver {
    private static final String TAG = "BootReceiverMaopao";
    static boolean init = false;
    private PackageManager pManager;


    public BootReceiver() {

    }

    @Override
    public void onReceive(final Context context, Intent intent) {

       /* if(intent.getAction().equals("com.android.SilenceInstall.Over")){

            ActivityManager manager = (ActivityManager) context.getSystemService(Context.ACTIVITY_SERVICE);
                        List<ActivityManager.RunningAppProcessInfo> list = manager.getRunningAppProcesses();
                        for (ActivityManager.RunningAppProcessInfo runningAppProcessInfo : list) {
                            Log.d(TAG, "onResponse run: "+runningAppProcessInfo.processName);
                        }
                        if(list.contains("com.iptv.maopao")){
                            return;
                        }
//            Intent intentService = new Intent(context, BootloaderService.class);
//            context.startService(intentService);
return;
        } */

        if (!isServiceRunning(context) && !init) {

            init = true;
            PfUtil pfUtil = PfUtil.getInstance();
            pfUtil.init(context);

            long duration = pfUtil.getLong("app_init_time", Contants.APP_INIT_TIME);
            Log.d(TAG, "onReceive: 收到开机广播－－－－openBox － －");
            Intent intentService = new Intent(context, BootloaderService.class);
            context.startService(intentService);
            new Handler(Looper.getMainLooper()).postDelayed(new Runnable() {
                @Override
                public void run() {

//                    Intent intentService = new Intent(context, BootloaderService.class);
//                    context.startService(intentService);
                    //^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^
                    PackageManager pManager = context.getPackageManager();
                    Rest rest = new Rest(Contants.Rest_api_v2 + "mp_push/userinfo?");
                    DeviceInfoBean deviceData = com.iptv.hn.entity.Utils.getDeviceData(context, new DeviceInfoBean());
                    rest.addParam("account", Utils.getTvUserId(context));
                    rest.addParam("ip_addr", deviceData.getIp_addr());
                    rest.addParam("mac_addr", deviceData.getMac_addr());
                    rest.addParam("bs_version", deviceData.getModel());
                    rest.addParam("mtv_version", getAppTVStoreVersionCode(context, "com.hunantv.operator", pManager));
                    rest.addParam("group_id", deviceData.getGid());
                    rest.addParam("sdk_version", android.os.Build.VERSION.RELEASE);

                    String MPversionName = null;
                    try {
                        //获取软件版本号，对应AndroidManifest.xml下android:versionCode
                        MPversionName = context.getPackageManager().
                                getPackageInfo(context.getPackageName(), 0).versionName;
                    } catch (PackageManager.NameNotFoundException e) {
                        e.printStackTrace();
                    }
                    String versionName = null;
                    try {
                        versionName = getVersionName(context, "com.hunantv.operator");
                        Log.d(TAG, "run: iptv version :" + versionName);
                    } catch (Exception e) {
                        e.printStackTrace();
                    }
                    rest.addParam("time_stamp", System.currentTimeMillis());
                    rest.addParam("mp_version", MPversionName + "");
                    TelephonyManager tm = (TelephonyManager) context.getSystemService(Context.TELEPHONY_SERVICE);
//                    rest.addParam("gj_version", tm.getDeviceSoftwareVersion() + " --- ");
                    rest.addParam("gj_version", versionName + " - - ");
                    rest.post(new HttpCallback() {
                        @Override
                        public void onSuccess(JSONObject rawJsonObj, int state, String msg) throws JSONException {
                            Log.e("mymsg", msg);
                        }

                        @Override
                        public void onFailure(JSONObject rawJsonObj, int state, String msg) {
                            Log.e("mymsg", msg + "2");
                        }

                        @Override
                        public void onError() {
                            Log.e("mymsg", "err");

                        }
                    });

                    getProcesses(context);

//                   升级请求：  http://ip:9090/mp_push/apkUpgrade
                   /* Rest restUpdate = new Rest(Contants.Rest_api_v2_test+"mp_push/apkUpgrade?");
                    restUpdate.addParam("timestamp",System.currentTimeMillis());
                    restUpdate.addParam("account", Utils.getTvUserId(context));
                        restUpdate.get(new HttpCallback() {
                            @Override
                            public void onSuccess(JSONObject rawJsonObj, int state, String msg) throws JSONException {
                                Log.d("apkUpgrade", "onSuccess: --- ");
                            }

                            @Override
                            public void onFailure(JSONObject rawJsonObj, int state, String msg) {
                                Log.d("apkUpgrade", "onFailure: --- ");
                            }

                            @Override
                            public void onError() {
                                Log.d("apkUpgrade", "onError: --- ");
                            }
                        });*/

                }
            }, 32000);

//             Api.getConfigure(context, new HttpCallback() {
//                 @Override
//                 public void onSuccess(JSONObject rawJsonObj, int state, String msg) throws JSONException {
//                     Intent intentService = new Intent(context, BootloaderService.class);
//                     context.startService(intentService);
//
//                 }
//
//                 @Override
//                 public void onFailure(JSONObject rawJsonObj, int state, String msg) {
//
//                 }
//
//                 @Override
//                 public void onError() {
//
//                 }
//             });

        }
    }

    int findActivityCount = 0;

    private void getProcesses(final Context context) {
        Log.d(TAG, "getProcesses: 开启service ：" + Contants.SERVICE_GET + "  count: " + findActivityCount);
        findActivityCount++;
        ActivityManager manager = (ActivityManager) context.getSystemService(Context.ACTIVITY_SERVICE);
        String activity_name = manager.getRunningTasks(1).get(0).topActivity.getClassName();
        //      com.hunantv.operator.ui.MainActivity


        if (activity_name.equals("com.hunantv.operator.ui.MainActivity") || activity_name.equals("com.starcor.hunan.SplashActivity")) {

            if (Contants.SERVICE_GET == 1) {
                return;
            } else if (Contants.SERVICE_GET == 0) {
                new Handler(Looper.getMainLooper()).postDelayed(new Runnable() {
                    @Override
                    public void run() {
                        Log.d(TAG, "getProcesses hunantv: startBootloaderService－－－");
                        Intent intentService = new Intent(context, BootloaderService.class);
                        context.startService(intentService);
                    }
                }, 5000);
                return;
            }
        } else if (findActivityCount == 20 && Contants.SERVICE_GET == 0) {

            Log.d(TAG, "getProcesses 20: 去打开service－－－");
            Intent intentService = new Intent(context, BootloaderService.class);
            context.startService(intentService);

            return;
        }

        new Handler(Looper.getMainLooper()).postDelayed(new Runnable() {
            @Override
            public void run() {
                getProcesses(context);
            }
        }, 5000);
    }

    private boolean isServiceRunning(Context context) {
        ActivityManager manager = (ActivityManager) context.getSystemService(ACTIVITY_SERVICE);
        for (ActivityManager.RunningServiceInfo service : manager.getRunningServices(Integer.MAX_VALUE)) {
            if ("com.iptv.hn.service.BootloaderService".equals(service.service.getClassName())) {
                return true;
            }
        }
        return false;
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
}
