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
import android.preference.PreferenceManager;
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
import java.lang.reflect.Type;
import java.util.List;

public class LocationService extends IntentService {
    private static final String TAG = "FineGeotag.Service";

    public static final String ACTION_LOCATION_FINE = "LocationFine";
    public static final String ACTION_LOCATION_COARSE = "LocationCoarse";
    public static final String ACTION_ALARM = "Alarm";

    private static final String ACTION_GEOTAGGED = "eu.faircode.action.GEOTAGGED";

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
            if (location == null)
                return;

            // Get location preferences
            boolean pref_altitude = prefs.getBoolean(ActivitySettings.PREF_ALTITUDE, ActivitySettings.DEFAULT_ALTITUDE);
            float pref_accuracy = Float.parseFloat(prefs.getString(ActivitySettings.PREF_ACCURACY, ActivitySettings.DEFAULT_ACCURACY));
            Log.w(TAG, "Prefer altitude=" + pref_altitude + " accuracy=" + pref_accuracy);

            // Persist better location
            Location bestLocation = deserialize(prefs.getString(image_filename, null));
            if (isBetterLocation(bestLocation, location)) {
                Log.w(TAG, "Better location=" + location + " image=" + image_filename);
                prefs.edit().putString(image_filename, serialize(location)).apply();
            }

            // Check altitude
            if (!location.hasAltitude() && pref_altitude) {
                Log.w(TAG, "No altitude image=" + image_filename);
                return;
            }

            // Check accuracy
            if (location.getAccuracy() > pref_accuracy) {
                Log.w(TAG, "Inaccurate image=" + image_filename);
                return;
            }

            // Process location
            handleLocation(image_filename, location);

        } else if (ACTION_ALARM.equals(intent.getAction())) {
            // Process location time-out
            Log.w(TAG, "Timeout image=" + image_filename);

            // Process best location
            Location bestLocation = deserialize(prefs.getString(image_filename, null));
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

            Log.w(TAG, "Best location=" + bestLocation + " image=" + image_filename);
            handleLocation(image_filename, bestLocation);
        }
    }

    private boolean isBetterLocation(Location prev, Location current) {
        SharedPreferences prefs = PreferenceManager.getDefaultSharedPreferences(this);
        boolean pref_altitude = prefs.getBoolean(ActivitySettings.PREF_ALTITUDE, ActivitySettings.DEFAULT_ALTITUDE);
        return (prev == null ||
                ((!pref_altitude || !prev.hasAltitude() || current.hasAltitude()) &&
                        current.getAccuracy() < prev.getAccuracy()));
    }

    private void handleLocation(String image_filename, Location location) {
        try {
            // Stop locating
            cancelPendingIntents(image_filename);
            SharedPreferences prefs = PreferenceManager.getDefaultSharedPreferences(this);
            prefs.edit().remove(image_filename).apply();
            if (location == null)
                return;

            // Write Exif
            ExifInterfaceEx exif = new ExifInterfaceEx(image_filename);
            exif.setLocation(location);
            exif.saveAttributes();
            Log.w(TAG, "Exif updated location=" + location + " image=" + image_filename);

            // Reverse geocode
            if (prefs.getBoolean(ActivitySettings.PREF_TOAST, ActivitySettings.DEFAULT_TOAST)) {
                String address = reverseGeocode(location);
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
        }
    }

    private void cancelPendingIntents(String image_filename) {
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
            alarmIntent.setAction(LocationService.ACTION_ALARM);
            alarmIntent.setData(Uri.fromFile(new File(image_filename)));
            PendingIntent pi = PendingIntent.getService(this, 0, alarmIntent, PendingIntent.FLAG_UPDATE_CURRENT);
            AlarmManager am = (AlarmManager) getSystemService(Context.ALARM_SERVICE);
            am.cancel(pi);
        }
    }

    private String reverseGeocode(Location location) throws IOException {
        String address = null;
        if (Geocoder.isPresent()) {
            Geocoder geocoder = new Geocoder(this);
            List<Address> listPlace = geocoder.getFromLocation(location.getLatitude(), location.getLongitude(), 1);
            if (listPlace != null && listPlace.size() > 0) {
                StringBuilder sb = new StringBuilder();
                for (int l = 0; l < listPlace.get(0).getMaxAddressLineIndex(); l++) {
                    if (l != 0)
                        sb.append("\n");
                    sb.append(listPlace.get(0).getAddressLine(l));
                }
                address = sb.toString();
            }
        }
        return address;
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

    private class LocationSerializer implements JsonSerializer<Location> {
        public JsonElement serialize(Location src, Type typeOfSrc, JsonSerializationContext context) {
            JsonObject jObject = new JsonObject();

            jObject.addProperty("Provider", src.getProvider());

            jObject.addProperty("Latitude", src.getLatitude());
            jObject.addProperty("Longitude", src.getLongitude());

            if (src.hasAltitude())
                jObject.addProperty("Altitude", src.getAltitude());

            if (src.hasSpeed())
                jObject.addProperty("Speed", src.getSpeed());

            if (src.hasAccuracy())
                jObject.addProperty("Accuracy", src.getAccuracy());

            jObject.addProperty("Time", src.getTime());

            return jObject;
        }
    }

    private class LocationDeserializer implements JsonDeserializer<Location> {
        public Location deserialize(JsonElement json, Type typeOfT, JsonDeserializationContext context)
                throws JsonParseException {
            JsonObject jObject = (JsonObject) json;
            Location location = new Location(jObject.get("Provider").getAsString());

            location.setLatitude(jObject.get("Latitude").getAsDouble());
            location.setLongitude(jObject.get("Longitude").getAsDouble());

            if (jObject.has("Altitude"))
                location.setAltitude(jObject.get("Altitude").getAsDouble());

            if (jObject.has("Speed"))
                location.setSpeed(jObject.get("Speed").getAsFloat());

            if (jObject.has("Accuracy"))
                location.setAccuracy(jObject.get("Accuracy").getAsFloat());

            location.setTime(jObject.get("Time").getAsLong());

            return location;
        }
    }

    private String serialize(Location location) {
        GsonBuilder builder = new GsonBuilder();
        builder.registerTypeAdapter(Location.class, new LocationSerializer());
        Gson gson = builder.create();
        String json = gson.toJson(location);
        return json;
    }

    private Location deserialize(String json) {
        GsonBuilder builder = new GsonBuilder();
        builder.registerTypeAdapter(Location.class, new LocationDeserializer());
        Gson gson = builder.create();
        Location location = gson.fromJson(json, Location.class);
        return location;
    }
}
