package es.calma.instagram;

import android.app.*;
import android.content.*;
import android.content.pm.PackageManager;
import android.os.*;
import android.service.notification.*;

/** Local mirroring of official Instagram DM notifications. No network or message API. */
public class DmNotificationService extends NotificationListenerService {
    private static final String CHANNEL="instagram_calma_dm";
    private NotificationManager manager() { return (NotificationManager)getSystemService(NOTIFICATION_SERVICE); }
    @Override public void onListenerConnected() {
        NotificationChannel channel=new NotificationChannel(CHANNEL,"Mensajes directos",NotificationManager.IMPORTANCE_HIGH);
        channel.setDescription("Avisos de DM recibidos por Instagram oficial"); manager().createNotificationChannel(channel);
    }
    @Override public void onNotificationPosted(StatusBarNotification sbn) {
        if(sbn==null || !"com.instagram.android".equals(sbn.getPackageName()) || !getSharedPreferences("calma",MODE_PRIVATE).getBoolean("dm_mirror",false))return;
        if(Build.VERSION.SDK_INT>=33 && checkSelfPermission("android.permission.POST_NOTIFICATIONS")!=PackageManager.PERMISSION_GRANTED)return;
        Notification original=sbn.getNotification();
        if((original.flags & Notification.FLAG_GROUP_SUMMARY)!=0)return;
        Bundle extras=original.extras;
        if(!DmRules.isDm(sbn.getPackageName(),original.category,original.getChannelId(),extras.containsKey(Notification.EXTRA_MESSAGES)))return;
        CharSequence title=extras.getCharSequence(Notification.EXTRA_TITLE);
        CharSequence text=extras.getCharSequence(Notification.EXTRA_TEXT);
        if(text==null)text=extras.getCharSequence(Notification.EXTRA_BIG_TEXT);
        if(title==null && text==null)return;
        Intent open=new Intent(this,MainActivity.class).putExtra("open_dm",true).addFlags(Intent.FLAG_ACTIVITY_CLEAR_TOP | Intent.FLAG_ACTIVITY_SINGLE_TOP);
        PendingIntent pending=PendingIntent.getActivity(this,0,open,PendingIntent.FLAG_UPDATE_CURRENT | PendingIntent.FLAG_IMMUTABLE);
        int icon=getResources().getIdentifier("notification","drawable",getPackageName());
        Notification copy=new Notification.Builder(this,CHANNEL).setSmallIcon(icon)
            .setContentTitle(title==null?"Mensaje directo":title).setContentText(text)
            .setStyle(new Notification.BigTextStyle().bigText(text)).setContentIntent(pending)
            .setCategory(Notification.CATEGORY_MESSAGE).setVisibility(Notification.VISIBILITY_PRIVATE)
            .setWhen(original.when).setOnlyAlertOnce(true).setAutoCancel(true).build();
        try { manager().notify(sbn.getKey(),1,copy); }catch(SecurityException ignored){}
    }
    @Override public void onNotificationRemoved(StatusBarNotification sbn) {
        if(sbn!=null && "com.instagram.android".equals(sbn.getPackageName()))manager().cancel(sbn.getKey(),1);
    }
}
