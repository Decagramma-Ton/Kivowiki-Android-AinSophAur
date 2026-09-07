package wiki.kivo.core.model

import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update

/** 仅表示系统确认的新可用网络；不轮询服务端、不包含账号或请求信息。 */
object NetworkRecovery {
    private val generation = MutableStateFlow(0L)
    val changes = generation.asStateFlow()

    fun connected() {
        generation.update { it + 1 }
    }
}
