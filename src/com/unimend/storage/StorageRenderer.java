package com.unimend.storage;

import android.content.Context;
import android.graphics.*;
import java.util.Locale;

final class StorageRenderer {
    private static final int W=960,H=320;
    private static final int WHITE=Color.rgb(240,240,240), GREY=Color.rgb(132,132,132);
    private static final int GREEN=Color.rgb(45,190,92), YELLOW=Color.rgb(245,190,45);
    private static final int RED=Color.rgb(235,48,54), BLUE=Color.rgb(62,151,226);
    private final Canvas c; private final Paint p=new Paint(Paint.ANTI_ALIAS_FLAG); private final Typeface doto;

    private StorageRenderer(Context context,Bitmap bitmap) {
        c=new Canvas(bitmap); c.scale(bitmap.getWidth()/(float)W,bitmap.getHeight()/(float)H);
        Typeface loaded;
        try { loaded=Typeface.createFromAsset(context.getAssets(),"doto.ttf"); }
        catch(Exception e) { loaded=Typeface.create("sans-serif",Typeface.BOLD); }
        doto=loaded;
    }
    static Bitmap render(Context context,DeviceStats s,int widthPx,int heightPx,int scanColumn) {
        Bitmap b=Bitmap.createBitmap(Math.max(320,widthPx),Math.max(108,heightPx),Bitmap.Config.ARGB_8888);
        b.setDensity(Bitmap.DENSITY_NONE); new StorageRenderer(context,b).draw(s,scanColumn); return b;
    }
    private void draw(DeviceStats s,int scan) {
        c.drawColor(Color.BLACK);
        float usedPct=s.total==0?0:s.used*100f/s.total;
        text("STORAGE",30,49,25,WHITE,true,Paint.Align.LEFT);
        text(Math.round(usedPct)+"%",30,112,82,WHITE,true,Paint.Align.LEFT);
        int ram=s.ramTotal==0?0:Math.round(s.ramUsed*100f/s.ramTotal);
        small("RAM",30,181,ram+"%",WHITE);
        String battery=s.battery<0?"--%":s.battery+"%";
        String temp=Float.isNaN(s.temperature)?"--°":String.format(Locale.US,"%.0f°",s.temperature);
        small(s.charging?"CHARGE":"BAT",30,222,battery+"  "+temp,s.charging?YELLOW:WHITE);

        final int cols=16,rows=8; final float x0=272,y0=72,cell=16,gap=4;
        int count=cols*rows, used=Math.round(count*(s.total==0?0:s.used/(float)s.total));
        int cache=Math.min(used,Math.round(count*(s.total==0?0:s.cache/(float)s.total)));
        int normal=Math.max(0,used-cache);
        text("CAPACITY",272,49,17,Color.rgb(120,120,120),false,Paint.Align.LEFT);
        for(int col=0;col<cols;col++) for(int row=0;row<rows;row++) {
            int index=col*rows+(rows-1-row); int color;
            if(index<normal) {
                int variation=((col*17+row*11)%4)*10;
                color=Color.rgb(Math.max(20,45-variation/2),Math.max(100,190-variation),Math.max(45,92-variation/3));
            } else if(index<used) color=YELLOW;
            else color=Color.rgb(27,27,27);
            float cx=x0+col*(cell+gap)+cell/2f,cy=y0+row*(cell+gap)+cell/2f;
            if(scan>=0 && onSlash(cx,cy,scan)) color=mix(color,Color.WHITE,.72f);
            p.setColor(color); p.setStyle(Paint.Style.FILL);
            c.drawRoundRect(x0+col*(cell+gap),y0+row*(cell+gap),x0+col*(cell+gap)+cell,y0+row*(cell+gap)+cell,3,3,p);
        }
        gridMetric("USED",format(s.used),272,260,GREEN);
        gridMetric("CACHE",s.cacheAvailable?format(s.cache):"--",380,260,YELLOW);
        gridMetric("FREE",format(s.free),494,260,GREY);
        p.setColor(Color.rgb(42,42,42)); c.drawRoundRect(608,52,611,272,1.5f,1.5f,p);
        drawScreenHeatmap(s,634,72,scan);
        warningBars(s,usedPct);
    }
    private void drawScreenHeatmap(DeviceStats s,float x0,float y0,int scan) {
        text("SCREEN · 7D",x0,49,17,s.usageAvailable?Color.rgb(120,120,120):RED,false,Paint.Align.LEFT);
        final float cell=16,gap=4;
        for(int day=0;day<7;day++) for(int slot=0;slot<12;slot++) {
            int minutes=s.screenBins[day*12+slot]; float strength=Math.min(1f,minutes/100f);
            int color=s.usageAvailable?mix(Color.rgb(18,27,35),BLUE,.18f+.82f*strength):Color.rgb(25,25,25);
            float x=x0+slot*(cell+gap),y=y0+day*(cell+gap);
            if(scan>=0&&onSlash(x+cell/2f,y+cell/2f,scan)) color=mix(color,Color.WHITE,.68f);
            p.setColor(color); c.drawRoundRect(x,y,x+cell,y+cell,3,3,p);
        }
        if(!s.usageAvailable) text("NO ACCESS",x0+118,226,19,RED,false,Paint.Align.CENTER);
        else {
            int today=0; for(int i=72;i<84;i++) today+=s.screenBins[i];
            text("TODAY",x0,260,16,Color.rgb(105,105,105),false,Paint.Align.LEFT);
            text(duration(today),x0+238,260,23,BLUE,true,Paint.Align.RIGHT);
        }
    }
    private boolean onSlash(float x,float y,int scan) {
        float start=272+72,end=870+228;
        float center=start+(end-start)*Math.max(0,Math.min(39,scan))/39f;
        return Math.abs((x+y)-center)<20f;
    }
    private void gridMetric(String label,String value,float x,float y,int color) {
        text(label,x,y-10,14,Color.rgb(100,100,100),false,Paint.Align.LEFT);
        text(value,x,y+12,17,color,false,Paint.Align.LEFT);
    }
    private static String duration(int minutes) {
        if(minutes<60) return minutes+"M";
        return (minutes/60)+"H "+(minutes%60)+"M";
    }
    private void warningBars(DeviceStats s,float usedPct) {
        float top=42,bottom=278,x=914;
        bar(x,top,bottom,usedPct>=85?RED:Color.rgb(45,12,14)); x+=10;
        bar(x,top,bottom,!Float.isNaN(s.temperature)&&s.temperature>=42?YELLOW:Color.rgb(45,35,12)); x+=10;
        int batteryColor=s.charging?GREEN:(s.battery>=0&&s.battery<=20?BLUE:Color.rgb(12,29,45));
        bar(x,top,bottom,batteryColor);
    }
    private void bar(float x,float top,float bottom,int color) { p.setColor(color); c.drawRoundRect(x,top,x+5,bottom,2.5f,2.5f,p); }
    private void small(String label,float x,float y,String value,int color) {
        text(label,x,y,19,GREY,false,Paint.Align.LEFT); text(value,x+93,y,20,color,false,Paint.Align.LEFT);
    }
    private void text(String value,float x,float centerY,float size,int color,boolean dot,Paint.Align align) {
        p.setTypeface(dot?doto:Typeface.create("sans-serif-medium",Typeface.NORMAL)); p.setTextSize(size);
        p.setColor(color); p.setStyle(Paint.Style.FILL); p.setTextAlign(align);
        Paint.FontMetrics fm=p.getFontMetrics(); c.drawText(value,x,centerY-(fm.ascent+fm.descent)/2f,p);
    }
    private static String format(long bytes) {
        if(bytes<0) return "--"; double gb=bytes/1073741824d;
        return gb>=100?String.format(Locale.US,"%.0f GB",gb):String.format(Locale.US,"%.1f GB",gb);
    }
    private static int mix(int a,int b,float n) {
        return Color.rgb(Math.round(Color.red(a)+(Color.red(b)-Color.red(a))*n),Math.round(Color.green(a)+(Color.green(b)-Color.green(a))*n),Math.round(Color.blue(a)+(Color.blue(b)-Color.blue(a))*n));
    }
}
