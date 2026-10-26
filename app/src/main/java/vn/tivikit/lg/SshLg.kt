package vn.tivikit.lg

import com.jcraft.jsch.ChannelExec
import com.jcraft.jsch.JSch
import com.jcraft.jsch.JSchException
import com.jcraft.jsch.Session
import com.jcraft.jsch.UIKeyboardInteractive
import com.jcraft.jsch.UserInfo
import java.io.ByteArrayOutputStream
import java.net.InetSocketAddress
import java.net.Socket
import javax.net.SocketFactory

/** Sai mật khẩu root khi đăng nhập SSH. */
class SshSaiMatKhauException : java.io.IOException("Sai mật khẩu SSH")

/**
 * SSH root vào tivi LG đã root (Homebrew Channel bật SSH Server: dropbear cổng 22, mặc định root / alpine), chỉ cho việc
 * cần root (Vibehack). Mỗi việc một phiên: nối, chạy, đóng. [mang]: socket của mạng Wi-Fi (xem KetNoiLg).
 */
class SshLg(private val ip: String, private val matKhau: String, private val mang: SocketFactory?) {
    companion object {
        const val CONG = 22

        /** Dòng đầu cổng [cong] của [ip] (lời chào SSH), chờ tối đa [ms]; không mở / không nói gì thì null. */
        fun dongDau(ip: String, cong: Int, ms: Int, mang: SocketFactory?): String? = runCatching {
            ((mang ?: SocketFactory.getDefault()).createSocket() as Socket).use { s ->
                s.connect(InetSocketAddress(ip, cong), ms); s.soTimeout = ms
                val v = s.getInputStream(); val b = ByteArrayOutputStream()
                while (b.size() < 255) { val c = v.read(); if (c < 0 || c == '\n'.code) break; b.write(c) }
                b.toString("UTF-8").trim().takeIf { it.isNotEmpty() }
            }
        }.getOrNull()
    }

    private fun phien(): Session {
        val s = JSch().getSession("root", ip, CONG)
        s.setPassword(matKhau)
        // Tivi trong nhà, khoá máy chủ do tivi tự sinh: không đối chiếu (như lần đầu ssh bấm yes).
        s.setConfig("StrictHostKeyChecking", "no")
        s.setConfig("PreferredAuthentications", "password,keyboard-interactive")
        // dropbear đời cũ chỉ có khoá ssh-rsa (SHA-1): JSch bản mới tắt mặc định, bật lại cho tivi cũ.
        s.setConfig("server_host_key", s.getConfig("server_host_key") + ",ssh-rsa")
        s.userInfo = object : UserInfo, UIKeyboardInteractive {
            override fun getPassphrase(): String? = null
            override fun getPassword(): String = matKhau
            override fun promptPassword(message: String?) = true
            override fun promptPassphrase(message: String?) = false
            override fun promptYesNo(message: String?) = true
            override fun showMessage(message: String?) {}
            override fun promptKeyboardInteractive(destination: String?, name: String?, instruction: String?, prompt: Array<out String>?, echo: BooleanArray?): Array<String> =
                Array(prompt?.size ?: 0) { matKhau }
        }
        if (mang != null) s.setSocketFactory(object : com.jcraft.jsch.SocketFactory {
            override fun createSocket(host: String, port: Int): Socket = mang.createSocket().apply { connect(InetSocketAddress(host, port), 6000) }
            override fun getInputStream(socket: Socket) = socket.getInputStream()
            override fun getOutputStream(socket: Socket) = socket.getOutputStream()
        })
        try { s.connect(8000) } catch (e: JSchException) {
            if (e.message?.contains("Auth", ignoreCase = true) == true) throw SshSaiMatKhauException()
            throw e
        }
        return s
    }

    /** Chạy [lenh], trả (mã thoát, chữ in ra); [dong] nhận từng dòng khi đang chạy. Quá [giay] giây thì ngắt (mã -1). */
    fun chay(lenh: String, giay: Int = 600, dong: (String) -> Unit = {}): Pair<Int, String> {
        val s = phien()
        try {
            val ch = s.openChannel("exec") as ChannelExec
            ch.setCommand(lenh)
            val vao = ch.inputStream
            ch.connect(8000)
            val tat = StringBuilder(); val hang = StringBuilder()
            val het = System.currentTimeMillis() + giay * 1000L
            val buf = ByteArray(4096)
            while (System.currentTimeMillis() < het) {
                while (vao.available() > 0) {
                    val n = vao.read(buf); if (n < 0) break
                    val chu = String(buf, 0, n, Charsets.UTF_8)
                    tat.append(chu)
                    for (c in chu) if (c == '\n') { dong(hang.toString()); hang.clear() } else hang.append(c)
                }
                if (ch.isClosed && vao.available() == 0) break
                Thread.sleep(100)
            }
            if (hang.isNotEmpty()) dong(hang.toString())
            val ma = if (ch.isClosed) ch.exitStatus else -1
            ch.disconnect()
            return ma to tat.toString()
        } finally { s.disconnect() }
    }

    /** Ghi [du] vào [duong] trên tivi (qua `cat >`). */
    fun ghi(du: ByteArray, duong: String) {
        val s = phien()
        try {
            val ch = s.openChannel("exec") as ChannelExec
            ch.setCommand("cat > " + Vibehack.trich(duong))
            val ra = ch.outputStream
            ch.connect(8000)
            ra.write(du); ra.flush(); ra.close()
            val het = System.currentTimeMillis() + 30_000
            while (!ch.isClosed && System.currentTimeMillis() < het) Thread.sleep(100)
            if (ch.exitStatus != 0) throw java.io.IOException("Không ghi được $duong lên tivi")
            ch.disconnect()
        } finally { s.disconnect() }
    }

    /** Đăng nhập rồi xem tivi có root thật, Homebrew Channel, Vibehack, LG Input Hook không. */
    fun kiemTra(): Vibehack.KiemTra = Vibehack.docKiemTra(chay(Vibehack.LENH_KIEM_TRA, 20).second)
}
