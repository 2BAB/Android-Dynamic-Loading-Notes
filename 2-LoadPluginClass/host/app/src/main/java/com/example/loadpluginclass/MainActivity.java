package com.example.loadpluginclass;

import android.os.Bundle;
import android.support.v7.app.AppCompatActivity;
import android.widget.Toast;

import java.io.File;

import dalvik.system.DexClassLoader;

public class MainActivity extends AppCompatActivity {

    private DexClassLoader dexClassLoader;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_main);

        installDex();
        loadPluginClassTest();
    }

    private void installDex() {
        File optimizedDexOutputPath = new File("/system/dex/" + "2-LoadPluginClass.apk");// 外部路径
        File dexOutputDir = this.getDir("dex", 0);// 无法直接从外部路径加载.dex文件，需要指定APP内部路径作为缓存目录（.dex文件会被解压到此目录）
        dexClassLoader = new DexClassLoader(
                optimizedDexOutputPath.getAbsolutePath(),
                dexOutputDir.getAbsolutePath(),
                null,
                getClassLoader());
    }

    private void loadPluginClassTest() {
        try {
            Class libProviderClazz = dexClassLoader.loadClass("com.example.loadpluginclass.PluginClassWillBeLoad");
            System.out.println("PluginClassWillBeLoad getClassloader:" + libProviderClazz.getClassLoader());
            System.out.println("LoadPluginClassTestInterface getClassloader:" + LoadPluginClassTestInterface.class.getClassLoader());

            LoadPluginClassTestInterface dexInterface = (LoadPluginClassTestInterface) libProviderClazz.newInstance();
            Toast.makeText(this, dexInterface.getPiValue() + "", Toast.LENGTH_LONG).show();
        } catch (Exception e) {
            e.printStackTrace();
        }
    }
}
