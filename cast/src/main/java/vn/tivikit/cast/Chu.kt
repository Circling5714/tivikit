package vn.tivikit.cast

import java.util.Locale

/** Chữ theo ngôn ngữ tivi: tiếng Việt nếu tivi đặt tiếng Việt, không thì tiếng Anh. */
val tiengViet: Boolean get() = Locale.getDefault().language == "vi"

fun t(vi: String, en: String) = if (tiengViet) vi else en
