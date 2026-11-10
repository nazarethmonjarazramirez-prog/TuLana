package mx.cochinito.app;

import android.Manifest;
import android.app.NotificationChannel;
import android.app.NotificationManager;
import android.app.PendingIntent;
import android.content.Context;
import android.content.Intent;
import android.content.SharedPreferences;
import android.content.pm.PackageManager;
import android.os.Build;

import androidx.core.app.NotificationCompat;
import androidx.core.app.NotificationManagerCompat;
import androidx.core.content.ContextCompat;
import androidx.work.ExistingPeriodicWorkPolicy;
import androidx.work.PeriodicWorkRequest;
import androidx.work.WorkManager;

import org.json.JSONArray;
import org.json.JSONObject;

import java.text.SimpleDateFormat;
import java.util.Calendar;
import java.util.Date;
import java.util.Locale;
import java.util.concurrent.TimeUnit;

/** Recordatorios de Merino: como máximo una notificación al día. */
public final class Reminders {
    private static final String PREFS = "tulana", CHANNEL = "recordatorios", WORK = "tulana-recordatorio";
    private static final int NOTIF_ID = 1001;

    private static final String[][] GENERAL = {
        {"¿Cómo te fue hoy con tu dinero?", "Escanea tus tickets del día. Te tardas menos de un minuto."},
        {"Merino te extraña 🐑", "Registra tus gastos de hoy y mira cuánto te queda para el mes."},
        {"Ese antojo también cuenta", "Anota tus compras pequeñas: son las que más se escapan."},
        {"Tu cochinito te espera", "Cada peso que apartas hoy te acerca a tu meta."},
        {"Un minuto para tu dinero", "Revisa en qué se te fue el dinero esta semana."},
        {"¿Guardaste el ticket?", "Tómale foto antes de que se pierda y yo lo clasifico."},
    };

    private static final String[][] GENERAL_EN = {
        {"How did your money do today?", "Scan today's receipts. It takes less than a minute."},
        {"Merino misses you 🐑", "Log today's expenses and see how much you have left this month."},
        {"That craving counts too", "Log your small purchases: they're the ones that slip away."},
        {"Your piggy bank is waiting", "Every peso you set aside today gets you closer to your goal."},
        {"One minute for your money", "Check where your money went this week."},
        {"Did you keep the receipt?", "Snap it before it gets lost and I'll sort it."},
    };

    private Reminders() { }

    private static SharedPreferences prefs(Context c) { return c.getSharedPreferences(PREFS, Context.MODE_PRIVATE); }

    private static String day(int offset) {
        Calendar cal = Calendar.getInstance();
        cal.add(Calendar.DAY_OF_YEAR, offset);
        return new SimpleDateFormat("yyyy-MM-dd", Locale.US).format(cal.getTime());
    }

    static void markOpened(Context c) { prefs(c).edit().putString("lastOpen", day(0)).apply(); }

    static boolean allowed(Context c) {
        if (Build.VERSION.SDK_INT >= 33
                && ContextCompat.checkSelfPermission(c, Manifest.permission.POST_NOTIFICATIONS) != PackageManager.PERMISSION_GRANTED) return false;
        return NotificationManagerCompat.from(c).areNotificationsEnabled();
    }

    /** La app manda su estado (metas, racha, pagos fijos) y la hora elegida. */
    static void configure(Context c, String json, Runnable askPermission) {
        try {
            JSONObject o = new JSONObject(json);
            boolean on = o.optBoolean("on", false);
            int h = o.optInt("hour", 20), m = o.optInt("minute", 0);
            String sched = on ? (h + ":" + m) : "off";
            SharedPreferences p = prefs(c);
            p.edit().putString("state", json).apply();
            if (sched.equals(p.getString("sched", ""))) return;
            p.edit().putString("sched", sched).apply();
            WorkManager wm = WorkManager.getInstance(c);
            if (on) {
                PeriodicWorkRequest req = new PeriodicWorkRequest.Builder(ReminderWorker.class, 24, TimeUnit.HOURS)
                        .setInitialDelay(delayTo(h, m), TimeUnit.MILLISECONDS)
                        .build();
                wm.enqueueUniquePeriodicWork(WORK, ExistingPeriodicWorkPolicy.CANCEL_AND_REENQUEUE, req);
                if (askPermission != null) askPermission.run();
            } else {
                wm.cancelUniqueWork(WORK);
            }
        } catch (Exception ignored) { }
    }

    private static long delayTo(int h, int m) {
        Calendar now = Calendar.getInstance(), next = Calendar.getInstance();
        next.set(Calendar.HOUR_OF_DAY, h);
        next.set(Calendar.MINUTE, m);
        next.set(Calendar.SECOND, 0);
        if (!next.after(now)) next.add(Calendar.DAY_OF_YEAR, 1);
        return next.getTimeInMillis() - now.getTimeInMillis();
    }

    /** Lo ejecuta WorkManager una vez al día a la hora elegida. */
    static void runDaily(Context c) {
        checkUpdate(c);
        SharedPreferences p = prefs(c);
        String today = day(0);
        if (today.equals(p.getString("lastNotified", ""))) return;
        JSONObject s;
        try { s = new JSONObject(p.getString("state", "{}")); } catch (Exception e) { return; }
        if (!s.optBoolean("on", false)) return;

        boolean en = "en".equals(s.optString("lang", "es"));
        String[] msg = paymentMessage(s, en);
        if (msg == null) msg = weeklyMessage(s);
        if (msg == null) {
            // Si ya abriste la app hoy, Merino no molesta
            if (today.equals(p.getString("lastOpen", ""))) return;
            int streak = s.optInt("streak", 0);
            if (streak > 0 && day(-1).equals(s.optString("streakLast", ""))) {
                msg = en ? new String[]{"Your " + streak + "-day streak is at risk 🔥", "Log an expense today to keep it."}
                        : new String[]{"Tu racha de " + streak + (streak == 1 ? " día" : " días") + " está en riesgo 🔥",
                        "Registra un gasto hoy para no perderla."};
            } else {
                String[][] g = en ? GENERAL_EN : GENERAL;
                msg = g[Calendar.getInstance().get(Calendar.DAY_OF_YEAR) % g.length];
            }
        }
        if (show(c, msg[0], msg[1])) p.edit().putString("lastNotified", today).apply();
    }

    /** Los domingos, Merino manda el resumen de la semana (la app lo calcula y se lo pasa al teléfono). */
    private static String[] weeklyMessage(JSONObject s) {
        Calendar cal = Calendar.getInstance();
        if (cal.get(Calendar.DAY_OF_WEEK) != Calendar.SUNDAY) return null;
        JSONObject w = s.optJSONObject("weekly");
        if (w == null) return null;
        cal.add(Calendar.DAY_OF_YEAR, -6);   // el lunes de esta semana
        String monday = new SimpleDateFormat("yyyy-MM-dd", Locale.US).format(cal.getTime());
        if (!monday.equals(w.optString("wk", ""))) return null;
        String title = w.optString("title", ""), text = w.optString("text", "");
        return title.isEmpty() || text.isEmpty() ? null : new String[]{title, text};
    }

    private static String[] paymentMessage(JSONObject s, boolean en) {
        JSONArray fx = s.optJSONArray("fixed");
        if (fx == null) return null;
        Calendar cal = Calendar.getInstance();
        int dom = cal.get(Calendar.DAY_OF_MONTH), last = cal.getActualMaximum(Calendar.DAY_OF_MONTH);
        String month = new SimpleDateFormat("yyyy-MM", Locale.US).format(new Date());
        for (int i = 0; i < fx.length(); i++) {
            JSONObject f = fx.optJSONObject(i);
            if (f == null) continue;
            int due = Math.min(f.optInt("dueDay", 0), last);
            if (due <= 0) continue;
            boolean paidThisMonth = month.equals(f.optString("paid", ""));
            String name = f.optString("name", "tu pago"), amt = f.optString("amt", "");
            if (due == dom && !paidThisMonth)
                return en ? new String[]{name + " is due today", "It's " + amt + ". When you pay it, mark it in TuLana."}
                        : new String[]{"Hoy vence " + name, "Son " + amt + ". Cuando lo pagues, márcalo en TuLana."};
            boolean tomorrow = due == dom + 1 || (dom == last && f.optInt("dueDay", 0) == 1);
            if (tomorrow && (!paidThisMonth || dom == last))
                return en ? new String[]{name + " is due tomorrow", "It's " + amt + ". I've already set it aside from what you can spend."}
                        : new String[]{"Mañana vence " + name, "Son " + amt + ". Ya lo tengo apartado de lo que puedes gastar."};
        }
        return null;
    }

    private static final String UPDATE_URL = "https://nazarethmonjarazramirez-prog.github.io/TuLana/version.json";
    private static final String APK_URL = "https://github.com/nazarethmonjarazramirez-prog/TuLana/releases/latest/download/TuLana.apk";

    /** Una vez al día revisa si hay versión nueva y avisa una sola vez por versión. */
    static void checkUpdate(Context c) {
        java.net.HttpURLConnection con = null;
        try {
            con = (java.net.HttpURLConnection) new java.net.URL(UPDATE_URL + "?t=" + System.currentTimeMillis()).openConnection();
            con.setConnectTimeout(8000); con.setReadTimeout(8000); con.setUseCaches(false);
            if (con.getResponseCode() != 200) return;
            StringBuilder sb = new StringBuilder();
            try (java.io.BufferedReader r = new java.io.BufferedReader(new java.io.InputStreamReader(con.getInputStream(), "UTF-8"))) {
                String line; while ((line = r.readLine()) != null) sb.append(line);
            }
            JSONObject v = new JSONObject(sb.toString());
            int code = v.optInt("code", 0);
            int mine = (int) androidx.core.content.pm.PackageInfoCompat.getLongVersionCode(c.getPackageManager().getPackageInfo(c.getPackageName(), 0));
            SharedPreferences p = prefs(c);
            if (code <= mine || code == p.getInt("updNotified", 0)) return;
            boolean en = false;
            try { en = "en".equals(new JSONObject(p.getString("state", "{}")).optString("lang", "es")); } catch (Exception ignored) { }
            String ver = v.optString("version", "");
            String title = en ? "New TuLana version " + ver + " 🐑" : "Nueva versión de TuLana " + ver + " 🐑";
            String text = en ? "Tap to update. It installs on top and you keep your data." : "Toca para actualizar. Se instala encima y no pierdes tus datos.";
            if (showLink(c, title, text, APK_URL)) p.edit().putInt("updNotified", code).apply();
        } catch (Exception ignored) {
        } finally { if (con != null) con.disconnect(); }
    }

    private static boolean showLink(Context c, String title, String text, String url) {
        if (!allowed(c)) return false;
        ensureChannel(c);
        Intent open = new Intent(Intent.ACTION_VIEW, android.net.Uri.parse(url)).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK);
        PendingIntent pi = PendingIntent.getActivity(c, 2, open, PendingIntent.FLAG_IMMUTABLE | PendingIntent.FLAG_UPDATE_CURRENT);
        NotificationCompat.Builder b = new NotificationCompat.Builder(c, CHANNEL)
                .setSmallIcon(R.drawable.ic_stat_merino).setColor(0xFFC4531E)
                .setContentTitle(title).setContentText(text)
                .setStyle(new NotificationCompat.BigTextStyle().bigText(text))
                .setAutoCancel(true).setContentIntent(pi);
        try { NotificationManagerCompat.from(c).notify(1002, b.build()); return true; } catch (SecurityException e) { return false; }
    }

    private static void ensureChannel(Context c) {
        NotificationManager nm = c.getSystemService(NotificationManager.class);
        if (nm != null && nm.getNotificationChannel(CHANNEL) == null) {
            NotificationChannel ch = new NotificationChannel(CHANNEL, "Recordatorios de Merino", NotificationManager.IMPORTANCE_DEFAULT);
            ch.setDescription("Avisos para registrar tus gastos, pagos por vencer y tu racha");
            nm.createNotificationChannel(ch);
        }
    }

    static boolean show(Context c, String title, String text) {
        if (!allowed(c)) return false;
        NotificationManager nm = c.getSystemService(NotificationManager.class);
        if (nm != null && nm.getNotificationChannel(CHANNEL) == null) {
            NotificationChannel ch = new NotificationChannel(CHANNEL, "Recordatorios de Merino", NotificationManager.IMPORTANCE_DEFAULT);
            ch.setDescription("Avisos para registrar tus gastos, pagos por vencer y tu racha");
            nm.createNotificationChannel(ch);
        }
        Intent open = new Intent(c, MainActivity.class).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK | Intent.FLAG_ACTIVITY_CLEAR_TOP);
        PendingIntent pi = PendingIntent.getActivity(c, 0, open, PendingIntent.FLAG_IMMUTABLE | PendingIntent.FLAG_UPDATE_CURRENT);
        NotificationCompat.Builder b = new NotificationCompat.Builder(c, CHANNEL)
                .setSmallIcon(R.drawable.ic_stat_merino)
                .setColor(0xFFC4531E)
                .setContentTitle(title)
                .setContentText(text)
                .setStyle(new NotificationCompat.BigTextStyle().bigText(text))
                .setAutoCancel(true)
                .setContentIntent(pi);
        try {
            NotificationManagerCompat.from(c).notify(NOTIF_ID, b.build());
            return true;
        } catch (SecurityException e) {
            return false;
        }
    }
}
