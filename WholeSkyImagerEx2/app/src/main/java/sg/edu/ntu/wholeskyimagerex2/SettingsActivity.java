package sg.edu.ntu.wholeskyimagerex2;

import android.os.Build;
import android.os.Bundle;
import android.preference.PreferenceActivity;
import android.preference.PreferenceScreen;
import android.support.v4.content.ContextCompat;
import android.support.v7.widget.Toolbar;
import android.view.LayoutInflater;
import android.view.Menu;
import android.view.View;
import android.view.Window;
import android.view.WindowManager;
import android.widget.LinearLayout;
import android.preference.PreferenceCategory;
import android.preference.Preference;
import android.preference.ListPreference;

import java.util.ArrayList;

/**
 * Created by Julian on 24.11.2016.
 */

public class SettingsActivity extends PreferenceActivity
{
    @Override
    public void onCreate(Bundle savedInstanceState)
    {
        super.onCreate(savedInstanceState);

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
//        isoPreference.setEntries();
//        isoPreference.setEntryValues();

        preferenceCategory.addPreference(isoPreference);

        ListPreference hdrPreference = new ListPreference(preferenceScreen.getContext());
        hdrPreference.setTitle("Use HDR?");
        hdrPreference.setKey("hdrPref");

        ArrayList<String> hdrModes = new ArrayList<String>();
        ArrayList<String> hdrValues = new ArrayList<String>();

        hdrModes.add("Standard");
        hdrValues.add("0");

//        if(mainActivity)
//        {
//
//        }
//
//
//        hdrPreference.setEntries();
//        hdrPreference.setEntryValues();

        preferenceCategory.addPreference(hdrPreference);
    }

    @Override
    protected void onPostCreate(Bundle savedInstanceState)
    {
        super.onPostCreate(savedInstanceState);

        LinearLayout root = (LinearLayout) findViewById(android.R.id.list).getParent().getParent().getParent();
        Toolbar bar = (Toolbar) LayoutInflater.from(this).inflate(R.layout.activity_settings, root, false);
        root.addView(bar, 0); // insert at top
        bar.setNavigationOnClickListener(new View.OnClickListener()
        {
            @Override
            public void onClick(View v)
            {
                finish();
            }
        });

        if (Build.VERSION.SDK_INT > 21)
        {
            Window window = this.getWindow();
            window.addFlags(WindowManager.LayoutParams.FLAG_DRAWS_SYSTEM_BAR_BACKGROUNDS);
            window.clearFlags(WindowManager.LayoutParams.FLAG_TRANSLUCENT_STATUS);
            window.setStatusBarColor(ContextCompat.getColor(this, R.color.colorStatusBar));
        }
    }

    @Override
    public boolean onCreateOptionsMenu(Menu menu)
    {
        getMenuInflater().inflate(R.menu.menu, menu);
        return true;
    }
}
