package com.xplayer.app.ui.player

/**
 * 播放页与 MainActivity 之间的线程安全/带身份校验桥接：
 * 使用 owner token 防止新旧播放页转场交替时，旧页面的 onDispose 误清掉新页面的 PiP 回调。
 */
object PlayerActivityBridge {

    private var activeOwner: Any? = null

    /** 返回 true 表示已进入画中画，Activity 不必再做默认处理 */
    var autoPip: (() -> Boolean)? = null
        private set

    /** PiP 状态变化：true 进入、false 退出 */
    var onPipChanged: ((Boolean) -> Unit)? = null
        private set

    fun isCurrentOwner(owner: Any): Boolean = activeOwner === owner

    fun register(
        owner: Any,
        autoPip: () -> Boolean,
        onPipChanged: (Boolean) -> Unit,
    ) {
        activeOwner = owner
        this.autoPip = autoPip
        this.onPipChanged = onPipChanged
    }

    fun unregister(owner: Any) {
        if (activeOwner === owner) {
            activeOwner = null
            autoPip = null
            onPipChanged = null
        }
    }

    fun clear() {
        activeOwner = null
        autoPip = null
        onPipChanged = null
    }
}
