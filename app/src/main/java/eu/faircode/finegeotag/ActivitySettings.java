package eu.faircode.finegeotag;

import android.content.Intent;
import android.content.SharedPreferences;
import android.content.pm.PackageInfo;
import android.content.pm.PackageManager;
import android.location.Geocoder;
import android.net.Uri;
import android.os.Bundle;
import android.preference.EditTextPreference;
import android.preference.ListPreference;
import android.preference.Preference;
import android.preference.PreferenceActivity;
import android.provider.Settings;

public class ActivitySettings extends PreferenceActivity implements SharedPreferences.OnSharedPreferenceChangeListener {
    private static final String TAG = "FineGeotag.Settings";

    public static final String PREF_ENABLED = "pref_enabled";
    public static final String PREF_TOAST = "pref_toast";
    public static final String PREF_ALTITUDE = "pref_altitude";
    public static final String PREF_ACCURACY = "pref_accuracy";
    public static final String PREF_TIMEOUT = "pref_timeout";
    public static final String PREF_KNOWN = "pref_known";
    public static final String PREF_CHECK = "pref_check";
    public static final String PREF_VERSION = "pref_version";

    public static final boolean DEFAULT_ENABLED = true;
    public static final boolean DEFAULT_TOAST = true;
    public static final boolean DEFAULT_ALTITUDE = true;
    public static final String DEFAULT_ACCURACY = "25"; // meters
    public static final String DEFAULT_TIMEOUT = "60"; // seconds
    public static final String DEFAULT_KNOWN = "240"; // minutes

    @Override
    public void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        addPreferencesFromResource(R.xml.preferences);
    }

    @Override
    protected void onResume() {
        super.onResume();
        getPreferenceScreen().getSharedPreferences().registerOnSharedPreferenceChangeListener(this);

        SharedPreferences prefs = getPreferenceScreen().getSharedPreferences();

        // Get preferences
        Preference pref_check = findPreference(PREF_CHECK);
        Preference pref_version = findPreference(PREF_VERSION);

        // Set summaries
        onSharedPreferenceChanged(prefs, PREF_ENABLED);
        onSharedPreferenceChanged(prefs, PREF_TOAST);
        onSharedPreferenceChanged(prefs, PREF_ALTITUDE);
        onSharedPreferenceChanged(prefs, PREF_ACCURACY);
        onSharedPreferenceChanged(prefs, PREF_TIMEOUT);
        onSharedPreferenceChanged(prefs, PREF_KNOWN);

        // Location settings
        Intent locationSettingsIntent = new Intent(Settings.ACTION_LOCATION_SOURCE_SETTINGS);
        if (getPackageManager().queryIntentActivities(locationSettingsIntent, 0).size() > 0)
            pref_check.setIntent(locationSettingsIntent);
        else
            pref_check.setEnabled(false);

        // Version
        Intent playStoreIntent = new Intent(Intent.ACTION_VIEW, Uri.parse("market://details?id=" + getPackageName()));
        if (getPackageManager().queryIntentActivities(playStoreIntent, 0).size() > 0)
            pref_version.setIntent(playStoreIntent);
        try {
            PackageInfo pInfo = getPackageManager().getPackageInfo(getPackageName(), 0);
            pref_version.setSummary(
                    pInfo.versionName + "/" + pInfo.versionCode + "\n" +
                            getString(R.string.msg_geocoder) + " " +
                            getString(Geocoder.isPresent() ? R.string.msg_yes : R.string.msg_no));
        } catch (PackageManager.NameNotFoundException ignored) {
        }
    }

    @Override
    protected void onPause() {
        super.onPause();
        getPreferenceScreen().getSharedPreferences().unregisterOnSharedPreferenceChangeListener(this);
    }

    @Override
    public void onSharedPreferenceChanged(SharedPreferences prefs, String key) {
        Preference pref = findPreference(key);

        // Remove empty string settings
        if (pref instanceof EditTextPreference)
            if ("".equals(prefs.getString(key, null))) {
                SharedPreferences.Editor edit = prefs.edit();
                edit.remove(key);
                edit.apply();
            }

        if (PREF_ENABLED.equals(key))
            pref.setSummary(prefs.getBoolean(PREF_ENABLED, true) ? getString(R.string.summary_enabled) : null);

        else if (PREF_TOAST.equals(key))
            pref.setSummary(prefs.getBoolean(PREF_TOAST, true) ? getString(R.string.summary_toast) : null);

        else if (PREF_ALTITUDE.equals(key))
            pref.setSummary(prefs.getBoolean(PREF_ALTITUDE, true) ? getString(R.string.summary_altitude) : null);

        else if (PREF_ACCURACY.equals(key))
            pref.setTitle(getString(R.string.title_accuracy, prefs.getString(key, DEFAULT_ACCURACY)));

        else if (PREF_TIMEOUT.equals(key))
            pref.setTitle(getString(R.string.title_timeout, prefs.getString(key, DEFAULT_TIMEOUT)));

        else if (PREF_KNOWN.equals(key)) {
            ListPreference lpref = (ListPreference) pref;
            int index = lpref.findIndexOfValue(prefs.getString(key, DEFAULT_KNOWN));
            pref.setTitle(getString(R.string.title_known, lpref.getEntries()[index]));
        }
    }
}
