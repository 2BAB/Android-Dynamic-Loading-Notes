package com.example.loadpluginresource;

import android.app.Application;
import android.app.Instrumentation;
import android.content.Context;

import java.io.File;
import java.lang.reflect.Field;
import java.lang.reflect.Method;

import dalvik.system.DexClassLoader;

/**
 * Created by 2bab on 2016/11/15.
 */

public class MyApplication extends Application {

    public static DexClassLoader dexClassLoader;

    @Override
    protected void attachBaseContext(Context base) {
        super.attachBaseContext(base);

        installDex();
        hookInstrumentation();
    }

    private void installDex() {
        File optimizedDexOutputPath = new File("/system/dex/" + "4-Plugin.apk");// 外部路径
        File dexOutputDir = this.getDir("dex", 0);// 无法直接从外部路径加载.dex文件，需要指定APP内部路径作为缓存目录（.dex文件会被解压到此目录）
        dexClassLoader = new DexClassLoader(
                optimizedDexOutputPath.getAbsolutePath(),
                dexOutputDir.getAbsolutePath(),
                null,
                getClassLoader());
    }

    private void hookInstrumentation() {
        try {
            Class<?> activityThreadClass = Class.forName("android.app.ActivityThread");
            Method currentActivityThreadMethod = activityThreadClass.getDeclaredMethod("currentActivityThread");
            currentActivityThreadMethod.setAccessible(true);
            Object currentActivityThread = currentActivityThreadMethod.invoke(null);

            // 拿到原始的 mInstrumentation字段
            Field mInstrumentationField = activityThreadClass.getDeclaredField("mInstrumentation");
            mInstrumentationField.setAccessible(true);
            Instrumentation mInstrumentation = (Instrumentation) mInstrumentationField.get(currentActivityThread);

            //如果没有注入过，就执行替换
            if (!(mInstrumentation instanceof CustomInstrumentation)) {
                CustomInstrumentation pluginInstrumentation = new CustomInstrumentation(mInstrumentation, dexClassLoader);
                mInstrumentationField.set(currentActivityThread, pluginInstrumentation);
            }
        } catch (Exception e) {
            e.printStackTrace();
        }
    }
}
