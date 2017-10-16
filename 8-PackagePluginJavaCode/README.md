# Android插件化笔记-8-PackagePluginJavaCode

## 背景

为什么打包还要讲？因为我发现大家都没说过这回事啊...而且有些开源的插件化框架并没有看到有仔细处理这块，导致自己研究的时候很困惑。具体来说，打包这块也是分两块，插件代码和插件资源的打包，这节先看插件代码的打包。

一般地，我们的插件，和普通的 Android Library 不同，是直接声明成 Application。因为只有这样，才能简单地借助原有的（Application）打包插件来打出 Dex 和资源编译。而打包 Application，对于插件而言，会打入很多不必要的二方、三方依赖。有人会说，用 provided 声明插件模块的依赖不就好了，但这仅仅只能是解决 .jar 的模块，.aar 的依赖是不支持 provided 的。所以我们要解决的问题，也就很明显了，实现一个通用的打包仲裁：

- Atlas、VirtualApk 等方案，都是把大部分的依赖打到主工程，插件本身只打入一些只有本插件才会用到的依赖；但他们的实现有所不同：
    - Atlas 添加了 bundleCompile、providedCompile 的 scope，插件方可以更友好地去声明依赖的形式；
    - VirtualApk 只是对主工程的依赖作分析，主工程有的就不打进去，主工程没有的就打进去，相对来讲比较粗暴；
- 不同的方案其实主要是自由度的选择不同，没有说好坏之分；
- 实现原理上，这里以 VirtualApk 为例，基于 Transform 来做，下面就看一下最基本的实现。


## Transform API

> To insert a transform into a build, you simply create a new class implementing one of the Transform interfaces, and register it with android.registerTransform(theTransform) or android.registerTransform(theTransform, dependencies).
> 
> A Transform that processes intermediary build artifacts.
For each added transform, a new task is created. The action of adding a transform takes care of handling dependencies between the tasks. This is done based on what the transform processes. The output of the transform becomes consumable by other transforms and these tasks get automatically linked together.
> 
> The Transform indicates what it applies to (content, scope) and what it generates (content).
> 
> A transform receives input as a collection TransformInput, which is composed of JarInputs and DirectoryInputs. Both provide information about the QualifiedContent.Scopes and QualifiedContent.ContentTypes associated with their particular content.
> 
> The output is handled by TransformOutputProvider which allows creating new self-contained content, each associated with their own Scopes and Content Types. The content handled by TransformInput/Output is managed by the transform system, and their location is not configurable.
> 
> It is best practice to write into as many outputs as Jar/Folder Inputs have been received by the transform. Combining all the inputs into a single output prevents downstream transform from processing limited scopes.
> 
> While it's possible to differentiate different Content Types by file extension, it's not possible to do so for Scopes. Therefore if a transform request a Scope but the only available Output contains more than the requested Scope, the build will fail.
If a transform request a single content type but the only available content includes more than the requested type, the input file/folder will contain all the files of all the types, but the transform should only read, process and output the type(s) it requested.
> 
> Additionally, a transform can indicate secondary inputs/outputs. These are not handled by upstream or downstream transforms, and are not restricted by type handled by transform. They can be anything.
> 
> It's up to each transform to manage where these files are, and to make sure that these files are generated before the transform is called. This is done through additional parameters when register the transform.
> 
> These secondary inputs/outputs allow a transform to read but not process any content. This can be achieved by having getScopes() return an empty list and use getReferencedScopes() to indicate what to read instead.

## 根据插件包名的打包过滤器

在插件打包时，引入下面这段脚本 (app/build.gradle)，用来过滤掉除了插件包名以外的其他类库和文件。

``` java
////////////////////////////////// Class Filter ///////////////////////////////////////

import com.android.build.api.transform.*
import com.android.build.gradle.internal.pipeline.TransformManager
import groovy.io.FileType
import org.apache.commons.io.FileUtils

class StripClassAndResTransform extends Transform {

    private Project project

    StripClassAndResTransform(Project project) {
        this.project = project
    }

    @Override
    String getName() {
        return 'stripClassAndRes'
    }

    @Override
    Set<QualifiedContent.ContentType> getInputTypes() {
        return TransformManager.CONTENT_JARS
    }

    @Override
    Set<QualifiedContent.Scope> getScopes() {
        return TransformManager.SCOPE_FULL_PROJECT
    }

    @Override
    boolean isIncremental() {
        return false
    }

    @Override
    void transform(final TransformInvocation transformInvocation) throws TransformException, InterruptedException, IOException {

        def applicationId = 'com/example/multiclassloader'


        if (!isIncremental()) {
            transformInvocation.outputProvider.deleteAll()
        }

        transformInvocation.inputs.each {
            it.directoryInputs.each { directoryInput ->
                directoryInput.file.traverse (type: FileType.FILES){
                    def entryName = it.path.substring(directoryInput.file.path.length() + 1)
                    def destName = directoryInput.name + '/' + entryName
                    def dest = transformInvocation.outputProvider.getContentLocation(
                            destName, directoryInput.contentTypes, directoryInput.scopes, Format.DIRECTORY)
                    // check whether it is a bundle-file
                    if (entryName.contains(applicationId)) {
                        FileUtils.copyFile(it, dest)
                    }
                }
            }

            it.jarInputs.each { jarInput ->
                // we don't need libs currently
            }
        }
    }
}

project.android.registerTransform(new StripClassAndResTransform(project))
```

最后要做的就是，正常地打包 `./gradlew assembleDebug` （工程名是 pluginByApk），然后看看打出来的 Apk 中的 Dex 文件是否包含了其他的依赖（例如 Support 包的文件），再用上一节的 Demo 测试一下即可（即 push 这个插件 apk 到 Downloads 文件夹，修改 bundleClassloader 加载的 apk 文件）。

## 参考资料

- [https://afterecho.uk/blog/create-a-standalone-gradle-plugin-for-android-part-4-the-transform-api.html](https://afterecho.uk/blog/create-a-standalone-gradle-plugin-for-android-part-4-the-transform-api.html)
- [https://github.com/didi/VirtualAPK/blob/HEAD/virtualapk-gradle-plugin/src/main/groovy/com.didi.virtualapk/transform/StripClassAndResTransform.groovy](https://github.com/didi/VirtualAPK/blob/HEAD/virtualapk-gradle-plugin/src/main/groovy/com.didi.virtualapk/transform/StripClassAndResTransform.groovy)
- [http://google.github.io/android-gradle-dsl/javadoc/current/](http://google.github.io/android-gradle-dsl/javadoc/current/)

