package eu.faircode.finegeotag;

import android.content.BroadcastReceiver;
import android.content.Context;
import android.content.Intent;
import android.content.SharedPreferences;
import android.database.Cursor;
import android.preference.PreferenceManager;
import android.provider.MediaStore;
import android.util.Log;

public class NewPictureReceiver extends BroadcastReceiver {
    private static final String TAG = "FineGeotag.Receiver";

    @Override
    public void onReceive(Context context, Intent intent) {
        Log.w(TAG, "Received " + intent);
        SharedPreferences prefs = PreferenceManager.getDefaultSharedPreferences(context);

        // Check if enabled
        if (!prefs.getBoolean(ActivitySettings.PREF_ENABLED, ActivitySettings.DEFAULT_ENABLED)) {
            Log.w(TAG, "Disabled");
            return;
        }

        // Get image file name
        Cursor cursor = null;
        String image_filename = null;
        try {
            cursor = context.getContentResolver().query(intent.getData(), new String[]{MediaStore.Images.Media.DATA}, null, null, null);
            if (!cursor.moveToFirst()) {
                Log.w(TAG, "No content");
                return;
            }
            image_filename = cursor.getString(cursor.getColumnIndex(MediaStore.Images.Media.DATA));
            Log.w(TAG, "Image=" + image_filename);
        } finally {
            if (cursor != null)
                cursor.close();
        }

        LocationService.startLocating(image_filename, context);
    }
}
