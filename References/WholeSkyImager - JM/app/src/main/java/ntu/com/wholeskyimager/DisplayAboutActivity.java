package ntu.com.wholeskyimager;

import android.content.Intent;
import android.os.Bundle;
import android.support.v7.app.AppCompatActivity;

public class DisplayAboutActivity extends AppCompatActivity {

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_display_about);

        android.support.v7.app.ActionBar menuSettings = getSupportActionBar();

        if (menuSettings != null) {
            menuSettings.setTitle("Settings");
            menuSettings.setDisplayHomeAsUpEnabled(false);
        }
        Intent intentAbout = getIntent();
    }
}
