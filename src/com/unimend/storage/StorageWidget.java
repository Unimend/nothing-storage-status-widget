package com.unimend.storage;

import android.app.PendingIntent;
import android.appwidget.*;
import android.content.*;
import android.graphics.Bitmap;
import android.os.*;
import android.widget.RemoteViews;

public class StorageWidget extends AppWidgetProvider {
    private static final String REFRESH="com.unimend.storage.REFRESH";
    private static final android.util.SparseIntArray animations=new android.util.SparseIntArray();
    private static int[] ids(Context c) { return AppWidgetManager.getInstance(c).getAppWidgetIds(new ComponentName(c,StorageWidget.class)); }
    private static void draw(Context c,int id,int scan) { draw(c,id,scan,DeviceStats.read(c,false)); }
    private static void draw(Context c,int id,int scan,DeviceStats stats) {
        AppWidgetManager manager=AppWidgetManager.getInstance(c); Bundle o=manager.getAppWidgetOptions(id);
        int dpW=Math.max(250,o.getInt(AppWidgetManager.OPTION_APPWIDGET_MIN_WIDTH,280));
        int dpH=Math.max(80,o.getInt(AppWidgetManager.OPTION_APPWIDGET_MAX_HEIGHT,100));
        float density=c.getResources().getDisplayMetrics().density;
        int pxW=Math.min(1800,Math.max(640,Math.round(dpW*density)));
        int pxH=Math.min(700,Math.max(220,Math.round(dpH*density)));
        Bitmap bitmap=StorageRenderer.render(c,stats,pxW,pxH,scan);
        RemoteViews views=new RemoteViews(c.getPackageName(),R.layout.widget); views.setImageViewBitmap(R.id.content,bitmap);
        String desc="存储已用"+Math.round(stats.used*100f/stats.total)+"%，可用"+(stats.free/1073741824L)+"GB，电量"+stats.battery+"%";
        views.setContentDescription(R.id.content,desc);
        Intent refresh=new Intent(c,StorageWidget.class).setAction(REFRESH).putExtra(AppWidgetManager.EXTRA_APPWIDGET_ID,id);
        views.setOnClickPendingIntent(R.id.refresh,PendingIntent.getBroadcast(c,id,refresh,PendingIntent.FLAG_IMMUTABLE|PendingIntent.FLAG_UPDATE_CURRENT));
        manager.updateAppWidget(id,views);
    }
    private static void animate(Context c,int id) {
        Context app=c.getApplicationContext();
        new Thread(()->animateReady(app,id,DeviceStats.read(app,true)),"storage-sample").start();
    }
    private static void animateReady(Context app,int id,DeviceStats stats) {
        int token;
        synchronized(animations) { token=animations.get(id,0)+1; animations.put(id,token); }
        Handler h=new Handler(Looper.getMainLooper());
        for(int frame=0;frame<14;frame++) { final int sweep=Math.min(39,frame*3);
            h.postDelayed(()-> { synchronized(animations) { if(animations.get(id)!=token) return; } draw(app,id,sweep,stats); },frame*100L);
        }
        h.postDelayed(()-> { synchronized(animations) { if(animations.get(id)!=token) return; animations.delete(id); } draw(app,id,-1,stats); },1450L);
    }
    @Override public void onUpdate(Context c,AppWidgetManager m,int[] widgetIds) { for(int id:widgetIds) animate(c,id); }
    @Override public void onAppWidgetOptionsChanged(Context c,AppWidgetManager m,int id,Bundle options) { draw(c,id,-1); }
    @Override public void onReceive(Context c,Intent i) {
        super.onReceive(c,i); String action=i.getAction();
        if(REFRESH.equals(action)) {
            int id=i.getIntExtra(AppWidgetManager.EXTRA_APPWIDGET_ID,-1); for(int existing:ids(c)) if(existing==id) animate(c,id);
        } else if(Intent.ACTION_MY_PACKAGE_REPLACED.equals(action)||Intent.ACTION_BOOT_COMPLETED.equals(action)||Intent.ACTION_DEVICE_STORAGE_LOW.equals(action)||Intent.ACTION_DEVICE_STORAGE_OK.equals(action)||Intent.ACTION_POWER_CONNECTED.equals(action)||Intent.ACTION_POWER_DISCONNECTED.equals(action)) {
            for(int id:ids(c)) draw(c,id,-1);
        }
    }
}
