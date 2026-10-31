package vn.tivikit.ui

import androidx.compose.foundation.background
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.*
import androidx.compose.material3.*
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import vn.tivikit.dulieu.MapVoice
import vn.tivikit.t

/**
 * Màn Map Voice (29/10/2026): nói vào nút mic của remote như thường, câu tìm nhạc / video mở bằng app YouTube đã chọn
 * (SmartTube, TizenTube, YouTube gốc), câu mở kênh mở bằng VTVgo / TV360 / FPT Play, câu riêng (31/10/2026) mở bằng app đã
 * đặt hay để Trợ lý Google làm.
 */
@Composable
internal fun ManMapVoice(vm: PhienLamViec) = Cuon {
    val m = vm.mapVoice
    Column(Modifier.fillMaxWidth(), verticalArrangement = Arrangement.spacedBy(6.dp)) {
        Text("Map Voice", style = KieuTieuDeLon)
        Phu(t("Bấm nút mic trên remote và nói như thường: \"mở nhạc piano trên YouTube\" thì tivi mở SmartTube hay TizenTube (không quảng cáo) thay vì YouTube gốc; \"mở VTV3\" thì mở kênh bằng VTVgo, TV360 hay FPT Play. Câu khác (tắt tivi, thời tiết, giá vàng…) vẫn do Trợ lý Google làm; câu riêng chọn được câu nào mở bằng app nào. Map Voice chạy hẳn trên tivi, không cần điện thoại khi dùng.",
            "Press the remote's mic button and speak as usual: \"play piano music on YouTube\" opens SmartTube or TizenTube (no ads) instead of the official YouTube; \"open VTV3\" opens the channel in VTVgo, TV360 or FPT Play. Anything else (turn off, weather, gold price…) is still handled by the Google Assistant; custom phrases let you pick which request opens which app. Map Voice runs on the TV itself; no phone is needed once it's set up."))
    }
    if (!m.duocCai) CanhBao(t("Map Voice cần Android 8 trở lên; tivi này là Android ${vm.thongTin?.android}.",
        "Map Voice needs Android 8 or later; this TV runs Android ${vm.thongTin?.android}."))
    if (m.dsYouTube.none { it != MapVoice.YOUTUBE_GOC }) {
        CanhBao(t("Tivi chưa có SmartTube hay TizenTube: câu tìm nhạc, video chỉ mở được YouTube gốc (nếu chọn). Cài SmartTube ở Cài app → Gợi ý.",
            "Neither SmartTube nor TizenTube is on this TV, so music and video requests can only go to the official YouTube (if chosen). Install SmartTube from Install apps → Suggestions."))
        NutPhu(t("Mở Cài app", "Open Install apps"), Icons.Rounded.AddToQueue, enabled = !vm.dangBan) { vm.moCaiApk() }
    }
    if (!m.coTroLy) CanhBao(t("Tivi không có Trợ lý Google (com.google.android.katniss): Map Voice đọc câu nói từ Trợ lý nên chưa dùng được trên tivi này.",
        "This TV has no Google Assistant (com.google.android.katniss): Map Voice reads what you say from the Assistant, so it can't work here yet."))
    else if (!m.troLyBat) {
        CanhBao(t("Trợ lý Google trên tivi đang tắt (tắt lúc dọn tivi hay làm nhẹ máy), nên nút mic không nghe gì. Bật lại để dùng Map Voice.",
            "The Google Assistant is disabled on the TV (during clean-up or to save memory), so the mic button hears nothing. Turn it back on to use Map Voice."))
        NutPhu(t("Bật lại Trợ lý Google", "Turn the Google Assistant back on"), Icons.Rounded.Mic, enabled = !vm.dangBan) { m.batTroLy() }
    }
    m.appTrung.forEach { goi ->
        CanhBao(t("Tivi có app $goi làm cùng việc: một câu sẽ bị hai app xử lý. Nên tắt app đó.",
            "The TV has $goi, which does the same job: each request would be handled twice. Disable that app."))
        NutPhu(t("Tắt $goi", "Disable $goi"), Icons.Rounded.Block, enabled = !vm.dangBan) { m.tatAppTrung(goi) }
    }

    m.trangThai?.let { tt ->
        The(t("Trên tivi", "On the TV"), Icons.Rounded.Mic) {
            Dong(tt.dichVuChay, t("Dịch vụ Map Voice đang chạy", "The Map Voice service is running"),
                t("Dịch vụ Map Voice đang tắt: bấm Bật lại bên dưới", "The Map Voice service is off: tap Turn on again below"))
            if (tt.dichVuChay) Dong(tt.adb == "da-noi", MapVoiceTivi.moTaAdb(tt.adb), MapVoiceTivi.moTaAdb(tt.adb))
            if (!tt.cauHinh.bat) Phu(t("Đang tạm tắt: câu nói về Trợ lý Google như thường.", "Paused: requests go to the Google Assistant as usual."), Cam)
            if (tt.appYouTube.isNotEmpty()) Phu(t("Mở YouTube bằng ", "YouTube opens in ") + m.tenApp(tt.appYouTube) + " · " +
                t("${tt.soKenh} kênh truyền hình", "${tt.soKenh} TV channels") +
                (if (tt.cauHinh.cauRieng.isEmpty()) "" else t(" · ${tt.cauHinh.cauRieng.size} câu riêng", " · ${tt.cauHinh.cauRieng.size} custom phrases")))
            if (tt.dichVuChay && tt.adb in listOf("tu-choi", "khong-co", "loi")) NutPhu(t("Nối lại", "Reconnect"), Icons.Rounded.Sync, enabled = !vm.dangBan) { m.noiLai() }
            if (tt.nhatKy.isNotEmpty()) Gap(t("Câu vừa nghe", "Recently heard"), Icons.Rounded.History) {
                tt.nhatKy.reversed().forEach { n ->
                    Text("\"${n.cau}\"", fontSize = 14.sp, fontWeight = FontWeight.SemiBold)
                    Phu("${vn.tivikit.dulieu.HenGio.gioCua(n.luc, java.time.ZoneId.systemDefault()).first} · ${MapVoiceTivi.moTa(n.viec, m::tenApp)}")
                }
            }
            TextButton(onClick = { m.lamMoi() }, enabled = !vm.dangBan) {
                Icon(Icons.Rounded.Refresh, null, Modifier.size(18.dp)); Spacer(Modifier.width(4.dp)); Text(t("Làm mới", "Refresh"))
            }
        }
    }

    TheCaiDat(vm, m)
    TheCauRieng(vm, m)
    if (m.daCai) NutPhu(t("Lưu lên tivi", "Save to the TV"), Icons.Rounded.Check, enabled = !vm.dangBan && m.doiChuaLuu) { m.luu() }
    else Phu(t("Cài đặt này gửi lên tivi khi bấm Bật Map Voice.", "These settings go to the TV when you tap Turn on Map Voice."))
    if (m.trangThai?.dichVuChay == true) TheThu(vm, m)

    val canBat = !m.daCai || m.trangThai?.dichVuChay != true
    NutChinh(if (!m.daCai) t("Bật Map Voice", "Turn on Map Voice") else if (canBat) t("Bật lại", "Turn on again") else t("Cài lại / cập nhật", "Reinstall / update"),
        enabled = !vm.dangBan && m.duocCai, icon = Icons.Rounded.Mic) { m.bat() }
    if (m.bao.isNotEmpty()) Phu(m.bao, Xanh)

    The(t("Cách dùng", "How to use"), Icons.Rounded.Info) {
        Phu(t("1. Bấm Bật Map Voice. Lần đầu tivi hiện hộp \"Cho phép gỡ lỗi?\": tích Luôn cho phép rồi chọn OK bằng remote (cho riêng Map Voice, chỉ một lần).",
            "1. Tap Turn on Map Voice. The first time, the TV shows \"Allow debugging?\": tick Always allow and press OK with the remote (just for Map Voice, only once)."))
        Phu(t("2. Bấm nút mic trên remote, nói như với Trợ lý Google: \"mở nhạc piano trên YouTube\", \"nhạc Trịnh Công Sơn\", \"tìm hoạt hình trên YouTube\", \"mở VTV3\", \"xem kênh HTV7 trên TV360\".",
            "2. Press the remote's mic button and talk as you would to the Google Assistant: \"play piano music on YouTube\", \"Trinh Cong Son songs\", \"search cartoons on YouTube\", \"open VTV3\", \"watch HTV7 on TV360\"."))
        Phu(t("3. Câu \"mở / phát / bật …\" phát luôn video đầu tiên; \"tìm …\" hay chỉ nói tên bài thì mở trang tìm kiếm của app YouTube đã chọn (đổi ở mục Cách chuyển). Trợ lý tự mở YouTube gốc cho câu khác thì Map Voice cũng chuyển sang app đó.",
            "3. \"Open / play …\" plays the first video; \"search …\" or just a title opens the chosen YouTube app's search page (change it under How requests move). If the Assistant opens the official YouTube for some other request, Map Voice moves that to the chosen app too."))
        Phu(t("4. Câu riêng: câu có chữ đã đặt (VD \"giá vàng\") thì làm theo câu riêng trước hết: để Trợ lý Google trả lời, tìm bằng một app YouTube, hay mở một app (FPT Play, ô Kênh nhanh…).",
            "4. Custom phrases: a request containing the words you set (e.g. \"gold price\") follows that phrase first: left to the Google Assistant, searched in a YouTube app, or opening an app (FPT Play, a Quick channel tile…)."))
        Phu(t("Map Voice cần ADB qua mạng của tivi luôn bật (cổng 5555): đừng bấm Tắt ADB trong TiviKit. Tivi chỉ có Gỡ lỗi không dây (mã ghép 6 số) thì chưa dùng được. YouTube gốc có thể hiện lên chừng một giây rồi mới sang SmartTube.",
            "Map Voice needs the TV's network ADB to stay on (port 5555): don't tap Turn off ADB in TiviKit. TVs with only Wireless debugging (6-digit pairing) can't use it yet. The official YouTube may flash for about a second before SmartTube."))
    }

    var hoiGo by remember { mutableStateOf(false) }
    if (m.daCai) TextButton(onClick = { hoiGo = true }, enabled = !vm.dangBan) {
        Icon(Icons.Rounded.DeleteOutline, null, Modifier.size(18.dp)); Spacer(Modifier.width(6.dp)); Text(t("Gỡ Map Voice khỏi tivi", "Remove Map Voice from the TV"))
    }
    if (hoiGo) AlertDialog(
        onDismissRequest = { hoiGo = false },
        icon = { Icon(Icons.Rounded.DeleteOutline, null, tint = Cam) },
        title = { Text(t("Gỡ Map Voice?", "Remove Map Voice?")) },
        text = { Text(t("Câu nói về lại Trợ lý Google như trước (tìm trên YouTube gốc). Cài lại thì tivi hỏi \"Cho phép gỡ lỗi?\" lần nữa.",
            "Voice requests go back to the Google Assistant (the official YouTube). After reinstalling, the TV asks \"Allow debugging?\" again.")) },
        confirmButton = { TextButton(onClick = { hoiGo = false; m.go() }) { Text(t("Gỡ", "Remove"), color = Cam) } },
        dismissButton = { TextButton(onClick = { hoiGo = false }) { Text(t("Thôi", "Cancel")) } },
    )
    Phu(t("Bật sẽ cài app nhỏ \"Map Voice (TiviKit)\" và bật dịch vụ trợ năng của nó (để chạy suốt, được mở SmartTube / TizenTube / VTVgo / TV360 / FPT Play và biết YouTube gốc có vừa hiện lên; không đọc nội dung màn hình, không bắt phím). App nối ADB của chính tivi bằng khoá riêng, chỉ đọc dòng nhật ký Trợ lý ghi câu vừa nghe, rồi đóng Trợ lý và YouTube gốc khi chuyển câu. 15 câu gần nhất lưu trên tivi để xem ở đây. Tìm video đầu tiên thì tivi hỏi trang tìm kiếm của YouTube. Cách đọc câu theo app Super Voice; Trợ lý Google đổi cách ghi nhật ký thì có thể hỏng tới khi TiviKit cập nhật. Gỡ hoặc Khôi phục tivi là xoá hết.",
        "Turning it on installs the small \"Map Voice (TiviKit)\" app and turns on its accessibility service (to keep running, be allowed to open SmartTube / TizenTube / VTVgo / TV360 / FPT Play and know if the official YouTube just appeared; it doesn't read the screen or catch keys). The app connects to the TV's own ADB with its own key, reads only the log line where the Assistant writes what it heard, and closes the Assistant and the official YouTube when it moves a request. The last 15 requests are kept on the TV so you can see them here. To play the first video the TV asks YouTube's search page. The idea comes from the Super Voice app; if the Google Assistant changes how it logs, this may break until TiviKit is updated. Remove or Restore deletes everything."))
}

/** Cách chuyển: bật / tạm tắt, app mở YouTube, phát luôn hay tìm, câu không nói YouTube, kênh truyền hình. */
@Composable
private fun TheCaiDat(vm: PhienLamViec, m: MapVoiceTivi) = The(t("Cách chuyển", "How requests move"), Icons.Rounded.Tune) {
    val c = m.cauHinh
    Hang(t("Bật Map Voice", "Map Voice on"), t("Tắt thì câu nói về Trợ lý Google như thường, app vẫn cài", "Off sends requests to the Google Assistant as usual; the app stays installed"), c.bat) {
        m.cauHinh = c.copy(bat = it)
    }
    if (m.dsYouTube.isNotEmpty()) {
        Text(t("Mở YouTube bằng", "Open YouTube in"), fontWeight = FontWeight.SemiBold)
        // Chưa chọn (hay app đã chọn không còn): app phụ dùng SmartTube / TizenTube đầu tiên.
        val dangChon = c.appYouTube.takeIf { it in m.dsYouTube } ?: m.dsYouTube.firstOrNull { it != MapVoice.YOUTUBE_GOC }
        Row(Modifier.horizontalScroll(rememberScrollState()), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            m.dsYouTube.forEach { g -> ChonChip(m.tenApp(g), dangChon == g) { m.cauHinh = c.copy(appYouTube = g) } }
        }
        if (dangChon == MapVoice.YOUTUBE_GOC) Phu(t("YouTube gốc có quảng cáo; Map Voice chỉ đóng Trợ lý rồi mở YouTube gốc với đúng chữ tìm.",
            "The official YouTube has ads; Map Voice just closes the Assistant and opens the official YouTube with the search words."))
    }
    Text(t("Câu tìm nhạc, video", "Music and video requests"), fontWeight = FontWeight.SemiBold)
    LuaChon(c.cachMo == MapVoice.CachMo.THEO_CAU, t("Theo câu nói", "By what you say"),
        t("\"mở / phát / bật …\" phát luôn video đầu tiên, \"tìm …\" mở trang tìm kiếm", "\"open / play …\" plays the first video, \"search …\" opens the search page")) {
        m.cauHinh = c.copy(cachMo = MapVoice.CachMo.THEO_CAU)
    }
    LuaChon(c.cachMo == MapVoice.CachMo.PHAT_LUON, t("Luôn phát video đầu tiên", "Always play the first video"), null) { m.cauHinh = c.copy(cachMo = MapVoice.CachMo.PHAT_LUON) }
    LuaChon(c.cachMo == MapVoice.CachMo.TIM_KIEM, t("Luôn mở trang tìm kiếm", "Always open the search page"), t("Chọn video bằng remote", "Pick the video with the remote")) {
        m.cauHinh = c.copy(cachMo = MapVoice.CachMo.TIM_KIEM)
    }
    Hang(t("Cả câu không nói \"YouTube\"", "Even without saying \"YouTube\""),
        t("VD \"nhạc piano\", \"bài hát …\", \"video …\", \"karaoke …\", \"hoạt hình\"", "e.g. \"nhạc piano\", \"bài hát …\", \"video …\", \"karaoke …\", \"hoạt hình\""), c.nhacVideo) {
        m.cauHinh = c.copy(nhacVideo = it)
    }
    Hang(t("Mở kênh truyền hình", "Open TV channels"), t("VD \"mở VTV3\", \"xem kênh HTV7 trên TV360\"", "e.g. \"mở VTV3\", \"xem kênh HTV7 trên TV360\""), c.kenh) {
        m.cauHinh = c.copy(kenh = it)
    }
    if (c.kenh) {
        // App xem kênh có trên tivi, theo thứ tự hiện; app đã chọn không có thì app phụ dùng app có trước.
        val coKenh = listOfNotNull(MapVoice.APP_VTVGO.takeIf { m.coVtvGo }, MapVoice.APP_TV360.takeIf { m.coTv360 }, MapVoice.APP_FPT.takeIf { m.coFpt })
        if (coKenh.isEmpty()) Phu(t("Tivi chưa có VTVgo, TV360 hay FPT Play bản tivi: câu mở kênh để Trợ lý làm.", "The TV has no VTVgo, TV360 or FPT Play for TV: channel requests are left to the Assistant."))
        else {
            Text(t("App mở kênh", "Channel app"), fontWeight = FontWeight.SemiBold)
            val dangChon = c.appKenh.takeIf { it in coKenh } ?: coKenh.first()
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                coKenh.forEach { a ->
                    ChonChip(when (a) { MapVoice.APP_TV360 -> "TV360"; MapVoice.APP_FPT -> "FPT Play"; else -> "VTVgo" }, dangChon == a) { m.cauHinh = c.copy(appKenh = a) }
                }
            }
            Phu(t("App này không có kênh vừa nói thì mở bằng app khác có kênh đó (VD HTV7 không có trên VTVgo). Nói \"… trên TV360\" / \"… trên VTVgo\" / \"… trên FPT Play\" để chọn app cho một lần. FPT Play cần đăng nhập trên tivi.",
                "If this app lacks the channel, another app that has it opens it (e.g. HTV7 isn't on VTVgo). Say \"… on TV360\" / \"… on VTVgo\" / \"… on FPT Play\" to pick the app once. FPT Play needs a login on the TV."))
        }
    }
}

/** Câu riêng (31/10/2026): câu có chữ … thì để Trợ lý Google làm, tìm bằng một app YouTube, hay mở một app; xét trước hết. */
@Composable
private fun TheCauRieng(vm: PhienLamViec, m: MapVoiceTivi) = The(t("Câu riêng", "Custom phrases"), Icons.Rounded.RecordVoiceOver) {
    Phu(t("Câu nói có chữ đặt ở đây thì làm theo câu riêng, trước mọi cách chuyển ở trên. VD \"giá vàng\" → Trợ lý Google (trả lời giá vàng), \"karaoke\" → TizenTube, \"phim\" → FPT Play. Chữ có dấu thì phải nói đúng dấu; gõ không dấu thì khớp cả có dấu.",
        "A request containing the words set here follows that phrase, before every rule above. E.g. \"giá vàng\" (gold price) → Google Assistant, \"karaoke\" → TizenTube, \"phim\" (films) → FPT Play. Words with Vietnamese accents must match the accents; words typed without accents match either way."))
    val c = m.cauHinh
    c.cauRieng.forEach { r ->
        Row(Modifier.fillMaxWidth().clip(RoundedCornerShape(12.dp)).background(The2).padding(start = 12.dp), verticalAlignment = Alignment.CenterVertically) {
            Text("\"${r.chu}\" → ${m.tenDich(r.dich)}", fontSize = 14.sp, modifier = Modifier.weight(1f).padding(vertical = 10.dp))
            IconButton(onClick = { m.boCauRieng(r) }, enabled = !vm.dangBan) { Icon(Icons.Rounded.Close, t("Bỏ câu riêng", "Remove"), tint = ChuPhu) }
        }
    }
    OutlinedTextField(m.chuMoi, { m.chuMoi = it.take(MapVoice.CHU_TOI_DA) }, modifier = Modifier.fillMaxWidth(), singleLine = true, shape = RoundedCornerShape(12.dp),
        label = { Text(t("Chữ trong câu nói, VD giá vàng", "Words in the request, e.g. giá vàng")) })
    Text(t("Thì", "Then"), fontWeight = FontWeight.SemiBold)
    var chonApp by remember { mutableStateOf(false) }
    val dich = m.dichMoi
    val dichYt = m.dsYouTube
    Row(Modifier.horizontalScroll(rememberScrollState()), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
        ChonChip(t("Trợ lý Google", "Google Assistant"), dich == MapVoice.DICH_TRO_LY) { m.dichMoi = MapVoice.DICH_TRO_LY }
        dichYt.forEach { g -> ChonChip(t("Tìm trên ", "Search in ") + m.tenApp(g), dich == g) { m.dichMoi = g } }
        val khac = dich != MapVoice.DICH_TRO_LY && dich != MapVoice.DICH_YOUTUBE && dich !in dichYt
        ChonChip(if (khac) t("Mở ", "Open ") + m.tenApp(dich) else t("Mở app khác…", "Open another app…"), khac) { chonApp = true }
    }
    val du = c.cauRieng.size >= MapVoice.CAU_RIENG_TOI_DA
    NutPhu(t("Thêm câu riêng", "Add custom phrase"), Icons.Rounded.Add, enabled = !vm.dangBan && m.chuMoi.isNotBlank() && !du) { m.themCauRieng() }
    if (du) Phu(t("Đã đủ ${MapVoice.CAU_RIENG_TOI_DA} câu riêng: bỏ bớt một câu để thêm câu mới.",
        "All ${MapVoice.CAU_RIENG_TOI_DA} custom phrases are used: remove one to add another."), Cam)
    if (chonApp) AlertDialog(
        onDismissRequest = { chonApp = false },
        confirmButton = { TextButton(onClick = { chonApp = false }) { Text(t("Đóng", "Close")) } },
        title = { Text(t("Mở app nào?", "Open which app?")) },
        text = {
            LazyColumn(Modifier.heightIn(max = 420.dp)) {
                items(m.dsAppMo) { g ->
                    LuaChon(m.dichMoi == g, m.tenApp(g), g) { m.dichMoi = g; chonApp = false }
                }
            }
        },
        containerColor = The1,
    )
    if (c.cauRieng.isNotEmpty() || m.chuMoi.isNotBlank()) Phu(if (m.daCai) t("Bấm Lưu lên tivi để tivi dùng câu riêng.", "Tap Save to the TV to use the custom phrases.")
        else t("Câu riêng gửi lên tivi khi bấm Bật Map Voice.", "Custom phrases go to the TV when you tap Turn on Map Voice."))
}

/** Thử một câu gõ tay: xem Map Voice hiểu thế nào, hay chạy luôn trên tivi như vừa nói. */
@Composable
private fun TheThu(vm: PhienLamViec, m: MapVoiceTivi) = The(t("Thử một câu", "Try a request"), Icons.Rounded.Science) {
    OutlinedTextField(m.cauThu, { m.cauThu = it.take(200) }, modifier = Modifier.fillMaxWidth(), singleLine = true, shape = RoundedCornerShape(12.dp),
        label = { Text(t("VD mở nhạc piano trên YouTube", "e.g. mở nhạc piano trên YouTube")) })
    val coCau = m.cauThu.isNotBlank()
    NutPhu(t("Xem Map Voice hiểu thế nào", "See how Map Voice reads it"), Icons.Rounded.Visibility, enabled = !vm.dangBan && coCau) { m.xem() }
    NutPhu(t("Chạy trên tivi", "Run it on the TV"), Icons.Rounded.PlayArrow, enabled = !vm.dangBan && coCau) { m.thu() }
    m.ketQuaThu?.let { Text(MapVoiceTivi.moTa(it, m::tenApp), fontWeight = FontWeight.SemiBold, color = if (it.loai == "loi") Cam else ChuChinh) }
    if (m.doiChuaLuu) Phu(t("Tivi dùng cài đặt đã lưu: bấm Lưu lên tivi trước nếu vừa đổi.", "The TV uses the saved settings: tap Save to the TV first if you just changed them."), Cam)
}

@Composable
private fun CanhBao(chu: String) = Row(Modifier.fillMaxWidth().clip(RoundedCornerShape(14.dp)).background(NenCanhBao).padding(12.dp)) {
    Icon(Icons.Rounded.Warning, null, tint = Cam); Spacer(Modifier.width(8.dp))
    Phu(chu, ChuChinh)
}

@Composable
private fun Dong(ok: Boolean, coChu: String, khongChu: String) = Row(verticalAlignment = Alignment.CenterVertically) {
    Icon(if (ok) Icons.Rounded.CheckCircle else Icons.Rounded.Warning, null, Modifier.size(16.dp), tint = if (ok) Xanh else Cam)
    Spacer(Modifier.width(6.dp))
    Text(if (ok) coChu else khongChu, fontSize = 13.sp, color = if (ok) Xanh else Cam)
}
