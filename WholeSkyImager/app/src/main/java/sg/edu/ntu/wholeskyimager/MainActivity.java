package sg.edu.ntu.wholeskyimager;

import android.Manifest;

import android.content.pm.ActivityInfo;

import android.graphics.drawable.ColorDrawable;
import android.os.Build;
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

public class MainActivity extends AppCompatActivity
{
    private Camera camera = null;
    private Button runButton = null;
    private Button stopButton = null;
    private final int REQUEST_CAMERA_PERMISSION = 200;
    private final int wahrsisModelNr = 6;
    private HandlerThread mBackgroundThread = null;
    private Handler mBackgroundHandler = null;
    private Handler imagingHandler = null;
    private FrameLayout frameLayout = null;
    private TextView tvEventLog = null;
    private final String TAG = "WSIApp";

    private int delayTime = 5;
    private int pictureInterval = 1;

    @Override
    protected void onCreate(Bundle savedInstanceState)
    {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_main);
        setRequestedOrientation(ActivityInfo.SCREEN_ORIENTATION_PORTRAIT);

        setupActionBar();

        initialize();

        checkPermissions();
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
        camera.closeCamera(frameLayout);
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

    private void startBackgroundThread()
    {
        mBackgroundThread = new HandlerThread("Camera Background");
        mBackgroundThread.start();
        mBackgroundHandler = new Handler(mBackgroundThread.getLooper());
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
                final int status = (Integer) runButton.getTag();

                if (status == 1)
                {
                    imagingHandler.removeCallbacks(imagingRunnable);

                    Log.d(TAG, "Imaging Stopped");
                    tvEventLog.append("\nImaging Stopped");

                    camera.closeCamera(frameLayout);
                    runButton.setTag(0);
                    runButton.setText(getResources().getString(R.string.runButton_text));
                }
            }
        });
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
        imagingHandler = new Handler();

        imagingHandler.postDelayed(imagingRunnable, delayTime * 1000);

        Log.d(TAG, "Imaging will begin in " + delayTime + " seconds");
        tvEventLog.append("\nImaging will begin in " + delayTime + " seconds");
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
            imagingHandler.postDelayed(this, pictureInterval * 60 * 1000);
        }
    };
}