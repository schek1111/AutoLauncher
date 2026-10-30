package com.autolauncher;

import android.Manifest;
import android.app.Activity;
import android.content.Intent;
import android.content.pm.PackageManager;
import android.location.Location;
import android.location.LocationListener;
import android.location.LocationManager;
import android.os.Bundle;
import android.provider.Settings;

public class MainActivity extends Activity {
    private DashboardView dashboard;
    private LocationManager locationManager;

    @Override public void onCreate(Bundle state) {
        super.onCreate(state);
        getWindow().getDecorView().setSystemUiVisibility(
                 View.SYSTEM_UI_FLAG_FULLSCREEN
                | View.SYSTEM_UI_FLAG_HIDE_NAVIGATION
                | View.SYSTEM_UI_FLAG_IMMERSIVE_STICKY
                | View.SYSTEM_UI_FLAG_LAYOUT_STABLE
                | View.SYSTEM_UI_FLAG_LAYOUT_HIDE_NAVIGATION
                | View.SYSTEM_UI_FLAG_LAYOUT_FULLSCREEN);
        dashboard = new DashboardView(this);
        dashboard.setAppLauncher(this::launchPackage);
        dashboard.setAppDrawer(this::showAppDrawer);
        setContentView(dashboard);
        locationManager = (LocationManager) getSystemService(LOCATION_SERVICE);
        requestGps();
    }

    private void requestGps() {
        if (checkSelfPermission(Manifest.permission.ACCESS_FINE_LOCATION) != PackageManager.PERMISSION_GRANTED) {
            requestPermissions(new String[]{Manifest.permission.ACCESS_FINE_LOCATION, Manifest.permission.ACCESS_COARSE_LOCATION}, 10);
            return;
        }
        try {
            locationManager.requestLocationUpdates(LocationManager.GPS_PROVIDER, 1000, 0,
                    new LocationListener() {
                        @Override public void onLocationChanged(Location location) {
                            dashboard.setGps(location.getSpeed() * 3.6f, location.hasAccuracy() ? location.getAccuracy() : -1);
                        }
                        @Override public void onProviderEnabled(String provider) {}
                        @Override public void onProviderDisabled(String provider) { dashboard.setGpsUnavailable(); }
                        @Override public void onStatusChanged(String provider, int status, Bundle extras) {}
                    });
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

    private void showAppDrawer() {
        java.util.ArrayList<String> labels = new java.util.ArrayList<>();
        java.util.ArrayList<String> packages = new java.util.ArrayList<>();
        android.content.Intent query = new android.content.Intent(Intent.ACTION_MAIN, null);
        query.addCategory(Intent.CATEGORY_LAUNCHER);
        for (android.content.pm.ResolveInfo info : getPackageManager().queryIntentActivities(query, 0)) {
            String pkg = info.activityInfo.packageName;
            if (pkg.equals(getPackageName())) continue;
            labels.add(info.loadLabel(getPackageManager()).toString());
            packages.add(pkg);
        }
        String[] items = labels.toArray(new String[0]);
        new android.app.AlertDialog.Builder(this)
                .setTitle("Все приложения")
                .setItems(items, (dialog, which) -> launchPackage(packages.get(which)))
                .setNegativeButton("Закрыть", null).show();
    }

    @Override protected void onDestroy() {
        super.onDestroy();
        if (locationManager != null) locationManager.removeUpdates((LocationListener) null);
    }
}
