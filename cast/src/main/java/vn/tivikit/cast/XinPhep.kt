package vn.tivikit.cast

import android.content.Context
import android.content.Intent
import android.os.Build
import android.os.Bundle
import android.view.WindowManager
import androidx.activity.compose.BackHandler
import androidx.activity.compose.setContent
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import kotlinx.coroutines.delay

/**
 * Máy lạ cast lần đầu (28/10/2026): hộp nổi trên app đang mở hỏi cho phép không. Cho phép luôn / tới hết hôm nay / 24 giờ
 * (cần mã bố mẹ khi Khoá trẻ em đang chạy), chặn; Back hay để yên 60 giây là bỏ qua lần này (lần sau hỏi lại). Video máy
 * đó gửi được giữ lại ở máy nhận, cho phép thì mới mở (MayThu.traLoiDuyet).
 */
class XinPhep : ManCoMa() {
    private var id by mutableStateOf("")
    private var ten by mutableStateOf("")
    private var video by mutableStateOf("")
    private var daTraLoi = false

    override fun onCreate(b: Bundle?) {
        super.onCreate(b)
        // Tivi đang tắt màn / màn bảo vệ: bật lên cho người trong nhà thấy (Android 14+ cần TURN_SCREEN_ON, TiviKit đã cấp).
        if (Build.VERSION.SDK_INT >= 27) { setTurnScreenOn(true); setShowWhenLocked(true) }
        else @Suppress("DEPRECATION") window.addFlags(WindowManager.LayoutParams.FLAG_TURN_SCREEN_ON or WindowManager.LayoutParams.FLAG_SHOW_WHEN_LOCKED)
        nhan(intent)
        setContent { ManHinh() }
    }

    /** Máy khác hỏi lúc hộp đang mở: câu cũ coi như bỏ qua, hỏi câu mới. */
    override fun onNewIntent(i: Intent) {
        super.onNewIntent(i)
        if (!daTraLoi) CastDichVu.dangChay?.may?.traLoiDuyet(id, null)
        nhan(i)
    }

    private fun nhan(i: Intent) {
        id = i.getStringExtra(ID).orEmpty()
        ten = i.getStringExtra(TEN).orEmpty()
        video = i.getStringExtra(VIDEO).orEmpty()
        daTraLoi = false
    }

    private fun tra(lam: DanhBa.Lam?) {
        if (daTraLoi) return
        daTraLoi = true
        CastDichVu.dangChay?.may?.traLoiDuyet(id, lam)
        finish()
    }

    override fun onDestroy() {
        if (!daTraLoi) { daTraLoi = true; CastDichVu.dangChay?.may?.traLoiDuyet(id, null) }
        super.onDestroy()
    }

    @Composable
    private fun ManHinh() {
        var con by remember(id) { mutableIntStateOf(GIAY) }
        var bai by remember(video) { mutableStateOf<String?>(null) }
        val fr = remember { FocusRequester() }
        LaunchedEffect(id) {
            while (con > 0) { delay(1_000); con-- }
            tra(null)
        }
        LaunchedEffect(video) {
            repeat(20) { TenVideo.lay(video)?.let { bai = it.first; return@LaunchedEffect }; delay(500) }
        }
        LaunchedEffect(id) { delay(150); runCatching { fr.requestFocus() } }
        BackHandler { tra(null) }
        // Đang nhập mã bố mẹ thì đếm lại từ đầu, khỏi tự đóng giữa chừng.
        val choPhep = { l: DanhBa.Lam -> con = GIAY; canMa(t("cho máy này cast lên tivi", "let this phone cast to the TV")) { tra(l) } }
        Box(Modifier.fillMaxSize().background(Color.Black.copy(alpha = 0.6f)), contentAlignment = Alignment.Center) {
            The(Modifier.width(640.dp).background(Mau.nenDuoi.copy(alpha = 0.94f), RoundedCornerShape(24.dp))) {
                Chu("📱 " + t("Có điện thoại muốn cast lên tivi", "A phone wants to cast to this TV"), 24.sp, dam = FontWeight.Bold)
                Chu(ten.ifEmpty { t("Điện thoại chưa biết tên", "Unnamed phone") }, 20.sp, Mau.nhan, FontWeight.SemiBold, dong = 1)
                Chu("▶ " + (bai ?: t("đang tra tên video…", "looking up the video…")), 16.sp, Mau.phu, dong = 2)
                Spacer(Modifier.height(6.dp))
                Nut(t("Cho phép luôn", "Allow always"), { choPhep(DanhBa.Lam.CHO_PHEP) }, Modifier.fillMaxWidth(), Mau.xanh, fr)
                Nut(t("Cho phép tới hết hôm nay (khách)", "Allow until the end of today (guest)"), { choPhep(DanhBa.Lam.HOM_NAY) }, Modifier.fillMaxWidth())
                Nut(t("Cho phép 24 giờ (khách)", "Allow for 24 hours (guest)"), { choPhep(DanhBa.Lam.MOT_NGAY) }, Modifier.fillMaxWidth())
                Nut(t("Chặn máy này", "Block this phone"), { tra(DanhBa.Lam.CHAN) }, Modifier.fillMaxWidth(), Mau.do_)
                Chu(t("Back: bỏ qua lần này (lần sau hỏi lại) · tự đóng sau $con giây", "Back: ignore this time (ask again next time) · closes in $con s"), 13.sp, Mau.mo)
            }
        }
    }

    companion object {
        private const val ID = "id"
        private const val TEN = "ten"
        private const val VIDEO = "video"
        const val GIAY = 60

        /** Mở hộp hỏi cho máy [d] gửi video [video] (máy nhận gọi, từ dịch vụ trợ năng nên mở được khi chạy nền). */
        fun mo(ctx: Context, d: DienThoai, video: String) = runCatching {
            ctx.startActivity(Intent(ctx, XinPhep::class.java).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
                .putExtra(ID, d.id).putExtra(TEN, d.tenHien.orEmpty()).putExtra(VIDEO, video))
        }
    }
}
