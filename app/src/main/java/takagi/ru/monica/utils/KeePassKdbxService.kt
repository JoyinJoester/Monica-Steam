package takagi.ru.monica.utils

import android.content.Context
import android.net.Uri
import android.os.ParcelFileDescriptor
import android.util.Base64
import android.util.Log
import app.keemobile.kotpass.cryptography.EncryptedValue
import app.keemobile.kotpass.cryptography.format.BaseCiphers
import app.keemobile.kotpass.cryptography.format.TwofishCipher
import app.keemobile.kotpass.database.Credentials
import app.keemobile.kotpass.database.KeePassDatabase
import app.keemobile.kotpass.database.decode
import app.keemobile.kotpass.database.encode
import app.keemobile.kotpass.database.header.KdfParameters
import app.keemobile.kotpass.database.modifiers.binaries
import app.keemobile.kotpass.database.modifiers.modifyBinaries
import app.keemobile.kotpass.database.modifiers.modifyParentGroup
import app.keemobile.kotpass.models.AutoTypeData
import app.keemobile.kotpass.models.AutoTypeItem
import app.keemobile.kotpass.models.BinaryData
import app.keemobile.kotpass.models.BinaryReference
import app.keemobile.kotpass.models.CustomDataValue
import app.keemobile.kotpass.models.Entry
import app.keemobile.kotpass.models.EntryFields
import app.keemobile.kotpass.models.EntryValue
import app.keemobile.kotpass.models.Group
import app.keemobile.kotpass.models.Meta
import app.keemobile.kotpass.models.TimeData
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.asCoroutineDispatcher
import kotlinx.coroutines.withContext
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.serialization.json.Json
import okio.ByteString
import okio.ByteString.Companion.toByteString
import takagi.ru.monica.data.KeePassStorageLocation
import takagi.ru.monica.data.ItemType
import takagi.ru.monica.data.KeePassCipherAlgorithm
import takagi.ru.monica.data.KeePassDatabaseSourceType
import takagi.ru.monica.data.KeePassDatabaseCreationOptions
import takagi.ru.monica.data.KeePassFormatVersion
import takagi.ru.monica.data.KeePassKdfAlgorithm
import takagi.ru.monica.data.LocalKeePassDatabase
import takagi.ru.monica.data.LocalKeePassDatabaseDao
import takagi.ru.monica.data.PasswordDatabase
import takagi.ru.monica.data.PasskeyEntry
import takagi.ru.monica.data.PasswordEntry
import takagi.ru.monica.data.SecureItem
import takagi.ru.monica.data.isRemoteSource
import takagi.ru.monica.data.resolvedActiveFilePath
import takagi.ru.monica.data.resolvedActiveStorageLocation
import takagi.ru.monica.data.model.BankCardData
import takagi.ru.monica.data.model.CardType
import takagi.ru.monica.data.model.CardWalletDataCodec
import takagi.ru.monica.data.model.SecureCustomField
import takagi.ru.monica.data.model.SecureCustomFieldType
import takagi.ru.monica.data.model.SshKeyData
import takagi.ru.monica.data.model.SshKeyDataCodec
import takagi.ru.monica.data.model.isSshKeyEntry
import takagi.ru.monica.data.model.TotpData
import takagi.ru.monica.data.model.formatForDisplay
import takagi.ru.monica.keepass.KeePassDxPasskeyCodec
import takagi.ru.monica.keepass.KeePassChangeOperation
import takagi.ru.monica.keepass.KeePassChangeSet
import takagi.ru.monica.keepass.KeePassChangeSetApplier
import takagi.ru.monica.keepass.KeePassChangeTarget
import takagi.ru.monica.keepass.KeePassAttachmentChangePatch
import takagi.ru.monica.keepass.KeePassEntryCreatePatch
import takagi.ru.monica.keepass.KeePassFieldChangePatch
import takagi.ru.monica.keepass.KeePassAutoTypeItemPatch
import takagi.ru.monica.keepass.KeePassAutoTypePatch
import takagi.ru.monica.keepass.KeePassBinaryPoolItemPatch
import takagi.ru.monica.keepass.KeePassBinaryReferencePatch
import takagi.ru.monica.keepass.KeePassCustomDataPatch
import takagi.ru.monica.keepass.KeePassEntryFingerprint
import takagi.ru.monica.keepass.KeePassFieldChange
import takagi.ru.monica.keepass.KeePassEntryTreeSnapshot
import takagi.ru.monica.keepass.KeePassGroupTreeChangePatch
import takagi.ru.monica.keepass.KeePassGroupTreeSnapshot
import takagi.ru.monica.keepass.KeePassPendingChangeBaseSnapshot
import takagi.ru.monica.keepass.KeePassPendingChangeRepository
import takagi.ru.monica.keepass.KeePassPendingFlushPlan
import takagi.ru.monica.keepass.KeePassPendingFlushPlanner
import takagi.ru.monica.keepass.KeePassRemoteRebase
import takagi.ru.monica.keepass.KeePassStructureChangePatch
import takagi.ru.monica.keepass.KeePassTimesPatch
import takagi.ru.monica.keepass.KeePassManagedFieldScope
import takagi.ru.monica.keepass.KeePassEntryFieldPatch
import takagi.ru.monica.keepass.KeePassFieldRegistry
import takagi.ru.monica.keepass.KeePassPasskeySyncCodec
import takagi.ru.monica.keepass.KeePassTotpCodec
import takagi.ru.monica.notes.domain.NoteContentCodec
import takagi.ru.monica.passkey.PasskeyCredentialIdCodec
import takagi.ru.monica.passkey.PasskeyPrivateKeyStore
import takagi.ru.monica.attachments.executor.KeePassAttachmentRef
import takagi.ru.monica.security.SecurityManager
import takagi.ru.monica.sync.SyncDiagnostics
import takagi.ru.monica.util.TotpDataResolver
import takagi.ru.monica.workers.KeePassRemoteUploadWorker
import java.io.ByteArrayInputStream
import java.io.ByteArrayOutputStream
import java.io.File
import java.io.FileNotFoundException
import java.io.FileOutputStream
import java.io.IOException
import java.time.Instant
import java.util.Date
import java.util.LinkedHashMap
import java.util.Locale
import java.util.UUID
import java.util.concurrent.Executors

data class KeePassEntryData(
    val title: String,
    val username: String,
    val password: String,
    val url: String,
    val notes: String,
    val appPackageName: String = "",
    val appName: String = "",
    val email: String = "",
    val phone: String = "",
    val addressLine: String = "",
    val city: String = "",
    val state: String = "",
    val zipCode: String = "",
    val country: String = "",
    val creditCardNumber: String = "",
    val creditCardHolder: String = "",
    val creditCardExpiry: String = "",
    val creditCardCVV: String = "",
    val sshKeyData: String = "",
    val monicaLocalId: Long?,
    val entryUuid: String?,
    val groupPath: String?,
    val groupUuid: String?,
    val isInRecycleBin: Boolean,
    /** 取值 `PASSWORD` / `SSO` / `WIFI`；用于还原 [PasswordEntry.loginType]。 */
    val loginType: String = "PASSWORD",
    val ssoProvider: String = "",
    val ssoRefEntryId: Long? = null,
    /** [takagi.ru.monica.data.model.WifiData] 的 JSON，仅在 WIFI 条目上有值。 */
    val wifiMetadata: String = "",
    val customFields: List<KeePassCustomFieldData> = emptyList()
)

data class KeePassCustomFieldData(
    val title: String,
    val value: String,
    val isProtected: Boolean,
    val sortOrder: Int = 0
)

internal data class KeePassRawStringField(
    val key: String,
    val value: String,
    val isProtected: Boolean
)

internal fun isLikelyKeePassRecycleBinPath(groupPath: String?): Boolean {
    val recycleNames = setOf("recyclebin", "trash", "回收站")
    return decodeKeePassPathSegments(groupPath).any { segment ->
        segment.lowercase(Locale.ROOT).replace(" ", "") in recycleNames
    }
}

data class KeePassGroupInfo(
    val name: String,
    val path: String,
    val uuid: String?,
    val depth: Int = 0,
    val displayPath: String = path
)

data class KeePassSecureItemData(
    val item: SecureItem,
    val sourceMonicaId: Long?,
    val isInRecycleBin: Boolean
)

data class KeePassWorkspaceSnapshot(
    val passwords: List<KeePassEntryData>,
    val secureItems: List<KeePassSecureItemData>,
    val groups: List<KeePassGroupInfo>
)

data class KeePassDatabaseDiagnostics(
    val entryCount: Int,
    val creationOptions: KeePassDatabaseCreationOptions
)

data class KeePassRestoreTarget(
    val groupPath: String?,
    val groupUuid: String?
)

data class KeePassConflictResolutionResult(
    val mergedBytes: ByteArray,
    val conflictCopyCount: Int
)

private data class EntryTraversalContext(
    val entry: Entry,
    val groupPath: String?,
    val groupUuid: UUID?,
    val isInRecycleBinByMeta: Boolean
)

private data class GroupTraversalContext(
    val pathKey: String?,
    val groupUuid: UUID,
    val isInRecycleBinByMeta: Boolean
)

private data class RemovedEntryContext(
    val entry: Entry,
    val previousParentUuid: UUID
)

private data class RemoteConflictEntrySnapshot(
    val entry: Entry,
    val groupPath: String?,
    val signature: String
)

private data class InternalConflictResolutionResult(
    val mergedDatabase: KeePassDatabase,
    val mergedBytes: ByteArray,
    val conflictCopyCount: Int
)

private data class RemoteSyncOutcome(
    val writeResult: FileSourceWriteResult,
    val finalBytes: ByteArray,
    val finalDatabase: KeePassDatabase,
    val conflictCopyCount: Int = 0
)

internal data class RemoteKdbxVerification(
    val bytes: ByteArray,
    val database: KeePassDatabase,
    val hash: String
)

data class KeePassRemoteSyncResult(
    val databaseName: String,
    val message: String
)

private enum class KeePassPasswordSkipReason {
    MONICA_SECURE_ITEM,
    PURE_PASSKEY,
    TEMPLATE,
    EMPTY
}

private data class KeePassPasswordEntryAnalysis(
    val data: KeePassEntryData?,
    val skipReason: KeePassPasswordSkipReason?,
    val hasPasskeyFields: Boolean,
    val hasTitle: Boolean,
    val hasUsername: Boolean,
    val hasPassword: Boolean,
    val hasUrl: Boolean,
    val hasNotes: Boolean,
    val fieldNames: List<String>
)

internal fun extractKeePassCustomFieldsForPasswordEntry(
    fields: List<KeePassRawStringField>
): List<KeePassCustomFieldData> {
    return fields
        .asSequence()
        .filter { field ->
            val normalizedKey = field.key.trim()
            normalizedKey.isNotBlank() &&
                field.value.isNotBlank() &&
                !isReservedKeePassPasswordFieldName(normalizedKey)
        }
        .mapIndexed { index, field ->
            KeePassCustomFieldData(
                title = field.key.trim(),
                value = field.value,
                isProtected = field.isProtected,
                sortOrder = index
            )
        }
        .toList()
}

private fun isReservedKeePassPasswordFieldName(key: String): Boolean {
    val normalized = key.trim()
    if (normalized.isBlank()) return true
    return KeePassFieldRegistry.isReservedPasswordProjectionField(normalized)
}

class KeePassKdbxService(
    private val context: Context,
    private val dao: LocalKeePassDatabaseDao,
    private val securityManager: SecurityManager
) {
    companion object {
        private const val TAG = "KeePassKdbxService"
        // Keep unknown-source cache short, but keep known internal files effectively "always warm".
        private const val UNKNOWN_SOURCE_CACHE_TTL_MS = 60_000L
        private const val PENDING_CHANGE_UPLOAD_RETRY_DELAY_MILLIS = 30_000L
        // Keep only the active database plus a tiny LRU warm set. Monica users may register many KDBX files.
        private const val MAX_WARM_CACHED_DATABASES = 2
        // Disable post-write full decode verification for normal writes to reduce save latency.
        // The database is still encoded by the library and written atomically/with rollback paths.
        private const val ENABLE_POST_WRITE_DECODE_VERIFICATION = false
        private const val FIELD_MONICA_LOCAL_ID = "MonicaLocalId"
        private const val FIELD_MONICA_CONFLICT_COPY = "MonicaConflictCopy"
        private const val FIELD_MONICA_ITEM_ID = "MonicaSecureItemId"
        private const val FIELD_MONICA_ITEM_TYPE = "MonicaItemType"
        private const val FIELD_MONICA_ITEM_DATA = "MonicaItemData"
        private const val FIELD_MONICA_IMAGE_PATHS = "MonicaImagePaths"
        private const val FIELD_MONICA_IS_FAVORITE = "MonicaIsFavorite"
        private const val FIELD_BANK_NAME = "Bank Name"
        private const val FIELD_CARD_TYPE = "Card Type"
        private const val FIELD_BILLING_ADDRESS = "Billing Address"
        private const val FIELD_BRAND = "Brand"
        private const val FIELD_NICKNAME = "Nickname"
        private const val FIELD_VALID_FROM_MONTH = "Valid From Month"
        private const val FIELD_VALID_FROM_YEAR = "Valid From Year"
        private const val FIELD_PIN = "PIN"
        private const val FIELD_IBAN = "IBAN"
        private const val FIELD_SWIFT_BIC = "SWIFT/BIC"
        private const val FIELD_ROUTING_NUMBER = "Routing Number"
        private const val FIELD_ACCOUNT_NUMBER = "Account Number"
        private const val FIELD_BRANCH_CODE = "Branch Code"
        private const val FIELD_CURRENCY = "Currency"
        private const val FIELD_CUSTOMER_SERVICE_PHONE = "Customer Service Phone"
        private const val FIELD_MONICA_PASSKEY_CREDENTIAL_ID = "MonicaPasskeyCredentialId"
        private const val FIELD_MONICA_PASSKEY_DATA = "MonicaPasskeyData"
        private const val FIELD_MONICA_PASSKEY_MODE = "MonicaPasskeyMode"
        private const val FIELD_MONICA_SSH_ALGORITHM = "MonicaSshAlgorithm"
        private const val FIELD_MONICA_SSH_KEY_SIZE = "MonicaSshKeySize"
        private const val FIELD_MONICA_SSH_PUBLIC_KEY = "MonicaSshPublicKey"
        private const val FIELD_MONICA_SSH_PRIVATE_KEY = "MonicaSshPrivateKey"
        private const val FIELD_MONICA_SSH_FINGERPRINT = "MonicaSshFingerprint"
        private const val FIELD_MONICA_SSH_COMMENT = "MonicaSshComment"
        private const val REMOTE_CONFLICT_TITLE_SUFFIX = "[远端冲突副本]"
        private const val FIELD_MONICA_SSH_FORMAT = "MonicaSshFormat"
        private const val FIELD_KEEPASS_XC_TEMPLATE = "_etm_template"
        // === WIFI 条目互通字段 ===
        // `SSID` 是 keepass2android 官方 WLan 模板的 `TemplateField_WLan_SSID`，
        // 同时也是 KeeWeb/KeePassXC 社区常见命名；把它作为"标准字段"写入以保证
        // 其他 KeePass 客户端能读懂。
        private const val FIELD_WIFI_SSID = "SSID"
        // Monica 扩展元数据（安全类型、代理、IP 等）。只有 Monica 自己解析；
        // 其它客户端看到的是一段 JSON 字符串，不影响它们的常规使用。
        private const val FIELD_MONICA_WIFI_DATA = "MonicaWifiData"
        // 快速识别标志；与 PasswordEntry.loginType 对应，避免读回来时要解析 JSON
        // 才能判断是不是 WIFI 条目。值为 "WIFI"/"SSO"/"PASSWORD"。
        private const val FIELD_MONICA_LOGIN_TYPE = "MonicaLoginType"
        private const val FIELD_APP_PACKAGE_NAME = "App Package Name"
        private const val FIELD_APP_NAME = "App Name"
        private const val FIELD_EMAIL = "Email"
        private const val FIELD_PHONE = "Phone"
        private const val FIELD_ADDRESS_LINE = "Address"
        private const val FIELD_CITY = "City"
        private const val FIELD_STATE = "State"
        private const val FIELD_POSTAL_CODE = "Postal Code"
        private const val FIELD_COUNTRY = "Country"
        private const val FIELD_CARD_NUMBER = "Card Number"
        private const val FIELD_CARD_HOLDER = "Card Holder"
        private const val FIELD_CARD_EXPIRY = "Card Expiry"
        private const val FIELD_CARD_CVV = "Card CVV"
        private const val FIELD_SSO_PROVIDER = "SSO Provider"
        private const val FIELD_MONICA_SSO_REF_ENTRY_ID = "MonicaSsoRefEntryId"
        // kotpass decode 在并发下可能触发 native 崩溃，必须跨实例串行化。
        private val globalDecodeMutex = Mutex()
        // 部分设备/ABI 下 decode 在不同工作线程切换时更易触发 native 崩溃，固定到单线程执行更稳。
        private val decodeExecutor = Executors.newSingleThreadExecutor { runnable ->
            Thread(runnable, "KeePassDecodeThread").apply { isDaemon = true }
        }
        private val decodeDispatcher = decodeExecutor.asCoroutineDispatcher()
        // 同一个远端 KeePass 数据库只能有一个真实上传执行者。
        private val remoteUploadMutexes = mutableMapOf<Long, Mutex>()
        // 写入采用“读-改-写”原子化；不同数据库可独立排队，跨库操作按 ID 顺序加锁。
        private val databaseMutationMutexes = mutableMapOf<Long, Mutex>()
        // 同一数据库首次打开时只允许一个入口执行解码，避免验证/读取并发触发重复开库。
        private val databaseLoadMutexes = mutableMapOf<Long, Mutex>()
        // 按数据库 ID 维护进程级已解锁会话；不同调用入口共享同一次 KDBX 解码结果。
        private val loadedDatabaseCache = LinkedHashMap<Long, CachedLoadedDatabase>(4, 0.75f, true)
        private var activeDatabaseId: Long? = null
        // 跨实例缓存失效信号：某实例更新数据库绑定后，其他实例的本地缓存应立即失效。
        private val externallyInvalidatedDatabaseIds = mutableSetOf<Long>()

        suspend fun <T> withGlobalDecodeLock(block: () -> T): T {
            return globalDecodeMutex.withLock { block() }
        }

        private fun mutationMutexForDatabase(databaseId: Long): Mutex {
            return synchronized(databaseMutationMutexes) {
                databaseMutationMutexes.getOrPut(databaseId) { Mutex() }
            }
        }

        private fun loadMutexForDatabase(databaseId: Long): Mutex {
            return synchronized(databaseLoadMutexes) {
                databaseLoadMutexes.getOrPut(databaseId) { Mutex() }
            }
        }

        private fun remoteUploadMutex(databaseId: Long): Mutex {
            return synchronized(remoteUploadMutexes) {
                remoteUploadMutexes.getOrPut(databaseId) { Mutex() }
            }
        }

        private suspend fun <T> withDatabaseMutationLocks(
            databaseIds: List<Long>,
            block: suspend () -> T
        ): T {
            val mutexes = databaseIds.distinct().sorted().map { mutationMutexForDatabase(it) }
            suspend fun lockAt(index: Int): T {
                return if (index >= mutexes.size) {
                    block()
                } else {
                    mutexes[index].withLock { lockAt(index + 1) }
                }
            }
            return lockAt(0)
        }

        @Synchronized
        fun invalidateProcessCache(databaseId: Long) {
            synchronized(loadedDatabaseCache) {
                loadedDatabaseCache.remove(databaseId)
                if (activeDatabaseId == databaseId) {
                    activeDatabaseId = null
                }
            }
            externallyInvalidatedDatabaseIds += databaseId
        }

        fun markDatabaseActive(databaseId: Long) {
            synchronized(loadedDatabaseCache) {
                activeDatabaseId = databaseId
                trimLoadedDatabaseCacheLocked()
            }
        }

        fun clearActiveDatabase(databaseId: Long? = null) {
            synchronized(loadedDatabaseCache) {
                if (databaseId == null || activeDatabaseId == databaseId) {
                    activeDatabaseId = null
                }
                trimLoadedDatabaseCacheLocked()
            }
        }

        fun trimInactiveCaches() {
            synchronized(loadedDatabaseCache) {
                trimLoadedDatabaseCacheLocked()
            }
        }

        private fun trimLoadedDatabaseCacheLocked() {
            val activeId = activeDatabaseId
            var warmCount = loadedDatabaseCache.keys.count { it != activeId }
            val iterator = loadedDatabaseCache.entries.iterator()
            while (warmCount > MAX_WARM_CACHED_DATABASES && iterator.hasNext()) {
                val entry = iterator.next()
                if (entry.key == activeId) continue
                iterator.remove()
                warmCount--
            }
        }

        @Synchronized
        private fun consumeProcessCacheInvalidation(databaseId: Long): Boolean {
            return externallyInvalidatedDatabaseIds.remove(databaseId)
        }

        fun inferCreationOptions(keePassDatabase: KeePassDatabase): KeePassDatabaseCreationOptions {
            val resolved = when (keePassDatabase) {
                is KeePassDatabase.Ver3x -> KeePassDatabaseCreationOptions(
                    formatVersion = KeePassFormatVersion.KDBX3,
                    cipherAlgorithm = resolveCipherAlgorithm(keePassDatabase.header.cipherId),
                    kdfAlgorithm = KeePassKdfAlgorithm.AES_KDF,
                    transformRounds = keePassDatabase.header.transformRounds.toLong(),
                    memoryBytes = KeePassDatabaseCreationOptions.DEFAULT_ARGON_MEMORY_BYTES,
                    parallelism = 1
                )
                is KeePassDatabase.Ver4x -> {
                    val kdf = keePassDatabase.header.kdfParameters
                    when (kdf) {
                        is KdfParameters.Aes -> KeePassDatabaseCreationOptions(
                            formatVersion = KeePassFormatVersion.KDBX4,
                            cipherAlgorithm = resolveCipherAlgorithm(keePassDatabase.header.cipherId),
                            kdfAlgorithm = KeePassKdfAlgorithm.AES_KDF,
                            transformRounds = kdf.rounds.toLong(),
                            memoryBytes = KeePassDatabaseCreationOptions.DEFAULT_ARGON_MEMORY_BYTES,
                            parallelism = 1
                        )
                        is KdfParameters.Argon2 -> KeePassDatabaseCreationOptions(
                            formatVersion = KeePassFormatVersion.KDBX4,
                            cipherAlgorithm = resolveCipherAlgorithm(keePassDatabase.header.cipherId),
                            kdfAlgorithm = if (kdf.variant == KdfParameters.Argon2.Variant.Argon2id) {
                                KeePassKdfAlgorithm.ARGON2ID
                            } else {
                                KeePassKdfAlgorithm.ARGON2D
                            },
                            transformRounds = kdf.iterations.toLong(),
                            memoryBytes = kdf.memory.toLong(),
                            parallelism = kdf.parallelism.toInt()
                        )
                    }
                }
            }
            return resolved.normalized()
        }

        private fun resolveCipherAlgorithm(cipherId: UUID): KeePassCipherAlgorithm {
            return when (cipherId) {
                BaseCiphers.Aes.uuid -> KeePassCipherAlgorithm.AES
                BaseCiphers.ChaCha20.uuid -> KeePassCipherAlgorithm.CHACHA20
                TwofishCipher.uuid -> KeePassCipherAlgorithm.TWOFISH
                else -> KeePassCipherAlgorithm.AES
            }
        }
    }
    
    private data class LoadedDatabase(
        val database: LocalKeePassDatabase,
        val credentials: Credentials,
        val keePassDatabase: KeePassDatabase,
        val sourceEtag: String?,
        val sourceLastModified: String?,
        val sourceSignature: DatabaseSourceSignature?
    )

    private data class CachedLoadedDatabase(
        val loaded: LoadedDatabase,
        val cachedAtMs: Long
    )

    private data class DatabaseSourceSignature(
        val sizeBytes: Long,
        val lastModifiedEpochMs: Long
    )

    private data class DatabaseSnapshot(
        val bytes: ByteArray,
        val etag: String?,
        val lastModified: String?,
        val signature: DatabaseSourceSignature?
    )

    private data class MutationPlan<T>(
        val updatedDatabase: KeePassDatabase,
        val result: T,
        val beforeRemoteUpload: (suspend (LocalKeePassDatabase, KeePassDatabase) -> Unit)? = null,
        val afterWrite: (suspend (LocalKeePassDatabase, KeePassDatabase) -> Unit)? = null
    )

    private data class CredentialsResolution(
        val candidates: List<KeePassCredentialCandidate>
    )

    suspend fun verifyDatabase(
        databaseId: Long,
        passwordOverride: String? = null,
        keyFileUriOverride: Uri? = null
    ): Result<Int> = withContext(Dispatchers.IO) {
        try {
            val diagnostics = inspectDatabase(
                databaseId = databaseId,
                passwordOverride = passwordOverride,
                keyFileUriOverride = keyFileUriOverride
            ).getOrElse { throw it }
            Result.success(diagnostics.entryCount)
        } catch (e: Exception) {
            val mapped = normalizeError(e)
            val code = (mapped as? KeePassOperationException)?.code ?: KeePassErrorCode.IO_READ_WRITE_FAILED
            Log.e(TAG, "verifyDatabase failed (databaseId=$databaseId, code=$code)", mapped)
            Result.failure(mapped)
        }
    }

    suspend fun verifyExternalDatabase(
        fileUri: Uri,
        password: String,
        keyFileUri: Uri? = null
    ): Result<Int> = withContext(Dispatchers.IO) {
        try {
            val diagnostics = inspectExternalDatabase(
                fileUri = fileUri,
                password = password,
                keyFileUri = keyFileUri
            ).getOrElse { throw it }
            Result.success(diagnostics.entryCount)
        } catch (e: Exception) {
            val mapped = normalizeError(e)
            val code = (mapped as? KeePassOperationException)?.code ?: KeePassErrorCode.IO_READ_WRITE_FAILED
            Log.e(
                TAG,
                "verifyExternalDatabase failed (uri=$fileUri, keyFile=${keyFileUri != null}, code=$code)",
                mapped
            )
            Result.failure(mapped)
        }
    }

    suspend fun inspectDatabase(
        databaseId: Long,
        passwordOverride: String? = null,
        keyFileUriOverride: Uri? = null
    ): Result<KeePassDatabaseDiagnostics> = withContext(Dispatchers.IO) {
        try {
            if (passwordOverride == null && keyFileUriOverride == null) {
                val loaded = getCachedLoadedDatabase(databaseId) ?: loadDatabase(databaseId)
                return@withContext Result.success(buildDiagnostics(loaded.keePassDatabase))
            }
            val database = dao.getDatabaseById(databaseId) ?: throw Exception("数据库不存在")
            val credentials = buildCredentials(
                database,
                passwordOverride = passwordOverride,
                keyFileUriOverride = keyFileUriOverride
            )
            val bytes = readDatabaseBytes(database)
            val (keePassDatabase, _) = decodeDatabaseWithFallback(
                bytes = bytes,
                credentialsResolution = credentials,
                sourceLabel = "databaseId=$databaseId",
                sourceName = database.resolvedActiveFilePath()
            )
            Result.success(buildDiagnostics(keePassDatabase))
        } catch (e: Exception) {
            Result.failure(normalizeError(e))
        }
    }

    suspend fun inspectExternalDatabase(
        fileUri: Uri,
        password: String,
        keyFileUri: Uri? = null
    ): Result<KeePassDatabaseDiagnostics> = withContext(Dispatchers.IO) {
        try {
            val credentials = buildCredentialsFromRaw(password = password, keyFileUri = keyFileUri)
            val bytes = readBytesFromUri(fileUri, "无法打开数据库文件")
            val (keePassDatabase, _) = decodeDatabaseWithFallback(
                bytes = bytes,
                credentialsResolution = credentials,
                sourceLabel = "uri=$fileUri",
                sourceName = fileUri.lastPathSegment ?: fileUri.toString()
            )
            Result.success(buildDiagnostics(keePassDatabase))
        } catch (e: Exception) {
            Result.failure(normalizeError(e))
        }
    }

    suspend fun syncRemoteDatabase(databaseId: Long): Result<KeePassRemoteSyncResult> = withContext(Dispatchers.IO) {
        try {
            val database = dao.getDatabaseById(databaseId)
                ?: throw IOException("数据库不存在")
            val syncedDatabaseName = database.name
            if (!database.isRemoteSource() || database.sourceId == null) {
                throw IllegalArgumentException("当前数据库不是远端来源")
            }

            val remoteDb = PasswordDatabase.getDatabase(context)
            val remoteSourceDao = remoteDb.keepassRemoteSourceDao()
            val syncStateDao = remoteDb.keepassRemoteSyncStateDao()
            val syncService = RemoteKeePassSyncService(
                databaseDao = dao,
                remoteSourceDao = remoteSourceDao,
                syncStateDao = syncStateDao
            )
            val remoteSource = remoteSourceDao.getSourceById(database.sourceId)
                ?: throw IllegalStateException("远端来源不存在")
            val fileSource = createRemoteFileSource(database, remoteSource)
            val workingPath = database.workingCopyPath ?: throw IllegalStateException("本地工作副本不存在")
            val workingFile = File(context.filesDir, workingPath)
            if (!workingFile.exists()) {
                throw IllegalStateException("本地工作副本不存在")
            }

            val workingBytes = workingFile.readBytes()
            val workingHash = GoogleDriveKeePassSupport.sha256Hex(workingBytes)
            val syncState = syncStateDao.getState(databaseId)
            val baseHash = syncState?.baseHash
            syncService.markComparing(databaseId, workingHash)

            val remoteStat = runCatching { fileSource.stat() }.getOrDefault(FileSourceStat())
            val remoteBytes = fileSource.read()
            val remoteHash = GoogleDriveKeePassSupport.sha256Hex(remoteBytes)
            val localHasChanges = if (baseHash.isNullOrBlank()) {
                workingHash != remoteHash
            } else {
                baseHash != workingHash
            }
            val remoteHasChanges = if (baseHash.isNullOrBlank()) {
                workingHash != remoteHash
            } else {
                baseHash != remoteHash
            }

            Log.i(
                TAG,
                "KeePass remote sync compare databaseId=$databaseId source=${database.sourceType} localHash=${workingHash.take(12)} remoteHash=${remoteHash.take(12)} baseHash=${baseHash?.take(12)} localChanged=$localHasChanges remoteChanged=$remoteHasChanges remoteVersion=${(remoteStat.etag ?: remoteStat.versionToken).orEmpty()}"
            )

            if (remoteHasChanges) {
                syncService.markDownloading(databaseId, workingHash)
                if (!localHasChanges) {
                    OneDriveKeePassSupport.writeRelativeFile(context, workingPath, remoteBytes)
                    database.cacheCopyPath?.let { cachePath ->
                        OneDriveKeePassSupport.writeRelativeFile(context, cachePath, remoteBytes)
                    }
                    syncService.markSynchronized(
                        databaseId = databaseId,
                        versionToken = remoteStat.versionToken,
                        etag = remoteStat.etag,
                        baseHash = remoteHash,
                        workingHash = remoteHash
                    )
                    invalidateProcessCache(databaseId)
                    return@withContext Result.success(
                        KeePassRemoteSyncResult(syncedDatabaseName, "已拉取远端最新版本")
                    )
                }

                if (database.sourceType == KeePassDatabaseSourceType.REMOTE_ONEDRIVE ||
                    database.sourceType == KeePassDatabaseSourceType.REMOTE_WEBDAV
                ) {
                    val loaded = loadDatabase(databaseId)
                    val mergeResult = resolveRemoteConflictInternal(
                        database = database,
                        credentials = loaded.credentials,
                        localDatabase = loaded.keePassDatabase,
                        remoteBytes = remoteBytes
                    )
                    syncService.markLocalChanges(databaseId, GoogleDriveKeePassSupport.sha256Hex(mergeResult.mergedBytes))
                    val writeResult = fileSource.write(
                        mergeResult.mergedBytes,
                        expectedVersion = remoteStat.etag ?: remoteStat.versionToken
                    )
                    val verifiedRemote = verifyRemoteKdbxWrite(
                        database = database,
                        fileSource = fileSource,
                        credentials = loaded.credentials,
                        expectedBytes = mergeResult.mergedBytes,
                        sourceLabel = "service-sync-merge"
                    )
                    OneDriveKeePassSupport.writeRelativeFile(context, workingPath, verifiedRemote.bytes)
                    database.cacheCopyPath?.let { cachePath ->
                        OneDriveKeePassSupport.writeRelativeFile(context, cachePath, verifiedRemote.bytes)
                    }
                    syncService.markSynchronized(
                        databaseId = databaseId,
                        versionToken = writeResult.versionToken,
                        etag = writeResult.etag,
                        baseHash = verifiedRemote.hash,
                        workingHash = verifiedRemote.hash
                    )
                    invalidateProcessCache(databaseId)
                    val message = if (mergeResult.conflictCopyCount > 0) {
                        "已合并本地与远端修改，并保留 ${mergeResult.conflictCopyCount} 个远端冲突副本"
                    } else {
                        "已合并本地与远端修改"
                    }
                    return@withContext Result.success(KeePassRemoteSyncResult(syncedDatabaseName, message))
                }

                val conflictMessage = "远端文件已变化，且本地工作副本也有修改，请先处理冲突"
                syncService.markConflict(
                    databaseId = databaseId,
                    workingHash = workingHash,
                    failureMessage = conflictMessage
                )
                throw IllegalStateException(conflictMessage)
            }

            if (!localHasChanges) {
                syncService.markSynchronized(
                    databaseId = databaseId,
                    versionToken = remoteStat.versionToken,
                    etag = remoteStat.etag,
                    baseHash = remoteHash,
                    workingHash = workingHash
                )
                return@withContext Result.success(KeePassRemoteSyncResult(syncedDatabaseName, "远端已是最新状态"))
            }

            syncService.markUploadInProgress(databaseId, workingHash)
            val writeResult = fileSource.write(
                workingBytes,
                expectedVersion = syncState?.remoteEtag ?: syncState?.remoteVersionToken
            )
            val verifiedRemote = verifyRemoteKdbxWrite(
                database = database,
                fileSource = fileSource,
                credentials = loadDatabase(databaseId).credentials,
                expectedBytes = workingBytes,
                sourceLabel = "service-sync-upload"
            )
            database.cacheCopyPath?.let { cachePath ->
                OneDriveKeePassSupport.writeRelativeFile(context, cachePath, verifiedRemote.bytes)
            }
            syncService.markSynchronized(
                databaseId = databaseId,
                versionToken = writeResult.versionToken,
                etag = writeResult.etag,
                baseHash = verifiedRemote.hash,
                workingHash = verifiedRemote.hash
            )
            invalidateProcessCache(databaseId)
            Result.success(KeePassRemoteSyncResult(syncedDatabaseName, "远端同步成功"))
        } catch (e: Exception) {
            Result.failure(normalizeError(e))
        }
    }

    private fun buildDiagnostics(keePassDatabase: KeePassDatabase): KeePassDatabaseDiagnostics {
        val (entries, hasRecycleBinMeta) = collectEntryContexts(keePassDatabase)
        val resolutionContext = KeePassFieldReferenceResolver.buildContext(entries.map { it.entry })
        val count = entries.count { context ->
            entryToData(
                entry = context.entry,
                groupPath = context.groupPath,
                groupUuid = context.groupUuid,
                isInRecycleBinByMeta = context.isInRecycleBinByMeta,
                hasRecycleBinMeta = hasRecycleBinMeta,
                resolutionContext = resolutionContext
            ) != null
        }
        return KeePassDatabaseDiagnostics(
            entryCount = count,
            creationOptions = inferCreationOptions(keePassDatabase)
        )
    }

    suspend fun createGroup(
        databaseId: Long,
        groupName: String,
        parentPath: String? = null
    ): Result<KeePassGroupInfo> = withContext(Dispatchers.IO) {
        try {
            val normalizedName = groupName.trim()
            if (normalizedName.isBlank()) {
                throw IllegalArgumentException("分组名称不能为空")
            }
            val groupInfo = mutateDatabase(databaseId) { loaded ->
                val parentSegments = decodeKeePassPathSegments(parentPath)
                val parentPathKey = parentPath?.takeIf { it.isNotBlank() }
                val parentGroupUuid = parentPathKey?.let {
                    findGroupUuidByPath(
                        group = loaded.keePassDatabase.content.group,
                        currentPathKey = null,
                        targetPathKey = it
                    )
                } ?: loaded.keePassDatabase.content.group.uuid
                val targetPath = buildKeePassPathKey(parentPathKey, normalizedName)
                val existedBefore = findGroupUuidByPath(
                    group = loaded.keePassDatabase.content.group,
                    currentPathKey = null,
                    targetPathKey = targetPath
                ) != null
                val result = addGroupToPath(
                    group = loaded.keePassDatabase.content.group,
                    parentSegments = parentSegments,
                    newGroupName = normalizedName,
                    currentPathKey = ""
                )
                MutationPlan(
                    updatedDatabase = loaded.keePassDatabase.modifyParentGroup { result.first },
                    result = result.second,
                    beforeRemoteUpload = { database, _ ->
                        val changeSet = if (!existedBefore) {
                            KeePassChangeSet(
                                databaseId = loaded.database.id,
                                target = KeePassChangeTarget.GROUP,
                                operation = KeePassChangeOperation.CREATE_GROUP,
                                entryUuid = null,
                                baseFingerprint = null,
                                baseGroupPath = parentPathKey,
                                baseGroupUuid = parentGroupUuid.toString(),
                                structurePatch = KeePassStructureChangePatch(
                                    sourceGroupPath = result.second.path,
                                    sourceGroupUuid = result.second.uuid,
                                    targetGroupPath = parentPathKey,
                                    targetGroupUuid = parentGroupUuid.toString(),
                                    groupName = normalizedName
                                )
                            )
                        } else {
                            null
                        }
                        enqueuePendingChangeSetsIfRemote(database, listOfNotNull(changeSet))
                    }
                )
            }
            Result.success(groupInfo)
        } catch (e: Exception) {
            Result.failure(normalizeError(e))
        }
    }

    suspend fun renameGroup(
        databaseId: Long,
        groupPath: String,
        newName: String
    ): Result<KeePassGroupInfo> = withContext(Dispatchers.IO) {
        try {
            val normalizedName = newName.trim()
            if (normalizedName.isBlank()) {
                throw IllegalArgumentException("分组名称不能为空")
            }
            val pathSegments = decodeKeePassPathSegments(groupPath)
            if (pathSegments.isEmpty()) {
                throw IllegalArgumentException("分组路径无效")
            }
            val groupInfo = mutateDatabase(databaseId) { loaded ->
                val targetGroupUuid = findGroupUuidByPath(
                    group = loaded.keePassDatabase.content.group,
                    currentPathKey = null,
                    targetPathKey = groupPath
                ) ?: throw IllegalArgumentException("分组不存在: $groupPath")
                val result = renameGroupByPath(
                    group = loaded.keePassDatabase.content.group,
                    pathSegments = pathSegments,
                    newName = normalizedName,
                    currentPathKey = ""
                )
                MutationPlan(
                    updatedDatabase = loaded.keePassDatabase.modifyParentGroup { result.first },
                    result = result.second,
                    beforeRemoteUpload = { database, _ ->
                        enqueuePendingChangeSetsIfRemote(
                            database,
                            listOf(
                                KeePassChangeSet(
                                    databaseId = loaded.database.id,
                                    target = KeePassChangeTarget.GROUP,
                                    operation = KeePassChangeOperation.RENAME_GROUP,
                                    entryUuid = null,
                                    baseFingerprint = null,
                                    baseGroupPath = groupPath,
                                    baseGroupUuid = targetGroupUuid.toString(),
                                    structurePatch = KeePassStructureChangePatch(
                                        sourceGroupPath = groupPath,
                                        sourceGroupUuid = targetGroupUuid.toString(),
                                        targetGroupPath = result.second.path,
                                        targetGroupUuid = targetGroupUuid.toString(),
                                        newGroupName = normalizedName
                                    )
                                )
                            )
                        )
                    }
                )
            }
            Result.success(groupInfo)
        } catch (e: Exception) {
            Result.failure(normalizeError(e))
        }
    }

    suspend fun deleteGroup(
        databaseId: Long,
        groupPath: String
    ): Result<Unit> = withContext(Dispatchers.IO) {
        try {
            val pathSegments = decodeKeePassPathSegments(groupPath)
            if (pathSegments.isEmpty()) {
                throw IllegalArgumentException("分组路径无效")
            }
            mutateDatabase(databaseId) { loaded ->
                val targetGroupUuid = findGroupUuidByPath(
                    group = loaded.keePassDatabase.content.group,
                    currentPathKey = null,
                    targetPathKey = groupPath
                ) ?: throw IllegalArgumentException("分组不存在: $groupPath")
                val result = removeGroupByPath(
                    group = loaded.keePassDatabase.content.group,
                    pathSegments = pathSegments
                )
                if (!result.second) {
                    throw IllegalArgumentException("分组不存在: $groupPath")
                }
                MutationPlan(
                    updatedDatabase = loaded.keePassDatabase.modifyParentGroup { result.first },
                    result = Unit,
                    beforeRemoteUpload = { database, _ ->
                        enqueuePendingChangeSetsIfRemote(
                            database,
                            listOf(
                                KeePassChangeSet(
                                    databaseId = loaded.database.id,
                                    target = KeePassChangeTarget.GROUP,
                                    operation = KeePassChangeOperation.DELETE_GROUP,
                                    entryUuid = null,
                                    baseFingerprint = null,
                                    baseGroupPath = groupPath,
                                    baseGroupUuid = targetGroupUuid.toString(),
                                    structurePatch = KeePassStructureChangePatch(
                                        sourceGroupPath = groupPath,
                                        sourceGroupUuid = targetGroupUuid.toString()
                                    )
                                )
                            )
                        )
                    }
                )
            }
            Result.success(Unit)
        } catch (e: Exception) {
            Result.failure(normalizeError(e))
        }
    }

    suspend fun moveGroup(
        sourceDatabaseId: Long,
        groupPath: String,
        targetDatabaseId: Long,
        targetParentPath: String? = null
    ): Result<KeePassGroupInfo> = withContext(Dispatchers.IO) {
        try {
            val sourcePathSegments = decodeKeePassPathSegments(groupPath)
            if (sourcePathSegments.isEmpty()) {
                throw IllegalArgumentException("分组路径无效")
            }

            val normalizedSourcePath = groupPath.trim()
            val normalizedTargetParentPath = targetParentPath?.trim()?.takeIf { it.isNotBlank() }
            val sourceParentPath = normalizedSourcePath.substringBeforeLast('/', "")
                .ifBlank { null }
            if (sourceDatabaseId == targetDatabaseId &&
                normalizedTargetParentPath == sourceParentPath
            ) {
                val movedGroup = listGroups(sourceDatabaseId).getOrThrow()
                    .firstOrNull { it.path == normalizedSourcePath }
                    ?: throw IllegalArgumentException("分组不存在: $groupPath")
                return@withContext Result.success(movedGroup)
            }

            if (sourceDatabaseId == targetDatabaseId &&
                !normalizedTargetParentPath.isNullOrBlank() &&
                (normalizedTargetParentPath == normalizedSourcePath ||
                    normalizedTargetParentPath.startsWith("$normalizedSourcePath/"))
            ) {
                throw IllegalArgumentException("不能移动到自身或子分组下")
            }

            val movedGroupInfo = withDatabaseMutationLocks(listOf(sourceDatabaseId, targetDatabaseId)) {
                try {
                    val sourceLoaded = getCachedLoadedDatabase(sourceDatabaseId) ?: loadDatabase(sourceDatabaseId)
                    val targetLoaded = if (sourceDatabaseId == targetDatabaseId) {
                        sourceLoaded
                    } else {
                        getCachedLoadedDatabase(targetDatabaseId) ?: loadDatabase(targetDatabaseId)
                    }
                    val sourceGroupUuid = findGroupUuidByPath(
                        group = sourceLoaded.keePassDatabase.content.group,
                        currentPathKey = null,
                        targetPathKey = normalizedSourcePath
                    ) ?: throw IllegalArgumentException("分组不存在: $groupPath")
                    val sourceParentGroupUuid = sourceParentPath?.let {
                        findGroupUuidByPath(
                            group = sourceLoaded.keePassDatabase.content.group,
                            currentPathKey = null,
                            targetPathKey = it
                        )
                    } ?: sourceLoaded.keePassDatabase.content.group.uuid
                    val targetParentGroupUuid = if (sourceDatabaseId == targetDatabaseId) {
                        normalizedTargetParentPath?.let {
                            findGroupUuidByPath(
                                group = sourceLoaded.keePassDatabase.content.group,
                                currentPathKey = null,
                                targetPathKey = it
                            )
                        } ?: sourceLoaded.keePassDatabase.content.group.uuid
                    } else {
                        normalizedTargetParentPath?.let {
                            findGroupUuidByPath(
                                group = targetLoaded.keePassDatabase.content.group,
                                currentPathKey = null,
                                targetPathKey = it
                            )
                        } ?: targetLoaded.keePassDatabase.content.group.uuid
                    }

                    val extracted = extractGroupByPath(
                        group = sourceLoaded.keePassDatabase.content.group,
                        pathSegments = sourcePathSegments
                    )
                    if (extracted.removedGroup == null) {
                        throw IllegalArgumentException("分组不存在: $groupPath")
                    }

                    val targetParentSegments = decodeKeePassPathSegments(normalizedTargetParentPath)
                    val groupToMove = extracted.removedGroup
                    val targetRootBeforeInsert = if (sourceDatabaseId == targetDatabaseId) {
                        extracted.updatedGroup
                    } else {
                        targetLoaded.keePassDatabase.content.group
                    }
                    val existingTargetPath = if (sourceDatabaseId == targetDatabaseId) {
                        null
                    } else {
                        findGroupPathByUuid(
                            group = targetRootBeforeInsert,
                            currentPathKey = null,
                            targetUuid = groupToMove.uuid
                        )
                    }
                    val inserted = if (existingTargetPath != null) {
                        val expectedTargetPath = buildKeePassPathKey(normalizedTargetParentPath, groupToMove.name)
                        if (existingTargetPath != expectedTargetPath) {
                            throw IllegalArgumentException("目标数据库已存在相同 UUID 的不同分组: $existingTargetPath")
                        }
                        InsertedGroupResult(
                            updatedGroup = targetRootBeforeInsert,
                            groupInfo = buildGroupInfoFromPath(groupToMove, existingTargetPath)
                        )
                    } else {
                        insertGroupToPath(
                            group = targetRootBeforeInsert,
                            parentSegments = targetParentSegments,
                            groupToInsert = groupToMove,
                            currentPathKey = ""
                        )
                    }
                    val groupTreePatch = if (sourceDatabaseId == targetDatabaseId) {
                        null
                    } else {
                        buildGroupTreeChangePatch(
                            database = sourceLoaded.keePassDatabase,
                            root = groupToMove,
                            sourceRootGroupUuid = sourceGroupUuid,
                            targetParentGroupUuid = targetParentGroupUuid
                        )
                    }

                    if (sourceDatabaseId == targetDatabaseId) {
                        val updatedDatabase = sourceLoaded.keePassDatabase.modifyParentGroup { inserted.updatedGroup }
                        writeDatabase(
                            database = sourceLoaded.database,
                            credentials = sourceLoaded.credentials,
                            keePassDatabase = updatedDatabase,
                            sourceEtag = sourceLoaded.sourceEtag,
                            sourceLastModified = sourceLoaded.sourceLastModified,
                            beforeRemoteUpload = { database, _ ->
                                enqueuePendingChangeSetsIfRemote(
                                    database,
                                    listOf(
                                        KeePassChangeSet(
                                            databaseId = sourceLoaded.database.id,
                                            target = KeePassChangeTarget.GROUP,
                                            operation = KeePassChangeOperation.MOVE_GROUP,
                                            entryUuid = null,
                                            baseFingerprint = null,
                                            baseGroupPath = sourceParentPath,
                                            baseGroupUuid = sourceParentGroupUuid.toString(),
                                            structurePatch = KeePassStructureChangePatch(
                                                sourceGroupPath = normalizedSourcePath,
                                                sourceGroupUuid = sourceGroupUuid.toString(),
                                                targetGroupPath = normalizedTargetParentPath,
                                                targetGroupUuid = targetParentGroupUuid.toString(),
                                                groupName = groupToMove.name
                                            )
                                        )
                                    )
                                )
                            }
                        )
                    } else {
                        // Write the target first; if source cleanup fails, preserving both trees is
                        // safer than deleting source data before the target is durable. When retrying
                        // after a target-success/source-failure split, skip target insertion if the
                        // same group UUID already exists at the intended target path.
                        if (existingTargetPath == null) {
                            val targetDatabase = targetLoaded.keePassDatabase.modifyParentGroup { inserted.updatedGroup }
                            val createTreeChangeSet = KeePassChangeSet(
                                databaseId = targetLoaded.database.id,
                                target = KeePassChangeTarget.GROUP,
                                operation = KeePassChangeOperation.CREATE_GROUP_TREE,
                                entryUuid = null,
                                baseFingerprint = null,
                                baseGroupPath = normalizedTargetParentPath,
                                baseGroupUuid = targetParentGroupUuid.toString(),
                                structurePatch = KeePassStructureChangePatch(
                                    targetGroupPath = normalizedTargetParentPath,
                                    targetGroupUuid = targetParentGroupUuid.toString(),
                                    groupName = groupToMove.name
                                ),
                                groupTreePatch = groupTreePatch
                            )
                            writeDatabase(
                                database = targetLoaded.database,
                                credentials = targetLoaded.credentials,
                                keePassDatabase = targetDatabase,
                                sourceEtag = targetLoaded.sourceEtag,
                                sourceLastModified = targetLoaded.sourceLastModified,
                                beforeRemoteUpload = { database, _ ->
                                    enqueuePendingChangeSetsIfRemote(database, listOf(createTreeChangeSet))
                                }
                            )
                        }
                        val sourceDatabase = sourceLoaded.keePassDatabase.modifyParentGroup { extracted.updatedGroup }
                        val deleteTreeChangeSet = KeePassChangeSet(
                            databaseId = sourceLoaded.database.id,
                            target = KeePassChangeTarget.GROUP,
                            operation = KeePassChangeOperation.DELETE_GROUP_TREE,
                            entryUuid = null,
                            baseFingerprint = null,
                            baseGroupPath = sourceParentPath,
                            baseGroupUuid = sourceParentGroupUuid.toString(),
                            structurePatch = KeePassStructureChangePatch(
                                sourceGroupPath = normalizedSourcePath,
                                sourceGroupUuid = sourceGroupUuid.toString(),
                                groupName = groupToMove.name
                            ),
                            groupTreePatch = groupTreePatch
                        )
                        writeDatabase(
                            database = sourceLoaded.database,
                            credentials = sourceLoaded.credentials,
                            keePassDatabase = sourceDatabase,
                            sourceEtag = sourceLoaded.sourceEtag,
                            sourceLastModified = sourceLoaded.sourceLastModified,
                            beforeRemoteUpload = { database, _ ->
                                enqueuePendingChangeSetsIfRemote(database, listOf(deleteTreeChangeSet))
                            }
                        )
                    }

                    inserted.groupInfo
                } catch (e: Exception) {
                    invalidateLoadedDatabaseCache(sourceDatabaseId)
                    if (targetDatabaseId != sourceDatabaseId) {
                        invalidateLoadedDatabaseCache(targetDatabaseId)
                    }
                    throw e
                }
            }
            Result.success(movedGroupInfo)
        } catch (e: Exception) {
            Result.failure(normalizeError(e))
        }
    }

    suspend fun readPasswordEntries(databaseId: Long): Result<List<KeePassEntryData>> = withContext(Dispatchers.IO) {
        try {
            val (database, _, keePassDatabase) = loadDatabase(databaseId)
            val (entries, hasRecycleBinMeta) = collectEntryContexts(keePassDatabase)
            val resolutionContext = KeePassFieldReferenceResolver.buildContext(entries.map { it.entry })
            val data = buildPasswordEntryData(
                databaseId = database.id,
                databaseName = database.name,
                entries = entries,
                hasRecycleBinMeta = hasRecycleBinMeta,
                resolutionContext = resolutionContext
            )
            dao.updateEntryCount(database.id, data.size)
            Result.success(data)
        } catch (e: Exception) {
            Result.failure(normalizeError(e))
        }
    }

    suspend fun loadWorkspace(
        databaseId: Long,
        includeRecycleBinGroups: Boolean = false,
        allowedSecureItemTypes: Set<ItemType>? = null
    ): Result<KeePassWorkspaceSnapshot> = withContext(Dispatchers.IO) {
        try {
            val (database, _, keePassDatabase) = loadDatabase(databaseId)
            val (entries, hasRecycleBinMeta) = collectEntryContexts(keePassDatabase)
            val resolutionContext = KeePassFieldReferenceResolver.buildContext(entries.map { it.entry })
            val passwords = buildPasswordEntryData(
                databaseId = database.id,
                databaseName = database.name,
                entries = entries,
                hasRecycleBinMeta = hasRecycleBinMeta,
                resolutionContext = resolutionContext
            )
            val secureItems = entries.mapNotNull { context ->
                entryToSecureItemData(
                    entry = context.entry,
                    databaseId = databaseId,
                    groupPath = context.groupPath,
                    groupUuid = context.groupUuid,
                    isInRecycleBinByMeta = context.isInRecycleBinByMeta,
                    hasRecycleBinMeta = hasRecycleBinMeta,
                    allowedTypes = allowedSecureItemTypes,
                    resolutionContext = resolutionContext
                )
            }
            val groups = buildGroupInfoList(keePassDatabase, includeRecycleBinGroups)
            dao.updateEntryCount(database.id, passwords.size)
            Result.success(
                KeePassWorkspaceSnapshot(
                    passwords = passwords,
                    secureItems = secureItems,
                    groups = groups
                )
            )
        } catch (e: Exception) {
            Result.failure(normalizeError(e))
        }
    }

    suspend fun listGroups(
        databaseId: Long,
        includeRecycleBin: Boolean = false
    ): Result<List<KeePassGroupInfo>> = withContext(Dispatchers.IO) {
        try {
            val (_, _, keePassDatabase) = loadDatabase(databaseId)
            Result.success(buildGroupInfoList(keePassDatabase, includeRecycleBin))
        } catch (e: Exception) {
            Result.failure(normalizeError(e))
        }
    }

    suspend fun addOrUpdatePasswordEntries(
        databaseId: Long,
        entries: List<PasswordEntry>,
        resolvePassword: (PasswordEntry) -> String,
        forceSyncWrite: Boolean = false,
        customFieldsByEntryId: Map<Long, List<KeePassCustomFieldData>> = emptyMap()
    ): Result<Int> = withContext(Dispatchers.IO) {
        try {
            val addedCount = mutateDatabase(
                databaseId = databaseId,
                forceSyncWrite = forceSyncWrite
            ) { loaded ->
                var updatedDatabase = loaded.keePassDatabase
                var addedCount = 0
                val pendingChangeSets = mutableListOf<KeePassChangeSet>()
                entries.forEach { entry ->
                    val plainPassword = resolvePassword(entry)
                    val customFields = customFieldsByEntryId[entry.id].orEmpty()
                    val updateResult = updateEntry(
                        keePassDatabase = updatedDatabase,
                        databaseId = loaded.database.id,
                        entry = entry,
                        plainPassword = plainPassword,
                        customFields = customFields
                    )
                    pendingChangeSets += updateResult.changeSets
                    if (updateResult.changed) {
                        updatedDatabase = updateResult.database
                    } else {
                        val newEntry = buildEntry(entry, plainPassword, customFields)
                        buildCreateEntryChangeSet(
                            database = updatedDatabase,
                            databaseId = loaded.database.id,
                            target = KeePassChangeTarget.PASSWORD,
                            entry = newEntry,
                            targetGroupPath = entry.keepassGroupPath
                        )?.let { pendingChangeSets += it }
                        val updatedRoot = addEntryToGroupPath(
                            rootGroup = updatedDatabase.content.group,
                            groupPath = entry.keepassGroupPath,
                            entry = newEntry
                        )
                        updatedDatabase = updatedDatabase.modifyParentGroup { updatedRoot }
                        addedCount++
                    }
                }
                MutationPlan(
                    updatedDatabase = updatedDatabase,
                    result = addedCount,
                    beforeRemoteUpload = { database, _ ->
                        enqueuePendingChangeSetsIfRemote(database, pendingChangeSets)
                    },
                    afterWrite = { database, writtenDatabase ->
                        val allEntries = mutableListOf<Entry>()
                        collectEntries(writtenDatabase.content.group, allEntries)
                        dao.updateEntryCount(database.id, allEntries.size)
                    }
                )
            }
            Result.success(addedCount)
        } catch (e: Exception) {
            Result.failure(normalizeError(e))
        }
    }

    suspend fun updatePasswordEntry(
        databaseId: Long,
        entry: PasswordEntry,
        resolvePassword: (PasswordEntry) -> String,
        customFields: List<KeePassCustomFieldData> = emptyList()
    ): Result<Unit> = withContext(Dispatchers.IO) {
        try {
            mutateDatabase(databaseId) { loaded ->
                val plainPassword = resolvePassword(entry)
                val updateResult = updateEntry(
                    keePassDatabase = loaded.keePassDatabase,
                    databaseId = loaded.database.id,
                    entry = entry,
                    plainPassword = plainPassword,
                    customFields = customFields
                )
                val pendingChangeSets = updateResult.changeSets.toMutableList()
                val updatedDatabase = if (updateResult.changed) updateResult.database else {
                    val newEntry = buildEntry(entry, plainPassword, customFields)
                    buildCreateEntryChangeSet(
                        database = loaded.keePassDatabase,
                        databaseId = loaded.database.id,
                        target = KeePassChangeTarget.PASSWORD,
                        entry = newEntry,
                        targetGroupPath = entry.keepassGroupPath
                    )?.let { pendingChangeSets += it }
                    val updatedRoot = addEntryToGroupPath(
                        rootGroup = loaded.keePassDatabase.content.group,
                        groupPath = entry.keepassGroupPath,
                        entry = newEntry
                    )
                    loaded.keePassDatabase.modifyParentGroup { updatedRoot }
                }
                MutationPlan(
                    updatedDatabase = updatedDatabase,
                    result = Unit,
                    beforeRemoteUpload = { database, _ ->
                        enqueuePendingChangeSetsIfRemote(database, pendingChangeSets)
                    }
                )
            }
            Result.success(Unit)
        } catch (e: Exception) {
            Result.failure(normalizeError(e))
        }
    }

    // ================================================================
    // Attachment API（供 takagi.ru.monica.attachments.executor 使用）
    //
    // 对应 .kiro/specs/monica-android-attachments Requirement 6。
    // 所有方法都通过既有的 `mutateDatabase` / `loadDatabase` 通道，不另起 IO 路径，
    // 以保证 kdbx 保存时机、锁与缓存行为与密码/安全项一致。
    // ================================================================

    /** 附件元数据视图（供 executor 层构造 [takagi.ru.monica.attachments.model.Attachment] 使用）。 */
    data class KeePassAttachmentInfo(
        val hashHex: String,
        val fileName: String,
        val sizeBytes: Long,
        val memoryProtection: Boolean
    )

    /**
     * 读取某个条目的全部附件元数据（不读字节）。
     *
     * 要求数据库已解锁（即已通过正常流程 load 过）。
     */
    suspend fun readEntryAttachments(
        databaseId: Long,
        entryUuid: String
    ): Result<List<KeePassAttachmentInfo>> = withContext(Dispatchers.IO) {
        try {
            val loaded = loadDatabase(databaseId)
            val targetUuid = parseUuid(entryUuid)
                ?: return@withContext Result.failure(IllegalArgumentException("Invalid entry uuid"))
            val entry = findEntryByUuid(loaded.keePassDatabase.content.group, targetUuid)
                ?: return@withContext Result.failure(NoSuchElementException("Entry not found"))
            val pool = loaded.keePassDatabase.binaries
            val infos = entry.binaries.map { ref ->
                val data = pool[ref.hash]
                KeePassAttachmentInfo(
                    hashHex = ref.hash.hex(),
                    fileName = ref.name,
                    sizeBytes = (data?.rawContent?.size ?: 0).toLong(),
                    memoryProtection = data?.memoryProtection ?: false
                )
            }
            Result.success(infos)
        } catch (e: Exception) {
            Result.failure(normalizeError(e))
        }
    }

    /**
     * 读取某个附件的明文字节（kotpass 已在内部处理解压）。
     */
    suspend fun readAttachmentBytes(
        databaseId: Long,
        entryUuid: String,
        hashHex: String
    ): Result<ByteArray> = withContext(Dispatchers.IO) {
        try {
            val loaded = loadDatabase(databaseId)
            val targetUuid = parseUuid(entryUuid)
                ?: return@withContext Result.failure(IllegalArgumentException("Invalid entry uuid"))
            val entry = findEntryByUuid(loaded.keePassDatabase.content.group, targetUuid)
                ?: return@withContext Result.failure(NoSuchElementException("Entry not found"))
            val requestedRef = KeePassAttachmentRef.decode(hashHex)
            val matchRef = entry.binaries.firstOrNull {
                it.hash.hex().equals(requestedRef.hashHex, ignoreCase = true) &&
                    (requestedRef.fileName == null || it.name == requestedRef.fileName)
            }
                ?: return@withContext Result.failure(NoSuchElementException("Attachment not found in entry"))
            val data = loaded.keePassDatabase.binaries[matchRef.hash]
                ?: return@withContext Result.failure(NoSuchElementException("Attachment missing in binary pool"))
            // 用 inputStream 读回解压后的明文字节；避免直接访问 BinaryData.content / BinaryData.rawContent
            // 避免不同 kotpass 版本里 abstract property 访问差异带来的符号解析问题。
            val bytes = data.inputStream().use { it.readBytes() }
            Result.success(bytes)
        } catch (e: Exception) {
            Result.failure(normalizeError(e))
        }
    }

    /**
     * 为条目新增一个附件：把字节写入 `Meta.binaries` 池（若 hash 已存在则复用），
     * 再把对应 [BinaryReference] 追加到 `Entry.binaries`。
     */
    suspend fun addAttachmentToEntry(
        databaseId: Long,
        entryUuid: String,
        fileName: String,
        bytes: ByteArray,
        memoryProtection: Boolean = false,
        compressed: Boolean = true
    ): Result<KeePassAttachmentInfo> = withContext(Dispatchers.IO) {
        try {
            val targetUuid = parseUuid(entryUuid)
                ?: return@withContext Result.failure(IllegalArgumentException("Invalid entry uuid"))

            val info = mutateDatabase(databaseId) { loaded ->
                val oldDb = loaded.keePassDatabase
                val existingEntry = findEntryByUuid(oldDb.content.group, targetUuid)
                    ?: throw NoSuchElementException("Entry not found")
                val baseFingerprint = buildConflictEntrySignature(existingEntry)

                val binaryData: BinaryData = if (compressed) {
                    BinaryData.Uncompressed(memoryProtection, bytes).toCompressed()
                } else {
                    BinaryData.Uncompressed(memoryProtection, bytes)
                }
                val hash = binaryData.hash

                // 1. 手工替换 Entry.binaries；避开 kotpass 的 inline modifyEntry
                val newRef = BinaryReference(hash = hash, name = fileName)
                val updatedEntry = existingEntry.copy(
                    binaries = existingEntry.binaries + newRef
                )

                // 2. 先更新 Entry 到 Group 树
                val rootRewriter: (Group) -> Group = { root ->
                    updateEntryInGroup(root, targetUuid, updatedEntry)
                }
                val dbWithUpdatedEntry = oldDb.modifyParentGroup(rootRewriter)

                // 3. 再写二进制池（未映射时 put；已存在相同 hash 则复用）
                val updatedDatabase = dbWithUpdatedEntry.modifyBinaries { pool ->
                    if (pool.containsKey(hash)) pool else pool + (hash to binaryData)
                }

                val resultInfo = KeePassAttachmentInfo(
                    hashHex = hash.hex(),
                    fileName = fileName,
                    sizeBytes = bytes.size.toLong(),
                    memoryProtection = memoryProtection
                )
                val changeSet = KeePassChangeSet(
                    databaseId = loaded.database.id,
                    target = KeePassChangeTarget.UNKNOWN_ENTRY,
                    operation = KeePassChangeOperation.ADD_ATTACHMENT,
                    entryUuid = targetUuid.toString(),
                    baseFingerprint = baseFingerprint,
                    attachmentPatch = KeePassAttachmentChangePatch(
                        fileName = fileName,
                        binaryHash = hash.hex(),
                        protected = memoryProtection,
                        compressed = compressed,
                        contentBase64 = Base64.encodeToString(bytes, Base64.NO_WRAP)
                    )
                )
                MutationPlan(
                    updatedDatabase = updatedDatabase,
                    result = resultInfo,
                    beforeRemoteUpload = { database, _ ->
                        enqueuePendingChangeSetsIfRemote(database, listOf(changeSet))
                    }
                )
            }
            Result.success(info)
        } catch (e: OutOfMemoryError) {
            Result.failure(e)
        } catch (e: Exception) {
            Result.failure(normalizeError(e))
        }
    }

    /**
     * 从条目中删除一个附件；若该 hash 在所有 Entry 里都不再被引用，从 `Meta.binaries` 池释放。
     */
    suspend fun deleteAttachmentFromEntry(
        databaseId: Long,
        entryUuid: String,
        hashHex: String
    ): Result<Boolean> = withContext(Dispatchers.IO) {
        try {
            val targetUuid = parseUuid(entryUuid)
                ?: return@withContext Result.failure(IllegalArgumentException("Invalid entry uuid"))
            val removed = mutateDatabase(databaseId) { loaded ->
                val oldDb = loaded.keePassDatabase
                val existingEntry = findEntryByUuid(oldDb.content.group, targetUuid)
                    ?: throw NoSuchElementException("Entry not found")
                val baseFingerprint = buildConflictEntrySignature(existingEntry)
                val requestedRef = KeePassAttachmentRef.decode(hashHex)
                val targetRef = existingEntry.binaries
                    .firstOrNull {
                        it.hash.hex().equals(requestedRef.hashHex, ignoreCase = true) &&
                            (requestedRef.fileName == null || it.name == requestedRef.fileName)
                    }
                if (targetRef == null) {
                    return@mutateDatabase MutationPlan(updatedDatabase = oldDb, result = false)
                }
                val updatedEntry = existingEntry.copy(
                    binaries = existingEntry.binaries - targetRef
                )
                val rootRewriter: (Group) -> Group = { root ->
                    updateEntryInGroup(root, targetUuid, updatedEntry)
                }
                val rootReplaced = oldDb.modifyParentGroup(rootRewriter)
                // 判断该 hash 是否还被任何条目引用；不再引用时从池释放。
                val stillReferenced = anyEntryReferencesHash(
                    rootReplaced.content.group,
                    targetRef.hash
                )
                val compacted = if (stillReferenced) {
                    rootReplaced
                } else {
                    rootReplaced.modifyBinaries { pool -> pool - targetRef.hash }
                }
                val binaryData = oldDb.binaries[targetRef.hash]
                val changeSet = KeePassChangeSet(
                    databaseId = loaded.database.id,
                    target = KeePassChangeTarget.UNKNOWN_ENTRY,
                    operation = KeePassChangeOperation.REMOVE_ATTACHMENT,
                    entryUuid = targetUuid.toString(),
                    baseFingerprint = baseFingerprint,
                    attachmentPatch = KeePassAttachmentChangePatch(
                        fileName = targetRef.name,
                        binaryHash = targetRef.hash.hex(),
                        protected = binaryData?.memoryProtection ?: false
                    )
                )
                MutationPlan(
                    updatedDatabase = compacted,
                    result = true,
                    beforeRemoteUpload = { database, _ ->
                        enqueuePendingChangeSetsIfRemote(database, listOf(changeSet))
                    }
                )
            }
            Result.success(removed)
        } catch (e: Exception) {
            Result.failure(normalizeError(e))
        }
    }

    /** 仅判断 kdbx 是否已解锁（有 cache 命中），不会触发解密。 */
    fun isDatabaseUnlocked(databaseId: Long): Boolean {
        return synchronized(loadedDatabaseCache) { loadedDatabaseCache.containsKey(databaseId) }
    }

    private fun findEntryByUuid(group: Group, uuid: UUID): Entry? {
        group.entries.firstOrNull { it.uuid == uuid }?.let { return it }
        for (child in group.groups) {
            findEntryByUuid(child, uuid)?.let { return it }
        }
        return null
    }

    private fun updateEntryInGroup(group: Group, targetUuid: UUID, replacement: Entry): Group {
        val newEntries = group.entries.map { if (it.uuid == targetUuid) replacement else it }
        val newGroups = group.groups.map { updateEntryInGroup(it, targetUuid, replacement) }
        return group.copy(entries = newEntries, groups = newGroups)
    }

    private fun anyEntryReferencesHash(group: Group, hash: okio.ByteString): Boolean {
        if (group.entries.any { entry -> entry.binaries.any { it.hash == hash } }) return true
        return group.groups.any { anyEntryReferencesHash(it, hash) }
    }

    // ================================================================
    // Attachment API end
    // ================================================================

    suspend fun addPasswordEntry(
        databaseId: Long,
        entry: PasswordEntry,
        resolvePassword: (PasswordEntry) -> String,
        forceSyncWrite: Boolean = false,
        customFields: List<KeePassCustomFieldData> = emptyList()
    ): Result<Unit> = withContext(Dispatchers.IO) {
        try {
            mutateDatabase(
                databaseId = databaseId,
                forceSyncWrite = forceSyncWrite
            ) { loaded ->
                val plainPassword = resolvePassword(entry)
                val newEntry = buildEntry(entry, plainPassword, customFields)
                val updatedRoot = addEntryToGroupPath(
                    rootGroup = loaded.keePassDatabase.content.group,
                    groupPath = entry.keepassGroupPath,
                    entry = newEntry
                )
                val updatedDatabase = loaded.keePassDatabase.modifyParentGroup { updatedRoot }
                MutationPlan(updatedDatabase = updatedDatabase, result = Unit)
            }
            Result.success(Unit)
        } catch (e: Exception) {
            Result.failure(normalizeError(e))
        }
    }

    suspend fun deletePasswordEntries(
        databaseId: Long,
        entries: List<PasswordEntry>,
        forceSyncWrite: Boolean = false
    ): Result<Int> = withContext(Dispatchers.IO) {
        try {
            val removedCount = mutateDatabase(
                databaseId = databaseId,
                forceSyncWrite = forceSyncWrite
            ) { loaded ->
                val deleteResult = applyEntryStructureChanges(
                    loaded = loaded,
                    targets = entries,
                    targetType = KeePassChangeTarget.PASSWORD,
                    operation = KeePassChangeOperation.PERMANENT_DELETE,
                    matcher = { entry, target, resolutionContext ->
                        matchesPasswordEntry(entry, target, resolutionContext)
                    }
                )
                MutationPlan(
                    updatedDatabase = deleteResult.database,
                    result = deleteResult.changedCount,
                    beforeRemoteUpload = { database, _ ->
                        enqueuePendingChangeSetsIfRemote(database, deleteResult.changeSets)
                    },
                    afterWrite = { database, writtenDatabase ->
                        val allEntries = mutableListOf<Entry>()
                        collectEntries(writtenDatabase.content.group, allEntries)
                        dao.updateEntryCount(database.id, allEntries.size)
                    }
                )
            }
            Result.success(removedCount)
        } catch (e: Exception) {
            Result.failure(normalizeError(e))
        }
    }

    suspend fun movePasswordEntriesToRecycleBin(
        databaseId: Long,
        entries: List<PasswordEntry>,
        forceSyncWrite: Boolean = false
    ): Result<Int> = withContext(Dispatchers.IO) {
        try {
            val movedCount = mutateDatabase(
                databaseId = databaseId,
                forceSyncWrite = forceSyncWrite
            ) { loaded ->
                val moveResult = applyEntryStructureChanges(
                    loaded = loaded,
                    targets = entries,
                    targetType = KeePassChangeTarget.PASSWORD,
                    operation = KeePassChangeOperation.MOVE_TO_RECYCLE_BIN,
                    matcher = { entry, target, resolutionContext ->
                        matchesPasswordEntry(entry, target, resolutionContext)
                    }
                )
                MutationPlan(
                    updatedDatabase = moveResult.database,
                    result = moveResult.changedCount,
                    beforeRemoteUpload = { database, _ ->
                        enqueuePendingChangeSetsIfRemote(database, moveResult.changeSets)
                    },
                    afterWrite = { database, writtenDatabase ->
                        val allEntries = mutableListOf<Entry>()
                        collectEntries(writtenDatabase.content.group, allEntries)
                        dao.updateEntryCount(database.id, allEntries.size)
                    }
                )
            }
            Result.success(movedCount)
        } catch (e: Exception) {
            Result.failure(normalizeError(e))
        }
    }

    suspend fun restorePasswordEntriesFromRecycleBin(
        databaseId: Long,
        entries: List<PasswordEntry>,
        forceSyncWrite: Boolean = false
    ): Result<Map<Long, KeePassRestoreTarget>> = withContext(Dispatchers.IO) {
        try {
            val restoredTargets = mutateDatabase(
                databaseId = databaseId,
                forceSyncWrite = forceSyncWrite
            ) { loaded ->
                val restoreResult = restoreEntriesFromRecycleBin(
                    loaded = loaded,
                    targets = entries,
                    targetType = KeePassChangeTarget.PASSWORD,
                    matcher = { entry, target, resolutionContext ->
                        matchesPasswordEntry(entry, target, resolutionContext)
                    },
                    roomId = { it.id },
                    preferredGroupPath = { it.keepassGroupPath },
                    preferredGroupUuid = { it.keepassGroupUuid }
                )
                MutationPlan(
                    updatedDatabase = restoreResult.database,
                    result = restoreResult.targetsByRoomId,
                    beforeRemoteUpload = { database, _ ->
                        enqueuePendingChangeSetsIfRemote(database, restoreResult.changeSets)
                    },
                    afterWrite = { database, writtenDatabase ->
                        val allEntries = mutableListOf<Entry>()
                        collectEntries(writtenDatabase.content.group, allEntries)
                        dao.updateEntryCount(database.id, allEntries.size)
                    }
                )
            }
            Result.success(restoredTargets)
        } catch (e: Exception) {
            Result.failure(normalizeError(e))
        }
    }

    suspend fun readSecureItems(
        databaseId: Long,
        allowedTypes: Set<ItemType>? = null
    ): Result<List<KeePassSecureItemData>> = withContext(Dispatchers.IO) {
        try {
            val (_, _, keePassDatabase) = loadDatabase(databaseId)
            val (entries, hasRecycleBinMeta) = collectEntryContexts(keePassDatabase)
            val resolutionContext = KeePassFieldReferenceResolver.buildContext(entries.map { it.entry })
            val data = entries.mapNotNull { context ->
                entryToSecureItemData(
                    entry = context.entry,
                    databaseId = databaseId,
                    groupPath = context.groupPath,
                    groupUuid = context.groupUuid,
                    isInRecycleBinByMeta = context.isInRecycleBinByMeta,
                    hasRecycleBinMeta = hasRecycleBinMeta,
                    allowedTypes = allowedTypes,
                    resolutionContext = resolutionContext
                )
            }
            Result.success(data)
        } catch (e: Exception) {
            Result.failure(normalizeError(e))
        }
    }

    suspend fun readPasskeyEntries(databaseId: Long): Result<List<PasskeyEntry>> = withContext(Dispatchers.IO) {
        try {
            val (_, _, keePassDatabase) = loadDatabase(databaseId)
            val (entries, _) = collectEntryContexts(keePassDatabase)
            val resolutionContext = KeePassFieldReferenceResolver.buildContext(entries.map { it.entry })
            val data = entries.mapNotNull { context ->
                entryToPasskey(
                    entry = context.entry,
                    databaseId = databaseId,
                    groupPath = context.groupPath,
                    groupUuid = context.groupUuid,
                    resolutionContext = resolutionContext
                )
            }
            Result.success(data)
        } catch (e: Exception) {
            Result.failure(normalizeError(e))
        }
    }

    suspend fun addOrUpdateSecureItems(
        databaseId: Long,
        items: List<SecureItem>,
        forceSyncWrite: Boolean = false
    ): Result<Int> = withContext(Dispatchers.IO) {
        try {
            val addedCount = mutateDatabase(
                databaseId = databaseId,
                forceSyncWrite = forceSyncWrite
            ) { loaded ->
                var updatedDatabase = loaded.keePassDatabase
                var addedCount = 0
                val pendingChangeSets = mutableListOf<KeePassChangeSet>()
                items.forEach { item ->
                    val updateResult = updateSecureItemInternal(
                        keePassDatabase = updatedDatabase,
                        databaseId = loaded.database.id,
                        item = item
                    )
                    pendingChangeSets += updateResult.changeSets
                    if (updateResult.changed) {
                        updatedDatabase = updateResult.database
                    } else {
                        val newEntry = buildSecureItemEntry(item)
                        buildCreateEntryChangeSet(
                            database = updatedDatabase,
                            databaseId = loaded.database.id,
                            target = KeePassChangeTarget.SECURE_ITEM,
                            entry = newEntry,
                            targetGroupPath = item.keepassGroupPath
                        )?.let { pendingChangeSets += it }
                        val updatedRoot = addEntryToGroupPath(
                            rootGroup = updatedDatabase.content.group,
                            groupPath = item.keepassGroupPath,
                            entry = newEntry
                        )
                        updatedDatabase = updatedDatabase.modifyParentGroup { updatedRoot }
                        addedCount++
                    }
                }
                MutationPlan(
                    updatedDatabase = updatedDatabase,
                    result = addedCount,
                    beforeRemoteUpload = { database, _ ->
                        enqueuePendingChangeSetsIfRemote(database, pendingChangeSets)
                    }
                )
            }
            Result.success(addedCount)
        } catch (e: Exception) {
            Result.failure(normalizeError(e))
        }
    }

    suspend fun addOrUpdatePasskeys(
        databaseId: Long,
        passkeys: List<PasskeyEntry>
    ): Result<Int> = withContext(Dispatchers.IO) {
        try {
            val addedCount = mutateDatabase(databaseId = databaseId) { loaded ->
                var updatedDatabase = loaded.keePassDatabase
                var addedCount = 0
                val pendingChangeSets = mutableListOf<KeePassChangeSet>()
                passkeys.forEach { passkey ->
                    val updateResult = updatePasskeyInternal(
                        keePassDatabase = updatedDatabase,
                        databaseId = loaded.database.id,
                        passkey = passkey
                    )
                    pendingChangeSets += updateResult.changeSets
                    if (updateResult.changed) {
                        updatedDatabase = updateResult.database
                    } else {
                        val newEntry = buildPasskeyEntry(passkey)
                        buildCreateEntryChangeSet(
                            database = updatedDatabase,
                            databaseId = loaded.database.id,
                            target = KeePassChangeTarget.PASSKEY,
                            entry = newEntry,
                            targetGroupPath = passkey.keepassGroupPath
                        )?.let { pendingChangeSets += it }
                        val updatedRoot = addEntryToGroupPath(
                            rootGroup = updatedDatabase.content.group,
                            groupPath = passkey.keepassGroupPath,
                            entry = newEntry
                        )
                        updatedDatabase = updatedDatabase.modifyParentGroup { updatedRoot }
                        addedCount++
                    }
                }
                MutationPlan(
                    updatedDatabase = updatedDatabase,
                    result = addedCount,
                    beforeRemoteUpload = { database, _ ->
                        enqueuePendingChangeSetsIfRemote(database, pendingChangeSets)
                    }
                )
            }
            Result.success(addedCount)
        } catch (e: Exception) {
            Result.failure(normalizeError(e))
        }
    }

    suspend fun updateSecureItem(
        databaseId: Long,
        item: SecureItem
    ): Result<Unit> = withContext(Dispatchers.IO) {
        try {
            mutateDatabase(databaseId) { loaded ->
                val updateResult = updateSecureItemInternal(
                    keePassDatabase = loaded.keePassDatabase,
                    databaseId = loaded.database.id,
                    item = item
                )
                val pendingChangeSets = updateResult.changeSets.toMutableList()
                val updatedDatabase = if (updateResult.changed) {
                    updateResult.database
                } else {
                    val newEntry = buildSecureItemEntry(item)
                    buildCreateEntryChangeSet(
                        database = loaded.keePassDatabase,
                        databaseId = loaded.database.id,
                        target = KeePassChangeTarget.SECURE_ITEM,
                        entry = newEntry,
                        targetGroupPath = item.keepassGroupPath
                    )?.let { pendingChangeSets += it }
                    val updatedRoot = addEntryToGroupPath(
                        rootGroup = loaded.keePassDatabase.content.group,
                        groupPath = item.keepassGroupPath,
                        entry = newEntry
                    )
                    loaded.keePassDatabase.modifyParentGroup { updatedRoot }
                }
                MutationPlan(
                    updatedDatabase = updatedDatabase,
                    result = Unit,
                    beforeRemoteUpload = { database, _ ->
                        enqueuePendingChangeSetsIfRemote(database, pendingChangeSets)
                    }
                )
            }
            Result.success(Unit)
        } catch (e: Exception) {
            Result.failure(normalizeError(e))
        }
    }

    suspend fun updatePasskey(
        databaseId: Long,
        passkey: PasskeyEntry
    ): Result<Unit> = withContext(Dispatchers.IO) {
        try {
            mutateDatabase(databaseId) { loaded ->
                val updateResult = updatePasskeyInternal(
                    keePassDatabase = loaded.keePassDatabase,
                    databaseId = loaded.database.id,
                    passkey = passkey
                )
                val pendingChangeSets = updateResult.changeSets.toMutableList()
                val updatedDatabase = if (updateResult.changed) {
                    updateResult.database
                } else {
                    val newEntry = buildPasskeyEntry(passkey)
                    buildCreateEntryChangeSet(
                        database = loaded.keePassDatabase,
                        databaseId = loaded.database.id,
                        target = KeePassChangeTarget.PASSKEY,
                        entry = newEntry,
                        targetGroupPath = passkey.keepassGroupPath
                    )?.let { pendingChangeSets += it }
                    val updatedRoot = addEntryToGroupPath(
                        rootGroup = loaded.keePassDatabase.content.group,
                        groupPath = passkey.keepassGroupPath,
                        entry = newEntry
                    )
                    loaded.keePassDatabase.modifyParentGroup { updatedRoot }
                }
                MutationPlan(
                    updatedDatabase = updatedDatabase,
                    result = Unit,
                    beforeRemoteUpload = { database, _ ->
                        enqueuePendingChangeSetsIfRemote(database, pendingChangeSets)
                    }
                )
            }
            Result.success(Unit)
        } catch (e: Exception) {
            Result.failure(normalizeError(e))
        }
    }

    suspend fun resolveRestoreTarget(
        databaseId: Long,
        preferredGroupPath: String?,
        preferredGroupUuid: String? = null
    ): Result<KeePassRestoreTarget> = withContext(Dispatchers.IO) {
        try {
            val (_, _, keePassDatabase) = loadDatabase(databaseId)
            val groupContextIndex = buildGroupTraversalContextIndex(keePassDatabase)
            val preferredUuid = parseUuid(preferredGroupUuid)
            if (preferredUuid != null) {
                val preferredContext = groupContextIndex[preferredUuid]
                if (preferredContext != null) {
                    val preferredInRecycle = preferredContext.isInRecycleBinByMeta
                    if (!preferredInRecycle) {
                        return@withContext Result.success(
                            KeePassRestoreTarget(
                                groupPath = preferredContext.pathKey,
                                groupUuid = preferredContext.groupUuid.toString()
                            )
                        )
                    }
                }
            }
            if (preferredGroupPath.isNullOrBlank()) {
                return@withContext Result.success(KeePassRestoreTarget(groupPath = null, groupUuid = null))
            }
            val recycleBinUuid = resolveRecycleBinUuid(keePassDatabase.content.meta)
            if (recycleBinUuid != null) {
                val inRecycleByMeta = isGroupPathInRecycleBinByMeta(
                    group = keePassDatabase.content.group,
                    currentPathKey = null,
                    targetPathKey = preferredGroupPath,
                    recycleBinUuid = recycleBinUuid,
                    parentInRecycleBin = false
                )
                if (inRecycleByMeta != null) {
                    val resolvedPath = if (inRecycleByMeta) null else preferredGroupPath
                    val resolvedUuid = resolvedPath?.let {
                        findGroupUuidByPath(
                            group = keePassDatabase.content.group,
                            currentPathKey = null,
                            targetPathKey = it
                        )?.toString()
                    }
                    return@withContext Result.success(
                        KeePassRestoreTarget(
                            groupPath = resolvedPath,
                            groupUuid = resolvedUuid
                        )
                    )
                }
            }
            if (isLikelyRecycleBinPath(preferredGroupPath)) {
                return@withContext Result.success(KeePassRestoreTarget(groupPath = null, groupUuid = null))
            }
            Result.success(
                KeePassRestoreTarget(
                    groupPath = preferredGroupPath,
                    groupUuid = findGroupUuidByPath(
                        group = keePassDatabase.content.group,
                        currentPathKey = null,
                        targetPathKey = preferredGroupPath
                    )?.toString()
                )
            )
        } catch (e: Exception) {
            Result.failure(normalizeError(e))
        }
    }

    suspend fun resolveRestoreTargetForPassword(
        databaseId: Long,
        target: PasswordEntry
    ): Result<KeePassRestoreTarget> = withContext(Dispatchers.IO) {
        try {
            val (_, _, keePassDatabase) = loadDatabase(databaseId)
            val groupContextIndex = buildGroupTraversalContextIndex(keePassDatabase)
            val (entries, hasRecycleBinMeta) = collectEntryContexts(keePassDatabase)
            val resolutionContext = KeePassFieldReferenceResolver.buildContext(entries.map { it.entry })
            val matched = entries.firstOrNull { context ->
                matchesPasswordEntry(context.entry, target, resolutionContext)
            }
                ?: return@withContext resolveRestoreTarget(
                    databaseId = databaseId,
                    preferredGroupPath = target.keepassGroupPath,
                    preferredGroupUuid = target.keepassGroupUuid
                )
            Result.success(
                resolveRestoreTargetFromEntryContext(
                    entryContext = matched,
                    hasRecycleBinMeta = hasRecycleBinMeta,
                    groupContextIndex = groupContextIndex
                )
            )
        } catch (e: Exception) {
            Result.failure(normalizeError(e))
        }
    }

    suspend fun resolveRestoreGroupPathForPassword(
        databaseId: Long,
        target: PasswordEntry
    ): Result<String?> = withContext(Dispatchers.IO) {
        resolveRestoreTargetForPassword(databaseId, target).map { it.groupPath }
    }

    suspend fun resolveRestoreTargetForSecureItem(
        databaseId: Long,
        target: SecureItem
    ): Result<KeePassRestoreTarget> = withContext(Dispatchers.IO) {
        try {
            val (_, _, keePassDatabase) = loadDatabase(databaseId)
            val groupContextIndex = buildGroupTraversalContextIndex(keePassDatabase)
            val (entries, hasRecycleBinMeta) = collectEntryContexts(keePassDatabase)
            val resolutionContext = KeePassFieldReferenceResolver.buildContext(entries.map { it.entry })
            val matched = entries.firstOrNull { context ->
                matchesSecureItemEntry(context.entry, target, resolutionContext)
            }
                ?: return@withContext resolveRestoreTarget(
                    databaseId = databaseId,
                    preferredGroupPath = target.keepassGroupPath,
                    preferredGroupUuid = target.keepassGroupUuid
                )
            Result.success(
                resolveRestoreTargetFromEntryContext(
                    entryContext = matched,
                    hasRecycleBinMeta = hasRecycleBinMeta,
                    groupContextIndex = groupContextIndex
                )
            )
        } catch (e: Exception) {
            Result.failure(normalizeError(e))
        }
    }

    suspend fun resolveRestoreGroupPathForSecureItem(
        databaseId: Long,
        target: SecureItem
    ): Result<String?> = withContext(Dispatchers.IO) {
        resolveRestoreTargetForSecureItem(databaseId, target).map { it.groupPath }
    }

    suspend fun deleteSecureItems(
        databaseId: Long,
        items: List<SecureItem>,
        forceSyncWrite: Boolean = false
    ): Result<Int> = withContext(Dispatchers.IO) {
        try {
            val removedCount = mutateDatabase(
                databaseId = databaseId,
                forceSyncWrite = forceSyncWrite
            ) { loaded ->
                val deleteResult = applyEntryStructureChanges(
                    loaded = loaded,
                    targets = items,
                    targetType = KeePassChangeTarget.SECURE_ITEM,
                    operation = KeePassChangeOperation.PERMANENT_DELETE,
                    matcher = { entry, target, resolutionContext ->
                        matchesSecureItemEntry(entry, target, resolutionContext)
                    }
                )
                MutationPlan(
                    updatedDatabase = deleteResult.database,
                    result = deleteResult.changedCount,
                    beforeRemoteUpload = { database, _ ->
                        enqueuePendingChangeSetsIfRemote(database, deleteResult.changeSets)
                    },
                    afterWrite = { database, writtenDatabase ->
                        val allEntries = mutableListOf<Entry>()
                        collectEntries(writtenDatabase.content.group, allEntries)
                        dao.updateEntryCount(database.id, allEntries.size)
                    }
                )
            }
            Result.success(removedCount)
        } catch (e: Exception) {
            Result.failure(normalizeError(e))
        }
    }

    suspend fun deletePasskeys(
        databaseId: Long,
        passkeys: List<PasskeyEntry>
    ): Result<Int> = withContext(Dispatchers.IO) {
        try {
            val removedCount = mutateDatabase(databaseId = databaseId) { loaded ->
                val deleteResult = applyEntryStructureChanges(
                    loaded = loaded,
                    targets = passkeys,
                    targetType = KeePassChangeTarget.PASSKEY,
                    operation = KeePassChangeOperation.PERMANENT_DELETE,
                    matcher = { entry, target, resolutionContext ->
                        matchesPasskeyEntry(entry, target, resolutionContext)
                    }
                )
                MutationPlan(
                    updatedDatabase = deleteResult.database,
                    result = deleteResult.changedCount,
                    beforeRemoteUpload = { database, _ ->
                        enqueuePendingChangeSetsIfRemote(database, deleteResult.changeSets)
                    },
                    afterWrite = { database, writtenDatabase ->
                        val allEntries = mutableListOf<Entry>()
                        collectEntries(writtenDatabase.content.group, allEntries)
                        dao.updateEntryCount(database.id, allEntries.size)
                    }
                )
            }
            Result.success(removedCount)
        } catch (e: Exception) {
            Result.failure(normalizeError(e))
        }
    }

    suspend fun moveSecureItemsToRecycleBin(
        databaseId: Long,
        items: List<SecureItem>,
        forceSyncWrite: Boolean = false
    ): Result<Int> = withContext(Dispatchers.IO) {
        try {
            val movedCount = mutateDatabase(
                databaseId = databaseId,
                forceSyncWrite = forceSyncWrite
            ) { loaded ->
                val moveResult = applyEntryStructureChanges(
                    loaded = loaded,
                    targets = items,
                    targetType = KeePassChangeTarget.SECURE_ITEM,
                    operation = KeePassChangeOperation.MOVE_TO_RECYCLE_BIN,
                    matcher = { entry, target, resolutionContext ->
                        matchesSecureItemEntry(entry, target, resolutionContext)
                    }
                )
                MutationPlan(
                    updatedDatabase = moveResult.database,
                    result = moveResult.changedCount,
                    beforeRemoteUpload = { database, _ ->
                        enqueuePendingChangeSetsIfRemote(database, moveResult.changeSets)
                    },
                    afterWrite = { database, writtenDatabase ->
                        val allEntries = mutableListOf<Entry>()
                        collectEntries(writtenDatabase.content.group, allEntries)
                        dao.updateEntryCount(database.id, allEntries.size)
                    }
                )
            }
            Result.success(movedCount)
        } catch (e: Exception) {
            Result.failure(normalizeError(e))
        }
    }

    suspend fun restoreSecureItemsFromRecycleBin(
        databaseId: Long,
        items: List<SecureItem>,
        forceSyncWrite: Boolean = false
    ): Result<Map<Long, KeePassRestoreTarget>> = withContext(Dispatchers.IO) {
        try {
            val restoredTargets = mutateDatabase(
                databaseId = databaseId,
                forceSyncWrite = forceSyncWrite
            ) { loaded ->
                val restoreResult = restoreEntriesFromRecycleBin(
                    loaded = loaded,
                    targets = items,
                    targetType = KeePassChangeTarget.SECURE_ITEM,
                    matcher = { entry, target, resolutionContext ->
                        matchesSecureItemEntry(entry, target, resolutionContext)
                    },
                    roomId = { it.id },
                    preferredGroupPath = { it.keepassGroupPath },
                    preferredGroupUuid = { it.keepassGroupUuid }
                )
                MutationPlan(
                    updatedDatabase = restoreResult.database,
                    result = restoreResult.targetsByRoomId,
                    beforeRemoteUpload = { database, _ ->
                        enqueuePendingChangeSetsIfRemote(database, restoreResult.changeSets)
                    },
                    afterWrite = { database, writtenDatabase ->
                        val allEntries = mutableListOf<Entry>()
                        collectEntries(writtenDatabase.content.group, allEntries)
                        dao.updateEntryCount(database.id, allEntries.size)
                    }
                )
            }
            Result.success(restoredTargets)
        } catch (e: Exception) {
            Result.failure(normalizeError(e))
        }
    }

    private fun buildEntry(
        entry: PasswordEntry,
        plainPassword: String,
        customFields: List<KeePassCustomFieldData> = emptyList()
    ): Entry {
        val base = Entry(
            uuid = parseUuid(entry.keepassEntryUuid) ?: UUID.randomUUID(),
            fields = buildEntryFields(entry, plainPassword, customFields)
        )
        return applyPasswordEntryPresentation(base, entry)
    }

    private fun buildUpdatedEntry(
        existingEntry: Entry,
        entry: PasswordEntry,
        plainPassword: String,
        customFields: List<KeePassCustomFieldData> = emptyList()
    ): Entry {
        val base = buildPasswordEntryFieldPatch(entry, plainPassword, customFields).applyTo(existingEntry)
        return applyPasswordEntryPresentation(base, entry)
    }

    private fun buildPasswordEntryFieldPatch(
        entry: PasswordEntry,
        plainPassword: String,
        customFields: List<KeePassCustomFieldData> = emptyList()
    ): KeePassEntryFieldPatch {
        val replacementFields = buildEntryFields(entry, plainPassword, customFields)
        return KeePassEntryFieldPatch.fromEntryFields(
            replacementFields = replacementFields,
            removeManagedField = KeePassFieldRegistry::isPasswordEntryOverlayField,
            removeFieldNames = replacementFields.keys + customFields.map { it.title.trim() }
        )
    }

    private fun applyPasswordEntryPresentation(
        base: Entry,
        entry: PasswordEntry
    ): Entry {
        // WIFI 条目使用 IRCommunication 图标，与 keepass2android WLan 模板一致。
        return if (entry.isWifiEntry()) {
            base.copy(icon = app.keemobile.kotpass.constants.PredefinedIcon.IRCommunication)
        } else {
            base
        }
    }

    private fun buildPasskeyEntry(passkey: PasskeyEntry): Entry {
        return Entry(
            uuid = UUID.randomUUID(),
            fields = buildPasskeyFields(passkey),
            times = buildPasskeyTimeData(passkey)
        )
    }

    private fun buildPasskeyTimeData(passkey: PasskeyEntry): TimeData {
        val createdAt = epochMillisToInstant(passkey.createdAt)
        val lastUsedAt = epochMillisToInstant(passkey.lastUsedAt.takeIf { it > 0L } ?: passkey.createdAt)
        val now = Instant.now()
        return TimeData(
            creationTime = createdAt,
            lastAccessTime = lastUsedAt,
            lastModificationTime = now,
            locationChanged = now,
            expiryTime = createdAt,
            expires = false,
            usageCount = passkey.useCount
        )
    }

    private fun epochMillisToInstant(value: Long): Instant {
        return runCatching { Instant.ofEpochMilli(value.takeIf { it > 0L } ?: System.currentTimeMillis()) }
            .getOrDefault(Instant.now())
    }

    private fun buildEntryFields(
        entry: PasswordEntry,
        plainPassword: String,
        customFields: List<KeePassCustomFieldData> = emptyList()
    ): EntryFields {
        val monicaId = if (entry.id > 0) entry.id.toString() else ""
        val pairs = mutableListOf<Pair<String, EntryValue>>(
            "Title" to EntryValue.Plain(entry.title),
            "UserName" to EntryValue.Plain(entry.username),
            "Password" to EntryValue.Encrypted(EncryptedValue.fromString(plainPassword)),
            "URL" to EntryValue.Plain(entry.website),
            "Notes" to EntryValue.Plain(entry.notes)
        )
        if (monicaId.isNotEmpty()) {
            pairs.add(FIELD_MONICA_LOCAL_ID to EntryValue.Plain(monicaId))
        }
        appendPasswordCompatibilityFields(pairs, entry)
        // WIFI 条目：写入与 keepass2android WLan 模板兼容的标准字段（SSID + Password），
        // 并把 Monica 专属扩展 (安全类型、代理、IP 设置等) 塞到 MonicaWifiData JSON。
        // 这样其他 KeePass 客户端能读到 SSID/Password 基本信息，Monica 自己读回时
        // 则能无损还原完整配置。
        if (entry.isWifiEntry()) {
            pairs += FIELD_MONICA_LOGIN_TYPE to EntryValue.Plain("WIFI")
            val wifi = takagi.ru.monica.data.model.WifiData.fromJsonOrEmpty(entry.wifiMetadata)
            val ssidForStandardField = wifi.ssid.ifBlank { entry.title }
            if (ssidForStandardField.isNotBlank()) {
                pairs += FIELD_WIFI_SSID to EntryValue.Plain(ssidForStandardField)
            }
            if (entry.wifiMetadata.isNotBlank()) {
                pairs += FIELD_MONICA_WIFI_DATA to EntryValue.Plain(entry.wifiMetadata)
            }
        }
        if (entry.isSshKeyEntry()) {
            pairs += FIELD_MONICA_LOGIN_TYPE to EntryValue.Plain("SSH_KEY")
        }
        SshKeyDataCodec.decode(entry.sshKeyData)?.let { ssh ->
            if (ssh.algorithm.isNotBlank()) {
                pairs += FIELD_MONICA_SSH_ALGORITHM to EntryValue.Plain(ssh.algorithm)
            }
            if (ssh.keySize > 0) {
                pairs += FIELD_MONICA_SSH_KEY_SIZE to EntryValue.Plain(ssh.keySize.toString())
            }
            if (ssh.publicKeyOpenSsh.isNotBlank()) {
                pairs += FIELD_MONICA_SSH_PUBLIC_KEY to EntryValue.Plain(ssh.publicKeyOpenSsh)
            }
            if (ssh.privateKeyOpenSsh.isNotBlank()) {
                pairs += FIELD_MONICA_SSH_PRIVATE_KEY to EntryValue.Encrypted(
                    EncryptedValue.fromString(ssh.privateKeyOpenSsh)
                )
            }
            if (ssh.fingerprintSha256.isNotBlank()) {
                pairs += FIELD_MONICA_SSH_FINGERPRINT to EntryValue.Plain(ssh.fingerprintSha256)
            }
            if (ssh.comment.isNotBlank()) {
                pairs += FIELD_MONICA_SSH_COMMENT to EntryValue.Plain(ssh.comment)
            }
            if (ssh.format.isNotBlank()) {
                pairs += FIELD_MONICA_SSH_FORMAT to EntryValue.Plain(ssh.format)
            }
        }
        appendKeePassCustomFields(pairs, customFields)
        return EntryFields.of(*pairs.toTypedArray())
    }

    private fun appendPasswordCompatibilityFields(
        pairs: MutableList<Pair<String, EntryValue>>,
        entry: PasswordEntry
    ) {
        fun addPlain(name: String, value: String) {
            if (value.isNotBlank()) {
                pairs += name to EntryValue.Plain(value)
            }
        }

        fun addProtected(name: String, value: String) {
            if (value.isNotBlank()) {
                pairs += name to EntryValue.Encrypted(EncryptedValue.fromString(value))
            }
        }

        addPlain(FIELD_APP_PACKAGE_NAME, entry.appPackageName)
        addPlain(FIELD_APP_NAME, entry.appName)
        addPlain(FIELD_EMAIL, entry.email)
        addPlain(FIELD_PHONE, entry.phone)
        addPlain(FIELD_ADDRESS_LINE, entry.addressLine)
        addPlain(FIELD_CITY, entry.city)
        addPlain(FIELD_STATE, entry.state)
        addPlain(FIELD_POSTAL_CODE, entry.zipCode)
        addPlain(FIELD_COUNTRY, entry.country)
        addProtected(FIELD_CARD_NUMBER, entry.creditCardNumber)
        addPlain(FIELD_CARD_HOLDER, entry.creditCardHolder)
        addPlain(FIELD_CARD_EXPIRY, entry.creditCardExpiry)
        addProtected(FIELD_CARD_CVV, entry.creditCardCVV)

        if (entry.loginType.equals("SSO", ignoreCase = true)) {
            pairs += FIELD_MONICA_LOGIN_TYPE to EntryValue.Plain("SSO")
            addPlain(FIELD_SSO_PROVIDER, entry.ssoProvider)
            entry.ssoRefEntryId?.let { addPlain(FIELD_MONICA_SSO_REF_ENTRY_ID, it.toString()) }
        }
    }

    private fun appendKeePassCustomFields(
        pairs: MutableList<Pair<String, EntryValue>>,
        customFields: List<KeePassCustomFieldData>
    ) {
        val usedKeys = pairs.mapTo(mutableSetOf()) { it.first.trim().lowercase(Locale.ROOT) }
        customFields
            .asSequence()
            .sortedWith(compareBy<KeePassCustomFieldData> { it.sortOrder }.thenBy { it.title })
            .forEach { field ->
                val key = field.title.trim()
                if (key.isBlank() || field.value.isBlank() || key.startsWith("_etm_")) return@forEach
                if (!usedKeys.add(key.lowercase(Locale.ROOT))) return@forEach
                val value = if (field.isProtected) {
                    EntryValue.Encrypted(EncryptedValue.fromString(field.value))
                } else {
                    EntryValue.Plain(field.value)
                }
                pairs += key to value
            }
    }

    private fun buildPasskeyFields(
        passkey: PasskeyEntry,
        existingEntry: Entry? = null
    ): EntryFields {
        val readableTitle = passkey.rpName.ifBlank { passkey.rpId }.ifBlank { "Passkey" }
        val portablePasskey = passkey.copy(
            privateKeyAlias = PasskeyPrivateKeyStore.resolve(context, passkey.privateKeyAlias).orEmpty()
        )
        val payload = KeePassPasskeySyncCodec.encode(portablePasskey)
        val pairs = mutableListOf<Pair<String, EntryValue>>(
            "Title" to EntryValue.Plain("$readableTitle [Passkey]"),
            "UserName" to EntryValue.Plain(passkey.userName.ifBlank { passkey.userDisplayName }),
            "Password" to EntryValue.Encrypted(EncryptedValue.fromString("")),
            "URL" to EntryValue.Plain(
                when {
                    passkey.rpId.isBlank() -> ""
                    "://" in passkey.rpId -> passkey.rpId
                    else -> "https://${passkey.rpId}"
                }
            ),
            "Notes" to EntryValue.Plain(passkey.notes),
            FIELD_MONICA_PASSKEY_CREDENTIAL_ID to EntryValue.Plain(passkey.credentialId),
            FIELD_MONICA_PASSKEY_MODE to EntryValue.Plain(PasskeyEntry.MODE_KEEPASS_COMPAT),
            FIELD_MONICA_PASSKEY_DATA to EntryValue.Encrypted(EncryptedValue.fromString(payload))
        )
        pairs += KeePassDxPasskeyCodec.buildCustomFieldPairs(
            passkey = passkey,
            existingFieldValue = { fieldName ->
                existingEntry?.let { getFieldValue(it, fieldName) }.orEmpty()
            },
            exportPrivateKeyPem = { keyMaterial ->
                PasskeyPrivateKeyStore.exportPem(context, keyMaterial)
            }
        )
        return EntryFields.of(*pairs.toTypedArray())
    }

    private fun buildUpdatedPasskeyEntry(
        existingEntry: Entry,
        passkey: PasskeyEntry
    ): Entry {
        return buildPasskeyEntryFieldPatch(passkey, existingEntry).applyTo(existingEntry)
    }

    private fun buildPasskeyEntryFieldPatch(
        passkey: PasskeyEntry,
        existingEntry: Entry? = null
    ): KeePassEntryFieldPatch {
        val replacementFields = buildPasskeyFields(passkey, existingEntry)
        return KeePassEntryFieldPatch.fromEntryFields(
            replacementFields = replacementFields,
            removeManagedField = KeePassFieldRegistry::isPasskeyEntryOverlayField,
            removeFieldNames = replacementFields.keys
        )
    }

    private fun buildSecureItemEntry(item: SecureItem): Entry {
        return Entry(
            uuid = parseUuid(item.keepassEntryUuid) ?: UUID.randomUUID(),
            fields = buildSecureItemFields(item)
        )
    }

    private fun buildUpdatedSecureItemEntry(
        existingEntry: Entry,
        item: SecureItem
    ): Entry {
        return buildSecureItemEntryFieldPatch(item).applyTo(existingEntry)
    }

    private fun buildSecureItemEntryFieldPatch(item: SecureItem): KeePassEntryFieldPatch {
        val replacementFields = buildSecureItemFields(item)
        val removeManagedField = if (item.itemType == ItemType.TOTP) {
            { name: String ->
                KeePassFieldRegistry.isSecureItemOverlayField(name) ||
                    KeePassFieldRegistry.isKeePassTotpField(name)
            }
        } else {
            KeePassFieldRegistry::isSecureItemOverlayField
        }
        return KeePassEntryFieldPatch.fromEntryFields(
            replacementFields = replacementFields,
            removeManagedField = removeManagedField,
            removeFieldNames = replacementFields.keys
        )
    }

    private fun buildSecureItemFields(item: SecureItem): EntryFields {
        val monicaId = if (item.id > 0) item.id.toString() else ""
        val portableItemData = portableSecureItemDataForKeePass(item)
        val noteForExternal = if (item.itemType == ItemType.NOTE) {
            val decoded = NoteContentCodec.decodeFromItem(item)
            NoteContentCodec.toExternalReadableContent(decoded.content)
        } else {
            item.notes
        }
        val pairs = mutableListOf<Pair<String, EntryValue>>(
            "Title" to EntryValue.Plain(item.title),
            "UserName" to EntryValue.Plain(""),
            "Password" to EntryValue.Encrypted(EncryptedValue.fromString("")),
            "URL" to EntryValue.Plain(""),
            "Notes" to EntryValue.Plain(noteForExternal),
            FIELD_MONICA_ITEM_TYPE to EntryValue.Plain(item.itemType.name),
            FIELD_MONICA_IMAGE_PATHS to EntryValue.Plain(item.imagePaths),
            FIELD_MONICA_IS_FAVORITE to EntryValue.Plain(item.isFavorite.toString())
        )
        if (item.itemType == ItemType.BANK_CARD) {
            CardWalletDataCodec.parseBankCardData(portableItemData)?.let { cardData ->
                appendBankCardFields(pairs, cardData)
            }
        } else {
            pairs += FIELD_MONICA_ITEM_DATA to EntryValue.Encrypted(EncryptedValue.fromString(portableItemData))
            if (item.itemType == ItemType.TOTP) {
                appendKeePassTotpFields(pairs, item, portableItemData)
            }
        }
        if (monicaId.isNotEmpty()) {
            pairs.add(FIELD_MONICA_ITEM_ID to EntryValue.Plain(monicaId))
        }
        return EntryFields.of(*pairs.toTypedArray())
    }

    private fun appendKeePassTotpFields(
        pairs: MutableList<Pair<String, EntryValue>>,
        item: SecureItem,
        portableItemData: String
    ) {
        val totpData = TotpDataResolver.parseStoredItemData(
            itemData = portableItemData,
            fallbackIssuer = item.title,
            fallbackAccountName = item.title
        ) ?: return
        val fields = KeePassTotpCodec.toKeePassFields(totpData, item.title)
        fields.forEach { (name, value) ->
            val entryValue = when (name) {
                KeePassTotpCodec.FIELD_OTP,
                KeePassTotpCodec.FIELD_TOTP_SEED -> EntryValue.Encrypted(EncryptedValue.fromString(value))
                else -> EntryValue.Plain(value)
            }
            pairs += name to entryValue
        }
    }

    private fun appendBankCardFields(
        pairs: MutableList<Pair<String, EntryValue>>,
        cardData: BankCardData
    ) {
        fun addPlain(name: String, value: String) {
            if (value.isNotBlank()) {
                pairs += name to EntryValue.Plain(value)
            }
        }

        fun addProtected(name: String, value: String) {
            if (value.isNotBlank()) {
                pairs += name to EntryValue.Encrypted(EncryptedValue.fromString(value))
            }
        }

        val billingAddressDisplay = CardWalletDataCodec.parseBillingAddress(cardData.billingAddress)
            .formatForDisplay()
            .ifBlank { cardData.billingAddress }

        addProtected(FIELD_CARD_NUMBER, cardData.cardNumber)
        addPlain(FIELD_CARD_HOLDER, cardData.cardholderName)
        addPlain("Expiry Month", cardData.expiryMonth)
        addPlain("Expiry Year", cardData.expiryYear)
        addProtected(FIELD_CARD_CVV, cardData.cvv)
        addPlain(FIELD_BANK_NAME, cardData.bankName)
        addPlain(FIELD_CARD_TYPE, cardData.cardType.name)
        addPlain(FIELD_BILLING_ADDRESS, billingAddressDisplay)
        addPlain(FIELD_BRAND, cardData.brand)
        addPlain(FIELD_NICKNAME, cardData.nickname)
        addPlain(FIELD_VALID_FROM_MONTH, cardData.validFromMonth)
        addPlain(FIELD_VALID_FROM_YEAR, cardData.validFromYear)
        addProtected(FIELD_PIN, cardData.pin)
        addProtected(FIELD_IBAN, cardData.iban)
        addProtected(FIELD_SWIFT_BIC, cardData.swiftBic)
        addProtected(FIELD_ROUTING_NUMBER, cardData.routingNumber)
        addProtected(FIELD_ACCOUNT_NUMBER, cardData.accountNumber)
        addPlain(FIELD_BRANCH_CODE, cardData.branchCode)
        addPlain(FIELD_CURRENCY, cardData.currency)
        addPlain(FIELD_CUSTOMER_SERVICE_PHONE, cardData.customerServicePhone)
        appendSecureItemCustomFields(pairs, cardData.customFields)
    }

    private fun appendSecureItemCustomFields(
        pairs: MutableList<Pair<String, EntryValue>>,
        customFields: List<SecureCustomField>
    ) {
        val usedKeys = pairs.mapTo(mutableSetOf()) { it.first.trim().lowercase(Locale.ROOT) }
        customFields
            .asSequence()
            .filter { it.isValid() }
            .forEach { field ->
                val key = field.label.trim()
                if (key.isBlank() || key.startsWith("_etm_")) return@forEach
                if (!usedKeys.add(key.lowercase(Locale.ROOT))) return@forEach
                val value = if (field.type == SecureCustomFieldType.HIDDEN) {
                    EntryValue.Encrypted(EncryptedValue.fromString(field.value))
                } else {
                    EntryValue.Plain(field.value)
                }
                pairs += key to value
            }
    }

    private fun portableSecureItemDataForKeePass(item: SecureItem): String {
        val itemData = item.itemData
        if (itemData.isBlank() || !securityManager.looksLikeMonicaCiphertext(itemData)) {
            return itemData
        }
        return runCatching { securityManager.decryptData(itemData) }.getOrElse { error ->
            throw IllegalStateException(
                "Cannot write encrypted secure item data to KeePass for itemId=${item.id}",
                error
            )
        }
    }

    private fun updateEntry(
        keePassDatabase: KeePassDatabase,
        databaseId: Long?,
        entry: PasswordEntry,
        plainPassword: String,
        customFields: List<KeePassCustomFieldData> = emptyList()
    ): KeePassEntryUpdateResult {
        val resolutionContext = buildResolutionContext(keePassDatabase)
        val (entryContexts, _) = collectEntryContexts(keePassDatabase)
        var firstMatchedContext: EntryTraversalContext? = null
        val matcher: (Entry) -> Boolean = { existing ->
            val matches = matchesPasswordEntry(existing, entry, resolutionContext)
            if (matches && firstMatchedContext == null) {
                firstMatchedContext = entryContexts.firstOrNull { it.entry.uuid == existing.uuid }
            }
            matches
        }
        val rootGroup = keePassDatabase.content.group
        val removeResult = removeEntryInGroup(rootGroup, matcher)
        val removedCount = removeResult.second
        if (removedCount <= 0) {
            return KeePassEntryUpdateResult(
                database = keePassDatabase,
                changed = false,
                changeSets = emptyList()
            )
        }

        val matchedContext = firstMatchedContext
        val fieldPatch = buildPasswordEntryFieldPatch(entry, plainPassword, customFields)
        val newEntry = matchedContext?.entry?.let { existing ->
            applyPasswordEntryPresentation(fieldPatch.applyTo(existing), entry)
        } ?: buildEntry(entry, plainPassword, customFields)
        val updatedRoot = addEntryToGroupPath(
            rootGroup = removeResult.first,
            groupPath = entry.keepassGroupPath,
            entry = newEntry
        )
        val updatedDatabase = keePassDatabase.modifyParentGroup { updatedRoot }
        return KeePassEntryUpdateResult(
            database = updatedDatabase,
            changed = true,
            changeSets = buildFieldUpdateChangeSets(
                database = keePassDatabase,
                databaseId = databaseId,
                target = KeePassChangeTarget.PASSWORD,
                matchedContext = matchedContext,
                targetGroupPath = entry.keepassGroupPath,
                fieldPatch = fieldPatch.toChangePatch(
                    managedScope = KeePassManagedFieldScope.PASSWORD,
                    baseEntry = matchedContext?.entry
                ),
                includeMoveChange = true
            )
        )
    }

    private fun updateSecureItemInternal(
        keePassDatabase: KeePassDatabase,
        databaseId: Long?,
        item: SecureItem
    ): KeePassEntryUpdateResult {
        val resolutionContext = buildResolutionContext(keePassDatabase)
        val (entryContexts, _) = collectEntryContexts(keePassDatabase)
        var matchedContext: EntryTraversalContext? = null
        val fieldPatch = buildSecureItemEntryFieldPatch(item)
        val matcher: (Entry) -> Boolean = { existing ->
            val matches = matchesSecureItemEntry(existing, item, resolutionContext)
            if (matches && matchedContext == null) {
                matchedContext = entryContexts.firstOrNull { it.entry.uuid == existing.uuid }
            }
            matches
        }
        val updater: (Entry) -> Entry = { existing ->
            fieldPatch.applyTo(existing)
        }
        val result = updateEntryInGroup(keePassDatabase.content.group, matcher, updater)
        val updatedDatabase = if (result.second) {
            keePassDatabase.modifyParentGroup { result.first }
        } else {
            keePassDatabase
        }
        return KeePassEntryUpdateResult(
            database = updatedDatabase,
            changed = result.second,
            changeSets = if (result.second) {
                buildFieldUpdateChangeSets(
                    database = keePassDatabase,
                    databaseId = databaseId,
                    target = KeePassChangeTarget.SECURE_ITEM,
                    matchedContext = matchedContext,
                    targetGroupPath = item.keepassGroupPath,
                    fieldPatch = fieldPatch.toChangePatch(
                        managedScope = KeePassManagedFieldScope.SECURE_ITEM,
                        baseEntry = matchedContext?.entry
                    ),
                    includeMoveChange = false
                )
            } else {
                emptyList()
            }
        )
    }

    private fun updatePasskeyInternal(
        keePassDatabase: KeePassDatabase,
        databaseId: Long?,
        passkey: PasskeyEntry
    ): KeePassEntryUpdateResult {
        val resolutionContext = buildResolutionContext(keePassDatabase)
        val (entryContexts, _) = collectEntryContexts(keePassDatabase)
        var matchedContext: EntryTraversalContext? = null
        val matcher: (Entry) -> Boolean = { existing ->
            val matches = matchesPasskeyEntry(existing, passkey, resolutionContext)
            if (matches && matchedContext == null) {
                matchedContext = entryContexts.firstOrNull { it.entry.uuid == existing.uuid }
            }
            matches
        }
        val rootGroup = keePassDatabase.content.group
        val removeResult = removeEntryInGroup(rootGroup, matcher)
        if (removeResult.second <= 0) {
            return KeePassEntryUpdateResult(
                database = keePassDatabase,
                changed = false,
                changeSets = emptyList()
            )
        }
        val existingEntry = matchedContext?.entry
            ?: throw IllegalStateException("Matched KeePass passkey entry context missing")
        val fieldPatch = buildPasskeyEntryFieldPatch(passkey, existingEntry)
        val updatedEntry = fieldPatch.applyTo(existingEntry)
        val updatedRoot = addEntryToGroupPath(
            rootGroup = removeResult.first,
            groupPath = passkey.keepassGroupPath,
            entry = updatedEntry
        )
        val updatedDatabase = keePassDatabase.modifyParentGroup { updatedRoot }
        return KeePassEntryUpdateResult(
            database = updatedDatabase,
            changed = true,
            changeSets = buildFieldUpdateChangeSets(
                database = keePassDatabase,
                databaseId = databaseId,
                target = KeePassChangeTarget.PASSKEY,
                matchedContext = matchedContext,
                targetGroupPath = passkey.keepassGroupPath,
                fieldPatch = fieldPatch.toChangePatch(
                    managedScope = KeePassManagedFieldScope.PASSKEY,
                    baseEntry = matchedContext?.entry
                ),
                includeMoveChange = true
            )
        )
    }

    private fun upsertPasskey(
        keePassDatabase: KeePassDatabase,
        passkey: PasskeyEntry
    ): KeePassDatabase {
        val updateResult = updatePasskeyInternal(
            keePassDatabase = keePassDatabase,
            databaseId = null,
            passkey = passkey
        )
        if (updateResult.changed) return updateResult.database

        val newEntry = buildPasskeyEntry(passkey)
        val updatedRoot = addEntryToGroupPath(
            rootGroup = keePassDatabase.content.group,
            groupPath = passkey.keepassGroupPath,
            entry = newEntry
        )
        return keePassDatabase.modifyParentGroup { updatedRoot }
    }

    private fun buildFieldUpdateChangeSets(
        database: KeePassDatabase,
        databaseId: Long?,
        target: KeePassChangeTarget,
        matchedContext: EntryTraversalContext?,
        targetGroupPath: String?,
        fieldPatch: KeePassFieldChangePatch,
        includeMoveChange: Boolean
    ): List<KeePassChangeSet> {
        if (databaseId == null || databaseId <= 0 || matchedContext == null) {
            return emptyList()
        }
        val baseFingerprint = buildConflictEntrySignature(matchedContext.entry)
        val entryUuid = matchedContext.entry.uuid.toString()
        val sourceGroupUuid = matchedContext.groupUuid?.toString()
        val changes = mutableListOf(
            KeePassChangeSet(
                databaseId = databaseId,
                target = target,
                operation = KeePassChangeOperation.FIELD_PATCH,
                entryUuid = entryUuid,
                baseFingerprint = baseFingerprint,
                baseGroupPath = matchedContext.groupPath,
                baseGroupUuid = sourceGroupUuid,
                fieldPatch = fieldPatch
            )
        )
        if (includeMoveChange && matchedContext.groupUuid != null) {
            val targetPath = targetGroupPath?.takeIf { it.isNotBlank() }
            val targetGroupUuid = if (targetPath == null) {
                database.content.group.uuid
            } else {
                findGroupUuidByPath(
                    group = database.content.group,
                    currentPathKey = null,
                    targetPathKey = targetPath
                )
            }
            if (targetGroupUuid != null && targetGroupUuid != matchedContext.groupUuid) {
                changes += KeePassChangeSet(
                    databaseId = databaseId,
                    target = target,
                    operation = KeePassChangeOperation.MOVE_ENTRY,
                    entryUuid = entryUuid,
                    baseFingerprint = baseFingerprint,
                    baseGroupPath = matchedContext.groupPath,
                    baseGroupUuid = sourceGroupUuid,
                    structurePatch = KeePassStructureChangePatch(
                        sourceGroupPath = matchedContext.groupPath,
                        sourceGroupUuid = sourceGroupUuid,
                        targetGroupPath = targetPath,
                        targetGroupUuid = targetGroupUuid.toString()
                    )
                )
            }
        }
        return changes
    }

    private fun buildCreateEntryChangeSet(
        database: KeePassDatabase,
        databaseId: Long,
        target: KeePassChangeTarget,
        entry: Entry,
        targetGroupPath: String?
    ): KeePassChangeSet? {
        val resolvedTargetPath = targetGroupPath?.takeIf { it.isNotBlank() }
        val targetGroupUuid = if (resolvedTargetPath == null) {
            database.content.group.uuid
        } else {
            findGroupUuidByPath(
                group = database.content.group,
                currentPathKey = null,
                targetPathKey = resolvedTargetPath
            )
        } ?: return null
        return KeePassChangeSet(
            databaseId = databaseId,
            target = target,
            operation = KeePassChangeOperation.CREATE_ENTRY,
            entryUuid = entry.uuid.toString(),
            baseFingerprint = null,
            entryPatch = KeePassEntryCreatePatch(
                targetGroupPath = resolvedTargetPath,
                targetGroupUuid = targetGroupUuid.toString(),
                fields = entry.fields.map { (name, value) ->
                    takagi.ru.monica.keepass.KeePassFieldChange(
                        name = name,
                        value = runCatching { value.content }.getOrDefault(""),
                        protected = value is EntryValue.Encrypted
                    )
                },
                iconName = entry.icon?.name
            )
        )
    }

    private fun removeEntry(
        keePassDatabase: KeePassDatabase,
        entry: PasswordEntry
    ): Pair<KeePassDatabase, Int> {
        val resolutionContext = buildResolutionContext(keePassDatabase)
        val matcher: (Entry) -> Boolean = { existing ->
            matchesPasswordEntry(existing, entry, resolutionContext)
        }
        val result = removeEntryInGroup(keePassDatabase.content.group, matcher)
        val updatedDatabase = if (result.second > 0) {
            keePassDatabase.modifyParentGroup { result.first }
        } else {
            keePassDatabase
        }
        return updatedDatabase to result.second
    }

    private fun removeSecureItem(
        keePassDatabase: KeePassDatabase,
        item: SecureItem
    ): Pair<KeePassDatabase, Int> {
        val resolutionContext = buildResolutionContext(keePassDatabase)
        val matcher: (Entry) -> Boolean = { existing ->
            matchesSecureItemEntry(existing, item, resolutionContext)
        }
        val result = removeEntryInGroup(keePassDatabase.content.group, matcher)
        val updatedDatabase = if (result.second > 0) {
            keePassDatabase.modifyParentGroup { result.first }
        } else {
            keePassDatabase
        }
        return updatedDatabase to result.second
    }

    private fun updateEntryInGroup(
        group: Group,
        matcher: (Entry) -> Boolean,
        updater: (Entry) -> Entry
    ): Pair<Group, Boolean> {
        var updated = false
        val newEntries = group.entries.map { entry ->
            if (!updated && matcher(entry)) {
                updated = true
                updater(entry)
            } else {
                entry
            }
        }
        val newGroups = group.groups.map { sub ->
            val result = updateEntryInGroup(sub, matcher, updater)
            if (result.second) {
                updated = true
            }
            result.first
        }
        return group.copy(entries = newEntries, groups = newGroups) to updated
    }

    private fun removeEntryInGroup(
        group: Group,
        matcher: (Entry) -> Boolean
    ): Pair<Group, Int> {
        val filteredEntries = group.entries.filterNot { matcher(it) }
        var removedCount = group.entries.size - filteredEntries.size
        val newGroups = group.groups.map { sub ->
            val result = removeEntryInGroup(sub, matcher)
            removedCount += result.second
            result.first
        }
        return group.copy(entries = filteredEntries, groups = newGroups) to removedCount
    }

    private fun removeAndCollectEntriesInGroup(
        group: Group,
        matcher: (Entry) -> Boolean,
        inRecycleBin: Boolean,
        recycleBinUuid: UUID?,
        removedEntries: MutableList<RemovedEntryContext>
    ): Pair<Group, Int> {
        val currentInRecycle = inRecycleBin || (recycleBinUuid != null && group.uuid == recycleBinUuid)
        var removedCount = 0

        val keptEntries = mutableListOf<Entry>()
        group.entries.forEach { entry ->
            val shouldRemove = matcher(entry) && !currentInRecycle
            if (shouldRemove) {
                removedEntries += RemovedEntryContext(
                    entry = entry,
                    previousParentUuid = group.uuid
                )
                removedCount++
            } else {
                keptEntries += entry
            }
        }

        val newGroups = group.groups.map { sub ->
            val result = removeAndCollectEntriesInGroup(
                group = sub,
                matcher = matcher,
                inRecycleBin = currentInRecycle,
                recycleBinUuid = recycleBinUuid,
                removedEntries = removedEntries
            )
            removedCount += result.second
            result.first
        }

        return group.copy(entries = keptEntries, groups = newGroups) to removedCount
    }

    private data class KeePassRecycleRestoreResult(
        val database: KeePassDatabase,
        val targetsByRoomId: Map<Long, KeePassRestoreTarget>,
        val changeSets: List<KeePassChangeSet>
    )

    private data class KeePassStructureApplyResult(
        val database: KeePassDatabase,
        val changedCount: Int,
        val changeSets: List<KeePassChangeSet>
    )

    private data class KeePassEntryUpdateResult(
        val database: KeePassDatabase,
        val changed: Boolean,
        val changeSets: List<KeePassChangeSet>
    )

    private fun <T> applyEntryStructureChanges(
        loaded: LoadedDatabase,
        targets: List<T>,
        targetType: KeePassChangeTarget,
        operation: KeePassChangeOperation,
        matcher: (Entry, T, KeePassEntryResolutionContext) -> Boolean
    ): KeePassStructureApplyResult {
        require(
            operation == KeePassChangeOperation.MOVE_TO_RECYCLE_BIN ||
                operation == KeePassChangeOperation.PERMANENT_DELETE
        ) {
            "Unsupported KeePass structure operation for entry apply: $operation"
        }
        if (targets.isEmpty()) {
            return KeePassStructureApplyResult(
                database = loaded.keePassDatabase,
                changedCount = 0,
                changeSets = emptyList()
            )
        }

        val recycleBinUuid = if (operation == KeePassChangeOperation.MOVE_TO_RECYCLE_BIN) {
            resolveRecycleBinUuid(loaded.keePassDatabase.content.meta)
                ?: throw IllegalStateException("KeePass recycle bin unavailable")
        } else {
            resolveRecycleBinUuid(loaded.keePassDatabase.content.meta)
        }
        val recycleBinPath = recycleBinUuid?.let {
            findGroupPathByUuid(
                group = loaded.keePassDatabase.content.group,
                currentPathKey = null,
                targetUuid = it
            )
        }
        if (operation == KeePassChangeOperation.MOVE_TO_RECYCLE_BIN && recycleBinPath == null) {
            throw IllegalStateException("KeePass recycle bin path unavailable")
        }

        val (entryContexts, hasRecycleBinMeta) = collectEntryContexts(loaded.keePassDatabase)
        val resolutionContext = KeePassFieldReferenceResolver.buildContext(entryContexts.map { it.entry })
        val applier = KeePassChangeSetApplier()
        var currentDatabase = loaded.keePassDatabase
        var changedCount = 0
        val appliedChangeSets = mutableListOf<KeePassChangeSet>()
        val usedEntryUuids = mutableSetOf<UUID>()

        targets.forEach { target ->
            val context = entryContexts.firstOrNull { candidate ->
                candidate.entry.uuid !in usedEntryUuids &&
                    candidate.matchesStructureOperationScope(
                        operation = operation,
                        hasRecycleBinMeta = hasRecycleBinMeta
                    ) &&
                    matcher(candidate.entry, target, resolutionContext)
            } ?: return@forEach
            val parentUuid = context.groupUuid
                ?: throw IllegalStateException("KeePass entry parent group unavailable for ${context.entry.uuid}")

            val structurePatch = when (operation) {
                KeePassChangeOperation.MOVE_TO_RECYCLE_BIN -> KeePassStructureChangePatch(
                    sourceGroupPath = context.groupPath,
                    sourceGroupUuid = parentUuid.toString(),
                    targetGroupPath = recycleBinPath,
                    targetGroupUuid = recycleBinUuid!!.toString(),
                    recycleBinGroupUuid = recycleBinUuid.toString(),
                    previousParentGroupUuid = parentUuid.toString()
                )
                KeePassChangeOperation.PERMANENT_DELETE -> KeePassStructureChangePatch(
                    sourceGroupPath = context.groupPath,
                    sourceGroupUuid = parentUuid.toString(),
                    recycleBinGroupUuid = recycleBinUuid?.toString(),
                    previousParentGroupUuid = context.entry.previousParentGroup?.toString()
                )
                else -> error("Unsupported KeePass structure operation: $operation")
            }

            val changeSet = KeePassChangeSet(
                databaseId = loaded.database.id,
                target = targetType,
                operation = operation,
                entryUuid = context.entry.uuid.toString(),
                baseFingerprint = buildConflictEntrySignature(context.entry),
                baseGroupPath = context.groupPath,
                baseGroupUuid = parentUuid.toString(),
                structurePatch = structurePatch
            )

            currentDatabase = applier.apply(currentDatabase, changeSet).updatedDatabase
            usedEntryUuids += context.entry.uuid
            appliedChangeSets += changeSet
            changedCount++
        }

        return KeePassStructureApplyResult(
            database = currentDatabase,
            changedCount = changedCount,
            changeSets = appliedChangeSets
        )
    }

    private fun EntryTraversalContext.matchesStructureOperationScope(
        operation: KeePassChangeOperation,
        hasRecycleBinMeta: Boolean
    ): Boolean {
        return when (operation) {
            KeePassChangeOperation.MOVE_TO_RECYCLE_BIN -> !isInKeePassRecycleBin(hasRecycleBinMeta)
            KeePassChangeOperation.PERMANENT_DELETE -> true
            else -> false
        }
    }

    private fun <T> restoreEntriesFromRecycleBin(
        loaded: LoadedDatabase,
        targets: List<T>,
        targetType: KeePassChangeTarget,
        matcher: (Entry, T, KeePassEntryResolutionContext) -> Boolean,
        roomId: (T) -> Long,
        preferredGroupPath: (T) -> String?,
        preferredGroupUuid: (T) -> String?
    ): KeePassRecycleRestoreResult {
        if (targets.isEmpty()) {
            return KeePassRecycleRestoreResult(
                database = loaded.keePassDatabase,
                targetsByRoomId = emptyMap(),
                changeSets = emptyList()
            )
        }

        val recycleBinUuid = resolveRecycleBinUuid(loaded.keePassDatabase.content.meta)
            ?: throw IllegalStateException("KeePass recycle bin unavailable")
        val (entryContexts, hasRecycleBinMeta) = collectEntryContexts(loaded.keePassDatabase)
        val resolutionContext = KeePassFieldReferenceResolver.buildContext(entryContexts.map { it.entry })
        val groupContextIndex = buildGroupTraversalContextIndex(loaded.keePassDatabase)
        val applier = KeePassChangeSetApplier()
        var currentDatabase = loaded.keePassDatabase
        val restoredTargets = linkedMapOf<Long, KeePassRestoreTarget>()
        val appliedChangeSets = mutableListOf<KeePassChangeSet>()
        val usedEntryUuids = mutableSetOf<UUID>()

        targets.forEach { target ->
            val targetRoomId = roomId(target)
            val context = entryContexts.firstOrNull { candidate ->
                candidate.entry.uuid !in usedEntryUuids &&
                    candidate.isInKeePassRecycleBin(hasRecycleBinMeta) &&
                    matcher(candidate.entry, target, resolutionContext)
            } ?: return@forEach

            val restoreTarget = resolveRestoreTargetFromEntryContext(
                entryContext = context,
                hasRecycleBinMeta = hasRecycleBinMeta,
                groupContextIndex = groupContextIndex
            ).withFallback(
                preferredGroupPath = preferredGroupPath(target),
                preferredGroupUuid = preferredGroupUuid(target),
                groupContextIndex = groupContextIndex
            )
            val previousParentUuid = context.entry.previousParentGroup
                ?: parseUuid(restoreTarget.groupUuid)
                ?: throw IllegalStateException("KeePass recycle bin restore target unavailable for ${context.entry.uuid}")

            val changeSet = KeePassChangeSet(
                databaseId = loaded.database.id,
                target = targetType,
                operation = KeePassChangeOperation.RESTORE_FROM_RECYCLE_BIN,
                entryUuid = context.entry.uuid.toString(),
                baseFingerprint = buildConflictEntrySignature(context.entry),
                baseGroupPath = context.groupPath,
                baseGroupUuid = context.groupUuid?.toString(),
                structurePatch = KeePassStructureChangePatch(
                    sourceGroupPath = context.groupPath,
                    sourceGroupUuid = context.groupUuid?.toString(),
                    targetGroupPath = restoreTarget.groupPath,
                    targetGroupUuid = restoreTarget.groupUuid ?: previousParentUuid.toString(),
                    recycleBinGroupUuid = recycleBinUuid.toString(),
                    previousParentGroupUuid = previousParentUuid.toString()
                )
            )

            currentDatabase = applier.apply(currentDatabase, changeSet).updatedDatabase
            usedEntryUuids += context.entry.uuid
            appliedChangeSets += changeSet
            restoredTargets[targetRoomId] = restoreTarget.copy(
                groupUuid = restoreTarget.groupUuid ?: previousParentUuid.toString()
            )
        }

        return KeePassRecycleRestoreResult(
            database = currentDatabase,
            targetsByRoomId = restoredTargets,
            changeSets = appliedChangeSets
        )
    }

    private fun EntryTraversalContext.isInKeePassRecycleBin(hasRecycleBinMeta: Boolean): Boolean {
        return if (hasRecycleBinMeta) {
            isInRecycleBinByMeta
        } else {
            isLikelyRecycleBinPath(groupPath)
        }
    }

    private fun KeePassRestoreTarget.withFallback(
        preferredGroupPath: String?,
        preferredGroupUuid: String?,
        groupContextIndex: Map<UUID, GroupTraversalContext>
    ): KeePassRestoreTarget {
        if (!groupUuid.isNullOrBlank() || !groupPath.isNullOrBlank()) {
            return this
        }
        val preferredUuid = parseUuid(preferredGroupUuid)
        if (preferredUuid != null) {
            val preferredContext = groupContextIndex[preferredUuid]
            if (preferredContext != null && !preferredContext.isInRecycleBinByMeta) {
                return KeePassRestoreTarget(
                    groupPath = preferredContext.pathKey,
                    groupUuid = preferredContext.groupUuid.toString()
                )
            }
        }
        if (!preferredGroupPath.isNullOrBlank() && !isLikelyRecycleBinPath(preferredGroupPath)) {
            return KeePassRestoreTarget(
                groupPath = preferredGroupPath,
                groupUuid = preferredGroupUuid
            )
        }
        return this
    }

    private fun addEntryToGroupPath(
        rootGroup: Group,
        groupPath: String?,
        entry: Entry
    ): Group {
        val segments = decodeKeePassPathSegments(groupPath)
        if (segments.isEmpty()) {
            return rootGroup.copy(entries = rootGroup.entries + entry)
        }
        return addEntryToGroupPathSegments(rootGroup, segments, entry)
    }

    private fun addEntryToGroupPathSegments(
        group: Group,
        segments: List<String>,
        entry: Entry
    ): Group {
        if (segments.isEmpty()) {
            return group.copy(entries = group.entries + entry)
        }

        val childName = segments.first()
        val childIndex = group.groups.indexOfFirst { it.name == childName }
        val childGroup = if (childIndex >= 0) {
            group.groups[childIndex]
        } else {
            Group(uuid = UUID.randomUUID(), name = childName)
        }

        val updatedChild = addEntryToGroupPathSegments(
            group = childGroup,
            segments = segments.drop(1),
            entry = entry
        )

        val updatedGroups = group.groups.toMutableList()
        if (childIndex >= 0) {
            updatedGroups[childIndex] = updatedChild
        } else {
            updatedGroups.add(updatedChild)
        }
        return group.copy(groups = updatedGroups)
    }

    private fun matchByKey(
        entry: Entry,
        target: PasswordEntry,
        resolutionContext: KeePassEntryResolutionContext? = null
    ): Boolean {
        val title = getStandardTitle(entry, resolutionContext)
        val username = getStandardUsername(entry, resolutionContext)
        val url = getStandardUrl(entry, resolutionContext)
        return title.equals(target.title, true) &&
            username.equals(target.username, true) &&
            url.equals(target.website, true)
    }

    private fun matchesPasswordEntry(
        entry: Entry,
        target: PasswordEntry,
        resolutionContext: KeePassEntryResolutionContext? = null
    ): Boolean {
        val targetUuid = parseUuid(target.keepassEntryUuid)
        if (targetUuid != null && entry.uuid == targetUuid) {
            return true
        }
        val monicaId = getFieldValue(entry, FIELD_MONICA_LOCAL_ID, resolutionContext).toLongOrNull()
        if (monicaId != null && target.id > 0 && monicaId == target.id) {
            return true
        }
        return matchByKey(entry, target, resolutionContext)
    }

    private fun matchSecureItemByKey(
        entry: Entry,
        target: SecureItem,
        resolutionContext: KeePassEntryResolutionContext? = null
    ): Boolean {
        val title = getFieldValue(entry, "Title", resolutionContext)
        val itemType = getFieldValue(entry, FIELD_MONICA_ITEM_TYPE, resolutionContext)
        return title.equals(target.title, true) &&
            itemType.equals(target.itemType.name, true)
    }

    private fun matchesSecureItemEntry(
        entry: Entry,
        target: SecureItem,
        resolutionContext: KeePassEntryResolutionContext? = null
    ): Boolean {
        val targetUuid = parseUuid(target.keepassEntryUuid)
        if (targetUuid != null && entry.uuid == targetUuid) {
            return true
        }
        val monicaId = getFieldValue(entry, FIELD_MONICA_ITEM_ID, resolutionContext).toLongOrNull()
        if (monicaId != null && target.id > 0 && monicaId == target.id) {
            return true
        }
        return matchSecureItemByKey(entry, target, resolutionContext)
    }

    private fun matchesPasskeyEntry(
        entry: Entry,
        target: PasskeyEntry,
        resolutionContext: KeePassEntryResolutionContext? = null
    ): Boolean {
        val targetCredentialId = PasskeyCredentialIdCodec.normalize(target.credentialId) ?: target.credentialId
        val credentialId = getFieldValue(entry, FIELD_MONICA_PASSKEY_CREDENTIAL_ID, resolutionContext)
        if (credentialId.isNotBlank()) {
            val normalized = PasskeyCredentialIdCodec.normalize(credentialId) ?: credentialId
            if (normalized == targetCredentialId) {
                return true
            }
        }

        val keepassDxCredentialId = getFieldValue(entry, KeePassDxPasskeyCodec.FIELD_CREDENTIAL_ID, resolutionContext)
        if (keepassDxCredentialId.isNotBlank()) {
            val normalized = PasskeyCredentialIdCodec.normalize(keepassDxCredentialId) ?: keepassDxCredentialId
            if (normalized == targetCredentialId) {
                return true
            }
        }

        val payload = getFieldValue(entry, FIELD_MONICA_PASSKEY_DATA, resolutionContext)
        val decoded = KeePassPasskeySyncCodec.decode(
            raw = payload,
            databaseId = target.keepassDatabaseId ?: -1L,
            groupPath = target.keepassGroupPath,
            groupUuid = null
        )
        val decodedCredentialId = PasskeyCredentialIdCodec.normalize(decoded?.credentialId) ?: decoded?.credentialId
        return decodedCredentialId == targetCredentialId
    }

    private fun entryToData(
        entry: Entry,
        groupPath: String?,
        groupUuid: UUID?,
        isInRecycleBinByMeta: Boolean,
        hasRecycleBinMeta: Boolean,
        resolutionContext: KeePassEntryResolutionContext? = null
    ): KeePassEntryData? = analyzePasswordEntry(
        entry = entry,
        groupPath = groupPath,
        groupUuid = groupUuid,
        isInRecycleBinByMeta = isInRecycleBinByMeta,
        hasRecycleBinMeta = hasRecycleBinMeta,
        resolutionContext = resolutionContext
    ).data

    private fun analyzePasswordEntry(
        entry: Entry,
        groupPath: String?,
        groupUuid: UUID?,
        isInRecycleBinByMeta: Boolean,
        hasRecycleBinMeta: Boolean,
        resolutionContext: KeePassEntryResolutionContext? = null
    ): KeePassPasswordEntryAnalysis {
        fun result(
            data: KeePassEntryData? = null,
            skipReason: KeePassPasswordSkipReason? = null,
            hasPasskeyFields: Boolean,
            title: String,
            username: String,
            password: String,
            url: String,
            notes: String
        ) = KeePassPasswordEntryAnalysis(
            data = data,
            skipReason = skipReason,
            hasPasskeyFields = hasPasskeyFields,
            hasTitle = title.isNotBlank(),
            hasUsername = username.isNotBlank(),
            hasPassword = password.isNotBlank(),
            hasUrl = url.isNotBlank(),
            hasNotes = notes.isNotBlank(),
            fieldNames = entry.fields.keys.sorted()
        )

        val title = getStandardTitle(entry, resolutionContext)
        val username = getStandardUsername(entry, resolutionContext)
        val password = resolveEntryPassword(entry, resolutionContext)
        val url = getStandardUrl(entry, resolutionContext)
        val notes = getStandardNotes(entry, resolutionContext)
        val hasPasskeyFields = isPasskeyEntry(entry, resolutionContext)

        // Monica 安全项（TOTP/笔记/卡片等）会写入 MonicaItemType，不应进入密码列表。
        if (getFieldValue(entry, FIELD_MONICA_ITEM_TYPE, resolutionContext).isNotBlank()) {
            return result(
                skipReason = KeePassPasswordSkipReason.MONICA_SECURE_ITEM,
                hasPasskeyFields = hasPasskeyFields,
                title = title,
                username = username,
                password = password,
                url = url,
                notes = notes
            )
        }
        if (
            hasPasskeyFields &&
            username.isBlank() &&
            password.isBlank() &&
            url.isBlank() &&
            notes.isBlank()
        ) {
            return result(
                skipReason = KeePassPasswordSkipReason.PURE_PASSKEY,
                hasPasskeyFields = hasPasskeyFields,
                title = title,
                username = username,
                password = password,
                url = url,
                notes = notes
            )
        }
        if (isEnhancedEntryTemplate(entry, title, username, password, url, notes, resolutionContext)) {
            Log.d(TAG, "Skip KeePassXC template entry from password sync")
            return result(
                skipReason = KeePassPasswordSkipReason.TEMPLATE,
                hasPasskeyFields = hasPasskeyFields,
                title = title,
                username = username,
                password = password,
                url = url,
                notes = notes
            )
        }
        val sshKeyData = SshKeyDataCodec.encode(
            SshKeyData(
                algorithm = getFieldValue(entry, FIELD_MONICA_SSH_ALGORITHM, resolutionContext),
                keySize = getFieldValue(entry, FIELD_MONICA_SSH_KEY_SIZE, resolutionContext).toIntOrNull() ?: 0,
                publicKeyOpenSsh = getFieldValue(entry, FIELD_MONICA_SSH_PUBLIC_KEY, resolutionContext),
                privateKeyOpenSsh = getFieldValue(entry, FIELD_MONICA_SSH_PRIVATE_KEY, resolutionContext),
                fingerprintSha256 = getFieldValue(entry, FIELD_MONICA_SSH_FINGERPRINT, resolutionContext),
                comment = getFieldValue(entry, FIELD_MONICA_SSH_COMMENT, resolutionContext),
                format = getFieldValue(entry, FIELD_MONICA_SSH_FORMAT, resolutionContext)
                    .ifBlank { SshKeyData.FORMAT_OPENSSH }
            )
        )
        if (title.isEmpty() && username.isEmpty() && password.isEmpty() && url.isEmpty() && notes.isEmpty()) {
            return result(
                skipReason = KeePassPasswordSkipReason.EMPTY,
                hasPasskeyFields = hasPasskeyFields,
                title = title,
                username = username,
                password = password,
                url = url,
                notes = notes
            )
        }
        val monicaId = getFieldValue(entry, FIELD_MONICA_LOCAL_ID, resolutionContext).toLongOrNull()
        val inRecycleBin = resolveRecycleBinFlag(
            groupPath = groupPath,
            isInRecycleBinByMeta = isInRecycleBinByMeta,
            hasRecycleBinMeta = hasRecycleBinMeta
        )
        // WIFI 识别：优先使用 Monica 自己写的元数据（包含安全类型/代理/IP 等），
        // 兜底看 keepass2android WLan 模板的 SSID 字段；两者都有的话 Monica 元
        // 数据更完整，优先用它。
        val monicaLoginType = getFieldValue(entry, FIELD_MONICA_LOGIN_TYPE, resolutionContext)
        val monicaWifiJson = getFieldValue(entry, FIELD_MONICA_WIFI_DATA, resolutionContext)
        val ssidField = getFieldValue(entry, FIELD_WIFI_SSID, resolutionContext)
        val appPackageName = getFieldValueIgnoreCase(
            entry,
            resolutionContext,
            FIELD_APP_PACKAGE_NAME,
            "AppPackageName",
            "MonicaAppPackageName",
            "AndroidAppPackageName",
            "PackageName"
        )
        val appName = getFieldValueIgnoreCase(
            entry,
            resolutionContext,
            FIELD_APP_NAME,
            "AppName",
            "MonicaAppName",
            "Application",
            "Application Name"
        )
        val email = getFieldValueIgnoreCase(entry, resolutionContext, FIELD_EMAIL, "E-mail", "Mail")
        val phone = getFieldValueIgnoreCase(entry, resolutionContext, FIELD_PHONE, "Phone Number", "Telephone")
        val addressLine = getFieldValueIgnoreCase(entry, resolutionContext, FIELD_ADDRESS_LINE, "Address Line")
        val city = getFieldValueIgnoreCase(entry, resolutionContext, FIELD_CITY)
        val state = getFieldValueIgnoreCase(entry, resolutionContext, FIELD_STATE, "Province")
        val zipCode = getFieldValueIgnoreCase(entry, resolutionContext, FIELD_POSTAL_CODE, "PostalCode", "Zip Code", "ZipCode")
        val country = getFieldValueIgnoreCase(entry, resolutionContext, FIELD_COUNTRY)
        val creditCardNumber = getFieldValueIgnoreCase(
            entry,
            resolutionContext,
            FIELD_CARD_NUMBER,
            "CardNumber",
            "Credit Card Number",
            "CreditCardNumber"
        )
        val creditCardHolder = getFieldValueIgnoreCase(
            entry,
            resolutionContext,
            FIELD_CARD_HOLDER,
            "CardHolder",
            "Credit Card Holder",
            "CreditCardHolder"
        )
        val creditCardExpiry = getFieldValueIgnoreCase(
            entry,
            resolutionContext,
            FIELD_CARD_EXPIRY,
            "CardExpiry",
            "Expiration Date",
            "Expiry Date"
        )
        val creditCardCVV = getFieldValueIgnoreCase(entry, resolutionContext, FIELD_CARD_CVV, "CardCVV", "CVV", "CVC")
        val ssoProvider = getFieldValueIgnoreCase(
            entry,
            resolutionContext,
            FIELD_SSO_PROVIDER,
            "SsoProvider",
            "MonicaSsoProvider"
        )
        val ssoRefEntryId = getFieldValueIgnoreCase(
            entry,
            resolutionContext,
            FIELD_MONICA_SSO_REF_ENTRY_ID,
            "SsoRefEntryId",
            "MonicaSsoRefId"
        ).toLongOrNull()
        val customFields = extractKeePassCustomFieldsForPasswordEntry(
            entry.fields.map { (key, value) ->
                KeePassRawStringField(
                    key = key,
                    value = getFieldValue(entry, key, resolutionContext),
                    isProtected = value is EntryValue.Encrypted
                )
            }
        )
        val (resolvedLoginType, resolvedWifiJson) = when {
            monicaLoginType.equals("WIFI", ignoreCase = true) && monicaWifiJson.isNotBlank() ->
                "WIFI" to monicaWifiJson
            monicaLoginType.equals("WIFI", ignoreCase = true) -> {
                val wifi = takagi.ru.monica.data.model.WifiData(ssid = ssidField.ifBlank { title })
                "WIFI" to wifi.toJson()
            }
            ssidField.isNotBlank() -> {
                // 外部客户端（kp2a/KeePassXC 手动填 SSID 字段）写入的兼容条目。
                val wifi = takagi.ru.monica.data.model.WifiData(ssid = ssidField)
                "WIFI" to wifi.toJson()
            }
            monicaLoginType.equals("SSO", ignoreCase = true) || ssoProvider.isNotBlank() -> "SSO" to ""
            monicaLoginType.equals("SSH_KEY", ignoreCase = true) -> "SSH_KEY" to ""
            else -> "PASSWORD" to ""
        }
        val data = KeePassEntryData(
            title = title,
            username = username,
            password = password,
            url = url,
            notes = notes,
            appPackageName = appPackageName,
            appName = appName,
            email = email,
            phone = phone,
            addressLine = addressLine,
            city = city,
            state = state,
            zipCode = zipCode,
            country = country,
            creditCardNumber = creditCardNumber,
            creditCardHolder = creditCardHolder,
            creditCardExpiry = creditCardExpiry,
            creditCardCVV = creditCardCVV,
            sshKeyData = sshKeyData,
            monicaLocalId = monicaId,
            entryUuid = entry.uuid.toString(),
            groupPath = groupPath,
            groupUuid = groupUuid?.toString(),
            isInRecycleBin = inRecycleBin,
            loginType = resolvedLoginType,
            ssoProvider = ssoProvider,
            ssoRefEntryId = ssoRefEntryId,
            wifiMetadata = resolvedWifiJson,
            customFields = customFields
        )
        return result(
            data = data,
            hasPasskeyFields = hasPasskeyFields,
            title = title,
            username = username,
            password = password,
            url = url,
            notes = notes
        )
    }

    private fun buildPasswordEntrySkipSample(
        context: EntryTraversalContext,
        analysis: KeePassPasswordEntryAnalysis,
        reason: KeePassPasswordSkipReason
    ): String {
        val uuidSuffix = context.entry.uuid.toString().takeLast(8)
        val fields = analysis.fieldNames
            .take(18)
            .joinToString("|")
            .ifBlank { "-" }
        val moreFields = (analysis.fieldNames.size - 18).takeIf { it > 0 }?.let { "+$it" } ?: ""
        return "reason=$reason, uuidSuffix=$uuidSuffix, group=${context.groupPath ?: "<root>"}, " +
            "hasPasskey=${analysis.hasPasskeyFields}, hasTitle=${analysis.hasTitle}, " +
            "hasUser=${analysis.hasUsername}, hasPassword=${analysis.hasPassword}, " +
            "hasUrl=${analysis.hasUrl}, hasNotes=${analysis.hasNotes}, fields=$fields$moreFields"
    }

    private fun buildPasswordEntryData(
        databaseId: Long,
        databaseName: String,
        entries: List<EntryTraversalContext>,
        hasRecycleBinMeta: Boolean,
        resolutionContext: KeePassEntryResolutionContext
    ): List<KeePassEntryData> {
        val skipCounts = mutableMapOf<KeePassPasswordSkipReason, Int>()
        var passkeyFieldCount = 0
        var importedWithPasskeyFields = 0
        val skippedSamples = mutableListOf<String>()
        val data = entries.mapNotNull { context ->
            val analysis = analyzePasswordEntry(
                entry = context.entry,
                groupPath = context.groupPath,
                groupUuid = context.groupUuid,
                isInRecycleBinByMeta = context.isInRecycleBinByMeta,
                hasRecycleBinMeta = hasRecycleBinMeta,
                resolutionContext = resolutionContext
            )
            if (analysis.hasPasskeyFields) {
                passkeyFieldCount++
            }
            if (analysis.data != null && analysis.hasPasskeyFields) {
                importedWithPasskeyFields++
            }
            analysis.skipReason?.let { reason ->
                skipCounts[reason] = (skipCounts[reason] ?: 0) + 1
                if (skippedSamples.size < 8) {
                    skippedSamples += buildPasswordEntrySkipSample(context, analysis, reason)
                }
            }
            analysis.data
        }
        Log.i(
            TAG,
            "KeePass password sync summary: databaseId=$databaseId, " +
                "total=${entries.size}, imported=${data.size}, passkeyFieldEntries=$passkeyFieldCount, " +
                "importedWithPasskeyFields=$importedWithPasskeyFields, " +
                "skippedSecureItem=${skipCounts[KeePassPasswordSkipReason.MONICA_SECURE_ITEM] ?: 0}, " +
                "skippedPurePasskey=${skipCounts[KeePassPasswordSkipReason.PURE_PASSKEY] ?: 0}, " +
                "skippedTemplate=${skipCounts[KeePassPasswordSkipReason.TEMPLATE] ?: 0}, " +
                "skippedEmpty=${skipCounts[KeePassPasswordSkipReason.EMPTY] ?: 0}"
        )
        if (data.isEmpty() || skipCounts.isNotEmpty()) {
            Log.i(TAG, "KeePass password sync skippedSampleCount=${skippedSamples.size}")
        }
        return data
    }

    private fun entryToSecureItemData(
        entry: Entry,
        databaseId: Long,
        groupPath: String?,
        groupUuid: UUID?,
        isInRecycleBinByMeta: Boolean,
        hasRecycleBinMeta: Boolean,
        allowedTypes: Set<ItemType>?,
        resolutionContext: KeePassEntryResolutionContext? = null
    ): KeePassSecureItemData? {
        if (isPasskeyEntry(entry, resolutionContext)) {
            return null
        }
        val typeRaw = getFieldValue(entry, FIELD_MONICA_ITEM_TYPE, resolutionContext)
        if (typeRaw.isNotBlank()) {
            val itemType = runCatching { ItemType.valueOf(typeRaw) }.getOrNull() ?: return null
            if (allowedTypes != null && itemType !in allowedTypes) return null

            val itemData = getFieldValue(entry, FIELD_MONICA_ITEM_DATA, resolutionContext)
                .ifBlank {
                    buildStructuredSecureItemDataFromEntry(itemType, entry, resolutionContext).orEmpty()
                }
            if (itemData.isBlank()) return null

            val title = getFieldValue(entry, "Title", resolutionContext)
            val notes = getFieldValue(entry, "Notes", resolutionContext)
            val imagePaths = getFieldValue(entry, FIELD_MONICA_IMAGE_PATHS, resolutionContext)
            val isFavorite = getFieldValue(entry, FIELD_MONICA_IS_FAVORITE, resolutionContext).toBoolean()
            val sourceMonicaId = getFieldValue(entry, FIELD_MONICA_ITEM_ID, resolutionContext).toLongOrNull()
            val now = Date()
            val inRecycleBin = resolveRecycleBinFlag(
                groupPath = groupPath,
                isInRecycleBinByMeta = isInRecycleBinByMeta,
                hasRecycleBinMeta = hasRecycleBinMeta
            )

            return KeePassSecureItemData(
                item = SecureItem(
                    id = 0,
                    itemType = itemType,
                    title = title.ifBlank { "Untitled" },
                    notes = notes,
                    isFavorite = isFavorite,
                    createdAt = now,
                    updatedAt = now,
                    itemData = itemData,
                    imagePaths = imagePaths,
                    keepassDatabaseId = databaseId,
                    keepassGroupPath = groupPath,
                    keepassEntryUuid = entry.uuid.toString(),
                    keepassGroupUuid = groupUuid?.toString(),
                    isDeleted = inRecycleBin,
                    deletedAt = if (inRecycleBin) now else null
                ),
                sourceMonicaId = sourceMonicaId,
                isInRecycleBin = inRecycleBin
            )
        }

        val allowTotp = allowedTypes == null || allowedTypes.contains(ItemType.TOTP)
        if (!allowTotp) return null

        val parsedTotp = parseStandardTotpFromEntry(entry, resolutionContext) ?: return null
        val title = getFieldValue(entry, "Title", resolutionContext)
        val notes = getFieldValue(entry, "Notes", resolutionContext)
        val now = Date()
        val inRecycleBin = resolveRecycleBinFlag(
            groupPath = groupPath,
            isInRecycleBinByMeta = isInRecycleBinByMeta,
            hasRecycleBinMeta = hasRecycleBinMeta
        )
        val fallbackTitle = parsedTotp.issuer.ifBlank { parsedTotp.accountName }.ifBlank { "Untitled" }

        return KeePassSecureItemData(
            item = SecureItem(
                id = 0,
                itemType = ItemType.TOTP,
                title = title.ifBlank { fallbackTitle },
                notes = notes,
                isFavorite = false,
                createdAt = now,
                updatedAt = now,
                itemData = Json.encodeToString(TotpData.serializer(), parsedTotp),
                imagePaths = "",
                keepassDatabaseId = databaseId,
                keepassGroupPath = groupPath,
                keepassEntryUuid = entry.uuid.toString(),
                keepassGroupUuid = groupUuid?.toString(),
                isDeleted = inRecycleBin,
                deletedAt = if (inRecycleBin) now else null
            ),
            sourceMonicaId = getFieldValue(entry, FIELD_MONICA_ITEM_ID, resolutionContext).toLongOrNull(),
            isInRecycleBin = inRecycleBin
        )
    }

    private fun buildStructuredSecureItemDataFromEntry(
        itemType: ItemType,
        entry: Entry,
        resolutionContext: KeePassEntryResolutionContext?
    ): String? {
        return when (itemType) {
            ItemType.BANK_CARD -> buildBankCardItemDataFromEntry(entry, resolutionContext)
            else -> null
        }
    }

    private fun buildBankCardItemDataFromEntry(
        entry: Entry,
        resolutionContext: KeePassEntryResolutionContext?
    ): String? {
        val expiryMonth = getFieldValueIgnoreCase(entry, resolutionContext, "Expiry Month")
        val expiryYear = getFieldValueIgnoreCase(entry, resolutionContext, "Expiry Year")
        val fallbackExpiry = splitCardExpiry(
            getFieldValueIgnoreCase(
                entry,
                resolutionContext,
                FIELD_CARD_EXPIRY,
                "CardExpiry",
                "Expiration Date",
                "Expiry Date"
            )
        )
        val data = BankCardData(
            cardNumber = getFieldValueIgnoreCase(
                entry,
                resolutionContext,
                FIELD_CARD_NUMBER,
                "CardNumber",
                "Credit Card Number",
                "CreditCardNumber"
            ),
            cardholderName = getFieldValueIgnoreCase(
                entry,
                resolutionContext,
                FIELD_CARD_HOLDER,
                "CardHolder",
                "Credit Card Holder",
                "CreditCardHolder"
            ),
            expiryMonth = expiryMonth.ifBlank { fallbackExpiry.first },
            expiryYear = expiryYear.ifBlank { fallbackExpiry.second },
            cvv = getFieldValueIgnoreCase(entry, resolutionContext, FIELD_CARD_CVV, "CardCVV", "CVV", "CVC"),
            bankName = getFieldValueIgnoreCase(entry, resolutionContext, FIELD_BANK_NAME),
            cardType = parseKeePassCardType(getFieldValueIgnoreCase(entry, resolutionContext, FIELD_CARD_TYPE)),
            billingAddress = getFieldValueIgnoreCase(entry, resolutionContext, FIELD_BILLING_ADDRESS),
            brand = getFieldValueIgnoreCase(entry, resolutionContext, FIELD_BRAND),
            nickname = getFieldValueIgnoreCase(entry, resolutionContext, FIELD_NICKNAME),
            validFromMonth = getFieldValueIgnoreCase(entry, resolutionContext, FIELD_VALID_FROM_MONTH),
            validFromYear = getFieldValueIgnoreCase(entry, resolutionContext, FIELD_VALID_FROM_YEAR),
            pin = getFieldValueIgnoreCase(entry, resolutionContext, FIELD_PIN),
            iban = getFieldValueIgnoreCase(entry, resolutionContext, FIELD_IBAN),
            swiftBic = getFieldValueIgnoreCase(entry, resolutionContext, FIELD_SWIFT_BIC),
            routingNumber = getFieldValueIgnoreCase(entry, resolutionContext, FIELD_ROUTING_NUMBER),
            accountNumber = getFieldValueIgnoreCase(entry, resolutionContext, FIELD_ACCOUNT_NUMBER),
            branchCode = getFieldValueIgnoreCase(entry, resolutionContext, FIELD_BRANCH_CODE),
            currency = getFieldValueIgnoreCase(entry, resolutionContext, FIELD_CURRENCY),
            customerServicePhone = getFieldValueIgnoreCase(entry, resolutionContext, FIELD_CUSTOMER_SERVICE_PHONE),
            customFields = extractStructuredSecureItemCustomFields(entry, resolutionContext)
        )
        val hasAnyCardField = listOf(
            data.cardNumber,
            data.cardholderName,
            data.expiryMonth,
            data.expiryYear,
            data.cvv,
            data.bankName,
            data.billingAddress,
            data.brand,
            data.nickname,
            data.pin,
            data.iban,
            data.swiftBic,
            data.routingNumber,
            data.accountNumber,
            data.branchCode,
            data.currency,
            data.customerServicePhone
        ).any { it.isNotBlank() } || data.customFields.isNotEmpty()

        return if (hasAnyCardField) {
            CardWalletDataCodec.encodeBankCardData(data)
        } else {
            null
        }
    }

    private fun splitCardExpiry(raw: String): Pair<String, String> {
        val normalized = raw.trim()
        if (normalized.isBlank()) return "" to ""
        val parts = normalized.split("/", "-", ".", " ")
            .map { it.trim() }
            .filter { it.isNotBlank() }
        if (parts.size < 2) return "" to normalized
        return parts[0] to parts[1]
    }

    private fun parseKeePassCardType(raw: String): CardType {
        return when (raw.trim().uppercase(Locale.ROOT).replace(" ", "_")) {
            "DEBIT", "DEBIT_CARD" -> CardType.DEBIT
            "PREPAID", "PREPAID_CARD" -> CardType.PREPAID
            else -> CardType.CREDIT
        }
    }

    private fun extractStructuredSecureItemCustomFields(
        entry: Entry,
        resolutionContext: KeePassEntryResolutionContext?
    ): List<SecureCustomField> {
        val reserved = (baseSecureItemFieldNames() + bankCardSecureItemFieldNames())
            .mapTo(mutableSetOf()) { it.lowercase(Locale.ROOT) }
        return entry.fields.mapNotNull { (key, value) ->
            val normalizedKey = key.trim()
            if (normalizedKey.isBlank() || normalizedKey.startsWith("_etm_")) return@mapNotNull null
            if (normalizedKey.lowercase(Locale.ROOT) in reserved) return@mapNotNull null
            val content = getFieldValue(entry, key, resolutionContext)
            if (content.isBlank()) return@mapNotNull null
            SecureCustomField(
                label = normalizedKey,
                value = content,
                type = if (value is EntryValue.Encrypted) {
                    SecureCustomFieldType.HIDDEN
                } else {
                    SecureCustomFieldType.TEXT
                }
            )
        }
    }

    private fun baseSecureItemFieldNames(): Set<String> {
        return setOf(
            "Title",
            "UserName",
            "Password",
            "URL",
            "Notes",
            FIELD_MONICA_ITEM_TYPE,
            FIELD_MONICA_ITEM_DATA,
            FIELD_MONICA_IMAGE_PATHS,
            FIELD_MONICA_IS_FAVORITE,
            FIELD_MONICA_ITEM_ID
        )
    }

    private fun bankCardSecureItemFieldNames(): Set<String> {
        return setOf(
            FIELD_CARD_NUMBER,
            "CardNumber",
            "Credit Card Number",
            "CreditCardNumber",
            FIELD_CARD_HOLDER,
            "CardHolder",
            "Credit Card Holder",
            "CreditCardHolder",
            FIELD_CARD_EXPIRY,
            "CardExpiry",
            "Expiration Date",
            "Expiry Date",
            "Expiry Month",
            "Expiry Year",
            FIELD_CARD_CVV,
            "CardCVV",
            "CVV",
            "CVC",
            FIELD_BANK_NAME,
            FIELD_CARD_TYPE,
            FIELD_BILLING_ADDRESS,
            FIELD_BRAND,
            FIELD_NICKNAME,
            FIELD_VALID_FROM_MONTH,
            FIELD_VALID_FROM_YEAR,
            FIELD_PIN,
            FIELD_IBAN,
            FIELD_SWIFT_BIC,
            FIELD_ROUTING_NUMBER,
            FIELD_ACCOUNT_NUMBER,
            FIELD_BRANCH_CODE,
            FIELD_CURRENCY,
            FIELD_CUSTOMER_SERVICE_PHONE
        )
    }

    private fun entryToPasskey(
        entry: Entry,
        databaseId: Long,
        groupPath: String?,
        groupUuid: UUID?,
        resolutionContext: KeePassEntryResolutionContext? = null
    ): PasskeyEntry? {
        if (isMonicaConflictCopy(entry, resolutionContext)) {
            return null
        }
        val title = getFieldValue(entry, "Title", resolutionContext)
        val notes = getFieldValue(entry, "Notes", resolutionContext)
        val rawCredentialId = getFieldValue(entry, FIELD_MONICA_PASSKEY_CREDENTIAL_ID, resolutionContext)
        val rawPayload = getFieldValue(entry, FIELD_MONICA_PASSKEY_DATA, resolutionContext)
        val decoded = if (rawCredentialId.isBlank() && rawPayload.isBlank()) {
            null
        } else {
            KeePassPasskeySyncCodec.decode(
                raw = rawPayload,
                databaseId = databaseId,
                groupPath = groupPath,
                groupUuid = groupUuid?.toString()
            )
        }

        val passkey = decoded?.copy(
            credentialId = rawCredentialId.ifBlank { decoded.credentialId },
            keepassDatabaseId = databaseId,
            keepassGroupPath = groupPath,
            bitwardenVaultId = null,
            bitwardenFolderId = null,
            bitwardenCipherId = null,
            syncStatus = "NONE",
            passkeyMode = PasskeyEntry.MODE_KEEPASS_COMPAT
        ) ?: KeePassDxPasskeyCodec.decode(
            getField = { key -> getFieldValue(entry, key, resolutionContext) },
            title = title,
            notes = notes,
            databaseId = databaseId,
            groupPath = groupPath,
            groupUuid = groupUuid?.toString(),
            createdAt = entry.times?.creationTime?.toEpochMilli() ?: System.currentTimeMillis(),
            lastUsedAt = entry.times?.lastAccessTime?.toEpochMilli()
                ?: entry.times?.creationTime?.toEpochMilli()
                ?: System.currentTimeMillis(),
            useCount = entry.times?.usageCount ?: 0
        )

        return passkey?.copy(
            keepassDatabaseId = databaseId,
            keepassGroupPath = groupPath,
            bitwardenVaultId = null,
            bitwardenFolderId = null,
            bitwardenCipherId = null,
            syncStatus = "NONE",
            passkeyMode = PasskeyEntry.MODE_KEEPASS_COMPAT
        )?.let { PasskeyPrivateKeyStore.protectPasskey(context, it) }
    }

    private fun isLikelyRecycleBinPath(groupPath: String?): Boolean {
        return isLikelyKeePassRecycleBinPath(groupPath)
    }

    private fun resolveRecycleBinUuid(meta: Meta): UUID? {
        if (!meta.recycleBinEnabled) return null
        return meta.recycleBinUuid
    }

    private fun parseUuid(value: String?): UUID? {
        if (value.isNullOrBlank()) return null
        return runCatching { UUID.fromString(value) }.getOrNull()
    }

    private fun resolveRecycleBinFlag(
        groupPath: String?,
        isInRecycleBinByMeta: Boolean,
        hasRecycleBinMeta: Boolean
    ): Boolean {
        if (hasRecycleBinMeta) return isInRecycleBinByMeta
        return isLikelyRecycleBinPath(groupPath)
    }

    /**
     * 标准 Password 字段为空时，尝试从常见自定义受保护字段中提取密码。
     */
    private fun resolveEntryPassword(
        entry: Entry,
        resolutionContext: KeePassEntryResolutionContext? = null
    ): String {
        fun isLikelyLabelValue(value: String, key: String? = null): Boolean {
            val normalized = value.trim().lowercase(Locale.ROOT)
            if (normalized.isBlank()) return true
            val labelTokens = setOf("password", "pass", "pwd", "pin", "密码", "口令")
            if (normalized in labelTokens) return true
            if (key != null && normalized == key.trim().lowercase(Locale.ROOT)) return true
            return false
        }

        val standardPassword = getStandardPassword(entry, resolutionContext)
        if (standardPassword.isNotBlank() && !isLikelyLabelValue(standardPassword, "Password")) {
            return standardPassword
        }
        var fallback = standardPassword.takeIf { it.isNotBlank() }

        val prioritizedKeys = listOf(
            "密码", "口令", "PIN", "Pin", "pin", "pwd", "PWD", "pass", "Pass", "password", "Password"
        )
        prioritizedKeys.forEach { key ->
            val value = getFieldValueIgnoreCase(entry, resolutionContext, key)
            if (value.isBlank()) return@forEach
            if (!isLikelyLabelValue(value, key)) return value
            if (fallback.isNullOrBlank()) fallback = value
        }

        entry.fields.forEach { (key, value) ->
            if (!KeePassFieldRegistry.isPasswordSecretFallbackCandidateField(key)) return@forEach
            if (value is EntryValue.Encrypted) {
                val content = KeePassFieldReferenceResolver.resolveValue(
                    rawValue = runCatching { value.content }.getOrDefault(""),
                    currentEntry = entry,
                    context = resolutionContext
                )
                if (content.isBlank()) return@forEach
                if (!isLikelyLabelValue(content, key)) return content
                if (fallback.isNullOrBlank()) fallback = content
            }
        }

        return fallback ?: ""
    }

    private fun getStandardTitle(
        entry: Entry,
        resolutionContext: KeePassEntryResolutionContext? = null
    ): String = getFieldValueIgnoreCase(entry, resolutionContext, "Title", "Name")

    private fun getStandardUsername(
        entry: Entry,
        resolutionContext: KeePassEntryResolutionContext? = null
    ): String = getFieldValueIgnoreCase(entry, resolutionContext, "UserName", "Username", "User", "Login")

    private fun getStandardPassword(
        entry: Entry,
        resolutionContext: KeePassEntryResolutionContext? = null
    ): String = getFieldValueIgnoreCase(entry, resolutionContext, "Password", "Pass", "pass", "pwd", "PWD", "密码", "口令")

    private fun getStandardUrl(
        entry: Entry,
        resolutionContext: KeePassEntryResolutionContext? = null
    ): String = getFieldValueIgnoreCase(entry, resolutionContext, "URL", "Url", "Website", "URI")

    private fun getStandardNotes(
        entry: Entry,
        resolutionContext: KeePassEntryResolutionContext? = null
    ): String = getFieldValueIgnoreCase(entry, resolutionContext, "Notes", "Note", "Comment")

    private fun getFieldValue(
        entry: Entry,
        key: String,
        resolutionContext: KeePassEntryResolutionContext? = null
    ): String {
        return KeePassFieldReferenceResolver.getFieldValue(entry, key, resolutionContext)
    }

    private fun getFieldValueIgnoreCase(
        entry: Entry,
        resolutionContext: KeePassEntryResolutionContext? = null,
        vararg keys: String
    ): String {
        return KeePassFieldReferenceResolver.getFieldValueIgnoreCase(
            entry = entry,
            context = resolutionContext,
            *keys
        )
    }

    private fun isEnhancedEntryTemplate(
        entry: Entry,
        title: String,
        username: String,
        password: String,
        url: String,
        notes: String,
        resolutionContext: KeePassEntryResolutionContext? = null
    ): Boolean {
        val templateFlag = getFieldValue(entry, FIELD_KEEPASS_XC_TEMPLATE, resolutionContext)
            .trim()
            .lowercase(Locale.ROOT)
        if (templateFlag.isEmpty() || templateFlag == "0" || templateFlag == "false") {
            return false
        }
        if (title.isBlank() || username.isNotBlank() || password.isNotBlank() || url.isNotBlank() || notes.isNotBlank()) {
            return false
        }

        val standardFields = setOf("Title", "UserName", "Password", "URL", "Notes")
        val hasFilledUserField = entry.fields.keys.any { key ->
            key !in standardFields &&
                !key.startsWith("_etm_") &&
                getFieldValue(entry, key, resolutionContext).isNotBlank()
        }
        return !hasFilledUserField
    }

    private fun isPasskeyEntry(
        entry: Entry,
        resolutionContext: KeePassEntryResolutionContext? = null
    ): Boolean {
        if (getFieldValue(entry, FIELD_MONICA_PASSKEY_CREDENTIAL_ID, resolutionContext).isNotBlank()) {
            return true
        }
        if (getFieldValue(entry, FIELD_MONICA_PASSKEY_DATA, resolutionContext).isNotBlank()) {
            return true
        }
        return KeePassDxPasskeyCodec.isPasskey { key ->
            getFieldValue(entry, key, resolutionContext)
        }
    }

    private fun isMonicaConflictCopy(
        entry: Entry,
        resolutionContext: KeePassEntryResolutionContext? = null
    ): Boolean {
        val marker = getFieldValue(entry, FIELD_MONICA_CONFLICT_COPY, resolutionContext)
            .trim()
            .lowercase(Locale.ROOT)
        return marker == "true" || marker == "1" || marker == "yes"
    }

    private fun parseStandardTotpFromEntry(
        entry: Entry,
        resolutionContext: KeePassEntryResolutionContext? = null
    ): TotpData? {
        return KeePassTotpCodec.parse(
            KeePassTotpCodec.Fields(
                otp = getFieldValueIgnoreCase(entry, resolutionContext, "otp"),
                seed = getFieldValueIgnoreCase(entry, resolutionContext, "TOTP Seed", "TOTPSeed"),
                settings = getFieldValueIgnoreCase(entry, resolutionContext, "TOTP Settings", "TOTPSettings"),
                period = getFieldValueIgnoreCase(entry, resolutionContext, "TOTP Period", "TOTPPeriod"),
                digits = getFieldValueIgnoreCase(entry, resolutionContext, "TOTP Digits", "TOTPDigits"),
                algorithm = getFieldValueIgnoreCase(entry, resolutionContext, "TOTP Algorithm", "TOTPAlgorithm"),
                counter = getFieldValueIgnoreCase(entry, resolutionContext, "HOTP Counter", "HOTPCounter"),
                type = getFieldValueIgnoreCase(entry, resolutionContext, "OTP Type", "OTPType", "TOTP Type", "TOTPType"),
                issuer = getFieldValue(entry, "Title", resolutionContext),
                accountName = getFieldValue(entry, "UserName", resolutionContext),
                link = getFieldValue(entry, "URL", resolutionContext)
            )
        )
    }

    private fun collectEntries(group: Group, entries: MutableList<Entry>) {
        entries.addAll(group.entries)
        group.groups.forEach { collectEntries(it, entries) }
    }

    private fun buildResolutionContext(keePassDatabase: KeePassDatabase): KeePassEntryResolutionContext {
        val entries = mutableListOf<Entry>()
        collectEntries(keePassDatabase.content.group, entries)
        return KeePassFieldReferenceResolver.buildContext(entries)
    }

    private fun buildGroupTraversalContextIndex(
        keePassDatabase: KeePassDatabase
    ): Map<UUID, GroupTraversalContext> {
        val recycleBinUuid = resolveRecycleBinUuid(keePassDatabase.content.meta)
        val result = mutableMapOf<UUID, GroupTraversalContext>()
        collectGroupTraversalContext(
            group = keePassDatabase.content.group,
            currentPathKey = null,
            recycleBinUuid = recycleBinUuid,
            parentInRecycleBin = false,
            result = result
        )
        return result
    }

    private fun collectGroupTraversalContext(
        group: Group,
        currentPathKey: String?,
        recycleBinUuid: UUID?,
        parentInRecycleBin: Boolean,
        result: MutableMap<UUID, GroupTraversalContext>
    ) {
        val inRecycleBin = parentInRecycleBin || (recycleBinUuid != null && group.uuid == recycleBinUuid)
        result[group.uuid] = GroupTraversalContext(
            pathKey = currentPathKey,
            groupUuid = group.uuid,
            isInRecycleBinByMeta = inRecycleBin
        )
        group.groups.forEach { child ->
            val childPathKey = buildKeePassPathKey(currentPathKey, child.name)
            collectGroupTraversalContext(
                group = child,
                currentPathKey = childPathKey,
                recycleBinUuid = recycleBinUuid,
                parentInRecycleBin = inRecycleBin,
                result = result
            )
        }
    }

    private fun resolveRestoreTargetFromEntryContext(
        entryContext: EntryTraversalContext,
        hasRecycleBinMeta: Boolean,
        groupContextIndex: Map<UUID, GroupTraversalContext>
    ): KeePassRestoreTarget {
        val inRecycleBin = if (hasRecycleBinMeta) {
            entryContext.isInRecycleBinByMeta
        } else {
            isLikelyRecycleBinPath(entryContext.groupPath)
        }
        if (!inRecycleBin) {
            return KeePassRestoreTarget(
                groupPath = entryContext.groupPath,
                groupUuid = entryContext.groupUuid?.toString()
            )
        }

        val previousParentUuid = entryContext.entry.previousParentGroup
        if (previousParentUuid != null) {
            val previousParentContext = groupContextIndex[previousParentUuid]
            if (previousParentContext != null) {
                val previousInRecycleBin = if (hasRecycleBinMeta) {
                    previousParentContext.isInRecycleBinByMeta
                } else {
                    isLikelyRecycleBinPath(previousParentContext.pathKey)
                }
                if (!previousInRecycleBin) {
                    return KeePassRestoreTarget(
                        groupPath = previousParentContext.pathKey,
                        groupUuid = previousParentContext.groupUuid.toString()
                    )
                }
            }
        }

        return KeePassRestoreTarget(groupPath = null, groupUuid = null)
    }

    private fun isGroupPathInRecycleBinByMeta(
        group: Group,
        currentPathKey: String?,
        targetPathKey: String,
        recycleBinUuid: UUID,
        parentInRecycleBin: Boolean
    ): Boolean? {
        val inRecycle = parentInRecycleBin || group.uuid == recycleBinUuid
        if (currentPathKey == targetPathKey) {
            return inRecycle
        }
        group.groups.forEach { child ->
            val childPathKey = buildKeePassPathKey(currentPathKey, child.name)
            val childResult = isGroupPathInRecycleBinByMeta(
                group = child,
                currentPathKey = childPathKey,
                targetPathKey = targetPathKey,
                recycleBinUuid = recycleBinUuid,
                parentInRecycleBin = inRecycle
            )
            if (childResult != null) {
                return childResult
            }
        }
        return null
    }

    private fun collectEntryContexts(
        keePassDatabase: KeePassDatabase
    ): Pair<List<EntryTraversalContext>, Boolean> {
        val recycleBinUuid = resolveRecycleBinUuid(keePassDatabase.content.meta)
        val hasRecycleBinMeta = recycleBinUuid != null
        val entries = mutableListOf<EntryTraversalContext>()
        collectEntriesWithGroupPath(
            group = keePassDatabase.content.group,
            currentPathKey = null,
            recycleBinUuid = recycleBinUuid,
            parentInRecycleBin = false,
            entries = entries
        )
        return entries to hasRecycleBinMeta
    }

    private fun collectEntriesWithGroupPath(
        group: Group,
        currentPathKey: String?,
        recycleBinUuid: UUID?,
        parentInRecycleBin: Boolean,
        entries: MutableList<EntryTraversalContext>
    ) {
        val inRecycleBin = parentInRecycleBin || (recycleBinUuid != null && group.uuid == recycleBinUuid)
        group.entries.forEach { entry ->
            entries.add(
                EntryTraversalContext(
                    entry = entry,
                    groupPath = currentPathKey,
                    groupUuid = group.uuid,
                    isInRecycleBinByMeta = inRecycleBin
                )
            )
        }
        group.groups.forEach { child ->
            val nextPathKey = buildKeePassPathKey(currentPathKey, child.name)
            collectEntriesWithGroupPath(
                group = child,
                currentPathKey = nextPathKey,
                recycleBinUuid = recycleBinUuid,
                parentInRecycleBin = inRecycleBin,
                entries = entries
            )
        }
    }

    private fun collectGroups(
        group: Group,
        parentPathKey: String,
        depth: Int,
        result: MutableList<KeePassGroupInfo>,
        recycleBinUuid: UUID?,
        includeRecycleBin: Boolean,
        parentInRecycleBin: Boolean
    ) {
        val inRecycleBin = parentInRecycleBin || (recycleBinUuid != null && group.uuid == recycleBinUuid)
        if (!includeRecycleBin && inRecycleBin) {
            return
        }
        val name = group.name.ifBlank { "(未命名)" }
        val currentPathKey = buildKeePassPathKey(parentPathKey, name)
        val currentDisplayPath = decodeKeePassPathForDisplay(currentPathKey)
        result.add(
            KeePassGroupInfo(
                name = name,
                path = currentPathKey,
                uuid = group.uuid.toString(),
                depth = depth,
                displayPath = currentDisplayPath
            )
        )
        group.groups.forEach { child ->
            collectGroups(
                group = child,
                parentPathKey = currentPathKey,
                depth = depth + 1,
                result = result,
                recycleBinUuid = recycleBinUuid,
                includeRecycleBin = includeRecycleBin,
                parentInRecycleBin = inRecycleBin
            )
        }
    }

    private fun buildGroupInfoList(
        keePassDatabase: KeePassDatabase,
        includeRecycleBin: Boolean
    ): List<KeePassGroupInfo> {
        val recycleBinUuid = resolveRecycleBinUuid(keePassDatabase.content.meta)
        val groups = mutableListOf<KeePassGroupInfo>()
        keePassDatabase.content.group.groups.forEach { group ->
            collectGroups(
                group = group,
                parentPathKey = "",
                depth = 0,
                result = groups,
                recycleBinUuid = recycleBinUuid,
                includeRecycleBin = includeRecycleBin,
                parentInRecycleBin = false
            )
        }
        return groups.sortedBy { it.displayPath }
    }

    private fun findGroupPathByUuid(
        group: Group,
        currentPathKey: String?,
        targetUuid: UUID
    ): String? {
        if (group.uuid == targetUuid) {
            return currentPathKey
        }
        group.groups.forEach { child ->
            val childPathKey = buildKeePassPathKey(currentPathKey, child.name)
            val childResult = findGroupPathByUuid(
                group = child,
                currentPathKey = childPathKey,
                targetUuid = targetUuid
            )
            if (childResult != null) {
                return childResult
            }
        }
        return null
    }

    private fun findGroupUuidByPath(
        group: Group,
        currentPathKey: String?,
        targetPathKey: String
    ): UUID? {
        if (currentPathKey == targetPathKey) {
            return group.uuid
        }
        group.groups.forEach { child ->
            val childPathKey = buildKeePassPathKey(currentPathKey, child.name)
            val childResult = findGroupUuidByPath(
                group = child,
                currentPathKey = childPathKey,
                targetPathKey = targetPathKey
            )
            if (childResult != null) return childResult
        }
        return null
    }

    private fun addGroupToPath(
        group: Group,
        parentSegments: List<String>,
        newGroupName: String,
        currentPathKey: String
    ): Pair<Group, KeePassGroupInfo> {
        if (parentSegments.isEmpty()) {
            val existing = group.groups.firstOrNull { it.name.equals(newGroupName, ignoreCase = true) }
            if (existing != null) {
                val existingPath = buildKeePassPathKey(currentPathKey, existing.name)
                return group to KeePassGroupInfo(
                    name = existing.name,
                    path = existingPath,
                    uuid = existing.uuid.toString(),
                    depth = decodeKeePassPathSegments(existingPath).size - 1,
                    displayPath = decodeKeePassPathForDisplay(existingPath)
                )
            }

            val newGroup = Group(
                uuid = UUID.randomUUID(),
                name = newGroupName
            )
            val newPath = buildKeePassPathKey(currentPathKey, newGroupName)
            return group.copy(groups = group.groups + newGroup) to KeePassGroupInfo(
                name = newGroupName,
                path = newPath,
                uuid = newGroup.uuid.toString(),
                depth = decodeKeePassPathSegments(newPath).size - 1,
                displayPath = decodeKeePassPathForDisplay(newPath)
            )
        }

        val nextSegment = parentSegments.first()
        val childIndex = group.groups.indexOfFirst { it.name == nextSegment }
        if (childIndex < 0) {
            throw IllegalArgumentException("父分组不存在: $nextSegment")
        }

        val child = group.groups[childIndex]
        val childPath = buildKeePassPathKey(currentPathKey, child.name)
        val childResult = addGroupToPath(
            group = child,
            parentSegments = parentSegments.drop(1),
            newGroupName = newGroupName,
            currentPathKey = childPath
        )

        val updatedGroups = group.groups.toMutableList()
        updatedGroups[childIndex] = childResult.first
        return group.copy(groups = updatedGroups) to childResult.second
    }

    private fun renameGroupByPath(
        group: Group,
        pathSegments: List<String>,
        newName: String,
        currentPathKey: String
    ): Pair<Group, KeePassGroupInfo> {
        val targetName = pathSegments.firstOrNull()
            ?: throw IllegalArgumentException("分组路径无效")
        val childIndex = group.groups.indexOfFirst { it.name == targetName }
        if (childIndex < 0) {
            throw IllegalArgumentException("分组不存在: $targetName")
        }

        val child = group.groups[childIndex]
        val updatedGroups = group.groups.toMutableList()

        return if (pathSegments.size == 1) {
            val conflict = group.groups.anyIndexed { index, sibling ->
                index != childIndex && sibling.name.equals(newName, ignoreCase = true)
            }
            if (conflict) {
                throw IllegalArgumentException("同级已存在同名分组")
            }

            val renamed = child.copy(name = newName)
            updatedGroups[childIndex] = renamed
            val newPath = buildKeePassPathKey(currentPathKey, newName)
            group.copy(groups = updatedGroups) to KeePassGroupInfo(
                name = newName,
                path = newPath,
                uuid = renamed.uuid.toString(),
                depth = decodeKeePassPathSegments(newPath).size - 1,
                displayPath = decodeKeePassPathForDisplay(newPath)
            )
        } else {
            val childPath = buildKeePassPathKey(currentPathKey, child.name)
            val childResult = renameGroupByPath(
                group = child,
                pathSegments = pathSegments.drop(1),
                newName = newName,
                currentPathKey = childPath
            )
            updatedGroups[childIndex] = childResult.first
            group.copy(groups = updatedGroups) to childResult.second
        }
    }

    private fun removeGroupByPath(
        group: Group,
        pathSegments: List<String>
    ): Pair<Group, Boolean> {
        val targetName = pathSegments.firstOrNull() ?: return group to false
        val childIndex = group.groups.indexOfFirst { it.name == targetName }
        if (childIndex < 0) return group to false

        val updatedGroups = group.groups.toMutableList()
        return if (pathSegments.size == 1) {
            updatedGroups.removeAt(childIndex)
            group.copy(groups = updatedGroups) to true
        } else {
            val child = group.groups[childIndex]
            val childResult = removeGroupByPath(child, pathSegments.drop(1))
            if (!childResult.second) return group to false
            updatedGroups[childIndex] = childResult.first
            group.copy(groups = updatedGroups) to true
        }
    }

    private data class ExtractedGroupResult(
        val updatedGroup: Group,
        val removedGroup: Group?
    )

    private fun extractGroupByPath(
        group: Group,
        pathSegments: List<String>
    ): ExtractedGroupResult {
        val targetName = pathSegments.firstOrNull() ?: return ExtractedGroupResult(group, null)
        val childIndex = group.groups.indexOfFirst { it.name == targetName }
        if (childIndex < 0) return ExtractedGroupResult(group, null)

        val updatedGroups = group.groups.toMutableList()
        return if (pathSegments.size == 1) {
            val removedGroup = updatedGroups.removeAt(childIndex)
            ExtractedGroupResult(
                updatedGroup = group.copy(groups = updatedGroups),
                removedGroup = removedGroup
            )
        } else {
            val child = group.groups[childIndex]
            val childResult = extractGroupByPath(child, pathSegments.drop(1))
            if (childResult.removedGroup == null) {
                return ExtractedGroupResult(group, null)
            }
            updatedGroups[childIndex] = childResult.updatedGroup
            ExtractedGroupResult(
                updatedGroup = group.copy(groups = updatedGroups),
                removedGroup = childResult.removedGroup
            )
        }
    }

    private data class InsertedGroupResult(
        val updatedGroup: Group,
        val groupInfo: KeePassGroupInfo
    )

    private fun buildGroupInfoFromPath(
        group: Group,
        pathKey: String
    ): KeePassGroupInfo {
        return KeePassGroupInfo(
            name = group.name,
            path = pathKey,
            uuid = group.uuid.toString(),
            depth = decodeKeePassPathSegments(pathKey).size - 1,
            displayPath = decodeKeePassPathForDisplay(pathKey)
        )
    }

    private fun insertGroupToPath(
        group: Group,
        parentSegments: List<String>,
        groupToInsert: Group,
        currentPathKey: String
    ): InsertedGroupResult {
        if (parentSegments.isEmpty()) {
            val conflict = group.groups.any { sibling ->
                sibling.uuid != groupToInsert.uuid && sibling.name.equals(groupToInsert.name, ignoreCase = true)
            }
            if (conflict) {
                throw IllegalArgumentException("同级已存在同名分组")
            }
            val newPath = buildKeePassPathKey(currentPathKey, groupToInsert.name)
            return InsertedGroupResult(
                updatedGroup = group.copy(groups = group.groups + groupToInsert),
                groupInfo = KeePassGroupInfo(
                    name = groupToInsert.name,
                    path = newPath,
                    uuid = groupToInsert.uuid.toString(),
                    depth = decodeKeePassPathSegments(newPath).size - 1,
                    displayPath = decodeKeePassPathForDisplay(newPath)
                )
            )
        }

        val nextSegment = parentSegments.first()
        val childIndex = group.groups.indexOfFirst { it.name == nextSegment }
        if (childIndex < 0) {
            throw IllegalArgumentException("父分组不存在: $nextSegment")
        }

        val child = group.groups[childIndex]
        val childPath = buildKeePassPathKey(currentPathKey, child.name)
        val childResult = insertGroupToPath(
            group = child,
            parentSegments = parentSegments.drop(1),
            groupToInsert = groupToInsert,
            currentPathKey = childPath
        )
        val updatedGroups = group.groups.toMutableList()
        updatedGroups[childIndex] = childResult.updatedGroup
        return InsertedGroupResult(
            updatedGroup = group.copy(groups = updatedGroups),
            groupInfo = childResult.groupInfo
        )
    }

    private fun buildGroupTreeChangePatch(
        database: KeePassDatabase,
        root: Group,
        sourceRootGroupUuid: UUID?,
        targetParentGroupUuid: UUID?
    ): KeePassGroupTreeChangePatch {
        val referencedBinaryHashes = linkedSetOf<String>()
        val snapshot = root.toGroupTreeSnapshot(referencedBinaryHashes)
        val binaryPool = referencedBinaryHashes.map { hashHex ->
            val hash = hashHex.toByteStringHash()
            val binaryData = database.binaries[hash]
                ?: throw IllegalStateException("KeePass binary pool is missing group tree attachment: $hashHex")
            KeePassBinaryPoolItemPatch(
                hash = hashHex,
                protected = binaryData.memoryProtection,
                compressed = true,
                contentBase64 = Base64.encodeToString(
                    binaryData.inputStream().use { it.readBytes() },
                    Base64.NO_WRAP
                )
            )
        }
        return KeePassGroupTreeChangePatch(
            root = snapshot,
            binaryPool = binaryPool,
            sourceRootGroupUuid = sourceRootGroupUuid?.toString(),
            targetParentGroupUuid = targetParentGroupUuid?.toString()
        )
    }

    private fun Group.toGroupTreeSnapshot(referencedBinaryHashes: MutableSet<String>): KeePassGroupTreeSnapshot {
        return KeePassGroupTreeSnapshot(
            uuid = uuid.toString(),
            name = name,
            notes = notes,
            iconName = icon.name,
            customIconUuid = customIconUuid?.toString(),
            expanded = expanded,
            defaultAutoTypeSequence = defaultAutoTypeSequence.orEmpty(),
            enableAutoType = enableAutoType.name,
            enableSearching = enableSearching.name,
            lastTopVisibleEntryUuid = lastTopVisibleEntry?.toString(),
            previousParentGroupUuid = previousParentGroup?.toString(),
            tags = tags,
            times = times?.toKeePassTimesPatch(),
            customData = customData.toKeePassCustomDataPatchList(),
            entries = entries.map { it.toEntryTreeSnapshot(referencedBinaryHashes) },
            groups = groups.map { it.toGroupTreeSnapshot(referencedBinaryHashes) }
        )
    }

    private fun Entry.toEntryTreeSnapshot(referencedBinaryHashes: MutableSet<String>): KeePassEntryTreeSnapshot {
        return KeePassEntryTreeSnapshot(
            uuid = uuid.toString(),
            fields = fields.map { (name, value) ->
                KeePassFieldChange(
                    name = name,
                    value = value.content,
                    protected = value is EntryValue.Encrypted
                )
            },
            binaries = binaries.map { reference ->
                val hashHex = reference.hash.hex()
                referencedBinaryHashes += hashHex
                KeePassBinaryReferencePatch(
                    name = reference.name,
                    hash = hashHex
                )
            },
            history = history.map { it.toEntryTreeSnapshot(referencedBinaryHashes) },
            iconName = icon.name,
            customIconUuid = customIconUuid?.toString(),
            foregroundColor = foregroundColor,
            backgroundColor = backgroundColor,
            overrideUrl = overrideUrl,
            autoType = autoType?.toKeePassAutoTypePatch(),
            tags = tags,
            times = times?.toKeePassTimesPatch(),
            customData = customData.toKeePassCustomDataPatchList(),
            previousParentGroupUuid = previousParentGroup?.toString(),
            qualityCheck = qualityCheck
        )
    }

    private fun TimeData.toKeePassTimesPatch(): KeePassTimesPatch {
        return KeePassTimesPatch(
            creationTimeEpochMillis = creationTime?.toEpochMilli(),
            lastModificationTimeEpochMillis = lastModificationTime?.toEpochMilli(),
            lastAccessTimeEpochMillis = lastAccessTime?.toEpochMilli(),
            expiryTimeEpochMillis = expiryTime?.toEpochMilli(),
            expires = expires,
            usageCount = usageCount,
            locationChangedEpochMillis = locationChanged?.toEpochMilli()
        )
    }

    private fun Map<String, CustomDataValue>.toKeePassCustomDataPatchList(): List<KeePassCustomDataPatch> {
        return map { (key, value) ->
            KeePassCustomDataPatch(
                key = key,
                value = value.value,
                lastModifiedEpochMillis = value.lastModified?.toEpochMilli()
            )
        }
    }

    private fun AutoTypeData.toKeePassAutoTypePatch(): KeePassAutoTypePatch {
        return KeePassAutoTypePatch(
            enabled = enabled,
            obfuscation = obfuscation.name,
            defaultSequence = defaultSequence.orEmpty(),
            items = items.map { it.toKeePassAutoTypeItemPatch() }
        )
    }

    private fun AutoTypeItem.toKeePassAutoTypeItemPatch(): KeePassAutoTypeItemPatch {
        return KeePassAutoTypeItemPatch(
            window = window,
            keystrokeSequence = keystrokeSequence
        )
    }

    private fun String.toByteStringHash(): ByteString {
        val normalized = trim()
        require(normalized.length % 2 == 0) { "Invalid hex length" }
        val bytes = ByteArray(normalized.length / 2) { index ->
            normalized.substring(index * 2, index * 2 + 2).toInt(16).toByte()
        }
        return bytes.toByteString()
    }

    private inline fun <T> List<T>.anyIndexed(predicate: (Int, T) -> Boolean): Boolean {
        for (index in indices) {
            if (predicate(index, this[index])) return true
        }
        return false
    }

    private suspend fun <T> mutateDatabase(
        databaseId: Long,
        forceSyncWrite: Boolean = false,
        mutation: (LoadedDatabase) -> MutationPlan<T>
    ): T {
        return withDatabaseMutationLocks(listOf(databaseId)) {
            try {
                if (forceSyncWrite && shouldForceReloadForWrite(databaseId)) {
                    invalidateLoadedDatabaseCache(databaseId)
                }
                val loaded = getCachedLoadedDatabase(databaseId) ?: loadDatabase(databaseId)
                val plan = mutation(loaded)
                writeDatabase(
                    database = loaded.database,
                    credentials = loaded.credentials,
                    keePassDatabase = plan.updatedDatabase,
                    sourceEtag = loaded.sourceEtag,
                    sourceLastModified = loaded.sourceLastModified,
                    beforeRemoteUpload = plan.beforeRemoteUpload
                )
                plan.afterWrite?.invoke(loaded.database, plan.updatedDatabase)
                plan.result
            } catch (e: Exception) {
                invalidateLoadedDatabaseCache(databaseId)
                throw e
            }
        }
    }

    private fun shouldForceReloadForWrite(databaseId: Long): Boolean {
        val cached = synchronized(loadedDatabaseCache) { loadedDatabaseCache[databaseId] } ?: return false
        return cached.loaded.sourceSignature == null
    }

    private suspend fun loadDatabase(databaseId: Long): LoadedDatabase {
        getCachedLoadedDatabase(databaseId)?.let { return it }
        return loadMutexForDatabase(databaseId).withLock {
            getCachedLoadedDatabase(databaseId)?.let { return@withLock it }
            val database = dao.getDatabaseById(databaseId) ?: throw Exception("数据库不存在")
            val credentials = buildCredentials(database)
            val snapshot = readDatabaseSnapshot(database)
            val (keePassDatabase, resolvedCredentials) = decodeDatabaseWithFallback(
                bytes = snapshot.bytes,
                credentialsResolution = credentials,
                sourceLabel = "databaseId=$databaseId",
                sourceName = database.resolvedActiveFilePath()
            )
            val loaded = LoadedDatabase(
                database = database,
                credentials = resolvedCredentials,
                keePassDatabase = keePassDatabase,
                sourceEtag = snapshot.etag,
                sourceLastModified = snapshot.lastModified,
                sourceSignature = snapshot.signature
            )
            cacheLoadedDatabase(loaded)
            loaded
        }
    }

    private suspend fun decodeDatabase(
        bytes: ByteArray,
        credentials: Credentials,
        sourceName: String? = null,
        logFailure: Boolean = true
    ): KeePassDatabase {
        return withContext(decodeDispatcher) {
            withGlobalDecodeLock {
                try {
                    KeePassFormatInspector.ensureKdbxSupported(bytes = bytes, sourceName = sourceName)
                    KeePassDatabase.decode(
                        ByteArrayInputStream(bytes),
                        credentials,
                        cipherProviders = KeePassCodecSupport.cipherProviders
                    )
                } catch (t: Throwable) {
                    val mapped = normalizeError(t)
                    if (logFailure) {
                        Log.e(
                            TAG,
                            "KDBX decode failed code=${(mapped as? KeePassOperationException)?.code ?: KeePassErrorCode.IO_READ_WRITE_FAILED}. ${databaseHeaderSummary(bytes)}",
                            mapped
                        )
                    }
                    throw mapped
                }
            }
        }
    }

    private suspend fun decodeDatabaseWithFallback(
        bytes: ByteArray,
        credentialsResolution: CredentialsResolution,
        sourceLabel: String,
        sourceName: String? = null
    ): Pair<KeePassDatabase, Credentials> {
        val candidates = credentialsResolution.candidates
        if (candidates.isEmpty()) {
            throw IllegalStateException("无可用凭据")
        }

        var lastError: Throwable? = null
        val attemptedLabels = mutableListOf<String>()
        candidates.forEachIndexed { index, candidate ->
            val isLast = index == candidates.lastIndex
            attemptedLabels += candidate.label
            try {
                val database = decodeDatabase(
                    bytes = bytes,
                    credentials = candidate.credentials,
                    sourceName = sourceName,
                    logFailure = isLast
                )
                if (index > 0) {
                    Log.w(
                        TAG,
                        "KDBX decoded using credential fallback ($sourceLabel, candidate=${candidate.label})"
                    )
                }
                return database to candidate.credentials
            } catch (error: Throwable) {
                val mapped = normalizeError(error)
                lastError = mapped
                val isInvalidCredential =
                    mapped is KeePassOperationException &&
                        mapped.code == KeePassErrorCode.INVALID_CREDENTIAL
                if (!isInvalidCredential || isLast) {
                    throw mapped
                }
            }
        }

        val allInvalidCredential = lastError is KeePassOperationException &&
            (lastError as KeePassOperationException).code == KeePassErrorCode.INVALID_CREDENTIAL
        if (allInvalidCredential) {
            throw KeePassOperationException(
                code = KeePassErrorCode.INVALID_CREDENTIAL,
                message = KeePassCredentialSupport.buildInvalidCredentialMessage(attemptedLabels),
                cause = lastError
            )
        }

        throw (lastError ?: KeePassOperationException(
            code = KeePassErrorCode.IO_READ_WRITE_FAILED,
            message = "KDBX 解码失败"
        ))
    }

    private fun databaseHeaderSummary(bytes: ByteArray): String {
        val headerLength = bytes.size.coerceAtMost(16)
        val headerHex = buildString {
            for (index in 0 until headerLength) {
                if (index > 0) append(' ')
                append(String.format(Locale.US, "%02X", bytes[index].toInt() and 0xFF))
            }
        }
        return "bytes=${bytes.size}, header[$headerLength]=$headerHex"
    }

    private fun buildCredentials(
        database: LocalKeePassDatabase,
        passwordOverride: String? = null,
        keyFileUriOverride: Uri? = null
    ): CredentialsResolution {
        val encryptedDbPassword = database.encryptedPassword
        val kdbxPassword = passwordOverride ?: (encryptedDbPassword?.let { securityManager.decryptData(it) } ?: "")
        val keyFileBytes = keyFileUriOverride?.let { uri ->
            readKeyFileBytes(uri)
        } ?: database.keyFileUri?.takeIf { it.isNotBlank() }?.let { uriString ->
            readKeyFileBytes(Uri.parse(uriString))
        }
        return resolveCredentials(kdbxPassword, keyFileBytes)
    }

    private fun buildCredentialsFromRaw(password: String, keyFileUri: Uri? = null): CredentialsResolution {
        val keyFileBytes = keyFileUri?.let { uri ->
            readKeyFileBytes(uri)
        }
        return resolveCredentials(password, keyFileBytes)
    }

    private fun readKeyFileBytes(uri: Uri): ByteArray {
        return readBytesFromUri(uri, "无法读取密钥文件")
    }

    private fun readBytesFromUri(uri: Uri, missingMessage: String): ByteArray {
        return try {
            openExternalInputBytes(uri)
                ?: throw FileNotFoundException(missingMessage)
        } catch (t: Throwable) {
            throw normalizeError(t)
        }
    }

    private fun openExternalInputBytes(uri: Uri): ByteArray? {
        val descriptorBytes = runCatching {
            context.contentResolver.openFileDescriptor(uri, "r")?.use { descriptor ->
                ParcelFileDescriptor.AutoCloseInputStream(descriptor).use { input ->
                    input.readBytes()
                }
            }
        }.onFailure { error ->
            Log.w(TAG, "openFileDescriptor r failed, retry with input stream", error)
        }.getOrNull()
        if (descriptorBytes != null) return descriptorBytes

        return context.contentResolver.openInputStream(uri)?.use { it.readBytes() }
    }

    private fun resolveCredentials(password: String, keyFileBytes: ByteArray?): CredentialsResolution {
        val candidates = KeePassCredentialSupport.buildCredentialCandidates(
            password = password,
            keyFileBytes = keyFileBytes
        )
        return CredentialsResolution(candidates = candidates)
    }

    private fun readDatabaseBytes(database: LocalKeePassDatabase): ByteArray {
        return readDatabaseSnapshot(database).bytes
    }

    private fun readDatabaseSnapshot(database: LocalKeePassDatabase): DatabaseSnapshot {
        return try {
            if (database.resolvedActiveStorageLocation() == KeePassStorageLocation.INTERNAL) {
                val file = File(context.filesDir, database.resolvedActiveFilePath())
                if (!file.exists()) throw FileNotFoundException("数据库文件不存在")
                val signature = DatabaseSourceSignature(
                    sizeBytes = file.length(),
                    lastModifiedEpochMs = file.lastModified()
                )
                DatabaseSnapshot(
                    bytes = file.readBytes(),
                    etag = null,
                    lastModified = null,
                    signature = signature
                )
            } else {
                val uri = Uri.parse(database.resolvedActiveFilePath())
                val bytes = readBytesFromUri(uri, "无法打开数据库文件")
                DatabaseSnapshot(bytes = bytes, etag = null, lastModified = null, signature = null)
            }
        } catch (t: Throwable) {
            throw normalizeError(t)
        }
    }

    private suspend fun writeDatabase(
        database: LocalKeePassDatabase,
        credentials: Credentials,
        keePassDatabase: KeePassDatabase,
        sourceEtag: String? = null,
        sourceLastModified: String? = null,
        beforeRemoteUpload: (suspend (LocalKeePassDatabase, KeePassDatabase) -> Unit)? = null
    ) {
        val bytes = encodeDatabase(keePassDatabase)
        if (ENABLE_POST_WRITE_DECODE_VERIFICATION) {
            decodeDatabase(bytes, credentials)
        }
        if (database.resolvedActiveStorageLocation() == KeePassStorageLocation.INTERNAL) {
            writeInternal(database, bytes)
        } else {
            writeExternal(database, bytes)
        }
        var resolvedSourceEtag = sourceEtag
        var resolvedSourceLastModified = sourceLastModified
        var resolvedDatabase = keePassDatabase
        if (database.isRemoteSource()) {
            beforeRemoteUpload?.invoke(database, resolvedDatabase)
            val syncOutcome = syncRemoteWorkingCopy(
                database = database,
                credentials = credentials,
                localDatabase = resolvedDatabase,
                bytes = bytes
            )
            if (syncOutcome != null) {
                resolvedSourceEtag = syncOutcome.writeResult.etag
                resolvedSourceLastModified = syncOutcome.writeResult.lastModified?.toString()
                resolvedDatabase = syncOutcome.finalDatabase
            } else {
                markRemoteWritePending(database, bytes)
                enqueueRemoteWorkingCopyUpload(database.id)
            }
        }
        val updatedSignature = currentSourceSignature(database)
        cacheLoadedDatabase(
            LoadedDatabase(
                database = database,
                credentials = credentials,
                keePassDatabase = resolvedDatabase,
                sourceEtag = resolvedSourceEtag,
                sourceLastModified = resolvedSourceLastModified,
                sourceSignature = updatedSignature
            )
        )
    }

    private fun keePassPendingChangeRepository(): KeePassPendingChangeRepository {
        return KeePassPendingChangeRepository(
            PasswordDatabase.getDatabase(context).keepassPendingChangeDao()
        )
    }

    private suspend fun enqueuePendingChangeSetsIfRemote(
        database: LocalKeePassDatabase,
        changeSets: List<KeePassChangeSet>
    ) {
        if (changeSets.isEmpty() || !database.isRemoteSource() || database.sourceId == null) {
            return
        }
        val remoteDb = PasswordDatabase.getDatabase(context)
        val syncState = remoteDb.keepassRemoteSyncStateDao().getState(database.id)
        val workingHashAtChange = runCatching {
            GoogleDriveKeePassSupport.sha256Hex(readDatabaseSnapshot(database).bytes)
        }.getOrNull()
        val baseSnapshot = KeePassPendingChangeBaseSnapshot(
            remoteVersionToken = syncState?.remoteVersionToken,
            remoteEtag = syncState?.remoteEtag,
            remoteLastModified = syncState?.remoteLastModified,
            baseHash = syncState?.baseHash,
            workingHashAtChange = workingHashAtChange
        )
        val repository = keePassPendingChangeRepository()
        changeSets.forEach { changeSet ->
            repository.enqueue(changeSet, baseSnapshot)
        }
    }

    private suspend fun syncRemoteWorkingCopy(
        database: LocalKeePassDatabase,
        credentials: Credentials,
        localDatabase: KeePassDatabase,
        bytes: ByteArray
    ): RemoteSyncOutcome? {
        if (!database.isRemoteSource() || database.sourceId == null) {
            return null
        }

        val remoteDb = PasswordDatabase.getDatabase(context)
        val syncService = RemoteKeePassSyncService(
            databaseDao = dao,
            remoteSourceDao = remoteDb.keepassRemoteSourceDao(),
            syncStateDao = remoteDb.keepassRemoteSyncStateDao()
        )
        val syncStateDao = remoteDb.keepassRemoteSyncStateDao()
        val workingHash = GoogleDriveKeePassSupport.sha256Hex(bytes)

        return try {
            when (database.sourceType) {
                KeePassDatabaseSourceType.REMOTE_WEBDAV -> {
                    val remoteSource = remoteDb.keepassRemoteSourceDao().getSourceById(database.sourceId)
                        ?: throw IllegalStateException("远端来源不存在")
                    val expectedRemoteVersion = syncStateDao.getState(database.id)?.let { state ->
                        state.remoteEtag ?: state.remoteVersionToken
                    }
                    val fileSource = WebDavKeePassSupport.createFileSource(remoteSource, securityManager)
                    val writeResult = fileSource.write(bytes, expectedVersion = expectedRemoteVersion)
                    val verifiedRemote = verifyRemoteKdbxWrite(
                        database = database,
                        fileSource = fileSource,
                        credentials = credentials,
                        expectedBytes = bytes,
                        sourceLabel = "foreground-webdav"
                    )
                    database.cacheCopyPath?.let { cachePath ->
                        writeInternalRelative(cachePath, verifiedRemote.bytes)
                    }
                    syncService.markSynchronized(
                        databaseId = database.id,
                        versionToken = writeResult.versionToken,
                        etag = writeResult.etag,
                        baseHash = verifiedRemote.hash,
                        workingHash = verifiedRemote.hash
                    )
                    RemoteSyncOutcome(
                        writeResult = writeResult,
                        finalBytes = verifiedRemote.bytes,
                        finalDatabase = verifiedRemote.database
                    )
                }
                KeePassDatabaseSourceType.REMOTE_ONEDRIVE -> {
                    val remoteSourceDao = remoteDb.keepassRemoteSourceDao()
                    val remoteSource = remoteSourceDao.getSourceById(database.sourceId)
                        ?: throw IllegalStateException("远端来源不存在")
                    val expectedRemoteVersion = syncStateDao.getState(database.id)?.let { state ->
                        state.remoteEtag ?: state.remoteVersionToken
                    }
                    val fileSource = OneDriveKeePassSupport.createFileSource(context, remoteSource)
                    val syncOutcome = try {
                        val writeResult = fileSource.write(bytes, expectedVersion = expectedRemoteVersion)
                        val verifiedRemote = verifyRemoteKdbxWrite(
                            database = database,
                            fileSource = fileSource,
                            credentials = credentials,
                            expectedBytes = bytes,
                            sourceLabel = "foreground-onedrive"
                        )
                        RemoteSyncOutcome(
                            writeResult = writeResult,
                            finalBytes = verifiedRemote.bytes,
                            finalDatabase = verifiedRemote.database
                        )
                    } catch (error: Exception) {
                        if (!isRemoteVersionConflict(error)) {
                            throw error
                        }
                        val remoteStat = runCatching { fileSource.stat() }.getOrDefault(FileSourceStat())
                        val currentRemoteVersion = remoteStat.etag ?: remoteStat.versionToken
                        if (currentRemoteVersion.isNullOrBlank()) {
                            throw error
                        }
                        val remoteBytes = fileSource.read()
                        val mergeResult = resolveRemoteConflictInternal(
                            database = database,
                            credentials = credentials,
                            localDatabase = localDatabase,
                            remoteBytes = remoteBytes
                        )
                        val retriedWrite = fileSource.write(
                            mergeResult.mergedBytes,
                            expectedVersion = currentRemoteVersion
                        )
                        val verifiedRemote = verifyRemoteKdbxWrite(
                            database = database,
                            fileSource = fileSource,
                            credentials = credentials,
                            expectedBytes = mergeResult.mergedBytes,
                            sourceLabel = "foreground-onedrive-merge"
                        )
                        database.workingCopyPath?.let { workingCopyPath ->
                            writeInternalRelative(workingCopyPath, verifiedRemote.bytes)
                        }
                        RemoteSyncOutcome(
                            writeResult = retriedWrite,
                            finalBytes = verifiedRemote.bytes,
                            finalDatabase = verifiedRemote.database,
                            conflictCopyCount = mergeResult.conflictCopyCount
                        )
                    }
                    if ((remoteSource.itemId.isNullOrBlank() || remoteSource.driveId.isNullOrBlank()) &&
                        (!syncOutcome.writeResult.remoteId.isNullOrBlank() || !syncOutcome.writeResult.driveId.isNullOrBlank())
                    ) {
                        remoteSourceDao.updateSource(
                            remoteSource.copy(
                                itemId = syncOutcome.writeResult.remoteId ?: remoteSource.itemId,
                                driveId = syncOutcome.writeResult.driveId ?: remoteSource.driveId,
                                updatedAt = System.currentTimeMillis()
                            )
                        )
                    }
                    database.cacheCopyPath?.let { cachePath ->
                        writeInternalRelative(cachePath, syncOutcome.finalBytes)
                    }
                    val finalWorkingHash = GoogleDriveKeePassSupport.sha256Hex(syncOutcome.finalBytes)
                    syncService.markSynchronized(
                        databaseId = database.id,
                        versionToken = syncOutcome.writeResult.versionToken,
                        etag = syncOutcome.writeResult.etag,
                        baseHash = finalWorkingHash,
                        workingHash = finalWorkingHash
                    )
                    syncOutcome
                }
                KeePassDatabaseSourceType.REMOTE_GOOGLE_DRIVE -> {
                    val remoteSource = remoteDb.keepassRemoteSourceDao().getSourceById(database.sourceId)
                        ?: throw IllegalStateException("远端来源不存在")
                    val fileSource = GoogleDriveKeePassSupport.createFileSource(context, remoteSource)
                    val writeResult = fileSource.write(bytes)
                    val verifiedRemote = verifyRemoteKdbxWrite(
                        database = database,
                        fileSource = fileSource,
                        credentials = credentials,
                        expectedBytes = bytes,
                        sourceLabel = "foreground-googledrive"
                    )
                    database.cacheCopyPath?.let { cachePath ->
                        writeInternalRelative(cachePath, verifiedRemote.bytes)
                    }
                    syncService.markSynchronized(
                        databaseId = database.id,
                        versionToken = writeResult.versionToken,
                        etag = writeResult.etag,
                        baseHash = verifiedRemote.hash,
                        workingHash = verifiedRemote.hash
                    )
                    RemoteSyncOutcome(
                        writeResult = writeResult,
                        finalBytes = verifiedRemote.bytes,
                        finalDatabase = verifiedRemote.database
                    )
                }
                else -> null
            }
        } catch (error: Exception) {
            syncService.markLocalChanges(database.id, workingHash)
            syncService.markSyncFailure(
                databaseId = database.id,
                failureCode = "REMOTE_WRITE_FAILED",
                failureMessage = error.message ?: "远端同步失败"
            )
            Log.w(TAG, "Remote working copy sync failed for db=${database.id}", error)
            null
        }
    }

    private suspend fun markRemoteWritePending(
        database: LocalKeePassDatabase,
        bytes: ByteArray
    ) {
        if (!database.isRemoteSource() || database.sourceId == null) {
            return
        }
        val remoteDb = PasswordDatabase.getDatabase(context)
        val syncService = RemoteKeePassSyncService(
            databaseDao = dao,
            remoteSourceDao = remoteDb.keepassRemoteSourceDao(),
            syncStateDao = remoteDb.keepassRemoteSyncStateDao()
        )
        syncService.markLocalChanges(
            databaseId = database.id,
            workingHash = GoogleDriveKeePassSupport.sha256Hex(bytes)
        )
    }

    private fun enqueueRemoteWorkingCopyUpload(databaseId: Long) {
        val taskId = SyncDiagnostics.nextTaskId("kp-upload")
        val target = "keepass:$databaseId"
        val trigger = "REMOTE_WORKING_COPY_UPLOAD"
        SyncDiagnostics.queued(
            taskId = taskId,
            target = target,
            trigger = trigger,
            detail = "worker=true"
        )
        val startedAt = SyncDiagnostics.start(
            taskId = taskId,
            target = target,
            trigger = trigger,
            detail = "worker=true"
        )
        KeePassRemoteUploadWorker.enqueue(context, databaseId)
        SyncDiagnostics.success(
            taskId = taskId,
            target = target,
            trigger = trigger,
            startedAt = startedAt,
            detail = "worker_enqueued=true"
        )
    }

    suspend fun flushPendingRemoteUpload(databaseId: Long): Boolean = withContext(Dispatchers.IO) {
        val database = dao.getDatabaseById(databaseId)
        if (database == null || !database.isRemoteSource() || database.sourceId == null) {
            return@withContext false
        }
        val workingBytes = readRemoteWorkingCopyBytes(database) ?: return@withContext false
        uploadRemoteWorkingCopyIfCurrent(
            databaseId = databaseId,
            expectedHash = GoogleDriveKeePassSupport.sha256Hex(workingBytes)
        )
        true
    }

    private suspend fun uploadRemoteWorkingCopyIfCurrent(
        databaseId: Long,
        expectedHash: String
    ) {
        remoteUploadMutex(databaseId).withLock {
            uploadRemoteWorkingCopyIfCurrentLocked(databaseId, expectedHash)
        }
    }

    private suspend fun uploadRemoteWorkingCopyIfCurrentLocked(
        databaseId: Long,
        expectedHash: String
    ) {
        val remoteDb = PasswordDatabase.getDatabase(context)
        val remoteSourceDao = remoteDb.keepassRemoteSourceDao()
        val syncStateDao = remoteDb.keepassRemoteSyncStateDao()
        val syncService = RemoteKeePassSyncService(
            databaseDao = dao,
            remoteSourceDao = remoteSourceDao,
            syncStateDao = syncStateDao
        )
        val database = dao.getDatabaseById(databaseId)
        if (database == null || !database.isRemoteSource() || database.sourceId == null) {
            return
        }
        val workingBytes = readRemoteWorkingCopyBytes(database) ?: return
        val workingHash = GoogleDriveKeePassSupport.sha256Hex(workingBytes)
        val syncState = syncStateDao.getState(databaseId)
        if (syncState?.hasLocalChanges == false && syncState.workingHash == workingHash) {
            return
        }
        if (syncState?.workingHash != null && syncState.workingHash != expectedHash && syncState.workingHash != workingHash) {
            return
        }

        val pendingRepository = keePassPendingChangeRepository()
        val pendingPlan = KeePassPendingFlushPlanner(pendingRepository).prepare(databaseId)
        pendingPlan.blocked.forEach { blocked ->
            pendingRepository.markBlocked(
                id = blocked.pendingId,
                reason = "${blocked.reason}: ${blocked.message}"
            )
        }
        pendingPlan.ready.forEach { item ->
            pendingRepository.markInProgress(item.pendingId)
        }

        var conflictFileSource: KeePassFileSource? = null
        try {
            syncService.markUploadInProgress(databaseId, workingHash)
            val remoteSource = remoteSourceDao.getSourceById(database.sourceId)
                ?: throw IllegalStateException("远端来源不存在")
            val fileSource = createRemoteFileSource(database, remoteSource)
            conflictFileSource = fileSource
            val credentials = loadDatabase(databaseId).credentials
            val expectedRemoteVersion = when (database.sourceType) {
                KeePassDatabaseSourceType.REMOTE_ONEDRIVE,
                KeePassDatabaseSourceType.REMOTE_GOOGLE_DRIVE,
                KeePassDatabaseSourceType.REMOTE_WEBDAV -> {
                    syncStateDao.getState(databaseId)?.let { state ->
                        state.remoteEtag ?: state.remoteVersionToken
                    }
                }
                else -> null
            }
            val writeResult = fileSource.write(workingBytes, expectedVersion = expectedRemoteVersion)
            val verifiedRemote = verifyRemoteKdbxWrite(
                database = database,
                fileSource = fileSource,
                credentials = credentials,
                expectedBytes = workingBytes,
                sourceLabel = "worker-upload"
            )
            updateOneDriveRemoteSourceBindingIfNeeded(
                database = database,
                writeResult = writeResult
            )
            database.cacheCopyPath?.let { cachePath ->
                writeInternalRelative(cachePath, verifiedRemote.bytes)
            }

            val latestBytes = readRemoteWorkingCopyBytes(database)
            val latestHash = latestBytes?.let { GoogleDriveKeePassSupport.sha256Hex(it) }
            if (latestHash == null || latestHash == workingHash) {
                syncService.markSynchronized(
                    databaseId = database.id,
                    versionToken = writeResult.versionToken,
                    etag = writeResult.etag,
                    baseHash = verifiedRemote.hash,
                    workingHash = verifiedRemote.hash
                )
                pendingPlan.ready.forEach { item ->
                    pendingRepository.markCompleted(item.pendingId)
                }
                pendingPlan.blocked.forEach { item ->
                    pendingRepository.markCompleted(item.pendingId)
                }
            } else {
                syncService.markUploadedButLocalChanged(
                    databaseId = database.id,
                    versionToken = writeResult.versionToken,
                    etag = writeResult.etag,
                    baseHash = verifiedRemote.hash,
                    workingHash = latestHash
                )
                pendingPlan.ready.forEach { item ->
                    pendingRepository.markCompleted(item.pendingId)
                }
                pendingPlan.blocked.forEach { item ->
                    pendingRepository.markCompleted(item.pendingId)
                }
                enqueueRemoteWorkingCopyUpload(database.id)
            }
        } catch (error: Exception) {
            val latestHash = readRemoteWorkingCopyBytes(database)
                ?.let { GoogleDriveKeePassSupport.sha256Hex(it) }
                ?: workingHash
            val fileSource = conflictFileSource
            var rebaseFailure: Throwable? = null
            if (isRemoteVersionConflict(error) && fileSource != null) {
                val rebaseResult = runCatching {
                    rebasePendingChangesOntoLatestRemote(
                        database = database,
                        fileSource = fileSource,
                        syncService = syncService,
                        pendingPlan = pendingPlan,
                        originalWorkingHash = workingHash
                    )
                }.onFailure { failure ->
                    rebaseFailure = failure
                }.getOrNull()
                if (rebaseResult != null) {
                    pendingPlan.ready.forEach { item ->
                        pendingRepository.markCompleted(item.pendingId)
                    }
                    val currentWorkingBytes = readRemoteWorkingCopyBytes(database)
                    val currentWorkingHash = currentWorkingBytes
                        ?.let { GoogleDriveKeePassSupport.sha256Hex(it) }
                        ?: rebaseResult.rebasedHash
                    if (currentWorkingHash == rebaseResult.rebasedHash) {
                        syncService.markSynchronized(
                            databaseId = database.id,
                            versionToken = rebaseResult.writeResult.versionToken,
                            etag = rebaseResult.writeResult.etag,
                            baseHash = rebaseResult.rebasedHash,
                            workingHash = rebaseResult.rebasedHash
                        )
                    } else {
                        syncService.markUploadedButLocalChanged(
                            databaseId = database.id,
                            versionToken = rebaseResult.writeResult.versionToken,
                            etag = rebaseResult.writeResult.etag,
                            baseHash = rebaseResult.rebasedHash,
                            workingHash = currentWorkingHash
                        )
                        enqueueRemoteWorkingCopyUpload(database.id)
                    }
                    return
                }
                syncService.markConflict(
                    databaseId = database.id,
                    workingHash = latestHash,
                    failureMessage = rebaseFailure?.message
                        ?: error.message
                        ?: "远端文件已变化，请先同步"
                )
            } else {
                syncService.markLocalChanges(database.id, latestHash)
                syncService.markSyncFailure(
                    databaseId = database.id,
                    failureCode = "REMOTE_BACKGROUND_UPLOAD_FAILED",
                    failureMessage = error.message ?: "远端后台同步失败"
                )
            }
            pendingPlan.ready.forEach { item ->
                pendingRepository.markFailed(
                    id = item.pendingId,
                    error = rebaseFailure ?: error,
                    retryDelayMillis = PENDING_CHANGE_UPLOAD_RETRY_DELAY_MILLIS
                )
            }
            throw error
        }
    }

    private data class KeePassRemoteRebaseResult(
        val writeResult: FileSourceWriteResult,
        val rebasedHash: String
    )

    private suspend fun rebasePendingChangesOntoLatestRemote(
        database: LocalKeePassDatabase,
        fileSource: KeePassFileSource,
        syncService: RemoteKeePassSyncService,
        pendingPlan: KeePassPendingFlushPlan,
        originalWorkingHash: String
    ): KeePassRemoteRebaseResult? {
        if (!pendingPlan.hasReadyChanges) {
            return null
        }
        KeePassRemoteRebase.requireNoBlockedChanges(pendingPlan)
        val remoteStat = fileSource.stat()
        val currentRemoteVersion = remoteStat.etag ?: remoteStat.versionToken
        if (currentRemoteVersion.isNullOrBlank()) {
            return null
        }

        syncService.markDownloading(database.id, originalWorkingHash)
        val credentials = loadDatabase(database.id).credentials
        val remoteBytes = fileSource.read()
        val remoteDatabase = decodeDatabase(
            bytes = remoteBytes,
            credentials = credentials,
            sourceName = "databaseId=${database.id}:pending-rebase"
        )
        val rebaseResult = KeePassRemoteRebase.applyReadyChanges(remoteDatabase, pendingPlan)
            ?: return null
        val rebasedBytes = encodeDatabase(rebaseResult.updatedDatabase)
        val writeResult = fileSource.write(
            bytes = rebasedBytes,
            expectedVersion = currentRemoteVersion
        )
        val verifiedRemote = verifyRemoteKdbxWrite(
            database = database,
            fileSource = fileSource,
            credentials = credentials,
            expectedBytes = rebasedBytes,
            sourceLabel = "pending-rebase"
        )
        updateOneDriveRemoteSourceBindingIfNeeded(
            database = database,
            writeResult = writeResult
        )

        database.cacheCopyPath?.let { cachePath ->
            writeInternalRelative(cachePath, verifiedRemote.bytes)
        }
        val latestWorkingHash = readRemoteWorkingCopyBytes(database)
            ?.let { GoogleDriveKeePassSupport.sha256Hex(it) }
        if (latestWorkingHash == null || latestWorkingHash == originalWorkingHash) {
            database.workingCopyPath?.let { workingCopyPath ->
                writeInternalRelative(workingCopyPath, verifiedRemote.bytes)
            }
        }

        return KeePassRemoteRebaseResult(
            writeResult = writeResult,
            rebasedHash = verifiedRemote.hash
        )
    }

    private suspend fun createRemoteFileSource(
        database: LocalKeePassDatabase,
        remoteSource: takagi.ru.monica.data.KeepassRemoteSource
    ): KeePassFileSource = when (database.sourceType) {
        KeePassDatabaseSourceType.REMOTE_WEBDAV -> WebDavKeePassSupport.createFileSource(remoteSource, securityManager)
        KeePassDatabaseSourceType.REMOTE_ONEDRIVE -> OneDriveKeePassSupport.createFileSource(context, remoteSource)
        KeePassDatabaseSourceType.REMOTE_GOOGLE_DRIVE -> GoogleDriveKeePassSupport.createFileSource(context, remoteSource)
        else -> throw IllegalStateException("不支持的远端来源类型: ${database.sourceType}")
    }

    private suspend fun updateOneDriveRemoteSourceBindingIfNeeded(
        database: LocalKeePassDatabase,
        writeResult: FileSourceWriteResult
    ) {
        if (database.sourceType != KeePassDatabaseSourceType.REMOTE_ONEDRIVE || database.sourceId == null) {
            return
        }
        if (writeResult.remoteId.isNullOrBlank() && writeResult.driveId.isNullOrBlank()) {
            return
        }
        val remoteDb = PasswordDatabase.getDatabase(context)
        val remoteSourceDao = remoteDb.keepassRemoteSourceDao()
        val remoteSource = remoteSourceDao.getSourceById(database.sourceId) ?: return
        if (!remoteSource.itemId.isNullOrBlank() && !remoteSource.driveId.isNullOrBlank()) {
            return
        }
        remoteSourceDao.updateSource(
            remoteSource.copy(
                itemId = writeResult.remoteId ?: remoteSource.itemId,
                driveId = writeResult.driveId ?: remoteSource.driveId,
                updatedAt = System.currentTimeMillis()
            )
        )
    }

    private fun readRemoteWorkingCopyBytes(database: LocalKeePassDatabase): ByteArray? {
        val workingCopyPath = database.workingCopyPath?.takeIf { it.isNotBlank() } ?: return null
        val file = File(context.filesDir, workingCopyPath)
        return if (file.exists()) file.readBytes() else null
    }

    internal suspend fun verifyRemoteKdbxWrite(
        databaseId: Long,
        fileSource: KeePassFileSource,
        expectedBytes: ByteArray,
        sourceLabel: String
    ): RemoteKdbxVerification = withContext(Dispatchers.IO) {
        val database = dao.getDatabaseById(databaseId)
            ?: throw IllegalStateException("数据库不存在")
        val credentials = loadDatabase(databaseId).credentials
        verifyRemoteKdbxWrite(
            database = database,
            fileSource = fileSource,
            credentials = credentials,
            expectedBytes = expectedBytes,
            sourceLabel = sourceLabel
        )
    }

    private suspend fun verifyRemoteKdbxWrite(
        database: LocalKeePassDatabase,
        fileSource: KeePassFileSource,
        credentials: Credentials,
        expectedBytes: ByteArray,
        sourceLabel: String
    ): RemoteKdbxVerification {
        val expectedHash = GoogleDriveKeePassSupport.sha256Hex(expectedBytes)
        return try {
            val remoteBytes = fileSource.read()
            val remoteHash = GoogleDriveKeePassSupport.sha256Hex(remoteBytes)
            if (remoteHash != expectedHash) {
                throw IOException(
                    "远端写入校验失败：上传后内容不一致 (${remoteBytes.size}/${expectedBytes.size})"
                )
            }
            val decoded = decodeDatabase(
                bytes = remoteBytes,
                credentials = credentials,
                sourceName = "databaseId=${database.id}:$sourceLabel"
            )
            Log.i(
                TAG,
                "Remote KDBX write verified db=${database.id} source=${database.sourceType} label=$sourceLabel bytes=${remoteBytes.size} hash=${remoteHash.take(12)}"
            )
            RemoteKdbxVerification(
                bytes = remoteBytes,
                database = decoded,
                hash = remoteHash
            )
        } catch (error: Exception) {
            Log.e(
                TAG,
                "Remote KDBX write verification failed db=${database.id} source=${database.sourceType} label=$sourceLabel expectedBytes=${expectedBytes.size} expectedHash=${expectedHash.take(12)}",
                error
            )
            throw error
        }
    }

    private suspend fun getCachedLoadedDatabase(databaseId: Long): LoadedDatabase? {
        if (consumeProcessCacheInvalidation(databaseId)) {
            invalidateLoadedDatabaseCache(databaseId)
            return null
        }

        val now = System.currentTimeMillis()
        val cached = synchronized(loadedDatabaseCache) { loadedDatabaseCache[databaseId] } ?: return null

        val latestDatabase = runCatching { dao.getDatabaseById(databaseId) }.getOrNull() ?: return null
        val previous = cached.loaded.database
        val configChanged =
            latestDatabase.filePath != previous.filePath ||
                latestDatabase.storageLocation != previous.storageLocation ||
                latestDatabase.sourceType != previous.sourceType ||
                latestDatabase.sourceId != previous.sourceId ||
                latestDatabase.workingCopyPath != previous.workingCopyPath ||
                latestDatabase.cacheCopyPath != previous.cacheCopyPath ||
                latestDatabase.keyFileUri != previous.keyFileUri ||
                latestDatabase.encryptedPassword != previous.encryptedPassword
        if (configChanged) {
            invalidateLoadedDatabaseCache(databaseId)
            return null
        }

        // Internal storage: keep cache warm as long as underlying file signature is unchanged.
        val cachedSignature = cached.loaded.sourceSignature
        if (cachedSignature != null) {
            val currentSignature = currentSourceSignature(latestDatabase)
            if (currentSignature == null || currentSignature != cachedSignature) {
                invalidateLoadedDatabaseCache(databaseId)
                return null
            }
        } else if (now - cached.cachedAtMs > UNKNOWN_SOURCE_CACHE_TTL_MS) {
            // External URI source does not have cheap signature checks, so keep a bounded cache window.
            invalidateLoadedDatabaseCache(databaseId)
            return null
        }

        return cached.loaded.copy(database = latestDatabase)
    }

    private fun currentSourceSignature(database: LocalKeePassDatabase): DatabaseSourceSignature? {
        if (database.resolvedActiveStorageLocation() != KeePassStorageLocation.INTERNAL) return null
        val file = File(context.filesDir, database.resolvedActiveFilePath())
        if (!file.exists()) return null
        return DatabaseSourceSignature(
            sizeBytes = file.length(),
            lastModifiedEpochMs = file.lastModified()
        )
    }

    private fun cacheLoadedDatabase(loaded: LoadedDatabase) {
        synchronized(loadedDatabaseCache) {
            loadedDatabaseCache[loaded.database.id] = CachedLoadedDatabase(
                loaded = loaded,
                cachedAtMs = System.currentTimeMillis()
            )
            trimLoadedDatabaseCacheLocked()
        }
    }

    private fun invalidateLoadedDatabaseCache(databaseId: Long) {
        synchronized(loadedDatabaseCache) {
            loadedDatabaseCache.remove(databaseId)
        }
    }

    private fun encodeDatabase(keePassDatabase: KeePassDatabase): ByteArray {
        return ByteArrayOutputStream().use { output ->
            keePassDatabase.encode(output, cipherProviders = KeePassCodecSupport.cipherProviders)
            output.toByteArray()
        }
    }

    private fun writeInternal(database: LocalKeePassDatabase, bytes: ByteArray) {
        try {
            val file = File(context.filesDir, database.resolvedActiveFilePath())
            writeInternalFile(file, bytes)
        } catch (t: Throwable) {
            throw normalizeError(t)
        }
    }

    private fun writeInternalRelative(relativePath: String, bytes: ByteArray) {
        try {
            val file = File(context.filesDir, relativePath)
            writeInternalFile(file, bytes)
        } catch (t: Throwable) {
            throw normalizeError(t)
        }
    }

    private fun writeInternalFile(file: File, bytes: ByteArray) {
        val parent = file.parentFile ?: throw IOException("无效的文件路径")
        if (!parent.exists()) parent.mkdirs()
        val tempFile = File(parent, "${file.name}.tmp")
        val backupFile = File(parent, "${file.name}.bak")
        FileOutputStream(tempFile).use {
            it.write(bytes)
            it.flush()
            it.fd.sync()
        }
        if (file.exists()) {
            if (backupFile.exists()) backupFile.delete()
            if (!file.renameTo(backupFile)) {
                backupFile.delete()
            }
        }
        val renamed = tempFile.renameTo(file)
        if (!renamed) {
            FileOutputStream(file).use {
                it.write(bytes)
                it.flush()
                it.fd.sync()
            }
            tempFile.delete()
        }
        if (backupFile.exists()) backupFile.delete()
    }

    private fun writeExternal(database: LocalKeePassDatabase, bytes: ByteArray) {
        val uri = Uri.parse(database.resolvedActiveFilePath())
        val originalBytes = runCatching { readDatabaseBytes(database) }.getOrNull()
        try {
            writeExternalBytes(uri, bytes)
        } catch (e: Exception) {
            if (originalBytes != null) {
                runCatching {
                    writeExternalBytes(uri, originalBytes)
                }
            }
            throw normalizeError(e)
        }
    }

    private fun writeExternalBytes(uri: Uri, bytes: ByteArray) {
        openExternalOutputStream(uri)?.use { output ->
            output.write(bytes)
            output.flush()
        } ?: throw IOException("无法写入数据库文件")
    }

    private fun openExternalOutputStream(uri: Uri) =
        try {
            context.contentResolver.openOutputStream(uri, "wt")
        } catch (e: FileNotFoundException) {
            Log.w(TAG, "openOutputStream wt failed, retry with rwt", e)
            context.contentResolver.openOutputStream(uri, "rwt")
        }

    suspend fun resolveRemoteConflict(
        databaseId: Long,
        remoteBytes: ByteArray
    ): Result<KeePassConflictResolutionResult> = withContext(Dispatchers.IO) {
        try {
            val loaded = loadDatabase(databaseId)
            val resolved = resolveRemoteConflictInternal(
                database = loaded.database,
                credentials = loaded.credentials,
                localDatabase = loaded.keePassDatabase,
                remoteBytes = remoteBytes
            )
            Result.success(
                KeePassConflictResolutionResult(
                    mergedBytes = resolved.mergedBytes,
                    conflictCopyCount = resolved.conflictCopyCount
                )
            )
        } catch (error: Exception) {
            Result.failure(normalizeError(error))
        }
    }

    private fun isRemoteVersionConflict(error: Throwable): Boolean {
        return error.message?.contains("远端文件已变化", ignoreCase = true) == true
    }

    private suspend fun resolveRemoteConflictInternal(
        database: LocalKeePassDatabase,
        credentials: Credentials,
        localDatabase: KeePassDatabase,
        remoteBytes: ByteArray
    ): InternalConflictResolutionResult {
        val cachePath = database.cacheCopyPath
            ?: throw IllegalStateException("缺少用于冲突处理的缓存副本")
        val cacheFile = File(context.filesDir, cachePath)
        if (!cacheFile.exists()) {
            throw IllegalStateException("缺少用于冲突处理的缓存副本")
        }

        val remoteDatabase = decodeDatabase(
            bytes = remoteBytes,
            credentials = credentials,
            sourceName = "databaseId=${database.id}:remote-conflict"
        )
        val baseDatabase = decodeDatabase(
            bytes = cacheFile.readBytes(),
            credentials = credentials,
            sourceName = "databaseId=${database.id}:base-conflict",
            logFailure = false
        )
        val mergedResult = mergeDatabasesForConflictResolution(
            baseDatabase = baseDatabase,
            localDatabase = localDatabase,
            remoteDatabase = remoteDatabase
        )
        return InternalConflictResolutionResult(
            mergedDatabase = mergedResult.first,
            mergedBytes = encodeDatabase(mergedResult.first),
            conflictCopyCount = mergedResult.second
        )
    }

    private fun mergeDatabasesForConflictResolution(
        baseDatabase: KeePassDatabase,
        localDatabase: KeePassDatabase,
        remoteDatabase: KeePassDatabase
    ): Pair<KeePassDatabase, Int> {
        val baseEntries = buildConflictEntrySnapshotMap(baseDatabase)
        val localEntries = buildConflictEntrySnapshotMap(localDatabase)
        val remoteEntries = buildConflictEntrySnapshotMap(remoteDatabase)

        var mergedRoot = remoteDatabase.content.group
        var conflictCopyCount = 0

        val candidateUuids = (baseEntries.keys + localEntries.keys).toSet()
        candidateUuids.forEach { uuid ->
            val baseSnapshot = baseEntries[uuid]
            val localSnapshot = localEntries[uuid]
            val remoteSnapshot = remoteEntries[uuid]

            val localChanged = hasConflictSnapshotChanged(baseSnapshot, localSnapshot)
            if (!localChanged) {
                return@forEach
            }

            val remoteChanged = hasConflictSnapshotChanged(baseSnapshot, remoteSnapshot)
            if (localSnapshot == null) {
                if (!remoteChanged) {
                    mergedRoot = removeEntryInGroup(mergedRoot) { it.uuid == uuid }.first
                }
                return@forEach
            }

            if (remoteChanged && remoteSnapshot != null && hasConflictSnapshotChanged(localSnapshot, remoteSnapshot)) {
                val conflictCopy = buildRemoteConflictCopy(remoteSnapshot.entry)
                if (!containsConflictCopyInGroupPath(
                        rootGroup = mergedRoot,
                        groupPath = remoteSnapshot.groupPath,
                        candidate = conflictCopy
                    )
                ) {
                    mergedRoot = addEntryToGroupPath(
                        rootGroup = mergedRoot,
                        groupPath = remoteSnapshot.groupPath,
                        entry = conflictCopy
                    )
                    conflictCopyCount++
                }
            }

            val withoutExisting = removeEntryInGroup(mergedRoot) { it.uuid == uuid }.first
            mergedRoot = addEntryToGroupPath(
                rootGroup = withoutExisting,
                groupPath = localSnapshot.groupPath,
                entry = localSnapshot.entry
            )
        }

        return remoteDatabase.modifyParentGroup { mergedRoot } to conflictCopyCount
    }

    private fun buildConflictEntrySnapshotMap(
        keePassDatabase: KeePassDatabase
    ): Map<UUID, RemoteConflictEntrySnapshot> {
        return collectEntryContexts(keePassDatabase)
            .first
            .associate { context ->
                context.entry.uuid to RemoteConflictEntrySnapshot(
                    entry = context.entry,
                    groupPath = context.groupPath,
                    signature = buildConflictEntrySignature(context.entry)
                )
            }
    }

    private fun hasConflictSnapshotChanged(
        base: RemoteConflictEntrySnapshot?,
        candidate: RemoteConflictEntrySnapshot?
    ): Boolean {
        return when {
            base == null -> candidate != null
            candidate == null -> true
            else -> base.groupPath != candidate.groupPath || base.signature != candidate.signature
        }
    }

    private fun buildRemoteConflictCopy(entry: Entry): Entry {
        val currentTitle = getFieldValue(entry, "Title").ifBlank { "Untitled" }
        val normalizedConflictTitle = normalizeRemoteConflictTitle(currentTitle)
        var hasConflictMarker = false
        val updatedFields = entry.fields
            .mapNotNull { (key, value) ->
                if (key == FIELD_MONICA_LOCAL_ID || key == FIELD_MONICA_ITEM_ID) {
                    return@mapNotNull null
                }
                if (key == FIELD_MONICA_CONFLICT_COPY) {
                    hasConflictMarker = true
                    return@mapNotNull key to EntryValue.Plain("true")
                }
                if (key == "Title") {
                    key to EntryValue.Plain(normalizedConflictTitle)
                } else {
                    key to value
                }
            }
            .toMutableList()
        if (updatedFields.none { it.first == "Title" }) {
            updatedFields += "Title" to EntryValue.Plain(normalizedConflictTitle)
        }
        if (!hasConflictMarker) {
            updatedFields += FIELD_MONICA_CONFLICT_COPY to EntryValue.Plain("true")
        }
        return entry.copy(
            uuid = UUID.randomUUID(),
            fields = EntryFields.of(*updatedFields.toTypedArray())
        )
    }

    private fun containsConflictCopyInGroupPath(
        rootGroup: Group,
        groupPath: String?,
        candidate: Entry
    ): Boolean {
        val targetSignature = buildConflictEntrySignature(candidate)
        return collectEntriesInGroupPath(rootGroup, groupPath)
            .any { existing -> buildConflictEntrySignature(existing) == targetSignature }
    }

    private fun collectEntriesInGroupPath(
        rootGroup: Group,
        groupPath: String?
    ): List<Entry> {
        val collected = mutableListOf<Entry>()
        fun walk(group: Group, currentPath: String?) {
            if (currentPath == groupPath) {
                collected += group.entries
            }
            group.groups.forEach { child ->
                val nextPath = buildKeePassPathKey(currentPath, child.name)
                walk(child, nextPath)
            }
        }
        walk(rootGroup, null)
        return collected
    }

    private fun buildConflictEntrySignature(entry: Entry): String {
        return KeePassEntryFingerprint.build(entry)
    }

    private fun normalizeRemoteConflictTitle(title: String): String {
        val normalized = normalizeRemoteConflictTitleForSignature(title)
        val hasSuffix = normalized.endsWith(" $REMOTE_CONFLICT_TITLE_SUFFIX") ||
            normalized == REMOTE_CONFLICT_TITLE_SUFFIX
        if (hasSuffix) {
            return normalized
        }
        val baseTitle = normalized.ifBlank { "Untitled" }
        return "$baseTitle $REMOTE_CONFLICT_TITLE_SUFFIX"
    }

    private fun normalizeRemoteConflictTitleForSignature(title: String): String {
        val suffixPattern = Regex("\\s*\\Q$REMOTE_CONFLICT_TITLE_SUFFIX\\E(?:\\s*\\Q$REMOTE_CONFLICT_TITLE_SUFFIX\\E)*\\s*$")
        val baseTitle = title
            .replace(suffixPattern, "")
            .trim()
        val hadSuffix = suffixPattern.containsMatchIn(title)
        return if (hadSuffix) {
            val normalizedBase = baseTitle.ifBlank { "Untitled" }
            "$normalizedBase $REMOTE_CONFLICT_TITLE_SUFFIX"
        } else {
            baseTitle
        }
    }

    private fun normalizeError(throwable: Throwable): Throwable {
        if (throwable is KeePassOperationException || throwable is IllegalArgumentException) {
            return throwable
        }
        return throwable.toKeePassOperationException()
    }
}
