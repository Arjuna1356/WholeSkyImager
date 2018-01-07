package sg.edu.ntu.wholeskyimagerex2;

import android.os.Bundle;
import android.support.v7.preference.ListPreference;
import android.support.v7.preference.PreferenceFragmentCompat;
import android.support.v7.preference.PreferenceScreen;
import android.support.v7.preference.PreferenceCategory;

import java.util.ArrayList;
import java.util.List;

import sg.edu.ntu.wholeskyimagerex2.Preview.Preview;

public class SettingsFragment extends PreferenceFragmentCompat
{
    public SettingsFragment()
    {
        // Required empty public constructor
    }

    @Override
    public void onCreatePreferences(Bundle savedInstance, String rootPreferenceKey)
    {
        MainActivity mainActivity = (MainActivity) getActivity();

        // Load the preferences from an XML resource
        addPreferencesFromResource(R.xml.settings);

        PreferenceScreen preferenceScreen = getPreferenceManager().createPreferenceScreen(mainActivity);

        // create preferences manually
        PreferenceCategory preferenceCategory = new PreferenceCategory(preferenceScreen.getContext());
        preferenceCategory.setTitle("Camera Options");
        //do anything you want with the preferencecategory here
        preferenceScreen.addPreference(preferenceCategory);

        ListPreference isoPreference = new ListPreference(preferenceScreen.getContext());
        isoPreference.setTitle("ISO Value");
        isoPreference.setKey("preference_iso");

        final String manual_value = "m";


        List<String> isoValues = new ArrayList<>();

        Preview preview = mainActivity.getPreview();
        if (preview.supportsISORange())
        {
            int min_iso = preview.getMinimumISO();
            int max_iso = preview.getMaximumISO();

            isoValues.add("auto");
            isoValues.add(manual_value);
            int[] iso_values = {50, 100, 200, 400, 800, 1600, 3200, 6400};
            isoValues.add("" + min_iso);
            for (int iso_value : iso_values)
            {
                if (iso_value > min_iso && iso_value < max_iso)
                {
                    isoValues.add("" + iso_value);
                }
            }
            isoValues.add("" + max_iso);
        }

        final CharSequence[] isoValsCharSeq = isoValues.toArray(new CharSequence[isoValues.size()]);

        isoPreference.setEntries(isoValsCharSeq);
        isoPreference.setEntryValues(isoValsCharSeq);

        preferenceCategory.addPreference(isoPreference);

        ListPreference hdrPreference = new ListPreference(preferenceScreen.getContext());
        hdrPreference.setTitle("Use HDR?");
        hdrPreference.setKey("hdrPref");
        hdrPreference.setSummary("Try to capture higher quality image.");

        List<String> hdrModes = new ArrayList<>();
        List<String> hdrValues = new ArrayList<>();

        hdrModes.add("Standard");
        hdrValues.add("0");

        if (mainActivity.supportsDRO())
        {
            hdrModes.add("DRO");
            hdrValues.add("1");
        }

        if (mainActivity.supportsHDR())
        {
            hdrModes.add("HDR");
            hdrValues.add("2");
        }

        final CharSequence[] hdrModeCharSeq = hdrModes.toArray(new CharSequence[hdrModes.size()]);
        final CharSequence[] hdrValCharSeq = hdrValues.toArray(new CharSequence[hdrValues.size()]);

        hdrPreference.setEntries(hdrModeCharSeq);
        hdrPreference.setEntryValues(hdrValCharSeq);

        preferenceCategory.addPreference(hdrPreference);
    }
}
