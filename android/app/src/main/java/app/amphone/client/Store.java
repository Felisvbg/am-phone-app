package app.amphone.client;

import android.content.Context;
import android.content.SharedPreferences;

import org.json.JSONArray;
import org.json.JSONException;
import org.json.JSONObject;

import java.util.ArrayList;
import java.util.List;

/**
 * Запазени сървъри и ключове за връзка.
 *
 * Всеки сървър е записан с базов адрес, например "http://1.2.3.4:30120/am-phone".
 * Интерфейсът се зарежда от base + "/ui/", а заявките отиват към base + "/api/...".
 */
public final class Store {

    private static final String PREFS = "amphone";
    private static final String KEY_SERVERS = "servers";
    private static final String KEY_CURRENT = "current";
    private static final String KEY_BACKGROUND = "background";

    public static final class Server {
        public final String base;
        public String name;
        public String token;

        Server(String base, String name, String token) {
            this.base = base;
            this.name = name;
            this.token = token;
        }

        public String uiUrl() {
            return base + "/ui/";
        }

        public String host() {
            return base.replaceFirst("^https?://", "").replaceFirst("/am-phone$", "");
        }

        public String title() {
            return name != null && !name.isEmpty() ? name : host();
        }
    }

    private Store() {
    }

    private static SharedPreferences prefs(Context c) {
        return c.getApplicationContext().getSharedPreferences(PREFS, Context.MODE_PRIVATE);
    }

    /** "1.2.3.4:30120" / "http://x/am-phone/ui/" -> "http://1.2.3.4:30120/am-phone" */
    public static String normalize(String input) {
        String a = input == null ? "" : input.trim();
        if (a.isEmpty()) return "";
        if (!a.startsWith("http://") && !a.startsWith("https://")) a = "http://" + a;
        int idx = a.indexOf("/am-phone");
        if (idx > 0) a = a.substring(0, idx);
        while (a.endsWith("/")) a = a.substring(0, a.length() - 1);
        String hostPart = a.replaceFirst("^https?://", "");
        if (hostPart.isEmpty() || hostPart.contains(" ")) return "";
        if (a.startsWith("http://") && !hostPart.contains(":") && !hostPart.contains("/")) a = a + ":30120";
        return a + "/am-phone";
    }

    /** Base address from any URL inside it ("http://x:30120/am-phone/ui/#/..." -> "http://x:30120/am-phone"). */
    public static String baseOf(String url) {
        if (url == null) return "";
        int idx = url.indexOf("/am-phone");
        if (idx < 0) return "";
        return url.substring(0, idx) + "/am-phone";
    }

    public static synchronized List<Server> servers(Context c) {
        List<Server> out = new ArrayList<>();
        try {
            JSONArray arr = new JSONArray(prefs(c).getString(KEY_SERVERS, "[]"));
            for (int i = 0; i < arr.length(); i++) {
                JSONObject o = arr.getJSONObject(i);
                out.add(new Server(o.getString("base"), o.optString("name", ""), o.optString("token", "")));
            }
        } catch (JSONException ignored) {
            // Повредени данни - започваме начисто.
        }
        return out;
    }

    private static void save(Context c, List<Server> list) {
        JSONArray arr = new JSONArray();
        for (Server s : list) {
            JSONObject o = new JSONObject();
            try {
                o.put("base", s.base);
                o.put("name", s.name == null ? "" : s.name);
                o.put("token", s.token == null ? "" : s.token);
            } catch (JSONException ignored) {
                continue;
            }
            arr.put(o);
        }
        prefs(c).edit().putString(KEY_SERVERS, arr.toString()).apply();
    }

    public static synchronized Server find(Context c, String base) {
        for (Server s : servers(c)) if (s.base.equals(base)) return s;
        return null;
    }

    /** Adds (or keeps) a server and returns it. */
    public static synchronized Server add(Context c, String base) {
        List<Server> list = servers(c);
        for (Server s : list) if (s.base.equals(base)) return s;
        Server s = new Server(base, "", "");
        list.add(s);
        save(c, list);
        return s;
    }

    public static synchronized void remove(Context c, String base) {
        List<Server> list = servers(c);
        for (int i = list.size() - 1; i >= 0; i--) if (list.get(i).base.equals(base)) list.remove(i);
        save(c, list);
        if (base.equals(current(c))) setCurrent(c, "");
    }

    public static synchronized void setToken(Context c, String base, String token) {
        List<Server> list = servers(c);
        boolean found = false;
        for (Server s : list) {
            if (s.base.equals(base)) {
                s.token = token;
                found = true;
            }
        }
        if (!found && token != null && !token.isEmpty()) list.add(new Server(base, "", token));
        save(c, list);
    }

    public static synchronized void setName(Context c, String base, String name) {
        List<Server> list = servers(c);
        for (Server s : list) if (s.base.equals(base)) s.name = name;
        save(c, list);
    }

    public static synchronized boolean anyLinked(Context c) {
        for (Server s : servers(c)) if (s.token != null && !s.token.isEmpty()) return true;
        return false;
    }

    public static String current(Context c) {
        return prefs(c).getString(KEY_CURRENT, "");
    }

    public static void setCurrent(Context c, String base) {
        prefs(c).edit().putString(KEY_CURRENT, base).apply();
    }

    /** Notifications while the app is closed (background service). */
    public static boolean background(Context c) {
        return prefs(c).getBoolean(KEY_BACKGROUND, true);
    }

    public static void setBackground(Context c, boolean on) {
        prefs(c).edit().putBoolean(KEY_BACKGROUND, on).apply();
    }
}
