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
import android.util.Log;
import android.util.Size;
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
import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;

/**
 * Created by Jonathan on 26/8/2017.
 */

public class Camera
{
    private String cameraId = null;
    protected CameraDevice cameraDevice = null;
    protected CameraCaptureSession cameraCaptureSessions = null;
    protected CaptureRequest.Builder captureRequestBuilder = null;
    private TextView tvEventLog = null;
    private final String TAG = this.getClass().getName();

    private Size imageDimension = null;
    private ImageReader imageReader = null;

    private TextureView textureView = null;

    private File file = null;

    private MainActivity mainActivity = null;

    private Handler mBackgroundHandler = null;

    private float lensHyperFocal = 0.0f;
    private int isoVal = 100;

    public Camera(MainActivity activity)
    {
        this.mainActivity = activity;
        this.tvEventLog = mainActivity.getTvEventLog();
    }

    public void setHandler(Handler handler)
    {
        this.mBackgroundHandler = handler;
    }

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

    protected void takePicture()
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
            captureBuilder.set(CaptureRequest.CONTROL_MODE, CameraMetadata.CONTROL_MODE_AUTO);
            final File file = new File(Environment.getExternalStorageDirectory() + "/pic.jpg");

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

    protected void createCameraPreview()
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

            captureRequestBuilder.set(CaptureRequest.CONTROL_MODE, CameraMetadata.CONTROL_MODE_AUTO);
            captureRequestBuilder.set(CaptureRequest.CONTROL_AF_MODE, CameraMetadata.CONTROL_AF_MODE_OFF);
            captureRequestBuilder.set(CaptureRequest.CONTROL_AE_MODE, CameraMetadata.CONTROL_AE_MODE_OFF);
            captureRequestBuilder.set(CaptureRequest.LENS_FOCUS_DISTANCE, lensHyperFocal);
            captureRequestBuilder.set(CaptureRequest.SENSOR_SENSITIVITY, isoVal);
        } catch (CameraAccessException e)
        {
            Log.e(TAG, "Error creating preview");
            tvEventLog.append("\nError creating preview");
        }
    }

    public void openCamera(FrameLayout frameLayout)
    {
        int REQUEST_CAMERA_PERMISSION = mainActivity.getRequestCameraPermission();

        CameraManager manager = (CameraManager) mainActivity.getSystemService(Context.CAMERA_SERVICE);

        try
        {
            cameraId = manager.getCameraIdList()[0];
            CameraCharacteristics characteristics = manager.getCameraCharacteristics(cameraId);
            StreamConfigurationMap map = characteristics.get(CameraCharacteristics.SCALER_STREAM_CONFIGURATION_MAP);
            assert map != null;
            imageDimension = map.getOutputSizes(SurfaceTexture.class)[0];

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

    protected void updatePreview()
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
        //check the current state before we display the screen
//        params = camera.getParameters();
//
//        //max value: +12, step size: exposure-compensation-step=0.166667. EV: +2
//        maxExposureComp = params.getMaxExposureCompensation();
//        minExposureComp = params.getMinExposureCompensation();
//
//        timeStampNew = new SimpleDateFormat("yyyy-MM-dd-HH-mm-ss").format(new Date());

//        if(timeStampOld != null && flagDeleteImages) {
//            //TODO: delte all files
//            if (flagDeleteImages) {
//                String filePath = Environment.getExternalStorageDirectory().getPath() + "/WSI/";
//                File imageFileLow = new File(filePath+timeStampOld+"-wahrsis" + wahrsisModelNr + "-low" + ".jpg");
//                File imageFileMed = new File(filePath+timeStampOld+"-wahrsis" + wahrsisModelNr + "-med" + ".jpg");
//                File imageFileHigh = new File(filePath+timeStampOld+"-wahrsis" + wahrsisModelNr + "-high" + ".jpg");
//                imageFileLow.delete();
//                imageFileMed.delete();
//                boolean statusDelete = imageFileHigh.delete();
//                Log.d(TAG, "Deleting successfull: " + statusDelete);
//            }
//        }

//        params.set("mode", "m");
//        params.set("iso", "ISO100");

//        camera.stopPreview();
        takePicture();
        Log.d(TAG, "Pictures successfully taken");
        tvEventLog.append("\nPictures successfully taken");
    }
}