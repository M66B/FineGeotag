package eu.faircode.finegeotag;

import android.app.AlarmManager;
import android.app.PendingIntent;
import android.content.BroadcastReceiver;
import android.content.Context;
import android.content.Intent;
import android.content.SharedPreferences;
import android.database.Cursor;
import android.location.Location;
import android.location.LocationManager;
import android.net.Uri;
import android.os.SystemClock;
import android.preference.PreferenceManager;
import android.util.Log;

import java.io.File;
import java.io.IOException;

public class NewPictureReceiver extends BroadcastReceiver {
    private static final String TAG = "FineGeotag.Receiver";

    @Override
    public void onReceive(Context context, Intent intent) {
        Log.w(TAG, "Received " + intent);

        // Check if enabled
        SharedPreferences prefs = PreferenceManager.getDefaultSharedPreferences(context);
        if (!prefs.getBoolean(ActivitySettings.PREF_ENABLED, true)) {
            Log.w(TAG, "Disabled");
            return;
        }

        // Get image file name
        Cursor cursor = null;
        String image_filename = null;
        try {
            cursor = context.getContentResolver().query(intent.getData(), null, null, null, null);
            if (!cursor.moveToFirst()) {
                Log.w(TAG, "No content");
                return;
            }
            image_filename = cursor.getString(cursor.getColumnIndex("_data"));
            Log.w(TAG, "Image=" + image_filename);
        } finally {
            if (cursor != null)
                cursor.close();
        }

        // Check provider
        String provider = prefs.getString(ActivitySettings.PREF_PROVIDER, LocationManager.GPS_PROVIDER);
        LocationManager lm = (LocationManager) context.getSystemService(Context.LOCATION_SERVICE);
        if (!lm.isProviderEnabled(provider)) {
            Log.w(TAG, "Provider " + provider + " not enabled image=" + image_filename);

            // Get best last known location
            Location bestLocation = null;
            for (String fallback : lm.getProviders(true)) {
                Location location = lm.getLastKnownLocation(fallback);
                Log.w(TAG, "Last known location=" + location + " fallback=" + fallback + " image=" + image_filename);
                if (location != null && (bestLocation == null || location.getAccuracy() < bestLocation.getAccuracy()))
                    bestLocation = location;
            }

            // Process best last known location
            if (bestLocation != null) {
                Log.w(TAG, "Best last known location=" + bestLocation + " image=" + image_filename);
                try {
                    // Write Exif
                    ExifInterfaceEx exif = new ExifInterfaceEx(image_filename);
                    exif.setLocation(bestLocation);
                    exif.saveAttributes();
                    Log.w(TAG, "Exif updated image=" + image_filename);
                } catch (IOException ex) {
                    Log.e(TAG, ex.toString() + "\n" + Log.getStackTraceString(ex));
                }
            }

            return;
        }

        // Request location
        Intent locationIntent = new Intent(context, LocationService.class);
        locationIntent.setAction(LocationService.ACTION_LOCATION);
        locationIntent.setData(Uri.fromFile(new File(image_filename)));
        PendingIntent pil = PendingIntent.getService(context, 0, locationIntent, PendingIntent.FLAG_UPDATE_CURRENT);
        lm.requestLocationUpdates(provider, 1000, 1, pil);
        Log.w(TAG, "Requested locations provider=" + provider + " image" + image_filename);

        // Set timeout
        int timeout = Integer.parseInt(prefs.getString(ActivitySettings.PREF_TIMEOUT, "60"));
        Intent alarmIntent = new Intent(context, LocationService.class);
        alarmIntent.setAction(LocationService.ACTION_ALARM);
        alarmIntent.setData(Uri.fromFile(new File(image_filename)));
        PendingIntent pia = PendingIntent.getService(context, 0, alarmIntent, PendingIntent.FLAG_UPDATE_CURRENT);
        AlarmManager am = (AlarmManager) context.getSystemService(Context.ALARM_SERVICE);
        am.set(AlarmManager.ELAPSED_REALTIME_WAKEUP, SystemClock.elapsedRealtime() + timeout * 1000, pia);
        Log.w(TAG, "Set timeout=" + timeout + "s image=" + image_filename);
    }
}
