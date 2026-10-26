package vn.tivikit.dulieu

import org.junit.Assert.assertArrayEquals
import org.junit.Assert.assertEquals
import org.junit.Test
import java.io.File
import java.io.RandomAccessFile
import java.util.zip.ZipFile

/** Đọc mục lục + từng mục zip theo đoạn (như đọc APK trên tivi bằng dd), so với java.util.zip (25/10/2026). */
class TenApkTest {
    private val apk = File("src/main/assets/tvhome.apk")

    /** Đọc đoạn như AppTrenTv.docDoan, đếm số lần đọc và số byte đã đọc. */
    private class DocFile(f: File) : TenApk.DocDoan {
        val raf = RandomAccessFile(f, "r")
        var soLan = 0; var soByte = 0L
        override fun doc(tu: Long, co: Int): ByteArray {
            val n = minOf(co.toLong(), raf.length() - tu).toInt()
            soLan++; soByte += n
            return ByteArray(n).also { raf.seek(tu); raf.readFully(it) }
        }
    }

    @Test fun mucLuc_giongZipFile() {
        val doc = DocFile(apk)
        val ml = TenApk.mucLuc(apk.length(), doc)
        ZipFile(apk).use { z -> assertEquals(z.entries().toList().map { it.name }.toSet(), ml.keys) }
    }

    @Test fun duLieu_giongZipFile_vaChiDocMotPhan() {
        val doc = DocFile(apk)
        val ml = TenApk.mucLuc(apk.length(), doc)
        ZipFile(apk).use { z ->
            for (ten in listOf("AndroidManifest.xml", "resources.arsc", "classes.dex")) {
                assertArrayEquals(ten, z.getInputStream(z.getEntry(ten)).readBytes(), TenApk.duLieu(ml.getValue(ten), doc))
            }
        }
        // Chỉ manifest + arsc thì đọc ít hơn cả file nhiều (đây là điều làm tên app hiện nhanh).
        val doc2 = DocFile(apk)
        val ml2 = TenApk.mucLuc(apk.length(), doc2)
        listOf("AndroidManifest.xml", "resources.arsc").forEach { TenApk.duLieu(ml2.getValue(it), doc2) }
        assert(doc2.soByte < apk.length() / 2) { "đọc ${doc2.soByte}/${apk.length()}" }
    }
}
