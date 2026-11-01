package vn.tivikit.dulieu

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

/** Lỗi lệnh pm gọn một dòng (1/11/2026): câu trả lời thật của TX3 (Android 9) và các dạng của Android khác. */
class LoiPmTest {
    /** `pm enable --user 0 com.tcl.browser` trên TX3: gói mẫu tivi ghi mà box không có. */
    private val tx3 = """
        Exception occurred while executing:
        java.lang.IllegalArgumentException: Unknown package: com.tcl.browser
        	at com.android.server.pm.PackageManagerService.setEnabledSetting(PackageManagerService.java:20681)
        	at com.android.server.pm.PackageManagerService.setApplicationEnabledSetting(PackageManagerService.java:20624)
        	at com.android.server.pm.PackageManagerShellCommand.runSetEnabledSetting(PackageManagerShellCommand.java:1610)
    """.trimIndent()

    @Test fun khongCoGoi() {
        assertTrue(LoiPm.khongCoGoi(tx3))
        // Android 12+: tên lệnh trong dòng đầu.
        assertTrue(LoiPm.khongCoGoi("Exception occurred while executing 'enable':\njava.lang.IllegalArgumentException: Unknown package: com.x\n\tat a.b(C.java:1)"))
        assertFalse(LoiPm.khongCoGoi("Package com.x new state: enabled"))
        assertFalse(LoiPm.khongCoGoi("Exception occurred while executing:\njava.lang.SecurityException: Shell cannot change component state for com.x/.A to 3"))
        assertFalse(LoiPm.khongCoGoi("lỗi: timeout"))
    }

    @Test fun gon() {
        assertEquals("Unknown package: com.tcl.browser", LoiPm.gon(tx3))
        // pm disable-user trên TX3 mở đầu bằng một dòng trống.
        assertEquals("Unknown package: com.tcl.browser", LoiPm.gon("\n$tx3"))
        assertEquals("Cannot disable a protected package: com.android.settings",
            LoiPm.gon("Exception occurred while executing 'disable-user':\njava.lang.IllegalArgumentException: Cannot disable a protected package: com.android.settings\n\tat x.y(Z.java:2)"))
        assertEquals("Shell cannot change component state for com.x/.A to 3",
            LoiPm.gon("Exception occurred while executing:\njava.lang.SecurityException: Shell cannot change component state for com.x/.A to 3\n\tat x.y(Z.java:2)"))
        // pm của Android 7 và ngoại lệ lồng nhau.
        assertEquals("Unknown package: com.x", LoiPm.gon("Error: java.lang.IllegalArgumentException: Unknown package: com.x"))
        assertEquals("boom", LoiPm.gon("java.lang.RuntimeException: java.lang.IllegalStateException: boom"))
        // Câu không phải ngoại lệ giữ nguyên; rỗng thì rỗng.
        assertEquals("Failure calling service package: Broken pipe (32)", LoiPm.gon("Failure calling service package: Broken pipe (32)"))
        assertEquals("lỗi: timeout", LoiPm.gon("lỗi: timeout"))
        assertEquals("", LoiPm.gon(""))
        assertEquals(200, LoiPm.gon("x".repeat(500)).length)
    }
}
