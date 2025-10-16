package io.nekohasekai.dsm

import android.util.Log
import de.robv.android.xposed.IXposedHookLoadPackage
import de.robv.android.xposed.XC_MethodHook
import de.robv.android.xposed.XC_MethodReplacement
import de.robv.android.xposed.XposedBridge
import de.robv.android.xposed.callbacks.XC_LoadPackage

class XposedMain : IXposedHookLoadPackage {

    companion object {
        private const val TAG = "DSM"
    }

    override fun handleLoadPackage(lpparam: XC_LoadPackage.LoadPackageParam) {
        if (lpparam.packageName != "org.thunderdog.challegram") {
            return
        }

        Log.i(TAG, "Module Active")

        // Hook 1: Make getConstructor return an invalid value
        hookGetConstructor(lpparam)

        // Hook 2: Intercept the send method
        hookClientSend(lpparam)
    }

    private fun hookGetConstructor(lpparam: XC_LoadPackage.LoadPackageParam) {
        runCatching {
            val clazz = Class.forName(
                "org.drinkless.tdlib.TdApi\$GetChatSponsoredMessages",
                false,
                lpparam.classLoader
            )

            // Hook the getConstructor method to return an invalid constructor ID
            clazz.declaredMethods
                .filter { it.name == "getConstructor" }
                .forEach { method ->
                    XposedBridge.hookMethod(method, object : XC_MethodReplacement() {
                        override fun replaceHookedMethod(param: MethodHookParam): Any {
                            Log.i(TAG, "Constructor hijacked")
                            // Return 0 to invalidate the request
                            return 0
                        }
                    })
                }

            Log.i(TAG, "Constructor hook installed")
        }.onFailure {
            Log.e(TAG, "Constructor hook failed", it)
        }
    }

    private fun hookClientSend(lpparam: XC_LoadPackage.LoadPackageParam) {
        runCatching {
            val clientClass = Class.forName(
                "org.drinkless.tdlib.Client",
                false,
                lpparam.classLoader
            )

            clientClass.declaredMethods.forEach { method ->
                val paramTypes = method.parameterTypes

                if (paramTypes.isNotEmpty() &&
                    (paramTypes[0].name.contains("TdApi") ||
                            paramTypes[0].name.contains("Function"))) {

                    XposedBridge.hookMethod(method, object : XC_MethodHook() {
                        override fun beforeHookedMethod(param: MethodHookParam) {
                            val request = param.args.getOrNull(0)
                            if (request != null) {
                                val className = request.javaClass.simpleName
                                if (className == "GetChatSponsoredMessages") {
                                    Log.i(TAG, "Send blocked")
                                    param.result = null
                                }
                            }
                        }
                    })
                }
            }

            Log.i(TAG, "Send hook installed")
        }.onFailure {
            Log.e(TAG, "Send hook failed", it)
        }
    }
}