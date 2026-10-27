package vn.tivikit.launcher

/**
 * Làm mờ mảng điểm ảnh ARGB [px] (rộng [w], cao [h]) tại chỗ: [luot] lượt hộp ngang rồi dọc bán kính [r], gần với làm mờ
 * Gauss. Chạy trên ảnh nền đã thu nhỏ 1/8, một lần mỗi khi đổi ảnh nền, nên kính mờ chạy được cả trên tivi Android 8–11
 * (làm mờ trực tiếp bằng RenderEffect chỉ có từ Android 12).
 */
fun lamMo(px: IntArray, w: Int, h: Int, r: Int, luot: Int = 3) {
    if (r <= 0 || w <= 0 || h <= 0) return
    require(px.size >= w * h)
    val tam = IntArray(px.size)
    repeat(luot) {
        hop(px, tam, w, h, r, ngang = true)
        hop(tam, px, w, h, r, ngang = false)
    }
}

/** Một lượt trung bình trượt cửa sổ 2r+1 theo hàng (ngang) hoặc cột; ra ngoài mép thì lấy điểm ở mép. */
private fun hop(vao: IntArray, ra: IntArray, w: Int, h: Int, r: Int, ngang: Boolean) {
    val dai = if (ngang) w else h
    val soDong = if (ngang) h else w
    val buoc = if (ngang) 1 else w
    val n = 2 * r + 1
    for (d in 0 until soDong) {
        val goc = if (ngang) d * w else d
        var a = 0
        var rr = 0
        var g = 0
        var b = 0
        for (i in -r..r) {
            val p = vao[goc + i.coerceIn(0, dai - 1) * buoc]
            a += p ushr 24; rr += (p shr 16) and 0xFF; g += (p shr 8) and 0xFF; b += p and 0xFF
        }
        for (i in 0 until dai) {
            ra[goc + i * buoc] = ((a / n) shl 24) or ((rr / n) shl 16) or ((g / n) shl 8) or (b / n)
            val bo = vao[goc + (i - r).coerceIn(0, dai - 1) * buoc]
            val them = vao[goc + (i + r + 1).coerceIn(0, dai - 1) * buoc]
            a += (them ushr 24) - (bo ushr 24)
            rr += ((them shr 16) and 0xFF) - ((bo shr 16) and 0xFF)
            g += ((them shr 8) and 0xFF) - ((bo shr 8) and 0xFF)
            b += (them and 0xFF) - (bo and 0xFF)
        }
    }
}
