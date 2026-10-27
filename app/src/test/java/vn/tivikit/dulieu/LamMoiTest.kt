package vn.tivikit.dulieu

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

/** Làm mới tivi: đọc RAM, thời gian chạy, ngưỡng xoá cache / gợi ý khởi động lại. */
class LamMoiTest {
    @Test fun meminfo() {
        val s = "MemTotal:        1547492 kB\nMemFree:           81236 kB\nMemAvailable:     612340 kB\nBuffers:           12000 kB\nCached:           480000 kB\n"
        assertEquals(LamMoi.Ram(1547492, 612340), LamMoi.tuMeminfo(s))
        // Nhân cũ không có MemAvailable: trống + bộ đệm.
        assertEquals(LamMoi.Ram(1547492, 81236 + 12000 + 480000), LamMoi.tuMeminfo(s.lines().filter { !it.startsWith("MemAvailable") }.joinToString("\n")))
        assertNull(LamMoi.tuMeminfo("cat: /proc/meminfo: Permission denied"))
    }

    @Test fun uptime() {
        assertEquals(1_043_521L, LamMoi.tuUptime("1043521.47 3870211.90\n"))
        assertNull(LamMoi.tuUptime(""))
    }

    @Test fun boNhoGanDay() {
        val gb = 1_048_576L
        // Tivi 8 GB (/data ~4.5 GB): 15% < 1 GB nên lấy mốc 1 GB.
        assertTrue(LamMoi.boNhoGanDay(4_500_000, 900_000))
        assertFalse(LamMoi.boNhoGanDay(4_500_000, 1_200_000))
        // Tivi 32 GB (/data ~25 GB): mốc 15%.
        assertTrue(LamMoi.boNhoGanDay(25 * gb, 3 * gb))
        assertFalse(LamMoi.boNhoGanDay(25 * gb, 5 * gb))
    }

    /** dumpsys cpuinfo của box Tanix TX3 Mini lúc CH Play cập nhật dồn sau khi đăng nhập lại (26/10/2026). */
    private val cpuinfoTx3 = """
        Load: 13.0 / 7.68 / 4.8
        CPU usage from 411770ms to 111691ms ago (2026-10-26 11:03:32.582 to 2026-10-26 11:08:32.661):
          63% 4640/com.android.vending:background: 53% user + 10% kernel / faults: 170609 minor 1 major
          49% 7292/com.google.android.gms.ui: 40% user + 8.6% kernel / faults: 114519 minor 2 major
          28% 3149/surfaceflinger: 15% user + 13% kernel / faults: 42223 minor
          21% 3332/system_server: 14% user + 6.7% kernel / faults: 33677 minor
          21% 7011/com.android.vending: 16% user + 4.4% kernel / faults: 55898 minor 6 major
          12% 3127/android.hardware.audio@2.0-service: 1.4% user + 10% kernel / faults: 1223 minor
          6.5% 4852/org.smarttube.stable: 4.3% user + 2.1% kernel / faults: 6105 minor
          1.9% 2111/irq/44-meson-am: 0% user + 1.9% kernel
         +0% 8614/dex2oat: 0% user + 0% kernel
        76% TOTAL: 48% user + 23% kernel + 0.3% iowait + 1.3% irq + 2.2% softirq
    """.trimIndent()

    @Test fun cpuinfo() {
        val ds = LamMoi.tuCpuinfo(cpuinfoTx3)
        assertEquals(listOf("com.android.vending:background", "com.google.android.gms.ui", "surfaceflinger", "system_server", "com.android.vending",
            "android.hardware.audio@2.0-service", "org.smarttube.stable", "irq/44-meson-am"), ds.map { it.ten })
        assertEquals(63.0, ds.first().phanTram, 0.0)
        assertTrue(LamMoi.tuCpuinfo("Load: 0.5 / 0.4 / 0.3\n").isEmpty())
    }

    @Test fun banViec_tx3LaCapNhat_kswapdLaThieuRam() {
        assertEquals(LamMoi.Nhom.CAP_NHAT, LamMoi.banViec(LamMoi.tuCpuinfo(cpuinfoTx3)))
        assertEquals(LamMoi.Nhom.THIEU_RAM, LamMoi.banViec(listOf(LamMoi.TienTrinh("org.smarttube.stable", 80.0), LamMoi.TienTrinh("kswapd0", 15.0))))
        assertEquals(LamMoi.Nhom.APP, LamMoi.banViec(listOf(LamMoi.TienTrinh("org.smarttube.stable", 80.0), LamMoi.TienTrinh("kswapd0", 4.0))))
        assertNull(LamMoi.banViec(emptyList()))
    }

    @Test fun nhomTienTrinh() {
        assertEquals(LamMoi.Nhom.CAP_NHAT, LamMoi.nhom("dex2oat64"))
        assertEquals(LamMoi.Nhom.CAP_NHAT, LamMoi.nhom("installd"))
        assertEquals(LamMoi.Nhom.GOOGLE, LamMoi.nhom("com.google.android.gms.persistent"))
        assertEquals(LamMoi.Nhom.MAN_HINH, LamMoi.nhom("android.hardware.graphics.composer@2.2-service"))
        assertEquals(LamMoi.Nhom.VIDEO, LamMoi.nhom("android.hardware.media.omx@1.0-service"))
        assertEquals(LamMoi.Nhom.VIDEO, LamMoi.nhom("media.codec"))
        assertEquals(LamMoi.Nhom.AM_THANH, LamMoi.nhom("android.hardware.audio@2.0-service"))
        assertEquals(LamMoi.Nhom.HE_THONG, LamMoi.nhom("adbd"))
        assertEquals(LamMoi.Nhom.HE_THONG, LamMoi.nhom("kworker/u8:0"))
        assertEquals(LamMoi.Nhom.HE_THONG, LamMoi.nhom("android.hardware.wifi@1.0-service"))
        assertEquals(LamMoi.Nhom.APP, LamMoi.nhom("org.smarttube.stable"))
    }

    @Test fun taiSoNhanNhietDo() {
        assertEquals(12.53, LamMoi.tuLoadavg("12.53 12.45 9.36 9/1367 10893\n")!!, 0.0)
        assertEquals(4, LamMoi.tuSoNhan("0-3\n"))
        assertEquals(6, LamMoi.tuSoNhan("0-1,4-7"))
        assertEquals(1, LamMoi.tuSoNhan("0"))
        assertNull(LamMoi.tuSoNhan(""))
        assertEquals(70, LamMoi.tuNhietDo("70000\n"))
        assertEquals(45, LamMoi.tuNhietDo("45"))
        assertNull(LamMoi.tuNhietDo("-5000"))
        assertTrue(LamMoi.quaTai(12.53, 4))
        assertFalse(LamMoi.quaTai(5.9, 4))
    }

    @Test fun ramVaThoiGian() {
        assertTrue(LamMoi.ramThap(LamMoi.Ram(2_000_000, 200_000)))
        assertFalse(LamMoi.ramThap(LamMoi.Ram(2_000_000, 700_000)))
        assertFalse(LamMoi.chayLau(6 * 86_400L + 23 * 3_600))
        assertTrue(LamMoi.chayLau(7 * 86_400L))
        assertEquals(Triple(12L, 4L, 5L), LamMoi.tach(12 * 86_400L + 4 * 3_600 + 5 * 60 + 59))
    }

    /** CPU bận đo bằng /proc/stat: Skyworth SWTV-22AE phát SmartTube, tải trung bình 70 trên 4 nhân mà CPU chỉ bận 42%. */
    @Test fun cpuBan_tuProcStat() {
        val a = "cpu  36190 5012 35896 71121 3308 0 1186 0 0 0"
        val b = "cpu  36556 5016 36324 72237 3308 0 1198 0 0 0"
        assertEquals(42, LamMoi.tuProcStat("$a\n$b\n"))
        // Chờ đĩa (iowait) tính là rảnh; hai mẫu như nhau / thiếu dòng / sai dạng thì không có số.
        assertEquals(50, LamMoi.tuProcStat("cpu 0 0 0 0 0 0 0 0\ncpu 50 0 0 25 25 0 0 0\n"))
        assertEquals(null, LamMoi.tuProcStat("$a\n$a\n"))
        assertEquals(null, LamMoi.tuProcStat(a))
        assertEquals(null, LamMoi.tuProcStat("cpu x y z\ncpu 1 2 3\n"))
        // Dòng cpu0..cpu3 của từng nhân không lẫn vào.
        assertEquals(42, LamMoi.tuProcStat("$a\ncpu0 1 2 3 4 5 6 7 8\n$b\ncpu0 9 9 9 9 9 9 9 9\n"))
        // TiviKit chạy trên chính tivi: utime / stime của nó (dòng "u s" sau mỗi dòng cpu) trừ ra khỏi phần bận.
        // Bận 80 / 180 nhịp, trong đó TiviKit 50: tivi bận (80 − 50) / 180 = 16%.
        assertEquals(16, LamMoi.tuProcStat("cpu 0 0 0 0 0 0 0 0\n100 20\ncpu 80 0 0 100 0 0 0 0\n130 40\n"))
        // Thiếu một dòng riêng (tiến trình vừa thoát): không trừ.
        assertEquals(44, LamMoi.tuProcStat("cpu 0 0 0 0 0 0 0 0\n100 20\ncpu 80 0 0 100 0 0 0 0\n"))
    }

    @Test fun quaTai_theoCpuBanTruoc() {
        // Đo được CPU bận thì không theo tải (tải 70 trên 4 nhân là cao ảo).
        assertFalse(LamMoi.quaTai(42, 70.0, 4))
        assertTrue(LamMoi.quaTai(90, 2.0, 4))
        // Không đo được: theo tải như trước.
        assertTrue(LamMoi.quaTai(null, 12.53, 4))
        assertFalse(LamMoi.quaTai(null, null, 4))
    }
}
