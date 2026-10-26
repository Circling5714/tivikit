package vn.tivikit.ui

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.SystemUpdate
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateListOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import vn.tivikit.adb.AppTrenTv
import vn.tivikit.dulieu.GoiY
import vn.tivikit.t

/**
 * Cập nhật app đã cài (25/10/2026): với các app gợi ý tải thẳng (SmartTube, Kodi, VLC…) đang có trên tivi, so phiên bản
 * trên tivi với bản mới nhất ở nơi tác giả phát hành; app nào có bản mới thì tải về và cài đè (giữ dữ liệu, đăng nhập).
 * App từ CH Play thì CH Play của tivi tự cập nhật.
 */
class CapNhatApp(private val vm: PhienLamViec) {
    /** Một app trên tivi: bản đang cài, bản mới nhất ([moi] null + [loi] khi không kiểm tra được). */
    class Dong(val app: GoiY.App, val goi: String, val banTv: String, val moi: GoiY.BanMoi?, val loi: String?) {
        /** true có bản mới, false đã mới nhất, null không so được số phiên bản. */
        val coBanMoi: Boolean? get() = moi?.let { GoiY.moiHon(it.phienBan, banTv) }
    }

    val ds = mutableStateListOf<Dong>()
    var daKiemTra by mutableStateOf(false)
        private set

    fun kiemTra() = vm.chay(t("Đang kiểm tra bản mới…", "Checking for new versions…")) {
        vm.damBaoKetNoi()
        val tt = vm.io { vm.tv.docTrangThai() }
        vm.trangThai = tt
        val abi = vm.layAbi()
        val cai = GoiY.DS.filter { !it.laCHPlay }.mapNotNull { g -> g.goi.firstOrNull { it in tt.caiDat }?.let { g to it } }
        val ban = vm.io { AppTrenTv(vm.adb).danhSach(cai.map { it.second }) }.associateBy { it.pkg }
        val moi = cai.map { (g, goi) ->
            vm.thongBao = t("Đang kiểm tra ${g.ten}…", "Checking ${g.ten}…")
            val r = runCatching { vm.io { GoiY.timBanMoi(g, abi) } }
            Dong(g, goi, ban[goi]?.versionName.orEmpty(), r.getOrNull(), r.exceptionOrNull()?.message)
        }
        ds.clear(); ds.addAll(moi)
        daKiemTra = true
    }

    /** Tải bản mới của các [dong] rồi cài đè lần lượt; xong đọc lại để thấy bản mới trên tivi. */
    fun capNhat(dong: List<Dong>) = vm.chay(t("Đang cập nhật…", "Updating…")) {
        vm.damBaoKetNoi()
        for (d in dong) {
            val moi = d.moi ?: continue
            val a = vm.themVaoDs(vm.taiVe(moi.url, t("Đang tải ${d.app.ten}…", "Downloading ${d.app.ten}…"), d.app.ten))
            if (!vm.caiMot(a, goTruoc = false)) break
        }
        val ban = vm.io { AppTrenTv(vm.adb).danhSach(ds.map { it.goi }) }.associateBy { it.pkg }
        val moi = ds.map { Dong(it.app, it.goi, ban[it.goi]?.versionName ?: it.banTv, it.moi, it.loi) }
        ds.clear(); ds.addAll(moi)
    }

    fun quen() { ds.clear(); daKiemTra = false }
}

/** Thẻ "Cập nhật app trên tivi" của màn Cài app. */
@Composable
internal fun TheCapNhat(vm: PhienLamViec) {
    val cn = vm.capNhatApp
    The(t("Cập nhật app trên tivi", "Update apps on the TV"), Icons.Rounded.SystemUpdate) {
        Phu(t("So SmartTube, Kodi, VLC… trên tivi với bản mới nhất nơi tác giả phát hành; cập nhật cài đè, giữ dữ liệu và đăng nhập. App từ CH Play do CH Play của tivi tự cập nhật.",
            "Compares SmartTube, Kodi, VLC… on the TV with the latest release from their authors; updates install over the top, keeping data and sign-ins. Play Store apps are updated by the TV's Play Store."))
        if (cn.daKiemTra) {
            if (cn.ds.isEmpty()) Phu(t("Tivi chưa có app nào trong danh sách gợi ý tải thẳng.", "The TV has none of the suggested direct-download apps."))
            cn.ds.forEach { d -> DongCapNhat(vm, d) }
            val canCapNhat = cn.ds.filter { it.coBanMoi == true }
            if (canCapNhat.size > 1) NutChinh(t("Cập nhật cả ${canCapNhat.size} app", "Update all ${canCapNhat.size} apps"), enabled = !vm.dangBan, icon = Icons.Rounded.SystemUpdate) { cn.capNhat(canCapNhat) }
            else if (cn.ds.isNotEmpty() && canCapNhat.isEmpty() && cn.ds.none { it.coBanMoi == null }) Phu(t("Mọi app đều đang là bản mới nhất.", "Every app is up to date."), Xanh)
        }
        NutPhu(if (cn.daKiemTra) t("Kiểm tra lại", "Check again") else t("Kiểm tra bản mới", "Check for updates"), Icons.Rounded.SystemUpdate, enabled = !vm.dangBan) { cn.kiemTra() }
    }
}

@Composable
private fun DongCapNhat(vm: PhienLamViec, d: CapNhatApp.Dong) {
    Row(Modifier.fillMaxWidth().clip(RoundedCornerShape(12.dp)).background(The2).padding(start = 12.dp, top = 8.dp, bottom = 8.dp),
        verticalAlignment = Alignment.CenterVertically) {
        Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(2.dp)) {
            Text(d.app.ten, fontWeight = FontWeight.SemiBold)
            val moi = d.moi?.phienBan
            Text(when {
                d.moi == null -> t("Không kiểm tra được: ${d.loi ?: "?"}", "Couldn't check: ${d.loi ?: "?"}")
                d.coBanMoi == true -> t("Trên tivi ${d.banTv.ifEmpty { "?" }} → bản mới $moi", "On the TV ${d.banTv.ifEmpty { "?" }} → new $moi")
                d.coBanMoi == false -> t("Bản ${d.banTv} · mới nhất", "Version ${d.banTv} · up to date")
                else -> t("Trên tivi ${d.banTv.ifEmpty { "?" }} · bản mới nhất ${moi ?: d.moi.tep}", "On the TV ${d.banTv.ifEmpty { "?" }} · latest ${moi ?: d.moi.tep}")
            }, fontSize = 13.sp, lineHeight = 18.sp, color = when (d.coBanMoi) { true -> Nhan; false -> Xanh; null -> ChuPhu })
        }
        when {
            d.moi == null -> {}
            d.coBanMoi == false -> Text(t("Mới nhất", "Latest"), fontSize = 13.sp, color = Xanh, modifier = Modifier.padding(end = 12.dp))
            // Không so được số phiên bản: vẫn cho cài bản mới nhất, người dùng tự nhìn số.
            else -> TextButton(onClick = { vm.capNhatApp.capNhat(listOf(d)) }, enabled = !vm.dangBan) {
                Text(if (d.coBanMoi == true) t("Cập nhật", "Update") else t("Cài bản này", "Install it"))
            }
        }
    }
}
