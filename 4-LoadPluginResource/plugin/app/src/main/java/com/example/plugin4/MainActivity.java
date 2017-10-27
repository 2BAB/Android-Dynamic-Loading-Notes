package com.example.plugin4;

import android.content.Context;
import android.content.res.AssetManager;
import android.content.res.Resources;
import android.os.Bundle;
import android.support.v7.app.AppCompatActivity;

import java.lang.reflect.Field;
import java.lang.reflect.Method;

public class MainActivity extends AppCompatActivity {
    private Resources pluginR;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);

        // 如果不 Hook mResource，也可以直接 getPluginR 来获取 values 的资源，但是无法装载 Layout
        // getPluginR().getString(R.string.plugin_string_res); //

        // Fragment 或者 自定义 View 等需要自己 Inflate 的也支持
        /*int bundleLayoutId = R.layout.activity_main;
        View bundleView = LayoutInflater.from(this).inflate(bundleLayoutId, null);
        setContentView(bundleView);*/

        setContentView(R.layout.activity_main);
    }

    @Override
    protected void attachBaseContext(Context newBase) {
        try {
            Field field = newBase.getClass().getDeclaredField("mResources");
            field.setAccessible(true);
            field.set(newBase, getPluginR(newBase));
        } catch (Exception e) {
            e.printStackTrace();
        }
        super.attachBaseContext(newBase);
    }

    public Resources getPluginR(Context context) {
        if (pluginR != null) {
            return pluginR;
        }
        try {
            String dexPath = "/sdcard/dex/" + "4-Plugin.apk";
            AssetManager assetManager = AssetManager.class.newInstance();
            Method addAssetPath = assetManager.getClass().getMethod("addAssetPath", String.class);
            addAssetPath.invoke(assetManager, dexPath);
            pluginR = new Resources(assetManager, context.getResources().getDisplayMetrics(), context.getResources().getConfiguration());

            //独立使用Resource时（不hook mResource）
            //Resources origin = super.getResources();
            //pluginR = new Resources(assetManager, origin.getDisplayMetrics(), origin.getConfiguration());
            return pluginR;
        } catch (Exception e) {
            e.printStackTrace();
            return null;
        }
    }
}
