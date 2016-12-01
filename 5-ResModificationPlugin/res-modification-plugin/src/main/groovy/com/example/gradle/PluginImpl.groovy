package com.example.gradle

import com.android.build.gradle.tasks.ProcessAndroidResources
import com.android.sdklib.BuildToolInfo
import com.example.gradle.aapt.Aapt
import com.example.gradle.aapt.SymbolParser
import org.gradle.api.Plugin
import org.gradle.api.Project
import org.gradle.api.file.FileTree

public class PluginImpl implements Plugin<Project> {

    private static final int UNSET_TYPEID = 99
    private static final int UNSET_ENTRYID = -1
    protected static def sPackageIds = [:] as LinkedHashMap<String, Integer>

    protected Project project

    def idMaps = [:]
    def idStrMaps = [:]
    def retainedTypes = []
    def retainedStyleables = []
    def allTypes = []
    def allStyleables = []
    def packageId = 121 //0x79

    @Override
    void apply(Project project) {
        this.project = project

        project.afterEvaluate {
            def processDebugResources = (ProcessAndroidResources) project.tasks['processDebugResources']
            processDebugResources.outputs.upToDateWhen { false }
            processDebugResources.doLast { ProcessAndroidResources i ->
                println "break point!"
                hookAapt(i)
            }
        }
    }

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

    /**
     * Prepare retained resource types and resource id maps for package slicing
     */
    protected void prepareSplit(File symbolFile) {
        def idsFile = symbolFile
        if (!idsFile.exists()) return



        def publicEntries = SymbolParser.getResourceEntries(new File(""))
        def bundleEntries = SymbolParser.getResourceEntries(idsFile)
        def staticIdMaps = [:]
        def staticIdStrMaps = [:]
        def retainedEntries = []
        def retainedPublicEntries = []
        def retainedStyleables = []

        bundleEntries.each { k, Map be ->
            be._typeId = UNSET_TYPEID // for sort
            be._entryId = UNSET_ENTRYID

            Map le = publicEntries.get(k)
            if (le != null) {
                // Use last built id
                be._typeId = le.typeId
                be._entryId = le.entryId
                retainedPublicEntries.add(be)
                publicEntries.remove(k)
                return
            }


            be.isStyleable ? retainedStyleables.add(be) : retainedEntries.add(be)
        }

        if (publicEntries.size() > 0) {
            throw new RuntimeException("No support deleting resources on lib.* now!\n" +
                    "  - ${publicEntries.keySet().join(", ")}\n" +
                    "see https://github.com/wequick/Small/issues/53 for more information.")
        }
        if (retainedEntries.size() == 0 && retainedPublicEntries.size() == 0) {
            this.retainedTypes = [] // Doesn't have any resources
            return
        }

        // Prepare public types
        def publicTypes = [:]
        def maxPublicTypeId = 0
        def unusedTypeIds = [] as Queue
        if (retainedPublicEntries.size() > 0) {
            retainedPublicEntries.each { e ->
                def typeId = e._typeId
                def entryId = e._entryId
                def type = publicTypes[e.type]
                if (type == null) {
                    publicTypes[e.type] = [id      : typeId, maxEntryId: entryId,
                                           entryIds: [entryId], unusedEntryIds: [] as Queue]
                    maxPublicTypeId = Math.max(typeId, maxPublicTypeId)
                } else {
                    type.maxEntryId = Math.max(entryId, type.maxEntryId)
                    type.entryIds.add(entryId)
                }
            }
            if (maxPublicTypeId != publicTypes.size()) {
                for (int i = 1; i < maxPublicTypeId; i++) {
                    if (publicTypes.find { k, t -> t.id == i } == null) unusedTypeIds.add(i)
                }
            }
            publicTypes.each { k, t ->
                if (t.maxEntryId != t.entryIds.size()) {
                    for (int i = 0; i < t.maxEntryId; i++) {
                        if (!t.entryIds.contains(i)) t.unusedEntryIds.add(i)
                    }
                }
            }
        }

        // First sort with origin(full) resources order
        retainedEntries.sort { a, b ->
            a.typeId <=> b.typeId ?: a.entryId <=> b.entryId
        }

        // Reassign resource type id (_typeId) and entry id (_entryId)
        def lastEntryIds = [:]
        if (retainedEntries.size() > 0) {
            if (retainedEntries[0].type != 'attr') {
                // reserved for `attr'
                if (maxPublicTypeId == 0) maxPublicTypeId = 1
                if (unusedTypeIds.size() > 0) unusedTypeIds.poll()
            }
            def selfTypes = [:]
            retainedEntries.each { e ->
                // Check if the type has been declared in public.txt
                def type = publicTypes[e.type]
                if (type != null) {
                    e._typeId = type.id
                    if (type.unusedEntryIds.size() > 0) {
                        e._entryId = type.unusedEntryIds.poll()
                    } else {
                        e._entryId = ++type.maxEntryId
                    }
                    return
                }
                // Assign new type with unused type id
                type = selfTypes[e.type]
                if (type != null) {
                    e._typeId = type.id
                } else {
                    if (unusedTypeIds.size() > 0) {
                        e._typeId = unusedTypeIds.poll()
                    } else {
                        e._typeId = ++maxPublicTypeId
                    }
                    selfTypes[e.type] = [id: e._typeId]
                }
                // Simply increase the entry id
                def entryId = lastEntryIds[e.type]
                if (entryId == null) {
                    entryId = 0
                } else {
                    entryId++
                }
                e._entryId = lastEntryIds[e.type] = entryId
            }

            retainedEntries += retainedPublicEntries
        } else {
            retainedEntries = retainedPublicEntries
        }

        // Resort with reassigned resources order
        retainedEntries.sort { a, b ->
            a._typeId <=> b._typeId ?: a._entryId <=> b._entryId
        }

        // Resort retained resources
        def retainedTypes = []
        def pid = (this.packageId << 24)
        def currType = null
        retainedEntries.each { e ->
            // Prepare entry id maps for resolving resources.arsc and binary xml files
            if (currType == null || currType.name != e.type) {
                // New type
                currType = [type: e.vtype, name: e.type, id: e.typeId, _id: e._typeId, entries: []]
                retainedTypes.add(currType)
            }
            def newResId = pid | (e._typeId << 16) | e._entryId
            def newResIdStr = "0x${Integer.toHexString(newResId)}"
            staticIdMaps.put(e.id, newResId)
            staticIdStrMaps.put(e.idStr, newResIdStr)

            // Prepare styleable id maps for resolving R.java
            if (retainedStyleables.size() > 0 && e.typeId == 1) {
                retainedStyleables.findAll { it.idStrs != null }.each {
                    // Replace `e.idStr' with `newResIdStr'
                    def index = it.idStrs.indexOf(e.idStr)
                    if (index >= 0) {
                        it.idStrs[index] = newResIdStr
                        it.mapped = true
                    }
                }
            }

            def entry = [name: e.key, id: e.entryId, _id: e._entryId, v: e.id, _v: newResId,
                         vs  : e.idStr, _vs: newResIdStr]
            currType.entries.add(entry)
        }

        // Update the id array for styleables
        retainedStyleables.findAll { it.mapped != null }.each {
            it.idStr = "{ ${it.idStrs.join(', ')} }"
            it.idStrs = null
        }

        // Collect all the resources for generating a temporary full edition R.java
        // which required in javac.
        // TODO: Do this only for the modules who's code really use R.xx of lib.*
        def allTypes = []
        def allStyleables = []
        def addedTypes = [:]

        retainedTypes.each { t ->
            def at = addedTypes[t.name]
            if (at != null) {
                at.entries.addAll(t.entries)
            } else {
                allTypes.add(t)
            }
        }
        allStyleables.addAll(retainedStyleables)

        this.idMaps = staticIdMaps
        this.idStrMaps = staticIdStrMaps
        this.retainedTypes = retainedTypes
        this.retainedStyleables = retainedStyleables

        this.allTypes = allTypes
        this.allStyleables = allStyleables

    }
}
