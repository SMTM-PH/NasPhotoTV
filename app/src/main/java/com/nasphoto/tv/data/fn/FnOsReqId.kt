package com.nasphoto.tv.data.fn

/**
 * WebSocket 登录请求 id：28 位十六进制 = 8 位前置 + 16 位 backId + 4 位后缀。
 *
 * 握手第一步必须用全零 backId；登录成功后换成服务端返回的 backId。
 * 这个生成器是进程级单例，所以每次握手前必须 [reset]，否则第二次登录会带着
 * 上一次会话的 id，被服务端按 401 拒绝。
 */
internal object FnOsReqId {

    private const val ZERO_BACK_ID = "0000000000000000"

    @Volatile
    private var backId: String = ZERO_BACK_ID

    fun reset() {
        backId = ZERO_BACK_ID
    }

    fun setBackId(value: String) {
        if (value.length == 16) backId = value
    }

    fun next(): String = FnOsCrypto.randomHex(4) + backId + FnOsCrypto.randomHex(2)
}
