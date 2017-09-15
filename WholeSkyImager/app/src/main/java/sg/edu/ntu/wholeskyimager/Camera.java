package sg.edu.ntu.wholeskyimager;

import android.Manifest;
import android.content.Context;
import android.content.pm.PackageManager;
import android.graphics.ImageFormat;
import android.graphics.SurfaceTexture;
import android.hardware.camera2.CameraAccessException;
import android.hardware.camera2.CameraCaptureSession;
import android.hardware.camera2.CameraCharacteristics;
import android.hardware.camera2.CameraDevice;
import android.hardware.camera2.CameraManager;
import android.hardware.camera2.CameraMetadata;
import android.hardware.camera2.CaptureRequest;
import android.hardware.camera2.TotalCaptureResult;
import android.hardware.camera2.params.StreamConfigurationMap;
import android.media.Image;
import android.media.ImageReader;
import android.os.Environment;
import android.os.Handler;
import android.support.annotation.NonNull;
import android.support.annotation.Nullable;
import android.util.Log;
import android.util.Size;
import android.util.SparseIntArray;
import android.view.Surface;
import android.view.TextureView;
import android.widget.FrameLayout;
import android.widget.TextView;
import android.widget.Toast;

import java.io.File;
import java.io.FileNotFoundException;
import java.io.FileOutputStream;
import java.io.IOException;
import java.io.OutputStream;
import java.nio.ByteBuffer;
import java.text.SimpleDateFormat;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Date;
import java.util.List;

/**
 * Created by Jonathan on 26/8/2017.
 */

public class Camera
{
    protected CameraDevice cameraDevice = null;
    protected CameraCaptureSession cameraCaptureSessions = null;
    protected CaptureRequest.Builder captureRequestBuilder = null;
    private TextView tvEventLog = null;
    private final String TAG = this.getClass().getName();
    private int wahrsisModelNr = -1;

    private Size imageDimension = null;
    private ImageReader imageReader = null;
    private TextureView textureView = null;
    private File file = null;

    private MainActivity mainActivity = null;
    private Handler mBackgroundHandler = null;

    private boolean afEnabled = true;
    private float lensHyperFocal = 0.0f;
    private int isoVal = 100;
    private int mSensorOrientation = 0;

    private static final SparseIntArray ORIENTATIONS = new SparseIntArray();
    static {
        ORIENTATIONS.append(Surface.ROTATION_0, 90);
        ORIENTATIONS.append(Surface.ROTATION_90, 0);
        ORIENTATIONS.append(Surface.ROTATION_180, 270);
        ORIENTATIONS.append(Surface.ROTATION_270, 180);
    }

    public Camera(MainActivity activity)
    {
        this.mainActivity = activity;
        this.wahrsisModelNr = mainActivity.getWahrsisModelNr();
        this.tvEventLog = mainActivity.getTvEventLog();
    }

    public void setHandler(Handler handler)
    {
        this.mBackgroundHandler = handler;
    }

    public void openCamera(FrameLayout frameLayout)
    {
        int REQUEST_CAMERA_PERMISSION = mainActivity.getRequestCameraPermission();
        String cameraId;

        CameraManager manager = (CameraManager) mainActivity.getSystemService(Context.CAMERA_SERVICE);

        try
        {
            cameraId = manager.getCameraIdList()[0];
            CameraCharacteristics characteristics = manager.getCameraCharacteristics(cameraId);
            StreamConfigurationMap map = characteristics.get(CameraCharacteristics.SCALER_STREAM_CONFIGURATION_MAP);
            assert map != null;
            imageDimension = map.getOutputSizes(SurfaceTexture.class)[0];

            mSensorOrientation = characteristics.get(CameraCharacteristics.SENSOR_ORIENTATION);

//            lensHyperFocal = characteristics.get(CameraCharacteristics.LENS_INFO_MINIMUM_FOCUS_DISTANCE);

            // Add permission for camera and let user grant the permission
            if (mainActivity.checkSelfPermission(Manifest.permission.CAMERA) == PackageManager.PERMISSION_GRANTED &&
                    mainActivity.checkSelfPermission(Manifest.permission.WRITE_EXTERNAL_STORAGE) == PackageManager.PERMISSION_GRANTED)
            {
                manager.openCamera(cameraId, stateCallback, null);
            } else
            {
                mainActivity.requestPermissions(new String[]{Manifest.permission.CAMERA, Manifest.permission.WRITE_EXTERNAL_STORAGE}, REQUEST_CAMERA_PERMISSION);
                return;
            }

            textureView = new TextureView(mainActivity);
            assert textureView != null;
            textureView.setSurfaceTextureListener(textureListener);

            frameLayout.addView(textureView);
        } catch (CameraAccessException e)
        {
            Log.e(TAG, "Error opening camera");
            tvEventLog.append("\nError opening camera");
        }
    }

    public void closeCamera(FrameLayout frameLayout)
    {
        if (cameraDevice != null)
        {
            cameraDevice.close();
            cameraDevice = null;
        }

        if (imageReader != null)
        {
            imageReader.close();
            imageReader = null;
        }

        frameLayout.removeView(textureView);
        textureView = null;

        Log.d(TAG, "Camera Closed");
        tvEventLog.append("\nCamera Closed");
    }

    public void runImagingTask()
    {
//        timeStampOld = timeStampNew;
        //do something
        Log.d(TAG, "Taking Picture");
        tvEventLog.append("\nTaking Picture");

        takePicture();
        Log.d(TAG, "Pictures successfully taken");
        tvEventLog.append("\nPictures successfully taken");
    }

    public void takePicture()
    {
        if (cameraDevice == null)
        {
            Log.e(TAG, "cameraDevice is null");
            tvEventLog.append("\ncameraDevice is null");
            return;
        }

        CameraManager manager = (CameraManager) mainActivity.getSystemService(Context.CAMERA_SERVICE);

        try
        {
            CameraCharacteristics characteristics = manager.getCameraCharacteristics(cameraDevice.getId());
            Size[] jpegSizes = null;

            if (characteristics != null)
            {
                jpegSizes = characteristics.get(CameraCharacteristics.SCALER_STREAM_CONFIGURATION_MAP).getOutputSizes(ImageFormat.JPEG);
            }

            int width = 640;
            int height = 480;

            if (jpegSizes != null && 0 < jpegSizes.length)
            {
                width = jpegSizes[0].getWidth();
                height = jpegSizes[0].getHeight();
            }

            ImageReader reader = ImageReader.newInstance(width, height, ImageFormat.JPEG, 1);

            List<Surface> outputSurfaces = new ArrayList<Surface>(2);
            outputSurfaces.add(reader.getSurface());
            outputSurfaces.add(new Surface(textureView.getSurfaceTexture()));

            final CaptureRequest.Builder captureBuilder = cameraDevice.createCaptureRequest(CameraDevice.TEMPLATE_STILL_CAPTURE);
            captureBuilder.addTarget(reader.getSurface());

            if(afEnabled)
            {
                captureBuilder.set(CaptureRequest.CONTROL_MODE, CameraMetadata.CONTROL_MODE_AUTO);
            }
            else
            {
                captureBuilder.set(CaptureRequest.CONTROL_AF_MODE, CameraMetadata.CONTROL_AF_MODE_OFF);
                captureBuilder.set(CaptureRequest.CONTROL_AE_MODE, CameraMetadata.CONTROL_AE_MODE_OFF);
                captureBuilder.set(CaptureRequest.LENS_FOCUS_DISTANCE, lensHyperFocal);
                captureBuilder.set(CaptureRequest.SENSOR_SENSITIVITY, isoVal);
            }

            int rotation = mainActivity.getWindowManager().getDefaultDisplay().getRotation();
            captureBuilder.set(CaptureRequest.JPEG_ORIENTATION, getOrientation(rotation));

            String timeStampNew = null;

            timeStampNew = new SimpleDateFormat("yyyy-MM-dd-HH-mm-ss").format(new Date());

            String fileName = timeStampNew + "-wahrsis" + wahrsisModelNr + "-" + "normal" + ".jpg";

            final File file = getOutputMediaFile(fileName);

            ImageReader.OnImageAvailableListener readerListener = new ImageReader.OnImageAvailableListener()
            {
                @Override
                public void onImageAvailable(ImageReader reader)
                {
                    Image image = null;

                    try
                    {
                        image = reader.acquireLatestImage();
                        ByteBuffer buffer = image.getPlanes()[0].getBuffer();
                        byte[] bytes = new byte[buffer.capacity()];
                        buffer.get(bytes);
                        save(bytes);
                    } catch (FileNotFoundException e)
                    {
                        e.printStackTrace();
                    } catch (IOException e)
                    {
                        e.printStackTrace();
                    } finally
                    {
                        if (image != null)
                        {
                            image.close();
                        }
                    }
                }

                private void save(byte[] bytes) throws IOException
                {
                    OutputStream output = null;
                    try
                    {
                        output = new FileOutputStream(file);
                        output.write(bytes);
                    } finally
                    {
                        if (null != output)
                        {
                            output.close();
                        }
                    }
                }
            };

            reader.setOnImageAvailableListener(readerListener, mBackgroundHandler);

            final CameraCaptureSession.CaptureCallback captureListener = new CameraCaptureSession.CaptureCallback()
            {
                @Override
                public void onCaptureCompleted(CameraCaptureSession session, CaptureRequest request, TotalCaptureResult result)
                {
                    super.onCaptureCompleted(session, request, result);
                    Toast.makeText(mainActivity, "Saved:" + file, Toast.LENGTH_SHORT).show();
                    createCameraPreview();
                }
            };

            cameraDevice.createCaptureSession(outputSurfaces, new CameraCaptureSession.StateCallback()
            {
                @Override
                public void onConfigured(CameraCaptureSession session)
                {
                    try
                    {
                        session.capture(captureBuilder.build(), captureListener, mBackgroundHandler);
                    } catch (CameraAccessException e)
                    {
                        e.printStackTrace();
                    }
                }

                @Override
                public void onConfigureFailed(CameraCaptureSession session)
                {
                }
            }, mBackgroundHandler);
        } catch (CameraAccessException e)
        {
            e.printStackTrace();
        }
    }

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

    /**
     * Retrieves the JPEG orientation from the specified screen rotation.
     *
     * @param rotation The screen rotation.
     * @return The JPEG orientation (one of 0, 90, 270, and 360)
     */
    private int getOrientation(int rotation) {
        // Sensor orientation is 90 for most devices, or 270 for some devices (eg. Nexus 5X)
        // We have to take that into account and rotate JPEG properly.
        // For devices with orientation of 90, we simply return our mapping from ORIENTATIONS.
        // For devices with orientation of 270, we need to rotate the JPEG 180 degrees.
        return (ORIENTATIONS.get(rotation) + mSensorOrientation + 270) % 360;
    }

    TextureView.SurfaceTextureListener textureListener = new TextureView.SurfaceTextureListener()
    {
        @Override
        public void onSurfaceTextureAvailable(SurfaceTexture surface, int width, int height)
        {
        }

        @Override
        public void onSurfaceTextureSizeChanged(SurfaceTexture surface, int width, int height)
        {
            // Transform you image captured size according to the surface width and height
        }

        @Override
        public boolean onSurfaceTextureDestroyed(SurfaceTexture surface)
        {
            return false;
        }

        @Override
        public void onSurfaceTextureUpdated(SurfaceTexture surface)
        {
        }
    };

    final CameraCaptureSession.CaptureCallback captureCallbackListener = new CameraCaptureSession.CaptureCallback()
    {
        @Override
        public void onCaptureCompleted(CameraCaptureSession session, CaptureRequest request, TotalCaptureResult result)
        {
            super.onCaptureCompleted(session, request, result);
            Toast.makeText(mainActivity, "Saved:" + file, Toast.LENGTH_SHORT).show();
            createCameraPreview();
        }
    };

    private final CameraDevice.StateCallback stateCallback = new CameraDevice.StateCallback()
    {
        @Override
        public void onOpened(CameraDevice camera)
        {
            //This is called when the camera is open

            cameraDevice = camera;
            createCameraPreview();

            Log.d(TAG, "Camera Opened");
            tvEventLog.append("\nCamera Opened");
        }

        @Override
        public void onDisconnected(CameraDevice camera)
        {
            cameraDevice.close();
        }

        @Override
        public void onError(CameraDevice camera, int error)
        {
            cameraDevice.close();
            cameraDevice = null;
        }
    };

    private void createCameraPreview()
    {
        try
        {
            SurfaceTexture texture = textureView.getSurfaceTexture();
            assert texture != null;
            texture.setDefaultBufferSize(imageDimension.getWidth(), imageDimension.getHeight());
            Surface surface = new Surface(texture);
            captureRequestBuilder = cameraDevice.createCaptureRequest(CameraDevice.TEMPLATE_PREVIEW);
            captureRequestBuilder.addTarget(surface);

            cameraDevice.createCaptureSession(Arrays.asList(surface), new CameraCaptureSession.StateCallback()
            {
                @Override
                public void onConfigured(@NonNull CameraCaptureSession cameraCaptureSession)
                {
                    //The camera is already closed
                    if (null == cameraDevice)
                    {
                        return;
                    }
                    // When the session is ready, we start displaying the preview.
                    cameraCaptureSessions = cameraCaptureSession;
                    updatePreview();
                }

                @Override
                public void onConfigureFailed(@NonNull CameraCaptureSession cameraCaptureSession)
                {
                    Toast.makeText(mainActivity, "Configuration change", Toast.LENGTH_SHORT).show();
                }
            }, null);

            afEnabled = mainActivity.getAFEnabled();

            if(afEnabled)
            {
                captureRequestBuilder.set(CaptureRequest.CONTROL_MODE, CameraMetadata.CONTROL_MODE_AUTO);
                tvEventLog.append("\nPreviewing AF");
            }
            else
            {
                captureRequestBuilder.set(CaptureRequest.CONTROL_AF_MODE, CameraMetadata.CONTROL_AF_MODE_OFF);
                captureRequestBuilder.set(CaptureRequest.CONTROL_AE_MODE, CameraMetadata.CONTROL_AE_MODE_OFF);
                captureRequestBuilder.set(CaptureRequest.LENS_FOCUS_DISTANCE, lensHyperFocal);
                captureRequestBuilder.set(CaptureRequest.SENSOR_SENSITIVITY, isoVal);
                tvEventLog.append("\nPreviewing Manual");
            }
        } catch (CameraAccessException e)
        {
            Log.e(TAG, "Error creating preview");
            tvEventLog.append("\nError creating preview");
        }
    }

    private void updatePreview()
    {
        if (cameraDevice == null)
        {
            Log.e(TAG, "updatePreview error");
            tvEventLog.append("\nupdatePreview error");
        }

        try
        {
            cameraCaptureSessions.setRepeatingRequest(captureRequestBuilder.build(), null, mBackgroundHandler);
        } catch (CameraAccessException e)
        {
            e.printStackTrace();
        }
    }
}