package com.unimend.storage;

import android.app.ActivityManager;
import android.content.*;
import android.os.*;
import android.os.storage.StorageManager;
import android.app.usage.UsageEvents;
import android.app.usage.UsageStatsManager;
import android.app.usage.StorageStats;
import android.app.usage.StorageStatsManager;
import java.io.File;
import java.io.BufferedReader;
import java.io.InputStreamReader;
import java.util.Calendar;
import java.util.concurrent.TimeUnit;
import java.util.UUID;

final class DeviceStats {
    final long total, free, used, cache, ramTotal, ramUsed;
    final int battery;
    final float temperature;
    final boolean charging;
    final int[] screenBins;
    final boolean usageAvailable, cacheAvailable;

    private DeviceStats(long total,long free,long cache,long ramTotal,long ramUsed,int battery,float temperature,boolean charging,int[] screenBins,boolean usageAvailable,boolean cacheAvailable) {
        this.total=total; this.free=free; this.used=Math.max(0,total-free);
        this.cache=Math.max(0,Math.min(cache,this.used));
        this.ramTotal=ramTotal; this.ramUsed=ramUsed; this.battery=battery;
        this.temperature=temperature; this.charging=charging;
        this.screenBins=screenBins; this.usageAvailable=usageAvailable; this.cacheAvailable=cacheAvailable;
    }

    static DeviceStats read(Context c,boolean refreshRoot) {
        File data=Environment.getDataDirectory(); StatFs fs=new StatFs(data.getAbsolutePath());
        long total=fs.getTotalBytes(),free=fs.getAvailableBytes();
        android.content.SharedPreferences prefs=c.getSharedPreferences("stats",Context.MODE_PRIVATE);
        long cache=prefs.getLong("root_cache",-1),cacheTime=prefs.getLong("root_cache_time",0);
        if(refreshRoot||System.currentTimeMillis()-cacheTime>30*60*1000L) {
            long measured=readSystemCache(c);
            if(measured<0) measured=readRootCache();
            if(measured>=0) { cache=measured; prefs.edit().putLong("root_cache",cache).putLong("root_cache_time",System.currentTimeMillis()).apply(); }
        }
        ActivityManager.MemoryInfo mi=new ActivityManager.MemoryInfo();
        ((ActivityManager)c.getSystemService(Context.ACTIVITY_SERVICE)).getMemoryInfo(mi);
        Intent b=c.registerReceiver(null,new IntentFilter(Intent.ACTION_BATTERY_CHANGED));
        int level=-1; float temp=Float.NaN; boolean charging=false;
        if(b!=null) {
            int raw=b.getIntExtra(BatteryManager.EXTRA_LEVEL,-1),scale=b.getIntExtra(BatteryManager.EXTRA_SCALE,100);
            if(raw>=0&&scale>0) level=Math.round(raw*100f/scale);
            int t=b.getIntExtra(BatteryManager.EXTRA_TEMPERATURE,Integer.MIN_VALUE);
            if(t!=Integer.MIN_VALUE) temp=t/10f;
            int status=b.getIntExtra(BatteryManager.EXTRA_STATUS,-1);
            charging=status==BatteryManager.BATTERY_STATUS_CHARGING||status==BatteryManager.BATTERY_STATUS_FULL;
        }
        ScreenData screen=readScreenTime(c);
        return new DeviceStats(total,free,Math.max(0,cache),mi.totalMem,mi.totalMem-mi.availMem,level,temp,charging,screen.bins,screen.available,cache>=0);
    }

    private static long readSystemCache(Context c) {
        if(Build.VERSION.SDK_INT<26) return -1;
        try {
            StorageStatsManager manager=(StorageStatsManager)c.getSystemService(Context.STORAGE_STATS_SERVICE);
            StorageStats stats=manager.queryStatsForUser(StorageManager.UUID_DEFAULT,android.os.Process.myUserHandle());
            return stats.getCacheBytes();
        } catch(Exception ignored) { return -1; }
    }

    private static long readRootCache() {
        java.lang.Process process=null;
        try {
            process=new ProcessBuilder("/debug_ramdisk/su","-c","dumpsys diskstats").redirectErrorStream(true).start();
            if(!process.waitFor(1600,TimeUnit.MILLISECONDS)) { process.destroy(); return -1; }
            BufferedReader reader=new BufferedReader(new InputStreamReader(process.getInputStream())); String line;
            while((line=reader.readLine())!=null) if(line.startsWith("App Cache Size:")) return Long.parseLong(line.substring(line.indexOf(':')+1).trim());
        } catch(Exception ignored) { if(process!=null) process.destroy(); }
        return -1;
    }

    private static final class ScreenData { final int[] bins; final boolean available; ScreenData(int[] b,boolean a){bins=b;available=a;} }
    private static ScreenData readScreenTime(Context c) {
        int[] bins=new int[84]; if(Build.VERSION.SDK_INT<29) return new ScreenData(bins,false);
        try {
            Calendar day=Calendar.getInstance(); day.set(Calendar.HOUR_OF_DAY,0); day.set(Calendar.MINUTE,0); day.set(Calendar.SECOND,0); day.set(Calendar.MILLISECOND,0); day.add(Calendar.DAY_OF_YEAR,-6);
            long start=day.getTimeInMillis(),now=System.currentTimeMillis(),on=-1;
            UsageEvents events=((UsageStatsManager)c.getSystemService(Context.USAGE_STATS_SERVICE)).queryEvents(start,now);
            UsageEvents.Event event=new UsageEvents.Event(); boolean saw=false;
            while(events.hasNextEvent()) {
                events.getNextEvent(event); int type=event.getEventType(); long at=event.getTimeStamp();
                if(type==UsageEvents.Event.SCREEN_INTERACTIVE) { on=at; saw=true; }
                else if(type==UsageEvents.Event.SCREEN_NON_INTERACTIVE) { if(on>=0) addInterval(bins,start,on,at); on=-1; saw=true; }
            }
            if(on>=0) addInterval(bins,start,on,now);
            return new ScreenData(bins,saw);
        } catch(Exception ignored) { return new ScreenData(bins,false); }
    }
    private static void addInterval(int[] bins,long origin,long from,long to) {
        long cursor=Math.max(origin,from),end=Math.max(cursor,to),slot=2*60*60*1000L;
        while(cursor<end) {
            int index=(int)((cursor-origin)/slot); if(index<0||index>=bins.length) break;
            long boundary=origin+(index+1L)*slot,part=Math.min(end,boundary)-cursor;
            bins[index]+=Math.round(part/60000f); cursor+=part;
        }
    }
}
