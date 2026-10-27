package vn.tivikit.trentv;

import android.content.Context;
import android.content.pm.ApplicationInfo;
import android.content.pm.PackageInfo;
import android.content.pm.PackageManager;
import android.graphics.Bitmap;
import android.graphics.Canvas;
import android.graphics.drawable.Drawable;
import android.os.Build;
import android.os.Looper;
import android.util.Base64;

import java.io.ByteArrayOutputStream;

/**
 * Tên và ảnh của app trên tivi, hỏi PackageManager của chính tivi (27/10/2026; ý từ tv-remote của HoangTran0410, MIT): một
 * lần chạy cho cả danh sách, thay vì đọc từng APK qua ADB. TiviKit chạy bằng app_process dưới quyền shell (adb/TrenTv.kt):
 *   ten                 mỗi gói một dòng: gói TAB versionCode TAB tên
 *   anh <cạnh> <gói>…  mỗi gói một dòng: gói TAB versionCode TAB B|I TAB ảnh WebP base64 — banner (B) nếu app có, không thì
 *                       biểu tượng (I), giữ tỉ lệ, cạnh dài nhất <cạnh> px (như TenApk.veAnh)
 * Lỗi chung: một dòng "loi TAB lý do". Chữ đi qua Chu.thoat nên cả đầu ra là ASCII.
 */
public final class TenApp {
    private TenApp() { }

    public static void main(String[] args) {
        try {
            Looper.prepareMainLooper();
            PackageManager pm = heThong().getPackageManager();
            if (args.length >= 1 && args[0].equals("ten")) inTen(pm);
            else if (args.length >= 2 && args[0].equals("anh")) inAnh(pm, Integer.parseInt(args[1]), args);
            else System.out.println("loi\tten | anh <canh> <goi>...");
        } catch (Throwable e) {
            System.out.println("loi\t" + Chu.thoat(String.valueOf(e)));
        }
        System.out.flush();
        System.exit(0); // ngữ cảnh hệ thống để lại luồng chạy nền: thoát hẳn
    }

    /** Ngữ cảnh hệ thống dựng ngay trong app_process (đủ để hỏi PackageManager, MediaSessionManager dưới quyền shell). */
    static Context heThong() throws Exception {
        Class<?> at = Class.forName("android.app.ActivityThread");
        Object t = at.getMethod("systemMain").invoke(null);
        return (Context) at.getMethod("getSystemContext").invoke(t);
    }

    @SuppressWarnings("deprecation")
    private static long ma(PackageInfo pi) {
        return Build.VERSION.SDK_INT >= 28 ? pi.getLongVersionCode() : pi.versionCode;
    }

    private static void inTen(PackageManager pm) {
        StringBuilder b = new StringBuilder();
        for (PackageInfo pi : pm.getInstalledPackages(0)) {
            ApplicationInfo ai = pi.applicationInfo;
            if (ai == null) continue;
            String ten;
            try { ten = ai.loadLabel(pm).toString(); } catch (Throwable e) { continue; }
            b.append(pi.packageName).append('\t').append(ma(pi)).append('\t').append(Chu.thoat(ten)).append('\n');
        }
        System.out.print(b);
    }

    private static void inAnh(PackageManager pm, int canh, String[] args) {
        for (int i = 2; i < args.length; i++) {
            String goi = args[i];
            try {
                PackageInfo pi = pm.getPackageInfo(goi, 0);
                ApplicationInfo ai = pi.applicationInfo;
                Drawable d = null;
                if (Build.VERSION.SDK_INT >= 20) try { d = ai.loadBanner(pm); } catch (Throwable ignored) { }
                boolean banner = d != null;
                if (d == null) d = ai.loadIcon(pm);
                System.out.println(goi + '\t' + ma(pi) + '\t' + (banner ? 'B' : 'I') + '\t' + Base64.encodeToString(webp(d, canh), Base64.NO_WRAP));
            } catch (Throwable ignored) {
                // Gói vừa gỡ, ảnh không vẽ được: bỏ qua, TiviKit đọc từ APK như trước.
            }
        }
    }

    /** Vẽ [d] ra bitmap giữ tỉ lệ, cạnh dài nhất [canh] px, nén WebP (vài KB một ảnh, base64 qua shell được). */
    @SuppressWarnings("deprecation")
    static byte[] webp(Drawable d, int canh) {
        int w0 = d.getIntrinsicWidth() > 0 ? d.getIntrinsicWidth() : 192;
        int h0 = d.getIntrinsicHeight() > 0 ? d.getIntrinsicHeight() : 192;
        float ti = Math.min(1f, canh / (float) Math.max(w0, h0));
        int w = Math.max(1, (int) (w0 * ti));
        int h = Math.max(1, (int) (h0 * ti));
        Bitmap bm = Bitmap.createBitmap(w, h, Bitmap.Config.ARGB_8888);
        d.setBounds(0, 0, w, h);
        d.draw(new Canvas(bm));
        ByteArrayOutputStream ra = new ByteArrayOutputStream();
        bm.compress(Bitmap.CompressFormat.WEBP, 90, ra);
        bm.recycle();
        return ra.toByteArray();
    }
}
