package com.autolauncher;

import android.Manifest;
import android.app.Activity;
import android.app.AlertDialog;
import android.content.Intent;
import android.content.pm.PackageManager;
import android.content.pm.ResolveInfo;
import android.location.Location;
import android.location.LocationListener;
import android.location.LocationManager;
import android.os.Bundle;
import android.view.View;
import java.util.ArrayList;
import java.util.Collections;
import java.util.Comparator;

public class MainActivity extends Activity {
    private DashboardView dashboard;
    private LocationManager locationManager;
    private LocationListener gpsListener;
    private static final String PREFS = "launcher_prefs";
    private static final String PINS = "pinned_apps";

    @Override public void onCreate(Bundle state) {
        super.onCreate(state);
        getWindow().getDecorView().setSystemUiVisibility(
                View.SYSTEM_UI_FLAG_FULLSCREEN | View.SYSTEM_UI_FLAG_HIDE_NAVIGATION
                | View.SYSTEM_UI_FLAG_IMMERSIVE_STICKY | View.SYSTEM_UI_FLAG_LAYOUT_STABLE
                | View.SYSTEM_UI_FLAG_LAYOUT_HIDE_NAVIGATION | View.SYSTEM_UI_FLAG_LAYOUT_FULLSCREEN);
        dashboard = new DashboardView(this);
        dashboard.setAppLauncher(this::launchPackage);
        dashboard.setAppDrawer(this::showAppDrawer);
        dashboard.setAppRemover(this::removePinnedApp);
        dashboard.setDiagnosticOpener(() -> startActivity(new Intent(this, DiagnosticActivity.class)));
        dashboard.setPinnedApps(loadPinnedApps());
        setContentView(dashboard);
        locationManager = (LocationManager) getSystemService(LOCATION_SERVICE);
        requestGps();
    }

    private ArrayList<DashboardView.AppShortcut> loadPinnedApps() {
        ArrayList<DashboardView.AppShortcut> result = new ArrayList<>();
        String saved = getSharedPreferences(PREFS, MODE_PRIVATE).getString(PINS, "");
        if (!saved.isEmpty()) {
            for (String item : saved.split("\\n")) {
                int sep = item.indexOf('|');
                if (sep > 0 && sep < item.length() - 1)
                    result.add(new DashboardView.AppShortcut(item.substring(0, sep), item.substring(sep + 1)));
            }
        }
        if (result.isEmpty()) {
            result.add(new DashboardView.AppShortcut("ru.yandex.yandexnavi", "НАВ"));
            result.add(new DashboardView.AppShortcut("com.maxmpz.audioplayer", "МУЗ"));
            result.add(new DashboardView.AppShortcut("com.android.dialer", "ТЕЛ"));
            result.add(new DashboardView.AppShortcut("com.android.settings", "SET"));
        }
        return result;
    }

    private void savePinnedApps() {
        StringBuilder value = new StringBuilder();
        for (DashboardView.AppShortcut app : dashboard.getPinnedApps()) {
            if (value.length() > 0) value.append('\n');
            value.append(app.packageName).append('|').append(app.label.replace("|", ""));
        }
        getSharedPreferences(PREFS, MODE_PRIVATE).edit().putString(PINS, value.toString()).apply();
    }

    private void requestGps() {
        if (checkSelfPermission(Manifest.permission.ACCESS_FINE_LOCATION) != PackageManager.PERMISSION_GRANTED) {
            requestPermissions(new String[]{Manifest.permission.ACCESS_FINE_LOCATION, Manifest.permission.ACCESS_COARSE_LOCATION}, 10);
            return;
        }
        try {
            gpsListener = new LocationListener() {
                @Override public void onLocationChanged(Location location) {
                    dashboard.setGps(location.getSpeed() * 3.6f, location.hasAccuracy() ? location.getAccuracy() : -1);
                }
                @Override public void onProviderEnabled(String provider) {}
                @Override public void onProviderDisabled(String provider) { dashboard.setGpsUnavailable(); }
                @Override public void onStatusChanged(String provider, int status, Bundle extras) {}
            };
            locationManager.requestLocationUpdates(LocationManager.GPS_PROVIDER, 1000, 0, gpsListener);
        } catch (SecurityException ignored) { }
    }

    @Override public void onRequestPermissionsResult(int requestCode, String[] permissions, int[] results) {
        super.onRequestPermissionsResult(requestCode, permissions, results);
        if (requestCode == 10 && results.length > 0 && results[0] == PackageManager.PERMISSION_GRANTED) requestGps();
    }

    private void launchPackage(String packageName) {
        try {
            Intent intent = getPackageManager().getLaunchIntentForPackage(packageName);
            if (intent != null) startActivity(intent);
        } catch (Exception ignored) { }
    }

    private ArrayList<DashboardView.AppShortcut> installedApps() {
        ArrayList<DashboardView.AppShortcut> apps = new ArrayList<>();
        Intent query = new Intent(Intent.ACTION_MAIN, null);
        query.addCategory(Intent.CATEGORY_LAUNCHER);
        for (ResolveInfo info : getPackageManager().queryIntentActivities(query, 0)) {
            String pkg = info.activityInfo.packageName;
            if (!pkg.equals(getPackageName()))
                apps.add(new DashboardView.AppShortcut(pkg, info.loadLabel(getPackageManager()).toString()));
        }
        Collections.sort(apps, Comparator.comparing(a -> a.label.toLowerCase()));
        return apps;
    }

    private void showAppDrawer() {
        ArrayList<DashboardView.AppShortcut> apps = installedApps();
        ArrayList<String> labels = new ArrayList<>();
        for (DashboardView.AppShortcut app : apps)
            labels.add(dashboard.hasPinnedApp(app.packageName) ? "✓  " + app.label : "+  " + app.label);
        new AlertDialog.Builder(this)
                .setTitle("Добавить приложение на панель")
                .setItems(labels.toArray(new String[0]), (dialog, which) -> {
                    DashboardView.AppShortcut selected = apps.get(which);
                    if (!dashboard.hasPinnedApp(selected.packageName)) {
                        dashboard.addPinnedApp(selected);
                        savePinnedApps();
                    } else {
                        launchPackage(selected.packageName);
                    }
                })
                .setNegativeButton("Закрыть", null).show();
    }

    private void removePinnedApp(String packageName, String label) {
        new AlertDialog.Builder(this)
                .setTitle("Убрать ярлык?")
                .setMessage(label)
                .setPositiveButton("Убрать", (d, w) -> {
                    dashboard.removePinnedApp(packageName);
                    savePinnedApps();
                })
                .setNegativeButton("Отмена", null).show();
    }

    @Override protected void onDestroy() {
        if (locationManager != null && gpsListener != null) locationManager.removeUpdates(gpsListener);
        super.onDestroy();
    }
}