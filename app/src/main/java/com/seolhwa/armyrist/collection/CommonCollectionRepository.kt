package com.seolhwa.armyrist.collection

import android.content.Context
import android.net.Uri
import org.json.JSONArray
import org.json.JSONObject
import java.io.File
import java.util.UUID

/**
 * Common Armyrist document-folder infrastructure.
 *
 * Folder membership is kept outside each tool's domain model on purpose.
 * A tool only exposes stable document IDs. This lets Counting / Checklist /
 * TimePlan reuse the same folder contract without changing their persistence schemas.
 */
enum class CollectionToolType {
    TIME_PLAN,
    COUNTING,
    CHECKLIST
}

data class ArmyristCollectionFolder(
    val id: String,
    val toolType: CollectionToolType,
    val name: String,
    val coverImagePath: String? = null,
    val memberIds: List<String> = emptyList(),
    val order: Int = 0,
    val createdAt: Long = System.currentTimeMillis(),
    val updatedAt: Long = System.currentTimeMillis()
)

class CommonCollectionRepository(context: Context) {
    private val appContext = context.applicationContext
    private val prefs = appContext.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
    private val coverDir = File(appContext.filesDir, "armyrist_collection_covers").apply { mkdirs() }

    @Synchronized
    fun folders(toolType: CollectionToolType): List<ArmyristCollectionFolder> =
        load().filter { it.toolType == toolType }.sortedBy { it.order }

    @Synchronized
    fun getFolder(folderId: String): ArmyristCollectionFolder? =
        load().firstOrNull { it.id == folderId }

    @Synchronized
    fun folderForMember(toolType: CollectionToolType, memberId: String): ArmyristCollectionFolder? =
        folders(toolType).firstOrNull { memberId in it.memberIds }

    @Synchronized
    fun createFolder(
        toolType: CollectionToolType,
        name: String,
        memberIds: List<String>
    ): ArmyristCollectionFolder? {
        val normalized = name.trim().takeIf { it.isNotEmpty() } ?: return null
        val distinctMembers = memberIds.filter { it.isNotBlank() }.distinct()
        if (distinctMembers.isEmpty()) return null

        val current = load().toMutableList()

        // A document can belong to at most one folder for the same tool.
        val detached = current.map { folder ->
            if (folder.toolType == toolType) {
                folder.copy(memberIds = folder.memberIds.filterNot { it in distinctMembers })
            } else folder
        }.toMutableList()

        val nextOrder = detached.filter { it.toolType == toolType }.maxOfOrNull { it.order }?.plus(1) ?: 0
        val now = System.currentTimeMillis()
        val folder = ArmyristCollectionFolder(
            id = UUID.randomUUID().toString(),
            toolType = toolType,
            name = normalized,
            memberIds = distinctMembers,
            order = nextOrder,
            createdAt = now,
            updatedAt = now
        )
        detached += folder

        return if (persist(normalizeOrders(detached))) folder else null
    }

    @Synchronized
    fun renameFolder(folderId: String, name: String): Boolean {
        val normalized = name.trim().takeIf { it.isNotEmpty() } ?: return false
        val current = load()
        if (current.none { it.id == folderId }) return false
        val now = System.currentTimeMillis()
        return persist(
            current.map {
                if (it.id == folderId) it.copy(name = normalized, updatedAt = now) else it
            }
        )
    }

    /**
     * Delete the folder container only. Member documents are deliberately preserved
     * and become unfiled. This is the safe default for Armyrist.
     */
    @Synchronized
    fun deleteFolder(folderId: String): Boolean {
        val current = load()
        val target = current.firstOrNull { it.id == folderId } ?: return false
        val next = current.filterNot { it.id == folderId }
        if (!persist(normalizeOrders(next))) return false
        target.coverImagePath?.let(::deleteCoverFile)
        return true
    }

    @Synchronized
    fun moveMembers(
        toolType: CollectionToolType,
        memberIds: List<String>,
        targetFolderId: String?
    ): Boolean {
        val moving = memberIds.filter { it.isNotBlank() }.distinct()
        if (moving.isEmpty()) return false

        val current = load()
        val target = targetFolderId?.let { id ->
            current.firstOrNull { it.id == id && it.toolType == toolType }
        }
        if (targetFolderId != null && target == null) return false

        val now = System.currentTimeMillis()
        val detached = current.map { folder ->
            if (folder.toolType == toolType) {
                folder.copy(
                    memberIds = folder.memberIds.filterNot { it in moving },
                    updatedAt = if (folder.memberIds.any { it in moving }) now else folder.updatedAt
                )
            } else folder
        }

        val attached = detached.map { folder ->
            if (folder.id == targetFolderId) {
                folder.copy(
                    memberIds = (folder.memberIds + moving).distinct(),
                    updatedAt = now
                )
            } else folder
        }

        return persist(attached)
    }

    @Synchronized
    fun replaceMemberOrder(folderId: String, orderedMemberIds: List<String>): Boolean {
        val current = load()
        val target = current.firstOrNull { it.id == folderId } ?: return false
        if (orderedMemberIds.distinct().size != orderedMemberIds.size) return false
        if (orderedMemberIds.toSet() != target.memberIds.toSet()) return false

        return persist(
            current.map {
                if (it.id == folderId) {
                    it.copy(memberIds = orderedMemberIds, updatedAt = System.currentTimeMillis())
                } else it
            }
        )
    }

    @Synchronized
    fun importCoverImage(folderId: String, uri: Uri): Boolean {
        val current = load()
        val target = current.firstOrNull { it.id == folderId } ?: return false

        val file = File(coverDir, "${folderId}_${System.currentTimeMillis()}.img")
        val copied = runCatching {
            appContext.contentResolver.openInputStream(uri)?.use { input ->
                file.outputStream().use { output -> input.copyTo(output) }
            } ?: error("Unable to open selected image.")
            file.length() > 0L
        }.getOrDefault(false)

        if (!copied) {
            file.delete()
            return false
        }

        val next = current.map {
            if (it.id == folderId) {
                it.copy(
                    coverImagePath = file.absolutePath,
                    updatedAt = System.currentTimeMillis()
                )
            } else it
        }

        if (!persist(next)) {
            file.delete()
            return false
        }

        target.coverImagePath?.let(::deleteCoverFile)
        return true
    }

    @Synchronized
    fun clearCoverImage(folderId: String): Boolean {
        val current = load()
        val target = current.firstOrNull { it.id == folderId } ?: return false
        val next = current.map {
            if (it.id == folderId) {
                it.copy(coverImagePath = null, updatedAt = System.currentTimeMillis())
            } else it
        }
        if (!persist(next)) return false
        target.coverImagePath?.let(::deleteCoverFile)
        return true
    }

    private fun deleteCoverFile(path: String) {
        runCatching {
            val file = File(path)
            if (file.canonicalPath.startsWith(coverDir.canonicalPath)) file.delete()
        }
    }

    private fun normalizeOrders(
        input: List<ArmyristCollectionFolder>
    ): List<ArmyristCollectionFolder> {
        val groups = input.groupBy { it.toolType }
        val normalizedById = buildMap {
            groups.forEach { (_, values) ->
                values.sortedBy { it.order }.forEachIndexed { index, folder ->
                    put(folder.id, folder.copy(order = index))
                }
            }
        }
        return input.map { normalizedById[it.id] ?: it }
    }

    private fun load(): List<ArmyristCollectionFolder> {
        val raw = prefs.getString(KEY_FOLDERS, null) ?: return emptyList()
        return runCatching {
            val root = JSONObject(raw)
            val array = root.optJSONArray("folders") ?: JSONArray()
            buildList {
                for (i in 0 until array.length()) {
                    val obj = array.optJSONObject(i) ?: continue
                    val tool = runCatching {
                        CollectionToolType.valueOf(obj.getString("toolType"))
                    }.getOrNull() ?: continue

                    val memberArray = obj.optJSONArray("memberIds") ?: JSONArray()
                    val members = buildList {
                        for (j in 0 until memberArray.length()) {
                            val id = memberArray.optString(j)
                            if (id.isNotBlank()) add(id)
                        }
                    }

                    add(
                        ArmyristCollectionFolder(
                            id = obj.getString("id"),
                            toolType = tool,
                            name = obj.optString("name", "폴더"),
                            coverImagePath = obj.optString("coverImagePath").takeIf { it.isNotBlank() },
                            memberIds = members.distinct(),
                            order = obj.optInt("order", i),
                            createdAt = obj.optLong("createdAt", 0L),
                            updatedAt = obj.optLong("updatedAt", 0L)
                        )
                    )
                }
            }
        }.getOrDefault(emptyList())
    }

    private fun persist(folders: List<ArmyristCollectionFolder>): Boolean {
        val root = JSONObject()
            .put("schemaVersion", SCHEMA_VERSION)
            .put(
                "folders",
                JSONArray().apply {
                    folders.forEach { folder ->
                        put(
                            JSONObject()
                                .put("id", folder.id)
                                .put("toolType", folder.toolType.name)
                                .put("name", folder.name)
                                .put("coverImagePath", folder.coverImagePath ?: "")
                                .put("memberIds", JSONArray(folder.memberIds))
                                .put("order", folder.order)
                                .put("createdAt", folder.createdAt)
                                .put("updatedAt", folder.updatedAt)
                        )
                    }
                }
            )

        return prefs.edit().putString(KEY_FOLDERS, root.toString()).commit()
    }

    companion object {
        private const val PREFS_NAME = "armyrist_common_collections"
        private const val KEY_FOLDERS = "folders_v1"
        private const val SCHEMA_VERSION = 1
    }
}
