package sg.edu.ntu.wholeskyimagerlegacy;

import android.hardware.Camera;
import android.content.Context;
import android.util.Log;
import android.view.SurfaceHolder;
import android.view.SurfaceView;

import java.io.IOException;

public class ImageSurfaceView extends SurfaceView implements SurfaceHolder.Callback
{
    private Camera camera;
    private SurfaceHolder surfaceHolder;

    public ImageSurfaceView(Context context, Camera camera)
    {
        super(context);
        this.camera = camera;
        this.surfaceHolder = getHolder();
        this.surfaceHolder.addCallback(this);
    }

    @Override
    public void surfaceCreated(SurfaceHolder holder)
    {
        try
        {
            this.camera.setDisplayOrientation(90);
            this.camera.setPreviewDisplay(holder);
            this.camera.startPreview();
        } catch (IOException e)
        {
            e.printStackTrace();
        }
    }

    @Override
    public void surfaceChanged(SurfaceHolder holder, int format, int width, int height)
    {

    }

    @Override
    public void surfaceDestroyed(SurfaceHolder holder)
    {
        this.camera.stopPreview();
        this.camera.release();
    }

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
}