package com.namazvaktim.app;
import android.app.*;import android.content.*;import android.os.Build;
public class AlarmReceiver extends BroadcastReceiver{
 @Override public void onReceive(Context c,Intent i){NotificationHelper.createChannels(c);String title=i.getStringExtra("title");String body=i.getStringExtra("body");String ch=i.getStringExtra("channel");if(ch==null)ch="namaz_vaktim";NotificationManager nm=(NotificationManager)c.getSystemService(Context.NOTIFICATION_SERVICE);Notification.Builder b=Build.VERSION.SDK_INT>=26?new Notification.Builder(c,ch):new Notification.Builder(c);b.setSmallIcon(android.R.drawable.ic_popup_reminder).setContentTitle(title).setContentText(body).setAutoCancel(true).setContentIntent(NotificationHelper.pi(c,"open",9000));nm.notify((int)(System.currentTimeMillis()%100000),b.build());}
}
