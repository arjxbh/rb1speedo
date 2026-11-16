package club.midlife.rb1speed;

import android.content.*;

/** Boot/power events schedule uploads only; they never launch a background camera. */
public final class UploadReceiver extends BroadcastReceiver {
    @Override public void onReceive(Context context, Intent intent) {
        if (Intent.ACTION_POWER_CONNECTED.equals(intent.getAction())) UploadJob.powerChanged(context, true);
        if (Intent.ACTION_POWER_DISCONNECTED.equals(intent.getAction())) UploadJob.powerChanged(context, false);
        UploadJob.schedule(context, false);
        if (Intent.ACTION_BOOT_COMPLETED.equals(intent.getAction()) && context.getSharedPreferences("dashcam", 0).getBoolean("enabled", false) &&
            (android.os.Build.VERSION.SDK_INT < 33 || context.checkSelfPermission(android.Manifest.permission.POST_NOTIFICATIONS) == android.content.pm.PackageManager.PERMISSION_GRANTED)) {
            android.app.NotificationManager manager = (android.app.NotificationManager)context.getSystemService(Context.NOTIFICATION_SERVICE);
            manager.createNotificationChannel(new android.app.NotificationChannel("dashcam-startup", "Dashcam startup", android.app.NotificationManager.IMPORTANCE_DEFAULT));
            android.app.PendingIntent open = android.app.PendingIntent.getActivity(context, 84, new Intent(context, MainActivity.class),
                android.app.PendingIntent.FLAG_IMMUTABLE | android.app.PendingIntent.FLAG_UPDATE_CURRENT);
            manager.notify(84, new android.app.Notification.Builder(context, "dashcam-startup").setSmallIcon(android.R.drawable.presence_video_online)
                .setContentTitle("Open RB1 to resume dashcam").setContentText("Phone restarted. Tap to open the dashboard and enable powered recording.")
                .setContentIntent(open).setAutoCancel(true).build());
        }
    }
}
