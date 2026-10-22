package app.amphone.client;

import android.Manifest;
import android.annotation.SuppressLint;
import android.app.Activity;
import android.content.Intent;
import android.content.pm.PackageManager;
import android.graphics.Color;
import android.net.Uri;
import android.os.Build;
import android.os.Bundle;
import android.os.Handler;
import android.os.Looper;
import android.provider.Settings;
import android.view.View;
import android.view.Window;
import android.view.WindowInsets;
import android.view.WindowInsetsController;
import android.view.WindowManager;
import android.webkit.JavascriptInterface;
import android.webkit.WebChromeClient;
import android.webkit.WebResourceError;
import android.webkit.WebResourceRequest;
import android.webkit.WebSettings;
import android.webkit.WebView;
import android.webkit.WebViewClient;

import org.json.JSONArray;
import org.json.JSONObject;

import java.util.List;

/**
 * AM Phone - телефонът от FiveM сървъра на истинския телефон.
 *
 * Приложението зарежда интерфейса от сървъра (http://<сървър>:30120/am-phone/ui/) в цял екран
 * и добавя това, което браузърът няма: известия при затворено приложение, вибрация при обаждане,
 * няколко запазени сървъра.
 */
public class MainActivity extends Activity {

    public static final String EXTRA_SERVER = "server";
    private static final String SETUP = "file:///android_asset/setup.html";

    private WebView web;
    private final Handler main = new Handler(Looper.getMainLooper());
    /** Base address of the server page that is loaded now ("" on the setup page). */
    private volatile String loadedBase = "";

    @SuppressLint("SetJavaScriptEnabled")
    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        requestWindowFeature(Window.FEATURE_NO_TITLE);
        getWindow().setStatusBarColor(Color.BLACK);
        getWindow().setNavigationBarColor(Color.BLACK);

        Notifier.createChannels(this);
        askNotificationPermission();

        web = new WebView(this);
        web.setBackgroundColor(Color.BLACK);
        WebSettings s = web.getSettings();
        s.setJavaScriptEnabled(true);
        s.setDomStorageEnabled(true);
        s.setMediaPlaybackRequiresUserGesture(false);
        s.setAllowFileAccess(true);
        s.setLoadWithOverviewMode(true);
        s.setUseWideViewPort(true);
        s.setSupportZoom(false);
        s.setBuiltInZoomControls(false);
        s.setCacheMode(WebSettings.LOAD_DEFAULT);
        s.setUserAgentString(s.getUserAgentString() + " AMPhone/" + BuildConfig.VERSION_NAME);

        web.addJavascriptInterface(new Bridge(), "AmPhoneNative");
        web.setWebChromeClient(new WebChromeClient());
        web.setWebViewClient(new WebViewClient() {
            @Override
            public void onPageStarted(WebView view, String url, android.graphics.Bitmap favicon) {
                loadedBase = url != null && url.startsWith("http") ? Store.baseOf(url) : "";
            }

            @Override
            public boolean shouldOverrideUrlLoading(WebView view, WebResourceRequest request) {
                Uri u = request.getUrl();
                String url = u.toString();
                if (url.startsWith("file:///android_asset/")) return false;
                if (!loadedBase.isEmpty() && url.startsWith(loadedBase)) return false;
                // Anything else opens in the browser.
                try {
                    startActivity(new Intent(Intent.ACTION_VIEW, u));
                } catch (Exception ignored) {
                    // No browser.
                }
                return true;
            }

            @Override
            public void onReceivedError(WebView view, WebResourceRequest request, WebResourceError error) {
                if (request.isForMainFrame()) {
                    showSetup(getString(R.string.server_no_answer, String.valueOf(error.getDescription())));
                }
            }
        });

        setContentView(web);
        hideSystemBars();
        openFromIntent(getIntent());
        PollService.sync(this);
    }

    @Override
    protected void onNewIntent(Intent intent) {
        super.onNewIntent(intent);
        setIntent(intent);
        String base = intent.getStringExtra(EXTRA_SERVER);
        if (base != null && !base.equals(loadedBase)) openServer(base);
    }

    private void openFromIntent(Intent intent) {
        String base = intent != null ? intent.getStringExtra(EXTRA_SERVER) : null;
        if (base == null || Store.find(this, base) == null) base = Store.current(this);
        if (base == null || base.isEmpty() || Store.find(this, base) == null) {
            List<Store.Server> list = Store.servers(this);
            base = list.size() == 1 ? list.get(0).base : null;
        }
        if (base == null) showSetup(null);
        else openServer(base);
    }

    private void openServer(String base) {
        Store.add(this, base);
        Store.setCurrent(this, base);
        loadedBase = base;
        web.loadUrl(base + "/ui/");
    }

    private void showSetup(String error) {
        loadedBase = "";
        String url = SETUP + "?v=" + BuildConfig.VERSION_NAME;
        if (error != null) url += "&error=" + Uri.encode(error);
        web.loadUrl(url);
    }

    private void hideSystemBars() {
        if (Build.VERSION.SDK_INT >= 30) {
            getWindow().setDecorFitsSystemWindows(false);
            WindowInsetsController c = getWindow().getInsetsController();
            if (c != null) {
                c.hide(WindowInsets.Type.statusBars() | WindowInsets.Type.navigationBars());
                c.setSystemBarsBehavior(WindowInsetsController.BEHAVIOR_SHOW_TRANSIENT_BARS_BY_SWIPE);
            }
        } else {
            getWindow().getDecorView().setSystemUiVisibility(
                View.SYSTEM_UI_FLAG_IMMERSIVE_STICKY | View.SYSTEM_UI_FLAG_FULLSCREEN
                    | View.SYSTEM_UI_FLAG_HIDE_NAVIGATION | View.SYSTEM_UI_FLAG_LAYOUT_STABLE
                    | View.SYSTEM_UI_FLAG_LAYOUT_HIDE_NAVIGATION | View.SYSTEM_UI_FLAG_LAYOUT_FULLSCREEN);
        }
    }

    @Override
    public void onWindowFocusChanged(boolean hasFocus) {
        super.onWindowFocusChanged(hasFocus);
        if (hasFocus) hideSystemBars();
    }

    @Override
    protected void onResume() {
        super.onResume();
        Notifier.foreground = true;
        Notifier.cancelCall(this);
    }

    @Override
    protected void onPause() {
        super.onPause();
        Notifier.foreground = false;
    }

    @SuppressWarnings("deprecation")
    @Override
    public void onBackPressed() {
        // The page handles "back" itself (closes an app / goes one screen back).
        web.evaluateJavascript("(window.amphoneBack && window.amphoneBack()) ? 1 : 0", (r) -> {
            if (!"1".equals(r)) moveTaskToBack(true);
        });
    }

    @Override
    protected void onDestroy() {
        Notifier.foreground = false;
        if (web != null) web.destroy();
        super.onDestroy();
    }

    private void askNotificationPermission() {
        if (Build.VERSION.SDK_INT >= 33
            && checkSelfPermission(Manifest.permission.POST_NOTIFICATIONS) != PackageManager.PERMISSION_GRANTED) {
            requestPermissions(new String[]{Manifest.permission.POST_NOTIFICATIONS}, 1);
        }
    }

    private boolean onSetup() {
        return loadedBase.isEmpty();
    }

    // ------------------------------------------------------------------ JS bridge

    private class Bridge {
        // ---- used by the phone page (from the server)

        @JavascriptInterface
        public void notify(String title, String body) {
            // With the background connection on, PollService shows notifications.
            if (Store.background(MainActivity.this)) return;
            final String base = loadedBase;
            main.post(() -> {
                if (!Notifier.foreground) Notifier.message(MainActivity.this, base, null, title, body);
            });
        }

        @JavascriptInterface
        public void ring(String name) {
            final String base = loadedBase;
            main.post(() -> {
                if (Notifier.foreground) Notifier.vibrateRing(MainActivity.this);
                else if (!Store.background(MainActivity.this)) Notifier.call(MainActivity.this, base, null, name);
            });
        }

        @JavascriptInterface
        public void stopRing() {
            main.post(() -> Notifier.cancelCall(MainActivity.this));
        }

        @JavascriptInterface
        public String deviceName() {
            return Build.MANUFACTURER + " " + Build.MODEL;
        }

        /** The page got (or lost) its key - remember it for background notifications. */
        @JavascriptInterface
        public void setToken(String token, String ignoredBase) {
            final String base = loadedBase;
            if (base.isEmpty()) return;
            Store.setToken(MainActivity.this, base, token == null ? "" : token);
            main.post(() -> PollService.sync(MainActivity.this));
        }

        @JavascriptInterface
        public void serverName(String name) {
            final String base = loadedBase;
            if (base.isEmpty() || name == null) return;
            Store.setName(MainActivity.this, base, name.length() > 40 ? name.substring(0, 40) : name);
        }

        @JavascriptInterface
        public void changeServer() {
            main.post(() -> showSetup(null));
        }

        @JavascriptInterface
        public void keepScreenOn(boolean on) {
            main.post(() -> {
                if (on) getWindow().addFlags(WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON);
                else getWindow().clearFlags(WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON);
            });
        }

        // ---- used only by the setup page (inside the app)

        @JavascriptInterface
        public String servers() {
            if (!onSetup()) return "[]";
            JSONArray arr = new JSONArray();
            for (Store.Server s : Store.servers(MainActivity.this)) {
                JSONObject o = new JSONObject();
                try {
                    o.put("base", s.base);
                    o.put("title", s.title());
                    o.put("host", s.host());
                    o.put("linked", s.token != null && !s.token.isEmpty());
                } catch (Exception ignored) {
                    continue;
                }
                arr.put(o);
            }
            return arr.toString();
        }

        @JavascriptInterface
        public String preview(String address) {
            String b = Store.normalize(address);
            return b.isEmpty() ? "" : b + "/ui/";
        }

        @JavascriptInterface
        public boolean addServer(String address) {
            if (!onSetup()) return false;
            final String base = Store.normalize(address);
            if (base.isEmpty()) return false;
            main.post(() -> MainActivity.this.openServer(base));
            return true;
        }

        @JavascriptInterface
        public void openServer(String base) {
            if (!onSetup() || Store.find(MainActivity.this, base) == null) return;
            main.post(() -> MainActivity.this.openServer(base));
        }

        @JavascriptInterface
        public void removeServer(String base) {
            if (!onSetup()) return;
            Store.remove(MainActivity.this, base);
            main.post(() -> PollService.sync(MainActivity.this));
        }

        @JavascriptInterface
        public boolean background() {
            return Store.background(MainActivity.this);
        }

        @JavascriptInterface
        public void setBackground(boolean on) {
            if (!onSetup()) return;
            Store.setBackground(MainActivity.this, on);
            main.post(() -> PollService.sync(MainActivity.this));
        }

        @JavascriptInterface
        public void batterySettings() {
            if (!onSetup()) return;
            main.post(() -> {
                try {
                    startActivity(new Intent(Settings.ACTION_IGNORE_BATTERY_OPTIMIZATION_SETTINGS));
                } catch (Exception ignored) {
                    // Not available on this phone.
                }
            });
        }

        @JavascriptInterface
        public String version() {
            return BuildConfig.VERSION_NAME;
        }
    }
}
