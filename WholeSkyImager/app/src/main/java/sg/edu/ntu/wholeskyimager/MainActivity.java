package sg.edu.ntu.wholeskyimager;

import android.Manifest;

import android.content.Intent;
import android.content.SharedPreferences;
import android.content.pm.ActivityInfo;

import android.graphics.Color;
import android.graphics.drawable.ColorDrawable;
import android.os.Build;
import android.preference.PreferenceManager;
import android.support.annotation.NonNull;
import android.support.v4.content.ContextCompat;
import android.support.v7.app.AppCompatActivity;

import android.os.Handler;
import android.os.HandlerThread;
import android.os.Bundle;

import android.content.pm.PackageManager;

import android.support.v7.widget.Toolbar;
import android.text.format.DateFormat;
import android.text.method.ScrollingMovementMethod;
import android.view.Menu;
import android.view.MenuItem;
import android.view.View;

import android.view.Window;
import android.view.WindowManager;
import android.widget.FrameLayout;
import android.widget.TextView;
import android.widget.Toast;
import android.widget.Button;

import android.util.Log;

import java.util.Date;
import java.util.concurrent.RunnableFuture;

import static android.util.Log.d;

public class MainActivity extends AppCompatActivity
{
    private final int REQUEST_CAMERA_PERMISSION = 200;
    private int wahrsisModelNr = 6;
    private final String TAG = "WSIApp";

    private int pictureInterval = 1;
    private int delayTime = 15;
    private boolean afEnabled = true;

    private Camera camera = null;
    SharedPreferences sharedPref = null;

    private Button runButton = null;
    private Button stopButton = null;
    private FrameLayout frameLayout = null;
    private TextView tvEventLog = null;
    private TextView tvStatusInfo = null;
    private TextView tvConnectionStatus = null;

    private HandlerThread mBackgroundThread = null;
    private Handler mBackgroundHandler = null;

    private String authorizationToken;
    WSIServerClient serverClient;

    @Override
    protected void onCreate(Bundle savedInstanceState)
    {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_main);
        setRequestedOrientation(ActivityInfo.SCREEN_ORIENTATION_PORTRAIT);

        setupActionBar();

        initialize();

        checkPermissions();

        getWSISettings();
    }

    @Override
    public boolean onCreateOptionsMenu(Menu menu)
    {
        // Inflate the menu; this adds items to the action bar if it is present.
        getMenuInflater().inflate(R.menu.menu, menu);

        return true;
    }

    // get picture data (no writing)
    @Override
    public boolean onOptionsItemSelected(MenuItem item)
    {
        // Handle action bar item clicks here. The action bar will
        // automatically handle clicks on the Home/Up button, so long
        // as you specify a parent activity in AndroidManifest.xml.
        int id = item.getItemId();

        switch (item.getItemId())
        {
            case R.id.action_settings:
                Intent intentSettings = new Intent(this, SettingsActivity.class);
                startActivity(intentSettings);
                return true;

            case R.id.action_refresh:
                getWSISettings();
//                checkNetworkStatus();
                Toast.makeText(MainActivity.this, "Refreshed Settings", Toast.LENGTH_SHORT).show();
                return true;

            case R.id.action_help:
                return true;

            case R.id.action_about:
                Intent intentAbout = new Intent(this, DisplayAboutActivity.class);
                startActivity(intentAbout);
                return true;

            default:
                return super.onOptionsItemSelected(item);
        }
    }

    @Override
    public void onRequestPermissionsResult(int requestCode, @NonNull String[] permissions, @NonNull int[] grantResults)
    {
        if (requestCode == REQUEST_CAMERA_PERMISSION)
        {
            if (grantResults[0] == PackageManager.PERMISSION_DENIED)
            {
                // close the app
                Toast.makeText(MainActivity.this, "Sorry, you can't use this app without granting permission", Toast.LENGTH_LONG).show();
                finish();
            }
        }
    }

    @Override
    public void onResume()
    {
        super.onResume();
        Log.d(TAG, "onResume");
        startBackgroundThread();
    }

    @Override
    public void onPause()
    {
        Log.d(TAG, "onPause");

        stopImaging();

        stopBackgroundThread();
        super.onPause();
    }

    public int getRequestCameraPermission()
    {
        return REQUEST_CAMERA_PERMISSION;
    }

    public void setupActionBar()
    {
        Toolbar myToolbar = (Toolbar) findViewById(R.id.my_toolbar);
        setSupportActionBar(myToolbar);
        android.support.v7.app.ActionBar menu = getSupportActionBar();
        menu.setDisplayHomeAsUpEnabled(false);
        menu.setBackgroundDrawable(new ColorDrawable(ContextCompat.getColor(this, R.color.colorActionBar)));
        menu.setTitle(R.string.app_name);

        if (Build.VERSION.SDK_INT > 21)
        {
            Window window = this.getWindow();
            window.addFlags(WindowManager.LayoutParams.FLAG_DRAWS_SYSTEM_BAR_BACKGROUNDS);
            window.clearFlags(WindowManager.LayoutParams.FLAG_TRANSLUCENT_STATUS);
            window.setStatusBarColor(ContextCompat.getColor(this, R.color.colorStatusBar));
        }
    }

    public TextView getTvEventLog()
    {
        return tvEventLog;
    }

    public int getWahrsisModelNr()
    {
        return wahrsisModelNr;
    }

    public boolean getAFEnabled()
    {
        return afEnabled;
    }

    public WSIServerClient getServerClient ()
    {
        return serverClient;
    }

    private void startBackgroundThread()
    {
        mBackgroundThread = new HandlerThread("Camera Background");
        mBackgroundThread.start();
        mBackgroundHandler = new Handler();
        camera.setHandler(mBackgroundHandler);
    }

    private void stopBackgroundThread()
    {
        mBackgroundThread.quitSafely();
        try
        {
            mBackgroundThread.join();
            mBackgroundThread = null;
            mBackgroundHandler = null;
            camera.setHandler(null);
        } catch (InterruptedException e)
        {
            e.printStackTrace();
        }
    }

    private void initialize()
    {
        frameLayout = (FrameLayout) findViewById(R.id.camera_preview);

        tvEventLog = (TextView) findViewById(R.id.tvEventLog);
        tvEventLog.setMovementMethod(new ScrollingMovementMethod());

        Date d2 = new Date();
        CharSequence dateTime2 = DateFormat.format("HH:mm:ss", d2.getTime());
        tvEventLog.append("\nTime: " + dateTime2);

        tvStatusInfo = (TextView) findViewById(R.id.tvStatusInfo);
        tvStatusInfo.setText("idle");

        tvConnectionStatus  = (TextView) findViewById(R.id.tvConnectionStatus);

        camera = new Camera(this);

        runButton = (Button) findViewById(R.id.buttonRun);
        assert runButton != null;
        runButton.setTag(0);
        runButton.setOnClickListener(new View.OnClickListener()
        {
            @Override
            public void onClick(View v)
            {
                final int status = (Integer) v.getTag();

                if (status == 0)
                {
                    getWSISettings();

                    camera.openCamera(frameLayout);
                    runButton.setText(getResources().getString(R.string.captureButton_text));
                    v.setTag(1);

                    beginImaging();
                } else
                {
                    camera.takePicture();
                }

            }
        });

        stopButton = (Button) findViewById(R.id.buttonStop);
        assert stopButton != null;
        stopButton.setOnClickListener(new View.OnClickListener()
        {
            @Override
            public void onClick(View v)
            {
                stopImaging();
            }
        });

        // initiate server client
        serverClient = new WSIServerClient(this, "https://www.visuo.adsc.com.sg/api/skypicture/", authorizationToken);
        checkNetworkStatus();
    }

    private void checkPermissions()
    {
        // Add permission for camera and let user grant the permission
        if (checkSelfPermission(Manifest.permission.CAMERA) != PackageManager.PERMISSION_GRANTED &&
                checkSelfPermission(Manifest.permission.WRITE_EXTERNAL_STORAGE) != PackageManager.PERMISSION_GRANTED)
        {
            requestPermissions(new String[]{Manifest.permission.CAMERA, Manifest.permission.WRITE_EXTERNAL_STORAGE}, REQUEST_CAMERA_PERMISSION);
            return;
        }
    }

    private void beginImaging()
    {
//        mBackgroundHandler = new Handler();

        mBackgroundHandler.postDelayed(imagingRunnable, delayTime * 1000);

        Log.d(TAG, "Imaging will begin in " + delayTime + " seconds");
        tvEventLog.append("\nImaging will begin in " + delayTime + " seconds");
    }

    private void stopImaging()
    {
        final int status = (Integer) runButton.getTag();

        if (status == 1)
        {
            mBackgroundHandler.removeCallbacks(imagingRunnable);
            mBackgroundHandler = null;

            runButton.setTag(0);
            runButton.setText(getResources().getString(R.string.runButton_text));

            Log.d(TAG, "Imaging Stopped");
            tvEventLog.append("\nImaging Stopped");

            camera.closeCamera(frameLayout);
        }
    }

    private void checkNetworkStatus() {
        // check internet connection
        if (serverClient.isConnected()) {
            tvConnectionStatus.setText("online");
            tvConnectionStatus.setTextColor(getResources().getColor(R.color.darkGreen));
            d(TAG, "Device is online.");
            tvEventLog.append("\nDevice is online.");
        } else {
            tvConnectionStatus.setText("offline");
            tvConnectionStatus.setTextColor(Color.BLACK);
            d(TAG, "Device is offline.");
            tvEventLog.append("\nDevice is offline.");
        }
    }

    /**
     * set up preferences
     */
    private void getWSISettings()
    {
        sharedPref = PreferenceManager.getDefaultSharedPreferences(this);

        Log.d(TAG, "Model No. in pref xml: " + Integer.parseInt(sharedPref.getString("wahrsisNo", "0")));
//        tvEventLog.append("\nModel No. in pref xml: " + Integer.parseInt(sharedPref.getString("wahrsisNo", "0")));
        // Set wahrsis model number according to settings activity
        if (Integer.parseInt(sharedPref.getString("wahrsisNo", "0")) != 0)
        {
            wahrsisModelNr = Integer.parseInt(sharedPref.getString("wahrsisNo", "404"));
            Log.d(TAG, "Model No. set to: " + wahrsisModelNr);
        }

        pictureInterval = Integer.parseInt(sharedPref.getString("picInterval", "404"));
        Log.d(TAG, "Picture interval: " + pictureInterval + " min.");

        pictureInterval = Integer.parseInt(sharedPref.getString("picInterval", "404"));

        delayTime = Integer.parseInt(sharedPref.getString("startDelay", "15"));

        afEnabled = sharedPref.getBoolean("enableAF", true);
//        tvEventLog.append("\nPicture interval: " + pictureInterval + " min.");
//        flagWriteExif = sharedPref.getBoolean("extendedExif", false);
//        Log.d(TAG, "Extended exif: " + flagWriteExif);
//        tvEventLog.append("\nExtended exif: " + flagWriteExif);
//        flagRealignImage = sharedPref.getBoolean("realignImage", false);
//        Log.d(TAG, "Realign image: " + flagRealignImage);
//        tvEventLog.append("\nRealign image: " + flagRealignImage);

//        authorizationToken = sharedPref.getString("authorToken", "f26543bea24e3545a8ef9708dffd7ce5d35127e2");
//        Log.d(TAG, "Authorization token: " + authorizationToken);
    }

    private final Runnable imagingRunnable = new Runnable()
    {
        @Override
        public void run()
        {
            try
            {
                Date d = new Date();
                CharSequence dateTime = DateFormat.format("yyyy-MM-dd hh:mm:ss", d.getTime());
                Log.d(TAG, "Runnable execution started. Time: " + dateTime + ". Interval: " + pictureInterval + " min");
                tvEventLog.append("\nRunnable execution started. Time: " + dateTime + ". Interval: " + pictureInterval + " min");
                camera.runImagingTask();
            } catch (Exception e)
            {
                // TODO: handle exception
                Log.e(TAG, "Error: Runnable exception");
                tvEventLog.append("\nError: Runnable exception");
            } finally
            {
                //also call the same runnable to call it at regular interval
            }
            mBackgroundHandler.postDelayed(this, pictureInterval * 60 * 1000);
        }
    };
}