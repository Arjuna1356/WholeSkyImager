package sg.edu.ntu.wholeskyimagerex;

import android.Manifest;

import android.annotation.TargetApi;

import android.app.ActivityManager;
import android.app.AlertDialog;

import android.content.DialogInterface;
import android.content.Intent;
import android.content.SharedPreferences;
import android.content.pm.ActivityInfo;
import android.content.pm.PackageManager;

import android.graphics.drawable.ColorDrawable;

import android.os.Build;
import android.os.Bundle;
import android.os.Environment;
import android.os.Handler;
import android.os.HandlerThread;

import android.preference.PreferenceManager;

import android.renderscript.RenderScript;

import android.support.annotation.NonNull;
import android.support.v4.app.ActivityCompat;
import android.support.v4.content.ContextCompat;
import android.support.v7.app.AppCompatActivity;
import android.support.v7.widget.Toolbar;

import android.text.format.DateFormat;
import android.text.method.ScrollingMovementMethod;

import android.util.Log;

import android.view.Menu;
import android.view.MenuItem;
import android.view.View;
import android.view.ViewGroup;
import android.view.Window;
import android.view.WindowManager;

import android.widget.Button;
import android.widget.FrameLayout;
import android.widget.TextView;

import java.io.File;
import java.util.Date;
import java.util.Locale;

import sg.edu.ntu.wholeskyimagerex.CameraController.CameraControllerManager2;
import sg.edu.ntu.wholeskyimagerex.Preview.Preview;

public class MainActivity extends AppCompatActivity
{
    private int wahrsisModelNr = 6;
    private final String TAG = "WSIApp";

    private int pictureInterval = 1;
    private int delayTime = 15;
    private boolean afEnabled = true;

    SharedPreferences sharedPref;

    private Button runButton;
    private Button stopButton;
    private FrameLayout frameLayout;
    private TextView tvEventLog;
    private TextView tvStatusInfo;

    private HandlerThread mBackgroundThread;
    private Handler mBackgroundHandler;

    private ActivityManager activityManager;
    private MyApplicationInterface applicationInterface;
    private Preview preview;
    private TextFormatter textFormatter;

    private boolean supports_camera2 = false;

    final private int MY_PERMISSIONS_REQUEST_CAMERA = 0;
    final private int MY_PERMISSIONS_REQUEST_RECORD_AUDIO = 1;
    final private int MY_PERMISSIONS_REQUEST_LOCATION = 2;

    @Override
    protected void onCreate(Bundle savedInstanceState)
    {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_main);
        setRequestedOrientation(ActivityInfo.SCREEN_ORIENTATION_PORTRAIT);

        setupActionBar();

        initialize(savedInstanceState);

        checkPermissions();
    }

    @Override
    protected void onDestroy()
    {
        if (applicationInterface != null)
        {
            applicationInterface.onDestroy();
        }
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.M)
        {
            // see note in HDRProcessor.onDestroy() - but from Android M, renderscript contexts are released with releaseAllContexts()
            // doc for releaseAllContexts() says "If no contexts have been created this function does nothing"
            RenderScript.releaseAllContexts();
        }

        super.onDestroy();
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

        waitUntilImageQueueEmpty(); // so we don't risk losing any images

        switch (id)
        {
            case R.id.action_settings:
                Intent intentSettings = new Intent(this, SettingsActivity.class);
                startActivity(intentSettings);
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
    public void onResume()
    {
        super.onResume();
        Log.d(TAG, "onResume");
//        startBackgroundThread();
    }

    @Override
    public void onPause()
    {
        Log.d(TAG, "onPause");

        waitUntilImageQueueEmpty(); // so we don't risk losing any images

        super.onPause();

        stopImaging();

//        stopBackgroundThread();
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

    public int getRequestCameraPermission()
    {
        return MY_PERMISSIONS_REQUEST_CAMERA;
    }

    public Preview getPreview()
    {
        return this.preview;
    }

    public MyApplicationInterface getApplicationInterface()
    {
        return this.applicationInterface;
    }

    public TextFormatter getTextFormatter()
    {
        return this.textFormatter;
    }

    public StorageUtils getStorageUtils()
    {
        return this.applicationInterface.getStorageUtils();
    }

    public boolean supportsDRO()
    {
        // require at least Android 5, for the Renderscript support in HDRProcessor
        return (Build.VERSION.SDK_INT >= Build.VERSION_CODES.LOLLIPOP);
    }

    public boolean supportsHDR()
    {
        // we also require the device have sufficient memory to do the processing, simplest to use the same test as we do for auto-stabilise...
        // also require at least Android 5, for the Renderscript support in HDRProcessor
        return (Build.VERSION.SDK_INT >= Build.VERSION_CODES.LOLLIPOP
                && (activityManager.getLargeMemoryClass() >= 128)
                && preview.supportsExpoBracketing());
    }

    public boolean supportsExpoBracketing()
    {
        return preview.supportsExpoBracketing();
    }

    public boolean supportsCamera2()
    {
        return this.supports_camera2;
    }

    public void requestCameraPermission()
    {
        if (MyDebug.LOG)
            Log.d(TAG, "requestCameraPermission");
        if (Build.VERSION.SDK_INT < Build.VERSION_CODES.M)
        {
            if (MyDebug.LOG)
                Log.e(TAG, "shouldn't be requesting permissions for pre-Android M!");
            return;
        }

        if (ActivityCompat.shouldShowRequestPermissionRationale(this, Manifest.permission.CAMERA)
                || ActivityCompat.shouldShowRequestPermissionRationale(this, Manifest.permission.WRITE_EXTERNAL_STORAGE))
        {
            // Show an explanation to the user *asynchronously* -- don't block
            // this thread waiting for the user's response! After the user
            // sees the explanation, try again to request the permission.
            showRequestPermissionRationale(MY_PERMISSIONS_REQUEST_CAMERA);
        } else
        {
            // Can go ahead and request the permission
            if (MyDebug.LOG)
                Log.d(TAG, "requesting camera permission...");
            ActivityCompat.requestPermissions(this, new String[]{Manifest.permission.CAMERA, Manifest.permission.WRITE_EXTERNAL_STORAGE}, MY_PERMISSIONS_REQUEST_CAMERA);
        }
    }

    @Override
    public void onRequestPermissionsResult(int requestCode, @NonNull String permissions[], @NonNull int[] grantResults)
    {
        if (MyDebug.LOG)
            Log.d(TAG, "onRequestPermissionsResult: requestCode " + requestCode);
        if (Build.VERSION.SDK_INT < Build.VERSION_CODES.M)
        {
            if (MyDebug.LOG)
                Log.e(TAG, "shouldn't be requesting permissions for pre-Android M!");
            return;
        }

        switch (requestCode)
        {
            case MY_PERMISSIONS_REQUEST_CAMERA:
            {
                // If request is cancelled, the result arrays are empty.
                if (grantResults.length > 0
                        && grantResults[0] == PackageManager.PERMISSION_GRANTED)
                {
                    // permission was granted, yay! Do the
                    // contacts-related task you need to do.
                    if (MyDebug.LOG)
                        Log.d(TAG, "camera permission granted");
//                    preview.retryOpenCamera();
                } else
                {
                    if (MyDebug.LOG)
                        Log.d(TAG, "camera permission denied");
                    // permission denied, boo! Disable the
                    // functionality that depends on this permission.
                    // Open Camera doesn't need to do anything: the camera will remain closed
                }
                return;
            }
            case MY_PERMISSIONS_REQUEST_RECORD_AUDIO:
            {
                // If request is cancelled, the result arrays are empty.
                if (grantResults.length > 0
                        && grantResults[0] == PackageManager.PERMISSION_GRANTED)
                {
                    // permission was granted, yay! Do the
                    // contacts-related task you need to do.
                    if (MyDebug.LOG)
                        Log.d(TAG, "record audio permission granted");
                    // no need to do anything
                } else
                {
                    if (MyDebug.LOG)
                        Log.d(TAG, "record audio permission denied");
                    // permission denied, boo! Disable the
                    // functionality that depends on this permission.
                    // no need to do anything
                    // note that we don't turn off record audio option, as user may then record video not realising audio won't be recorded - best to be explicit each time
                }
                return;
            }
            case MY_PERMISSIONS_REQUEST_LOCATION:
            {
                // If request is cancelled, the result arrays are empty.
                if (grantResults.length > 0
                        && grantResults[0] == PackageManager.PERMISSION_GRANTED)
                {
                    // permission was granted, yay! Do the
                    // contacts-related task you need to do.
                    if (MyDebug.LOG)
                        Log.d(TAG, "location permission granted");
                    initLocation();
                } else
                {
                    if (MyDebug.LOG)
                        Log.d(TAG, "location permission denied");
                    // permission denied, boo! Disable the
                    // functionality that depends on this permission.
                    // for location, seems best to turn the option back off
                    if (MyDebug.LOG)
                        Log.d(TAG, "location permission not available, so switch location off");
                    SharedPreferences settings = PreferenceManager.getDefaultSharedPreferences(this);
                    SharedPreferences.Editor editor = settings.edit();
                    editor.putBoolean(PreferenceKeys.getLocationPreferenceKey(), false);
                    editor.apply();
                }
                return;
            }
            default:
            {
                if (MyDebug.LOG)
                    Log.e(TAG, "unknown requestCode " + requestCode);
            }
        }
    }

    private void initialize(Bundle savedInstanceState)
    {
        long debug_time = 0;
        if (MyDebug.LOG)
        {
            Log.d(TAG, "initialize: " + this);
            debug_time = System.currentTimeMillis();
        }

        frameLayout = (FrameLayout) findViewById(R.id.camera_preview);

        tvEventLog = (TextView) findViewById(R.id.tvEventLog);
        tvEventLog.setMovementMethod(new ScrollingMovementMethod());

        Date d2 = new Date();
        CharSequence dateTime2 = DateFormat.format("HH:mm:ss", d2.getTime());
        tvEventLog.append("\nTime: " + dateTime2);

        tvStatusInfo = (TextView) findViewById(R.id.tvStatusInfo);
        tvStatusInfo.setText("idle");

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
                    beginImaging();

                    runButton.setText(getResources().getString(R.string.captureButton_text));
                    v.setTag(1);
                } else
                {
                    clickedTakePhoto();
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
                final int status = (Integer) runButton.getTag();

                if (status == 1)
                {
                    stopImaging();

                    runButton.setTag(0);
                    runButton.setText(getResources().getString(R.string.runButton_text));
                }
            }
        });

        sharedPref = PreferenceManager.getDefaultSharedPreferences(this);

        activityManager = (ActivityManager) getSystemService(ACTIVITY_SERVICE);

        applicationInterface = new MyApplicationInterface(this, savedInstanceState);
        if (MyDebug.LOG)
            Log.d(TAG, "onCreate: time after creating application interface: " + (System.currentTimeMillis() - debug_time));

        textFormatter = new TextFormatter(this);

        // determine whether we support Camera2 API
        initCamera2Support();

        // show "about" dialog for first time use; also set some per-device defaults
        boolean has_done_first_time = sharedPref.contains(PreferenceKeys.getFirstTimePreferenceKey());

        if (!has_done_first_time)
        {
            setDeviceDefaults();
            setFirstTimeFlag();
        }

        setSaveLocation();
    }

    /* This method sets the preference defaults which are set specific for a particular device.
     * This method should be called when Open Camera is run for the very first time after installation,
     * or when the user has requested to "Reset settings".
     */
    private void setDeviceDefaults()
    {
        if (MyDebug.LOG)
            Log.d(TAG, "setDeviceDefaults");
        boolean is_samsung = Build.MANUFACTURER.toLowerCase(Locale.US).contains("samsung");
        boolean is_oneplus = Build.MANUFACTURER.toLowerCase(Locale.US).contains("oneplus");

        if (MyDebug.LOG)
        {
            Log.d(TAG, "is_samsung? " + is_samsung);
            Log.d(TAG, "is_oneplus? " + is_oneplus);
        }
        if (is_samsung || is_oneplus)
        {
            // workaround needed for Samsung S7 at least (tested on Samsung RTL)
            // workaround needed for OnePlus 3 at least (see http://forum.xda-developers.com/oneplus-3/help/camera2-support-t3453103 )
            // update for v1.37: significant improvements have been made for standard flash and Camera2 API. But OnePlus 3T still has problem
            // that photos come out with a blue tinge if flash is on, and the scene is bright enough not to need it; Samsung devices also seem
            // to work okay, testing on S7 on RTL, but still keeping the fake flash mode in place for these devices, until we're sure of good
            // behaviour
            if (MyDebug.LOG)
                Log.d(TAG, "set fake flash for camera2");
            SharedPreferences.Editor editor = sharedPref.edit();
            editor.putBoolean(PreferenceKeys.getCamera2FakeFlashPreferenceKey(), true);
            editor.apply();
        }
    }

    private void setFirstTimeFlag()
    {
        if (MyDebug.LOG)
            Log.d(TAG, "setFirstTimeFlag");
        SharedPreferences.Editor editor = sharedPref.edit();
        editor.putBoolean(PreferenceKeys.getFirstTimePreferenceKey(), true);
        editor.apply();
    }

    private void checkPermissions()
    {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.M)
        {
            // Add permission for camera and let user grant the permission
            if (ContextCompat.checkSelfPermission(this, Manifest.permission.CAMERA) != PackageManager.PERMISSION_GRANTED &&
                    ContextCompat.checkSelfPermission(this, Manifest.permission.WRITE_EXTERNAL_STORAGE) != PackageManager.PERMISSION_GRANTED)
            {
                if (MyDebug.LOG)
                    Log.d(TAG, "camera permission not available");
                applicationInterface.requestCameraPermission();
                // return for now - the application should try to reopen the camera if permission is granted
                return;
            }
        }
    }

    /**
     * Determine whether we support Camera2 API.
     */
    private void initCamera2Support()
    {
        if (MyDebug.LOG)
            Log.d(TAG, "initCamera2Support");
        supports_camera2 = false;
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.LOLLIPOP)
        {
            CameraControllerManager2 manager2 = new CameraControllerManager2(this);
            supports_camera2 = true;
            if (manager2.getNumberOfCameras() == 0)
            {
                if (MyDebug.LOG)
                    Log.d(TAG, "Camera2 reports 0 cameras");
                supports_camera2 = false;
            }
            for (int i = 0; i < manager2.getNumberOfCameras() && supports_camera2; i++)
            {
                if (!manager2.allowCamera2Support(i))
                {
                    if (MyDebug.LOG)
                        Log.d(TAG, "camera " + i + " doesn't have limited or full support for Camera2 API");
                    supports_camera2 = false;
                }
            }
        }
        if (MyDebug.LOG)
            Log.d(TAG, "supports_camera2? " + supports_camera2);
    }

    private void requestLocationPermission()
    {
        if (MyDebug.LOG)
            Log.d(TAG, "requestLocationPermission");
        if (Build.VERSION.SDK_INT < Build.VERSION_CODES.M)
        {
            if (MyDebug.LOG)
                Log.e(TAG, "shouldn't be requesting permissions for pre-Android M!");
            return;
        }

        if (ActivityCompat.shouldShowRequestPermissionRationale(this, Manifest.permission.ACCESS_FINE_LOCATION) ||
                ActivityCompat.shouldShowRequestPermissionRationale(this, Manifest.permission.ACCESS_COARSE_LOCATION))
        {
            // Show an explanation to the user *asynchronously* -- don't block
            // this thread waiting for the user's response! After the user
            // sees the explanation, try again to request the permission.
            showRequestPermissionRationale(MY_PERMISSIONS_REQUEST_LOCATION);
        } else
        {
            // Can go ahead and request the permission
            if (MyDebug.LOG)
                Log.d(TAG, "requesting location permissions...");
            ActivityCompat.requestPermissions(this, new String[]{Manifest.permission.ACCESS_FINE_LOCATION, Manifest.permission.ACCESS_COARSE_LOCATION}, MY_PERMISSIONS_REQUEST_LOCATION);
        }
    }

    /**
     * Show a "rationale" to the user for needing a particular permission, then request that permission again
     * once they close the dialog.
     */
    @TargetApi(Build.VERSION_CODES.JELLY_BEAN_MR1)
    private void showRequestPermissionRationale(final int permission_code)
    {
        if (MyDebug.LOG)
            Log.d(TAG, "showRequestPermissionRational: " + permission_code);
        if (Build.VERSION.SDK_INT < Build.VERSION_CODES.M)
        {
            if (MyDebug.LOG)
                Log.e(TAG, "shouldn't be requesting permissions for pre-Android M!");
            return;
        }

        boolean ok = true;
        String[] permissions = null;
        int message_id = 0;
        if (permission_code == MY_PERMISSIONS_REQUEST_CAMERA)
        {
            if (MyDebug.LOG)
                Log.d(TAG, "display rationale for camera permission");
            permissions = new String[]{Manifest.permission.CAMERA, Manifest.permission.WRITE_EXTERNAL_STORAGE};
            message_id = R.string.permission_rationale_camera;
        } else if (permission_code == MY_PERMISSIONS_REQUEST_RECORD_AUDIO)
        {
            if (MyDebug.LOG)
                Log.d(TAG, "display rationale for record audio permission");
            permissions = new String[]{Manifest.permission.RECORD_AUDIO};
            message_id = R.string.permission_rationale_record_audio;
        } else if (permission_code == MY_PERMISSIONS_REQUEST_LOCATION)
        {
            if (MyDebug.LOG)
                Log.d(TAG, "display rationale for location permission");
            permissions = new String[]{Manifest.permission.ACCESS_FINE_LOCATION, Manifest.permission.ACCESS_COARSE_LOCATION};
            message_id = R.string.permission_rationale_location;
        } else
        {
            if (MyDebug.LOG)
                Log.e(TAG, "showRequestPermissionRational unknown permission_code: " + permission_code);
            ok = false;
        }

        if (ok)
        {
            final String[] permissions_f = permissions;
            new AlertDialog.Builder(this)
                    .setTitle(R.string.permission_rationale_title)
                    .setMessage(message_id)
                    .setIcon(android.R.drawable.ic_dialog_alert)
                    .setPositiveButton(android.R.string.ok, null)
                    .setOnDismissListener(new DialogInterface.OnDismissListener()
                    {
                        public void onDismiss(DialogInterface dialog)
                        {
                            if (MyDebug.LOG)
                                Log.d(TAG, "requesting permission...");
                            ActivityCompat.requestPermissions(MainActivity.this, permissions_f, permission_code);
                        }
                    }).show();
        }
    }

    private void initLocation()
    {
        if (MyDebug.LOG)
            Log.d(TAG, "initLocation");
        if (!applicationInterface.getLocationSupplier().setupLocationListener())
        {
            if (MyDebug.LOG)
                Log.d(TAG, "location permission not available, so request permission");
            requestLocationPermission();
        }
    }

    private void setSaveLocation()
    {
        SharedPreferences sharedPreferences = PreferenceManager.getDefaultSharedPreferences(this);
        String orig_save_location = this.applicationInterface.getStorageUtils().getSaveLocation();

        // make a new file directory inside the "sdcard" folder
        File mediaStorageDir = new File(Environment.getExternalStorageDirectory(), "WSI");

        // if folder could not be created
        if (!mediaStorageDir.exists())
        {
            if (!mediaStorageDir.mkdirs())
            {
                Log.d("WholeSkyImager", "failed to create directory");
            }
        }

        if( !orig_save_location.equals(mediaStorageDir.toString()) ) {
            if( MyDebug.LOG )
                Log.d(TAG, "changed save_folder to: " + this.applicationInterface.getStorageUtils().getSaveLocation());
            SharedPreferences.Editor editor = sharedPreferences.edit();
            editor.putString(PreferenceKeys.getSaveLocationPreferenceKey(), mediaStorageDir.toString());
            editor.apply();
        }
    }

    private void beginImaging()
    {
        // set up the camera and its preview
        preview = new Preview(applicationInterface, ((ViewGroup) findViewById(R.id.camera_preview)));
        preview.onResume();

        Log.d(TAG, "Imaging will begin in " + delayTime + " seconds");
        tvEventLog.append("\nImaging will begin in " + delayTime + " seconds");
    }

    private void stopImaging()
    {
        final int status = (Integer) runButton.getTag();

        if (status == 1)
        {
            preview.onPause();
            preview = null;

            runButton.setTag(0);
            runButton.setText(getResources().getString(R.string.runButton_text));

            Log.d(TAG, "Imaging Stopped");
            tvEventLog.append("\nImaging Stopped");
        }
    }

    private void clickedTakePhoto()
    {
        if (MyDebug.LOG)
            Log.d(TAG, "clickedTakePhoto");

        tvEventLog.append("\nCapturing Image");

        this.preview.takePicturePressed();
    }

    private void waitUntilImageQueueEmpty()
    {
        if (MyDebug.LOG)
            Log.d(TAG, "waitUntilImageQueueEmpty");
        applicationInterface.getImageSaver().waitUntilDone();

        final int status = (Integer) runButton.getTag();

        if (status == 1)
        {
            tvEventLog.append("\nImage Capture Done");
        }
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