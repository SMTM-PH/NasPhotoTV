/*
 * Copyright 2026 SMTM-PH and NasPhotoTV contributors
 * SPDX-License-Identifier: Apache-2.0
 */

package com.nasphoto.tv.data.fn

/** The remote import might have committed before its response became unavailable. */
internal class FnOsPhotoImportUncertain(cause: Throwable? = null) :
    Exception("飞牛入库结果暂时无法确认", cause)
