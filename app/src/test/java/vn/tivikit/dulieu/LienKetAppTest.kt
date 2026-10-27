package vn.tivikit.dulieu

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test
import vn.tivikit.dulieu.LienKetApp.MoBang

/** Set App Link: đọc kết quả quét, lệnh theo bản Android, link người dùng gõ. */
class LienKetAppTest {
    /** Chữ tivi Skyworth SWTV-22AE (Android 11) trả về ngày 27/10/2026, trước khi đặt SmartTube. */
    private val quetSkyworth = """
        @@B
        4 activities found:
          Activity #0:
            priority=0 preferredOrder=0 match=0x208000 specificIndex=-1 isDefault=true
            com.coccoc.trinhduyet_tv/com.coccoc.IntentDispatcher
          Activity #1:
            priority=0 preferredOrder=0 match=0x208000 specificIndex=-1 isDefault=true
            com.coccoc.trinhduyet_tv/coccoc.android_tv.WebSurfingActivity
          Activity #2:
            priority=0 preferredOrder=0 match=0x208000 specificIndex=-1 isDefault=true
            com.phlox.tvwebbrowser/.activity.main.MainActivity
          Activity #3:
            priority=-1 preferredOrder=0 match=0x208000 specificIndex=-1 isDefault=true
            com.google.android.tv.frameworkpackagestubs/.Stubs${'$'}BrowserStub
        @@Q0
        5 activities found:
          Activity #0:
            priority=0 preferredOrder=0 match=0x508000 specificIndex=-1 isDefault=true
            org.smarttube.stable/com.liskovsoft.smartyoutubetv2.tv.ui.main.SplashActivity
          Activity #1:
            priority=0 preferredOrder=0 match=0x208000 specificIndex=-1 isDefault=true
            com.coccoc.trinhduyet_tv/com.coccoc.IntentDispatcher
          Activity #2:
            priority=0 preferredOrder=0 match=0x208000 specificIndex=-1 isDefault=true
            com.phlox.tvwebbrowser/.activity.main.MainActivity
          Activity #3:
            priority=-1 preferredOrder=0 match=0x208000 specificIndex=-1 isDefault=true
            com.google.android.tv.frameworkpackagestubs/.Stubs${'$'}BrowserStub
        @@R0
        android/com.android.internal.app.ResolverActivity
        @@Q1
        4 activities found:
          Activity #0:
            priority=0 preferredOrder=0 match=0x208000 specificIndex=-1 isDefault=true
            com.coccoc.trinhduyet_tv/com.coccoc.IntentDispatcher
        @@R1
        android/com.android.internal.app.ResolverActivity
        @@Q2
        2 activities found:
          Activity #0:
            priority=0 preferredOrder=0 match=0x208000 specificIndex=-1 isDefault=true
            com.netflix.ninja/.MainActivity
        @@R2
        com.netflix.ninja/.MainActivity
    """.trimIndent()

    @Test fun docQuet() {
        val q = LienKetApp.tuQuet(quetSkyworth, 3)
        assertEquals(setOf("com.coccoc.trinhduyet_tv", "com.phlox.tvwebbrowser", "com.google.android.tv.frameworkpackagestubs"), q.trinhDuyet)
        // YouTube: chỉ SmartTube là app riêng, trình duyệt bị bỏ; tivi đang hỏi chọn app.
        assertEquals(LienKetApp.KetQua(listOf("org.smarttube.stable"), MoBang.Hoi), q.ketQua[0])
        // Trang chỉ trình duyệt mở được: không có lựa chọn.
        assertEquals(emptyList<String>(), q.ketQua[1].ungVien)
        assertEquals(LienKetApp.KetQua(listOf("com.netflix.ninja"), MoBang.App("com.netflix.ninja")), q.ketQua[2])
    }

    @Test fun moBang() {
        val td = setOf("com.phlox.tvwebbrowser")
        assertEquals(MoBang.App("org.smarttube.stable"), LienKetApp.moBangTu("priority=0 preferredOrder=200000005\norg.smarttube.stable/com.liskovsoft.smartyoutubetv2.tv.ui.main.SplashActivity\n", td))
        assertEquals(MoBang.Hoi, LienKetApp.moBangTu("android/com.android.internal.app.ResolverActivity", td))
        assertEquals(MoBang.TrinhDuyet("com.phlox.tvwebbrowser"), LienKetApp.moBangTu("com.phlox.tvwebbrowser/.activity.main.MainActivity", td))
        assertEquals(MoBang.KhongCo, LienKetApp.moBangTu("No activity found", td))
    }

    @Test fun lenhTheoBanAndroid() {
        assertEquals("pm set-app-link --user 0 org.smarttube.stable always", LienKetApp.lenhDat(30, "org.smarttube.stable"))
        assertEquals("pm set-app-links-allowed --user 0 --package org.smarttube.stable true; pm set-app-links-user-selection --user 0 --package org.smarttube.stable true all",
            LienKetApp.lenhDat(33, "org.smarttube.stable"))
        assertEquals("pm set-app-link --user 0 a.b undefined; pm set-app-link --user 0 c.d undefined", LienKetApp.lenhBo(28, listOf("a.b", "c.d")))
        assertEquals("pm set-app-links-user-selection --user 0 --package a.b false all; pm set-app-links-allowed --user 0 --package a.b true", LienKetApp.lenhBo(31, listOf("a.b")))
        assertEquals("pm set-app-links-allowed --user 0 --package com.google.android.youtube.tv false", LienKetApp.lenhChan("com.google.android.youtube.tv"))
        // Tên gói lạ (chèn lệnh) bị từ chối.
        assertTrue(runCatching { LienKetApp.lenhDat(30, "a.b; reboot") }.isFailure)
    }

    @Test fun lenhQuetBocNhay() {
        val l = LienKetApp.lenhQuet(listOf("https://www.youtube.com/watch?v=dQw4w9WgXcQ&t=1"))
        assertTrue(l.startsWith("echo @@B; cmd package query-activities --brief -a android.intent.action.VIEW -c android.intent.category.BROWSABLE -d 'https://tivikit-kiem-tra.invalid/'"))
        assertTrue(l.contains("echo @@Q0; cmd package query-activities --brief -a android.intent.action.VIEW -c android.intent.category.BROWSABLE -d 'https://www.youtube.com/watch?v=dQw4w9WgXcQ&t=1'"))
        assertTrue(l.endsWith("echo @@R0; cmd package resolve-activity --brief -a android.intent.action.VIEW -c android.intent.category.BROWSABLE -d 'https://www.youtube.com/watch?v=dQw4w9WgXcQ&t=1' | tail -n 1"))
    }

    @Test fun linkNguoiDungGo() {
        assertEquals("https://vimeo.com/", LienKetApp.chuanHoa(" vimeo.com "))
        assertEquals("https://youtu.be/abc", LienKetApp.chuanHoa("https://youtu.be/abc"))
        assertEquals("http://www.nhaccuatui.com/bai-hat/x", LienKetApp.chuanHoa("http://www.nhaccuatui.com/bai-hat/x"))
        listOf("", "localhost", "ftp://a.com/x", "javascript:alert(1)", "a b.com", "x.com'; reboot", ".com", "https://").forEach { assertNull(it, LienKetApp.chuanHoa(it)) }
        assertEquals("youtube.com", LienKetApp.tenMien("https://www.youtube.com/watch?v=1"))
    }

    @Test fun danhSachTrang() {
        val ds = LienKetApp.TRANG
        assertEquals(ds.size, ds.map { it.link }.toSet().size)
        ds.forEach { assertEquals(it.link, it.link, LienKetApp.chuanHoa(it.link)) }
    }
}
