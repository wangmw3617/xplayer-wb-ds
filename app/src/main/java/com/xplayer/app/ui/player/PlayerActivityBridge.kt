package com.xplayer.app.ui.player

/**
 * 播放页与 Activity 之间的最小桥接：
 * Activity 无法直接读到 Compose 状态，于是把「按 Home 时是否自动进 PiP」和
 * 「PiP 模式变化」两个回调注册到这里，避免在 Activity 里持有一堆播放器引用。
 */
object PlayerActivityBridge {

    /** 返回 true 表示已进入画中画，Activity 不必再做默认处理 */
    var autoPip: (() -> Boolean)? = null

    /** PiP 状态变化：true 进入、false 退出 */
    var onPipChanged: ((Boolean) -> Unit)? = null

    fun clear() {
        autoPip = null
        onPipChanged = null
    }
}
