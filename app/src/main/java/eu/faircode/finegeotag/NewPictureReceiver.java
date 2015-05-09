package eu.faircode.finegeotag;

import android.app.AlarmManager;
import android.app.PendingIntent;
import android.content.BroadcastReceiver;
import android.content.Context;
import android.content.Intent;
import android.content.SharedPreferences;
import android.database.Cursor;
import android.location.LocationManager;
import android.net.Uri;
import android.os.SystemClock;
import android.preference.PreferenceManager;
import android.util.Log;

import java.io.File;

public class NewPictureReceiver extends BroadcastReceiver {
    private static final String TAG = "FineGeotag.Receiver";
    private static final int LOCATION_MIN_TIME = 1000; // milliseconds
    private static final int LOCATION_MIN_DISTANCE = 1; // meters

    @Override
    public void onReceive(Context context, Intent intent) {
        Log.w(TAG, "Received " + intent);
        SharedPreferences prefs = PreferenceManager.getDefaultSharedPreferences(context);
        LocationManager lm = (LocationManager) context.getSystemService(Context.LOCATION_SERVICE);

        // Check if enabled
        if (!prefs.getBoolean(ActivitySettings.PREF_ENABLED, ActivitySettings.DEFAULT_ENABLED)) {
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

        // Request coarse location
        if (lm.isProviderEnabled(LocationManager.NETWORK_PROVIDER)) {
            Intent locationIntent = new Intent(context, LocationService.class);
            locationIntent.setAction(LocationService.ACTION_LOCATION_COARSE);
            locationIntent.setData(Uri.fromFile(new File(image_filename)));
            PendingIntent pi = PendingIntent.getService(context, 0, locationIntent, PendingIntent.FLAG_UPDATE_CURRENT);
            lm.requestLocationUpdates(LocationManager.NETWORK_PROVIDER, LOCATION_MIN_TIME, LOCATION_MIN_DISTANCE, pi);
            Log.w(TAG, "Requested network locations image=" + image_filename);
        }

        // Request fine location
        if (lm.isProviderEnabled(LocationManager.GPS_PROVIDER)) {
            Intent locationIntent = new Intent(context, LocationService.class);
            locationIntent.setAction(LocationService.ACTION_LOCATION_FINE);
            locationIntent.setData(Uri.fromFile(new File(image_filename)));
            PendingIntent pi = PendingIntent.getService(context, 0, locationIntent, PendingIntent.FLAG_UPDATE_CURRENT);
            lm.requestLocationUpdates(LocationManager.GPS_PROVIDER, LOCATION_MIN_TIME, LOCATION_MIN_DISTANCE, pi);
            Log.w(TAG, "Requested GPS locations image=" + image_filename);
        }

        // Set location timeout
        int timeout = Integer.parseInt(prefs.getString(ActivitySettings.PREF_TIMEOUT, ActivitySettings.DEFAULT_TIMEOUT));
        if (!lm.isProviderEnabled(LocationManager.NETWORK_PROVIDER) && !lm.isProviderEnabled(LocationManager.GPS_PROVIDER))
            timeout = 1;
        Intent alarmIntent = new Intent(context, LocationService.class);
        alarmIntent.setAction(LocationService.ACTION_ALARM);
        alarmIntent.setData(Uri.fromFile(new File(image_filename)));
        PendingIntent pia = PendingIntent.getService(context, 0, alarmIntent, PendingIntent.FLAG_UPDATE_CURRENT);
        AlarmManager am = (AlarmManager) context.getSystemService(Context.ALARM_SERVICE);
        am.set(AlarmManager.ELAPSED_REALTIME_WAKEUP, SystemClock.elapsedRealtime() + timeout * 1000, pia);
        Log.w(TAG, "Set timeout=" + timeout + "s image=" + image_filename);
    }
}
