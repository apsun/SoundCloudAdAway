package com.crossbowffs.soundcloudadaway;

import android.os.Build;
import de.robv.android.xposed.XC_MethodHook;
import de.robv.android.xposed.XC_MethodReplacement;
import de.robv.android.xposed.XposedBridge;
import de.robv.android.xposed.XposedHelpers;
import de.robv.android.xposed.callbacks.XC_LoadPackage;

import java.io.File;
import java.lang.reflect.Constructor;
import java.lang.reflect.Member;

public final class Xutil {
    private static XC_MethodReplacement sDoNothingAndLog = null;

    private Xutil() { }

    public static String getAppVersion(XC_LoadPackage.LoadPackageParam lpparam) {
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

    public static void printInitInfo(XC_LoadPackage.LoadPackageParam lpparam) {
        Xlog.i("Phone manufacturer: %s", Build.MANUFACTURER);
        Xlog.i("Phone model: %s", Build.MODEL);
        Xlog.i("Android version: %s", Build.VERSION.RELEASE);
        Xlog.i("Xposed bridge version: %d", XposedBridge.XPOSED_BRIDGE_VERSION);
        Xlog.i("App version: %s", getAppVersion(lpparam));
        Xlog.i("Module version: %s (%d)", BuildConfig.VERSION_NAME, BuildConfig.VERSION_CODE);
    }

    public static void findAndHookConstructor(String className, ClassLoader classLoader, Object... params) {
        Xlog.i("Hooking %s#<init>()");
        try {
            XposedHelpers.findAndHookConstructor(className, classLoader, params);
        } catch (Throwable e) {
            Xlog.e("Failed to hook %s#<init>()", className, e);
        }
    }

    public static void findAndHookMethod(String className, ClassLoader classLoader, String methodName, Object... params) {
        Xlog.i("Hooking %s#%s()", className, methodName);
        try {
            XposedHelpers.findAndHookMethod(className, classLoader, methodName, params);
        } catch (Throwable e) {
            Xlog.e("Failed to hook %s#%s()", className, methodName, e);
        }
    }

    public static String getMethodName(XC_MethodHook.MethodHookParam param) {
        Member method = param.method;
        String methodName;
        if (method instanceof Constructor) {
            methodName = "<init>";
        } else {
            methodName = method.getName();
        }
        Class<?> cls = method.getDeclaringClass();
        return String.format("%s#%s()", cls.getSimpleName(), methodName);
    }

    public static XC_MethodReplacement returnConstantAndLog(final Object value) {
        return new XC_MethodReplacement() {
            @Override
            protected Object replaceHookedMethod(MethodHookParam param) throws Throwable {
                Xlog.i("Overriding %s -> %s", getMethodName(param), value);
                return value;
            }
        };
    }

    public static XC_MethodReplacement doNothingAndLog() {
        if (sDoNothingAndLog == null) {
            sDoNothingAndLog = new XC_MethodReplacement() {
                @Override
                protected Object replaceHookedMethod(MethodHookParam param) throws Throwable {
                    Xlog.i("Blocking %s", getMethodName(param));
                    return null;
                }
            };
        }
        return sDoNothingAndLog;
    }
}
