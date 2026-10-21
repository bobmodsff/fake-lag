package com.lgl.panel;

import android.app.Notification;
import android.app.NotificationChannel;
import android.app.NotificationManager;
import android.app.PendingIntent;
import android.app.Service;
import android.content.Context;
import android.content.Intent;
import android.graphics.Color;
import android.graphics.PixelFormat;
import android.graphics.Typeface;
import android.graphics.drawable.GradientDrawable;
import android.os.Build;
import android.os.IBinder;
import android.provider.Settings;
import android.view.Gravity;
import android.view.MotionEvent;
import android.view.View;
import android.view.WindowManager;
import android.widget.LinearLayout;
import android.widget.TextView;

import androidx.core.app.NotificationCompat;

public final class LagOverlayService extends Service {
    static final String ACTION_START = "com.lgl.panel.overlay.START";
    static final String ACTION_SET_ACTIVE = "com.lgl.panel.overlay.SET_ACTIVE";
    static final String ACTION_OVERLAY_CHANGED = "com.lgl.panel.overlay.OVERLAY_CHANGED";
    static final String EXTRA_ACTIVE = "active";
    private static final String CHANNEL_ID = "lgl_overlay";
    private static final int NOTIFICATION_ID = 30;
    private static final String PREFS = "lgl_panel";

    private WindowManager windowManager;
    private View overlayView;
    private TextView toggleButton;
    private boolean lagActive;

    @Override
    public void onCreate() {
        super.onCreate();
        windowManager = (WindowManager) getSystemService(WINDOW_SERVICE);
        createNotificationChannel();
        startForeground(NOTIFICATION_ID, buildNotification());
        lagActive = getSharedPreferences(PREFS, MODE_PRIVATE).getBoolean("lag_active", false);
    }

    @Override
    public int onStartCommand(Intent intent, int flags, int startId) {
        String action = intent == null ? ACTION_START : intent.getAction();
        if (ACTION_SET_ACTIVE.equals(action)) {
            setLagActive(intent.getBooleanExtra(EXTRA_ACTIVE, false), false);
            if (Settings.canDrawOverlays(this)) showOverlay();
        } else if (ACTION_START.equals(action) || action == null) {
            if (!Settings.canDrawOverlays(this)) {
                stopSelf();
                return START_NOT_STICKY;
            }
            showOverlay();
        }
        return START_STICKY;
    }

    private void showOverlay() {
        if (overlayView != null || !Settings.canDrawOverlays(this)) return;

        LinearLayout panel = new LinearLayout(this);
        panel.setGravity(Gravity.CENTER_VERTICAL);
        panel.setPadding(dp(10), dp(8), dp(10), dp(8));
        panel.setElevation(dp(8));
        panel.setBackground(background(Color.rgb(12, 15, 20), Color.rgb(198, 255, 92), 18));

        TextView brand = new TextView(this);
        brand.setText("LGL\n3.0");
        brand.setTextColor(Color.rgb(198, 255, 92));
        brand.setTextSize(10);
        brand.setTypeface(Typeface.DEFAULT, Typeface.BOLD);
        brand.setGravity(Gravity.CENTER);
        panel.addView(brand, new LinearLayout.LayoutParams(dp(38), -1));

        toggleButton = new TextView(this);
        toggleButton.setGravity(Gravity.CENTER);
        toggleButton.setTextColor(Color.rgb(17, 21, 11));
        toggleButton.setTextSize(11);
        toggleButton.setTypeface(Typeface.DEFAULT, Typeface.BOLD);
        toggleButton.setBackground(background(Color.rgb(198, 255, 92), Color.TRANSPARENT, 12));
        LinearLayout.LayoutParams buttonParams = new LinearLayout.LayoutParams(dp(108), dp(42));
        buttonParams.setMargins(dp(8), 0, 0, 0);
        panel.addView(toggleButton, buttonParams);
        toggleButton.setOnClickListener(view -> {
            setLagActive(!lagActive, true);
            sendBroadcast(new Intent(MainActivity.ACTION_TOGGLE_LAG).setPackage(getPackageName()));
        });

        TextView closeButton = new TextView(this);
        closeButton.setText("×");
        closeButton.setTextSize(19);
        closeButton.setTextColor(Color.LTGRAY);
        closeButton.setGravity(Gravity.CENTER);
        LinearLayout.LayoutParams closeParams = new LinearLayout.LayoutParams(dp(25), -1);
        panel.addView(closeButton, closeParams);
        closeButton.setOnClickListener(view -> {
            getSharedPreferences(PREFS, MODE_PRIVATE).edit()
                    .putBoolean("overlay_enabled", false).apply();
            stopSelf();
        });

        panel.setOnTouchListener(new DragListener());
        overlayView = panel;
        WindowManager.LayoutParams params = new WindowManager.LayoutParams(
                dp(205),
                dp(66),
                Build.VERSION.SDK_INT >= Build.VERSION_CODES.O
                        ? WindowManager.LayoutParams.TYPE_APPLICATION_OVERLAY
                        : WindowManager.LayoutParams.TYPE_PHONE,
                WindowManager.LayoutParams.FLAG_NOT_FOCUSABLE
                        | WindowManager.LayoutParams.FLAG_LAYOUT_IN_SCREEN,
                PixelFormat.TRANSLUCENT);
        params.gravity = Gravity.TOP | Gravity.START;
        params.x = getResources().getDisplayMetrics().widthPixels - dp(225);
        params.y = dp(180);
        windowManager.addView(overlayView, params);
        updateButton();
    }

    private void setLagActive(boolean active, boolean persist) {
        lagActive = active;
        if (persist) {
            getSharedPreferences(PREFS, MODE_PRIVATE).edit()
                    .putBoolean("lag_active", active).apply();
        }
        updateButton();
    }

    private void updateButton() {
        if (toggleButton == null) return;
        toggleButton.setText(lagActive ? "DESATIVAR" : "ATIVAR");
        toggleButton.setBackground(background(
                lagActive ? Color.rgb(255, 100, 127) : Color.rgb(198, 255, 92),
                Color.TRANSPARENT,
                12));
        toggleButton.setTextColor(lagActive ? Color.rgb(33, 9, 12) : Color.rgb(17, 21, 11));
    }

    private void createNotificationChannel() {
        if (Build.VERSION.SDK_INT < Build.VERSION_CODES.O) return;
        NotificationChannel channel = new NotificationChannel(
                CHANNEL_ID, "Controle flutuante LGL 3.0", NotificationManager.IMPORTANCE_LOW);
        channel.setDescription("Notificação necessária enquanto o controle flutuante estiver ativo.");
        getSystemService(NotificationManager.class).createNotificationChannel(channel);
    }

    private Notification buildNotification() {
        Intent openApp = new Intent(this, MainActivity.class);
        PendingIntent pendingIntent = PendingIntent.getActivity(
                this,
                0,
                openApp,
                PendingIntent.FLAG_UPDATE_CURRENT | PendingIntent.FLAG_IMMUTABLE);
        return new NotificationCompat.Builder(this, CHANNEL_ID)
                .setSmallIcon(R.drawable.ic_stat_lgl)
                .setContentTitle("LGL 3.0 ativo")
                .setContentText("Controle flutuante visual em execução.")
                .setContentIntent(pendingIntent)
                .setOngoing(true)
                .setPriority(NotificationCompat.PRIORITY_LOW)
                .build();
    }

    private GradientDrawable background(int color, int strokeColor, int radius) {
        GradientDrawable drawable = new GradientDrawable();
        drawable.setColor(color);
        drawable.setCornerRadius(dp(radius));
        if (strokeColor != Color.TRANSPARENT) drawable.setStroke(dp(1), strokeColor);
        return drawable;
    }

    private int dp(int value) {
        return Math.round(value * getResources().getDisplayMetrics().density);
    }

    @Override
    public void onDestroy() {
        if (overlayView != null) {
            windowManager.removeView(overlayView);
            overlayView = null;
        }
        getSharedPreferences(PREFS, MODE_PRIVATE).edit()
                .putBoolean("overlay_enabled", false).apply();
        sendBroadcast(new Intent(ACTION_OVERLAY_CHANGED).setPackage(getPackageName()));
        super.onDestroy();
    }

    @Override
    public IBinder onBind(Intent intent) {
        return null;
    }

    private final class DragListener implements View.OnTouchListener {
        private int initialX;
        private int initialY;
        private float touchX;
        private float touchY;

        @Override
        public boolean onTouch(View view, MotionEvent event) {
            if (event.getAction() == MotionEvent.ACTION_DOWN) {
                WindowManager.LayoutParams params = (WindowManager.LayoutParams) view.getLayoutParams();
                initialX = params.x;
                initialY = params.y;
                touchX = event.getRawX();
                touchY = event.getRawY();
                return true;
            }
            if (event.getAction() == MotionEvent.ACTION_MOVE) {
                WindowManager.LayoutParams params = (WindowManager.LayoutParams) view.getLayoutParams();
                params.x = initialX + Math.round(event.getRawX() - touchX);
                params.y = initialY + Math.round(event.getRawY() - touchY);
                windowManager.updateViewLayout(view, params);
                return true;
            }
            return event.getAction() == MotionEvent.ACTION_UP;
        }
    }
}
