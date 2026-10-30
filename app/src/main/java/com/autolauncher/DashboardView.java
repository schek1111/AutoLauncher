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
    private final Paint p = new Paint(3);
    private float speed = -1, accuracy = -1;
    private AppLauncher appLauncher;
    private AppDrawer appDrawer;
    private final List<RectF> appHits = new ArrayList<>();
    private RectF allHit = new RectF();
    private float scaleX, scaleY;

    public DashboardView(Context c) { super(c); setLayerType(View.LAYER_TYPE_SOFTWARE, null); }
    public void setAppLauncher(AppLauncher l) { appLauncher = l; }
    public void setAppDrawer(AppDrawer d) { appDrawer = d; }
    public void setGps(float kmh, float acc) { speed = kmh; accuracy = acc; postInvalidate(); }
    public void setGpsUnavailable() { speed = -1; postInvalidate(); }

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
        // Subtle technical background
        p.setColor(col("#101B25")); p.setStrokeWidth(1);
        for(int x=0;x<1024;x+=32)c.drawLine(x,0,x,600,p);
        for(int y=0;y<600;y+=32)c.drawLine(0,y,1024,y,p);

        // Left app rail
        panel(c,12,12,104,588);
        text(c,"AUTO",28,43,col("#57DDF2"),15,true);
        String[] names={"NAV","MUS","TEL","SET"};
        String[] pkgs={"ru.yandex.yandexnavi","com.maxmpz.audioplayer","com.android.dialer","com.android.settings"};
        appHits.clear();
        for(int i=0;i<4;i++){
            float y=70+i*86; RectF hit=new RectF(22,y,94,y+68); appHits.add(hit);
            p.setColor(col("#203746")); c.drawRoundRect(hit,13,13,p);
            p.setColor(col("#42CFEA")); p.setStyle(Paint.Style.STROKE); p.setStrokeWidth(1.5f); c.drawRoundRect(hit,13,13,p); p.setStyle(Paint.Style.FILL);
            text(c,names[i],37,y+41,col("#D9F8FF"),17,true);
        }
        allHit.set(20,438,96,558); p.setColor(col("#193443")); c.drawRoundRect(allHit,12,12,p);
        text(c,"ВСЕ",35,480,col("#63DDF1"),15,true); text(c,"APP",36,502,col("#63DDF1"),15,true);

        // GPS speedometer
        panel(c,118,12,536,365);
        text(c,"GPS  •  СКОРОСТЬ",142,43,col("#77DDED"),16,true);
        String val=speed<0?"---":String.valueOf(Math.round(speed));
        paint(c,col("#F1FBFF"),112,true); c.drawText(val,150,205,p);
        text(c,"км/ч",365,204,col("#55D6ED"),25,true);
        text(c,speed<0?"ОЖИДАНИЕ GPS":"GPS ПРИЁМ АКТИВЕН",146,248,col("#8CA6B5"),14,true);
        text(c,accuracy>0?"ТОЧНОСТЬ  ±"+Math.round(accuracy)+" м":"ОЖИДАНИЕ СПУТНИКОВ",146,278,col("#617F91"),12,false);
        p.setColor(col("#24495B")); p.setStrokeWidth(3); c.drawRoundRect(145,310,505,316,3,3,p);

        // OBD2 block, intentionally separate from speedometer and map
        panel(c,118,380,536,588);
        text(c,"OBD2  •  ДАННЫЕ ЭБУ",140,411,col("#77DDED"),15,true);
        String[][] data={{"ТЕМПЕРАТУРА","-- °C"},{"СРЕДНИЙ РАСХОД","-- л/100"},{"НАПРЯЖЕНИЕ","-- В"},{"ОБОРОТЫ","---- об/мин"}};
        for(int i=0;i<4;i++){
            int y=446+i*32;
            text(c,data[i][0],142,y,col("#91A7B4"),13,true);
            text(c,data[i][1],360,y,col("#E9F8FC"),17,true);
        }

        // Navigation placeholder: real Yandex UI integration is a later task
        panel(c,550,12,1012,430);
        text(c,"НАВИГАЦИЯ",574,43,col("#77DDED"),15,true);
        p.setColor(col("#12212B")); c.drawRoundRect(568,58,994,414,12,12,p);
        p.setColor(col("#31505D")); p.setStrokeWidth(3); p.setStyle(Paint.Style.STROKE);
        PathHelper.drawRoads(c,p); p.setStyle(Paint.Style.FILL);
        text(c,"Яндекс Навигатор подключим отдельно",590,236,col("#9CB3C0"),18,true);
        text(c,"КАРТА ПОКА НЕ ЗАГРУЖАЕТСЯ",590,264,col("#5E8798"),12,true);

        // Radio and media
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
        if(e.getAction()!=MotionEvent.ACTION_UP)return true;
        float x=e.getX()*1024f/getWidth(), y=e.getY()*600f/getHeight();
        if(allHit.contains(x,y)){if(appDrawer!=null)appDrawer.open();return true;}
        for(int i=0;i<appHits.size();i++) if(appHits.get(i).contains(x,y)){
            if(appLauncher!=null)appLauncher.launch(new String[]{"ru.yandex.yandexnavi","com.maxmpz.audioplayer","com.android.dialer","com.android.settings"}[i]);
            return true;
        }
        return true;
    }
}
