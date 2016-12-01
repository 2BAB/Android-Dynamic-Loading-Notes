package com.example.plugin5;

import android.app.Activity;
import android.content.Context;
import android.content.res.AssetManager;
import android.content.res.Resources;
import android.os.Bundle;
import android.widget.TextView;

import java.lang.reflect.Field;
import java.lang.reflect.Method;

public class MainActivity extends Activity {

    private Resources allResources;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);

        // 使用插件的资源
        setContentView(R.layout.plugin_activity_main);
        TextView testTv = (TextView) findViewById(R.id.test_textview);

        // 使用宿主的资源
        String hostName = getResources().getString(
                getResources().getIdentifier("host_name", "string", "com.example.resmodification"));
        int hostNameColor = getResources().getColor(
                getResources().getIdentifier("host_name_color", "color", "com.example.resmodification"));
        testTv.setText(hostName);
        testTv.setTextColor(hostNameColor);
    }


    @Override
    protected void attachBaseContext(Context newBase) {
        hookResource(newBase);
        super.attachBaseContext(newBase);
    }

    /**
     * 宿主和插件的资源放在了一个 Resource 对象里，因为我们在打包时做了资源PP段分区，所以不会出现资源冲突的现象。
     * 不过目前只是在该 Activity 把我们构建的 Resource 对象 Set 进去了，所以也只能在当前 Context 的环境里同时
     * 访问到两个包的资源（我们仅做简单的测试）。一个成熟的插件化架构应该是把所有 Context 初始化的注入都做好（有多
     * 种实现手段）。
     */
    public Resources getPluginR(Context context) {
        if (allResources != null) {
            return allResources;
        }
        try {
            String dexPath = "/system/dex/" + "5-Plugin.apk";
            AssetManager assetManager = AssetManager.class.newInstance();
            Method addAssetPath = assetManager.getClass().getMethod("addAssetPaths", new Class[]{String[].class});
            String[] paths = new String[2];
            paths[0] = dexPath; // 插件 Asset
            paths[1] = context.getPackageResourcePath(); // 宿主的 Asset
            addAssetPath.invoke(assetManager, new Object[]{paths});
            allResources = new Resources(assetManager, context.getResources().getDisplayMetrics(), context.getResources().getConfiguration());

            return allResources;

        } catch (Exception e) {
            e.printStackTrace();
            return null;
        }
    }

    private void hookResource(Context newBase) {
        try {
            Field field = newBase.getClass().getDeclaredField("mResources");
            field.setAccessible(true);
            field.set(newBase, getPluginR(newBase));
        } catch (Exception e) {
            e.printStackTrace();
        }
    }
}
