package wiki.kivo.core.designsystem

import androidx.compose.foundation.*
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.*
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.outlined.ArrowForward
import androidx.compose.material.icons.outlined.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.heading
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import coil3.compose.AsyncImage
import kotlinx.coroutines.delay
import wiki.kivo.core.model.*

/** 所有品牌占位统一使用站方授权的官方矢量 LOGO。 */
@Composable
fun ArchiveMark(
    modifier: Modifier = Modifier,
    @Suppress("UNUSED_PARAMETER") tint: Color = Color.Unspecified,
) {
    Image(
        painter = painterResource(R.drawable.kivo_logo),
        contentDescription = "KivoWiki",
        modifier = modifier.size(40.dp),
        contentScale = ContentScale.Fit,
    )
}

@Composable
fun SectionTitle(
    title: String,
    eyebrow: String? = null,
    action: String? = null,
    onAction: () -> Unit = {},
) {
    Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
        Column(Modifier.weight(1f)) {
            if (eyebrow != null)
                Text(
                    eyebrow,
                    style = MaterialTheme.typography.labelSmall,
                    color = MaterialTheme.colorScheme.primary,
                )
            Text(
                title,
                Modifier.semantics { heading() },
                style = MaterialTheme.typography.titleLarge,
            )
        }
        if (action != null)
            TextButton(onClick = onAction) {
                Text(action)
                Spacer(Modifier.width(4.dp))
                Icon(Icons.AutoMirrored.Outlined.ArrowForward, null, Modifier.size(16.dp))
            }
    }
}

@Composable
fun KivoCard(
    modifier: Modifier = Modifier,
    onClick: (() -> Unit)? = null,
    content: @Composable ColumnScope.() -> Unit,
) {
    val shape = RoundedCornerShape(20.dp)
    if (onClick != null)
        Surface(
            onClick = onClick,
            modifier = modifier,
            shape = shape,
            color = MaterialTheme.colorScheme.surface,
        ) {
            Column(content = content)
        }
    else
        Surface(modifier = modifier, shape = shape, color = MaterialTheme.colorScheme.surface) {
            Column(content = content)
        }
}

@Composable
fun KivoImage(
    url: String?,
    description: String?,
    modifier: Modifier = Modifier,
    contentScale: ContentScale = ContentScale.Crop,
    alignment: Alignment = Alignment.Center,
    backgroundColor: Color = MaterialTheme.colorScheme.surfaceVariant,
    onDimensions: ((Int, Int) -> Unit)? = null,
) {
    val settings = LocalKivoSettings.current
    val allowed =
        url != null &&
            settings.loadImages &&
            (settings.externalImages || UrlPolicy.isFirstPartyImage(url))
    if (allowed) {
        // 图鉴密集列表避免每张图片再创建 Subcomposition；Coil 按布局尺寸解码并复用缓存。
        var loaded by remember(url) { mutableStateOf(false) }
        var failed by remember(url) { mutableStateOf(false) }
        var attempt by remember(url) { mutableIntStateOf(0) }
        val network by NetworkRecovery.changes.collectAsState()
        var previousNetwork by remember(url) { mutableLongStateOf(network) }
        LaunchedEffect(network) {
            if (network != previousNetwork && failed) {
                failed = false
                attempt = 0
            }
            previousNetwork = network
        }
        // 请求取消由 Coil 随可见性处理；短暂断连最多补发两次，离屏不会继续刷请求。
        LaunchedEffect(url, failed, attempt) {
            if (failed && attempt < 2) {
                delay(800L shl attempt)
                failed = false
                attempt++
            }
        }
        // 透明图标可交由宿主提供底色；加载中和失败时仍保留统一占位反馈。
        Box(modifier.background(backgroundColor)) {
            if (!loaded) ImagePlaceholder()
            key(url, attempt, network) {
                AsyncImage(
                    model = url,
                    contentDescription = description,
                    modifier = Modifier.matchParentSize(),
                    contentScale = contentScale,
                    alignment = alignment,
                    onLoading = { loaded = false },
                    onSuccess = {
                        loaded = true
                        failed = false
                        onDimensions?.invoke(it.result.image.width, it.result.image.height)
                    },
                    onError = {
                        loaded = false
                        failed = true
                    },
                )
            }
            if (failed && attempt >= 2) {
                IconButton(
                    {
                        attempt = 0
                        failed = false
                    },
                    Modifier.align(Alignment.Center),
                ) {
                    Icon(Icons.Outlined.Refresh, "重新加载图片")
                }
            }
        }
    } else
        Box(
            modifier.background(backgroundColor).semantics {
                if (description != null) contentDescription = description
            },
            contentAlignment = Alignment.Center,
        ) {
            ImagePlaceholder()
        }
}

@Composable
private fun ImagePlaceholder() {
    Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
        Icon(
            Icons.Outlined.AutoAwesome,
            null,
            Modifier.size(26.dp),
            tint = MaterialTheme.colorScheme.primary.copy(alpha = .3f),
        )
    }
}

@Composable
fun StatusNote(text: String, action: String? = null, onAction: () -> Unit = {}) {
    Row(
        Modifier.fillMaxWidth()
            .clip(RoundedCornerShape(12.dp))
            .background(MaterialTheme.colorScheme.surfaceVariant)
            .padding(start = 12.dp, end = 4.dp, top = 6.dp, bottom = 6.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Icon(
            Icons.Outlined.Info,
            null,
            Modifier.size(18.dp),
            MaterialTheme.colorScheme.onSurfaceVariant,
        )
        Spacer(Modifier.width(8.dp))
        Text(
            text,
            Modifier.weight(1f),
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
        if (action != null)
            TextButton(onClick = onAction) {
                Text(action, style = MaterialTheme.typography.labelMedium)
            }
    }
}

@Composable
fun <T> SectionStatus(state: LoadState<T>, retry: () -> Unit) {
    val network by NetworkRecovery.changes.collectAsState()
    var previousNetwork by remember { mutableLongStateOf(network) }
    val latestRetry by rememberUpdatedState(retry)
    LaunchedEffect(network) {
        if (network != previousNetwork && state.error != null && !state.loading) latestRetry()
        previousNetwork = network
    }
    when {
        state.error != null -> StatusNote(requireNotNull(state.error), "重试", retry)
        state.value == null && state.loading -> {
            Column(
                Modifier.fillMaxWidth().padding(vertical = 12.dp),
                verticalArrangement = Arrangement.spacedBy(10.dp),
            ) {
                Box(
                    Modifier.fillMaxWidth(.7f)
                        .height(16.dp)
                        .clip(RoundedCornerShape(8.dp))
                        .background(MaterialTheme.colorScheme.surfaceVariant)
                )
                Box(
                    Modifier.fillMaxWidth()
                        .height(72.dp)
                        .clip(RoundedCornerShape(16.dp))
                        .background(MaterialTheme.colorScheme.surfaceVariant)
                )
            }
        }
        state.fromCache && state.fetchedAt != null ->
            Text(
                "缓存于 ${shortDate(state.fetchedAt)}${if (state.loading) " · 更新中" else ""}",
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
    }
}

@Composable
fun ContentRow(card: ContentCard, modifier: Modifier = Modifier, onClick: () -> Unit) {
    Row(
        modifier.fillMaxWidth().clickable(onClick = onClick).padding(vertical = 14.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Column(Modifier.weight(1f)) {
            Text(
                card.title,
                style = MaterialTheme.typography.titleMedium,
                maxLines = 2,
                overflow = TextOverflow.Ellipsis,
            )
            if (card.summary.isNotBlank())
                Text(
                    card.summary,
                    Modifier.padding(top = 6.dp),
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    maxLines = 2,
                    overflow = TextOverflow.Ellipsis,
                )
            Text(
                listOf(
                        card.key.type.label,
                        card.timestamp?.let { shortDate(it, pattern = "yyyy.MM.dd") },
                    )
                    .filterNotNull()
                    .joinToString("  ·  "),
                Modifier.padding(top = 7.dp),
                style = MaterialTheme.typography.labelSmall,
                color = MaterialTheme.colorScheme.primary,
            )
        }
        if (card.image != null) {
            Spacer(Modifier.width(16.dp))
            KivoImage(card.image, null, Modifier.size(86.dp, 68.dp).clip(RoundedCornerShape(12.dp)))
        } else {
            Spacer(Modifier.width(8.dp))
            Icon(
                Icons.AutoMirrored.Outlined.ArrowForward,
                null,
                Modifier.size(18.dp),
                MaterialTheme.colorScheme.outline,
            )
        }
    }
}

@Composable
fun EmptyState(
    title: String,
    description: String,
    modifier: Modifier = Modifier,
    action: String? = null,
    onAction: () -> Unit = {},
) {
    Column(
        modifier.fillMaxWidth().padding(28.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
    ) {
        ArchiveMark(Modifier.size(64.dp), MaterialTheme.colorScheme.primary.copy(alpha = .55f))
        Text(title, Modifier.padding(top = 20.dp), style = MaterialTheme.typography.titleLarge)
        Text(
            description,
            Modifier.padding(top = 8.dp),
            style = MaterialTheme.typography.bodyMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            textAlign = androidx.compose.ui.text.style.TextAlign.Center,
        )
        if (action != null)
            FilledTonalButton(onAction, Modifier.padding(top = 18.dp)) { Text(action) }
    }
}
