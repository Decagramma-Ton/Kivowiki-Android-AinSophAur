package wiki.kivo.core.media

import java.io.File

/**
 * 重媒体磁盘淘汰器。调用方持有下载互斥锁，并在 IO 线程调用。 骨架/模型以完整依赖包为单位，downloads 按单文件淘汰；收藏和用户导出不在此目录。
 * 正在使用的包暂时允许超额，释放后再次整理。无上限时不扫描磁盘。
 */
internal object MediaCacheEviction {
    fun trim(root: File, budget: Long, protected: Set<File> = emptySet()) {
        require(budget >= 0)
        if (budget == Long.MAX_VALUE) return
        val canonicalRoot = root.canonicalFile
        val pinned = protected.map { it.canonicalFile }.toSet()
        val candidates =
            root
                .listFiles()
                .orEmpty()
                .flatMap {
                    if (it.name == "downloads" && it.isDirectory) it.listFiles().orEmpty().toList()
                    else listOf(it)
                }
                .sortedBy { it.lastModified() }
        fun size(file: File) = file.walkTopDown().filter { it.isFile }.sumOf { it.length() }
        var bytes = size(root)
        for (file in candidates) {
            if (bytes <= budget) break
            val canonical = file.canonicalFile
            if (
                canonical in pinned ||
                    !canonical.path.startsWith(canonicalRoot.path + File.separator)
            )
                continue
            val length = size(file)
            // 仅在确实删除成功后扣减，磁盘失败不能被当作已经满足预算。
            if (file.deleteRecursively()) bytes -= length
        }
    }
}
