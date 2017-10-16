package com.example.multiclassloader;

import android.app.Application;
import android.content.Context;

import java.lang.reflect.Field;

/**
 * Created by 2bab on 2017/9/30.
 */

public class MultiClassloaderApplication extends Application {

    @Override
    protected void attachBaseContext(Context base) {
        super.attachBaseContext(base);
        replacePathClassloaderParent(base);
    }

    @Override
    public void onCreate() {
        super.onCreate();
    }

    private void replacePathClassloaderParent(Context context) {
        ClassLoader pathClassloader = context.getClassLoader();
        DispatchClassloader dispatchClassloader = new DispatchClassloader(pathClassloader, context);
        final Class<?> clz = ClassLoader.class;
        try {
            final Field parentField = clz.getDeclaredField("parent");
            parentField.setAccessible(true);
            parentField.set(pathClassloader, dispatchClassloader);
        } catch (Exception e) {
            e.printStackTrace();
        }
    }

}
