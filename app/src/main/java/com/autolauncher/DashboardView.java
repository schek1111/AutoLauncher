package com.autolauncher;

import android.content.Context;
import android.graphics.Canvas;
import android.graphics.Paint;
import android.graphics.RectF;
import android.graphics.Typeface;
import android.view.MotionEvent;
import android.view.View;
import java.util.ArrayList;
import java.util.List;

public class DashboardView extends View {
    public interface AppLauncher { void launch(String packageName); }
    public interface AppDrawer { void open(); }
    public interface AppRemover { void remove(String packageName, String label); }
    public static class AppShortcut {
        public final String packageName;
        public final String label;
        public AppShortcut(String pkg, String name) { packageName = pkg; label = name; }
    }

    private final Paint p = new Paint(3);
    private float speed = -1, accuracy = -1;
    private AppLauncher appLauncher;
    private AppDrawer appDrawer;
    private AppRemover appRemover;
    private final List<AppShortcut> pinnedApps = new ArrayList<>();
    private final List<RectF> appHits = new ArrayList<>();
    private final List<RectF> removeHits = new ArrayList<>();
    private final RectF allHit = new RectF();
    private float railScroll = 0, downX, downY, lastY;
    private boolean dragging = false, moved = false;
    private long downTime;
    private int touchedIndex = -1;
    private static final float RAIL_TOP = 58, RAIL_BOTTOM = 486, ITEM_H = 66, ITEM_GAP = 10;

    public DashboardView(Context c) { super(c); setLayerType(View.LAYER_TYPE_SOFTWARE, null); }
    public void setAppLauncher(AppLauncher l) { appLauncher = l; }
    public void setAppDrawer(AppDrawer d) { appDrawer = d; }
    public void setAppRemover(AppRemover r) { appRemover = r; }
    public void setGps(float kmh, float acc) { speed = kmh; accuracy = acc; postInvalidate(); }
    public void setGpsUnavailable() { speed = -1; postInvalidate(); }
    public void setPinnedApps(List<AppShortcut> apps) { pinnedApps.clear(); pinnedApps.addAll(apps); invalidate(); }
    public List<AppShortcut> getPinnedApps() { return new ArrayList<>(pinnedApps); }
    public boolean hasPinnedApp(String pkg) { for (AppShortcut a : pinnedApps) if (a.packageName.equals(pkg)) return true; return false; }
    public void addPinnedApp(AppShortcut app) { if (!hasPinnedApp(app.packageName)) { pinnedApps.add(app); invalidate(); } }
    public void removePinnedApp(String pkg) { for (int i=0;i<pinnedApps.size();i++) if (pinnedApps.get(i).packageName.equals(pkg)) { pinnedApps.remove(i); break; } invalidate(); }

    private void paint(Canvas c, int color, float size, boolean bold) {
        p.setColor(color); p.setTextSize(size); p.setTypeface(bold ? Typeface.create("sans-serif", Typeface.BOLD) : Typeface.create("sans-serif", Typeface.NORMAL));
        p.setStyle(Paint.Style.FILL); p.setShadowLayer(0,0,0,0);
    }
    private int col(String s) { return android.graphics.Color.parseColor(s); }
    private void panel(Canvas c, float l,float t,float r,float b) {
        p.setColor(col("#151F29")); p.setStyle(Paint.Style.FILL); p.setShadowLayer(12,0,0,col("#223F58")); c.drawRoundRect(l,t,r,b,18,18,p); p.clearShadowLayer();
        p.setColor(col("#28546A")); p.setStyle(Paint.Style.STROKE); p.setStrokeWidth(1.4f); c.drawRoundRect(l,t,r,b,18,18,p); p.setStyle(Paint.Style.FILL);
    }
    private void text(Canvas c,String s,float x,float y,int color,float size,boolean bold) { paint(c,color,size,bold); c.drawText(s,x,y,p); }

    @Override protected void onDraw(Canvas canvas) {
        super.onDraw(canvas);
        Canvas c=canvas; c.save(); c.scale(getWidth()/1024f,getHeight()/600f);
        c.drawColor(col("#080D13"));
        p.setColor(col("#101B25")); p.setStrokeWidth(1);
        for(int x=0;x<1024;x+=32)c.drawLine(x,0,x,600,p);
        for(int y=0;y<600;y+=32)c.drawLine(0,y,1024,y,p);

        // Scrollable app rail; tap launches, long-press removes.
        panel(c,12,12,104,588);
        text(c,"AUTO",28,42,col("#57DDF2"),15,true);
        appHits.clear(); removeHits.clear();
        c.save();
        c.clipRect(16,RAIL_TOP,100,RAIL_BOTTOM);
        float maxScroll = Math.max(0, pinnedApps.size()*(ITEM_H+ITEM_GAP)-ITEM_GAP-(RAIL_BOTTOM-RAIL_TOP));
        railScroll = Math.max(0, Math.min(railScroll, maxScroll));
        for(int i=0;i<pinnedApps.size();i++){
            float y=RAIL_TOP+i*(ITEM_H+ITEM_GAP)-railScroll;
            RectF hit=new RectF(22,y,94,y+ITEM_H); appHits.add(hit);
            p.setColor(col("#203746")); c.drawRoundRect(hit,13,13,p);
            p.setColor(col("#42CFEA")); p.setStyle(Paint.Style.STROKE); p.setStrokeWidth(1.5f); c.drawRoundRect(hit,13,13,p); p.setStyle(Paint.Style.FILL);
            String label=pinnedApps.get(i).label;
            if(label.length()>9) label=label.substring(0,8)+"…";
            paint(c,col("#D9F8FF"),label.length()>6?12:15,true);
            float tw=p.measureText(label);
            c.drawText(label,hit.centerX()-tw/2,y+40,p);
        }
        c.restore();
        allHit.set(20,510,96,570); p.setColor(col("#193443")); c.drawRoundRect(allHit,12,12,p);
        text(c,"+ APP",29,546,col("#63DDF1"),15,true);
        text(c,"ДОБАВИТЬ",21,562,col("#63DDF1"),9,true);

        panel(c,118,12,536,365);
        text(c,"GPS  •  СКОРОСТЬ",142,43,col("#77DDED"),16,true);
        String val=speed<0?"---":String.valueOf(Math.round(speed));
        paint(c,col("#F1FBFF"),112,true); c.drawText(val,150,205,p);
        text(c,"км/ч",365,204,col("#55D6ED"),25,true);
        text(c,speed<0?"ОЖИДАНИЕ GPS":"GPS ПРИЁМ АКТИВЕН",146,248,col("#8CA6B5"),14,true);
        text(c,accuracy>0?"ТОЧНОСТЬ  ±"+Math.round(accuracy)+" м":"ОЖИДАНИЕ СПУТНИКОВ",146,278,col("#617F91"),12,false);
        p.setColor(col("#24495B")); p.setStrokeWidth(3); c.drawRoundRect(145,310,505,316,3,3,p);

        panel(c,118,380,536,588);
        text(c,"OBD2  •  ДАННЫЕ ЭБУ",140,411,col("#77DDED"),15,true);
        String[][] data={{"ТЕМПЕРАТУРА","-- °C"},{"СРЕДНИЙ РАСХОД","-- л/100"},{"НАПРЯЖЕНИЕ","-- В"},{"ОБОРОТЫ","---- об/мин"}};
        for(int i=0;i<4;i++){
            int y=446+i*32;
            text(c,data[i][0],142,y,col("#91A7B4"),13,true);
            text(c,data[i][1],360,y,col("#E9F8FC"),17,true);
        }

        panel(c,550,12,1012,430);
        text(c,"НАВИГАЦИЯ",574,43,col("#77DDED"),15,true);
        p.setColor(col("#12212B")); c.drawRoundRect(568,58,994,414,12,12,p);
        p.setColor(col("#31505D")); p.setStrokeWidth(3); p.setStyle(Paint.Style.STROKE);
        PathHelper.drawRoads(c,p); p.setStyle(Paint.Style.FILL);
        text(c,"Яндекс Навигатор подключим отдельно",590,236,col("#9CB3C0"),18,true);
        text(c,"КАРТА ПОКА НЕ ЗАГРУЖАЕТСЯ",590,264,col("#5E8798"),12,true);

        panel(c,550,444,1012,588);
        text(c,"РАДИО",574,474,col("#77DDED"),14,true);
        text(c,"Штатный FM-тюнер",574,511,col("#B7C8D0"),16,false);
        text(c,"FM",574,550,col("#55D6ED"),22,true);
        p.setColor(col("#2A4654")); c.drawLine(770,460,770,572,p);
        text(c,"МУЗЫКА",792,474,col("#77DDED"),14,true);
        text(c,"Локальный плеер",792,511,col("#B7C8D0"),16,false);
        text(c,"◀     ▶     ▶▶",792,550,col("#55D6ED"),20,true);
        c.restore();
    }

    @Override public boolean onTouchEvent(MotionEvent e) {
        float x=e.getX()*1024f/getWidth(), y=e.getY()*600f/getHeight();
        switch(e.getAction()) {
            case MotionEvent.ACTION_DOWN:
                downX=x; downY=y; lastY=y; downTime=System.currentTimeMillis(); moved=false; dragging=false; touchedIndex=-1;
                if(x>=12 && x<=104 && y>=RAIL_TOP && y<=RAIL_BOTTOM) {
                    for(int i=0;i<appHits.size();i++) if(appHits.get(i).contains(x,y)) { touchedIndex=i; break; }
                }
                return true;
            case MotionEvent.ACTION_MOVE:
                if(x>=12 && x<=104 && downY>=RAIL_TOP && downY<=RAIL_BOTTOM) {
                    if(Math.abs(y-downY)>7) { dragging=true; moved=true; }
                    if(dragging) { railScroll += lastY-y; lastY=y; invalidate(); }
                }
                return true;
            case MotionEvent.ACTION_UP:
                if(moved) return true;
                if(allHit.contains(x,y)) { if(appDrawer!=null) appDrawer.open(); return true; }
                if(touchedIndex>=0 && touchedIndex<pinnedApps.size() && x>=12 && x<=104) {
                    AppShortcut app=pinnedApps.get(touchedIndex);
                    if(System.currentTimeMillis()-downTime>650) {
                        if(appRemover!=null) appRemover.remove(app.packageName,app.label);
                    } else if(appLauncher!=null) appLauncher.launch(app.packageName);
                    return true;
                }
                return true;
        }
        return true;
    }
}