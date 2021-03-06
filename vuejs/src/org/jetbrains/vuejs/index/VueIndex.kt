package org.jetbrains.vuejs.index

import com.intellij.javascript.nodejs.packageJson.PackageJsonDependencies
import com.intellij.lang.javascript.buildTools.npm.PackageJsonUtil
import com.intellij.lang.javascript.psi.JSImplicitElementProvider
import com.intellij.lang.javascript.psi.stubs.JSImplicitElement
import com.intellij.openapi.project.DumbService
import com.intellij.openapi.project.Project
import com.intellij.openapi.roots.ProjectRootModificationTracker
import com.intellij.openapi.vfs.VirtualFile
import com.intellij.openapi.vfs.VirtualFileManager
import com.intellij.psi.search.FileTypeIndex
import com.intellij.psi.search.GlobalSearchScope
import com.intellij.psi.stubs.StubIndex
import com.intellij.psi.stubs.StubIndexKey
import com.intellij.psi.util.CachedValueProvider
import com.intellij.psi.util.CachedValuesManager
import com.intellij.util.Processor
import org.jetbrains.vuejs.VueFileType

val VUE = "vue"
val INDICES:MutableMap<StubIndexKey<String, JSImplicitElementProvider>, String> = mutableMapOf()
val GLOBAL = "global"
val LOCAL = "local"

fun getForAllKeys(scope:GlobalSearchScope, key:StubIndexKey<String, JSImplicitElementProvider>,
                  filter: ((String) -> Boolean)?): Collection<JSImplicitElement> {
  var keys = StubIndex.getInstance().getAllKeys(key, scope.project!!)
  if (filter != null) keys = keys.filter { filter.invoke(it) }
  return keys.mapNotNull { resolve(it, scope, key) }.flatMap { it.toList() }
}

fun resolve(name:String, scope:GlobalSearchScope, key:StubIndexKey<String, JSImplicitElementProvider>): Collection<JSImplicitElement>? {
  if (DumbService.isDumb(scope.project!!)) return null
  val result = mutableListOf<JSImplicitElement>()
  StubIndex.getInstance().processElements(key, name, scope.project!!, scope, JSImplicitElementProvider::class.java, Processor {
    provider: JSImplicitElementProvider? ->
      provider?.indexingData?.implicitElements
        // the check for name is needed for groups of elements, for instance:
        // directives: {a:..., b:...} -> a and b are recorded in 'directives' data.
        // You can find it with 'a' or 'b' key, but you should filter the result
        ?.filter { it.userString == INDICES[key] && name == it.name }
        ?.forEach { result.add(it) }
    return@Processor true
  })
  return if (result.isEmpty()) null else result
}

fun hasVue(project: Project): Boolean {
  if (DumbService.isDumb(project)) return false

  return CachedValuesManager.getManager(project).getCachedValue(project, {
    var hasVue = false
    var packageJson:VirtualFile? = null
    if (project.baseDir != null) {
      packageJson = project.baseDir.findChild(PackageJsonUtil.FILE_NAME)
      if (packageJson != null) {
        val dependencies = PackageJsonDependencies.getOrCreate(project, packageJson)
        if (dependencies != null &&
            (dependencies.dependencies.containsKey(VUE) || dependencies.devDependencies.containsKey(VUE))) {
          hasVue = true
        }
      }
    }

    if (hasVue) {
      CachedValueProvider.Result.create(true, packageJson)
    } else {
      val result = FileTypeIndex.containsFileOfType(VueFileType.INSTANCE, GlobalSearchScope.projectScope(project))
      CachedValueProvider.Result.create(result, VirtualFileManager.VFS_STRUCTURE_MODIFICATIONS, ProjectRootModificationTracker.getInstance(project))
    }
  })
}