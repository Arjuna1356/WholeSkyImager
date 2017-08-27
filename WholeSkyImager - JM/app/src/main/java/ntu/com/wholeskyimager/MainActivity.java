/**
 * @author Julian Mueller
 * Version 0.3.0
 * added Compass / Accelerometer functionality
 */
/**
 * This code hase been created by Julian Mueller at the Nanyang Technological University, Singapore
 * as part of the Bachelor's Degree in Electrical Engineering.
 * Contact: jul.mueller@gmx.ch
 */
package ntu.com.wholeskyimager;

import android.app.Notification;
import android.app.NotificationManager;
import android.app.PendingIntent;
import android.content.Intent;
import android.content.SharedPreferences;
import android.content.pm.ActivityInfo;
import android.graphics.Bitmap;
import android.graphics.BitmapFactory;
import android.graphics.Color;
import android.graphics.Matrix;
import android.graphics.drawable.ColorDrawable;
import android.graphics.drawable.Drawable;
import android.hardware.Camera;
import android.hardware.Camera.PictureCallback;
import android.hardware.Sensor;
import android.hardware.SensorEvent;
import android.hardware.SensorEventListener;
import android.hardware.SensorManager;
import android.location.Criteria;
import android.location.Location;
import android.location.LocationListener;
import android.location.LocationManager;
import android.net.Uri;
import android.os.Build;
import android.os.Bundle;
import android.os.Environment;
import android.os.Handler;
import android.preference.PreferenceManager;
import android.provider.Settings;
import android.support.annotation.Nullable;
import android.support.v4.content.ContextCompat;
import android.support.v7.app.AppCompatActivity;
import android.support.v7.widget.Toolbar;
import android.text.Html;
import android.text.format.DateFormat;
import android.text.method.ScrollingMovementMethod;
import android.util.Log;
import android.view.Menu;
import android.view.MenuItem;
import android.view.View;
import android.view.Window;
import android.view.WindowManager;
import android.widget.FrameLayout;
import android.widget.ImageView;
import android.widget.TextView;
import android.widget.Toast;

import com.google.android.gms.appindexing.Action;
import com.google.android.gms.appindexing.AppIndex;
import com.google.android.gms.appindexing.Thing;
import com.google.android.gms.common.api.GoogleApiClient;

import org.opencv.android.OpenCVLoader;

import java.io.File;
import java.io.FileNotFoundException;
import java.io.FileOutputStream;
import java.io.IOException;
import java.text.SimpleDateFormat;
import java.util.Date;
import java.util.List;

import it.sephiroth.android.library.exif2.ExifInterface;
import it.sephiroth.android.library.exif2.ExifTag;

import static android.util.Log.d;
import static it.sephiroth.android.library.exif2.ExifInterface.TAG_GPS_IMG_DIRECTION;

//import android.media.ExifInterface;

@SuppressWarnings("deprecation")
public class MainActivity extends AppCompatActivity implements SensorEventListener {
    private boolean uploadFinished = false;
    private final String TAG = this.getClass().getName(); //gets the name of the current class eg. "MyActivity".
    private static int wahrsisModelNr = 5;
    private int pictureInterval = 0;
    private boolean sPreviewing, flagWriteExif = true, flagRealignImage = false, flagCamReady = true, flagWaitForMinute = true;
    private boolean flagUploadImages = true, flagDeleteImages = true;
    protected TextView mainLabel, tvConnectionStatus, tvStatusInfo, tvEventLog;
    protected ImageView outputImage;
    SharedPreferences sharedPref;
    private ImageSurfaceView mImageSurfaceView;
    private Camera camera;
    public String[] evState = {"low","med","high"};
    private String timeStampNew = null, timeStampOld = null;
    private int pictureCounter = 0;
    private int maxExposureComp, minExposureComp;
    Camera.Parameters params;

    public static final String MWSIPreferences = "MyPrefs" ;
    private String authorizationToken;
    WSIServerClient serverClient;

    private boolean hdrModeOn, connectionStatus, flagRunImaging = false;
    private int exposureCompensationValue;
    private FrameLayout cameraPreviewLayout;

    //Sensor components
    private SensorManager mSensorManager;
    private Sensor mSensor;
    private Sensor magnetometer;
    private Sensor accelerometer;
    private float[] mGravity;
    private float[] mGeomagnetic;
    private float azimuth;

    private String gpsLong, gpsLat, gravityString;
    private double longitude, latitude;
    private int updateCounter = 0;

    private LocationManager locationManager;
    private Location location;
    private String provider;

    /**
     * ATTENTION: This was auto-generated to implement the App Indexing API.
     * See https://g.co/AppIndexing/AndroidStudio for more information.
     */
    private GoogleApiClient mClient;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_main);
        setRequestedOrientation(ActivityInfo.SCREEN_ORIENTATION_PORTRAIT);

        setupActionBar();

        outputImage = (ImageView) findViewById(R.id.imageOutput);
        tvConnectionStatus  = (TextView) findViewById(R.id.tvConnectionStatus);
        tvStatusInfo = (TextView) findViewById(R.id.tvStatusInfo);
        tvEventLog = (TextView) findViewById(R.id.tvEventLog);
        tvEventLog.setMovementMethod(new ScrollingMovementMethod());

        Date d2 = new Date();
        CharSequence dateTime2 = DateFormat.format("HH:mm:ss", d2.getTime());
        tvEventLog.append("\nTime: " + dateTime2);
        camera = checkDeviceCamera();

        mImageSurfaceView = new ImageSurfaceView(MainActivity.this, camera);
        cameraPreviewLayout = (FrameLayout) findViewById(R.id.camera_preview);
        cameraPreviewLayout.addView(mImageSurfaceView);

        NotificationManager notificationManager = (NotificationManager)getSystemService(NOTIFICATION_SERVICE);

        // prepare intent which is triggered if the notification is selected
        Intent intent = new Intent(this, MainActivity.class);
        PendingIntent pIntent = PendingIntent.getActivity(this, (int) System.currentTimeMillis(), intent, 0);

        // build notification
        Notification notificationUploadStarted  = new Notification.Builder(this)
                .setContentTitle("Upload started")
                .setContentText("Waiting for response code...")
                .setSmallIcon(R.drawable.ic_sync_black_24dp)
                .setContentIntent(pIntent)
                .setAutoCancel(true).build();

        // Check if OpenCV works properly
        if (!OpenCVLoader.initDebug()) {
            Log.e(this.getClass().getSimpleName(), "  OpenCVLoader.initDebug(), not working.");
            tvEventLog.append("\nOpenCVLoader.initDebug(), not working.");

        } else {
            d(this.getClass().getSimpleName(), "  OpenCVLoader.initDebug(), working.");
            tvEventLog.append("\nOpenCVLoader.initDebug(), working.");
        }

        // fetch and set settings
        getWSISettings();

        // initiate server client
        serverClient = new WSIServerClient(this, "https://www.visuo.adsc.com.sg/api/skypicture/", authorizationToken);
        checkNetworkStatus();
        instantiateGPS();
        instantiateSensors();
        tvStatusInfo.setText("idle");

        final Handler handler = new Handler();
        final Runnable runnable = new Runnable() {
            @Override
            public void run() {
                try{
                    if (flagWaitForMinute) {
                        Date d = new Date();
                        d.getTime();
                        int seconds = d.getSeconds();
                        Log.d(TAG, "seconds: " + seconds);
                        if (seconds == 0 && flagRunImaging) {
                            flagWaitForMinute = false;
                        }
                    }

                    if(!flagWaitForMinute && flagRunImaging) {
                        Date d = new Date();
                        CharSequence dateTime = DateFormat.format("yyyy-MM-dd hh:mm:ss", d.getTime());
                        d(TAG, "Runnable execution started. Time: " + dateTime + ". Interval: " + pictureInterval + " min.");
                        tvEventLog.append("\nRunnable execution started. Time: " + dateTime + ". Interval: " + pictureInterval + " min.");
                        runImagingTask();
                    }
                }
                catch (Exception e) {
                    // TODO: handle exception
                    d(TAG, "Error: Runnable exception.");
                }
                finally{
                    //also call the same runnable to call it at regular interval
                }
                if (!flagWaitForMinute && flagRunImaging) {
                    handler.postDelayed(this, pictureInterval * 60 * 1000);
                }
                else if (flagWaitForMinute){
                    //wait until full minute (eg. 12:16:00) before capturing images.
                    handler.postDelayed(this, 1000);
                }
            }
        };
        handler.post(runnable);

        // ATTENTION: This was auto-generated to implement the App Indexing API.
        // See https://g.co/AppIndexing/AndroidStudio for more information.
        mClient = new GoogleApiClient.Builder(this).addApi(AppIndex.API).build();
    }

    @Override
    public boolean onCreateOptionsMenu(Menu menu) {
        // Inflate the menu; this adds items to the action bar if it is present.
        getMenuInflater().inflate(R.menu.menu, menu);

        return true;
    }

    // get picture data (no writing)
    @Override
    public boolean onOptionsItemSelected(MenuItem item) {
        // Handle action bar item clicks here. The action bar will
        // automatically handle clicks on the Home/Up button, so long
        // as you specify a parent activity in AndroidManifest.xml.
        int id = item.getItemId();
        switch (item.getItemId()) {
            case R.id.action_settings:
                Toast.makeText(MainActivity.this, "SETTINGS", Toast.LENGTH_SHORT).show();
                Intent intentSettings = new Intent(this, SettingsActivity.class);
                startActivity(intentSettings);
                return true;

            case R.id.action_refresh:
                getWSISettings();
                checkNetworkStatus();
                Toast.makeText(MainActivity.this, "Refreshed Settings", Toast.LENGTH_SHORT).show();
                return true;

            case R.id.action_help:
                Toast.makeText(MainActivity.this, "HELP", Toast.LENGTH_SHORT).show();
                return true;

            case R.id.action_about:
                Toast.makeText(MainActivity.this, "ABOUT", Toast.LENGTH_SHORT).show();
                Intent intentAbout = new Intent(this, DisplayAboutActivity.class);
                startActivity(intentAbout);
                return true;

            default:
                return super.onOptionsItemSelected(item);
        }
    }

    // Picture Callback Method
    PictureCallback pictureCallback = new PictureCallback() {
        @Override
        //action if picture is taken
        public void onPictureTaken(byte[] data, Camera camera) {

            Bitmap bitmap = BitmapFactory.decodeByteArray(data, 0, data.length);
            Bitmap bitmapRotated = null;
            if (bitmap == null) {
                Toast.makeText(MainActivity.this, "Captured image is empty", Toast.LENGTH_LONG).show();
                tvEventLog.append("\nCaptured image is empty.");
                return;
            }

            //actual image file (jpg): pictureFile
            //naming convention: YYYY-MM-DD-HH-MM-SS-wahrsisN.jpg eg. 2016-11-22-14-20-01-wahrsis5.jpg
            //take the current timeStamp
//            String ending = "temp";
            Log.d(TAG, "evState: " + evState[pictureCounter]);
            String fileName = timeStampNew + "-wahrsis" + wahrsisModelNr + "-" + evState[pictureCounter] + ".jpg";
            String fileNameRotated = timeStampNew + "-wahrsis" + wahrsisModelNr + "-" + evState[pictureCounter] + "-rotated" + ".jpg";

            File pictureFile = getOutputMediaFile(fileName);
            File pictureFileRotated = getOutputMediaFile(fileNameRotated);
            String filePath = Environment.getExternalStorageDirectory().getPath() + "/WSI/";

            if (pictureFile == null) {
                return;
            }
            try {
                FileOutputStream fos = new FileOutputStream(pictureFile);
                fos.write(data);
                fos.close();
                Log.d(TAG, "Save successful: " + pictureFile.getName());
                tvEventLog.append("\nSave successful: " + pictureFile.getName());
//                    Toast toast = Toast.makeText(MainActivity.this, "Original picture saved: " + pictureFile.getName(), Toast.LENGTH_LONG);
//                    toast.show();
                if (flagRealignImage) {
                    //rotate image according to compass direction
                    bitmapRotated = RotateBitmap(bitmap, azimuth);
                    FileOutputStream fos2 = new FileOutputStream(pictureFileRotated);
                    bitmapRotated.compress(Bitmap.CompressFormat.JPEG, 100, fos2);
                    // write the file
                    fos.close();
                    Log.d(TAG, "Save successful (rotated): " + pictureFileRotated.getName());
                    Log.d(TAG, filePath + fileNameRotated);
                    copyExif(pictureFile.getAbsolutePath(), pictureFileRotated.getAbsolutePath());
                }
            } catch (FileNotFoundException e) {
                Log.e(TAG, "Save failed.");
                tvEventLog.append("\nSave failed.");
            } catch (IOException e) {
                Log.e(TAG, "Save failed.");
                tvEventLog.append("\nSave failed.");
            }
            if (mImageSurfaceView.getPreviewState()) {
                mImageSurfaceView.refreshCamera();
            }

            pictureCounter++;
            if (pictureCounter < 3){
                switch (pictureCounter) {
                    case 1:
                        params.setExposureCompensation(minExposureComp);
                        camera.setParameters(params);
                        camera.takePicture(null, null, pictureCallback);
                        break;
                    case 2:
                        params.setExposureCompensation(minExposureComp);
                        camera.setParameters(params);
                        camera.takePicture(null, null, pictureCallback);
                        break;
                    case 3:
                        params.setExposureCompensation(maxExposureComp);
                        camera.setParameters(params);
                        camera.takePicture(null, null, pictureCallback);
                        break;
                }
            }
            else {
                pictureCounter = 0;
                if(flagUploadImages) {
                    if (serverClient.isConnected()) {
                        // post image series (Low, Med, High EV) to specific URL and receive HTTP Status Code
                        int responseCode = serverClient.httpPOST(timeStampNew, wahrsisModelNr);
                        d(TAG, "POST execution finished. Response code: " + responseCode);
                        if (responseCode == 201) {
                            tvStatusInfo.setText("Image uploaded.");
                        }
                    } else {
                        d(TAG, "POST Execution not possible. No connection to internet.");
                    }
                    Log.d(TAG, "Finished taking low, med, high images. Reset counter.");
                }
            }
        }
    };

    @Nullable // this denotes that the method might legitimately return null
    private static File getOutputMediaFile(String fileName) {
        // make a new file directory inside the "sdcard" folder
        File mediaStorageDir = new File("/sdcard/", "WSI");

        // if folder could not be created
        if (!mediaStorageDir.exists()) {
            if (!mediaStorageDir.mkdirs()) {
                d("WholeSkyImager", "failed to create directory");
                return null;
            }
        }
        //naming convention: YYYY-MM-DD-HH-MM-SS-wahrsisN.jpg eg. 2016-11-22-14-20-01-wahrsis5.jpg
        //take the current timeStamp
//        String timeStamp = new SimpleDateFormat("yyyy-MM-dd-HH-mm-ss").format(new Date());
//        String complete = timeStamp.concat("-wahrsis" + wahrsisModelNr + ".jpg");
        File mediaFile;
        //and make a media file:
        mediaFile = new File(mediaStorageDir.getPath() + File.separator + fileName);

        return mediaFile;
    }


    // check if camera is available and use back facing camera
    /**
     * Check avaiablity of camera
     *
     * @return Camera
     */
    @SuppressWarnings("deprecation")
    private Camera checkDeviceCamera() {
        int cameraId = findBackFacingCamera();
        Camera mCamera = null;
        try {
            mCamera = Camera.open(cameraId);  //try to open camera

            Camera.Parameters params = mCamera.getParameters();

            //set highest resolution as output
            List<Camera.Size> sizes = params.getSupportedPictureSizes();
            Camera.Size size = sizes.get(0);
            //Camera.Size size1 = sizes.get(0);
            //find largest size
            for (int i = 0; i < sizes.size(); i++) {

                if (sizes.get(i).width > size.width)
                    size = sizes.get(i);
            }
            params.setPictureSize(size.width, size.height);

            //check if Focus mode infinity is available and set it
            List<String> focusModes = params.getSupportedFocusModes();
            if (focusModes.contains(Camera.Parameters.FOCUS_MODE_INFINITY)) {
                params.setFocusMode(Camera.Parameters.FOCUS_MODE_INFINITY);
            }
            params.setSceneMode(Camera.Parameters.SCENE_MODE_HDR);
            params.setRotation(90);
            params.setJpegQuality(100);
            params.setWhiteBalance(Camera.Parameters.WHITE_BALANCE_AUTO);
            params.setFlashMode(Camera.Parameters.FLASH_MODE_OFF);

            // save settings
            mCamera.setParameters(params);
            d(this.getClass().getSimpleName(), "Camera started successfully.");
            tvEventLog.append("\nCamera started successfully.");
        } catch (Exception e) {
            e.printStackTrace(); //show error if camera can't be accessed
            Log.e(this.getClass().getSimpleName(), "Could not start camera");
        }
        return mCamera;
    }

    /**
     * Find back facing camera
     * @return cameraId
     */
    @SuppressWarnings("deprecation")
    private int findBackFacingCamera() {
        int cameraId = -1;
        //Search for the back facing camera
        //get the number of cameras
        int numberOfCameras = Camera.getNumberOfCameras();
        //for every camera check
        for (int i = 0; i < numberOfCameras; i++) {
            Camera.CameraInfo info = new Camera.CameraInfo();
            Camera.getCameraInfo(i, info);
            if (info.facing == Camera.CameraInfo.CAMERA_FACING_BACK) {
                cameraId = i;
                break;
            }
        }
        return cameraId;
    }

    /**
     * HVA Method
     * @return horizontalViewAngle
     */
    public double getHVA() {
        return camera.getParameters().getHorizontalViewAngle();
    }

    /**
     * VVA Method
     * @return verticalViewAngle
     */
    public double getVVA() {
        return camera.getParameters().getVerticalViewAngle();
    }

    private Bitmap scaleDownBitmapImage(Bitmap bitmap, int newWidth, int newHeight) {
        Bitmap resizedBitmap = Bitmap.createScaledBitmap(bitmap, newWidth, newHeight, true);
        return resizedBitmap;
    }

    //button method
    public void startEdgeDetection(View view) {
        if (updateCounter > 10) {

        }
    }

    /**
     * Main method to take pictures
     */
    public void runImagingTask() {
        timeStampOld = timeStampNew;
        //do something
        Log.d("Button Pressed", "Image loading should be started!");
        tvEventLog.append("\nImage loading should be started.");
        //check the current state before we display the screen
        params = camera.getParameters();

        //max value: +12, step size: exposure-compensation-step=0.166667. EV: +2
        maxExposureComp = params.getMaxExposureCompensation();
        minExposureComp = params.getMinExposureCompensation();

        timeStampNew = new SimpleDateFormat("yyyy-MM-dd-HH-mm-ss").format(new Date());

        if(timeStampOld != null && flagDeleteImages) {
            //TODO: delte all files
            if (flagDeleteImages) {
                String filePath = Environment.getExternalStorageDirectory().getPath() + "/WSI/";
                File imageFileLow = new File(filePath+timeStampOld+"-wahrsis" + wahrsisModelNr + "-low" + ".jpg");
                File imageFileMed = new File(filePath+timeStampOld+"-wahrsis" + wahrsisModelNr + "-med" + ".jpg");
                File imageFileHigh = new File(filePath+timeStampOld+"-wahrsis" + wahrsisModelNr + "-high" + ".jpg");
                imageFileLow.delete();
                imageFileMed.delete();
                boolean statusDelete = imageFileHigh.delete();
                Log.d(TAG, "Deleting successfull: " + statusDelete);
            }
        }

        params.set("mode", "m");
        params.set("iso", "ISO100");

//        camera.stopPreview();
        camera.takePicture(null, null, pictureCallback);
        Log.d(TAG, "Pictures successfully taken and uploaded.");
    }

    /**
     * Button Method Upload Image
     */
    public void stopImaging(View view) {
        flagWaitForMinute = true;
        flagRunImaging = false;
        Log.d(TAG, "Imaging process stopped.");
        tvEventLog.append("\nImaging deactivated.");
    }

    /**
     * Button Method Take Picture
     */
    @SuppressWarnings("deprecation")
    public void startImaging(View view) throws InterruptedException {
        flagRunImaging = true;
        Log.d(TAG, "Imaging process will start soon. Runflag: " + flagRunImaging + ", waitForMinute: " + flagWaitForMinute);
        tvEventLog.append("\nImaging activated.");

    }

    //camera part
    protected void onActivityResult(int requestCode, int resultCode, Intent data) {
        // TODO Auto-generated method stub
        super.onActivityResult(requestCode, resultCode, data);
        Bitmap bp = (Bitmap) data.getExtras().get("data");
        outputImage.setImageBitmap(bp);
    }

    /**
     * ATTENTION: This was auto-generated to implement the App Indexing API.
     * See https://g.co/AppIndexing/AndroidStudio for more information.
     */
    public Action getIndexApiAction() {
        Thing object = new Thing.Builder()
                .setName("Main Page") // TODO: Define a title for the content shown.
                // TODO: Make sure this auto-generated URL is correct.
                .setUrl(Uri.parse("http://www.wholesky.ch"))
                .build();
        return new Action.Builder(Action.TYPE_VIEW)
                .setObject(object)
                .setActionStatus(Action.STATUS_TYPE_COMPLETED)
                .build();
    }

    //Activity Lifecycle Methods
    @Override
    public void onStart() {
        super.onStart();

        // ATTENTION: This was auto-generated to implement the App Indexing API.
        // See https://g.co/AppIndexing/AndroidStudio for more information.
        mImageSurfaceView.refreshCamera();
        mClient.connect();
        AppIndex.AppIndexApi.start(mClient, getIndexApiAction());
    }

    @Override
    public void onStop() {
        super.onStop();
        // ATTENTION: This was auto-generated to implement the App Indexing API.
        // See https://g.co/AppIndexing/AndroidStudio for more information.
        AppIndex.AppIndexApi.end(mClient, getIndexApiAction());
        mClient.disconnect();
    }

    @Override
    protected void onPause() {
//        mImageSurfaceView.releaseCamera();
        super.onPause();

        // stop the listener and save battery
        mSensorManager.unregisterListener(this);

        //        camera.stopPreview();
    }

    @Override
    protected void onResume() {
        super.onResume();
        camera.startPreview();
        // for the system's orientation sensor registered listeners
        mSensorManager.registerListener(this, accelerometer, SensorManager.SENSOR_DELAY_UI);
        mSensorManager.registerListener(this, magnetometer, SensorManager.SENSOR_DELAY_UI);
        mSensorManager.registerListener(this, mSensor, SensorManager.SENSOR_DELAY_NORMAL);
//        mSensorManager.registerListener(this, mSensor, 1000000);
    }

    @Override
    protected void onDestroy() {
        super.onDestroy();
    }


    @Override
    public void onSensorChanged(SensorEvent sensorEvent) {

        Sensor mySensor = sensorEvent.sensor;

        if (sensorEvent.sensor.getType() == Sensor.TYPE_ACCELEROMETER)
            mGravity = sensorEvent.values;
        if (sensorEvent.sensor.getType() == Sensor.TYPE_MAGNETIC_FIELD)
            mGeomagnetic = sensorEvent.values;

        // calculate heading
        if (mGravity != null && mGeomagnetic != null) {
            float R[] = new float[9];
            float I[] = new float[9];
            boolean success = SensorManager.getRotationMatrix(R, I, mGravity, mGeomagnetic);
            if (success) {
                float orientation[] = new float[3];
                SensorManager.getOrientation(R, orientation);
                //Log.i("MyActivity", "SensorEvent" + azimut);
                float thisAzimuth = (float) Math.toDegrees(orientation[0]); // orientation contains: azimut, pitch and roll
                thisAzimuth = Math.round(thisAzimuth * 10f) / 10f;
                azimuth = thisAzimuth;
//                Log.d(TAG, "azimuth: " + azimuth + "°.");
//                textHeading.setText(Float.toString(azimut));
//                compassNeedle.setRotation(360-azimut);
            }
        }
    }

    @Override
    public void onAccuracyChanged(Sensor sensor, int accuracy) {
        // not in use
    }

    // Define a listener that responds to location updates
    LocationListener locationListener = new LocationListener() {
        public void onLocationChanged(Location location) {
            // Called when a new location is found by the network location provider.
            longitude = location.getLongitude();
            latitude = location.getLatitude();
            updateCounter++;
            Log.d(TAG, "Long: " + longitude + " Lat: " + latitude);
        }

        public void onStatusChanged(String provider, int status, Bundle extras) {}

        public void onProviderEnabled(String provider) {}

        public void onProviderDisabled(String provider) {
            if (provider.equals("gps")) {
                Log.d(TAG, "GPS Provider disabled.");
            }
        }
    };

    public boolean instantiateSensors() {
        boolean success = false;
        //instantiate device sensor
        mSensorManager = (SensorManager) getSystemService(SENSOR_SERVICE);
        mSensor = mSensorManager.getDefaultSensor(Sensor.TYPE_GRAVITY);

        // accelerometer sensor (for device orientation)
        if( mSensorManager.getDefaultSensor(Sensor.TYPE_ACCELEROMETER) != null ) {
            Log.d(TAG, "Found accelerometer.");
            tvEventLog.append("\nFound accelerometer.");
            accelerometer = mSensorManager.getDefaultSensor(Sensor.TYPE_ACCELEROMETER);
        } else {
            Log.e(TAG, "No support for accelerometer.");
            tvEventLog.append("\nNo support for accelerometer.");
        }

        // magnetic sensor (for compass direction)
        if( mSensorManager.getDefaultSensor(Sensor.TYPE_MAGNETIC_FIELD) != null ) {
            Log.d(TAG, "Found magnetic sensor.");
            tvEventLog.append("\nFound magnetic sensor.");
            magnetometer = mSensorManager.getDefaultSensor(Sensor.TYPE_MAGNETIC_FIELD);
            success = true;
        } else {
            Log.e(TAG, "No support for magnetic sensor.");
            tvEventLog.append("\nNo support for magnetic sensor.");
        }
        return success;
    }

    public boolean instantiateGPS() {
        boolean success = false;
        LocationManager locationManager = (LocationManager) getSystemService(LOCATION_SERVICE);

        boolean enabled = locationManager.isProviderEnabled(LocationManager.GPS_PROVIDER);
        if (!enabled) {
            Log.d(TAG, "GPS is not activated.");
            tvEventLog.append("\nGPS is not activated.");
            Intent gpsSettingsIntent = new Intent(Settings.ACTION_LOCATION_SOURCE_SETTINGS);
            startActivity(gpsSettingsIntent);
        }
        // Define the criteria how to select the locatioin provider -> use
        // default
        Criteria criteria = new Criteria();
        provider = locationManager.getBestProvider(criteria, false);
        // Register the listener with the Location Manager to receive location updates
        try {
            locationManager.requestLocationUpdates(LocationManager.GPS_PROVIDER, 300000, 5, locationListener); //minimum interval 5 minutes, minimum distance 5 meter
        } catch (SecurityException e) {
            Log.d(TAG, "GPS access has not been granted.");
            tvEventLog.append("\nGPS access has not been granted.");
        }

        // Initialize the location fields
        if (location != null) {
            Log.d(TAG, "Provider " + provider + " has been selected.");
            tvEventLog.append("\nProvider " + provider + " has been selected.");
            success = true;
        } else {
            Log.d(TAG, "Could not find location.");
            tvEventLog.append("\nCould not find location.");
        }
        return success;
    }
    public void setupActionBar() {
        Toolbar myToolbar = (Toolbar) findViewById(R.id.my_toolbar);
        setSupportActionBar(myToolbar);
        Drawable drawable = ContextCompat.getDrawable(getApplicationContext(), R.drawable.ic_more_vert_white_24dp);
        myToolbar.setOverflowIcon(drawable);
        android.support.v7.app.ActionBar menu = getSupportActionBar();
        menu.setDisplayHomeAsUpEnabled(false);
        menu.setLogo(R.mipmap.ic_launcher);
        menu.setDisplayUseLogoEnabled(true);
        menu.setBackgroundDrawable(new ColorDrawable(getResources().getColor(R.color.colorActionBar)));
        menu.setTitle(Html.fromHtml("<font color='#ffffff'>Whole Sky Imager</font>"));

        if (Build.VERSION.SDK_INT > 21) {
            Window window = this.getWindow();
            window.addFlags(WindowManager.LayoutParams.FLAG_DRAWS_SYSTEM_BAR_BACKGROUNDS);
            window.clearFlags(WindowManager.LayoutParams.FLAG_TRANSLUCENT_STATUS);
            window.setStatusBarColor(ContextCompat.getColor(this, R.color.colorStatusBar));
        }
    }

    public void checkNetworkStatus() {
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
    private void getWSISettings() {
        sharedPref = PreferenceManager.getDefaultSharedPreferences(this);

        d(TAG, "Model No. in pref xml: " + Integer.parseInt(sharedPref.getString("wahrsisNo", "0")));
//        tvEventLog.append("\nModel No. in pref xml: " + Integer.parseInt(sharedPref.getString("wahrsisNo", "0")));
        // Set wahrsis model number according to settings activity
        if (Integer.parseInt(sharedPref.getString("wahrsisNo", "0")) != 0) {
            wahrsisModelNr = Integer.parseInt(sharedPref.getString("wahrsisNo", "404"));
            d(TAG, "Model No. set to: " + wahrsisModelNr);
        }
        pictureInterval = Integer.parseInt(sharedPref.getString("picInterval", "404"));
        d(TAG, "Picture interval: " + pictureInterval + " min.");
//        tvEventLog.append("\nPicture interval: " + pictureInterval + " min.");
//        flagWriteExif = sharedPref.getBoolean("extendedExif", false);
        d(TAG, "Extended exif: " + flagWriteExif);
//        tvEventLog.append("\nExtended exif: " + flagWriteExif);
//        flagRealignImage = sharedPref.getBoolean("realignImage", false);
        d(TAG, "Realign image: " + flagRealignImage);
//        tvEventLog.append("\nRealign image: " + flagRealignImage);

        authorizationToken = sharedPref.getString("authorToken", "f26543bea24e3545a8ef9708dffd7ce5d35127e2");
        d(TAG, "Authorization token: " + authorizationToken);
    }

      /**
     * Transform the floating point Longitude / Latitude into a DMS String. The latitude is expressed as
     * three RATIONAL values giving the degrees, minutes, and seconds, respectively.
     * When degrees, minutes and seconds are expressed, the format is dd/1,mm/1,ss/1.
     * @param position
     * @return DMS Longitude String
     */
    public String coordinatesToDMS(double position) {
        if (location == null) {
            return "0/1,0/1,0/1000";
        }
        String[] degMinSec = Location.convert(position, Location.FORMAT_SECONDS).split(":");
        return degMinSec[0] + "/1," + degMinSec[1] + "/1," + degMinSec[2] + "/1000";
    }

    private void writeExifData(String filepath) {
//        try {
            // Save the orientation in EXIF.
            ExifInterface exif = new ExifInterface();

            Log.d(TAG, "filepath: " + filepath);
//            exif.writeExif(filepath);exi
//            exif.setAttribute(TAG_GPS_LATITUDE, coordinatesToDMS(latitude));
//            exif.setAttribute(ExifInterface.TAG_GPS_LATITUDE_REF, latitude < 0 ? "S" : "N");
//            exif.setAttribute(ExifInterface.TAG_GPS_LONGITUDE, coordinatesToDMS(longitude));
//            exif.setAttribute(ExifInterface.TAG_GPS_LONGITUDE_REF, longitude < 0 ? "W" : "E");
//            exif.saveAttributes();
//            Log.d(TAG, "Set exif data.");
//        } catch (IOException e) {
//            Log.e(TAG, "Cannot set exif data: " + filepath);
//        }
    }
    private void copyExif(String source, String target) {
        ExifInterface oldExif = new ExifInterface();
        ExifInterface newExif = new ExifInterface();

        try {
            oldExif.readExif(source,ExifInterface.Options.OPTION_ALL);
            List<ExifTag> all_tags = oldExif.getAllTags();
//            newExif.writeExif(source, target);
            newExif.setExif(all_tags);
            if (flagWriteExif) {
                Log.d(TAG, "Compass: " + Math.round(azimuth));
                newExif.setTagValue(TAG_GPS_IMG_DIRECTION, Math.round(azimuth));
//                newExif.setTagValue(ExifInterface.TAG_GPS_IMG_DIRECTION, Math.round(azimuth));
                newExif.setTagValue(ExifInterface.TAG_GPS_IMG_DIRECTION_REF, 'M');
                newExif.setTagValue(ExifInterface.TAG_GPS_LATITUDE, coordinatesToDMS(latitude));
                newExif.setTagValue(ExifInterface.TAG_GPS_LATITUDE_REF, latitude < 0 ? "S" : "N");
                newExif.setTagValue(ExifInterface.TAG_GPS_LONGITUDE, coordinatesToDMS(longitude));
                newExif.setTagValue(ExifInterface.TAG_GPS_LONGITUDE_REF, longitude < 0 ? "W" : "E");
                //newExif.setTagValue(ExifInterface.TAG_USER_COMMENT, Float.toString(roll)+","+Float.toString(pitch));
            }
            newExif.writeExif(target);
            Log.d(TAG, "Writing exif completed.");
        } catch (IOException e) {
            e.printStackTrace();
        }

//
//
//        if (oldExif.getAttribute("DateTime") != null) {
//            newExif.setAttribute("DateTime",
//                    oldExif.getAttribute("DateTime"));
//        }
//        if (oldExif.getAttribute(ExifInterface.TAG_APERTURE) != null) {
//            newExif.setAttribute(ExifInterface.TAG_APERTURE,
//                    oldExif.getAttribute(ExifInterface.TAG_APERTURE));
//        }
//        if (oldExif.getAttribute(ExifInterface.TAG_ISO) != null) {
//            newExif.setAttribute(ExifInterface.TAG_ISO,
//                    oldExif.getAttribute(ExifInterface.TAG_ISO));
//        }
//        if (oldExif.getAttribute(ExifInterface.TAG_EXPOSURE_TIME) != null) {
//            newExif.setAttribute(ExifInterface.TAG_EXPOSURE_TIME,
//                    oldExif.getAttribute(ExifInterface.TAG_EXPOSURE_TIME));
//        }
//        if (oldExif.getAttribute(ExifInterface.TAG_DATETIME_DIGITIZED) != null) {
//            newExif.setAttribute(ExifInterface.TAG_DATETIME_DIGITIZED,
//                    oldExif.getAttribute(ExifInterface.TAG_DATETIME_DIGITIZED));
//        }
//        if (oldExif.getAttribute(ExifInterface.TAG_MODEL) != null) {
//            newExif.setAttribute(ExifInterface.TAG_MODEL,
//                    oldExif.getAttribute(ExifInterface.TAG_MODEL));
//        }
//        if (oldExif.getAttribute(ExifInterface.TAG_MAKE) != null) {
//            newExif.setAttribute(ExifInterface.TAG_MAKE,
//                    oldExif.getAttribute(ExifInterface.TAG_MAKE));
//        }
//        if (oldExif.getAttribute(ExifInterface.TAG_WHITE_BALANCE) != null) {
//            newExif.setAttribute(ExifInterface.TAG_WHITE_BALANCE,
//                    oldExif.getAttribute(ExifInterface.TAG_WHITE_BALANCE));
//        }
//        try {
//            newExif.saveAttributes();
//        } catch (IOException e) {
//            e.printStackTrace();
//        }
    }

    /**
     * Rotates Bitmap according to compass
     * @param source
     * @param angle
     * @return
     */
    public static Bitmap RotateBitmap(Bitmap source, float angle)
    {
        //initial way
//        Matrix matrix = new Matrix();
//        matrix.postRotate(angle);
//        return Bitmap.createBitmap(source, 0, 0, source.getWidth(), source.getHeight(), matrix, true);

        //more advanced way
        Matrix matrix = new Matrix();
        matrix.postRotate(angle);
        return Bitmap.createBitmap(source, 0, 0, source.getWidth(), source.getHeight(), matrix, true);
    }

    public static boolean setUploadStatus(boolean status) {
        Log.d("WSIServerClient", "Status: " + status);
        return status;
    }
}
