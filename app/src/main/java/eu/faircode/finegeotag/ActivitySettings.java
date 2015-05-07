package eu.faircode.finegeotag;

import android.content.Context;
import android.content.SharedPreferences;
import android.location.LocationManager;
import android.os.Bundle;
import android.preference.ListPreference;
import android.preference.Preference;
import android.preference.PreferenceActivity;
import android.util.Log;

import java.util.List;

public class ActivitySettings extends PreferenceActivity implements SharedPreferences.OnSharedPreferenceChangeListener {
    private static final String TAG = "FineGeotag.Settings";

    public static final String PREF_ENABLED = "pref_enabled";
    public static final String PREF_PROVIDER = "pref_provider";
    public static final String PREF_TIMEOUT = "pref_timeout";
    public static final String PREF_TOAST = "pref_toast";

    @Override
    public void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        addPreferencesFromResource(R.xml.preferences);

        Preference pref_timeout = findPreference(PREF_TIMEOUT);
        ListPreference pref_provider = (ListPreference) findPreference(PREF_PROVIDER);

        LocationManager lm = (LocationManager) getSystemService(Context.LOCATION_SERVICE);
        List<String> listProvider = lm.getProviders(true);
        pref_provider.setEntries(listProvider.toArray(new CharSequence[0]));
        pref_provider.setEntryValues(listProvider.toArray(new CharSequence[0]));

        SharedPreferences prefs = getPreferenceScreen().getSharedPreferences();
        pref_provider.setSummary(prefs.getString(PREF_PROVIDER, LocationManager.GPS_PROVIDER));
        pref_timeout.setSummary(getString(R.string.summary_timeout, prefs.getString(PREF_TIMEOUT, null)));

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
            pref.setSummary(sharedPreferences.getString(key, null));

        else if (PREF_TIMEOUT.equals(key))
            pref.setSummary(getString(R.string.summary_timeout, sharedPreferences.getString(key, null)));
    }
}
