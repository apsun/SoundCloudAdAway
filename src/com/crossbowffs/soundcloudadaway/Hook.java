package com.crossbowffs.soundcloudadaway;

import android.app.Notification;
import android.os.Build;
import de.robv.android.xposed.IXposedHookLoadPackage;
import de.robv.android.xposed.XC_MethodHook;
import de.robv.android.xposed.XposedBridge;
import de.robv.android.xposed.XposedHelpers;
import de.robv.android.xposed.callbacks.XC_LoadPackage;

import java.io.File;

public class Hook implements IXposedHookLoadPackage {
    private static String getPackageVersion(XC_LoadPackage.LoadPackageParam lpparam) {
        Class<?> parserCls = XposedHelpers.findClass("android.content.pm.PackageParser", lpparam.classLoader);
        Object parser;
        try {
            parser = parserCls.newInstance();
        } catch (InstantiationException e) {
            return null;
        } catch (IllegalAccessException e) {
            return null;
        }
        File apkPath = new File(lpparam.appInfo.sourceDir);
        Object pkg = XposedHelpers.callMethod(parser, "parsePackage", apkPath, 0);
        String versionName = (String)XposedHelpers.getObjectField(pkg, "mVersionName");
        int versionCode = XposedHelpers.getIntField(pkg, "mVersionCode");
        return String.format("%s (%d)", versionName, versionCode);
    }

    private static String urnToString(Object urn) {
        return (String)XposedHelpers.getObjectField(urn, "content");
    }

    private static Object getAudioAdUrn(Object audioAd) {
        return XposedHelpers.callMethod(audioAd, "getUrn");
    }

    private static Object getVideoAdUrn(Object videoAd) {
        return XposedHelpers.callMethod(videoAd, "getAdUrn");
    }

    private static boolean isStreamAd(Object streamItem) {
        return
            (Boolean)XposedHelpers.callMethod(streamItem, "isAd") ||
            (Boolean)XposedHelpers.callMethod(streamItem, "isPromoted") ||
            (Boolean)XposedHelpers.callMethod(streamItem, "isUpsell");
    }

    private static void printInitInfo(XC_LoadPackage.LoadPackageParam lpparam) {
        Xlog.i("SoundCloud AdAway initializing...");
        Xlog.i("Phone manufacturer: %s", Build.MANUFACTURER);
        Xlog.i("Phone model: %s", Build.MODEL);
        Xlog.i("Android version: %s", Build.VERSION.RELEASE);
        Xlog.i("Xposed bridge version: %d", XposedBridge.XPOSED_BRIDGE_VERSION);
        Xlog.i("SoundCloud APK version: %s", getPackageVersion(lpparam));
        Xlog.i("SoundCloud AdAway version: %s (%d)", BuildConfig.VERSION_NAME, BuildConfig.VERSION_CODE);
    }

    @Override
    public void handleLoadPackage(XC_LoadPackage.LoadPackageParam lpparam) throws Throwable {
        if (!"com.soundcloud.android".equals(lpparam.packageName)) {
            return;
        }

        // Possibly relevant classes:
        // AdsController
        // AdsOperations
        // AdFunctions
        // PlayQueueManager
        // PlayQueue
        // PlayQueueItem

        printInitInfo(lpparam);

        try {
            Xlog.i("Hooking AdsOperations.insertAudioAd()...");
            XposedHelpers.findAndHookMethod(
                "com.soundcloud.android.ads.AdsOperations", lpparam.classLoader,
                "insertAudioAd",
                "com.soundcloud.android.playback.TrackQueueItem",
                "com.soundcloud.android.ads.ApiAudioAd",
                new XC_MethodHook() {
                    @Override
                    protected void beforeHookedMethod(MethodHookParam param) throws Throwable {
                        Object urn = getAudioAdUrn(param.args[1]);
                        String urnStr = urnToString(urn);
                        Xlog.i("Blocked audio ad: %s", urnStr);
                        param.setResult(null);
                    }
                });

            Xlog.i("Hooking AdsOperations.insertVideoAd()...");
            XposedHelpers.findAndHookMethod(
                "com.soundcloud.android.ads.AdsOperations", lpparam.classLoader,
                "insertVideoAd",
                "com.soundcloud.android.playback.TrackQueueItem",
                "com.soundcloud.android.ads.ApiVideoAd",
                new XC_MethodHook() {
                    @Override
                    protected void beforeHookedMethod(MethodHookParam param) throws Throwable {
                        Object urn = getVideoAdUrn(param.args[1]);
                        String urnStr = urnToString(urn);
                        Xlog.i("Blocked video ad: %s", urnStr);
                        param.setResult(null);
                    }
                });

            Xlog.i("Hooking StreamAdapter.addItem(int, StreamItem)...");
            XposedHelpers.findAndHookMethod(
                "com.soundcloud.android.stream.StreamAdapter", lpparam.classLoader,
                "addItem",
                int.class,
                "com.soundcloud.android.stream.StreamItem",
                new XC_MethodHook() {
                    @Override
                    protected void beforeHookedMethod(MethodHookParam param) throws Throwable {
                        if (isStreamAd(param.args[1])) {
                            Xlog.i("Blocked stream ad");
                            param.setResult(null);
                        }
                    }
                });

            Xlog.i("Hooking StreamAdapter.addItem(StreamItem)...");
            XposedHelpers.findAndHookMethod(
                "com.soundcloud.android.stream.StreamAdapter", lpparam.classLoader,
                "addItem",
                "com.soundcloud.android.stream.StreamItem",
                new XC_MethodHook() {
                    @Override
                    protected void beforeHookedMethod(MethodHookParam param) throws Throwable {
                        if (isStreamAd(param.args[0])) {
                            Xlog.i("Blocked stream ad");
                            param.setResult(null);
                        }
                    }
                });

            // Technically this can be done in a better way by disabling the
            // GCM receiver that calls this method in the first place, but that
            // requires access to a Context object and I don't feel like finding
            // a good way to obtain one.
            Xlog.i("Hooking NotificationManagerCompat.notify()...");
            XposedHelpers.findAndHookMethod(
                "android.support.v4.app.NotificationManagerCompat", lpparam.classLoader,
                "notify", String.class, int.class, Notification.class,
                new XC_MethodHook() {
                    @Override
                    protected void beforeHookedMethod(MethodHookParam param) throws Throwable {
                        String tag = (String)param.args[0];
                        if ("appboy_notification".equals(tag)) {
                            Xlog.i("Blocked Appboy notification");
                            param.setResult(null);
                        }
                    }
                });
        } catch (Throwable e) {
            Xlog.e("Exception occurred while hooking methods", e);
            throw e;
        }

        Xlog.i("SoundCloud AdAway init successful!");
    }
}
