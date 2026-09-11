package com.atxz.coolapkdetailplus

import android.util.Log
import com.highcapable.yukihookapi.YukiHookAPI
import io.github.libxposed.api.XposedModule
import io.github.libxposed.api.XposedModuleInterface

class LibXposedEntry : XposedModule() {

    override fun onModuleLoaded(param: XposedModuleInterface.ModuleLoadedParam) {
        super.onModuleLoaded(param)
        // Direct call to LibXposed API 102 logging method
        log(Log.INFO, "CoolapkDetailPlus", "[LibXposed API 102] Module loaded into process: ${param.processName} (Framework: $frameworkName $frameworkVersion, API: $apiVersion)")
    }

    override fun onPackageReady(param: XposedModuleInterface.PackageReadyParam) {
        super.onPackageReady(param)

        // Direct call to LibXposed API 102 logging method
        log(Log.INFO, "CoolapkDetailPlus", "[LibXposed API 102] Package ready: ${param.packageName}")

        if (param.packageName == "com.coolapk.market") {
            log(Log.INFO, "CoolapkDetailPlus", "[LibXposed API 102] Target package com.coolapk.market matched. Encasing hooks.")

            if (android.os.Build.VERSION.SDK_INT >= android.os.Build.VERSION_CODES.P) {
                try {
                    val factory = param.applicationInfo.appComponentFactory
                    if (factory != null && factory.contains("CoreComponentFactory")) {
                        param.applicationInfo.appComponentFactory = "android.app.AppComponentFactory"
                        log(Log.INFO, "CoolapkDetailPlus", "[LibXposed API 102] Fixed appComponentFactory ($factory -> android.app.AppComponentFactory)")
                    }
                } catch (t: Throwable) {
                    log(Log.WARN, "CoolapkDetailPlus", "[LibXposed API 102] Failed to check appComponentFactory: ${t.message}")
                }
            }

            YukiHookAPI.encase {
                loadApp("com.coolapk.market") {
                    CoolapkHook.onHook()
                }
            }
        }
    }
}
