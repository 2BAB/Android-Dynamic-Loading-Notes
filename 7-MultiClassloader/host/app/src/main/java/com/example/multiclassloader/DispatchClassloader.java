package com.example.multiclassloader;

import android.content.Context;
import android.os.Environment;

import java.io.File;

/**
 * Created by 2bab on 2017/9/30.
 */

public class DispatchClassloader extends ClassLoader {

    private BundleClassloader dexClassLoader;
    private Context context;
    private ClassLoader origin;

    public DispatchClassloader(ClassLoader origin, Context context) {
        super(origin.getParent());
        this.origin = origin;
        this.context = context;
        installDex();
    }

    private void installDex() {
        // 这里目前只装载了一个测试 Dex，正常情况下需要装载某个目录下的所有 dex 文件（通常每个 Bundle 有一个 Dex）
        File optimizedDexOutputPath = new File(
                Environment.getExternalStoragePublicDirectory(Environment.DIRECTORY_DOWNLOADS) + "/7-MultiClassloader.dex");
        File dexOutputDir = context.getDir("dex", 0);
        dexClassLoader = new BundleClassloader(
                optimizedDexOutputPath.getAbsolutePath(),
                dexOutputDir.getAbsolutePath(),
                null,
                origin);
    }

    @Override
    protected Class<?> findClass(String name) throws ClassNotFoundException {
        // 需要在这里遍历所有 Bundle 的 Classloader，或者用包名等来做查找分发
        Class<?> clz = dexClassLoader.findClass(name);
        return clz;
    }
}
