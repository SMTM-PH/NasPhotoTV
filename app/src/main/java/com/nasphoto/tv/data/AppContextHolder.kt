package com.nasphoto.tv.data

/** Application Context 持有者：数据层（设置、证书指纹、图片缓存）需要它，但不该到处传 Context。 */
object AppContextHolder {
    @Volatile
    var context: android.content.Context? = null
}
