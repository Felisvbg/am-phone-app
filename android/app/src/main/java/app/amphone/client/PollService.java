package app.amphone.client;

import android.app.Service;
import android.content.Context;
import android.content.Intent;
import android.content.pm.ServiceInfo;
import android.os.Build;
import android.os.IBinder;

import org.json.JSONArray;
import org.json.JSONObject;

import java.io.ByteArrayOutputStream;
import java.io.InputStream;
import java.net.HttpURLConnection;
import java.net.URL;
import java.nio.charset.StandardCharsets;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

/**
 * Фонова връзка: слуша сървърите (long-poll), докато приложението е затворено,
 * и показва известия за съобщения и обаждания.
 */
public class PollService extends Service {

    private final Map<String, Worker> workers = new HashMap<>();

    public static void sync(Context c) {
        Intent i = new Intent(c, PollService.class);
        if (Store.background(c) && Store.anyLinked(c)) {
            try {
                c.startForegroundService(i);
            } catch (RuntimeException ignored) {
                // Android не позволява стартиране от фона в момента - ще опитаме при следващото отваряне.
            }
        } else {
            c.stopService(i);
        }
    }

    @Override
    public IBinder onBind(Intent intent) {
        return null;
    }

    @Override
    public void onCreate() {
        super.onCreate();
        Notifier.createChannels(this);
    }

    @Override
    public int onStartCommand(Intent intent, int flags, int startId) {
        List<Store.Server> servers = Store.servers(this);
        int linked = 0;
        for (Store.Server s : servers) if (s.token != null && !s.token.isEmpty()) linked++;

        if (Build.VERSION.SDK_INT >= 34) {
            startForeground(Notifier.ID_SERVICE, Notifier.service(this, Math.max(linked, 1)),
                ServiceInfo.FOREGROUND_SERVICE_TYPE_REMOTE_MESSAGING);
        } else {
            startForeground(Notifier.ID_SERVICE, Notifier.service(this, Math.max(linked, 1)));
        }

        if (linked == 0 || !Store.background(this)) {
            stopAll();
            stopSelf();
            return START_NOT_STICKY;
        }

        // Start workers for new servers / changed tokens, stop the rest.
        Map<String, String> wanted = new HashMap<>();
        for (Store.Server s : servers) {
            if (s.token != null && !s.token.isEmpty()) wanted.put(s.base, s.token);
        }
        for (String base : workers.keySet().toArray(new String[0])) {
            Worker w = workers.get(base);
            if (!wanted.containsKey(base) || !wanted.get(base).equals(w.token)) {
                w.stop();
                workers.remove(base);
            }
        }
        for (Map.Entry<String, String> e : wanted.entrySet()) {
            if (!workers.containsKey(e.getKey())) {
                Worker w = new Worker(e.getKey(), e.getValue());
                workers.put(e.getKey(), w);
                w.start();
            }
        }
        return START_STICKY;
    }

    private void stopAll() {
        for (Worker w : workers.values()) w.stop();
        workers.clear();
    }

    @Override
    public void onDestroy() {
        stopAll();
        super.onDestroy();
    }

    // ------------------------------------------------------------------ worker

    private final class Worker extends Thread {
        final String base;
        final String token;
        volatile boolean running = true;
        volatile HttpURLConnection conn;
        long seq = -1;

        Worker(String base, String token) {
            super("amphone-poll");
            this.base = base;
            this.token = token;
        }

        void stop() {
            running = false;
            HttpURLConnection c = conn;
            if (c != null) c.disconnect();
            interrupt();
        }

        @Override
        public void run() {
            int failures = 0;
            while (running) {
                try {
                    String url = base + "/api/poll" + (seq >= 0 ? "?since=" + seq : "");
                    HttpURLConnection c = (HttpURLConnection) new URL(url).openConnection();
                    conn = c;
                    c.setConnectTimeout(10_000);
                    c.setReadTimeout(60_000);
                    c.setUseCaches(false);
                    c.setRequestProperty("X-AM-Token", token);
                    int code = c.getResponseCode();
                    if (code == 401) {
                        // The device was unlinked in the game.
                        Store.setToken(PollService.this, base, "");
                        running = false;
                        sync(PollService.this);
                        return;
                    }
                    if (code != 200) throw new Exception("HTTP " + code);
                    JSONObject d = new JSONObject(read(c.getInputStream()));
                    c.disconnect();
                    failures = 0;
                    if (d.optBoolean("reset") || d.optBoolean("resync")) {
                        seq = d.optLong("seq", 0);
                        continue;
                    }
                    seq = d.optLong("seq", seq);
                    JSONArray evs = d.optJSONArray("events");
                    if (evs != null) {
                        for (int i = 0; i < evs.length(); i++) handle(evs.optJSONObject(i));
                    }
                } catch (Exception e) {
                    if (!running) return;
                    failures++;
                    try {
                        Thread.sleep(Math.min(60_000, 3_000L * failures));
                    } catch (InterruptedException ie) {
                        return;
                    }
                }
            }
        }

        private void handle(JSONObject ev) {
            if (ev == null) return;
            String name = ev.optString("event");
            JSONObject data = ev.optJSONObject("data");
            if (data == null) data = new JSONObject();
            Context ctx = PollService.this;
            Store.Server s = Store.find(ctx, base);
            String server = s != null && Store.servers(ctx).size() > 1 ? s.title() : null;

            switch (name) {
                case "notify":
                    if (!Notifier.foreground) {
                        Notifier.message(ctx, base, server, data.optString("title", "AM Phone"), data.optString("body", ""));
                    }
                    break;
                case "call:incoming":
                    if (!Notifier.foreground) {
                        String who = data.optString("name", "");
                        if (who.isEmpty()) who = data.optString("display", "");
                        Notifier.call(ctx, base, server, who);
                    }
                    break;
                case "call:active":
                case "call:ended":
                    Notifier.cancelCall(ctx);
                    break;
                default:
                    break;
            }
        }
    }

    private static String read(InputStream in) throws java.io.IOException {
        ByteArrayOutputStream out = new ByteArrayOutputStream();
        byte[] buf = new byte[4096];
        int n;
        while ((n = in.read(buf)) > 0) out.write(buf, 0, n);
        in.close();
        return out.toString(StandardCharsets.UTF_8.name());
    }
}
