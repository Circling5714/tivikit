package vn.tivikit.teleremote

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class DaNhiemTrTest {
    /** Như kết quả LENH_DOC đọc trên Skyworth ngày 2/11/2026 (rút gọn), thêm một app bàn phím và một app VPN người dùng cài. */
    private val kq = listOf(
        """
        package:com.coccoc.trinhduyet_tv
        package:com.gathin.tvhome
        package:com.ionitech.airscreen
        package:org.smarttube.stable
        package:org.xbmc.kodi
        package:com.spotify.music
        package:vn.tivikit
        package:vn.tivikit.mapvoice
        package:com.projectivy.launcher
        package:com.ban.phim
        package:com.vpn.nha
        package:com.nghe.thongbao
        """,
        """
        USER           ARGS
        root           init
        system         system_server
        shell          logcat -v brief -b main -T 11-02 14:30:12.000 -e setFinalRecognizedText
        u0_a149        com.gathin.tvhome
        u0_a88         com.coccoc.trinhduyet_tv
        u0_a88         com.coccoc.trinhduyet_tv:sandboxed_process0
        u0_i12         com.coccoc.trinhduyet_tv:sandboxed_process0:org.chromium.content.app.SandboxedProcessService0:0
        u0_a90         com.ionitech.airscreen
        u0_a91         org.smarttube.stable
        u0_a92         org.xbmc.kodi
        u0_a92         org.xbmc.kodi:remote
        u0_a93         vn.tivikit.mapvoice
        u0_a94         com.projectivy.launcher
        u0_a95         com.ban.phim
        u0_a96         com.vpn.nha
        u0_a97         com.nghe.thongbao
        u0_a45         com.google.android.apps.tv.launcherx
        u0_a60         com.google.android.youtube.tv
        """,
        "vn.tivikit.hengio/.HenGioDichVu:com.gathin.tvhome/com.gathin.tvhome.HomeKeyService:vn.tivikit.mapvoice/vn.tivikit.mapvoice.MapVoiceDichVu",
        "com.ban.phim/.BanPhim",
        "com.google.android.apps.tv.dreamx/.BackdropDream",
        "com.vpn.nha",
        "com.nghe.thongbao/com.nghe.thongbao.NgheDichVu",
    ).joinToString("\n@@\n")

    @Test
    fun chiDongAppNguoiDungDangChay() {
        // Còn lại: app người dùng cài đang chạy, không phải TiviKit, app Home, bàn phím, VPN, đọc thông báo; Spotify không chạy.
        // App hệ thống (launcherx, YouTube gốc) không có trong danh sách app người dùng nên không đóng.
        assertEquals(listOf("com.coccoc.trinhduyet_tv", "com.ionitech.airscreen", "org.smarttube.stable", "org.xbmc.kodi"),
            DaNhiemTr.chon(kq, setOf("com.projectivy.launcher", "vn.tivikit.teleremote")))
        // Không biết Projectivy là app Home thì nó cũng bị đóng (TeleRemoteDichVu luôn đưa app Home vào giuThem).
        assertTrue("com.projectivy.launcher" in DaNhiemTr.chon(kq, emptySet()))
    }

    @Test
    fun ketQuaHongThiKhongDongGi() {
        assertEquals(emptyList<String>(), DaNhiemTr.chon("", emptySet()))
        assertEquals(emptyList<String>(), DaNhiemTr.chon("error: closed", emptySet()))
        // Cài đặt chưa đặt thì shell in "null": không phải gói nào.
        assertEquals(listOf("a.b"), DaNhiemTr.chon("package:a.b\n@@\nu0_a1 a.b\n@@\nnull\n@@\nnull", emptySet()))
    }

    @Test
    fun lenhDocKhongCoDauTach() {
        // ps in cả dòng "sh -c <LENH_DOC>": chữ của lệnh mà có "@@" thì cắt đôi phần tiến trình.
        assertFalse(DaNhiemTr.LENH_DOC.contains("@@"))
        assertEquals(6, DaNhiemTr.LENH_DOC.split("echo @''@").size - 1)
    }

    @Test
    fun tiviKit() {
        listOf("vn.tivikit", "vn.tivikit.kenh2", "vn.tivikit.teleremote", "com.gathin.tvhome").forEach { assertTrue(it, DaNhiemTr.laTiviKit(it)) }
        listOf("vn.tivikitx", "vn.vtv.vtvgotv", "com.gathin").forEach { assertFalse(it, DaNhiemTr.laTiviKit(it)) }
    }

    @Test
    fun lenhDong() {
        assertEquals("am force-stop a.b; am force-stop org.xbmc.kodi; am kill-all", DaNhiemTr.lenhDong(listOf("a.b", "org.xbmc.kodi")))
        assertEquals("am kill-all", DaNhiemTr.lenhDong(emptyList()))
        assertTrue(runCatching { DaNhiemTr.lenhDong(listOf("a.b; reboot")) }.isFailure)
    }
}
