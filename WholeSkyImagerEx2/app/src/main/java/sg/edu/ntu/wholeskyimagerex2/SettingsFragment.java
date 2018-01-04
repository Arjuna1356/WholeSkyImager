package sg.edu.ntu.wholeskyimagerex2;

import android.content.Context;
import android.net.Uri;
import android.os.Bundle;
import android.preference.ListPreference;
import android.preference.PreferenceCategory;
import android.preference.PreferenceFragment;
import android.preference.PreferenceScreen;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;

import java.util.ArrayList;

public class SettingsFragment extends PreferenceFragment
{
    public SettingsFragment()
    {
        // Required empty public constructor
    }

    @Override
    public View onCreateView(LayoutInflater inflater, ViewGroup container,
                             Bundle savedInstanceState)
    {
        // Inflate the layout for this fragment
        View view = inflater.inflate(R.layout.fragment_settings, container, false);

        // Load the preferences from an XML resource
        addPreferencesFromResource(R.xml.settings);

        PreferenceScreen preferenceScreen = this.getPreferenceScreen();

        // create preferences manually
        PreferenceCategory preferenceCategory = new PreferenceCategory(preferenceScreen.getContext());
        preferenceCategory.setTitle("Camera Options");
        //do anything you want with the preferencecategory here
        preferenceScreen.addPreference(preferenceCategory);

        ListPreference isoPreference = new ListPreference(preferenceScreen.getContext());
        isoPreference.setTitle("ISO Value");
        isoPreference.setKey("preference_iso");
        isoPreference.setEntries();
        isoPreference.setEntryValues();

        preferenceCategory.addPreference(isoPreference);

        ListPreference hdrPreference = new ListPreference(preferenceScreen.getContext());
        hdrPreference.setTitle("Use HDR?");
        hdrPreference.setKey("hdrPref");

        ArrayList<String> hdrModes = new ArrayList<String>();
        ArrayList<String> hdrValues = new ArrayList<String>();

        hdrModes.add("Standard");
        hdrValues.add("0");

        if(mainActivity)
        {

        }

        hdrPreference.setEntries();
        hdrPreference.setEntryValues();

        preferenceCategory.addPreference(hdrPreference);

        return view;
    }
}
