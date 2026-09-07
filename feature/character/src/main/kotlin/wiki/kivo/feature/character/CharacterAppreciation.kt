package wiki.kivo.feature.character

import androidx.compose.foundation.clickable
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.unit.dp
import wiki.kivo.core.data.CharacterRepository
import wiki.kivo.core.designsystem.*
import wiki.kivo.core.model.*

/** 图集默认只构建当前分类的横向预览；所有动态资源 ID 都保持可访问。 */
@Composable
internal fun appreciationParts(
    profile: CharacterProfile,
    repository: CharacterRepository,
    link: (String) -> Unit,
    image: (String) -> Unit,
    preview: (Int, Boolean) -> Unit,
): List<DetailPart> = buildList {
    add(
        DetailPart("appreciation-note") {
            Text(
                "特殊换装素材可能收录在原皮档案中。图集可按分类横向浏览或展开；动态预览只在打开后加载。",
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }
    )
    if (profile.gallery.any { it.images.isNotEmpty() })
        add(
            DetailPart("gallery", "角色画廊") {
                GalleryPanel(profile.gallery, image)
            }
        )
    if (profile.spine.isNotEmpty()) add(DetailPart("spines", "立绘与回忆大厅") {})
    profile.spine.forEach { id ->
        add(DetailPart("spine-$id") { MediaEntry(id, true, repository) { preview(id, true) } })
    }
    if (profile.models.isNotEmpty())
        add(
            DetailPart("models", "3D模型") {
                TextButton({ link("https://kivo.wiki/article/55") }) { Text("查看网站模型已知问题清单") }
            }
        )
    profile.models.forEach { id ->
        add(DetailPart("model-$id") { MediaEntry(id, false, repository) { preview(id, false) } })
    }
    if (
        profile.gallery.isEmpty() &&
            profile.spine.isEmpty() &&
            profile.models.isEmpty()
    )
        add(DetailPart("no-media") { Text("这份档案尚未收录鉴赏素材。") })
}

/**
 * 分类标签不进入页内目录，避免拥有十余类差分图的角色把目录撑长。
 * 折叠态是有界的横向惰性列表；只有用户主动展开时才组合当前分类的全部缩略图。
 */
@Composable
private fun GalleryPanel(galleries: List<CharacterGallery>, open: (String) -> Unit) {
    // 保留 API 原始分类下标，便于测试、无障碍定位和问题回报；空分类不显示。
    val available = galleries.mapIndexedNotNull { group, gallery ->
        gallery.takeIf { it.images.isNotEmpty() }?.let { group to it }
    }
    val key = available.joinToString("|") { (group, item) -> "$group:${item.title}:${item.images.size}" }
    var selected by rememberSaveable(key) { mutableIntStateOf(0) }
    var expanded by rememberSaveable(key) { mutableStateOf(false) }
    val index = selected.coerceIn(0, available.lastIndex)
    val (groupIndex, gallery) = available[index]
    Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
        Row(
            modifier =
                Modifier.fillMaxWidth()
                    .horizontalScroll(rememberScrollState())
                    .testTag("gallery_groups"),
            horizontalArrangement = Arrangement.spacedBy(8.dp),
        ) {
            available.forEachIndexed { position, (group, item) ->
                FilterChip(
                    selected = position == index,
                    onClick = {
                        selected = position
                        expanded = false
                    },
                    label = { Text(item.title.ifBlank { "图集 ${group+1}" }) },
                    modifier = Modifier.testTag("gallery_group_$group"),
                )
            }
        }
        Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
            Text(
                "${gallery.images.size} 张 · 点按可查看原图与保存",
                Modifier.weight(1f),
                style = MaterialTheme.typography.labelMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
            TextButton({ expanded = !expanded }) {
                Icon(
                    if (expanded) Icons.Outlined.UnfoldLess else Icons.Outlined.UnfoldMore,
                    null,
                    Modifier.size(18.dp),
                )
                Spacer(Modifier.width(4.dp))
                Text(if (expanded) "收起" else "展开全部")
            }
        }
        if (!expanded)
            LazyRow(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                itemsIndexed(gallery.images, key = { item, url -> "$item-$url" }) { item, url ->
                    GalleryThumbnail(url, groupIndex, item, gallery.title, open, Modifier.width(132.dp))
                }
            }
        else
            Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                gallery.images.chunked(3).forEachIndexed { row, urls ->
                    Row(
                        Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.spacedBy(8.dp),
                    ) {
                        urls.forEachIndexed { column, url ->
                            val item = row * 3 + column
                            GalleryThumbnail(
                                url,
                                groupIndex,
                                item,
                                gallery.title,
                                open,
                                Modifier.weight(1f),
                            )
                        }
                        repeat(3 - urls.size) { Spacer(Modifier.weight(1f)) }
                    }
                }
            }
    }
}

@Composable
private fun GalleryThumbnail(
    url: String,
    group: Int,
    item: Int,
    title: String,
    open: (String) -> Unit,
    modifier: Modifier,
) {
    KivoImage(
        url,
        "${title.ifBlank { "角色图集" }} ${item+1}",
        modifier.height(156.dp)
            .testTag("gallery_image_${group}_$item")
            .clip(RoundedCornerShape(12.dp))
            .clickable { open(url) },
        ContentScale.Fit,
    )
}

@Composable
private fun MediaEntry(id: Int, spine: Boolean, repository: CharacterRepository, open: () -> Unit) {
    var retry by rememberSaveable(id) { mutableIntStateOf(0) }
    val state by
        produceState(LoadState<CharacterMedia>(), id, spine, retry) {
            repository.media(id, spine).collect { value = it }
        }
    KivoCard(
        modifier = Modifier.testTag("media_$id"),
        onClick = if (state.value != null) open else null,
    ) {
        Column(Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
            Row(
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(14.dp),
            ) {
                Icon(
                    if (spine) Icons.Outlined.Animation else Icons.Outlined.ViewInAr,
                    null,
                    Modifier.size(30.dp),
                    MaterialTheme.colorScheme.primary,
                )
                Column(Modifier.weight(1f)) {
                    Text(
                        state.value?.label ?: "读取素材 $id…",
                        style = MaterialTheme.typography.titleMedium,
                    )
                    Text(
                        when (state.value?.type) {
                            "home" -> "回忆大厅 · Spine"
                            "spr" -> "动态立绘 · Spine"
                            "halo" -> "光环 · 3D"
                            "body" -> "人物 · 3D"
                            else -> "素材 #$id"
                        },
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                }
                Icon(Icons.Outlined.PlayCircle, "打开动态预览")
            }
            SectionStatus(state) { retry++ }
        }
    }
}
