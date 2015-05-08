package eu.faircode.finegeotag;

import android.content.Context;
import android.content.SharedPreferences;
import android.content.pm.PackageInfo;
import android.content.pm.PackageManager;
import android.location.LocationManager;
import android.os.Bundle;
import android.preference.EditTextPreference;
import android.preference.ListPreference;
import android.preference.Preference;
import android.preference.PreferenceActivity;
import android.util.Log;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;

public class ActivitySettings extends PreferenceActivity implements SharedPreferences.OnSharedPreferenceChangeListener {
    private static final String TAG = "FineGeotag.Settings";

    public static final String PREF_ENABLED = "pref_enabled";
    public static final String PREF_TOAST = "pref_toast";
    public static final String PREF_PROVIDER = "pref_provider";
    public static final String PREF_TIMEOUT = "pref_timeout";
    public static final String PREF_ACCURACY = "pref_accuracy";
    public static final String PREF_VERSION = "pref_version";

    public static final String DEFAULT_TIMEOUT = "60";
    public static final String DEFAULT_ACCURACY = "50";

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
        Preference pref_enabled = findPreference(PREF_ENABLED);
        ListPreference pref_provider = (ListPreference) findPreference(PREF_PROVIDER);
        Preference pref_timeout = findPreference(PREF_TIMEOUT);
        Preference pref_accuracy = findPreference(PREF_ACCURACY);
        Preference pref_version = findPreference(PREF_VERSION);

        // Set default values
        pref_provider.setDefaultValue(getDefaultProvider(this));
        pref_timeout.setDefaultValue(DEFAULT_TIMEOUT);
        pref_accuracy.setDefaultValue(DEFAULT_ACCURACY);

        // Get provider name/values
        LocationManager lm = (LocationManager) getSystemService(Context.LOCATION_SERVICE);
        List<String> listProviderValue = lm.getProviders(true);
        Collections.sort(listProviderValue);
        List<String> listProviderName = new ArrayList<>();
        for (String provider : listProviderValue)
            listProviderName.add(translateProvider(provider));
        pref_provider.setEntries(listProviderName.toArray(new CharSequence[0]));
        pref_provider.setEntryValues(listProviderValue.toArray(new CharSequence[0]));

        // Set summaries
        pref_enabled.setSummary(prefs.getBoolean(PREF_ENABLED, true) ? getString(R.string.summary_camera) : null);
        if (lm.isProviderEnabled(prefs.getString(PREF_PROVIDER, getDefaultProvider(this))))
            pref_provider.setSummary(translateProvider(prefs.getString(PREF_PROVIDER, getDefaultProvider(this))));
        else
            pref_provider.setSummary(getString(R.string.provider_select));
        pref_timeout.setSummary(getString(R.string.summary_seconds, prefs.getString(PREF_TIMEOUT, DEFAULT_TIMEOUT)));
        pref_accuracy.setSummary(getString(R.string.summary_meters, prefs.getString(PREF_ACCURACY, DEFAULT_ACCURACY)));

        try {
            String self = ActivitySettings.class.getPackage().getName();
            PackageInfo pInfo = getPackageManager().getPackageInfo(self, 0);
            pref_version.setSummary(pInfo.versionName);
        } catch (PackageManager.NameNotFoundException ignored) {
        }
    }

    @Override
    protected void onPause() {
        super.onPause();
        getPreferenceScreen().getSharedPreferences().unregisterOnSharedPreferenceChangeListener(this);
    }

    public static String getDefaultProvider(Context context) {
        String provider = LocationManager.PASSIVE_PROVIDER;
        LocationManager lm = (LocationManager) context.getSystemService(Context.LOCATION_SERVICE);
        if (lm.isProviderEnabled(LocationManager.GPS_PROVIDER))
            provider = LocationManager.GPS_PROVIDER;
        else if (lm.isProviderEnabled(LocationManager.NETWORK_PROVIDER))
            provider = LocationManager.NETWORK_PROVIDER;
        return provider;
    }

    private String translateProvider(String provider) {
        String self = ActivitySettings.class.getPackage().getName();
        int resId = getResources().getIdentifier("provider_" + provider, "string", self);
        return (resId > 0 ? getString(resId) : provider);
    }

    @Override
    public void onSharedPreferenceChanged(SharedPreferences prefs, String key) {
        Log.w(TAG, "Changed pref=" + key);
        Preference pref = findPreference(key);

        // Remove empty string settings
        if (pref instanceof EditTextPreference)
            if ("".equals(prefs.getString(key, null))) {
                SharedPreferences.Editor edit = prefs.edit();
                edit.remove(key);
                edit.apply();
            }

        if (PREF_ENABLED.equals(key))
            pref.setSummary(prefs.getBoolean(PREF_ENABLED, true) ? getString(R.string.summary_camera) : null);

        else if (PREF_PROVIDER.equals(key))
            pref.setSummary(translateProvider(prefs.getString(key, getDefaultProvider(this))));

        else if (PREF_TIMEOUT.equals(key))
            pref.setSummary(getString(R.string.summary_seconds, prefs.getString(key, DEFAULT_TIMEOUT)));

        else if (PREF_ACCURACY.equals(key))
            pref.setSummary(getString(R.string.summary_meters, prefs.getString(key, DEFAULT_ACCURACY)));
    }
}
