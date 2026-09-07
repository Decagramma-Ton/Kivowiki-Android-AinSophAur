package wiki.kivo.core.media

import java.nio.ByteBuffer
import java.nio.ByteOrder
import kotlin.math.*
import kotlinx.serialization.json.*

data class GltfPackage(
    val document: JsonObject,
    val resources: Map<String, ByteArray>,
    val hasMouth: Boolean = false,
) {
    fun jsonBuffer() = ByteBuffer.wrap(document.toString().toByteArray())
}

internal fun obj(vararg pairs: Pair<String, JsonElement>) = JsonObject(mapOf(*pairs))

internal fun num(value: Number) = JsonPrimitive(value)

internal fun str(value: String) = JsonPrimitive(value)

internal fun nums(vararg values: Number) = JsonArray(values.map(::num))

internal fun JsonObject.arr(key: String) = (get(key) as? JsonArray).orEmpty()

internal fun JsonObject.int(key: String, default: Int = 0) =
    (get(key) as? JsonPrimitive)?.intOrNull ?: default

internal fun JsonObject.string(key: String) = (get(key) as? JsonPrimitive)?.contentOrNull.orEmpty()

internal fun JsonObject.replace(vararg pairs: Pair<String, JsonElement>) =
    JsonObject(toMutableMap().apply { putAll(pairs) })

/** 有界 GLB2 解包；保持所有节点、蒙皮、动画、扩展与内嵌图片，不将 GLB 当静态 OBJ。 */
object CharacterGltf {
    fun read(prepared: PreparedCharacterMedia): GltfPackage =
        when (prepared.file.extension.lowercase()) {
            "obj" ->
                CharacterObj.convert(
                    prepared.file.readText(),
                    prepared.companion?.readText(),
                    prepared.textures.associate { it.name to it.readBytes() },
                )
            "glb" -> glb(prepared.file.readBytes())
            "gltf" ->
                GltfPackage(
                    Json.parseToJsonElement(prepared.file.readText()).jsonObject,
                    emptyMap(),
                )
            else -> error("不支持的模型格式：${prepared.file.extension}")
        }

    fun glb(bytes: ByteArray): GltfPackage {
        require(bytes.size in 20..96 * 1024 * 1024) { "模型文件大小无效" }
        val input = ByteBuffer.wrap(bytes).order(ByteOrder.LITTLE_ENDIAN)
        require(input.int == 0x46546c67 && input.int == 2 && input.int == bytes.size) {
            "GLB2 文件头或长度不一致"
        }
        var json: JsonObject? = null
        var binary: ByteArray? = null
        while (input.remaining() > 0) {
            require(input.remaining() >= 8) { "GLB 区块头不完整" }
            val length = input.int
            val type = input.int
            require(length >= 0 && length % 4 == 0 && length <= input.remaining()) { "GLB 区块长度不合法" }
            val chunk = ByteArray(length)
            input.get(chunk)
            when (type) {
                0x4e4f534a -> {
                    require(json == null && binary == null)
                    json = Json.parseToJsonElement(String(chunk).trimEnd(' ', '\u0000')).jsonObject
                }
                0x004e4942 -> {
                    require(binary == null && json != null)
                    binary = chunk
                }
            }
        }
        val document = requireNotNull(json) { "GLB 缺少场景信息" }
        val resources = mutableMapOf<String, ByteArray>()
        val buffers =
            document.arr("buffers").mapIndexed { index, element ->
                val buffer = element.jsonObject
                if (buffer.string("uri").isEmpty()) {
                    require(
                        index == 0 && binary != null && buffer.int("byteLength") <= binary.size
                    ) {
                        "GLB 缓冲缺失"
                    }
                    resources["kivo-body.bin"] = binary
                    buffer.replace("uri" to str("kivo-body.bin"))
                } else buffer
            }
        return GltfPackage(document.replace("buffers" to JsonArray(buffers)), resources)
    }

    /** 与网页同样使用不受场景光照影响的游戏贴图。保留纹理坐标、透明参数与原材质名称。 */
    fun unlit(source: GltfPackage): GltfPackage {
        val materials =
            source.document.arr("materials").map { value ->
                val m = value.jsonObject
                val extensions =
                    (m["extensions"] as? JsonObject ?: obj()).replace(
                        "KHR_materials_unlit" to obj()
                    )
                m.replace("extensions" to extensions, "doubleSided" to JsonPrimitive(true))
            }
        return source.copy(
            document =
                source.document.replace(
                    "materials" to JsonArray(materials),
                    "extensionsUsed" to
                        JsonArray(
                            (source.document.arr("extensionsUsed") + str("KHR_materials_unlit"))
                                .distinct()
                        ),
                )
        )
    }

    /** 按网页的 EyeMouth/Mouth 材质和连通几何定位嘴部，避免把整张眼部贴图一起替换。 只拆三角形索引；权重、关节、UV、morph 和原动画均继续使用源数据。 */
    fun mouth(source: GltfPackage, texture: ByteArray, cell: Int): GltfPackage {
        require(cell in 0..63)
        val doc = source.document
        val accessors = doc.arr("accessors").toMutableList()
        val views = doc.arr("bufferViews").toMutableList()
        val buffers = doc.arr("buffers").toMutableList()
        val materials = doc.arr("materials").toMutableList()
        val images = doc.arr("images").toMutableList()
        val textures = doc.arr("textures").toMutableList()
        val resources = source.resources.toMutableMap()
        var applied = false
        fun readAccessor(index: Int, components: Int): List<DoubleArray> {
            val a = accessors[index].jsonObject
            require(a["sparse"] == null) { "稀疏嘴部数据暂不支持" }
            val v = views[a.int("bufferView")].jsonObject
            val b =
                source.resources[buffers[v.int("buffer")].jsonObject.string("uri")]
                    ?: error("模型外部缓冲未就绪")
            val type = a.int("componentType")
            val size =
                when (type) {
                    5121 -> 1
                    5123 -> 2
                    5125,
                    5126 -> 4
                    else -> error("嘴部数据类型不支持")
                }
            val start = v.int("byteOffset") + a.int("byteOffset")
            val stride = v.int("byteStride", components * size)
            val count = a.int("count")
            require(
                count in 1..1_000_000 &&
                    stride >= components * size &&
                    start >= 0 &&
                    start.toLong() + (count - 1L) * stride + components * size <= b.size
            ) {
                "模型属性越界"
            }
            val input = ByteBuffer.wrap(b).order(ByteOrder.LITTLE_ENDIAN)
            return List(count) { i ->
                input.position(start + i * stride)
                DoubleArray(components) {
                    when (type) {
                        5121 -> (input.get().toInt() and 255).toDouble()
                        5123 -> (input.short.toInt() and 65535).toDouble()
                        5125 -> (input.int.toLong() and 0xffffffffL).toDouble()
                        else -> input.float.toDouble()
                    }.also { require(it.isFinite()) }
                }
            }
        }
        fun indices(values: List<Int>): Int {
            val uri = "kivo-mouth-indices-${accessors.size}.bin"
            val bytes = ByteBuffer.allocate(values.size * 4).order(ByteOrder.LITTLE_ENDIAN)
            values.forEach(bytes::putInt)
            resources[uri] = bytes.array()
            buffers += obj("uri" to str(uri), "byteLength" to num(bytes.capacity()))
            views +=
                obj(
                    "buffer" to num(buffers.lastIndex),
                    "byteLength" to num(bytes.capacity()),
                    "target" to num(34963),
                )
            accessors +=
                obj(
                    "bufferView" to num(views.lastIndex),
                    "componentType" to num(5125),
                    "count" to num(values.size),
                    "type" to str("SCALAR"),
                )
            return accessors.lastIndex
        }
        val meshes =
            doc.arr("meshes").map { element ->
                val mesh = element.jsonObject
                mesh.replace(
                    "primitives" to
                        JsonArray(
                            mesh.arr("primitives").flatMap { p ->
                                val primitive = p.jsonObject
                                val material =
                                    materials
                                        .getOrNull(primitive.int("material", -1))
                                        ?.jsonObject
                                        ?.string("name")
                                        ?.lowercase()
                                        .orEmpty()
                                val attrs = primitive["attributes"]?.jsonObject
                                val eligible =
                                    !applied &&
                                        listOf("_eyemouth", "_mouth", "_eyemoutn")
                                            .any(material::endsWith) &&
                                        attrs?.get("JOINTS_0") != null &&
                                        primitive["indices"] != null &&
                                        primitive.int("mode", 4) == 4
                                if (!eligible) listOf(p)
                                else {
                                    val positions =
                                        readAccessor(requireNotNull(attrs).int("POSITION"), 3)
                                    val original =
                                        readAccessor(primitive.int("indices"), 1).map {
                                            it[0].toInt()
                                        }
                                    require(
                                        original.size % 3 == 0 &&
                                            original.all { it in positions.indices }
                                    ) {
                                        "嘴部索引无效"
                                    }
                                    val groups = connectedTriangles(positions, original)
                                    val mouth =
                                        groups.minByOrNull { group ->
                                            val ys = group.map { positions[it][1] }
                                            (ys.min() + ys.max()) / 2
                                        } ?: emptyList()
                                    if (mouth.isEmpty()) listOf(p)
                                    else {
                                        applied = true
                                        images += obj("uri" to str("kivo-mouth.png"))
                                        resources["kivo-mouth.png"] = texture
                                        textures += obj("source" to num(images.lastIndex))
                                        val transform =
                                            obj(
                                                "offset" to nums((cell % 8) / 8f, (cell / 8) / 8f),
                                                "scale" to nums(.5f, .5f),
                                            )
                                        materials +=
                                            obj(
                                                "name" to str("KivoMouth"),
                                                "doubleSided" to JsonPrimitive(true),
                                                "alphaMode" to str("BLEND"),
                                                "extensions" to obj("KHR_materials_unlit" to obj()),
                                                "pbrMetallicRoughness" to
                                                    obj(
                                                        "baseColorTexture" to
                                                            obj(
                                                                "index" to num(textures.lastIndex),
                                                                "extensions" to
                                                                    obj(
                                                                        "KHR_texture_transform" to
                                                                            transform
                                                                    ),
                                                            ),
                                                        "metallicFactor" to num(0),
                                                    ),
                                            )
                                        val rest = groups.filter { it !== mouth }.flatten()
                                        buildList {
                                            if (rest.isNotEmpty())
                                                add(
                                                    primitive.replace(
                                                        "indices" to num(indices(rest))
                                                    )
                                                )
                                            add(
                                                primitive.replace(
                                                    "indices" to num(indices(mouth)),
                                                    "material" to num(materials.lastIndex),
                                                )
                                            )
                                        }
                                    }
                                }
                            }
                        )
                )
            }
        if (!applied) return source
        return GltfPackage(
            doc.replace(
                "meshes" to JsonArray(meshes),
                "accessors" to JsonArray(accessors),
                "bufferViews" to JsonArray(views),
                "buffers" to JsonArray(buffers),
                "materials" to JsonArray(materials),
                "images" to JsonArray(images),
                "textures" to JsonArray(textures),
                "extensionsUsed" to
                    JsonArray(
                        (doc.arr("extensionsUsed") +
                                listOf(str("KHR_materials_unlit"), str("KHR_texture_transform")))
                            .distinct()
                    ),
            ),
            resources,
            true,
        )
    }

    /** 相邻三角形共享两处空间顶点才连通；用边索引替代网页 O(n²) 的两两比较。 */
    fun connectedTriangles(positions: List<DoubleArray>, indices: List<Int>): List<List<Int>> {
        val vertexIds = HashMap<String, Int>()
        val canonical = positions.map { p ->
            val key = p.joinToString(",") { round(it / 1e-6).toLong().toString() }
            vertexIds.getOrPut(key) { vertexIds.size }
        }
        val parent = IntArray(indices.size / 3) { it }
        fun root(start: Int): Int {
            var i = start
            while (parent[i] != i) {
                parent[i] = parent[parent[i]]
                i = parent[i]
            }
            return i
        }
        val edges = HashMap<Long, Int>()
        indices.chunked(3).forEachIndexed { triangle, values ->
            require(values.size == 3)
            for (edge in 0..2) {
                val a = canonical[values[edge]]
                val b = canonical[values[(edge + 1) % 3]]
                val key = (min(a, b).toLong() shl 32) or max(a, b).toLong()
                val previous = edges.putIfAbsent(key, triangle)
                if (previous != null) parent[root(triangle)] = root(previous)
            }
        }
        val groups = linkedMapOf<Int, MutableList<Int>>()
        indices.chunked(3).forEachIndexed { triangle, values ->
            groups.getOrPut(root(triangle)) { mutableListOf() }.addAll(values)
        }
        return groups.values.toList()
    }
}

/** 旧光环 OBJ 只在本地转成标准 glTF。支持多材质、负索引及多边形扇形剖分，拒绝越界面。 */
object CharacterObj {
    fun convert(text: String, mtl: String?, textures: Map<String, ByteArray>): GltfPackage {
        require(text.length <= 32 * 1024 * 1024) { "OBJ 体积过大" }
        val positions = mutableListOf<List<Float>>()
        val uv = mutableListOf<List<Float>>()
        val groups = linkedMapOf<String, MutableList<Pair<Int, Int>>>()
        var material = "default"
        fun resolve(raw: String, size: Int): Int {
            val n = raw.toInt()
            return (if (n < 0) size + n else n - 1).also {
                require(it in 0 until size) { "OBJ 索引越界" }
            }
        }
        for (line in text.lineSequence()) {
            val parts = line.substringBefore('#').trim().split(Regex("\\s+"))
            if (parts.isEmpty()) continue
            when (parts[0]) {
                "v" -> {
                    require(parts.size >= 4)
                    positions +=
                        parts.drop(1).take(3).map {
                            it.toFloat().also { v -> require(v.isFinite()) }
                        }
                    require(positions.size <= 1_000_000)
                }
                "vt" -> {
                    require(parts.size >= 3)
                    uv +=
                        parts.drop(1).take(2).map {
                            it.toFloat().also { v -> require(v.isFinite()) }
                        }
                }
                "usemtl" -> material = parts.drop(1).joinToString(" ")
                "f" -> {
                    val face =
                        parts.drop(1).map {
                            val ref = it.split('/')
                            resolve(ref[0], positions.size) to
                                (ref.getOrNull(1)?.takeIf(String::isNotBlank)?.let { n ->
                                    resolve(n, uv.size)
                                } ?: -1)
                        }
                    require(face.size in 3..1024)
                    val result = groups.getOrPut(material) { mutableListOf() }
                    for (i in 1 until face.lastIndex) result.addAll(
                        listOf(face[0], face[i], face[i + 1])
                    )
                    require(result.size <= 3_000_000)
                }
            }
        }
        require(groups.isNotEmpty()) { "OBJ 没有可显示的面" }
        val maps = mutableMapOf<String, String>()
        var current = "default"
        mtl?.lineSequence()?.forEach { line ->
            val parts = line.trim().split(Regex("\\s+"))
            when (parts[0]) {
                "newmtl" -> current = parts.drop(1).joinToString(" ")
                "map_Kd" ->
                    maps[current] =
                        parts
                            .drop(1)
                            .joinToString(" ")
                            .substringAfterLast('/')
                            .substringAfterLast('\\')
            }
        }
        val buffers = mutableListOf<JsonElement>()
        val views = mutableListOf<JsonElement>()
        val accessors = mutableListOf<JsonElement>()
        val materials = mutableListOf<JsonElement>()
        val images = mutableListOf<JsonElement>()
        val textureRefs = mutableListOf<JsonElement>()
        val primitives = mutableListOf<JsonElement>()
        val resources = mutableMapOf<String, ByteArray>()
        for ((name, triangles) in groups) {
            val bytes = ByteBuffer.allocate(triangles.size * 20).order(ByteOrder.LITTLE_ENDIAN)
            val min = DoubleArray(3) { Double.POSITIVE_INFINITY }
            val max = DoubleArray(3) { Double.NEGATIVE_INFINITY }
            for ((v, t) in triangles) {
                positions[v].forEachIndexed { i, f ->
                    bytes.putFloat(f)
                    min[i] = min(min[i], f.toDouble())
                    max[i] = max(max[i], f.toDouble())
                }
                bytes.putFloat(if (t < 0) 0f else uv[t][0])
                bytes.putFloat(if (t < 0) 0f else 1f - uv[t][1])
            }
            val uri = "kivo-obj-${buffers.size}.bin"
            resources[uri] = bytes.array()
            buffers += obj("uri" to str(uri), "byteLength" to num(bytes.capacity()))
            views +=
                obj(
                    "buffer" to num(buffers.lastIndex),
                    "byteLength" to num(bytes.capacity()),
                    "byteStride" to num(20),
                    "target" to num(34962),
                )
            accessors +=
                obj(
                    "bufferView" to num(views.lastIndex),
                    "componentType" to num(5126),
                    "count" to num(triangles.size),
                    "type" to str("VEC3"),
                    "min" to nums(*min.toTypedArray()),
                    "max" to nums(*max.toTypedArray()),
                )
            val positionIndex = accessors.lastIndex
            accessors +=
                obj(
                    "bufferView" to num(views.lastIndex),
                    "byteOffset" to num(12),
                    "componentType" to num(5126),
                    "count" to num(triangles.size),
                    "type" to str("VEC2"),
                )
            val textureName = maps[name] ?: textures.keys.singleOrNull()
            val color =
                if (textureName != null) {
                    val image = textures[textureName] ?: error("缺少 OBJ 纹理：$textureName")
                    val imageUri =
                        "kivo-obj-texture-${images.size}.${textureName.substringAfterLast('.')}"
                    resources[imageUri] = image
                    images += obj("uri" to str(imageUri))
                    textureRefs += obj("source" to num(images.lastIndex))
                    obj(
                        "baseColorTexture" to obj("index" to num(textureRefs.lastIndex)),
                        "metallicFactor" to num(0),
                    )
                } else obj("metallicFactor" to num(0))
            materials +=
                obj(
                    "name" to str(name),
                    "doubleSided" to JsonPrimitive(true),
                    "alphaMode" to str("BLEND"),
                    "extensions" to obj("KHR_materials_unlit" to obj()),
                    "pbrMetallicRoughness" to color,
                )
            primitives +=
                obj(
                    "attributes" to
                        obj(
                            "POSITION" to num(positionIndex),
                            "TEXCOORD_0" to num(accessors.lastIndex),
                        ),
                    "material" to num(materials.lastIndex),
                )
        }
        return GltfPackage(
            obj(
                "asset" to obj("version" to str("2.0")),
                "scene" to num(0),
                "scenes" to JsonArray(listOf(obj("nodes" to nums(0)))),
                "nodes" to JsonArray(listOf(obj("mesh" to num(0)))),
                "meshes" to JsonArray(listOf(obj("primitives" to JsonArray(primitives)))),
                "buffers" to JsonArray(buffers),
                "bufferViews" to JsonArray(views),
                "accessors" to JsonArray(accessors),
                "materials" to JsonArray(materials),
                "textures" to JsonArray(textureRefs),
                "images" to JsonArray(images),
                "extensionsUsed" to JsonArray(listOf(str("KHR_materials_unlit"))),
            ),
            resources,
        )
    }
}
