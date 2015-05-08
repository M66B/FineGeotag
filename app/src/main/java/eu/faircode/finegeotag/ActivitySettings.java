package eu.faircode.finegeotag;

import android.content.Context;
import android.content.SharedPreferences;
import android.content.pm.PackageInfo;
import android.content.pm.PackageManager;
import android.location.LocationManager;
import android.os.Bundle;
import android.preference.ListPreference;
import android.preference.Preference;
import android.preference.PreferenceActivity;
import android.util.Log;

import java.util.ArrayList;
import java.util.List;

public class ActivitySettings extends PreferenceActivity implements SharedPreferences.OnSharedPreferenceChangeListener {
    private static final String TAG = "FineGeotag.Settings";

    public static final String PREF_ENABLED = "pref_enabled";
    public static final String PREF_TOAST = "pref_toast";
    public static final String PREF_PROVIDER = "pref_provider";
    public static final String PREF_TIMEOUT = "pref_timeout";
    public static final String PREF_ACCURACY = "pref_accuracy";
    public static final String PREF_VERSION = "pref_version";

    @Override
    public void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        addPreferencesFromResource(R.xml.preferences);

        // Get preferences
        ListPreference pref_provider = (ListPreference) findPreference(PREF_PROVIDER);
        Preference pref_timeout = findPreference(PREF_TIMEOUT);
        Preference pref_accurary = findPreference(PREF_ACCURACY);
        Preference pref_version = findPreference(PREF_VERSION);

        // Get provider name/values
        LocationManager lm = (LocationManager) getSystemService(Context.LOCATION_SERVICE);
        List<String> listProviderValue = lm.getProviders(true);
        List<String> listProviderName = new ArrayList<>();
        for (String provider : listProviderValue)
            listProviderName.add(translate(provider));
        pref_provider.setEntries(listProviderName.toArray(new CharSequence[0]));
        pref_provider.setEntryValues(listProviderValue.toArray(new CharSequence[0]));

        // Set summaries
        SharedPreferences prefs = getPreferenceScreen().getSharedPreferences();
        pref_provider.setSummary(translate(prefs.getString(PREF_PROVIDER, LocationManager.GPS_PROVIDER)));
        pref_timeout.setSummary(getString(R.string.summary_seconds, prefs.getString(PREF_TIMEOUT, null)));
        pref_accurary.setSummary(getString(R.string.summary_meters, prefs.getString(PREF_ACCURACY, null)));

        try {
            String self = ActivitySettings.class.getPackage().getName();
            PackageInfo pInfo = getPackageManager().getPackageInfo(self, 0);
            pref_version.setSummary(pInfo.versionName);
        } catch (PackageManager.NameNotFoundException ignored) {
        }
    }

    private String translate(String provider) {
        String self = ActivitySettings.class.getPackage().getName();
        int resId = getResources().getIdentifier("provider_" + provider, "string", self);
        return (resId > 0 ? getString(resId) : provider);
    }

    @Override
    protected void onResume() {
        super.onResume();
        getPreferenceScreen().getSharedPreferences().registerOnSharedPreferenceChangeListener(this);
    }

    @Override
    protected void onPause() {
        super.onPause();
        getPreferenceScreen().getSharedPreferences().unregisterOnSharedPreferenceChangeListener(this);
    }

    @Override
    public void onSharedPreferenceChanged(SharedPreferences sharedPreferences, String key) {
        Log.w(TAG, "Changed pref=" + key);

        Preference pref = findPreference(key);
        if (PREF_PROVIDER.equals(key))
            pref.setSummary(translate(sharedPreferences.getString(key, null)));

        else if (PREF_TIMEOUT.equals(key))
            pref.setSummary(getString(R.string.summary_seconds, sharedPreferences.getString(key, null)));

        else if (PREF_ACCURACY.equals(key))
            pref.setSummary(getString(R.string.summary_meters, sharedPreferences.getString(key, null)));
    }
}
