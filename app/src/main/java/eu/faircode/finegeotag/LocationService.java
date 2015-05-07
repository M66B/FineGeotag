package eu.faircode.finegeotag;

import android.app.IntentService;
import android.app.PendingIntent;
import android.content.Context;
import android.content.Intent;
import android.location.Location;
import android.location.LocationManager;
import android.net.Uri;
import android.os.Handler;
import android.os.Looper;
import android.util.Log;
import android.widget.Toast;

import java.io.File;
import java.io.IOException;

public class LocationService extends IntentService {
    private static final String TAG = "FineGeotag.Service";

    public static final String ACTION_LOCATION = "Location";
    public static final String ACTION_ALARM = "Alarm";

    public LocationService() {
        super(TAG);
    }

    @Override
    protected void onHandleIntent(Intent intent) {
        Log.w(TAG, "Intent=" + intent);

        if (ACTION_LOCATION.equals(intent.getAction())) {
            String image_filename = intent.getData().getPath();
            Location location = (Location) intent.getExtras().get(LocationManager.KEY_LOCATION_CHANGED);
            Log.w(TAG, "Location=" + location + " image=" + image_filename);

            if (location != null)
                try {
                    ExifInterfaceEx exif = new ExifInterfaceEx(image_filename);
                    exif.setLocation(location);
                    exif.saveAttributes();
                    Log.w(TAG, "Exif updated image=" + image_filename);
                    notify(getString(R.string.msg_geotagged, new File(image_filename).getName()));
                } catch (IOException ex) {
                    Log.e(TAG, ex.toString() + "\n" + Log.getStackTraceString(ex));
                }

        } else if (ACTION_ALARM.equals(intent.getAction())) {
            String image_filename = intent.getData().getPath();
            Log.w(TAG, "Alarm image" + image_filename);

            LocationManager lm = (LocationManager) getSystemService(Context.LOCATION_SERVICE);
            Intent locationIntent = new Intent(this, LocationService.class);
            locationIntent.setAction(LocationService.ACTION_LOCATION);
            locationIntent.setData(Uri.fromFile(new File(image_filename)));
            PendingIntent pi = PendingIntent.getService(this, 0, locationIntent, PendingIntent.FLAG_UPDATE_CURRENT);
            lm.removeUpdates(pi);
            Log.w(TAG, "Timeout image=" + image_filename);
            notify(getString(R.string.msg_failed, new File(image_filename).getName()));
        }
    }

    private void notify(final String text) {
        new Handler(Looper.getMainLooper()).post(new Runnable() {
            @Override
            public void run() {
                Toast.makeText(getApplicationContext(), text, Toast.LENGTH_LONG).show();
            }
        });
    }
}
