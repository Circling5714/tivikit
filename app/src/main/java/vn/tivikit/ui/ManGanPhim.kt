package vn.tivikit.ui

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.*
import androidx.compose.material3.*
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import vn.tivikit.dulieu.PhimRieng
import vn.tivikit.t

/** Màn Nút remote (25/10/2026): dò nút trên remote thật, gán sang app khác (VD YouTube → SmartTube), bấm thử. */
@Composable
internal fun ManGanPhim(vm: PhienLamViec) = Cuon {
    val g = vm.ganPhim
    Column(Modifier.fillMaxWidth(), verticalArrangement = Arrangement.spacedBy(6.dp)) {
        Text(t("Nút remote", "Remote buttons"), style = KieuTieuDeLon)
        Phu(t("Gán một nút trên remote thật để mở app khác, VD nút YouTube mở SmartTube. App đọc mã nút lúc bấm, rồi ghi vào Home sạch: dịch vụ phím của Home sạch bắt nút đó và mở app đã chọn.",
            "Make a button on the real remote open another app, e.g. the YouTube button opens SmartTube. The app reads the button's code as you press it and writes it into the clean Home, whose key service catches the button and opens your app."))
    }
    if (!vm.daCoHomeSach) The(t("Cần Home sạch", "Needs the clean Home"), Icons.Rounded.Info) {
        Phu(if (vm.khongCaiHome) t("Máy này không cài được Home sạch (Fire TV hoặc Android 7 trở xuống) nên không gán nút được.",
                "This device can't run the clean Home (Fire TV or Android 7 and older), so buttons can't be remapped.")
            else t("Gán nút chạy nhờ dịch vụ phím của Home sạch. Cài Home sạch trước rồi quay lại đây.",
                "Remapping works through the clean Home's key service. Install the clean Home first, then come back."))
        if (!vm.khongCaiHome) NutChinh(t("Cài Home sạch", "Install the clean Home"), enabled = !vm.dangBan, icon = Icons.Rounded.Home) { vm.boQuaDon(Man.CHINH) }
    } else {
        The(t("Nút đã gán", "Mapped buttons"), Icons.Rounded.SmartButton) {
            if (vm.phimRieng.isEmpty()) Phu(t("Chưa gán nút nào.", "No buttons mapped yet."))
            vm.phimRieng.forEach { m ->
                Row(Modifier.fillMaxWidth().clip(RoundedCornerShape(14.dp)).background(The2).padding(start = 12.dp), verticalAlignment = Alignment.CenterVertically) {
                    Column(Modifier.weight(1f).padding(vertical = 10.dp)) {
                        Text(t("Nút ", "") + GanPhim.tenNut(m) + " → " + g.tenApp(PhimRieng.dichCua(m)), fontWeight = FontWeight.SemiBold)
                        Phu(m)
                    }
                    TextButton(onClick = { g.bo(m) }, enabled = !vm.dangBan) { Text(t("Bỏ", "Remove")) }
                }
            }
        }

        The(t("Gán nút mới", "Map a button"), Icons.Rounded.TouchApp) {
            Phu(t("1. Bấm Dò nút, rồi trong 20 giây bấm nút cần gán trên remote của tivi (VD nút YouTube). Lúc dò, nút vẫn làm việc cũ trên tivi, không sao.",
                "1. Tap Detect, then within 20 seconds press the button on the TV's remote (e.g. YouTube). While detecting the button still does its old job on the TV; that's fine."))
            NutPhu(if (g.nut == null) t("Dò nút", "Detect button") else t("Dò lại", "Detect again"), Icons.Rounded.TouchApp, enabled = !vm.dangBan) { g.doNut() }
            g.nut?.let { n ->
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Icon(Icons.Rounded.CheckCircle, null, Modifier.size(18.dp), tint = Xanh); Spacer(Modifier.width(8.dp))
                    Column {
                        Text(t("Đã nhận nút ", "Got button ") + GanPhim.tenNut(n), fontWeight = FontWeight.SemiBold)
                        Phu(listOfNotNull(n.tenThietBi.ifBlank { null }, t("mã quét ${n.maQuet}", "scan code ${n.maQuet}"),
                            n.usage?.let { "usage 0x%06x".format(it) }).joinToString(" · "))
                    }
                }
                Phu(t("2. Chọn app nút này sẽ mở:", "2. Pick the app this button opens:"))
                val dau = g.dsApp.filter { it in PhimRieng.GOI_SMARTTUBE || it == g.goi }
                dau.forEach { p -> LuaChon(g.goi == p, g.tenApp(p), p) { g.goi = p } }
                if (g.dsApp.none { it in PhimRieng.GOI_SMARTTUBE }) Phu(t("Tivi chưa có SmartTube. Cài ở mục Cài app (file APK hoặc đường link tải từ github.com/yuliskov/SmartTube) rồi mở lại màn này.",
                    "SmartTube isn't on the TV. Install it under Install apps (APK file or a download link from github.com/yuliskov/SmartTube), then reopen this screen."), Cam)
                val khac = g.dsApp.filter { it !in dau }
                if (khac.isNotEmpty()) Gap(t("App khác (${khac.size})", "Other apps (${khac.size})"), Icons.Rounded.Apps) {
                    khac.forEach { p -> LuaChon(g.goi == p, g.tenApp(p), p) { g.goi = p } }
                }
                if (g.laCvte) Hang(t("Tivi đã khởi động lại sau khi cài Home sạch", "The TV was restarted after installing the clean Home"),
                    t("Bo mạch CVTE: bật dịch vụ phím ngay lượt đổi Home từng làm một tivi treo ở màn khởi động. Tắt mở tivi một lần, thấy Home sạch lên bình thường rồi mới bật.",
                        "CVTE board: turning on the key helper in the same pass as the Home change once hung a TV on boot. Restart the TV once and check the clean Home comes up first."),
                    g.daKhoiDongLai) { g.daKhoiDongLai = it }
                NutChinh(t("Lưu và áp dụng", "Save and apply"), enabled = !vm.dangBan && g.goi != null && (!g.laCvte || g.daKhoiDongLai), icon = Icons.Rounded.Check) { g.luu() }
            }
        }

        g.vuaGan?.let { (n, p) ->
            The(t("Bấm thử", "Test it"), Icons.Rounded.PlayCircle) {
                Phu(t("Bấm Thử nút, rồi bấm nút ${GanPhim.tenNut(n)} trên remote. App xem ${g.tenApp(p)} có mở ra trên tivi không.",
                    "Tap Test, then press ${GanPhim.tenNut(n)} on the remote. The app checks that ${g.tenApp(p)} opens on the TV."))
                NutPhu(t("Thử nút", "Test button"), Icons.Rounded.PlayCircle, enabled = !vm.dangBan) { g.thu() }
                if (g.thuOk == true) Row(verticalAlignment = Alignment.CenterVertically) {
                    Icon(Icons.Rounded.CheckCircle, null, Modifier.size(16.dp), tint = Xanh); Spacer(Modifier.width(6.dp))
                    Text(t("Nút chạy đúng", "The button works"), fontSize = 13.sp, color = Xanh)
                }
            }
        }
        if (g.bao.isNotEmpty()) Phu(g.bao, if (g.thuOk == false) Cam else Xanh)
        val coYouTube = vm.trangThai?.let { GanPhim.YOUTUBE in it.caiDat && GanPhim.YOUTUBE !in it.daTat } == true
        if (g.goiYTatYouTube || (coYouTube && vm.phimRieng.any { PhimRieng.dichCua(it) in PhimRieng.GOI_SMARTTUBE }))
            TextButton(onClick = { vm.qlApp.mo() }, enabled = !vm.dangBan) {
                Icon(Icons.Rounded.Apps, null, Modifier.size(18.dp)); Spacer(Modifier.width(6.dp))
                Text(t("Tắt YouTube gốc ở Quản lý app", "Disable the stock YouTube in Manage apps"))
            }
        if (vm.laTclGoogleTv) Phu(t("TCL Google TV: khi dịch vụ phím chạy, giữ nút âm lượng chỉ nhảy từng nấc (lỗi của hãng). Bỏ hết nút gán thì trở lại như cũ.",
            "TCL Google TV: while the key service runs, holding volume steps once per press (a firmware quirk). Removing all mapped buttons puts it back."), Cam)
        Phu(t("Remote gửi mã riêng cho từng nút thì gán theo mã quét (chỉ đúng nút đó); remote Bluetooth gửi mã chung thì gán theo tên phím Android. Nút gán giữ lại khi Đổi giao diện; Khôi phục tivi gỡ Home sạch thì mất theo. Mỗi lần lưu, Home sạch đặt lại vài chỉnh sửa làm ngay trên tivi (số cột, hiệu ứng, độ trong dock) theo app.",
            "Remotes that send a code per button are mapped by scan code (that button only); Bluetooth remotes sharing one code are mapped by Android key name. Mappings survive Change look; restoring the TV removes the clean Home and them with it. Each save makes the clean Home reset a few on-TV tweaks (columns, effects, dock opacity) to the app's values."))
    }
}
