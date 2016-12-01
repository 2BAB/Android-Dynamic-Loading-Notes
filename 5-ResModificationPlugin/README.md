# Android插件化笔记-5-ResModificationPlugin

上节学习到「各插件构造各自的 `Resource` 对象，各个插件的资源互不影响」，本节使用另外一种方案——「所有插件的资源都加载到一个 `AssetManager`，全局可用」。

单一 Resource（AssetManager）的方案，主要问题在于资源 ID 冲突，解决的方案大体上分三种：

1. 修改 AAPT 的源码
2. 修改 AAPT 的生成产物（R.java，resource.arsc，各类 xml 包括 layout）
3. 使用 public.xml 手动设置 padding

其中方案 1 出现的较早，原理也比较简单，修改的部分不多，携程的 [DynamicApk](https://github.com/CtripMobile/DynamicAPK/tree/master/caapt) 等开源项目都在使用。而方案 2 则鲜为人知，但是 [Small](https://github.com/wequick/Small) 项目给我们做了一个完整的实例，**本节的 Gradle 插件就是基于 Small 的源码「抽离 + 修改」而来**。方案 3 不涉及到打包流程改动，在此不做阐释。

## 资源的打包过程

这里引用罗老师的一篇博文：

> 一. 解析AndroidManifest.xml
> 
> 二. 添加被引用资源包
> 
> 三. 收集资源文件
> 
> 四. 将收集到的资源增加到资源表
> 
> 五. 编译values类资源
> 
> 六. 给Bag资源分配ID
> 
> 七. 编译Xml资源文件
> 
> 八. 生成资源符号
> 
> 九. 生成资源索引表
> 
> 十. 编译AndroidManifest.xml文件
> 
> 十一. 生成R.java文件
> 
> 十二. 打包APK文件
> 

显然，我们的插入点应该是 11-12 步中间（这废话啊），然后我们来看一个 Apk 打包过程中，Gradle 的哪个任务对应了这个插入点（注意，这里以 Debug 打包为例）：


> :app:preBuild UP-TO-DATE
> 
> :app:preDebugBuild UP-TO-DATE
> 
> :app:checkDebugManifest
> 
> :app:preReleaseBuild UP-TO-DATE
> 
> ...
> 
> :app:prepareDebugDependencies
> 
> :app:compileDebugAidl UP-TO-DATE
> 
> :app:compileDebugRenderscript UP-TO-DATE
> 
> :app:generateDebugBuildConfig UP-TO-DATE
> 
> :app:generateDebugResValues UP-TO-DATE
> 
> :app:generateDebugResources UP-TO-DATE
> 
> :app:mergeDebugResources UP-TO-DATE
> 
> :app:processDebugManifest UP-TO-DATE
> 
> :app:processDebugResources UP-TO-DATE
> 
////////上面是 Resource 处理 ////////////这里就是分割点////////////////下面是 Java Source 处理/////////
> 
> :app:generateDebugSources UP-TO-DATE 
> 
> :app:incrementalDebugJavaCompilationSafeguard UP-TO-DATE
> 
> :app:compileDebugJavaWithJavac
Incremental compilation of 2 classes completed in 0.737 secs.
> 
> :app:compileDebugNdk UP-TO-DATE
> 
> ...
> 
> :app:transformResourcesWithMergeJavaResForDebug UP-TO-DATE
> 
> :app:validateSigningDebug
> 
> :app:packageDebug
> 
> :app:assembleDebug

可以看到 Resource 的处理和 Java 文件的处理有一个比较明晰的分割处，所以我们就在这个地方修改 AAPT 的生成物。

## Hook

插件打包依赖于我们的打包插件：

``` groovy
// project's  build.gradle
classpath 'com.example.gradle:res-modification-plugin:1.0.1-SNAPSHOT' 

// app's build.gradle
apply plugin: 'res-modification' 
```

Gradle 插件需要注入的点：

``` groovy
@Override
void apply(Project project) {
    this.project = project

    project.afterEvaluate {
        def processDebugResources = (ProcessAndroidResources) project.tasks['processDebugResources']
         
        // 大神阿永（https://github.com/lomanyong）的提示，防止 processDebugResources 因为 Up-To-Data 而跳过
        processDebugResources.outputs.upToDateWhen { false }
        
        // 注入点
        processDebugResources.doLast { ProcessAndroidResources i ->
            println "inject point!"
            hookAapt(i)
        }
    }
}
```

实现资源分区的的大致流程（详情请查看源码）：

``` groovy
private def hookAapt(ProcessAndroidResources aaptTask) {

    // Unpack resources.ap_
    File apFile = aaptTask.packageOutputFile
    FileTree apFiles = project.zipTree(apFile)
    File unzipApDir = new File(apFile.parentFile, 'ap_unzip')
    unzipApDir.delete()
    project.copy {
        from apFiles
        into unzipApDir

        include 'AndroidManifest.xml'
        include 'resources.arsc'
        include 'res/**/*'
    }

    // Modify assets
    File symbolFile = new File(aaptTask.textSymbolOutputDir, 'R.txt')
    prepareSplit(symbolFile)
    File sourceOutputDir = aaptTask.sourceOutputDir
    File rJavaFile = new File(sourceOutputDir, "com/example/plugin5/R.java")
    def rev = project.android.buildToolsRevision
    int noResourcesFlag = 0
    def filteredResources = new HashSet()
    def updatedResources = new HashSet()


    Aapt aapt = new Aapt(unzipApDir, rJavaFile, symbolFile, rev)
    if (this.retainedTypes != null && this.retainedTypes.size() > 0) {
        aapt.filterResources(this.retainedTypes, filteredResources)
        println "[${project.name}] split library res files..."

        aapt.filterPackage(this.retainedTypes, this.packageId, this.idMaps, null,
                this.retainedStyleables, updatedResources)

        println "[${project.name}] slice asset package and reset package id..."

        String pkg = "com.example.plugin5"
        // Overwrite the aapt-generated R.java with full edition
        rJavaFile.delete()
        aapt.generateRJava(rJavaFile, pkg, this.allTypes, this.allStyleables)


        println "[${project.name}] split library R.java files..."
    } else {
        println 'No Resource To Modify'
    }


    String aaptExe = aaptTask.buildTools.getPath(BuildToolInfo.PathId.AAPT)

    // Delete filtered entries.
    // Cause there is no `aapt update' command supported, so for the updated resources
    // we also delete first and run `aapt add' later.
    filteredResources.addAll(updatedResources)
    ZipUtils.with(apFile).deleteAll(filteredResources)

    // Re-add updated entries.
    // $ aapt add resources.ap_ file1 file2 ...
    project.exec {
        executable aaptExe
        workingDir unzipApDir
        args 'add', apFile.path
        args updatedResources
        
        // store the output instead of printing to the console
        // standardOutput = new ByteArrayOutputStream()
    }
}
```




这样，我们就可以在 Plugin 的 Activity 里实现「宿主+插件」的资源加载：

``` java
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
```

## Demo Usage

1. 在 Gradle 插件工程执行 `./gradlew publishToMavenLocal`，使得 Gradle 插件可以被我们的 插件 App 工程找到并依赖
2. 在插件工程 `./gradlew assembleDebug` 打出插件
3. 导入插件到手机指定目录（这个目录是自己随便指定的，跟 Demo 代码里的加载路径一致即可）：`mv app-debug.apk 5-Plugin.apk && adb push 5-Plugin.apk /system/dex/`
4. 在宿主工程 `./gradlew installDebug` 打包并安装宿主 APK
5. 打开宿主 App，查看效果

## 参考资料

本系列为笔记文，文中有大量的源码解析都是引用的其他作者的成果，详见下方参考资料。

- [ Android应用程序资源的编译和打包过程分析](http://blog.csdn.net/luoshengyang/article/details/8744683)
- [插件化-资源处理](http://www.jianshu.com/p/96d5b83ca26c)
- [Small](https://github.com/wequick/Small)
