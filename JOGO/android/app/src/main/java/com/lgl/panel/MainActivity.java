package com.lgl.panel;

import android.app.Activity;
import android.content.BroadcastReceiver;
import android.content.Context;
import android.content.Intent;
import android.content.IntentFilter;
import android.net.Uri;
import android.os.Build;
import android.os.Bundle;
import android.provider.Settings;
import android.webkit.JavascriptInterface;
import android.webkit.WebResourceRequest;
import android.webkit.WebView;
import android.webkit.WebViewClient;

import androidx.core.content.ContextCompat;
import androidx.webkit.WebViewAssetLoader;

public final class MainActivity extends Activity {
    static final String ACTION_TOGGLE_LAG = "com.lgl.panel.TOGGLE_LAG";
    private static final int OVERLAY_PERMISSION_REQUEST = 301;
    private static final String APP_URL = "https://appassets.androidplatform.net/assets/www/index.html";
    private static final String PREFS = "lgl_panel";

    private WebView webView;
    private boolean waitingForOverlayPermission;
    private boolean receiverRegistered;

    private final BroadcastReceiver overlayReceiver = new BroadcastReceiver() {
        @Override
        public void onReceive(Context context, Intent intent) {
            if (ACTION_TOGGLE_LAG.equals(intent.getAction())) {
                dispatch("lgl-toggle-lag", null);
            } else if (LagOverlayService.ACTION_OVERLAY_CHANGED.equals(intent.getAction())) {
                dispatch("lgl-overlay-state", "false");
            }
        }
    };

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);

        webView = new WebView(this);
        webView.setBackgroundColor(0xFF090B12);
        webView.getSettings().setJavaScriptEnabled(true);
        webView.getSettings().setDomStorageEnabled(true);
        webView.getSettings().setAllowFileAccess(false);
        webView.getSettings().setAllowContentAccess(false);

        WebViewAssetLoader assetLoader = new WebViewAssetLoader.Builder()
                .addPathHandler("/assets/", new WebViewAssetLoader.AssetsPathHandler(this))
                .build();
        webView.setWebViewClient(new WebViewClient() {
            @Override
            public android.webkit.WebResourceResponse shouldInterceptRequest(
                    WebView view, WebResourceRequest request) {
                return assetLoader.shouldInterceptRequest(request.getUrl());
            }

            @Override
            public boolean shouldOverrideUrlLoading(WebView view, WebResourceRequest request) {
                String destination = request.getUrl().toString();
                return !destination.equals(APP_URL) && !destination.startsWith(APP_URL + "#");
            }
        });
        webView.addJavascriptInterface(new MobileBridge(), "lagLabMobile");
        setContentView(webView);
        webView.loadUrl(APP_URL);

        IntentFilter filter = new IntentFilter();
        filter.addAction(ACTION_TOGGLE_LAG);
        filter.addAction(LagOverlayService.ACTION_OVERLAY_CHANGED);
        if (Build.VERSION.SDK_INT >= 33) {
            registerReceiver(overlayReceiver, filter, Context.RECEIVER_NOT_EXPORTED);
        } else {
            registerReceiver(overlayReceiver, filter);
        }
        receiverRegistered = true;
    }

    @Override
    protected void onResume() {
        super.onResume();
        if (waitingForOverlayPermission) {
            waitingForOverlayPermission = false;
            if (Settings.canDrawOverlays(this)) {
                startOverlayService();
                dispatch("lgl-overlay-state", "true");
            } else {
                dispatch("lgl-overlay-state", "false");
            }
        }
        if (webView != null) {
            boolean enabled = getSharedPreferences(PREFS, MODE_PRIVATE).getBoolean("overlay_enabled", false)
                    && Settings.canDrawOverlays(this);
            boolean active = getSharedPreferences(PREFS, MODE_PRIVATE).getBoolean("lag_active", false);
            if (enabled) startOverlayService();
            webView.post(() -> {
                dispatch("lgl-overlay-state", Boolean.toString(enabled && Settings.canDrawOverlays(this)));
                dispatch("lgl-lag-state", Boolean.toString(active));
            });
        }
    }

    @Override
    protected void onActivityResult(int requestCode, int resultCode, Intent data) {
        super.onActivityResult(requestCode, resultCode, data);
        if (requestCode == OVERLAY_PERMISSION_REQUEST && !Settings.canDrawOverlays(this)) {
            waitingForOverlayPermission = false;
            getSharedPreferences(PREFS, MODE_PRIVATE).edit().putBoolean("overlay_enabled", false).apply();
            dispatch("lgl-overlay-state", "false");
        }
    }

    @Override
    protected void onDestroy() {
        if (receiverRegistered) {
            unregisterReceiver(overlayReceiver);
            receiverRegistered = false;
        }
        if (webView != null) {
            webView.removeJavascriptInterface("lagLabMobile");
            webView.destroy();
            webView = null;
        }
        super.onDestroy();
    }

    private void requestOverlay(boolean enabled) {
        if (!enabled) {
            getSharedPreferences(PREFS, MODE_PRIVATE).edit().putBoolean("overlay_enabled", false).apply();
            stopService(new Intent(this, LagOverlayService.class));
            dispatch("lgl-overlay-state", "false");
            return;
        }

        if (!Settings.canDrawOverlays(this)) {
            waitingForOverlayPermission = true;
            Intent permissionIntent = new Intent(
                    Settings.ACTION_MANAGE_OVERLAY_PERMISSION,
                    Uri.parse("package:" + getPackageName()));
            startActivityForResult(permissionIntent, OVERLAY_PERMISSION_REQUEST);
            return;
        }

        startOverlayService();
        dispatch("lgl-overlay-state", "true");
    }

    private void startOverlayService() {
        getSharedPreferences(PREFS, MODE_PRIVATE).edit().putBoolean("overlay_enabled", true).apply();
        Intent serviceIntent = new Intent(this, LagOverlayService.class)
                .setAction(LagOverlayService.ACTION_START);
        ContextCompat.startForegroundService(this, serviceIntent);
    }

    private void dispatch(String eventName, String booleanValue) {
        if (webView == null) return;
        String detail = booleanValue == null ? "" : ",{detail:" + booleanValue + "}";
        webView.evaluateJavascript(
                "window.dispatchEvent(new CustomEvent('" + eventName + "'" + detail + "))",
                null);
    }

    public final class MobileBridge {
        @JavascriptInterface
        public void setOverlayEnabled(boolean enabled) {
            runOnUiThread(() -> requestOverlay(enabled));
        }

        @JavascriptInterface
        public void setLagState(boolean active) {
            getSharedPreferences(PREFS, MODE_PRIVATE).edit().putBoolean("lag_active", active).apply();
            boolean overlayEnabled = getSharedPreferences(PREFS, MODE_PRIVATE)
                    .getBoolean("overlay_enabled", false);
            if (!overlayEnabled || !Settings.canDrawOverlays(MainActivity.this)) return;
            Intent update = new Intent(MainActivity.this, LagOverlayService.class)
                    .setAction(LagOverlayService.ACTION_SET_ACTIVE)
                    .putExtra(LagOverlayService.EXTRA_ACTIVE, active);
            startService(update);
        }

        @JavascriptInterface
        public void requestCurrentState() {
            runOnUiThread(() -> {
                boolean enabled = getSharedPreferences(PREFS, MODE_PRIVATE).getBoolean("overlay_enabled", false)
                        && Settings.canDrawOverlays(MainActivity.this);
                boolean active = getSharedPreferences(PREFS, MODE_PRIVATE).getBoolean("lag_active", false);
                dispatch("lgl-overlay-state", Boolean.toString(enabled));
                dispatch("lgl-lag-state", Boolean.toString(active));
            });
        }
    }
}
