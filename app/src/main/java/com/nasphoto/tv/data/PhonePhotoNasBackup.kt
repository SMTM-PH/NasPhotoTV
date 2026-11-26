/*
 * Copyright 2026 SMTM-PH and NasPhotoTV contributors
 * SPDX-License-Identifier: Apache-2.0
 */

package com.nasphoto.tv.data

import android.content.Context
import com.nasphoto.tv.data.fn.FnOsPhotoImportUncertain
import java.io.File
import java.io.IOException
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.channels.Channel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.coroutines.withContext

internal data class PhoneNasBackupState(
    val savedCount: Int = 0,
    val pendingCount: Int = 0,
    val verificationCount: Int = 0,
    val saving: Boolean = false,
    val retryRequired: Boolean = false,
)

/** 每个 NAS 账号有自己的待保存照片。注销或关机后仍可继续保存。 */
internal class PhonePhotoNasBackup(private val context: Context, val ownerKey: String) {
    private val wake = Channel<Unit>(Channel.CONFLATED)
    private val worker = Mutex()
    private val mutableState = MutableStateFlow(PhoneNasBackupState())
    val state = mutableState.asStateFlow()

    fun requestSave() {
        wake.trySend(Unit)
    }

    suspend fun run(
        checkImported: suspend (File) -> Boolean,
        upload: suspend (File, suspend () -> Unit) -> Unit,
    ) = withContext(Dispatchers.IO) {
        worker.withLock {
            requestSave()
            while (true) {
                wake.receive()
                val photos = PhonePhotoFiles.nasPhotos(context, ownerKey)
                val pending = photos.filterNot { PhonePhotoFiles.nasReceipt(it).isFile }
                publish(saving = pending.isNotEmpty())
                var retryRequired = false
                for (photo in pending) {
                    if (!photo.isFile) continue
                    val importing = PhonePhotoFiles.nasImportMarker(photo)
                    val checking = importing.isFile
                    try {
                        if (checking) {
                            if (!checkImported(photo)) continue
                        } else {
                            upload(photo) {
                                if (!photo.isFile) throw IOException("照片已从电视移除")
                                importing.createNewFile()
                                Unit
                            }
                        }
                    } catch (cancelled: CancellationException) {
                        throw cancelled
                    } catch (_: FnOsPhotoImportUncertain) {
                        continue
                    } catch (_: Exception) {
                        if (!checking) importing.delete()
                        retryRequired = true
                        break
                    }
                    // 如果写入收据失败，保留导入标记，下次只核对 NAS 上的文件。
                    try {
                        if (photo.isFile) PhonePhotoFiles.nasReceipt(photo).createNewFile()
                        importing.delete()
                    } catch (_: IOException) {
                        retryRequired = true
                    }
                    publish(saving = true)
                }
                publish(retryRequired = retryRequired)
            }
        }
    }

    private fun publish(saving: Boolean = false, retryRequired: Boolean = false) {
        val photos = PhonePhotoFiles.nasPhotos(context, ownerKey)
        val pending = photos.filterNot { PhonePhotoFiles.nasReceipt(it).isFile }
        mutableState.value = PhoneNasBackupState(
            savedCount = photos.size - pending.size,
            pendingCount = pending.size,
            verificationCount = pending.count { PhonePhotoFiles.nasImportMarker(it).isFile },
            saving = saving && pending.isNotEmpty(),
            retryRequired = retryRequired && pending.isNotEmpty(),
        )
    }
}
