package com.autolauncher;

import android.app.Activity;
import android.app.AlertDialog;
import android.bluetooth.BluetoothAdapter;
import android.bluetooth.BluetoothDevice;
import android.bluetooth.BluetoothSocket;
import android.os.Bundle;
import android.os.Handler;
import android.os.Looper;
import android.view.Gravity;
import android.view.View;
import android.widget.Button;
import android.widget.LinearLayout;
import android.widget.ScrollView;
import android.widget.TextView;
import java.io.InputStream;
import java.io.OutputStream;
import java.util.ArrayList;
import java.util.Set;
import java.util.UUID;

public class DiagnosticActivity extends Activity {
    private static final UUID SPP = UUID.fromString("00001101-0000-1000-8000-00805F9B34FB");
    private final Handler handler = new Handler(Looper.getMainLooper());
    private BluetoothAdapter adapter;
    private BluetoothSocket socket;
    private Thread worker;
    private TextView status, log;
    private Button connectButton, readButton;
    private volatile boolean running;

    @Override public void onCreate(Bundle state) {
        super.onCreate(state);
        getWindow().getDecorView().setSystemUiVisibility(View.SYSTEM_UI_FLAG_FULLSCREEN | View.SYSTEM_UI_FLAG_HIDE_NAVIGATION | View.SYSTEM_UI_FLAG_IMMERSIVE_STICKY);
        adapter = BluetoothAdapter.getDefaultAdapter();
        LinearLayout root = new LinearLayout(this);
        root.setOrientation(LinearLayout.VERTICAL);
        root.setPadding(24,18,24,18);
        root.setBackgroundColor(0xFF080D13);
        TextView title = makeText("ДИАГНОСТИКА ЭБУ • ELM327",22,0xFF63DDF1);
        root.addView(title);
        status = makeText("Статус: адаптер не подключён",16,0xFFEAF8FC);
        root.addView(status);
        LinearLayout buttons = new LinearLayout(this);
        buttons.setOrientation(LinearLayout.HORIZONTAL);
        connectButton = new Button(this);
        connectButton.setText("ВЫБРАТЬ ELM327");
        connectButton.setOnClickListener(v -> chooseDevice());
        buttons.addView(connectButton);
        readButton = new Button(this);
        readButton.setText("ОПРОС ЭБУ");
        readButton.setEnabled(false);
        readButton.setOnClickListener(v -> requestEcuData());
        buttons.addView(readButton);
        Button back = new Button(this);
        back.setText("НАЗАД");
        back.setOnClickListener(v -> finish());
        buttons.addView(back);
        root.addView(buttons);
        TextView hint = makeText("Журнал обмена с адаптером и ЭБУ",14,0xFF77DDED);
        root.addView(hint);
        ScrollView scroll = new ScrollView(this);
        log = makeText("Ожидание подключения…",13,0xFFB7C8D0);
        log.setGravity(Gravity.TOP);
        scroll.addView(log);
        root.addView(scroll,new LinearLayout.LayoutParams(-1,0,1));
        setContentView(root);
        if(adapter==null) setStatus("Bluetooth не поддерживается");
        else if(!adapter.isEnabled()) setStatus("Включи Bluetooth магнитолы");
    }

    private TextView makeText(String value,int size,int color) {
        TextView t=new TextView(this); t.setText(value); t.setTextSize(size); t.setTextColor(color); t.setPadding(8,10,8,10); return t;
    }
    private void setStatus(String s) { status.setText("Статус: "+s); }
    private void append(String s) { log.append("\\n"+s); }
    private void chooseDevice() {
        if(adapter==null || !adapter.isEnabled()) { setStatus("Bluetooth выключен или недоступен"); return; }
        try {
            Set<BluetoothDevice> bonded=adapter.getBondedDevices();
            ArrayList<BluetoothDevice> devices=new ArrayList<>(bonded);
            if(devices.isEmpty()) { setStatus("Нет сопряжённых устройств"); return; }
            String[] names=new String[devices.size()];
            for(int i=0;i<devices.size();i++) names[i]=devices.get(i).getName()+"\\n"+devices.get(i).getAddress();
            new AlertDialog.Builder(this).setTitle("Выбери адаптер ELM327").setItems(names,(dialog,which)->connect(devices.get(which))).setNegativeButton("Отмена",null).show();
        } catch(SecurityException e) { setStatus("Нет разрешения Bluetooth"); }
    }
    private void connect(BluetoothDevice device) {
        disconnect();
        setStatus("Подключение к "+device.getName()+"…");
        append("CONNECT "+device.getName()+" "+device.getAddress());
        worker=new Thread(() -> {
            try {
                BluetoothSocket s=device.createRfcommSocketToServiceRecord(SPP);
                s.connect(); socket=s; running=true;
                handler.post(() -> { setStatus("Соединение установлено: "+device.getName()); connectButton.setText("ОТКЛЮЧИТЬ"); connectButton.setOnClickListener(v -> disconnect()); append("SPP CONNECTED"); });
                initializeElm(s.getInputStream(),s.getOutputStream());
            } catch(Exception e) {
                handler.post(() -> { setStatus("Ошибка соединения: "+e.getMessage()); append("ERROR: "+e); });
                closeSocket();
            }
        });
        worker.start();
    }

    private void initializeElm(InputStream in, OutputStream out) throws Exception {
        String[] basic={"ATZ","ATI","ATE0","ATL0","ATS0","ATH0"};
        for(String cmd:basic) {
            if(!running) return;
            String response=sendElm(cmd,in,out,5000);
            if(cmd.equals("ATI")) handler.post(() -> append("Идентификатор адаптера: "+clean(response)));
        }
        handler.post(() -> append("Начинаю настройку профиля Январь 5.1.1…"));

        // OpenDiag's published ELM profile for January 5.1.1:
        // ISO 14230 fast init, 10.4 kbaud, ECU header 8110F1, short timeout.
        String[][] profile={
            {"ATSP5","Выбор ISO 14230-4 KWP fast init"},
            {"ATIB10","Скорость 10.4 кбод"},
            {"ATSH8110F1","Адресация ЭБУ Январь"},
            {"ATST10","Тайм-аут ответа"},
            {"ATSW00","Параметр ожидания"}
        };
        boolean configured=true;
        for(String[] step:profile) {
            if(!running) return;
            String response=sendElm(step[0],in,out,3500);
            String normalized=response.toUpperCase();
            if(normalized.contains("ERROR") || normalized.contains("UNABLE") || normalized.contains("?")) {
                configured=false;
                handler.post(() -> append("ПРОВЕРЬ: "+step[0]+" → "+clean(response)));
            } else {
                handler.post(() -> append("OK: "+step[1]+" ["+step[0]+"]"));
            }
        }
        if(configured) {
            handler.post(() -> append("Проверка связи с ЭБУ: запрос 2101…"));
            String ecuResponse=sendElm("2101",in,out,5000);
            String normalized=ecuResponse.toUpperCase(java.util.Locale.US).replace(" ","");
            boolean ecuAnswered=normalized.contains("6101") && !normalized.contains("NODATA");
            handler.post(() -> {
                if(ecuAnswered) {
                    setStatus("Получен ответ ЭБУ на запрос 2101");
                    readButton.setEnabled(true);
                    append("ЭБУ ответил. Сохрани журнал TX/RX для расшифровки.");
                } else {
                    setStatus("ELM настроен, ответ ЭБУ не подтверждён");
                    append("2101 не дал подтверждённого ответа. Не считаем связь установленной.");
                }
            });
        } else {
            handler.post(() -> {
                setStatus("Адаптер не принял одну или несколько настроек");
                append("Связь с ЭБУ не подтверждена. Смотри строки ПРОВЕРЬ выше.");
            });
        }
    }

    private void requestEcuData() {
        if(!running || socket==null) { append("Сначала подключи ELM327"); return; }
        if(worker!=null && worker.isAlive()) { append("Опрос уже выполняется"); return; }
        worker=new Thread(() -> {
            try {
                String response=sendElm("2101",socket.getInputStream(),socket.getOutputStream(),5000);
                String normalized=response.toUpperCase(java.util.Locale.US).replace(" ","");
                if(normalized.contains("6101")) handler.post(() -> setStatus("ЭБУ отвечает на 2101"));
                else handler.post(() -> setStatus("Ответ ЭБУ не подтверждён"));
            } catch(Exception e) {
                handler.post(() -> { setStatus("Ошибка опроса: "+e.getMessage()); append("ERROR: "+e); });
            }
        });
        worker.start();
    }

    private synchronized String sendElm(String cmd,InputStream in,OutputStream out,long timeout) throws Exception {
        out.write((cmd+"\r").getBytes("US-ASCII")); out.flush();
        handler.post(() -> append("TX: "+cmd));
        String response=readResponse(in,timeout);
        String result=response.isEmpty()?"(нет ответа)":clean(response);
        handler.post(() -> append("RX: "+result));
        return response;
    }
    private String clean(String value) {
        return value.replace("\r"," ").replace("\n"," ").replace(">","").trim();
    }
    private String readResponse(InputStream in,long timeout) throws Exception {
        StringBuilder b=new StringBuilder(); long end=System.currentTimeMillis()+timeout;
        while(System.currentTimeMillis()<end && running) {
            if(in.available()>0) { int ch=in.read(); if(ch<0) break; if(ch=='>') break; b.append((char)ch); }
            else Thread.sleep(20);
        }
        return b.toString();
    }
    private void disconnect() {
        running=false; closeSocket();
        if(readButton!=null) readButton.setEnabled(false);
        if(status!=null) setStatus("Отключено");
        if(connectButton!=null) { connectButton.setText("ВЫБРАТЬ ELM327"); connectButton.setOnClickListener(v -> chooseDevice()); }
        if(log!=null) append("DISCONNECTED");
    }
    private void closeSocket() { try { if(socket!=null) socket.close(); } catch(Exception ignored) {} socket=null; }
    @Override protected void onDestroy() { disconnect(); super.onDestroy(); }
}
