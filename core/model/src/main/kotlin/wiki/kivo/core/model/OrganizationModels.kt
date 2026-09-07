package wiki.kivo.core.model

/** 组织与关系使用不同实体身份；关系并不必然是社团，也可能跨越多个组织。 */
data class OrganizationProfile(
    val id: Int,
    val name: WikiText,
    val logo: String?,
    val cover: String?,
    val description: String,
    val maps: List<OrganizationMap>,
    val students: List<Student>,
    val relations: List<Int>,
) {
    fun card(translation: TranslationMode) =
        ContentCard(EntityKey(EntityType.SCHOOL, id), name.display(translation), logo)
}

data class OrganizationMap(val name: String, val image: String?, val landmarks: List<Landmark>)

data class Landmark(val name: String, val description: String, val x: Float?, val y: Float?)

data class RelationProfile(
    val id: Int,
    val name: WikiText,
    val image: String?,
    val description: String,
    val mainStudents: List<Student>,
    val secondaryStudents: List<Student>,
) {
    fun card(translation: TranslationMode) =
        ContentCard(EntityKey(EntityType.RELATION, id), name.display(translation), image)

    // 保留换装的独立身份，不按姓名误合并同名角色；主要归属优先展示。
    fun members(exclude: Int? = null): List<Student> =
        (mainStudents + secondaryStudents).distinctBy { it.id }.filterNot { it.id == exclude }
}
