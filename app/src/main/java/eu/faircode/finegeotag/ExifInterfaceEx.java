package eu.faircode.finegeotag;

import android.location.Location;
import android.media.ExifInterface;

import java.io.File;
import java.io.IOException;
import java.text.SimpleDateFormat;
import java.util.Date;

public class ExifInterfaceEx extends ExifInterface {

    public ExifInterfaceEx(String filename) throws IOException {
        super(filename);
    }

    public ExifInterfaceEx(File file) throws IOException {
        super(file.getAbsolutePath());
    }

    public void setLocation(Location location) {
        // Latitude
        double lat = location.getLatitude();
        this.setAttribute(TAG_GPS_LATITUDE_REF, lat > 0 ? "N" : "S");
        this.setAttribute(TAG_GPS_LATITUDE, DMS(lat));

        // Longitude
        double lon = location.getLongitude();
        this.setAttribute(TAG_GPS_LONGITUDE_REF, lon > 0 ? "E" : "W");
        this.setAttribute(TAG_GPS_LONGITUDE, DMS(lon));

        // Date/time
        Date date = new Date(location.getTime());
        this.setAttribute(TAG_GPS_DATESTAMP, new SimpleDateFormat("y:M:d").format(date));
        this.setAttribute(TAG_GPS_TIMESTAMP, new SimpleDateFormat("H:m:s").format(date));

        // Altitude
        if (location.hasAltitude()) {
            double altitude = location.getAltitude();
            this.setAttribute(TAG_GPS_ALTITUDE_REF, altitude > 0 ? "0" : "1");
            this.setAttribute(TAG_GPS_ALTITUDE, String.valueOf(Math.abs(altitude)));
        }

        // Speed
        if (location.hasSpeed()) {
            this.setAttribute("GPSSpeedRef", "K"); // Km/h
            this.setAttribute("GPSSpeed", String.valueOf(location.getSpeed() * 3600 / 1000));
        }
    }

    private static String DMS(double x) {
        int d = (int) Math.abs(x);
        int m = (int) Math.abs((x % 1) * 60);
        int s = (int) Math.abs((((x % 1) * 60) % 1) * 60);
        return String.format("%d/1,%d/1,%d/1", d, m, s);
    }
}
