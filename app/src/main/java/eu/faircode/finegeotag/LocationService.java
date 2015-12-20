package eu.faircode.finegeotag;

import android.app.AlarmManager;
import android.app.IntentService;
import android.app.PendingIntent;
import android.content.Context;
import android.content.Intent;
import android.content.SharedPreferences;
import android.location.Address;
import android.location.Geocoder;
import android.location.Location;
import android.location.LocationManager;
import android.net.Uri;
import android.os.Handler;
import android.os.Looper;
import android.os.SystemClock;
import android.preference.PreferenceManager;
import android.text.TextUtils;
import android.util.Log;
import android.view.Gravity;
import android.view.LayoutInflater;
import android.view.View;
import android.widget.ImageView;
import android.widget.TextView;
import android.widget.Toast;

import com.google.gson.Gson;
import com.google.gson.GsonBuilder;
import com.google.gson.JsonDeserializationContext;
import com.google.gson.JsonDeserializer;
import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import com.google.gson.JsonParseException;
import com.google.gson.JsonSerializationContext;
import com.google.gson.JsonSerializer;

import java.io.File;
import java.io.IOException;
import java.io.InputStream;
import java.lang.reflect.Type;
import java.nio.ByteBuffer;
import java.nio.ByteOrder;
import java.util.ArrayList;
import java.util.List;

public class LocationService extends IntentService {
    private static final String TAG = "FineGeotag.Service";

    public static final String ACTION_LOCATION_FINE = "LocationFine";
    public static final String ACTION_LOCATION_COARSE = "LocationCoarse";
    public static final String ACTION_TIMEOUT = "TimeOut";

    private static final int LOCATION_MIN_TIME = 1000; // milliseconds
    private static final int LOCATION_MIN_DISTANCE = 1; // meters
    private static final String PREFIX_LOCATION = "location_";
    private static final String ACTION_GEOTAGGED = "eu.faircode.action.GEOTAGGED";

    private static int mEGM96Pointer = -1;
    private static int mEGM96Offset;

    public LocationService() {
        super(TAG);
    }

    @Override
    protected void onHandleIntent(Intent intent) {
        Log.w(TAG, "Intent=" + intent);
        String image_filename = intent.getData().getPath();
        SharedPreferences prefs = PreferenceManager.getDefaultSharedPreferences(this);

        if (ACTION_LOCATION_FINE.equals(intent.getAction()) ||
                ACTION_LOCATION_COARSE.equals(intent.getAction())) {
            // Process location update
            Location location = (Location) intent.getExtras().get(LocationManager.KEY_LOCATION_CHANGED);
            Log.w(TAG, "Update location=" + location + " image=" + image_filename);
            if (location == null ||
                    (location.getLatitude() == 0.0 && location.getLongitude() == 0.0))
                return;

            // Correct altitude
            if (LocationManager.GPS_PROVIDER.equals(location.getProvider()))
                try {
                    double offset = getEGM96Offset(location, this);
                    Log.w(TAG, "Offset=" + offset);
                    location.setAltitude(location.getAltitude() - offset);
                    Log.w(TAG, "Corrected location=" + location);
                } catch (IOException ex) {
                    Log.w(TAG, ex.toString() + "\n" + Log.getStackTraceString(ex));
                }

            // Get location preferences
            boolean pref_altitude = prefs.getBoolean(ActivitySettings.PREF_ALTITUDE, ActivitySettings.DEFAULT_ALTITUDE);
            float pref_accuracy = Float.parseFloat(prefs.getString(ActivitySettings.PREF_ACCURACY, ActivitySettings.DEFAULT_ACCURACY));
            Log.w(TAG, "Prefer altitude=" + pref_altitude + " accuracy=" + pref_accuracy);

            // Persist better location
            Location bestLocation = LocationDeserializer.deserialize(prefs.getString(PREFIX_LOCATION + image_filename, null));
            if (isBetterLocation(bestLocation, location)) {
                Log.w(TAG, "Better location=" + location + " image=" + image_filename);
                prefs.edit().putString(PREFIX_LOCATION + image_filename, LocationSerializer.serialize(location)).apply();
            }

            // Check altitude
            if (!location.hasAltitude() && pref_altitude) {
                Log.w(TAG, "No altitude image=" + image_filename);
                return;
            }

            // Check accuracy
            if (!location.hasAccuracy() || location.getAccuracy() > pref_accuracy) {
                Log.w(TAG, "Inaccurate image=" + image_filename);
                return;
            }

            stopLocating(image_filename);

            // Process location
            handleLocation(image_filename, location);

        } else if (ACTION_TIMEOUT.equals(intent.getAction())) {
            // Process location time-out
            Log.w(TAG, "Timeout image=" + image_filename);

            // Process best location
            Location bestLocation = LocationDeserializer.deserialize(prefs.getString(PREFIX_LOCATION + image_filename, null));
            if (bestLocation == null) {
                int known = Integer.parseInt(prefs.getString(ActivitySettings.PREF_KNOWN, ActivitySettings.DEFAULT_KNOWN));
                LocationManager lm = (LocationManager) getSystemService(Context.LOCATION_SERVICE);
                for (String provider : lm.getProviders(false)) {
                    Location lastKnownLocation = lm.getLastKnownLocation(provider);
                    Log.w(TAG, "Last known location=" + lastKnownLocation + " provider=" + provider);
                    if (lastKnownLocation != null &&
                            lastKnownLocation.getTime() > System.currentTimeMillis() - known * 60 * 1000 &&
                            isBetterLocation(bestLocation, lastKnownLocation))
                        bestLocation = lastKnownLocation;
                }
            }

            stopLocating(image_filename);

            Log.w(TAG, "Best location=" + bestLocation + " image=" + image_filename);
            if (bestLocation != null)
                handleLocation(image_filename, bestLocation);
        }
    }

    public static void startLocating(String image_filename, Context context) {
        LocationManager lm = (LocationManager) context.getSystemService(Context.LOCATION_SERVICE);
        SharedPreferences prefs = PreferenceManager.getDefaultSharedPreferences(context);

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
        alarmIntent.setAction(LocationService.ACTION_TIMEOUT);
        alarmIntent.setData(Uri.fromFile(new File(image_filename)));
        PendingIntent pia = PendingIntent.getService(context, 0, alarmIntent, PendingIntent.FLAG_UPDATE_CURRENT);
        AlarmManager am = (AlarmManager) context.getSystemService(Context.ALARM_SERVICE);
        am.set(AlarmManager.ELAPSED_REALTIME_WAKEUP, SystemClock.elapsedRealtime() + timeout * 1000, pia);
        Log.w(TAG, "Set timeout=" + timeout + "s image=" + image_filename);
    }

    private static double getEGM96Offset(Location location, Context context) throws IOException {
        InputStream is = null;
        try {
            double lat = location.getLatitude();
            double lon = location.getLongitude();

            int y = (int) Math.floor((90 - lat) * 4);
            int x = (int) Math.floor((lon >= 0 ? lon : lon + 360) * 4);
            int p = ((y * 1440) + x) * 2;
            int o;

            if (mEGM96Pointer >= 0 && p == mEGM96Pointer)
                o = mEGM96Offset;
            else {
                is = context.getAssets().open("WW15MGH.DAC");
                is.skip(p);

                ByteBuffer bb = ByteBuffer.allocate(2);
                bb.order(ByteOrder.BIG_ENDIAN);
                bb.put((byte) is.read());
                bb.put((byte) is.read());
                o = bb.getShort(0);

                mEGM96Pointer = p;
                mEGM96Offset = o;
            }

            return o / 100d;
        } finally {
            if (is != null) {
                try {
                    is.close();
                } catch (IOException ignored) {
                }
            }
        }
    }

    private boolean isBetterLocation(Location prev, Location current) {
        SharedPreferences prefs = PreferenceManager.getDefaultSharedPreferences(this);
        boolean pref_altitude = prefs.getBoolean(ActivitySettings.PREF_ALTITUDE, ActivitySettings.DEFAULT_ALTITUDE);
        return (prev == null ||
                ((!pref_altitude || !prev.hasAltitude() || current.hasAltitude()) &&
                        (current.hasAccuracy() ? current.getAccuracy() : Float.MAX_VALUE) <
                                (prev.hasAccuracy() ? prev.getAccuracy() : Float.MAX_VALUE)));
    }

    private void handleLocation(String image_filename, Location location) {
        try {
            SharedPreferences prefs = PreferenceManager.getDefaultSharedPreferences(this);

            // Check if image still exists
            if (!new File(image_filename).exists()) {
                Log.w(TAG, "File deleted image=" + image_filename);
                return;
            }

            // Write Exif
            ExifInterfaceEx exif = new ExifInterfaceEx(image_filename);
            exif.setLocation(location);
            exif.saveAttributes();
            Log.w(TAG, "Exif updated location=" + location + " image=" + image_filename);

            // Reverse geocode
            if (prefs.getBoolean(ActivitySettings.PREF_TOAST, ActivitySettings.DEFAULT_TOAST)) {
                String address = TextUtils.join("\n", reverseGeocode(location, this));
                Log.w(TAG, "Address=" + address + " image=" + image_filename);
                address = getString(R.string.msg_geotagged) + (address == null ? "" : "\n" + address);
                notify(image_filename, address);
            }

            // Broadcast geotagged intent
            Intent intent = new Intent(ACTION_GEOTAGGED);
            intent.setData(Uri.fromFile(new File(image_filename)));
            intent.putExtra(LocationManager.KEY_LOCATION_CHANGED, location);
            Log.w(TAG, "Broadcasting " + intent);
            sendBroadcast(intent);
        } catch (IOException ex) {
            Log.e(TAG, ex.toString() + "\n" + Log.getStackTraceString(ex));
        } catch (Throwable ex) {
            Log.e(TAG, ex.toString() + "\n" + Log.getStackTraceString(ex));
        }
    }

    private void stopLocating(String image_filename) {
        SharedPreferences prefs = PreferenceManager.getDefaultSharedPreferences(this);
        LocationManager lm = (LocationManager) getSystemService(Context.LOCATION_SERVICE);

        // Cancel coarse location updates
        {
            Intent locationIntent = new Intent(this, LocationService.class);
            locationIntent.setAction(LocationService.ACTION_LOCATION_COARSE);
            locationIntent.setData(Uri.fromFile(new File(image_filename)));
            PendingIntent pi = PendingIntent.getService(this, 0, locationIntent, PendingIntent.FLAG_UPDATE_CURRENT);
            lm.removeUpdates(pi);
        }

        // Cancel fine location updates
        {
            Intent locationIntent = new Intent(this, LocationService.class);
            locationIntent.setAction(LocationService.ACTION_LOCATION_FINE);
            locationIntent.setData(Uri.fromFile(new File(image_filename)));
            PendingIntent pi = PendingIntent.getService(this, 0, locationIntent, PendingIntent.FLAG_UPDATE_CURRENT);
            lm.removeUpdates(pi);
        }

        // Cancel alarm
        {
            Intent alarmIntent = new Intent(this, LocationService.class);
            alarmIntent.setAction(LocationService.ACTION_TIMEOUT);
            alarmIntent.setData(Uri.fromFile(new File(image_filename)));
            PendingIntent pi = PendingIntent.getService(this, 0, alarmIntent, PendingIntent.FLAG_UPDATE_CURRENT);
            AlarmManager am = (AlarmManager) getSystemService(Context.ALARM_SERVICE);
            am.cancel(pi);
        }

        prefs.edit().remove(PREFIX_LOCATION + image_filename).apply();
    }

    private static List<String> reverseGeocode(Location location, Context context) {
        List<String> listLine = new ArrayList<>();
        if (location != null && Geocoder.isPresent())
            try {
                Geocoder geocoder = new Geocoder(context);
                List<Address> listPlace = geocoder.getFromLocation(location.getLatitude(), location.getLongitude(), 1);
                if (listPlace != null && listPlace.size() > 0) {
                    for (int l = 0; l < listPlace.get(0).getMaxAddressLineIndex(); l++)
                        listLine.add(listPlace.get(0).getAddressLine(l));
                }
            } catch (IOException ignored) {
            }
        return listLine;
    }

    private void notify(final String image_filename, final String text) {
        new Handler(Looper.getMainLooper()).post(new Runnable() {
            @Override
            public void run() {
                LayoutInflater inflater = LayoutInflater.from(LocationService.this);
                View layout = inflater.inflate(R.layout.geotagged, null);

                ImageView iv = (ImageView) layout.findViewById(R.id.image);
                iv.setImageURI(Uri.fromFile(new File(image_filename)));
                TextView tv = (TextView) layout.findViewById(R.id.text);
                tv.setText(text);

                Toast toast = new Toast(getApplicationContext());
                toast.setGravity(Gravity.CENTER_VERTICAL, 0, 0);
                toast.setDuration(Toast.LENGTH_LONG);
                toast.setView(layout);
                toast.show();
            }
        });
    }

    // Serialization

    private static class LocationSerializer implements JsonSerializer<Location> {
        public JsonElement serialize(Location src, Type typeOfSrc, JsonSerializationContext context) {
            JsonObject jObject = new JsonObject();

            jObject.addProperty("Provider", src.getProvider());
            jObject.addProperty("Time", src.getTime());
            jObject.addProperty("Latitude", src.getLatitude());
            jObject.addProperty("Longitude", src.getLongitude());

            if (src.hasAltitude())
                jObject.addProperty("Altitude", src.getAltitude());

            if (src.hasSpeed())
                jObject.addProperty("Speed", src.getSpeed());

            if (src.hasAccuracy())
                jObject.addProperty("Accuracy", src.getAccuracy());

            if (src.hasBearing())
                jObject.addProperty("Bearing", src.getBearing());

            return jObject;
        }

        public static String serialize(Location location) {
            GsonBuilder builder = new GsonBuilder();
            builder.registerTypeAdapter(Location.class, new LocationSerializer());
            Gson gson = builder.create();
            String json = gson.toJson(location);
            return json;
        }
    }

    private static class LocationDeserializer implements JsonDeserializer<Location> {
        public Location deserialize(JsonElement json, Type typeOfT, JsonDeserializationContext context)
                throws JsonParseException {
            JsonObject jObject = (JsonObject) json;
            Location location = new Location(jObject.get("Provider").getAsString());

            location.setTime(jObject.get("Time").getAsLong());
            location.setLatitude(jObject.get("Latitude").getAsDouble());
            location.setLongitude(jObject.get("Longitude").getAsDouble());

            if (jObject.has("Altitude"))
                location.setAltitude(jObject.get("Altitude").getAsDouble());

            if (jObject.has("Speed"))
                location.setSpeed(jObject.get("Speed").getAsFloat());

            if (jObject.has("Bearing"))
                location.setBearing(jObject.get("Bearing").getAsFloat());

            if (jObject.has("Accuracy"))
                location.setAccuracy(jObject.get("Accuracy").getAsFloat());

            return location;
        }

        public static Location deserialize(String json) {
            GsonBuilder builder = new GsonBuilder();
            builder.registerTypeAdapter(Location.class, new LocationDeserializer());
            Gson gson = builder.create();
            Location location = gson.fromJson(json, Location.class);
            return location;
        }
    }
}
