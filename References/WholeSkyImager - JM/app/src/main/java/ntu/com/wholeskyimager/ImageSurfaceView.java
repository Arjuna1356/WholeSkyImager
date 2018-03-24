package ntu.com.wholeskyimager;

/**
 * Created by Julian on 26.09.2016.
 */

import android.hardware.Camera;
import android.content.Context;
import android.util.Log;
import android.view.Surface;
import android.view.SurfaceHolder;
import android.view.SurfaceView;

import java.io.IOException;

public class ImageSurfaceView extends SurfaceView implements SurfaceHolder.Callback {

    private Camera camera;
    private SurfaceHolder surfaceHolder;
    private boolean sPreviewing;

    //Constructor that
    @SuppressWarnings("deprecation")
    public ImageSurfaceView(Context context, Camera camera) {
        super(context);
        this.camera = camera;
        this.surfaceHolder = getHolder();
        this.surfaceHolder.addCallback(this);
        this.surfaceHolder.setType(SurfaceHolder.SURFACE_TYPE_PUSH_BUFFERS);
    }

    public boolean getPreviewState() {
        return sPreviewing;
    }

    @Override
    public void surfaceCreated(SurfaceHolder holder) {
        try {
            this.camera.setDisplayOrientation(90);
            this.camera.setPreviewDisplay(holder);
            this.camera.startPreview();
            sPreviewing = true;
        } catch (IOException e) {
            e.printStackTrace();
        }
    }

    @Override
    public void surfaceChanged(SurfaceHolder holder, int format, int width, int height) {
        // start preview with new settings
        try {
            camera.setPreviewDisplay(surfaceHolder);
            camera.startPreview();
        } catch (Exception e) {
            // intentionally left blank for a test
        }
    }

    @Override
    public void surfaceDestroyed(SurfaceHolder holder) {
        this.camera.stopPreview();
        this.camera.setPreviewCallback(null); //newly added
        this.camera.release();
    }

    /**
     * custom camera tweaks and startPreview()
     */
    public void refreshCamera() {
        if (surfaceHolder.getSurface() == null || camera == null) {
            // preview surface does not exist, camera not opened created yet
            return;
        }
        Log.i(null, "CameraPreview refreshCamera()");
        // stop preview before making changes
        try {
            camera.stopPreview();
        } catch (Exception e) {
            // ignore: tried to stop a non-existent preview
        }
        try {
            camera.setPreviewDisplay(surfaceHolder);
            camera.startPreview();
        } catch (Exception e) {
            // this error is fixed in the camera Error Callback (Error 100)
            Log.d(VIEW_LOG_TAG, "Error starting camera preview: " + e.getMessage());
        }
    }

    public void releaseCamera() {
            // check if Camera instance exists

        sPreviewing = false;
        if (camera != null) {
            sPreviewing = false;
            // first stop preview
            camera.stopPreview();
            // then cancel its preview callback
            camera.setPreviewCallback(null);
            // and finally release it
            camera.release();
            // sanitize you Camera object holder
            camera = null;
        }
    }
}