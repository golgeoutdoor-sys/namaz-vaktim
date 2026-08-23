package com.namazvaktim.app;

import android.app.*;
import android.content.*;
import android.os.Build;
import org.json.JSONObject;
import java.util.*;

public class NotificationHelper {
    static final String CH="namaz_vaktim";
    public static void createChannels(Context c){if(Build.VERSION.SDK_INT>=26){NotificationManager nm=c.getSystemService(NotificationManager.class);nm.createNotificationChannel(new NotificationChannel(CH,"Namaz Vakitleri",NotificationManager.IMPORTANCE_HIGH));nm.createNotificationChannel(new NotificationChannel("ayet","Günün Ayeti",NotificationManager.IMPORTANCE_DEFAULT));}}
    static PendingIntent pi(Context c,String action,int id){Intent i=new Intent(c,MainActivity.class);i.setAction(action);return PendingIntent.getActivity(c,id,i,PendingIntent.FLAG_UPDATE_CURRENT|PendingIntent.FLAG_IMMUTABLE);}
    public static void schedule(Context c,String title,String body,long when,int id,String channel){if(when<=System.currentTimeMillis())return;AlarmManager am=(AlarmManager)c.getSystemService(Context.ALARM_SERVICE);Intent i=new Intent(c,AlarmReceiver.class);i.putExtra("title",title);i.putExtra("body",body);i.putExtra("channel",channel);PendingIntent p=PendingIntent.getBroadcast(c,id,i,PendingIntent.FLAG_UPDATE_CURRENT|PendingIntent.FLAG_IMMUTABLE);if(Build.VERSION.SDK_INT>=31&&!am.canScheduleExactAlarms())return;if(Build.VERSION.SDK_INT>=23)am.setExactAndAllowWhileIdle(AlarmManager.RTC_WAKEUP,when,p);else am.setExact(AlarmManager.RTC_WAKEUP,when,p);}
    public static void schedulePrayerNotifications(Context c,JSONObject t){SharedPreferences p=c.getSharedPreferences("namaz",Context.MODE_PRIVATE);if(!p.getBoolean("notify_on",true))return;int mins=p.getInt("notify_min",3);String[] keys={"Fajr","Dhuhr","Asr","Maghrib","Isha"};String[] names={"Sabah","Öğle","İkindi","Akşam","Yatsı"};Calendar now=Calendar.getInstance();for(int i=0;i<keys.length;i++){try{String[] z=t.optString(keys[i]).substring(0,5).split(":");Calendar x=(Calendar)now.clone();x.set(Calendar.HOUR_OF_DAY,Integer.parseInt(z[0]));x.set(Calendar.MINUTE,Integer.parseInt(z[1]));x.set(Calendar.SECOND,0);x.set(Calendar.MILLISECOND,0);x.add(Calendar.MINUTE,-mins);schedule(c,names[i]+" vakti yaklaşıyor","⏰ "+names[i]+" vaktine "+mins+" dakika kaldı.",x.getTimeInMillis(),100+i,CH);}catch(Exception ignored){}}}
    public static void scheduleDailyAyet(Context c){SharedPreferences p=c.getSharedPreferences("namaz",Context.MODE_PRIVATE);if(!p.getBoolean("ayet_notify",true))return;Calendar x=Calendar.getInstance();x.set(Calendar.HOUR_OF_DAY,9);x.set(Calendar.MINUTE,0);x.set(Calendar.SECOND,0);x.set(Calendar.MILLISECOND,0);if(x.getTimeInMillis()<=System.currentTimeMillis())x.add(Calendar.DAY_OF_YEAR,1);schedule(c,"Namaz Vaktim — Günün Ayeti","📖 Bugünün ayetini okumak için uygulamayı açın.",x.getTimeInMillis(),2000,"ayet");}
}
