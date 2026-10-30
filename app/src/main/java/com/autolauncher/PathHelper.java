package com.autolauncher;
import android.graphics.Canvas;
import android.graphics.Paint;
import android.graphics.Path;
public final class PathHelper {
    private PathHelper() {}
    public static void drawRoads(Canvas c, Paint p) {
        p.setColor(0xFF29424C); p.setStrokeWidth(10); p.setStyle(Paint.Style.STROKE);
        Path a=new Path(); a.moveTo(580,95); a.cubicTo(690,150,650,230,760,255); a.cubicTo(840,280,850,330,970,370); c.drawPath(a,p);
        Path b=new Path(); b.moveTo(620,400); b.cubicTo(690,320,760,350,810,270); b.cubicTo(870,180,900,150,985,95); c.drawPath(b,p);
        p.setColor(0xFF3A7888); p.setStrokeWidth(3); c.drawLine(780,62,780,410,p);
        p.setColor(0xFF1C3540); p.setStrokeWidth(2);
        for(int i=0;i<6;i++) c.drawLine(570,100+i*55,990,100+i*45,p);
    }
}
