package vn.tivikit.dulieu

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test
import vn.tivikit.dulieu.DongLenh.Cho
import vn.tivikit.dulieu.DongLenh.MucDo

/** Dòng lệnh ADB: chuẩn hoá, tách lệnh, cảnh báo, sao lưu, chỗ trống, bọc / tách chữ in ra, lệnh mẫu. */
class DongLenhTest {
    private fun ma(lenh: String, canGiu: (String) -> Boolean = { false }) = DongLenh.kiemTra(lenh, canGiu).map { it.ma }
    private fun muc(lenh: String) = DongLenh.kiemTra(lenh).maxOfOrNull { it.mucDo }

    @Test fun boAdbShell() {
        assertEquals("pm list packages" to true, DongLenh.chuanHoa("adb shell pm list packages"))
        assertEquals("getprop ro.product.model" to true, DongLenh.chuanHoa("adb -s 192.168.1.5:5555 shell getprop ro.product.model"))
        assertEquals("pm list packages -3" to true, DongLenh.chuanHoa("adb shell \"pm list packages -3\""))
        assertEquals("pm disable-user a.b\npm disable-user c.d" to true, DongLenh.chuanHoa("adb shell pm disable-user a.b\r\nadb shell pm disable-user c.d"))
        assertEquals("" to true, DongLenh.chuanHoa("adb shell"))
        assertEquals("getprop" to false, DongLenh.chuanHoa("  getprop "))
        // Lệnh có chữ shell ở giữa thì để nguyên.
        assertEquals("echo adb shell" to false, DongLenh.chuanHoa("echo adb shell"))
    }

    @Test fun tachLenh() {
        val c = DongLenh.tach("pm list packages | grep -i you > /sdcard/x.txt 2>&1; echo 'a b' \"c\\\"d\" # chú thích ; rm -rf /")
        assertEquals(listOf(listOf("pm", "list", "packages"), listOf("grep", "-i", "you"), listOf("echo", "a b", "c\"d")), c.map { it.tu })
        assertEquals(listOf("/sdcard/x.txt"), c[1].ghiVao)
        // Lệnh trong su -c / sh -c, sau gán biến, nohup, timeout, xargs đều được xét.
        assertEquals(listOf("rm"), DongLenh.cacLenh("su -c 'rm -rf /sdcard'").map { it.ten })
        assertEquals(listOf("svc"), DongLenh.cacLenh("A=1 nohup timeout 5 svc wifi disable").map { it.ten })
        assertEquals(listOf("pm", "cut", "pm"), DongLenh.cacLenh("pm list packages -3 | cut -d: -f2 | xargs -n1 pm disable-user --user 0").map { it.ten })
        assertEquals(listOf("pm"), DongLenh.cacLenh("for p in a.b c.d; do pm disable-user --user 0 \$p; done").map { it.ten })
        assertEquals(listOf("su"), DongLenh.cacLenh("su").map { it.ten })
    }

    @Test fun lenhAnToanKhongHoi() {
        listOf("getprop ro.product.model", "pm list packages -3", "logcat -d", "logcat -t 200", "ping -c 4 8.8.8.8", "wm density", "wm density reset",
            "settings get global http_proxy", "settings put global http_proxy :0", "am force-stop a.b", "monkey -p a.b 1", "top -n 1 -m 15",
            "dumpsys package a.b | grep versionName", "logcat -d | grep -i factory_reset", "cat /system/build.prop", "cp /system/etc/hosts /sdcard/",
            "ls -la /sdcard/Download", "input keyevent KEYCODE_HOME", "pm enable --user 0 a.b", "pm install-existing a.b", "settings put global window_animation_scale 0.5",
        ).forEach { assertTrue(it, DongLenh.kiemTra(it).none { c -> c.mucDo >= MucDo.CAN_THAN }) }
    }

    @Test fun tatGoi() {
        assertEquals(MucDo.CAN_THAN, muc("pm disable-user --user 0 com.foo.bar"))
        assertTrue("pm-tat" in ma("pm disable-user --user 0 com.foo.bar"))
        // Gói tivi cần (Home, Cài đặt, lõi Android…): nguy hiểm.
        val canGiu = { p: String -> p == "com.android.tv.settings" }
        assertEquals(listOf("goi-loi:com.android.tv.settings", "pm-tat"), ma("cmd package disable-user --user 0 com.android.tv.settings", canGiu))
        assertTrue("goi-loi:com.android.tv.settings" in ma("pm clear com.android.tv.settings", canGiu))
        // Không rõ gói (vòng lặp, xargs): vẫn hỏi.
        assertTrue("pm-tat-nhieu" in ma("for p in a.b c.d; do pm disable-user --user 0 \$p; done"))
        assertTrue("pm-tat-nhieu" in ma("pm list packages -3 | cut -d: -f2 | xargs -n1 pm disable-user --user 0"))
        assertTrue("pm-go-user" in ma("pm uninstall -k --user 0 a.b"))
        assertTrue("pm-go" in ma("pm uninstall a.b"))
        assertTrue("tivikit:vn.tivikit.hengio" in ma("pm uninstall vn.tivikit.hengio"))
    }

    @Test fun xoaFile() {
        assertEquals(listOf("rm"), ma("rm /sdcard/Download/a.txt"))
        assertEquals(listOf("rm"), ma("rm -r /sdcard/Download/TiviKit"))
        assertEquals(listOf("rm"), ma("rm /sdcard/Download/TiviKit/*"))
        listOf("rm -rf /sdcard", "rm -rf /sdcard/", "rm -r /storage/emulated/0/DCIM", "rm /sdcard/*", "rm -rf *", "rm -rf \$HOME", "rm -fr /data/local/tmp")
            .forEach { assertEquals(it, listOf("rm-rong"), ma(it)) }
        assertEquals(listOf("rm", "rm-sao-luu"), ma("rm /data/local/tmp/cleantv-sao-luu.json"))
        assertEquals(listOf("he-thong"), ma("rm /system/app/a.apk"))
        assertEquals(listOf("he-thong"), ma("echo 127.0.0.1 x >> /system/etc/hosts"))
        assertTrue("find-xoa" in ma("find /sdcard -name '*.tmp' -delete"))
        assertTrue("dd-phan-vung" in ma("dd if=/sdcard/a of=/dev/block/mmcblk0"))
        assertTrue("dinh-dang" in ma("mkfs.ext4 /dev/block/sda1"))
        assertTrue("sm-format" in ma("sm format public:8,1"))
        assertTrue("remount" in ma("mount -o rw,remount /system"))
    }

    @Test fun matKetNoi() {
        listOf("svc wifi disable", "cmd wifi set-wifi-enabled disabled", "settings put global adb_enabled 0", "settings delete global adb_enabled",
            "setprop ctl.stop adbd", "killall adbd", "reboot -p", "reboot recovery", "ifconfig wlan0 down", "settings put global development_settings_enabled 0",
        ).forEach { l -> assertTrue(l, DongLenh.kiemTra(l).any { it.matKetNoi && it.mucDo == MucDo.NGUY_HIEM }) }
        assertEquals(listOf("khoi-dong-lai"), ma("reboot"))
        assertEquals(listOf("che-do-dac-biet"), ma("reboot bootloader"))
        assertEquals(listOf("tat-han"), ma("svc power shutdown"))
        assertEquals(listOf("ngu"), ma("input keyevent KEYCODE_SLEEP"))
        assertTrue(DongLenh.kiemTra("sh -c \"svc wifi disable\"").any { it.ma == "tat-wifi" })
    }

    /** Lệnh con trong nháy kép vẫn chạy (28/10/2026). */
    @Test fun lenhConTrongNhayKep() {
        assertTrue(DongLenh.kiemTra("echo \"\$(reboot -p)\"").any { it.ma == "tat-han" })
        assertTrue(DongLenh.kiemTra("echo \"`reboot -p`\"").any { it.ma == "tat-han" })
        assertTrue(DongLenh.cacLenh("r=\"\$(pm uninstall --user 0 com.android.tv.settings)\"").any { it.ten == "pm" })
        assertTrue(DongLenh.kiemTra("echo \"\$(getprop ro.product.model)\"").isEmpty())
    }

    @Test fun caiDat() {
        assertEquals(listOf("proxy"), ma("settings put global http_proxy 10.0.0.2:8080"))
        assertEquals(listOf("tro-nang"), ma("settings put secure enabled_accessibility_services a.b/.Dv"))
        // Có lệnh đọc cùng dòng nhưng ghi thẳng giá trị khác: vẫn là ghi đè.
        assertEquals(listOf("tro-nang"), ma("settings get secure enabled_accessibility_services; settings put secure enabled_accessibility_services a.b/.Dv"))
        // cmd settings là cùng một lệnh.
        assertTrue(DongLenh.kiemTra("cmd settings put global adb_enabled 0").any { it.ma == "adb-tat" })
        assertEquals(listOf("global" to "adb_enabled"), DongLenh.caiDatDoi("cmd settings put global adb_enabled 0"))
        assertEquals(listOf("chua-cai-dat"), ma("settings put secure user_setup_complete 0"))
        assertEquals(listOf("co-chu"), ma("settings put system font_scale 3"))
        assertEquals(listOf("settings-reset"), ma("settings reset global trusted_defaults"))
        assertEquals(listOf("dns"), ma("settings put global private_dns_mode off"))
        assertEquals(listOf("ban-phim"), ma("ime set a.b/.Ime"))
        assertEquals(listOf("wm-density"), ma("wm density 200"))
        assertEquals(listOf("wm-size"), ma("wm size 1280x720"))
        assertEquals(listOf("setprop"), ma("setprop persist.sys.locale vi-VN"))
        assertEquals(listOf("xoa-sach"), ma("am broadcast -a android.intent.action.MASTER_CLEAR"))
    }

    @Test fun lienTucVaTuongTac() {
        assertEquals(listOf("chay-lien-tuc"), ma("logcat"))
        assertEquals(listOf("chay-lien-tuc"), ma("logcat -T 10"))
        assertEquals(listOf("ping-mai"), ma("ping 8.8.8.8"))
        assertEquals(listOf("chay-lien-tuc"), ma("tail -f /sdcard/a.log"))
        assertEquals(listOf("chay-lien-tuc"), ma("top"))
        assertEquals(MucDo.BAO, muc("logcat"))
        listOf("vi /sdcard/a.txt", "su", "sh", "nano a", "sqlite3").forEach { assertEquals(it, MucDo.KHONG_CHAY, muc(it)) }
        // su -c / su 0 có lệnh: chạy được, xét lệnh bên trong.
        assertNull(muc("su -c 'getprop ro.product.model'"))
        assertNull(muc("su 0 id"))
        // Vòng while read đọc từ ống: không phải chờ gõ.
        assertNull(muc("pm list packages | while read p; do echo \$p; done"))
        assertEquals(MucDo.KHONG_CHAY, muc("adb install a.apk"))
        assertEquals(MucDo.KHONG_CHAY, muc("fastboot devices"))
        assertTrue(DongLenh.kiemTra("monkey -p a.b 500").any { it.ma == "monkey" && it.mucDo == MucDo.CAN_THAN })
    }

    @Test fun saoLuuVaGiaTriCu() {
        assertEquals(listOf("a.b", "c.d"), DongLenh.goiTat("pm disable-user --user 0 a.b; cmd package disable-user c.d"))
        assertEquals(listOf("a.b"), DongLenh.goiTat("su -c 'pm disable-user --user 0 a.b'"))
        // Tắt một thành phần (gói/lớp): gói vẫn chạy, không ghi.
        assertEquals(emptyList<String>(), DongLenh.goiTat("pm disable a.b/.Receiver"))
        assertEquals(emptyList<String>(), DongLenh.goiTat("for p in a.b; do pm disable-user \$p; done"))
        assertEquals(listOf("a.b"), DongLenh.goiBat("pm enable --user 0 a.b"))
        assertEquals(listOf("a.b"), DongLenh.goiGo("pm uninstall -k --user 0 a.b"))
        assertEquals(emptyList<String>(), DongLenh.goiGo("pm uninstall a.b"))
        assertEquals(listOf("global" to "window_animation_scale", "secure" to "attentive_timeout"),
            DongLenh.caiDatDoi("settings put global window_animation_scale 0.5; settings delete secure attentive_timeout; settings get global x; settings --user 0 put global window_animation_scale 1"))
    }

    @Test fun choTrong() {
        assertEquals(listOf(Cho.GOI, Cho.QUYEN), DongLenh.choTrong("pm grant {goi} {quyen}"))
        assertEquals("pm grant a.b android.permission.DUMP", DongLenh.dien("pm grant {goi} {quyen}", mapOf(Cho.GOI to " a.b ", Cho.QUYEN to "android.permission.DUMP")))
        // Chữ tự do bọc nháy đơn: khoảng trắng, nháy, $ không thành lệnh.
        assertEquals("input text 'it'\\''s \$HOME; rm'", DongLenh.dien("input text {chu}", mapOf(Cho.CHU to "it's \$HOME; rm")))
        assertTrue(Cho.GOI.hopLe("com.google.android.youtube.tv"))
        assertFalse(Cho.GOI.hopLe("a.b; reboot"))
        assertFalse(Cho.DICH_VU.hopLe("a.b"))
        assertTrue(Cho.DICH_VU.hopLe("com.vidu.app/.DichVu"))
        assertTrue(Cho.BANG.hopLe("secure"))
        assertFalse(Cho.BANG.hopLe("SECURE x"))
        // Gõ dở chưa báo đỏ, gõ ký tự sai thì báo ngay; chữ tự do không bao giờ báo.
        assertFalse(Cho.GOI.saiRo("vlc"))
        assertTrue(Cho.QUYEN.saiRo("android.permission.READ LOGS"))
        assertTrue(Cho.GOI.saiRo("a.b;reboot"))
        assertFalse(Cho.CHU.saiRo("xin chào; \$x"))
        assertTrue(DongLenh.conCho("am force-stop <gói>"))
        assertTrue(DongLenh.conCho("am force-stop <package>"))
        assertFalse(DongLenh.conCho("am force-stop a.b"))
        assertEquals("ping -c 4 1.1.1.1", DongLenh.dien("ping -c 4 {diachi}", mapOf(Cho.DIA_CHI to "1.1.1.1")))
    }

    @Test fun bocLenh() {
        assertEquals("cd '/sdcard/My '\\''Files' 2>/dev/null; sh -c 'echo \"__TK1_P\$\$\"\necho '\\''a'\\''\n__r=\$?; echo; echo \"__TK1_D\$PWD\"; exit \$__r' 2>&1; __x=\$?; echo; echo \"__TK1_X\$__x\"",
            DongLenh.boc("echo 'a'", "/sdcard/My 'Files", "__TK1_"))
    }

    /** Chữ tivi gửi về cho lệnh đã bọc, nạp một lần và từng ký tự một phải ra như nhau. */
    private fun tach(chu: String, dau: String = "__TK1_"): Triple<String, DongLenh.BoTach, String> {
        val mot = DongLenh.BoTach(dau)
        val ra1 = mot.nap(chu) + mot.ket()
        val tung = DongLenh.BoTach(dau)
        val ra2 = StringBuilder().apply { chu.forEach { append(tung.nap(it.toString())) }; append(tung.ket()) }.toString()
        assertEquals(ra1, ra2)
        return Triple(ra1, mot, ra2)
    }

    @Test fun tachChu() {
        tach("__TK1_P1234\nhello\r\nworld\n\n__TK1_D/sdcard\n\n__TK1_X0\n").let { (ra, b) ->
            assertEquals("hello\nworld\n", ra); assertEquals("1234", b.pid); assertEquals("/sdcard", b.thuMuc); assertEquals(0, b.maThoat)
        }
        // In dở dòng (echo -n): echo trong boc kết thúc dòng đó.
        assertEquals("abc\n", tach("__TK1_P9\nabc\n__TK1_D/\n\n__TK1_X0\n").first)
        // Dòng trống cuối của chính lệnh được giữ, chỉ bỏ dòng của echo.
        assertEquals("x\n\n", tach("__TK1_P9\nx\n\n\n__TK1_D/\n\n__TK1_X0\n").first)
        // Không in gì.
        assertEquals("", tach("__TK1_P9\n\n__TK1_D/data/local/tmp\n\n__TK1_X0\n").first)
        // Lỗi cú pháp: sh trong không chạy tới dòng D, thư mục giữ nguyên.
        tach("__TK1_P5\nsh: syntax error: unexpected 'fi'\n\n__TK1_X2\n").let { (ra, b) ->
            assertEquals("sh: syntax error: unexpected 'fi'\n", ra); assertNull(b.thuMuc); assertEquals(2, b.maThoat)
        }
        // Dòng giống đầu dấu nhưng không phải dấu.
        assertEquals("__Tx\n__TK2_X0\n", tach("__TK1_P1\n__Tx\n__TK2_X0\n\n__TK1_X0\n").first)
        // Chữ sau dấu kết thúc bỏ qua.
        assertEquals("", tach("__TK1_P1\n\n__TK1_X1\nrác").first)
    }

    @Test fun tachChuDangChay() {
        // Chữ tới ngay khi có, không chờ hết dòng (ping, logcat); phần có thể là dấu thì giữ lại.
        val b = DongLenh.BoTach("__TK1_")
        assertEquals("", b.nap("__TK1_P7\n"))
        assertEquals("64 bytes", b.nap("64 bytes"))
        assertEquals(" from 8.8.8.8\n", b.nap(" from 8.8.8.8\n"))
        assertEquals("", b.nap("__TK"))
        assertFalse(b.xong)
        // Bấm Dừng giữa chừng: đưa nốt phần giữ.
        assertEquals("__TK", b.ket())
        assertEquals("7", b.pid)
    }

    @Test fun lenhMau() {
        val ds = DongLenh.DS
        assertTrue(ds.size >= 150)
        assertEquals(ds.size, ds.map { it.lenh }.toSet().size)
        // Nhóm liền nhau theo thứ tự hay dùng → ít dùng.
        assertEquals(ds.map { it.nhom.ordinal }.sorted(), ds.map { it.nhom.ordinal })
        assertEquals(DongLenh.Nhom.entries.toSet(), ds.map { it.nhom }.toSet())
        val ma = DongLenh.Cho.entries.map { it.ma }.toSet()
        ds.forEach { m ->
            assertTrue(m.lenh, m.vi.isNotBlank() && m.en.isNotBlank() && m.vi != m.en)
            Regex("""\{[a-z]+\}""").findAll(m.lenh).forEach { assertTrue("${m.lenh}: ${it.value}", it.value in ma) }
            assertTrue(m.lenh, DongLenh.kiemTra(m.lenh).none { it.mucDo == MucDo.KHONG_CHAY })
            assertFalse(m.lenh, DongLenh.conCho(m.lenh))
        }
        fun mau(l: String) = ds.first { it.lenh == l }
        assertEquals(MucDo.NGUY_HIEM, mau("reboot recovery").mucDo)
        assertEquals(MucDo.CAN_THAN, mau("pm clear {goi}").mucDo)
        assertNull(mau("getprop ro.product.model").mucDo)
        // Lệnh mẫu bật dịch vụ trợ năng giữ danh sách cũ: không bị cảnh báo ghi đè.
        assertNull(ds.first { it.lenh.contains("enabled_accessibility_services \"\$s:{dichvu}\"") }.mucDo)
        assertTrue(mau("pm disable-user --user 0 {goi}").coCho)
        assertFalse(mau("pm list packages -3").coCho)
    }
}
