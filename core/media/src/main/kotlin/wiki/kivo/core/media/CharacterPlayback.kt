package wiki.kivo.core.media

import android.content.Context
import androidx.annotation.OptIn
import androidx.media3.common.*
import androidx.media3.common.util.UnstableApi
import androidx.media3.datasource.okhttp.OkHttpDataSource
import androidx.media3.exoplayer.ExoPlayer
import androidx.media3.exoplayer.source.DefaultMediaSourceFactory
import dagger.hilt.android.qualifiers.ApplicationContext
import javax.inject.Inject
import javax.inject.Singleton
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.asStateFlow
import okhttp3.OkHttpClient

data class PlaybackStatus(
    val url: String? = null,
    val playing: Boolean = false,
    val loading: Boolean = false,
    val error: String? = null,
)

/** 全局只有一个 Media3 实例。语音和技能视频共享音频焦点，切源前停旧源，离页即释放。 */
@Singleton
@OptIn(UnstableApi::class)
class CharacterPlayback
@Inject
constructor(
    @param:ApplicationContext private val context: Context,
    private val client: OkHttpClient,
) {
    private val mutable = MutableStateFlow(PlaybackStatus())
    val status = mutable.asStateFlow()
    private var owner: Any? = null
    var player: ExoPlayer? = null
        private set

    fun acquire(token: Any): ExoPlayer {
        if (owner !== token) release()
        owner = token
        return player
            ?: ExoPlayer.Builder(context)
                .setMediaSourceFactory(
                    DefaultMediaSourceFactory(context)
                        .setDataSourceFactory(OkHttpDataSource.Factory(client))
                )
                .setAudioAttributes(
                    AudioAttributes.Builder()
                        .setUsage(C.USAGE_MEDIA)
                        .setContentType(C.AUDIO_CONTENT_TYPE_MUSIC)
                        .build(),
                    true,
                )
                .setHandleAudioBecomingNoisy(true)
                .build()
                .also { p ->
                    player = p
                    p.addListener(
                        object : Player.Listener {
                            override fun onEvents(player: Player, events: Player.Events) {
                                mutable.value =
                                    mutable.value.copy(
                                        playing = player.isPlaying,
                                        loading = player.playbackState == Player.STATE_BUFFERING,
                                    )
                            }

                            override fun onPlayerError(error: PlaybackException) {
                                mutable.value =
                                    mutable.value.copy(
                                        playing = false,
                                        loading = false,
                                        error = "音频／视频暂时无法播放，请重试（${error.errorCodeName}）",
                                    )
                            }
                        }
                    )
                }
    }

    fun play(token: Any, url: String) {
        CharacterAssets.requireTrusted(url)
        val p = acquire(token)
        if (
            mutable.value.url == url &&
                p.playbackState != Player.STATE_IDLE &&
                mutable.value.error == null
        ) {
            if (p.isPlaying) p.pause()
            else {
                if (p.playbackState == Player.STATE_ENDED) p.seekTo(0)
                p.play()
            }
        } else {
            p.stop()
            mutable.value = PlaybackStatus(url, loading = true)
            p.setMediaItem(MediaItem.fromUri(url))
            p.prepare()
            p.play()
        }
    }

    fun pause(token: Any) {
        if (owner === token) player?.pause()
    }

    fun release(token: Any? = null) {
        if (token == null || owner === token) {
            player?.release()
            player = null
            owner = null
            mutable.value = PlaybackStatus()
        }
    }
}
