package sg.edu.ntu.wholeskyimagerlegacy;

import android.Manifest;
import android.content.pm.PackageManager;

import android.hardware.Camera;
import android.hardware.Camera.PictureCallback;

import android.os.Environment;

import android.os.Handler;
import android.support.v4.content.ContextCompat;
import android.support.v4.app.ActivityCompat;
import android.support.annotation.Nullable;

import android.util.Log;

import java.util.List;
import java.util.Date;

import android.widget.FrameLayout;

import java.text.SimpleDateFormat;

import android.graphics.Bitmap;
import android.graphics.BitmapFactory;

import android.widget.TextView;
import android.widget.Toast;

import java.io.File;
import java.io.FileNotFoundException;
import java.io.FileOutputStream;
import java.io.IOException;

@SuppressWarnings("deprecation")
public class CameraOperator
{
    private Camera camera = null;
    private MainActivity mainActivity = null;
    private ImageSurfaceView mImageSurfaceView = null;
    private int wahrsisModelNr = -1;
    private TextView tvEventLog = null;
    private boolean afEnabled = true;
    private final String TAG = this.getClass().getName();
    private int pictureCounter = 0;
    private boolean createHDR = false;
    private String[] evState = {"med", "low", "high"};
    private int minExposureComp, maxExposureComp;
    private String timeStampNew = null;
    private String timeStampOld = null;
    private boolean keepImages = true;

    private Camera.Parameters params = null;

    private WSIServerClient serverClient = null;

    public CameraOperator(MainActivity activity)
    {
        this.mainActivity = activity;
        this.wahrsisModelNr = mainActivity.getWahrsisModelNr();
        this.tvEventLog = mainActivity.getTvEventLog();
        this.serverClient = mainActivity.getServerClient();
        this.createHDR = mainActivity.getCreateHDR();
    }

    public void openCamera(FrameLayout frameLayout)
    {
        int REQUEST_CAMERA_PERMISSION = mainActivity.getRequestCameraPermission();

        try
        {
            if (ContextCompat.checkSelfPermission(mainActivity, Manifest.permission.CAMERA) == PackageManager.PERMISSION_GRANTED &&
                    ContextCompat.checkSelfPermission(mainActivity, Manifest.permission.WRITE_EXTERNAL_STORAGE) == PackageManager.PERMISSION_GRANTED)
            {
                this.camera = checkDeviceCamera();
            } else
            {
                ActivityCompat.requestPermissions(mainActivity, new String[]{Manifest.permission.CAMERA, Manifest.permission.WRITE_EXTERNAL_STORAGE}, REQUEST_CAMERA_PERMISSION);
                return;
            }

            mImageSurfaceView = new ImageSurfaceView(mainActivity, camera);
            assert mImageSurfaceView != null;

            frameLayout.addView(mImageSurfaceView);

            Log.d(TAG, "Camera Opened");
            tvEventLog.append("\nCamera Opened");

            this.createHDR = mainActivity.getCreateHDR();

            pictureCounter = 0;
        } catch (Exception e)
        {
            Log.e(TAG, "Open Camera Failed");
            tvEventLog.append("\nOpen Camera Failed");
        }
    }

    public void closeCamera(FrameLayout frameLayout)
    {
        frameLayout.removeView(mImageSurfaceView);
        mImageSurfaceView = null;

        Log.d(TAG, "Camera Closed");
        tvEventLog.append("\nCamera Closed");
    }

    public void runImagingTask()
    {
        keepImages = mainActivity.getKeepImages();

        timeStampOld = timeStampNew;

        Log.d(TAG, "Taking Picture");
        tvEventLog.append("\nTaking Picture");

        //max value: +12, step size: exposure-compensation-step=0.166667. EV: +2
        maxExposureComp = params.getMaxExposureCompensation();
        minExposureComp = params.getMinExposureCompensation();

        timeStampNew = new SimpleDateFormat("yyyy-MM-dd-HH-mm-ss").format(new Date());

        if (timeStampOld != null && !keepImages)
        {
            if (!keepImages)
            {
                String filePath = Environment.getExternalStorageDirectory().getPath() + "/WSI/";
                File imageFileLow = new File(filePath + timeStampOld + "-wahrsis" + wahrsisModelNr + "-low" + ".jpg");
                File imageFileMed = new File(filePath + timeStampOld + "-wahrsis" + wahrsisModelNr + "-med" + ".jpg");
                File imageFileHigh = new File(filePath + timeStampOld + "-wahrsis" + wahrsisModelNr + "-high" + ".jpg");
                imageFileLow.delete();
                imageFileMed.delete();
                boolean statusDelete = imageFileHigh.delete();
                Log.d(TAG, "Deleting successfull: " + statusDelete);
            }
        }

        takePicture();
        Log.d(TAG, "Pictures successfully taken");
        tvEventLog.append("\nPictures successfully taken");
    }

    public void takePicture()
    {
        try
        {
            camera.takePicture(null, null, pictureCallback);
        } catch (Exception e)
        {
            Log.e(TAG, "Take Picture Failed");
            tvEventLog.append("\nTake Picture Failed");
        }
    }

    private Camera checkDeviceCamera()
    {
        Camera mCamera = null;

        try
        {
            mCamera = Camera.open();

            assert mCamera != null;

            afEnabled = mainActivity.getAFEnabled();

            setCameraParameters(mCamera);
        } catch (Exception e)
        {
            Log.e(TAG, "Check Device Camera Failed");
            tvEventLog.append("\nCheck Device Camera Failed");
        }

        return mCamera;
    }

    private void setCameraParameters(Camera mCamera)
    {
        params = mCamera.getParameters();

        //set highest resolution as output
        List<Camera.Size> sizes = params.getSupportedPictureSizes();
        Camera.Size size = sizes.get(0);
        //find largest size
        for (int i = 0; i < sizes.size(); i++)
        {
            if (sizes.get(i).width > size.width)
            {
                size = sizes.get(i);
            }
        }

        params.setPictureSize(size.width, size.height);

        //check if Focus mode infinity is available and set it
        List<String> focusModes = params.getSupportedFocusModes();

        if (afEnabled)
        {
            if (focusModes.contains(Camera.Parameters.FOCUS_MODE_CONTINUOUS_PICTURE))
            {
                params.setFocusMode(Camera.Parameters.FOCUS_MODE_CONTINUOUS_PICTURE);
                tvEventLog.append("\nPreviewing AF");
            }
        } else
        {
            if (focusModes.contains(Camera.Parameters.FOCUS_MODE_INFINITY))
            {
                params.setFocusMode(Camera.Parameters.FOCUS_MODE_INFINITY);
                tvEventLog.append("\nPreviewing Manual");
            }
        }

        params.set("mode", "m");
        params.set("iso", "ISO100");
//        params.setWhiteBalance(Camera.Parameters.WHITE_BALANCE_AUTO);
//        params.setSceneMode(Camera.Parameters.SCENE_MODE_HDR);
        params.setRotation(90);
        params.setJpegQuality(100);
        params.setFlashMode(Camera.Parameters.FLASH_MODE_OFF);

        // save settings
        mCamera.setParameters(params);
    }

    PictureCallback pictureCallback = new PictureCallback()
    {
        Handler handler = new Handler();

        @Override
        public void onPictureTaken(byte[] data, final Camera camera)
        {
            Bitmap bitmap = BitmapFactory.decodeByteArray(data, 0, data.length);
            if (bitmap == null)
            {
                Toast.makeText(mainActivity, "Captured image is empty", Toast.LENGTH_LONG).show();
                return;
            }

            //actual image file (jpg): pictureFile
            //naming convention: YYYY-MM-DD-HH-MM-SS-wahrsisN.jpg eg. 2016-11-22-14-20-01-wahrsis5.jpg
            if (!createHDR)
            {
                Log.d(TAG, "evState: normal");
                String fileName = timeStampNew + "-wahrsis" + wahrsisModelNr + ".jpg";

                File pictureFile = getOutputMediaFile(fileName);

                if (pictureFile == null)
                {
                    return;
                }

                try
                {
                    FileOutputStream fos = new FileOutputStream(pictureFile);
                    fos.write(data);
                    fos.close();
                    Log.d(TAG, "Save successful: " + pictureFile.getName());
                    tvEventLog.append("\nSave successful: " + pictureFile.getName());
                } catch (FileNotFoundException e)
                {
                    Log.e(TAG, "Save failed.");
                    tvEventLog.append("\nSave failed.");
                } catch (IOException e)
                {
                    Log.e(TAG, "Save failed.");
                    tvEventLog.append("\nSave failed.");
                }

                mImageSurfaceView.refreshCamera();

                if (serverClient.isConnected())
                {
                    // post image series (Low, Med, High EV) to specific URL and receive HTTP Status Code
                    serverClient.httpPOST(timeStampNew, wahrsisModelNr);
                } else
                {
                    Log.e(TAG, "POST Execution not possible. No connection to internet.");
                }
                Log.d(TAG, "Finished taking image.");
            } else
            {
                Log.d(TAG, "evState: " + evState[pictureCounter]);
                String fileName = timeStampNew + "-wahrsis" + wahrsisModelNr + "-" + evState[pictureCounter] + ".jpg";

                File pictureFile = getOutputMediaFile(fileName);

                if (pictureFile == null)
                {
                    return;
                }

                try
                {
                    FileOutputStream fos = new FileOutputStream(pictureFile);
                    fos.write(data);
                    fos.close();
                    Log.d(TAG, "Save successful: " + pictureFile.getName());
                    tvEventLog.append("\nSave successful: " + pictureFile.getName());
                } catch (FileNotFoundException e)
                {
                    Log.e(TAG, "Save failed.");
                    tvEventLog.append("\nSave failed.");
                } catch (IOException e)
                {
                    Log.e(TAG, "Save failed.");
                    tvEventLog.append("\nSave failed.");
                }

                mImageSurfaceView.refreshCamera();

                pictureCounter++;

                if (pictureCounter < 3)
                {
                    switch (pictureCounter)
                    {
                        case 1:
                            params.setExposureCompensation(minExposureComp);
                            camera.setParameters(params);

                            handler.postDelayed(new Runnable()
                            {
                                @Override
                                public void run()
                                {
//                                    if( MyDebug.LOG )
                                    Log.d(TAG, "take picture after delay for next expo");
                                    if (camera != null)
                                    { // make sure camera wasn't released in the meantime
                                        camera.takePicture(null, null, pictureCallback);
                                    }
                                }
                            }, 1000);

                            break;
                        case 2:
                            params.setExposureCompensation(maxExposureComp);
                            camera.setParameters(params);

                            handler.postDelayed(new Runnable()
                            {
                                @Override
                                public void run()
                                {
//                                    if( MyDebug.LOG )
                                    Log.d(TAG, "take picture after delay for next expo");
                                    if (camera != null)
                                    { // make sure camera wasn't released in the meantime
                                        camera.takePicture(null, null, pictureCallback);
                                    }
                                }
                            }, 1000);
                            break;
                    }
                } else
                {
                    pictureCounter = 0;

                    params.setExposureCompensation(0);
                    camera.setParameters(params);

                    if (serverClient.isConnected())
                    {
                        // post image series (Low, Med, High EV) to specific URL and receive HTTP Status Code
                        serverClient.httpPOST(timeStampNew, wahrsisModelNr);
                    } else
                    {
                        Log.e(TAG, "POST Execution not possible. No connection to internet.");
                    }
                    Log.d(TAG, "Finished taking low, med, high images. Reset counter.");
                }
            }
        }
    };

    @Nullable // this denotes that the method might legitimately return null
    private static File getOutputMediaFile(String fileName)
    {
        // make a new file directory inside the "sdcard" folder
        File mediaStorageDir = new File(Environment.getExternalStorageDirectory(), "WSI");

        // if folder could not be created
        if (!mediaStorageDir.exists())
        {
            if (!mediaStorageDir.mkdirs())
            {
                Log.d("WholeSkyImager", "failed to create directory");
                return null;
            }
        }
        //naming convention: YYYY-MM-DD-HH-MM-SS-wahrsisN.jpg eg. 2016-11-22-14-20-01-wahrsis5.jpg
        //take the current timeStamp
//        String timeStamp = new SimpleDateFormat("yyyy-MM-dd-HH-mm-ss").format(new Date());
//        String complete = timeStamp.concat("-wahrsis" + wahrsisModelNr + ".jpg");
//        File mediaFile;
        //and make a media file:
        File mediaFile = new File(mediaStorageDir.getPath() + File.separator + fileName);

        return mediaFile;
    }
}