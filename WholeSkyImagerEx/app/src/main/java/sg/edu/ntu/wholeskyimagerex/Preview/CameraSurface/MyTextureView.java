package sg.edu.ntu.wholeskyimagerex.Preview.CameraSurface;

import android.content.Context;
import android.graphics.Matrix;
import android.media.MediaRecorder;
import android.util.Log;
import android.view.TextureView;
import android.view.View;

import sg.edu.ntu.wholeskyimagerex.CameraController.CameraController;
import sg.edu.ntu.wholeskyimagerex.CameraController.CameraControllerException;
import sg.edu.ntu.wholeskyimagerex.MyDebug;
import sg.edu.ntu.wholeskyimagerex.Preview.Preview;

/** Provides support for the surface used for the preview, using a TextureView.
 */
public class MyTextureView extends TextureView implements CameraSurface {
	private static final String TAG = "MyTextureView";

	private final Preview preview;
	private final int [] measure_spec = new int[2];
	
	public MyTextureView(Context context, Preview preview) {
		super(context);
		this.preview = preview;
		if( MyDebug.LOG ) {
			Log.d(TAG, "new MyTextureView");
		}

		// Install a TextureView.SurfaceTextureListener so we get notified when the
		// underlying surface is created and destroyed.
		this.setSurfaceTextureListener(preview);
	}
	
	@Override
	public View getView() {
		return this;
	}
	
	@Override
	public void setPreviewDisplay(CameraController camera_controller) {
		if( MyDebug.LOG )
			Log.d(TAG, "setPreviewDisplay");
		try {
			camera_controller.setPreviewTexture(this.getSurfaceTexture());
		}
		catch(CameraControllerException e) {
			if( MyDebug.LOG )
				Log.e(TAG, "Failed to set preview display: " + e.getMessage());
			e.printStackTrace();
		}
	}

	@Override
	public void setVideoRecorder(MediaRecorder video_recorder) {
		// should be no need to do anything (see documentation for MediaRecorder.setPreviewDisplay())
	}

	/*@Override
	public void onDraw(Canvas canvas) {
		preview.draw(canvas);
	}*/

    @Override
    protected void onMeasure(int widthSpec, int heightSpec) {
    	preview.getMeasureSpec(measure_spec, widthSpec, heightSpec);
    	super.onMeasure(measure_spec[0], measure_spec[1]);
    }

	@Override
	public void setTransform(Matrix matrix) {
		super.setTransform(matrix);
	}

	@Override
	public void onPause() {
	}

	@Override
	public void onResume() {
	}
}
