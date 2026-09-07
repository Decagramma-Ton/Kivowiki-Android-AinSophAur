package wiki.kivo.feature.character

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.unit.dp
import wiki.kivo.core.data.CharacterRepository
import wiki.kivo.core.designsystem.*
import wiki.kivo.core.model.*

internal fun battleParts(
    profile: CharacterProfile,
    battle: BattleProfile?,
    translation: TranslationMode,
    vm: CharacterViewModel,
    open: (ContentCard) -> Unit,
    link: (String) -> Unit,
    image: (String) -> Unit,
    video: (String) -> Unit,
    login: () -> Unit,
): List<DetailPart> = buildList {
    add(
        DetailPart("release", "实装状态") {
            KivoCard {
                Column(Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
                    if (profile.isNpc) Text("这是 NPC 档案，暂无可操作角色的实装数据。")
                    else
                        FieldRows(
                            profile.releases.map {
                                InfoField(
                                    it.server,
                                    if (it.installed) "已实装 · ${it.date.ifBlank{"日期待补充"}}"
                                    else "未实装",
                                )
                            }
                        )
                }
            }
        }
    )
    if (battle == null || !battle.hasBattleData) {
        add(
            DetailPart("no-battle") {
                Text(
                    "暂无战斗数据。可以继续查看资料、鉴赏和已收录的语音。",
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
        )
        return@buildList
    }
    add(
        DetailPart("battle", "战斗定位") {
            KivoCard {
                Column(Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(16.dp)) {
                    FieldRows(
                        listOf(
                            InfoField(
                                "部队 / 站位",
                                "${battle.team} · ${CharacterFilters.label("team_position",battle.position)}",
                            ),
                            InfoField("职能", CharacterFilters.label("type", battle.type)),
                            InfoField(
                                "攻击 / 防御",
                                "${CharacterFilters.label("attack_attribute",battle.attack)} · ${CharacterFilters.label("defensive_attributes",battle.defense)}",
                            ),
                            InfoField(
                                "稀有度 / 获取",
                                "${"★".repeat(battle.rarity.coerceIn(0,5))} · ${if(battle.limited)"限定" else "常驻"}",
                            ),
                            InfoField("群控能力", if (battle.groupControl) "有" else "无"),
                        )
                    )
                    Row(
                        Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.spacedBy(8.dp),
                    ) {
                        battle.terrains.forEach { terrain ->
                            Surface(
                                Modifier.weight(1f),
                                color = MaterialTheme.colorScheme.surfaceVariant,
                                shape = RoundedCornerShape(12.dp),
                            ) {
                                Column(
                                    Modifier.padding(12.dp),
                                    horizontalAlignment = Alignment.CenterHorizontally,
                                ) {
                                    Text(
                                        terrain.label,
                                        style = MaterialTheme.typography.labelMedium,
                                    )
                                    Text(
                                        terrain.value,
                                        style = MaterialTheme.typography.headlineSmall,
                                        color = MaterialTheme.colorScheme.primary,
                                    )
                                }
                            }
                        }
                    }
                }
            }
        }
    )
    fun appendSkills(skills: List<CharacterSkill>, prefix: String, label: String, depth: Int = 0) {
        skills.forEachIndexed { index, skill ->
            val key = "$prefix-$index"
            add(
                DetailPart(key, if (index == 0 && depth == 0) label else null) {
                    SkillCard(
                        skill,
                        translation,
                        vm.repository,
                        open,
                        link,
                        image,
                        video,
                        if (depth > 0) "衍生技能" else label,
                        key,
                        label == "EX 技能" || label == "固有武器强化技能",
                    )
                }
            )
            // 衍生技能保留父分类的图标色，标题仍由 depth 显示为“衍生技能”。
            appendSkills(skill.derived, "$key-derived", label, depth + 1)
        }
    }
    appendSkills(battle.ex, "${battle.style}-ex", "EX 技能")
    appendSkills(battle.passive, "${battle.style}-passive", "普通 / 被动 / 辅助技能")
    add(DetailPart("skill-reactions") { ReactionRow(profile.skillDeclare, vm, login) })
    battle.equipment.forEachIndexed { index, id ->
        add(
            DetailPart("equipment-$id", if (index == 0) "装备信息" else null) {
                EquipmentCard(id, translation, vm.repository, link, image)
            }
        )
    }
    if (battle.favoriteEquipment > 0)
        add(
            DetailPart("favorite-equipment", "爱用品") {
                EquipmentCard(battle.favoriteEquipment, translation, vm.repository, link, image)
            }
        )
    fun itemRows(ids: List<Int>, key: String, title: String, favor: Map<Int, Int> = emptyMap()) {
        if (ids.isEmpty()) return
        ids.chunked(3).forEachIndexed { rowIndex, row ->
            add(
                DetailPart("$key-$rowIndex", if (rowIndex == 0) title else null) {
                    Row(
                        Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.spacedBy(10.dp),
                    ) {
                        row.forEach { id ->
                            Box(Modifier.weight(1f)) {
                                ItemTile(id, translation, vm.repository, open, favor[id])
                            }
                        }
                        repeat(3 - row.size) { Spacer(Modifier.weight(1f)) }
                    }
                }
            )
        }
    }
    itemRows(battle.materials, "material", "养成所需素材")
    itemRows(
        profile.gifts.map { it.id },
        "gift",
        "喜欢的礼物",
        profile.gifts.associate { it.id to it.favorability },
    )
    itemRows(profile.furniture, "furniture", "可以互动的家具")
    if (battle.weapon.name.original.isNotBlank() || battle.weapon.name.cn.isNotBlank())
        add(DetailPart("weapon", "专属武器") { WeaponCard(battle.weapon, translation, link, image) })
    appendSkills(battle.weapon.skills, "${battle.style}-weapon-skill", "固有武器强化技能")
    add(DetailPart("stats", "基础数据") { BasicStats(battle) })
}

@Composable
private fun SkillCard(
    skill: CharacterSkill,
    translation: TranslationMode,
    repository: CharacterRepository,
    open: (ContentCard) -> Unit,
    link: (String) -> Unit,
    image: (String) -> Unit,
    video: (String) -> Unit,
    label: String,
    key: String,
    redIcon: Boolean,
) {
    val max = LocalKivoSettings.current.levelMax
    // 各技能、衍生技能独立使用响应中的可用等级，避免把 5 级父技能强行套到 10 级衍生技能上。
    var level by
        rememberSaveable(key, skill.levels.size) {
            mutableIntStateOf(if (max) (skill.levels.size - 1).coerceAtLeast(0) else 0)
        }
    val info = skill.levels.getOrNull(level)
    val exSkill = label == "EX 技能"
    // 前台技能图标为白色透明 PNG：采用站内原色，避免明暗主题容器色降低白色轮廓对比度。
    val accentContainer = if (redIcon) Color(0xFFE54933) else Color(0xFF6172F4)
    val accentContent =
        if (exSkill) MaterialTheme.colorScheme.onErrorContainer
        else MaterialTheme.colorScheme.onPrimaryContainer
    KivoCard {
        Column(
            Modifier.padding(16.dp).testTag("skill_$key"),
            verticalArrangement = Arrangement.spacedBy(12.dp),
        ) {
            Row(
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(12.dp),
            ) {
                // 底色直接位于透明图标下方，不在图片内部重复绘制灰色占位底。
                Surface(
                    modifier = Modifier.size(58.dp),
                    shape = RoundedCornerShape(14.dp),
                    color = accentContainer,
                ) {
                    KivoImage(
                        skill.icon,
                        null,
                        Modifier.padding(6.dp).fillMaxSize().clip(RoundedCornerShape(9.dp)),
                        ContentScale.Fit,
                        backgroundColor = Color.Transparent,
                    )
                }
                Column(Modifier.weight(1f)) {
                    Text(
                        label,
                        style = MaterialTheme.typography.labelSmall,
                        color = accentContent,
                    )
                    Text(
                        skill.title.display(translation),
                        style = MaterialTheme.typography.titleMedium,
                    )
                }
            }
            if (!skill.passive && info?.cost != null)
                Text(
                    "COST ${info.cost}",
                    style = MaterialTheme.typography.labelLarge,
                    color = MaterialTheme.colorScheme.primary,
                )
            if (info != null) WikiBody(info.description.display(translation), link, image)
            else Text("技能说明待补充")
            LevelControl("技能等级", skill.levels.size, level) { level = it }
            if (skill.maxLevel > skill.levels.size)
                Text(
                    "已收录 ${skill.levels.size} / ${skill.maxLevel} 级说明",
                    style = MaterialTheme.typography.bodySmall,
                )
            if (skill.linkedStudent > 0)
                LinkedCharacter(skill.linkedStudent, repository, translation, open)
            skill.preview?.let { url ->
                OutlinedButton({ video(url) }, Modifier.fillMaxWidth()) {
                    Icon(Icons.Outlined.PlayCircleOutline, null)
                    Spacer(Modifier.width(8.dp))
                    Text("播放技能演示")
                }
            }
        }
    }
}

@Composable
private fun LinkedCharacter(
    id: Int,
    repository: CharacterRepository,
    translation: TranslationMode,
    open: (ContentCard) -> Unit,
) {
    val state by
        produceState(LoadState<CharacterProfile>(), id) {
            repository.profile(id).collect { value = it }
        }
    val student = state.value?.student?.translated(translation)
    TextButton({
        open(student?.card() ?: ContentCard(EntityKey(EntityType.STUDENT, id), "关联角色"))
    }) {
        Icon(Icons.Outlined.Link, null)
        Spacer(Modifier.width(8.dp))
        Text("联动角色 · ${student?.displayName ?: "编号 $id"}")
    }
}

@Composable
private fun EquipmentCard(
    id: Int,
    translation: TranslationMode,
    repository: CharacterRepository,
    link: (String) -> Unit,
    image: (String) -> Unit,
) {
    var retry by remember { mutableIntStateOf(0) }
    val state by
        produceState(LoadState<CharacterEquipment>(), id, retry) {
            repository.equipment(id).collect { value = it }
        }
    val equipment = state.value
    val max = LocalKivoSettings.current.levelMax
    var level by
        rememberSaveable(id, equipment?.levels?.size) {
            mutableIntStateOf(
                if (max) (equipment?.levels?.size?.minus(1) ?: 0).coerceAtLeast(0) else 0
            )
        }
    KivoCard {
        Column(Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
            SectionStatus(state) { retry++ }
            equipment?.let { e ->
                val tier = e.levels.getOrNull(level)
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(12.dp),
                ) {
                    KivoImage(tier?.icon, null, Modifier.size(58.dp), ContentScale.Fit)
                    Text(e.name.display(translation), style = MaterialTheme.typography.titleMedium)
                }
                LevelControl(if (e.favorite) "爱用品 T" else "装备 T", e.levels.size, level) {
                    level = it
                }
                tier?.let { WikiBody(it.description.display(translation), link, image) }
            }
        }
    }
}

@Composable
private fun ItemTile(
    id: Int,
    translation: TranslationMode,
    repository: CharacterRepository,
    open: (ContentCard) -> Unit,
    favorability: Int?,
) {
    var retry by remember { mutableIntStateOf(0) }
    val state by
        produceState(LoadState<CharacterItem>(), id, retry) {
            repository.item(id).collect { value = it }
        }
    val item = state.value
    KivoCard(
        onClick = {
            if (item == null) retry++
            else
                open(
                    ContentCard(
                        EntityKey(EntityType.ITEM, id),
                        item.name.display(translation),
                        item.icon,
                    )
                )
        }
    ) {
        Column(
            Modifier.fillMaxWidth().padding(10.dp),
            verticalArrangement = Arrangement.spacedBy(8.dp),
            horizontalAlignment = Alignment.CenterHorizontally,
        ) {
            KivoImage(item?.icon, null, Modifier.size(60.dp), ContentScale.Fit)
            Text(
                item?.name?.display(translation)
                    ?: if (state.error != null) "点按重试 · $id" else "读取中…",
                style = MaterialTheme.typography.labelMedium,
            )
            if (favorability != null)
                Text(
                    "好感度 +$favorability",
                    style = MaterialTheme.typography.labelSmall,
                    color = MaterialTheme.colorScheme.primary,
                )
        }
    }
}

@Composable
private fun WeaponCard(
    weapon: CharacterWeapon,
    translation: TranslationMode,
    link: (String) -> Unit,
    image: (String) -> Unit,
) {
    val max = LocalKivoSettings.current.levelMax
    var stage by
        rememberSaveable(weapon.name.original) {
            mutableIntStateOf(if (max) (weapon.stages.size - 1).coerceAtLeast(0) else 0)
        }
    KivoCard {
        Column(Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
            Text(weapon.name.display(translation), style = MaterialTheme.typography.titleLarge)
            weapon.icon?.let { url ->
                KivoImage(
                    url,
                    "专属武器",
                    Modifier.fillMaxWidth().height(140.dp).clickable { image(url) },
                    ContentScale.Fit,
                )
            }
            WikiBody(weapon.description.display(translation), link, image)
            LevelControl("武器星级", weapon.stages.size, stage) { stage = it }
            weapon.stages.getOrNull(stage)?.let {
                Text(it.title.ifBlank { "暂无强化效果" }, style = MaterialTheme.typography.titleSmall)
                WikiBody(it.description, link, image)
            }
        }
    }
}

@Composable
private fun BasicStats(battle: BattleProfile) {
    var group by rememberSaveable(battle.style) { mutableIntStateOf(0) }
    KivoCard {
        Column(Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
            Text(
                "原始基础值，不包含装备、羁绊或技能加成。",
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
            if (battle.stats.size > 1) LevelControl("数据组", battle.stats.size, group) { group = it }
            FieldRows(battle.stats.getOrNull(group).orEmpty())
        }
    }
}
