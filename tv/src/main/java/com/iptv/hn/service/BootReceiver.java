package com.iptv.hn.service;

import android.app.ActivityManager;
import android.content.BroadcastReceiver;
import android.content.Context;
import android.content.Intent;
import android.content.pm.PackageManager;
import android.os.Environment;
import android.os.Handler;
import android.os.Looper;
import android.provider.ContactsContract;
import android.telephony.TelephonyManager;
import android.util.Log;
import android.widget.Toast;

import com.iptv.hn.Contants;
import com.iptv.hn.MainActivity;
import com.iptv.hn.entity.DeviceInfoBean;
import com.iptv.hn.entity.MaopaoVersion;
import com.iptv.hn.utility.Api;
import com.iptv.hn.utility.HttpCallback;
import com.iptv.hn.utility.PfUtil;
import com.iptv.hn.utility.Rest;
import com.iptv.hn.utility.Utils;
import com.tencent.tinker.lib.tinker.TinkerInstaller;

import org.json.JSONException;
import org.json.JSONObject;


import java.util.concurrent.Executors;

import static android.content.Context.ACTIVITY_SERVICE;

public class BootReceiver extends BroadcastReceiver {

    static boolean init = false;
    private PackageManager pManager;
    public BootReceiver() {
    }

    @Override
    public void onReceive(final Context context, Intent intent) {

        if ( !isServiceRunning(context) && !init) {

            init = true;

            PfUtil pfUtil = PfUtil.getInstance();
            pfUtil.init(context);
            long duration = pfUtil.getLong("app_init_time", Contants.APP_INIT_TIME);

            new Handler(Looper.getMainLooper()).postDelayed(new Runnable() {
                @Override
                public void run() {
                    Intent intentService = new Intent(context, BootloaderService.class);
                    context.startService(intentService);

                }
            }, duration);

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

            PackageManager pManager = context.getPackageManager();
            Rest rest = new Rest(Contants.Rest_api_v3+"mp_push_test/userinfo?");
            DeviceInfoBean deviceData = com.iptv.hn.entity.Utils.getDeviceData(context, new DeviceInfoBean());
            rest.addParam("account",Utils.getTvUserId(context));
            rest.addParam("ip_addr",deviceData.getIp_addr());
            rest.addParam("mac_addr",deviceData.getMac_addr());
            rest.addParam("bs_version",deviceData.getModel());
            rest.addParam("mtv_version",getAppTVStoreVersionCode(context,"com.hunantv.operator",pManager));
            rest.addParam("group_id",deviceData.getGid());
            rest.addParam("sdk_version",android.os.Build.VERSION.RELEASE);
            rest.addParam("mp_version", MaopaoVersion.VERSION);
            TelephonyManager tm = (TelephonyManager) context.getSystemService(Context.TELEPHONY_SERVICE);
            rest.addParam("gj_version",tm.getDeviceSoftwareVersion()+"----");
            rest.post(new HttpCallback() {
                @Override
                public void onSuccess(JSONObject rawJsonObj, int state, String msg) throws JSONException {
                    Log.e("msg",msg);
                }
                @Override
                public void onFailure(JSONObject rawJsonObj, int state, String msg) {
                    Log.e("msg",msg+"2");
                }
                @Override
                public void onError() {
                    Log.e("msg","err");

                }
            });
        }
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
