/*
 * Copyright 2026 SMTM-PH and NasPhotoTV contributors
 * SPDX-License-Identifier: Apache-2.0
 */

package com.nasphoto.tv.data

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

/** 在线更新的纯逻辑回归：版本比较、GitHub 响应解析、签名匹配选包、备用地址拼接。 */
class AppUpdateTest {

    @Test
    fun versionComparisonIgnoresLeadingVAndComparesNumerically() {
        assertTrue(isNewerVersion("v1.2.7", "1.2.6"))
        assertTrue(isNewerVersion("1.3.0", "1.2.6"))
        assertTrue(isNewerVersion("v2.0.0", "1.9.9"))
        assertFalse(isNewerVersion("v1.2.6", "1.2.6"))
        assertFalse(isNewerVersion("1.2.5", "1.2.6"))
        assertTrue(isNewerVersion("v1.2.10", "v1.2.9")) // 10 > 9，不能按字符串比较
        assertFalse(isNewerVersion("", "1.2.6"))
        assertFalse(isNewerVersion("garbage", "1.2.6"))
    }

    @Test
    fun parsesGitHubLatestReleaseJson() {
        val json = """{
            "tag_name":"v1.2.7",
            "name":"NAS Photo TV 1.2.7",
            "body":"## 更新\n- 在线更新",
            "html_url":"https://github.com/SMTM-PH/NasPhotoTV/releases/tag/v1.2.7",
            "assets":[
              {"name":"NasPhotoTV-1.2.7-release.apk","size":14852521,
               "browser_download_url":"https://github.com/SMTM-PH/NasPhotoTV/releases/download/v1.2.7/NasPhotoTV-1.2.7-release.apk"},
              {"name":"NasPhotoTV-1.2.7-debug.apk","size":24404911,
               "browser_download_url":"https://github.com/SMTM-PH/NasPhotoTV/releases/download/v1.2.7/NasPhotoTV-1.2.7-debug.apk"},
              {"name":"notes.txt","browser_download_url":""}
            ]
        }"""
        val info = parseRelease(json)!!
        assertEquals("v1.2.7", info.version)
        assertEquals(2, info.assets.size)
        assertEquals("NasPhotoTV-1.2.7-release.apk", info.assets[0].name)
        assertEquals(14852521L, info.assets[0].size)
        assertTrue(info.notes.contains("在线更新"))
    }

    @Test
    fun rejectsMalformedReleaseJson() {
        assertEquals(null, parseRelease("not json"))
        assertEquals(null, parseRelease("""{"assets":[]}"""))
        assertEquals(null, parseRelease("{}"))
    }

    @Test
    fun picksAssetMatchingInstalledSignature() {
        val info = UpdateInfo(
            version = "v1.2.7",
            notes = "",
            htmlUrl = "",
            assets = listOf(
                UpdateAsset("NasPhotoTV-1.2.7-release.apk", "https://host/r.apk", 1),
                UpdateAsset("NasPhotoTV-1.2.7-debug.apk", "https://host/d.apk", 1),
            ),
        )
        assertEquals("NasPhotoTV-1.2.7-release.apk", pickAsset(info, isReleaseSignature = true)!!.name)
        assertEquals("NasPhotoTV-1.2.7-debug.apk", pickAsset(info, isReleaseSignature = false)!!.name)
    }

    @Test
    fun fallsBackToAnyApkWhenOnlyOneAssetExists() {
        val info = UpdateInfo(
            version = "v1.2.7", notes = "", htmlUrl = "",
            assets = listOf(UpdateAsset("app.apk", "https://host/app.apk", 1)),
        )
        assertEquals("app.apk", pickAsset(info, isReleaseSignature = true)!!.name)
    }

    @Test
    fun customApiUrlAndDownloadMirror() {
        assertEquals(AppUpdateConfig.DEFAULT_API, effectiveApiUrl(""))
        assertEquals(AppUpdateConfig.DEFAULT_API, effectiveApiUrl("   "))
        assertEquals("https://mirror.example/api/latest", effectiveApiUrl(" https://mirror.example/api/latest "))
        val original = "https://github.com/SMTM-PH/NasPhotoTV/releases/download/v1.2.7/app.apk"
        assertEquals(original, mirroredUrl("", original))
        assertEquals(
            "https://ghproxy.net/$original",
            mirroredUrl("https://ghproxy.net/", original),
        )
        assertEquals("relative/path", mirroredUrl("https://ghproxy.net", "relative/path"))
    }
}
