package sg.edu.ntu.wholeskyimagerlegacy;

import android.Manifest;
import android.support.v4.app.ActivityCompat;
import android.support.v4.content.ContextCompat;
import android.support.v7.app.AppCompatActivity;

import android.os.Bundle;

import android.content.pm.PackageManager;

import android.view.View;
import android.widget.FrameLayout;
import android.widget.Toast;
import android.widget.Button;

import android.support.annotation.NonNull;

import android.support.v4.app.FragmentManager;
import android.support.v4.app.FragmentTransaction;

import android.util.Log;

import android.os.Handler;
import android.os.HandlerThread;

public class MainActivity extends AppCompatActivity
{
    private Camera camera;

    private Button runButton;
    private Button stopButton;

    private static final int REQUEST_CAMERA_PERMISSION = 200;

    private HandlerThread mBackgroundThread;

    private Handler mBackgroundHandler;

    private FrameLayout frameLayout;

    private static final String TAG = "WSIApp";

    private FragmentManager fragmentManager;

    private RunFragment runFragment;
    private CaptureFragment captureFragment;

    @Override
    protected void onCreate(Bundle savedInstanceState)
    {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_main);

        fragmentManager = getSupportFragmentManager();

        camera = new Camera(this);
        frameLayout = (FrameLayout) findViewById(R.id.camera_preview);

        runFragment = new RunFragment();
        captureFragment = new CaptureFragment();

        FragmentTransaction fragmentTransaction = fragmentManager.beginTransaction();
        fragmentTransaction.add(R.id.runCaptureFrame, runFragment, "runCaptureFragment");
        fragmentTransaction.commit();

//        runButton = (Button) findViewById(R.id.buttonRun);
//        assert runButton != null;
//        runButton.setOnClickListener(new View.OnClickListener()
//        {
//            @Override
//            public void onClick(View v)
//            {
//                camera.openCamera(frameLayout);
//            }
//        });

        stopButton = (Button) findViewById(R.id.buttonStop);
        assert stopButton != null;
        stopButton.setOnClickListener(new View.OnClickListener()
        {
            @Override
            public void onClick(View v)
            {
                camera.closeCamera(frameLayout);
                FragmentTransaction fragmentTransaction = fragmentManager.beginTransaction();
                fragmentTransaction.replace(R.id.runCaptureFrame, runFragment, "runFragment");
                fragmentTransaction.commit();
            }
        });

        // Add permission for camera and let user grant the permission
        if (ContextCompat.checkSelfPermission(this, Manifest.permission.CAMERA) != PackageManager.PERMISSION_GRANTED &&
                ContextCompat.checkSelfPermission(this, Manifest.permission.WRITE_EXTERNAL_STORAGE) != PackageManager.PERMISSION_GRANTED)
        {
            ActivityCompat.requestPermissions(this, new String[]{Manifest.permission.CAMERA, Manifest.permission.WRITE_EXTERNAL_STORAGE}, REQUEST_CAMERA_PERMISSION);
            return;
        }
    }

    protected void startBackgroundThread()
    {
        mBackgroundThread = new HandlerThread("Camera Background");
        mBackgroundThread.start();
        mBackgroundHandler = new Handler(mBackgroundThread.getLooper());
        camera.setHandler(mBackgroundHandler);
    }

    protected void stopBackgroundThread()
    {
        mBackgroundThread.quit();
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

    @Override
    public void onRequestPermissionsResult(int requestCode, @NonNull String[] permissions, @NonNull int[] grantResults)
    {
        if (requestCode == REQUEST_CAMERA_PERMISSION)
        {
            if (grantResults[0] == PackageManager.PERMISSION_DENIED)
            {
                // close the app
                Toast.makeText(MainActivity.this, "Sorry!!!, you can't use this app without granting permission", Toast.LENGTH_LONG).show();
                finish();
            }
        }
    }

    @Override
    public void onResume()
    {
        super.onResume();
        Log.e(TAG, "onResume");
        startBackgroundThread();
    }

    @Override
    public void onPause()
    {
        Log.e(TAG, "onPause");
        camera.closeCamera(frameLayout);
        stopBackgroundThread();
        super.onPause();
    }

    public Camera getCamera()
    {
        return this.camera;
    }

    public CaptureFragment getCaptureFragment()
    {
        return captureFragment;
    }

    public FrameLayout getFrameLayout()
    {
        return frameLayout;
    }
}
