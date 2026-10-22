package app.amphone.client;

import android.app.Notification;
import android.app.NotificationChannel;
import android.app.NotificationManager;
import android.app.PendingIntent;
import android.content.Context;
import android.content.Intent;
import android.media.AudioAttributes;
import android.media.RingtoneManager;
import android.os.VibrationEffect;
import android.os.Vibrator;

/** Известия: съобщения, обаждания и постоянното известие на фоновата връзка. */
public final class Notifier {

    public static final String CH_MSG = "messages";
    public static final String CH_CALL = "calls";
    public static final String CH_SERVICE = "service";

    public static final int ID_SERVICE = 1;
    public static final int ID_CALL = 2;

    private static final long[] RING = {0, 800, 500, 800, 500, 800, 500, 800};

    /** true while MainActivity is on screen; then the page itself shows everything. */
    public static volatile boolean foreground = false;

    private static int nextId = 100;

    private Notifier() {
    }

    public static void createChannels(Context c) {
        NotificationManager nm = c.getSystemService(NotificationManager.class);
        if (nm == null) return;

        NotificationChannel msg = new NotificationChannel(CH_MSG, c.getString(R.string.ch_messages), NotificationManager.IMPORTANCE_HIGH);
        msg.setDescription(c.getString(R.string.ch_messages_desc));
        msg.enableVibration(true);
        nm.createNotificationChannel(msg);

        NotificationChannel call = new NotificationChannel(CH_CALL, c.getString(R.string.ch_calls), NotificationManager.IMPORTANCE_HIGH);
        call.setDescription(c.getString(R.string.ch_calls_desc));
        call.enableVibration(true);
        call.setVibrationPattern(RING);
        call.setSound(RingtoneManager.getDefaultUri(RingtoneManager.TYPE_RINGTONE),
            new AudioAttributes.Builder().setUsage(AudioAttributes.USAGE_NOTIFICATION_RINGTONE).build());
        nm.createNotificationChannel(call);

        NotificationChannel svc = new NotificationChannel(CH_SERVICE, c.getString(R.string.ch_service), NotificationManager.IMPORTANCE_MIN);
        svc.setDescription(c.getString(R.string.ch_service_desc));
        svc.setShowBadge(false);
        nm.createNotificationChannel(svc);
    }

    static PendingIntent openApp(Context c, String base) {
        Intent i = new Intent(c, MainActivity.class);
        i.setFlags(Intent.FLAG_ACTIVITY_SINGLE_TOP | Intent.FLAG_ACTIVITY_CLEAR_TOP | Intent.FLAG_ACTIVITY_NEW_TASK);
        if (base != null) i.putExtra(MainActivity.EXTRA_SERVER, base);
        int code = base == null ? 0 : base.hashCode();
        return PendingIntent.getActivity(c, code, i, PendingIntent.FLAG_IMMUTABLE | PendingIntent.FLAG_UPDATE_CURRENT);
    }

    private static void post(Context c, int id, Notification n) {
        NotificationManager nm = c.getSystemService(NotificationManager.class);
        if (nm == null) return;
        try {
            nm.notify(id, n);
        } catch (SecurityException ignored) {
            // Няма разрешение за известия.
        }
    }

    public static void message(Context c, String base, String server, String title, String body) {
        Notification.Builder b = new Notification.Builder(c, CH_MSG)
            .setSmallIcon(R.drawable.ic_stat)
            .setContentTitle(title)
            .setContentText(body)
            .setStyle(new Notification.BigTextStyle().bigText(body))
            .setAutoCancel(true)
            .setCategory(Notification.CATEGORY_MESSAGE)
            .setContentIntent(openApp(c, base));
        if (server != null && !server.isEmpty()) b.setSubText(server);
        post(c, nextId++, b.build());
    }

    public static void call(Context c, String base, String server, String name) {
        Notification.Builder b = new Notification.Builder(c, CH_CALL)
            .setSmallIcon(R.drawable.ic_stat)
            .setContentTitle(c.getString(R.string.incoming_call))
            .setContentText(name)
            .setAutoCancel(true)
            .setOngoing(true)
            .setCategory(Notification.CATEGORY_CALL)
            .setTimeoutAfter(45_000)
            .setContentIntent(openApp(c, base));
        if (server != null && !server.isEmpty()) b.setSubText(server);
        Notification n = b.build();
        n.flags |= Notification.FLAG_INSISTENT;
        post(c, ID_CALL, n);
    }

    public static void cancelCall(Context c) {
        NotificationManager nm = c.getSystemService(NotificationManager.class);
        if (nm != null) nm.cancel(ID_CALL);
        stopVibrate(c);
    }

    public static Notification service(Context c, int linked) {
        return new Notification.Builder(c, CH_SERVICE)
            .setSmallIcon(R.drawable.ic_stat)
            .setContentTitle(c.getString(R.string.service_title))
            .setContentText(c.getResources().getQuantityString(R.plurals.service_text, linked, linked))
            .setOngoing(true)
            .setShowWhen(false)
            .setContentIntent(openApp(c, null))
            .build();
    }

    public static void vibrateRing(Context c) {
        Vibrator v = c.getSystemService(Vibrator.class);
        if (v == null || !v.hasVibrator()) return;
        v.vibrate(VibrationEffect.createWaveform(RING, -1));
    }

    public static void stopVibrate(Context c) {
        Vibrator v = c.getSystemService(Vibrator.class);
        if (v != null) v.cancel();
    }
}
