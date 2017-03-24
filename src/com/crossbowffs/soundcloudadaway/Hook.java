package com.crossbowffs.soundcloudadaway;

import android.app.Activity;
import android.content.Context;
import android.content.Intent;
import de.robv.android.xposed.IXposedHookLoadPackage;
import de.robv.android.xposed.XC_MethodHook;
import de.robv.android.xposed.XC_MethodReplacement;
import de.robv.android.xposed.XposedHelpers;
import de.robv.android.xposed.callbacks.XC_LoadPackage;

public class Hook implements IXposedHookLoadPackage {
    private static boolean isStreamAd(Object streamItem) {
        if ((Boolean)XposedHelpers.callMethod(streamItem, "isAd")) {
            Xlog.d("Blocked stream item: isAd() == true");
            return true;
        }

        if ((Boolean)XposedHelpers.callMethod(streamItem, "isPromoted")) {
            Xlog.d("Blocked stream item: isPromoted() == true");
            return true;
        }

        if ((Boolean)XposedHelpers.callMethod(streamItem, "isUpsell")) {
            Xlog.d("Blocked stream item: isUpsell() == true");
            return true;
        }

        return false;
    }

    private static void blockPlayQueueAds(XC_LoadPackage.LoadPackageParam lpparam) {
        // Block audio and video ads during playback
        Xutil.findAndHookMethod(
            "com.soundcloud.android.ads.AdsOperations", lpparam.classLoader,
            "insertAudioAd",
            "com.soundcloud.android.playback.TrackQueueItem",
            "com.soundcloud.android.ads.ApiAudioAd",
            Xutil.doNothingAndLog());

        Xutil.findAndHookMethod(
            "com.soundcloud.android.ads.AdsOperations", lpparam.classLoader,
            "insertVideoAd",
            "com.soundcloud.android.playback.TrackQueueItem",
            "com.soundcloud.android.ads.ApiVideoAd",
            Xutil.doNothingAndLog());
    }

    private static void blockStreamAds(XC_LoadPackage.LoadPackageParam lpparam) {
        // Block stream ads (e.g. promoted tracks, "install this app")
        Xutil.findAndHookMethod(
            "com.soundcloud.android.stream.StreamAdapter", lpparam.classLoader,
            "addItem",
            int.class,
            "com.soundcloud.android.stream.StreamItem",
            new XC_MethodHook() {
                @Override
                protected void beforeHookedMethod(MethodHookParam param) throws Throwable {
                    if (isStreamAd(param.args[1])) {
                        param.setResult(null);
                    }
                }
            });

        Xutil.findAndHookMethod(
            "com.soundcloud.android.stream.StreamAdapter", lpparam.classLoader,
            "addItem",
            "com.soundcloud.android.stream.StreamItem",
            new XC_MethodHook() {
                @Override
                protected void beforeHookedMethod(MethodHookParam param) throws Throwable {
                    if (isStreamAd(param.args[0])) {
                        param.setResult(null);
                    }
                }
            });
    }

    private static void blockOverlayAds(XC_LoadPackage.LoadPackageParam lpparam) {
        Xutil.findAndHookMethod(
            "com.soundcloud.android.ads.AdOverlayController", lpparam.classLoader,
            "shouldDisplayAdOverlay",
            boolean.class,
            Xutil.returnConstantAndLog(false));
    }

    private static void blockInterstitialAds(XC_LoadPackage.LoadPackageParam lpparam) {
        Xutil.findAndHookMethod(
            "com.soundcloud.android.ads.AdsOperations", lpparam.classLoader,
            "applyInterstitialAd",
            "com.soundcloud.android.ads.ApiInterstitial",
            "com.soundcloud.android.playback.TrackQueueItem",
            Xutil.doNothingAndLog());
    }

    private static void blockAppboyPushNotifications(XC_LoadPackage.LoadPackageParam lpparam) {
        // Block Appboy push notifications (no, the notification settings
        // aren't enough)
        Xutil.findAndHookMethod(
            "com.appboy.AppboyGcmReceiver", lpparam.classLoader,
            "onReceive",
            Context.class,
            Intent.class,
            XC_MethodReplacement.DO_NOTHING);
    }

    private static void blockAppboyInAppMessages(XC_LoadPackage.LoadPackageParam lpparam) {
        // Block Appboy in-app messages. An example is the "upgrade to
        // SoundCloud Go" popup that gets displayed when the app starts.
        Xutil.findAndHookMethod(
            "com.soundcloud.android.analytics.appboy.RealAppboyWrapper", lpparam.classLoader,
            "registerInAppMessageManager",
            Activity.class,
            XC_MethodReplacement.DO_NOTHING);

        Xutil.findAndHookMethod(
            "com.soundcloud.android.analytics.appboy.RealAppboyWrapper", lpparam.classLoader,
            "unregisterInAppMessageManager",
            Activity.class,
            XC_MethodReplacement.DO_NOTHING);
    }

    private static void enableOfflineContent(XC_LoadPackage.LoadPackageParam lpparam) {
        // Enables downloading music for offline listening
        Xutil.findAndHookMethod(
            "com.soundcloud.android.configuration.FeatureOperations", lpparam.classLoader,
            "isOfflineContentEnabled",
            XC_MethodReplacement.returnConstant(true));
    }

    @Override
    public void handleLoadPackage(final XC_LoadPackage.LoadPackageParam lpparam) throws Throwable {
        if (!"com.soundcloud.android".equals(lpparam.packageName)) {
            return;
        }

        Xlog.i("SoundCloud AdAway initializing...");
        Xutil.printInitInfo(lpparam);

        blockPlayQueueAds(lpparam);
        blockStreamAds(lpparam);
        blockOverlayAds(lpparam);
        blockInterstitialAds(lpparam);
        blockAppboyPushNotifications(lpparam);
        blockAppboyInAppMessages(lpparam);
        enableOfflineContent(lpparam);

        Xlog.i("SoundCloud AdAway initialization complete!");
    }
}
