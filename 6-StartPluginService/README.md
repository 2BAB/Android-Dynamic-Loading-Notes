# Android插件化笔记-6-StartPluginService

上节学习到了插件化资源 ID 冲突的问题和解法，本节主要讨论 Service 的插件化启动。按照本系列的尿性，肯定要简单易搞，所以预注册 Service 是本节的讨论前提。具体原因在 Activity 那节写了，但是需要额外说明的是其实 Service 由于量少且新增少，是比较少做[复杂插件化](http://weishu.me/2016/05/11/understand-plugin-framework-service/)方案的。

## Service 的启动流程

推荐看这篇 [startService启动过程分析](http://gityuan.com/2016/03/06/start-service/)：

![](http://gityuan.com/images/android-service/am/Seq_start_service.png)

![](http://gityuan.com/images/android-service/start_service/start_service_processes.jpg)

其实由于我们提前注册了 Service，没有了各种校验问题，所以只需要跟 Activity 一样注入 ClassLoader 去加载对应的模块代码就可以启动 Service。但是 Service 并没有像 Activity 那样方便的切入点（Instrumention）：


[->ActivityThread.java]

``` java
private void handleCreateService(CreateServiceData data) {
    // If we are getting ready to gc after going to the background, well
    // we are back active so skip it.
    unscheduleGcIdler();

    LoadedApk packageInfo = getPackageInfoNoCheck(
                data.info.applicationInfo, data.compatInfo);
    Service service = null;
    try {
        java.lang.ClassLoader cl = packageInfo.getClassLoader();
        service = (Service) cl.loadClass(data.info.name).newInstance();
    } catch (Exception e) {
        if (!mInstrumentation.onException(service, e)) {
            throw new RuntimeException("Unable to instantiate service " + data.info.name + ": " + e.toString(), e);
        }
    }
    ...
}

public final LoadedApk getPackageInfoNoCheck(ApplicationInfo ai, CompatibilityInfo compatInfo) {
    return getPackageInfo(ai, compatInfo, null, false, true, false);
}


private LoadedApk getPackageInfo(ApplicationInfo aInfo, CompatibilityInfo compatInfo, 
        ClassLoader baseLoader, boolean securityViolation, boolean includeCode, boolean registerPackage) {
    final boolean differentUser = (UserHandle.myUserId() != UserHandle.getUserId(aInfo.uid));
    synchronized (mResourcesManager) {
        WeakReference<LoadedApk> ref;
        if (differentUser) {
            // Caching not supported across users
            ref = null;
        } else if (includeCode) {
            ref = mPackages.get(aInfo.packageName);
        } else {
            ref = mResourcePackages.get(aInfo.packageName);
        }
        
        LoadedApk packageInfo = ref != null ? ref.get() : null;
        if (packageInfo == null || (packageInfo.mResources != null && !packageInfo.mResources.getAssets().isUpToDate())) {
            if (localLOGV) Slog.v(TAG, (includeCode ? "Loading code package "
                        : "Loading resource-only package ") + aInfo.packageName
                        + " (in " + (mBoundApplication != null
                                ? mBoundApplication.processName : null)
                        + ")");
            packageInfo =
                    new LoadedApk(this, aInfo, compatInfo, baseLoader,
                            securityViolation, includeCode &&
                            (aInfo.flags&ApplicationInfo.FLAG_HAS_CODE) != 0, registerPackage);

            if (mSystemThread && "android".equals(aInfo.packageName)) {
                    packageInfo.installSystemApplicationInfo(aInfo,
                            getSystemContext().mPackageInfo.getClassLoader());
            }

            if (differentUser) {
                // Caching not supported across users
            } else if (includeCode) {
                mPackages.put(aInfo.packageName,
                        new WeakReference<LoadedApk>(packageInfo));
            } else {
                mResourcePackages.put(aInfo.packageName,
                        new WeakReference<LoadedApk>(packageInfo));
            }
        }
        return packageInfo;
    }
}
```

既然没有直接有效的切入点，那么其他的插件化方案都是怎么做的？除了文初 weishu 的那个高级方法以外，还有一种比较实在并且通吃的解法。

## 动态插入 Element - Dex 动态性的通解

Dex 动态载入的原理其实是从 Google MultiDex 方案出来后大家才敢投入研究和使用的，具体参考[Android分包原理](http://souly.cn/%E6%8A%80%E6%9C%AF%E5%8D%9A%E6%96%87/2016/02/25/android%E5%88%86%E5%8C%85%E5%8E%9F%E7%90%86/)这篇文章。

在了解了有这样的方案之后，很多人纷纷表示这可以用来做插件化和热修复，类似的博客有大头鬼的[Android热更新实现原理](http://blog.csdn.net/lzyzsd/article/details/49843581)。

看完这两篇之后，其实应该了解的差不多了。**本质上这种 Dex 载入的方案就是把代码载入到自定义的 ClassLoader 中，跟之前写的 Activity Hook 方案异曲同工。只不过这是一种比较彻底、方便，一次性解决了所有需要注入 ClassLoader 的地方。不仅可以用来启动 Service，也可以用来启动 Activity（当然前提是你预注册了）。**

具体代码 Demo 请参考文初的链接。
## Demo Usage

1. 在插件工程 `./gradlew assembleDebug` 打出插件
2. 导入插件到手机指定目录（这个目录是自己随便指定的，跟 Demo 代码里的加载路径一致即可）：`mv app/build/outputs/apk/app-debug.apk app/build/outputs/apk/6-Plugin.apk && adb push app/build/outputs/apk/6-Plugin.apk /system/dex/`
3. 在宿主工程 `./gradlew installDebug` 打包并安装宿主 APK
4. 打开宿主 App，查看效果
5. ![屏幕快照 2017-03-13 21.06.47](http://engineering-blog-2bab.qiniudn.com/%E5%B1%8F%E5%B9%95%E5%BF%AB%E7%85%A7%202017-03-13%2021.06.47.png)
6. ![](http://engineering-blog-2bab.qiniudn.com/dexElement.gif)




## 参考资料

本系列为笔记文，文中有大量的源码解析都是引用的其他作者的成果，本文参考资料均已在文中给出链接。

