package com.example.multiclassloader;

import dalvik.system.DexClassLoader;

/**
 * Created by 2bab on 2017/9/30.
 */

public class BundleClassloader extends DexClassLoader {

    public BundleClassloader(String dexPath, String optimizedDirectory, String librarySearchPath, ClassLoader parent) {
        super(dexPath, optimizedDirectory, librarySearchPath, parent);
    }

    @Override
    public Class<?> findClass(String name) throws ClassNotFoundException {
        return super.findClass(name);
    }
}
