package com.seolhwa.armyrist

import android.content.Context
import android.util.Base64
import org.json.JSONArray
import org.json.JSONObject
import java.security.MessageDigest
import java.security.SecureRandom
import java.time.OffsetDateTime
import java.time.format.DateTimeFormatter
import javax.crypto.Cipher
import javax.crypto.SecretKeyFactory
import javax.crypto.spec.GCMParameterSpec
import javax.crypto.spec.PBEKeySpec
import javax.crypto.spec.SecretKeySpec

enum class ArmyristPortableDataType {
    BACKUP,
    COUNTING,
    CHECKLIST,
    TIME_PLAN,
    REPORT_TEMPLATE
}

data class BackupSummary(
    val countingSheets: Int,
    val checklists: Int,
    val timePlans: Int,
    val reportTemplates: Int,
    val userProfileIncluded: Boolean,
    val createdAt: String,
    val encrypted: Boolean
)

data class ValidatedBackup(
    val summary: BackupSummary,
    val countingSnapshot: String,
    val coreSnapshot: String,
    val intervalLabels: Map<String, String>
)

data class ContainerInspection(
    val dataType: ArmyristPortableDataType,
    val encrypted: Boolean,
    val createdAt: String,
    val formatVersion: Int
)

sealed class PortableResult<out T> {
    data class Success<T>(val value: T) : PortableResult<T>()
    data class Error(val message: String) : PortableResult<Nothing>()
}

object ArmyristPortableDataManager {
    const val FORMAT_IDENTIFIER = "ARMYRIST_DATA"
    const val FORMAT_VERSION = 1
    const val PAYLOAD_SCHEMA_VERSION = 1

    private const val COUNTING_PREFS = "armyrist_stage1"
    private const val COUNTING_KEY = "snapshot_v1"

    private const val CORE_PREFS = "armyrist_stage2_core"
    private const val CORE_KEY = "snapshot_v1"

    private const val TIMEPLAN_LABEL_PREFS =
        "armyrist_timeplan_interval_labels"

    private const val JOURNAL_PREFS =
        "armyrist_stage3_restore_journal"
    private const val JOURNAL_KEY = "pending_restore"

    private const val KDF_ITERATIONS = 600_000
    private const val KEY_BITS = 256
    private const val GCM_TAG_BITS = 128

    private val secureRandom = SecureRandom()

    fun recoverInterruptedRestore(context: Context) {
        val prefs = context.getSharedPreferences(
            JOURNAL_PREFS,
            Context.MODE_PRIVATE
        )
        val raw = prefs.getString(JOURNAL_KEY, null) ?: return

        runCatching {
            val journal = JSONObject(raw)
            if (journal.optString("status") != "PREPARED") {
                prefs.edit().remove(JOURNAL_KEY).commit()
                return
            }

            val old = journal.getJSONObject("old")

            restoreStringPreference(
                context,
                COUNTING_PREFS,
                COUNTING_KEY,
                old.optStringOrNull("counting")
            )
            restoreStringPreference(
                context,
                CORE_PREFS,
                CORE_KEY,
                old.optStringOrNull("core")
            )
            restoreStringMap(
                context,
                TIMEPLAN_LABEL_PREFS,
                old.optJSONObject("intervalLabels") ?: JSONObject()
            )

            prefs.edit().remove(JOURNAL_KEY).commit()
        }.onFailure {
            // Keep the journal if recovery itself could not complete.
            // This is safer than silently declaring success.
        }
    }

    fun currentSummary(context: Context): BackupSummary {
        val counting = readCountingRoot(context)
        val core = readCoreRoot(context)

        return BackupSummary(
            countingSheets =
                counting.optJSONArray("sheets")?.length() ?: 0,
            checklists =
                core.optJSONArray("checklists")?.length() ?: 0,
            timePlans =
                core.optJSONArray("timePlans")?.length() ?: 0,
            reportTemplates =
                core.optJSONArray("reportTemplates")?.length() ?: 0,
            userProfileIncluded = true,
            createdAt = OffsetDateTime.now()
                .format(DateTimeFormatter.ISO_OFFSET_DATE_TIME),
            encrypted = false
        )
    }

    fun createFullBackup(
        context: Context,
        password: CharArray?
    ): PortableResult<ByteArray> {
        return runCatching {
            val countingRoot = readCountingRoot(context)
            val coreRoot = readCoreRoot(context)
            val intervalLabels =
                readStringMap(context, TIMEPLAN_LABEL_PREFS)

            val payloadObject = JSONObject()
                .put("schemaVersion", PAYLOAD_SCHEMA_VERSION)
                .put("countingSnapshot", countingRoot)
                .put("coreSnapshot", coreRoot)
                .put(
                    "intervalLabels",
                    JSONObject(intervalLabels as Map<*, *>)
                )

            createContainerBytes(
                dataType = ArmyristPortableDataType.BACKUP,
                payloadBytes =
                    payloadObject.toString().toByteArray(Charsets.UTF_8),
                password = password
            )
        }.fold(
            onSuccess = { PortableResult.Success(it) },
            onFailure = {
                PortableResult.Error(
                    "백업 파일을 생성할 수 없습니다."
                )
            }
        )
    }

    fun inspect(bytes: ByteArray): PortableResult<ContainerInspection> {
        return runCatching {
            require(bytes.isNotEmpty())
            val outer = JSONObject(bytes.toString(Charsets.UTF_8))
            validateOuterMetadata(outer)

            val encryption = outer.getJSONObject("encryption")
            ContainerInspection(
                dataType = ArmyristPortableDataType.valueOf(
                    outer.getString("dataType")
                ),
                encrypted = encryption.getBoolean("enabled"),
                createdAt = outer.getString("createdAt"),
                formatVersion = outer.getInt("formatVersion")
            )
        }.fold(
            onSuccess = { PortableResult.Success(it) },
            onFailure = {
                PortableResult.Error(
                    "지원하는 Armyrist 데이터 파일이 아닙니다."
                )
            }
        )
    }

    fun validateBackup(
        bytes: ByteArray,
        password: CharArray?
    ): PortableResult<ValidatedBackup> {
        return runCatching {
            require(bytes.isNotEmpty())

            val outer = JSONObject(bytes.toString(Charsets.UTF_8))
            validateOuterMetadata(outer)

            require(
                ArmyristPortableDataType.valueOf(
                    outer.getString("dataType")
                ) == ArmyristPortableDataType.BACKUP
            )

            val encryption = outer.getJSONObject("encryption")
            val encrypted = encryption.getBoolean("enabled")
            val payloadBytes = Base64.decode(
                outer.getString("payload"),
                Base64.NO_WRAP
            )

            val plaintext =
                if (encrypted) {
                    require(password != null && password.isNotEmpty())
                    decryptPayload(
                        ciphertext = payloadBytes,
                        password = password,
                        encryption = encryption
                    )
                } else {
                    val expectedHash =
                        outer.optString("payloadHash", "")
                    require(expectedHash.isNotBlank())
                    val actualHash = sha256Hex(payloadBytes)
                    require(
                        actualHash.equals(
                            expectedHash,
                            ignoreCase = true
                        )
                    )
                    payloadBytes
                }

            val payload = JSONObject(
                plaintext.toString(Charsets.UTF_8)
            )
            require(
                payload.getInt("schemaVersion") ==
                    PAYLOAD_SCHEMA_VERSION
            )

            val counting =
                payload.getJSONObject("countingSnapshot")
            val core =
                payload.getJSONObject("coreSnapshot")
            val labels =
                payload.optJSONObject("intervalLabels") ?: JSONObject()

            validateCountingSnapshot(counting)
            validateCoreSnapshot(core)

            val labelMap = linkedMapOf<String, String>()
            labels.keys().forEach { key ->
                labelMap[key] = labels.optString(key, "")
            }

            ValidatedBackup(
                summary = BackupSummary(
                    countingSheets =
                        counting.optJSONArray("sheets")
                            ?.length() ?: 0,
                    checklists =
                        core.optJSONArray("checklists")
                            ?.length() ?: 0,
                    timePlans =
                        core.optJSONArray("timePlans")
                            ?.length() ?: 0,
                    reportTemplates =
                        core.optJSONArray("reportTemplates")
                            ?.length() ?: 0,
                    userProfileIncluded = true,
                    createdAt = outer.getString("createdAt"),
                    encrypted = encrypted
                ),
                countingSnapshot = counting.toString(),
                coreSnapshot = core.toString(),
                intervalLabels = labelMap
            )
        }.fold(
            onSuccess = { PortableResult.Success(it) },
            onFailure = {
                PortableResult.Error(
                    "암호가 올바르지 않거나 파일이 손상되었습니다."
                )
            }
        )
    }

    fun restoreFullBackup(
        context: Context,
        backup: ValidatedBackup
    ): PortableResult<Unit> {
        return runCatching {
            // Re-validate before any mutation.
            val counting = JSONObject(backup.countingSnapshot)
            val core = JSONObject(backup.coreSnapshot)
            validateCountingSnapshot(counting)
            validateCoreSnapshot(core)

            val oldCounting = context.getSharedPreferences(
                COUNTING_PREFS,
                Context.MODE_PRIVATE
            ).getString(COUNTING_KEY, null)

            val oldCore = context.getSharedPreferences(
                CORE_PREFS,
                Context.MODE_PRIVATE
            ).getString(CORE_KEY, null)

            val oldLabels =
                readStringMap(context, TIMEPLAN_LABEL_PREFS)

            val oldObject = JSONObject()
                .put(
                    "counting",
                    oldCounting ?: JSONObject.NULL
                )
                .put(
                    "core",
                    oldCore ?: JSONObject.NULL
                )
                .put(
                    "intervalLabels",
                    JSONObject(oldLabels as Map<*, *>)
                )

            val journal = JSONObject()
                .put("status", "PREPARED")
                .put("old", oldObject)

            val journalPrefs = context.getSharedPreferences(
                JOURNAL_PREFS,
                Context.MODE_PRIVATE
            )

            require(
                journalPrefs.edit()
                    .putString(JOURNAL_KEY, journal.toString())
                    .commit()
            )

            var success = false
            try {
                require(
                    context.getSharedPreferences(
                        COUNTING_PREFS,
                        Context.MODE_PRIVATE
                    ).edit()
                        .putString(
                            COUNTING_KEY,
                            backup.countingSnapshot
                        )
                        .commit()
                )

                require(
                    context.getSharedPreferences(
                        CORE_PREFS,
                        Context.MODE_PRIVATE
                    ).edit()
                        .putString(
                            CORE_KEY,
                            backup.coreSnapshot
                        )
                        .commit()
                )

                val labelsPrefs = context.getSharedPreferences(
                    TIMEPLAN_LABEL_PREFS,
                    Context.MODE_PRIVATE
                )
                val labelsEditor = labelsPrefs.edit().clear()
                backup.intervalLabels.forEach { (key, value) ->
                    labelsEditor.putString(key, value)
                }
                require(labelsEditor.commit())

                success = true
            } finally {
                if (success) {
                    journalPrefs.edit()
                        .remove(JOURNAL_KEY)
                        .commit()
                } else {
                    recoverInterruptedRestore(context)
                }
            }

            Unit
        }.fold(
            onSuccess = { PortableResult.Success(Unit) },
            onFailure = {
                PortableResult.Error(
                    "복원에 실패했습니다. 기존 데이터는 유지됩니다."
                )
            }
        )
    }

    private fun createContainerBytes(
        dataType: ArmyristPortableDataType,
        payloadBytes: ByteArray,
        password: CharArray?
    ): ByteArray {
        val encrypted = !password.isNullOrEmpty()

        val encryptionMetadata: JSONObject
        val outputPayload: ByteArray
        val payloadHash: String?

        if (encrypted) {
            val salt = ByteArray(16).also(secureRandom::nextBytes)
            val iv = ByteArray(12).also(secureRandom::nextBytes)
            val key = deriveKey(
                password = password!!,
                salt = salt,
                iterations = KDF_ITERATIONS
            )
            val cipher = Cipher.getInstance("AES/GCM/NoPadding")
            cipher.init(
                Cipher.ENCRYPT_MODE,
                key,
                GCMParameterSpec(GCM_TAG_BITS, iv)
            )
            outputPayload = cipher.doFinal(payloadBytes)
            payloadHash = null

            encryptionMetadata = JSONObject()
                .put("enabled", true)
                .put("algorithm", "AES-256-GCM")
                .put("kdf", "PBKDF2-HMAC-SHA256")
                .put("kdfIterations", KDF_ITERATIONS)
                .put(
                    "salt",
                    Base64.encodeToString(
                        salt,
                        Base64.NO_WRAP
                    )
                )
                .put(
                    "iv",
                    Base64.encodeToString(
                        iv,
                        Base64.NO_WRAP
                    )
                )
        } else {
            outputPayload = payloadBytes
            payloadHash = sha256Hex(payloadBytes)
            encryptionMetadata = JSONObject()
                .put("enabled", false)
        }

        val outer = JSONObject()
            .put("formatIdentifier", FORMAT_IDENTIFIER)
            .put("formatVersion", FORMAT_VERSION)
            .put("dataType", dataType.name)
            .put(
                "createdAt",
                OffsetDateTime.now().format(
                    DateTimeFormatter.ISO_OFFSET_DATE_TIME
                )
            )
            .put("encryption", encryptionMetadata)
            .put("payloadEncoding", "BASE64")
            .put(
                "payload",
                Base64.encodeToString(
                    outputPayload,
                    Base64.NO_WRAP
                )
            )

        if (payloadHash != null) {
            outer.put("payloadHash", payloadHash)
        }

        return outer.toString().toByteArray(Charsets.UTF_8)
    }

    private fun decryptPayload(
        ciphertext: ByteArray,
        password: CharArray,
        encryption: JSONObject
    ): ByteArray {
        require(
            encryption.getString("algorithm") ==
                "AES-256-GCM"
        )
        require(
            encryption.getString("kdf") ==
                "PBKDF2-HMAC-SHA256"
        )

        val iterations =
            encryption.getInt("kdfIterations")
        require(iterations > 0)

        val salt = Base64.decode(
            encryption.getString("salt"),
            Base64.NO_WRAP
        )
        val iv = Base64.decode(
            encryption.getString("iv"),
            Base64.NO_WRAP
        )

        require(salt.size >= 16)
        require(iv.isNotEmpty())

        val key = deriveKey(password, salt, iterations)
        val cipher = Cipher.getInstance("AES/GCM/NoPadding")
        cipher.init(
            Cipher.DECRYPT_MODE,
            key,
            GCMParameterSpec(GCM_TAG_BITS, iv)
        )
        return cipher.doFinal(ciphertext)
    }

    private fun deriveKey(
        password: CharArray,
        salt: ByteArray,
        iterations: Int
    ): SecretKeySpec {
        val factory = SecretKeyFactory.getInstance(
            "PBKDF2WithHmacSHA256"
        )
        val spec = PBEKeySpec(
            password,
            salt,
            iterations,
            KEY_BITS
        )
        return try {
            SecretKeySpec(
                factory.generateSecret(spec).encoded,
                "AES"
            )
        } finally {
            spec.clearPassword()
        }
    }

    private fun validateOuterMetadata(outer: JSONObject) {
        require(
            outer.getString("formatIdentifier") ==
                FORMAT_IDENTIFIER
        )
        require(
            outer.getInt("formatVersion") ==
                FORMAT_VERSION
        )
        ArmyristPortableDataType.valueOf(
            outer.getString("dataType")
        )
        require(
            outer.getString("payloadEncoding") ==
                "BASE64"
        )
        require(outer.has("createdAt"))
        require(outer.has("encryption"))
        require(outer.has("payload"))
    }

    private fun validateCountingSnapshot(root: JSONObject) {
        val sheets = root.optJSONArray("sheets") ?: JSONArray()

        for (i in 0 until sheets.length()) {
            val sheet = sheets.getJSONObject(i)
            require(
                sheet.optString("title").trim().isNotEmpty()
            )

            val groups =
                sheet.optJSONArray("groups") ?: JSONArray()
            val groupIds = mutableSetOf<String>()
            for (g in 0 until groups.length()) {
                val group = groups.getJSONObject(g)
                val id = group.getString("id")
                require(id.isNotBlank())
                require(
                    group.optString("name").trim().isNotEmpty()
                )
                groupIds += id
            }

            val items =
                sheet.optJSONArray("items") ?: JSONArray()
            for (j in 0 until items.length()) {
                val item = items.getJSONObject(j)
                require(item.getInt("quantity") >= 0)
                require(
                    item.optString("name").trim().isNotEmpty()
                )
                require(
                    item.optString("unit").trim().isNotEmpty()
                )
                if (!item.isNull("groupId")) {
                    require(
                        item.getString("groupId") in groupIds
                    )
                }
            }

            val calculations =
                sheet.optJSONArray("calculations") ?: JSONArray()
            for (c in 0 until calculations.length()) {
                val calc = calculations.getJSONObject(c)
                require(
                    calc.getString("leftGroupId") in groupIds
                )
                require(
                    calc.getString("rightGroupId") in groupIds
                )
                require(
                    calc.getString("operator") in
                        setOf("ADD", "SUBTRACT")
                )
            }
        }
    }

    private fun validateCoreSnapshot(root: JSONObject) {
        val checklists =
            root.optJSONArray("checklists") ?: JSONArray()
        for (i in 0 until checklists.length()) {
            val checklist = checklists.getJSONObject(i)
            require(
                checklist.optString("title").trim().isNotEmpty()
            )

            val groups =
                checklist.optJSONArray("groups") ?: JSONArray()
            val groupIds = mutableSetOf<String>()
            for (g in 0 until groups.length()) {
                val group = groups.getJSONObject(g)
                require(
                    group.optString("name").trim().isNotEmpty()
                )
                groupIds += group.getString("id")
            }

            val items =
                checklist.optJSONArray("items") ?: JSONArray()
            validateChecklistItems(items, groupIds)

            val deleted =
                checklist.optJSONArray("deletedItems") ?: JSONArray()
            validateChecklistItems(deleted, groupIds)
        }

        val timePlans =
            root.optJSONArray("timePlans") ?: JSONArray()
        for (i in 0 until timePlans.length()) {
            val plan = timePlans.getJSONObject(i)
            require(
                plan.optString("title").trim().isNotEmpty()
            )
            val points =
                plan.optJSONArray("points") ?: JSONArray()
            require(points.length() >= 2)
            for (p in 0 until points.length()) {
                val point = points.getJSONObject(p)
                require(
                    point.optString("name").trim().isNotEmpty()
                )
                if (!point.isNull("timeMinutes")) {
                    require(
                        point.getInt("timeMinutes") in 0..1439
                    )
                }
            }
        }

        val templates =
            root.optJSONArray("reportTemplates") ?: JSONArray()
        var defaultCount = 0
        for (i in 0 until templates.length()) {
            val template = templates.getJSONObject(i)
            require(
                template.optString("name").trim().isNotEmpty()
            )
            if (template.optBoolean("isDefault", false)) {
                defaultCount++
            }
        }
        require(defaultCount <= 1)
    }

    private fun validateChecklistItems(
        items: JSONArray,
        groupIds: Set<String>
    ) {
        val allowed = setOf(
            "INCOMPLETE",
            "COMPLETE",
            "NOT_APPLICABLE"
        )
        for (i in 0 until items.length()) {
            val item = items.getJSONObject(i)
            require(
                item.optString("name").trim().isNotEmpty()
            )
            require(item.getString("status") in allowed)

            if (!item.isNull("groupId")) {
                require(
                    item.getString("groupId") in groupIds
                )
            }

            val enabled =
                item.optBoolean("notificationEnabled", false)
            if (enabled) {
                require(
                    !item.isNull("scheduledTimeMinutes")
                )
                require(
                    item.getInt("scheduledTimeMinutes") in 0..1439
                )
            } else if (!item.isNull("scheduledTimeMinutes")) {
                require(
                    item.getInt("scheduledTimeMinutes") in 0..1439
                )
            }
        }
    }

    private fun readCountingRoot(context: Context): JSONObject {
        val raw = context.getSharedPreferences(
            COUNTING_PREFS,
            Context.MODE_PRIVATE
        ).getString(COUNTING_KEY, null)

        return if (raw.isNullOrBlank()) {
            JSONObject()
                .put("version", 2)
                .put("sheets", JSONArray())
        } else {
            JSONObject(raw)
        }
    }

    private fun readCoreRoot(context: Context): JSONObject {
        val raw = context.getSharedPreferences(
            CORE_PREFS,
            Context.MODE_PRIVATE
        ).getString(CORE_KEY, null)

        return if (raw.isNullOrBlank()) {
            JSONObject()
                .put("version", 1)
                .put("checklists", JSONArray())
                .put("timePlans", JSONArray())
                .put(
                    "userProfile",
                    JSONObject().put("displayName", "")
                )
                .put("reportTemplates", JSONArray())
        } else {
            JSONObject(raw)
        }
    }

    private fun readStringMap(
        context: Context,
        prefsName: String
    ): Map<String, String> {
        val all = context.getSharedPreferences(
            prefsName,
            Context.MODE_PRIVATE
        ).all

        val result = linkedMapOf<String, String>()
        all.forEach { (key, value) ->
            if (value is String) {
                result[key] = value
            }
        }
        return result
    }

    private fun restoreStringMap(
        context: Context,
        prefsName: String,
        source: JSONObject
    ) {
        val prefs = context.getSharedPreferences(
            prefsName,
            Context.MODE_PRIVATE
        )
        val editor = prefs.edit().clear()
        source.keys().forEach { key ->
            editor.putString(key, source.optString(key, ""))
        }
        require(editor.commit())
    }

    private fun restoreStringPreference(
        context: Context,
        prefsName: String,
        key: String,
        value: String?
    ) {
        val editor = context.getSharedPreferences(
            prefsName,
            Context.MODE_PRIVATE
        ).edit()

        if (value == null) {
            editor.remove(key)
        } else {
            editor.putString(key, value)
        }
        require(editor.commit())
    }

    private fun JSONObject.optStringOrNull(key: String): String? {
        if (!has(key) || isNull(key)) return null
        return getString(key)
    }

    private fun sha256Hex(bytes: ByteArray): String {
        val digest = MessageDigest.getInstance("SHA-256")
            .digest(bytes)
        return digest.joinToString("") { "%02x".format(it) }
    }
}
