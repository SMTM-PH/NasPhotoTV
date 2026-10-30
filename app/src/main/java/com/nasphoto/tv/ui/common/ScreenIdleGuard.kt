package com.nasphoto.tv.ui.common

/**
 * ================= 屏保空闲豁免（短时持有） =================
 *
 * 为什么需要：空闲检测只认「按键事件」。软件输入法（系统 IME）打字、语音输入这类
 * 不产生 KeyDown 的交互，会被误判成「用户睡着了」，60 秒后屏保直接盖脸 ——
 * 表现就是「输入到一半键盘没了」（用户反馈 1 的一个真实成因）。
 *
 * 用法：需要豁免的界面（如文字输入层）在组合期间每 10 秒调一次 [hold]，
 * 空闲检测循环发现仍在持有期内就跳过本次判定。持有是「续期制」的，
 * 界面销毁后最迟 30 秒自动失效，不存在忘记释放导致的永久屏保失效。
 */
object ScreenIdleGuard {
    @Volatile
    private var heldUntilMs: Long = 0L

    /** 从现在起 [seconds] 秒内不进入屏保；重复调用即续期 */
    fun hold(seconds: Int = 30) {
        heldUntilMs = System.currentTimeMillis() + seconds * 1000L
    }

    /** 空闲检测循环调用：true = 当前处于豁免期 */
    fun isHeld(): Boolean = System.currentTimeMillis() < heldUntilMs
}
