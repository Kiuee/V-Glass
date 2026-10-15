package com.moji.liquidglass.hook

import android.util.Log
import de.robv.android.xposed.XC_MethodHook
import de.robv.android.xposed.XposedBridge
import io.github.libxposed.api.XposedInterface
import io.github.libxposed.api.XposedModule
import java.lang.reflect.Executable

/** 模块日志统一 TAG（logcat 可按此过滤）。 */
const val TAG = "LiquidGlass"

/** 一次被 hook 调用的上下文：before 短路返回非 null 即跳过原方法；after 里可读 [result] 并替换。 */
class HookCall(
    val args: List<Any?>,
    val thisObj: Any?,
) {
    @Volatile
    var result: Any? = null

    /** 原方法是否已执行（用于区分"原方法异常"与"回调异常"）。 */
    @Volatile
    var proceeded: Boolean = false
}

/**
 * 屏蔽现代 (libxposed API 102) 与经典 (XposedBridge 82) 两套 hook API 的差异，
 * 使注入逻辑对两边复用（模式取自 vivoColoricon 已验证实现）。
 */
interface HookFacade {
    fun log(priority: Int, msg: String)
    fun log(priority: Int, msg: String, t: Throwable)

    /** before 返回非 null → 跳过原方法并返回该值；after 返回非 null → 替换原返回值。 */
    fun around(
        m: Executable,
        before: ((HookCall) -> Any?)?,
        after: ((HookCall) -> Any?)?,
    )

    /** 放行原方法，但把第 [index] 个 int 参数改写为 transform 的返回值。 */
    fun mapIntArg(m: Executable, index: Int, transform: (Int) -> Int)

    /**
     * 满足 [gate] 时跳过原方法（仅适用于无返回值的 setter 类方法），
     * 否则放行。用于"拦截应用对某 setter 的调用"场景，不依赖参数改写能力。
     */
    fun swallowArgIf(m: Executable, index: Int, gate: (Any?) -> Boolean)
}

/** 现代 API 102 (libxposed) 适配。 */
class ModernFacade(private val module: XposedModule) : HookFacade {

    override fun log(priority: Int, msg: String) {
        // 双通道：framework 日志（tag 可能被框架改写，故写入正文保证可过滤）+ 直接 logcat
        try {
            module.log(priority, TAG, "[$TAG] $msg")
        } catch (_: Throwable) {
        }
        Log.println(priority, TAG, msg)
    }

    override fun log(priority: Int, msg: String, t: Throwable) {
        try {
            module.log(priority, TAG, "[$TAG] $msg", t)
        } catch (_: Throwable) {
        }
        Log.println(priority, TAG, "$msg\n${Log.getStackTraceString(t)}")
    }

    override fun around(
        m: Executable,
        before: ((HookCall) -> Any?)?,
        after: ((HookCall) -> Any?)?,
    ) {
        try {
            module.hook(m)
                .setExceptionMode(XposedInterface.ExceptionMode.PROTECTIVE)
                .intercept { chain ->
                    var call: HookCall? = null
                    try {
                        call = HookCall(chain.args, chain.thisObject)
                        val early = before?.invoke(call)
                        if (early != null && early !is Unit) return@intercept early
                        val r = chain.proceed()
                        call.proceeded = true
                        call.result = r
                        val out = after?.invoke(call)
                        if (out != null && out !is Unit) out else r
                    } catch (t: Throwable) {
                        if (call?.proceeded == true) throw t // 原方法自身的异常照常上抛
                        log(Log.WARN, "callback failed: ${m.name}: $t")
                        try {
                            chain.proceed()
                        } catch (_: Throwable) {
                            null
                        }
                    }
                }
        } catch (t: Throwable) {
            log(Log.WARN, "hook failed: $m", t)
        }
    }

    override fun mapIntArg(m: Executable, index: Int, transform: (Int) -> Int) {
        try {
            // libxposed 102 的 chain.args 是不可变列表（改参抛 UnsupportedOperationException），
            // 无法"改参后 proceed"。改为原样 proceed 后按新值重入一次：对值落地型方法
            // （setPadding、留白分发等）重设即最终生效；重入时新旧值相等不再重入，保证收敛。
            module.hook(m)
                .setExceptionMode(XposedInterface.ExceptionMode.PROTECTIVE)
                .intercept { chain ->
                    val result = chain.proceed()
                    val original = (chain.args.getOrNull(index) as? Number)?.toInt() ?: 0
                    val transformed = transform(original)
                    val method = m as? java.lang.reflect.Method
                    if (transformed != original && method != null) {
                        runCatching {
                            val args = chain.args.toTypedArray()
                            args[index] = transformed
                            method.invoke(chain.thisObject, *args)
                        }.onFailure { log(Log.WARN, "mapIntArg re-invoke failed: ${m.name}", it) }
                    }
                    result
                }
        } catch (t: Throwable) {
            log(Log.WARN, "hook failed: $m", t)
        }
    }

    override fun swallowArgIf(m: Executable, index: Int, gate: (Any?) -> Boolean) {
        try {
            module.hook(m)
                .setExceptionMode(XposedInterface.ExceptionMode.PROTECTIVE)
                .intercept { chain ->
                    if (gate(chain.args.getOrNull(index))) null else chain.proceed()
                }
        } catch (t: Throwable) {
            log(Log.WARN, "hook failed: $m", t)
        }
    }
}
/** 经典 API 82 (assets/xposed_init 入口) 适配。 */
class LegacyFacade : HookFacade {

    private val calls = object : ThreadLocal<HookCall>() {}

    override fun log(priority: Int, msg: String) {
        Log.println(priority, TAG, msg)
        try {
            XposedBridge.log("[$TAG] $msg")
        } catch (_: Throwable) {
            // 桩未被遮蔽时（非宿主进程）忽略
        }
    }

    override fun log(priority: Int, msg: String, t: Throwable) {
        Log.println(priority, TAG, "$msg: $t")
        try {
            XposedBridge.log(t)
        } catch (_: Throwable) {
        }
    }

    override fun around(
        m: Executable,
        before: ((HookCall) -> Any?)?,
        after: ((HookCall) -> Any?)?,
    ) {
        try {
            XposedBridge.hookMethod(m, object : XC_MethodHook() {
                override fun beforeHookedMethod(param: XC_MethodHook.MethodHookParam) {
                    val call = HookCall(param.args?.toList() ?: emptyList(), param.thisObject)
                    calls.set(call)
                    try {
                        val early = before?.invoke(call)
                        if (early != null && early !is Unit) param.result = early
                    } catch (t: Throwable) {
                        log(Log.WARN, "before failed: ${m.name}: $t")
                    }
                }

                override fun afterHookedMethod(param: XC_MethodHook.MethodHookParam) {
                    val call = calls.get() ?: HookCall(param.args?.toList() ?: emptyList(), param.thisObject)
                    try {
                        call.result = param.result
                    } catch (_: Throwable) {
                    }
                    try {
                        after?.invoke(call)?.let { param.result = it }
                    } catch (t: Throwable) {
                        log(Log.WARN, "after failed: ${m.name}: $t")
                    }
                }
            })
        } catch (t: Throwable) {
            log(Log.WARN, "hook failed: $m", t)
        }
    }

    override fun mapIntArg(m: Executable, index: Int, transform: (Int) -> Int) {
        try {
            XposedBridge.hookMethod(m, object : XC_MethodHook() {
                override fun beforeHookedMethod(param: XC_MethodHook.MethodHookParam) {
                    try {
                        val original = (param.args.getOrNull(index) as? Number)?.toInt() ?: 0
                        param.args[index] = transform(original)
                    } catch (t: Throwable) {
                        log(Log.WARN, "mapIntArg failed: ${m.name}", t)
                    }
                }
            })
        } catch (t: Throwable) {
            log(Log.WARN, "hook failed: $m", t)
        }
    }

    override fun swallowArgIf(m: Executable, index: Int, gate: (Any?) -> Boolean) {
        try {
            XposedBridge.hookMethod(m, object : XC_MethodHook() {
                override fun beforeHookedMethod(param: XC_MethodHook.MethodHookParam) {
                    try {
                        if (gate(param.args.getOrNull(index))) param.result = null
                    } catch (t: Throwable) {
                        log(Log.WARN, "swallowArgIf failed: ${m.name}", t)
                    }
                }
            })
        } catch (t: Throwable) {
            log(Log.WARN, "hook failed: $m", t)
        }
    }
}
