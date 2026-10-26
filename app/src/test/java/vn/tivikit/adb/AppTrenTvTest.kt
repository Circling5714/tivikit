package vn.tivikit.adb

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

/** Đọc kết quả df / dumpsys của màn Quản lý app (25/10/2026). */
class AppTrenTvTest {
    @Test fun df() {
        val out = "Filesystem      1K-blocks    Used Available Use% Mounted on\n/dev/block/dm-5  24735148 5823452  18911696  24% /data\n"
        assertEquals(BoNho(24735148, 5823452, 18911696), AppTrenTv.tuDf(out))
        assertNull(AppTrenTv.tuDf("df: /data: Permission denied"))
    }

    /** Lệnh của màn Làm mới tivi: meminfo, uptime, df cách nhau bằng dòng @@. */
    @Test fun tinhTrang() {
        val out = "MemTotal:        1547492 kB\nMemFree:           81236 kB\nMemAvailable:     612340 kB\n@@\n1043521.47 3870211.90\n@@\n" +
            "Filesystem      1K-blocks    Used Available Use% Mounted on\n/dev/block/dm-5   4500000 3600000    900000  80% /data\n"
        assertEquals(TinhTrangTv(vn.tivikit.dulieu.LamMoi.Ram(1547492, 612340), BoNho(4500000, 3600000, 900000), 1043521L), AppTrenTv.tuTinhTrang(out))
        // Một phần đọc không được thì phần đó null, phần khác vẫn có.
        assertEquals(TinhTrangTv(null, null, 1043521L), AppTrenTv.tuTinhTrang("cat: /proc/meminfo: Permission denied\n@@\n1043521.47 1.0\n@@\n"))
    }

    /** Lệnh Đang chạy ngầm của Quản lý app: ps, rồi pm list packages sau dòng @@ (gói "android" không tính). */
    @Test fun chayNgam() {
        val (ds, goi) = AppTrenTv.tuChayNgam("  RSS NAME\n109136 app.revanced.android.gms\n100776 app.revanced.android.gms:persistent\n" +
            "4800 adbd\n@@\npackage:android\npackage:app.revanced.android.gms\npackage:org.smarttube.stable\n")
        assertEquals(setOf("app.revanced.android.gms", "org.smarttube.stable"), goi)
        assertEquals(listOf(vn.tivikit.dulieu.ChayNgam.App("app.revanced.android.gms", 209_912)), ds)
    }

    /** 26/10/2026: thêm loadavg, số nhân, nhiệt độ, cpuinfo (box TX3 Mini lúc CH Play cập nhật dồn). */
    @Test fun tinhTrangCoCpu() {
        val out = "MemTotal: 2054204 kB\nMemAvailable: 1177420 kB\n@@\n1320.5 4000.1\n@@\n/dev/block/data 10900404 2204996 8646256 21% /data\n@@\n" +
            "12.53 12.45 9.36 9/1367 10893\n@@\n0-3\n@@\n70000\n@@\nLoad: 13.0 / 7.68 / 4.8\n" +
            "  63% 4640/com.android.vending:background: 53% user + 10% kernel / faults: 170609 minor 1 major\n" +
            "  28% 3149/surfaceflinger: 15% user + 13% kernel / faults: 42223 minor\n"
        val tt = AppTrenTv.tuTinhTrang(out)
        assertEquals(12.53, tt.tai!!, 0.001)
        assertEquals(4, tt.soNhan)
        assertEquals(70, tt.nhietDo)
        assertEquals(listOf("com.android.vending:background", "surfaceflinger"), tt.anCpu.map { it.ten })
        assertEquals(1_177_420L, tt.ram?.conKb)
    }

    @Test fun danhSach() {
        val out = """
@@ com.netflix.ninja|48211345
    versionCode=63452 minSdk=21 targetSdk=33
    versionName=11.2.1 build 7
@@ com.fptplay.tv|31000000
    versionCode=1201 minSdk=21 targetSdk=34
    versionName=7.1.0
@@ khong-hop-le|1
""".trimIndent()
        assertEquals(listOf(AppTv("com.netflix.ninja", "11.2.1 build 7", 63452, 48211345), AppTv("com.fptplay.tv", "7.1.0", 1201, 31000000)),
            AppTrenTv.tuDanhSach(out))
    }

    @Test fun diskstats() {
        val out = "Latency: 2ms [512B Data Write]\nPackage Names: [\"com.a\",\"com.b\"]\nApp Sizes: [100,200]\nApp Data Sizes: [10,20]\nCache Sizes: [1,2]\n"
        val kq = AppTrenTv.tuDiskstats(out)
        assertEquals(CoDuLieu(200, 20, 2), kq["com.b"])
        assertTrue(AppTrenTv.tuDiskstats("Package Names: [\"com.a\"]\nApp Sizes: []").isEmpty())
    }
}
