package com.crossbowffs.soundcloudadaway;

import android.app.Activity;
import android.content.Context;
import android.content.Intent;
import android.os.Build;
import de.robv.android.xposed.*;
import de.robv.android.xposed.callbacks.XC_LoadPackage;

import java.io.File;
import java.lang.reflect.Method;

public class Hook implements IXposedHookLoadPackage {
    private static String getPackageVersion(XC_LoadPackage.LoadPackageParam lpparam) {
        try {
            Class<?> parserCls = XposedHelpers.findClass("android.content.pm.PackageParser", lpparam.classLoader);
            Object parser = parserCls.newInstance();
            File apkPath = new File(lpparam.appInfo.sourceDir);
            Object pkg = XposedHelpers.callMethod(parser, "parsePackage", apkPath, 0);
            String versionName = (String)XposedHelpers.getObjectField(pkg, "mVersionName");
            int versionCode = XposedHelpers.getIntField(pkg, "mVersionCode");
            return String.format("%s (%d)", versionName, versionCode);
        } catch (Throwable e) {
            return null;
        }
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

    private static void findAndHookMethod(String className, ClassLoader classLoader, String methodName, Object... parameterTypesAndCallback) {
        Xlog.i("Hooking %s#%s()", className, methodName);
        try {
            XposedHelpers.findAndHookMethod(className, classLoader, methodName, parameterTypesAndCallback);
        } catch (Throwable e) {
            Xlog.e("Failed to hook %s#%s()", className, methodName, e);
        }
    }

    private static boolean isStreamAd(Object streamItem) {
        return (Boolean)XposedHelpers.callMethod(streamItem, "isAd") ||
               (Boolean)XposedHelpers.callMethod(streamItem, "isPromoted") ||
               (Boolean)XposedHelpers.callMethod(streamItem, "isUpsell");
    }

    private static void blockPlayQueueAds(XC_LoadPackage.LoadPackageParam lpparam) {
        // Block audio and video ads during playback
        findAndHookMethod(
            "com.soundcloud.android.ads.AdsOperations", lpparam.classLoader,
            "insertAudioAd",
            "com.soundcloud.android.playback.TrackQueueItem",
            "com.soundcloud.android.ads.ApiAudioAd",
            new XC_MethodHook() {
                @Override
                protected void beforeHookedMethod(MethodHookParam param) throws Throwable {
                    Xlog.i("Blocked audio ad");
                    param.setResult(null);
                }
            });

        findAndHookMethod(
            "com.soundcloud.android.ads.AdsOperations", lpparam.classLoader,
            "insertVideoAd",
            "com.soundcloud.android.playback.TrackQueueItem",
            "com.soundcloud.android.ads.ApiVideoAd",
            new XC_MethodHook() {
                @Override
                protected void beforeHookedMethod(MethodHookParam param) throws Throwable {
                    Xlog.i("Blocked video ad");
                    param.setResult(null);
                }
            });
    }

    private static void blockStreamAds(XC_LoadPackage.LoadPackageParam lpparam) {
        // Block stream ads (e.g. promoted tracks, "install this app")
        findAndHookMethod(
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

        findAndHookMethod(
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
    }

    private static void blockOverlayAds(XC_LoadPackage.LoadPackageParam lpparam) {
        // Block overlay ads
        findAndHookMethod(
            "com.soundcloud.android.ads.AdOverlayController", lpparam.classLoader,
            "shouldDisplayAdOverlay",
            boolean.class,
            XC_MethodReplacement.returnConstant(false));
    }

    private static void blockAppboyPushNotifications(XC_LoadPackage.LoadPackageParam lpparam) {
        // Block Appboy push notifications (no, the notification settings
        // aren't enough)
        findAndHookMethod(
            "com.appboy.AppboyGcmReceiver", lpparam.classLoader,
            "onReceive",
            Context.class,
            Intent.class,
            XC_MethodReplacement.DO_NOTHING);
    }

    private static void blockAppboyInAppMessages(XC_LoadPackage.LoadPackageParam lpparam) {
        // Block Appboy in-app messages. An example is the "upgrade to
        // SoundCloud Go" popup that gets displayed when the app starts.
        findAndHookMethod(
            "com.soundcloud.android.analytics.appboy.RealAppboyWrapper", lpparam.classLoader,
            "registerInAppMessageManager",
            Activity.class,
            XC_MethodReplacement.DO_NOTHING);

        findAndHookMethod(
            "com.soundcloud.android.analytics.appboy.RealAppboyWrapper", lpparam.classLoader,
            "unregisterInAppMessageManager",
            Activity.class,
            XC_MethodReplacement.DO_NOTHING);
    }

    private static void enableOfflineContent(XC_LoadPackage.LoadPackageParam lpparam) {
        // Enables downloading music for offline listening
        findAndHookMethod(
            "com.soundcloud.android.configuration.FeatureOperations", lpparam.classLoader,
            "isOfflineContentEnabled",
            XC_MethodReplacement.returnConstant(true));
    }

    private static void enableDeveloperMode(XC_LoadPackage.LoadPackageParam lpparam) {
        // This gives you a cool developer menu if you slide from the right edge.
        // It's not actually useful for anything though (no, you can't upgrade
        // to Go for free, I've tried)

        // The return type is RxJava Observable<Boolean>, which is ProGuarded.
        // We can reliably find the return type from the method itself.
        Object observableTrue;
        try {
            Class<?> featureOpsCls = XposedHelpers.findClass("com.soundcloud.android.configuration.FeatureOperations", lpparam.classLoader);
            Method devMenuEnabledMethod = XposedHelpers.findMethodExact(featureOpsCls, "developmentMenuEnabled");
            Class<?> observableCls = devMenuEnabledMethod.getReturnType();
            observableTrue = XposedHelpers.callStaticMethod(observableCls, "just", new Class<?>[] {Object.class}, true);
        } catch (Throwable e) {
            Xlog.e("Failed to create Observable<Boolean>(true) object", e);
            return;
        }

        findAndHookMethod(
            "com.soundcloud.android.configuration.FeatureOperations", lpparam.classLoader,
            "developmentMenuEnabled",
            XC_MethodReplacement.returnConstant(observableTrue));

        findAndHookMethod(
            "com.soundcloud.android.configuration.FeatureOperations", lpparam.classLoader,
            "isDevelopmentMenuEnabled",
            XC_MethodReplacement.returnConstant(true));
    }

    @Override
    public void handleLoadPackage(final XC_LoadPackage.LoadPackageParam lpparam) throws Throwable {
        if (!"com.soundcloud.android".equals(lpparam.packageName)) {
            return;
        }

        printInitInfo(lpparam);

        blockPlayQueueAds(lpparam);
        blockStreamAds(lpparam);
        blockOverlayAds(lpparam);
        blockAppboyPushNotifications(lpparam);
        blockAppboyInAppMessages(lpparam);
        enableOfflineContent(lpparam);
        // enableDeveloperMode(lpparam);

        Xlog.i("SoundCloud AdAway initialization complete!");
    }
}
