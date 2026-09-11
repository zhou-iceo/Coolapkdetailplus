package com.atxz.coolapkdetailplus

import com.highcapable.yukihookapi.YukiHookAPI
import com.highcapable.yukihookapi.annotation.xposed.InjectYukiHookWithXposed
import com.highcapable.yukihookapi.hook.factory.configs
import com.highcapable.yukihookapi.hook.xposed.proxy.IYukiHookXposedInit

@InjectYukiHookWithXposed
class HookEntry : IYukiHookXposedInit {

    override fun onInit() {
        configs {
            debugLog {
                tag = "CoolapkDetailPlus"
                isEnable = true
            }
        }
    }

    override fun onHook() {
        YukiHookAPI.encase {
            loadApp("com.coolapk.market") {
                CoolapkHook.onHook()
            }
        }
    }
}
