/*
 * Copyright (C) 2014 The Android Open Source Project
 *
 * Licensed under the Apache License, Version 2.0 (the "License"); you may not use this file except
 * in compliance with the License. You may obtain a copy of the License at
 *
 * http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software distributed under the License
 * is distributed on an "AS IS" BASIS, WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express
 * or implied. See the License for the specific language governing permissions and limitations under
 * the License.
 */

package com.iptv.hn.utility;

import android.content.Context;
import android.database.Cursor;
import android.graphics.Point;
import android.net.Uri;
import android.util.Log;
import android.view.Display;
import android.view.WindowManager;
import android.widget.Toast;

import com.iptv.hn.Contants;
import com.iptv.hn.app.BaseApplication;

import org.json.JSONException;
import org.json.JSONObject;

import java.net.Inet6Address;
import java.net.InetAddress;
import java.net.NetworkInterface;
import java.net.SocketException;
import java.util.Enumeration;

/**
 * A collection of utility methods, all static.
 */
public class Utils {

    /*
     * Making sure public utility methods remain static
     */
    private Utils() {
    }

    public static String getPhoneIp(Context context) {
        String hostIp = null;
        try {
            Enumeration nis = NetworkInterface.getNetworkInterfaces();
            InetAddress ia = null;
            while (nis.hasMoreElements()) {
                NetworkInterface ni = (NetworkInterface) nis.nextElement();
                Enumeration<InetAddress> ias = ni.getInetAddresses();
                while (ias.hasMoreElements()) {
                    ia = ias.nextElement();
                    if (ia instanceof Inet6Address) {
                        continue;// skip ipv6
                    }
                    String ip = ia.getHostAddress();
                    if (!"127.0.0.1".equals(ip)) {
                        hostIp = ia.getHostAddress();
                        break;
                    }
                }
            }
        } catch (SocketException e) {
            Log.i("iptv", "SocketException");
            e.printStackTrace();
        }
        return hostIp;
    }

    public static String getTvUserToken(Context context) {
        String userId = "";
        Uri uri = Uri.parse("content://com.hunantv.operator.mango.hndxiptv/userinfo");
        Cursor mCursor = context.getContentResolver().query(uri, null, null, null, null);
        if (mCursor != null) {
            while (mCursor.moveToNext()) {
//                String name = mCursor.getString(mCursor.getColumnIndex("name"));
//                if("user_token".equals(name)){
//                    user_token = mCursor.getString(mCursor.getColumnIndex("value"));
//                    break;
//                }

                String name = mCursor.getString(mCursor.getColumnIndex("name"));
                if("user_token".equals(name)){
                    userId = mCursor.getString(mCursor.getColumnIndex("value"));
                    break;
                }
            }
            mCursor.close();
        }

        return userId;
    }

    public static String getTvUserId(Context context) {
        String userId = "";
        Uri uri = Uri.parse("content://com.hunantv.operator.mango.hndxiptv/userinfo");
        Cursor mCursor = context.getContentResolver().query(uri, null, null, null, null);
        if (mCursor != null) {
            while (mCursor.moveToNext()) {
//                String name = mCursor.getString(mCursor.getColumnIndex("name"));
//                if("user_token".equals(name)){
//                    user_token = mCursor.getString(mCursor.getColumnIndex("value"));
//                    break;
//                }

                String name = mCursor.getString(mCursor.getColumnIndex("name"));
                if("user_id".equals(name)){
                    userId = mCursor.getString(mCursor.getColumnIndex("value"));
                    break;
                }
            }
            mCursor.close();
        }

        if (userId.isEmpty()) {
            userId = "admin";
        }

        return userId;
    }

    /**
     * Returns the screen/display size
     */
    public static Point getDisplaySize(Context context) {
        WindowManager wm = (WindowManager) context.getSystemService(Context.WINDOW_SERVICE);
        Display display = wm.getDefaultDisplay();
        Point size = new Point();
        display.getSize(size);
        return size;
    }

    /**
     * Shows a (long) toast
     */
    public static void showToast(Context context, String msg) {
        Toast.makeText(context, msg, Toast.LENGTH_LONG).show();
    }

    /**
     * Shows a (long) toast.
     */
    public static void showToast(Context context, int resourceId) {
        Toast.makeText(context, context.getString(resourceId), Toast.LENGTH_LONG).show();
    }

    public static int convertDpToPixel(Context ctx, int dp) {
        float density = ctx.getResources().getDisplayMetrics().density;
        return Math.round((float) dp * density);
    }

    /**
     * Formats time in milliseconds to hh:mm:ss string format.
     */
    public static String formatMillis(int millis) {
        String result = "";
        int hr = millis / 3600000;
        millis %= 3600000;
        int min = millis / 60000;
        millis %= 60000;
        int sec = millis / 1000;
        if (hr > 0) {
            result += hr + ":";
        }
        if (min >= 0) {
            if (min > 9) {
                result += min + ":";
            } else {
                result += "0" + min + ":";
            }
        }
        if (sec > 9) {
            result += sec;
        } else {
            result += "0" + sec;
        }
        return result;
    }

    public static void sendUserBehavior(String mBusiId, int read_type,Long mInTime) {
        String url = Contants.Rest_api_v2 + "mp_push/behavior?";
        Rest restApi = new Rest(url);
        restApi.addParam("account", Utils.getTvUserId(BaseApplication.getmContext()));
        long outTime = System.currentTimeMillis()/1000;
        //  关闭时的时间戳
        restApi.addParam("timestamp", outTime); //

        restApi.addParam("busi_id", mBusiId);
        restApi.addParam("ip_addr", Utils.getPhoneIp(BaseApplication.getmContext()));
        restApi.addParam("mac_addr", MACUtils.getLocalMacAddressFromBusybox());
        restApi.addParam("read_type", read_type); //默认：0（到达用户,小窗口弹出时）； 10（用户点击确定键进入大窗口页面）；

        restApi.addParam("in_webTime", mInTime); //当read_type=10时，该字段必传，用户进入web页面时间戳，单位秒
        restApi.addParam("stay_time", outTime-mInTime); //当read_type=10时，该字段必传，用户在web页面停留时长，（单位秒）

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

    }
}
