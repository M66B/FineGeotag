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
        double lat = location.getLatitude();
        double lon = location.getLongitude();

        this.setAttribute(TAG_GPS_LATITUDE_REF, ((lat > 0) ? "N" : "S"));
        this.setAttribute(TAG_GPS_LONGITUDE_REF, ((lon > 0) ? "E" : "W"));

        int latDeg = (int) Math.abs(lat);
        int latMin = (int) Math.abs(((lat % 1) * 60));
        int latSec = (int) Math.abs(((((lat % 1) * 60) % 1) * 60));

        int lonDeg = (int) Math.abs(lon);
        int lonMin = (int) Math.abs(((lon % 1) * 60));
        int lonSec = (int) Math.abs(((((lon % 1) * 60) % 1) * 60));

        String latStr = String.format("%d/1,%d/1,%d/1", latDeg, latMin, latSec);
        String lonStr = String.format("%d/1,%d/1,%d/1", lonDeg, lonMin, lonSec);

        this.setAttribute(TAG_GPS_LATITUDE, latStr);
        this.setAttribute(TAG_GPS_LONGITUDE, lonStr);

        Date date = new Date(location.getTime());

        String dateStamp = new SimpleDateFormat("y:M:d").format(date);
        String timeStamp = new SimpleDateFormat("H:m:s").format(date);

        this.setAttribute(TAG_GPS_DATESTAMP, dateStamp);
        this.setAttribute(TAG_GPS_TIMESTAMP, timeStamp);

        if (location.hasAltitude()) {
            double altitude = location.getAltitude();
            this.setAttribute(TAG_GPS_ALTITUDE_REF, (altitude > 0 ? "0" : "1"));
            this.setAttribute(TAG_GPS_ALTITUDE, String.valueOf(Math.abs(altitude)));
        }
    }
}
