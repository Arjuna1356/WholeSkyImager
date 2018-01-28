package sg.edu.ntu.wholeskyimagerex2;

import java.io.File;
import java.io.FileNotFoundException;
import java.io.FileWriter;
import java.io.IOException;
import java.io.OutputStreamWriter;
import java.util.ArrayList;
import java.util.Calendar;
import java.util.Date;
import java.util.List;
import java.util.Timer;
import java.util.TimerTask;

import sg.edu.ntu.wholeskyimagerex2.CameraController.CameraController;
import sg.edu.ntu.wholeskyimagerex2.Preview.ApplicationInterface;
import sg.edu.ntu.wholeskyimagerex2.Preview.Preview;
//import sg.edu.ntu.wholeskyimagerex2.UI.DrawPreview;

import android.annotation.TargetApi;
import android.app.Activity;
import android.content.Context;
import android.content.Intent;
import android.content.SharedPreferences;
import android.graphics.Bitmap;
import android.graphics.Canvas;
import android.graphics.Color;
import android.graphics.Paint;
import android.graphics.Rect;
import android.hardware.camera2.DngCreator;
import android.location.Location;
import android.media.CamcorderProfile;
import android.media.Image;
import android.media.MediaMetadataRetriever;
import android.media.MediaRecorder;
import android.net.Uri;
import android.os.Build;
import android.os.Bundle;
import android.os.Environment;
import android.os.Handler;
import android.os.ParcelFileDescriptor;
import android.preference.PreferenceManager;
import android.provider.DocumentsContract;
import android.provider.MediaStore;
import android.util.Log;
import android.util.Pair;
import android.view.MotionEvent;
import android.view.View;
import android.widget.ImageButton;

/** Our implementation of ApplicationInterface, see there for details.
 */
public class MyApplicationInterface implements ApplicationInterface {
	private static final String TAG = "MyApplicationInterface";

	// note, okay to change the order of enums in future versions, as getPhotoMode() does not rely on the order for the saved photo mode
	public enum PhotoMode {
		Standard,
		DRO, // single image "fake" HDR
		HDR, // HDR created from multiple (expo bracketing) images
	}

	private final MainActivity main_activity;
	private final LocationSupplier locationSupplier;
//	private final GyroSensor gyroSensor;
	private final StorageUtils storageUtils;
//	private final DrawPreview drawPreview;
	private final ImageSaver imageSaver;
    private WSIServerClient serverClient;

	private final Rect text_bounds = new Rect();
	private boolean used_front_screen_flash ;

	private boolean last_images_saf; // whether the last images array are using SAF or not
	/** This class keeps track of the images saved in this batch, for use with Pause Preview option, so we can share or trash images.
	 */
	private static class LastImage {
		public final boolean share; // one of the images in the list should have share set to true, to indicate which image to share
		public final String name;
		final Uri uri;

		LastImage(Uri uri, boolean share) {
			this.name = null;
			this.uri = uri;
			this.share = share;
		}

		LastImage(String filename, boolean share) {
			this.name = filename;
			this.uri = Uri.parse("file://" + this.name);
			this.share = share;
		}
	}
	private final List<LastImage> last_images = new ArrayList<>();

	// camera properties which are saved in bundle, but not stored in preferences (so will be remembered if the app goes into background, but not after restart)
	private int cameraId = 0;
	private int zoom_factor = 0;
	private float focus_distance = 0.0f;

	MyApplicationInterface(MainActivity main_activity, Bundle savedInstanceState) {
		long debug_time = 0;
		if( MyDebug.LOG ) {
			Log.d(TAG, "MyApplicationInterface");
			debug_time = System.currentTimeMillis();
		}
		this.main_activity = main_activity;
		this.locationSupplier = new LocationSupplier(main_activity);
		if( MyDebug.LOG )
			Log.d(TAG, "MyApplicationInterface: time after creating location supplier: " + (System.currentTimeMillis() - debug_time));
//		this.gyroSensor = new GyroSensor(main_activity);
		this.storageUtils = new StorageUtils(main_activity);
		if( MyDebug.LOG )
			Log.d(TAG, "MyApplicationInterface: time after creating storage utils: " + (System.currentTimeMillis() - debug_time));
//		this.drawPreview = new DrawPreview(main_activity, this);

		this.imageSaver = new ImageSaver(main_activity);
		this.imageSaver.start();

		if( savedInstanceState != null ) {
			// load the things we saved in onSaveInstanceState().
			if( MyDebug.LOG )
				Log.d(TAG, "read from savedInstanceState");
			cameraId = savedInstanceState.getInt("cameraId", 0);
			if( MyDebug.LOG )
				Log.d(TAG, "found cameraId: " + cameraId);
			zoom_factor = savedInstanceState.getInt("zoom_factor", 0);
			if( MyDebug.LOG )
				Log.d(TAG, "found zoom_factor: " + zoom_factor);
			focus_distance = savedInstanceState.getFloat("focus_distance", 0.0f);
			if( MyDebug.LOG )
				Log.d(TAG, "found focus_distance: " + focus_distance);
		}

		if( MyDebug.LOG )
			Log.d(TAG, "MyApplicationInterface: total time to create MyApplicationInterface: " + (System.currentTimeMillis() - debug_time));
	}

	/** Here we save states which aren't saved in preferences (we don't want them to be saved if the
	 *  application is restarted from scratch), but we do want to preserve if Android has to recreate
	 *  the application (e.g., configuration change, or it's destroyed while in background).
	 */
	void onSaveInstanceState(Bundle state) {
		if( MyDebug.LOG )
			Log.d(TAG, "onSaveInstanceState");
		if( MyDebug.LOG )
			Log.d(TAG, "save cameraId: " + cameraId);
		state.putInt("cameraId", cameraId);
		if( MyDebug.LOG )
			Log.d(TAG, "save zoom_factor: " + zoom_factor);
		state.putInt("zoom_factor", zoom_factor);
		if( MyDebug.LOG )
			Log.d(TAG, "save focus_distance: " + focus_distance);
		state.putFloat("focus_distance", focus_distance);
	}

	void onDestroy() {
		if( MyDebug.LOG )
			Log.d(TAG, "onDestroy");
//		if( drawPreview != null ) {
//			drawPreview.onDestroy();
//		}
		if( imageSaver != null ) {
			imageSaver.onDestroy();
		}
	}

	LocationSupplier getLocationSupplier() {
		return locationSupplier;
	}

//	public GyroSensor getGyroSensor() {
//		return gyroSensor;
//	}

	StorageUtils getStorageUtils() {
		return storageUtils;
	}

	ImageSaver getImageSaver() {
		return imageSaver;
	}

	@Override
	public Context getContext() {
		return main_activity;
	}

	@Override
	public boolean useCamera2() {
		SharedPreferences sharedPreferences = PreferenceManager.getDefaultSharedPreferences(getContext());
		if( main_activity.supportsCamera2() ) {
			return sharedPreferences.getBoolean(PreferenceKeys.getUseCamera2PreferenceKey(), false);
		}
		return false;
	}

	@Override
	public Location getLocation() {
		return locationSupplier.getLocation();
	}

	@Override
	public int getCameraIdPref() {
		return cameraId;
	}

	@Override
	public String getFlashPref() {
		SharedPreferences sharedPreferences = PreferenceManager.getDefaultSharedPreferences(getContext());
		return sharedPreferences.getString(PreferenceKeys.getFlashPreferenceKey(cameraId), "");
	}

	@Override
	public String getFocusPref() {
		SharedPreferences sharedPreferences = PreferenceManager.getDefaultSharedPreferences(getContext());
		return sharedPreferences.getString(PreferenceKeys.getFocusPreferenceKey(cameraId), "");
//		SharedPreferences sharedPreferences = PreferenceManager.getDefaultSharedPreferences(getContext());
//
//		String afEnabled;
//
//		if(sharedPreferences.getBoolean("enableAF", true))
//		{
//			afEnabled = "focus_mode_auto";
//		}
//		else
//		{
//			afEnabled = "focus_mode_infinity";
//		}
//
//		return afEnabled;
	}

	@Override
	public String getSceneModePref() {
		SharedPreferences sharedPreferences = PreferenceManager.getDefaultSharedPreferences(getContext());
		return sharedPreferences.getString(PreferenceKeys.getSceneModePreferenceKey(), "auto");
	}

	@Override
	public String getColorEffectPref() {
		SharedPreferences sharedPreferences = PreferenceManager.getDefaultSharedPreferences(getContext());
		return sharedPreferences.getString(PreferenceKeys.getColorEffectPreferenceKey(), "none");
	}

	@Override
	public String getWhiteBalancePref() {
		SharedPreferences sharedPreferences = PreferenceManager.getDefaultSharedPreferences(getContext());
		return sharedPreferences.getString(PreferenceKeys.getWhiteBalancePreferenceKey(), "auto");
	}

	@Override
	public int getWhiteBalanceTemperaturePref() {
		SharedPreferences sharedPreferences = PreferenceManager.getDefaultSharedPreferences(getContext());
		return sharedPreferences.getInt(PreferenceKeys.getWhiteBalanceTemperaturePreferenceKey(), 5000);
	}

	@Override
	public String getISOPref() {
		SharedPreferences sharedPreferences = PreferenceManager.getDefaultSharedPreferences(getContext());
		return sharedPreferences.getString(PreferenceKeys.getISOPreferenceKey(), "auto");
	}

	@Override
	public int getExposureCompensationPref() {
		SharedPreferences sharedPreferences = PreferenceManager.getDefaultSharedPreferences(getContext());
		String value = sharedPreferences.getString(PreferenceKeys.getExposurePreferenceKey(), "0");
		if( MyDebug.LOG )
			Log.d(TAG, "saved exposure value: " + value);
		int exposure = 0;
		try {
			exposure = Integer.parseInt(value);
			if( MyDebug.LOG )
				Log.d(TAG, "exposure: " + exposure);
		}
		catch(NumberFormatException exception) {
			if( MyDebug.LOG )
				Log.d(TAG, "exposure invalid format, can't parse to int");
		}
		return exposure;
	}

	@Override
	public Pair<Integer, Integer> getCameraResolutionPref() {
		SharedPreferences sharedPreferences = PreferenceManager.getDefaultSharedPreferences(getContext());
		String resolution_value = sharedPreferences.getString(PreferenceKeys.getResolutionPreferenceKey(cameraId), "");
		if( MyDebug.LOG )
			Log.d(TAG, "resolution_value: " + resolution_value);
		if( resolution_value.length() > 0 ) {
			// parse the saved size, and make sure it is still valid
			int index = resolution_value.indexOf(' ');
			if( index == -1 ) {
				if( MyDebug.LOG )
					Log.d(TAG, "resolution_value invalid format, can't find space");
			}
			else {
				String resolution_w_s = resolution_value.substring(0, index);
				String resolution_h_s = resolution_value.substring(index+1);
				if( MyDebug.LOG ) {
					Log.d(TAG, "resolution_w_s: " + resolution_w_s);
					Log.d(TAG, "resolution_h_s: " + resolution_h_s);
				}
				try {
					int resolution_w = Integer.parseInt(resolution_w_s);
					if( MyDebug.LOG )
						Log.d(TAG, "resolution_w: " + resolution_w);
					int resolution_h = Integer.parseInt(resolution_h_s);
					if( MyDebug.LOG )
						Log.d(TAG, "resolution_h: " + resolution_h);
					return new Pair<>(resolution_w, resolution_h);
				}
				catch(NumberFormatException exception) {
					if( MyDebug.LOG )
						Log.d(TAG, "resolution_value invalid format, can't parse w or h to int");
				}
			}
		}
		return null;
	}

//    @Override
//	public void captureImage()
//    {
//        main_activity.clickedTakePhoto();
//    }

	/** getImageQualityPref() returns the image quality used for the Camera Controller for taking a
	 *  photo - in some cases, we may set that to a higher value, then perform processing on the
	 *  resultant JPEG before resaving. This method returns the image quality setting to be used for
	 *  saving the final image (as specified by the user).
	 */
	private int getSaveImageQualityPref() {
		if( MyDebug.LOG )
			Log.d(TAG, "getSaveImageQualityPref");
		SharedPreferences sharedPreferences = PreferenceManager.getDefaultSharedPreferences(getContext());
		String image_quality_s = sharedPreferences.getString(PreferenceKeys.getQualityPreferenceKey(), "90");
		int image_quality;
		try {
			image_quality = Integer.parseInt(image_quality_s);
		}
		catch(NumberFormatException exception) {
			if( MyDebug.LOG )
				Log.e(TAG, "image_quality_s invalid format: " + image_quality_s);
			image_quality = 90;
		}
		return image_quality;
	}

	@Override
	public int getImageQualityPref(){
		if( MyDebug.LOG )
			Log.d(TAG, "getImageQualityPref");
		// see documentation for getSaveImageQualityPref(): in DRO mode we want to take the photo
		// at 100% quality for post-processing, the final image will then be saved at the user requested
		// setting
		PhotoMode photo_mode = getPhotoMode();
		if( photo_mode == PhotoMode.DRO )
			return 100;
		return getSaveImageQualityPref();
	}

	@Override
	public String getPreviewSizePref() {
		SharedPreferences sharedPreferences = PreferenceManager.getDefaultSharedPreferences(getContext());
		return sharedPreferences.getString(PreferenceKeys.getPreviewSizePreferenceKey(), "preference_preview_size_wysiwyg");
	}

	@Override
	public String getPreviewRotationPref() {
		SharedPreferences sharedPreferences = PreferenceManager.getDefaultSharedPreferences(getContext());
		return sharedPreferences.getString(PreferenceKeys.getRotatePreviewPreferenceKey(), "0");
	}

	@Override
	public String getLockOrientationPref() {
		SharedPreferences sharedPreferences = PreferenceManager.getDefaultSharedPreferences(getContext());
		return sharedPreferences.getString(PreferenceKeys.getLockOrientationPreferenceKey(), "none");
	}

	@Override
	public boolean getTouchCapturePref() {
		SharedPreferences sharedPreferences = PreferenceManager.getDefaultSharedPreferences(getContext());
		String value = sharedPreferences.getString(PreferenceKeys.getTouchCapturePreferenceKey(), "none");
		return value.equals("single");
	}

	@Override
	public boolean getDoubleTapCapturePref() {
		SharedPreferences sharedPreferences = PreferenceManager.getDefaultSharedPreferences(getContext());
		String value = sharedPreferences.getString(PreferenceKeys.getTouchCapturePreferenceKey(), "none");
		return value.equals("double");
	}

	@Override
	public boolean getPausePreviewPref() {
		SharedPreferences sharedPreferences = PreferenceManager.getDefaultSharedPreferences(getContext());
		return sharedPreferences.getBoolean(PreferenceKeys.getPausePreviewPreferenceKey(), false);
	}

	@Override
	public boolean getShowToastsPref() {
		SharedPreferences sharedPreferences = PreferenceManager.getDefaultSharedPreferences(getContext());
		return sharedPreferences.getBoolean(PreferenceKeys.getShowToastsPreferenceKey(), true);
	}

	public boolean getThumbnailAnimationPref() {
		SharedPreferences sharedPreferences = PreferenceManager.getDefaultSharedPreferences(getContext());
		return sharedPreferences.getBoolean(PreferenceKeys.getThumbnailAnimationPreferenceKey(), true);
	}

	@Override
	public boolean getShutterSoundPref() {
		SharedPreferences sharedPreferences = PreferenceManager.getDefaultSharedPreferences(getContext());
		return sharedPreferences.getBoolean(PreferenceKeys.getShutterSoundPreferenceKey(), false);
	}

	@Override
	public boolean getStartupFocusPref() {
		SharedPreferences sharedPreferences = PreferenceManager.getDefaultSharedPreferences(getContext());
		return sharedPreferences.getBoolean(PreferenceKeys.getStartupFocusPreferenceKey(), true);
	}

	@Override
	public long getTimerPref() {
		SharedPreferences sharedPreferences = PreferenceManager.getDefaultSharedPreferences(getContext());
		String timer_value = sharedPreferences.getString(PreferenceKeys.getTimerPreferenceKey(), "0");
		long timer_delay;
		try {
			timer_delay = (long)Integer.parseInt(timer_value) * 1000;
		}
		catch(NumberFormatException e) {
			if( MyDebug.LOG )
				Log.e(TAG, "failed to parse preference_timer value: " + timer_value);
			e.printStackTrace();
			timer_delay = 0;
		}
		return timer_delay;
	}

	@Override
	public String getRepeatPref() {
		SharedPreferences sharedPreferences = PreferenceManager.getDefaultSharedPreferences(getContext());
		return sharedPreferences.getString(PreferenceKeys.getBurstModePreferenceKey(), "1");
	}

	@Override
	public long getRepeatIntervalPref() {
		SharedPreferences sharedPreferences = PreferenceManager.getDefaultSharedPreferences(getContext());
		String timer_value = sharedPreferences.getString(PreferenceKeys.getBurstIntervalPreferenceKey(), "0");
		long timer_delay;
		try {
			timer_delay = (long)Integer.parseInt(timer_value) * 1000;
		}
		catch(NumberFormatException e) {
			if( MyDebug.LOG )
				Log.e(TAG, "failed to parse preference_burst_interval value: " + timer_value);
			e.printStackTrace();
			timer_delay = 0;
		}
		return timer_delay;
	}

	@Override
	public boolean getGeotaggingPref() {
		SharedPreferences sharedPreferences = PreferenceManager.getDefaultSharedPreferences(getContext());
		return sharedPreferences.getBoolean(PreferenceKeys.getLocationPreferenceKey(), false);
	}

	@Override
	public boolean getRequireLocationPref() {
		SharedPreferences sharedPreferences = PreferenceManager.getDefaultSharedPreferences(getContext());
		return sharedPreferences.getBoolean(PreferenceKeys.getRequireLocationPreferenceKey(), false);
	}

	private boolean getGeodirectionPref() {
		SharedPreferences sharedPreferences = PreferenceManager.getDefaultSharedPreferences(getContext());
		return sharedPreferences.getBoolean(PreferenceKeys.getGPSDirectionPreferenceKey(), false);
	}

	private boolean getAutoStabilisePref() {
		SharedPreferences sharedPreferences = PreferenceManager.getDefaultSharedPreferences(getContext());
		return getAutoStabilisePref(sharedPreferences);
	}

	public boolean getAutoStabilisePref(SharedPreferences sharedPreferences) {
		boolean auto_stabilise = sharedPreferences.getBoolean(PreferenceKeys.getAutoStabilisePreferenceKey(), false);
		if( auto_stabilise && main_activity.supportsAutoStabilise() )
			return true;
		return false;
	}

	private String getStampPref() {
		SharedPreferences sharedPreferences = PreferenceManager.getDefaultSharedPreferences(getContext());
		return getStampPref(sharedPreferences);
	}

	public String getStampPref(SharedPreferences sharedPreferences) {
		return sharedPreferences.getString(PreferenceKeys.getStampPreferenceKey(), "preference_stamp_no");
	}

	private String getStampDateFormatPref() {
		SharedPreferences sharedPreferences = PreferenceManager.getDefaultSharedPreferences(getContext());
		return sharedPreferences.getString(PreferenceKeys.getStampDateFormatPreferenceKey(), "preference_stamp_dateformat_default");
	}

	private String getStampTimeFormatPref() {
		SharedPreferences sharedPreferences = PreferenceManager.getDefaultSharedPreferences(getContext());
		return sharedPreferences.getString(PreferenceKeys.getStampTimeFormatPreferenceKey(), "preference_stamp_timeformat_default");
	}

	private String getStampGPSFormatPref() {
		SharedPreferences sharedPreferences = PreferenceManager.getDefaultSharedPreferences(getContext());
		return sharedPreferences.getString(PreferenceKeys.getStampGPSFormatPreferenceKey(), "preference_stamp_gpsformat_default");
	}

	private String getTextStampPref() {
		SharedPreferences sharedPreferences = PreferenceManager.getDefaultSharedPreferences(getContext());
		return sharedPreferences.getString(PreferenceKeys.getTextStampPreferenceKey(), "");
	}

	private int getTextStampFontSizePref() {
		SharedPreferences sharedPreferences = PreferenceManager.getDefaultSharedPreferences(getContext());
		int font_size = 12;
		String value = sharedPreferences.getString(PreferenceKeys.getStampFontSizePreferenceKey(), "12");
		if( MyDebug.LOG )
			Log.d(TAG, "saved font size: " + value);
		try {
			font_size = Integer.parseInt(value);
			if( MyDebug.LOG )
				Log.d(TAG, "font_size: " + font_size);
		}
		catch(NumberFormatException exception) {
			if( MyDebug.LOG )
				Log.d(TAG, "font size invalid format, can't parse to int");
		}
		return font_size;
	}

	@Override
	public int getZoomPref() {
		if( MyDebug.LOG )
			Log.d(TAG, "getZoomPref: " + zoom_factor);
		return zoom_factor;
	}

	@Override
	public double getCalibratedLevelAngle() {
		SharedPreferences sharedPreferences = PreferenceManager.getDefaultSharedPreferences(getContext());
		return sharedPreferences.getFloat(PreferenceKeys.getCalibratedLevelAnglePreferenceKey(), 0.0f);
	}

	@Override
	public long getExposureTimePref() {
		SharedPreferences sharedPreferences = PreferenceManager.getDefaultSharedPreferences(getContext());
		return sharedPreferences.getLong(PreferenceKeys.getExposureTimePreferenceKey(), CameraController.EXPOSURE_TIME_DEFAULT);
	}

	@Override
	public float getFocusDistancePref() {
		return focus_distance;
	}

	@Override
	public boolean isExpoBracketingPref() {
		PhotoMode photo_mode = getPhotoMode();
		if( photo_mode == PhotoMode.HDR )
			return true;
		return false;
	}

	@Override
	public int getExpoBracketingNImagesPref() {
		if( MyDebug.LOG )
			Log.d(TAG, "getExpoBracketingNImagesPref");
		int n_images;
		PhotoMode photo_mode = getPhotoMode();
		if( photo_mode == PhotoMode.HDR ) {
			// always set 3 images for HDR
			n_images = 3;
		}
		else {
			SharedPreferences sharedPreferences = PreferenceManager.getDefaultSharedPreferences(getContext());
			String n_images_s = sharedPreferences.getString(PreferenceKeys.getExpoBracketingNImagesPreferenceKey(), "3");
			try {
				n_images = Integer.parseInt(n_images_s);
			}
			catch(NumberFormatException exception) {
				if( MyDebug.LOG )
					Log.e(TAG, "n_images_s invalid format: " + n_images_s);
				n_images = 3;
			}
		}
		if( MyDebug.LOG )
			Log.d(TAG, "n_images = " + n_images);
		return n_images;
	}

	@Override
	public double getExpoBracketingStopsPref() {
		if( MyDebug.LOG )
			Log.d(TAG, "getExpoBracketingStopsPref");
		double n_stops;
		PhotoMode photo_mode = getPhotoMode();
		if( photo_mode == PhotoMode.HDR ) {
			// always set 2 stops for HDR
			n_stops = 2.0;
		}
		else {
			SharedPreferences sharedPreferences = PreferenceManager.getDefaultSharedPreferences(getContext());
			String n_stops_s = sharedPreferences.getString(PreferenceKeys.getExpoBracketingStopsPreferenceKey(), "2");
			try {
				n_stops = Double.parseDouble(n_stops_s);
			}
			catch(NumberFormatException exception) {
				if( MyDebug.LOG )
					Log.e(TAG, "n_stops_s invalid format: " + n_stops_s);
				n_stops = 2.0;
			}
		}
		if( MyDebug.LOG )
			Log.d(TAG, "n_stops = " + n_stops);
		return n_stops;
	}

	public PhotoMode getPhotoMode() {
		SharedPreferences sharedPreferences = PreferenceManager.getDefaultSharedPreferences(getContext());
		return getPhotoMode(sharedPreferences);
	}

	public PhotoMode getPhotoMode(SharedPreferences sharedPreferences) {
		String photo_mode_pref = sharedPreferences.getString(PreferenceKeys.getPhotoModePreferenceKey(), "preference_photo_mode_std");
		boolean dro = photo_mode_pref.equals("preference_photo_mode_dro");
		if( dro && main_activity.supportsDRO() )
			return PhotoMode.DRO;
		boolean hdr = photo_mode_pref.equals("preference_photo_mode_hdr");
		if( hdr && main_activity.supportsHDR() )
			return PhotoMode.HDR;
		return PhotoMode.Standard;
	}

	@Override
	public boolean getOptimiseAEForDROPref() {
		PhotoMode photo_mode = getPhotoMode();
		return( photo_mode == PhotoMode.DRO );
	}

	@Override
	public boolean isRawPref() {
		SharedPreferences sharedPreferences = PreferenceManager.getDefaultSharedPreferences(getContext());
		return isRawPref(sharedPreferences);
	}

	public boolean isRawPref(SharedPreferences sharedPreferences) {
		if( isImageCaptureIntent() )
			return false;
		return sharedPreferences.getString(PreferenceKeys.getRawPreferenceKey(), "preference_raw_no").equals("preference_raw_yes");
	}

	@Override
	public boolean useCamera2FakeFlash() {
		SharedPreferences sharedPreferences = PreferenceManager.getDefaultSharedPreferences(getContext());
		return sharedPreferences.getBoolean(PreferenceKeys.getCamera2FakeFlashPreferenceKey(), false);
	}

	@Override
	public boolean useCamera2FastBurst() {
		SharedPreferences sharedPreferences = PreferenceManager.getDefaultSharedPreferences(getContext());
		return sharedPreferences.getBoolean(PreferenceKeys.getCamera2FastBurstPreferenceKey(), true);
	}

//	@Override
//	public void cameraSetup() {
//		main_activity.cameraSetup();
//	}

//	@Override
//	public void onContinuousFocusMove(boolean start) {
//		if( MyDebug.LOG )
//			Log.d(TAG, "onContinuousFocusMove: " + start);
//		drawPreview.onContinuousFocusMove(start);
//	}

//	@Override
//	public void cameraInOperation(boolean in_operation) {
//		if( MyDebug.LOG )
//			Log.d(TAG, "cameraInOperation: " + in_operation);
//		if( !in_operation && used_front_screen_flash ) {
//			main_activity.setBrightnessForCamera(false); // ensure screen brightness matches user preference, after using front screen flash
//			used_front_screen_flash = false;
//		}
//		main_activity.getMainUI().showGUI(!in_operation);
//	}

//	@Override
//	public void turnFrontScreenFlashOn() {
//		if( MyDebug.LOG )
//			Log.d(TAG, "turnFrontScreenFlashOn");
//		used_front_screen_flash = true;
//		main_activity.setBrightnessForCamera(true); // ensure we have max screen brightness, even if user preference not set for max brightness
//	}

//	void updateThumbnail(Bitmap thumbnail) {
//		if( MyDebug.LOG )
//			Log.d(TAG, "updateThumbnail");
//		main_activity.updateGalleryIcon(thumbnail);
//	}

	@Override
	public void setCameraIdPref(int cameraId) {
		this.cameraId = cameraId;
	}

	@Override
	public void setFlashPref(String flash_value) {
		SharedPreferences sharedPreferences = PreferenceManager.getDefaultSharedPreferences(getContext());
		SharedPreferences.Editor editor = sharedPreferences.edit();
		editor.putString(PreferenceKeys.getFlashPreferenceKey(cameraId), flash_value);
		editor.apply();
	}

	@Override
	public void setFocusPref(String focus_value) {
		SharedPreferences sharedPreferences = PreferenceManager.getDefaultSharedPreferences(getContext());
		SharedPreferences.Editor editor = sharedPreferences.edit();
		editor.putString(PreferenceKeys.getFocusPreferenceKey(cameraId), focus_value);
		editor.apply();
	}

	@Override
	public void setSceneModePref(String scene_mode) {
		SharedPreferences sharedPreferences = PreferenceManager.getDefaultSharedPreferences(getContext());
		SharedPreferences.Editor editor = sharedPreferences.edit();
		editor.putString(PreferenceKeys.getSceneModePreferenceKey(), scene_mode);
		editor.apply();
	}

	@Override
	public void clearSceneModePref() {
		SharedPreferences sharedPreferences = PreferenceManager.getDefaultSharedPreferences(getContext());
		SharedPreferences.Editor editor = sharedPreferences.edit();
		editor.remove(PreferenceKeys.getSceneModePreferenceKey());
		editor.apply();
	}

	@Override
	public void setColorEffectPref(String color_effect) {
		SharedPreferences sharedPreferences = PreferenceManager.getDefaultSharedPreferences(getContext());
		SharedPreferences.Editor editor = sharedPreferences.edit();
		editor.putString(PreferenceKeys.getColorEffectPreferenceKey(), color_effect);
		editor.apply();
	}

	@Override
	public void clearColorEffectPref() {
		SharedPreferences sharedPreferences = PreferenceManager.getDefaultSharedPreferences(getContext());
		SharedPreferences.Editor editor = sharedPreferences.edit();
		editor.remove(PreferenceKeys.getColorEffectPreferenceKey());
		editor.apply();
	}

	@Override
	public void setWhiteBalancePref(String white_balance) {
		SharedPreferences sharedPreferences = PreferenceManager.getDefaultSharedPreferences(getContext());
		SharedPreferences.Editor editor = sharedPreferences.edit();
		editor.putString(PreferenceKeys.getWhiteBalancePreferenceKey(), white_balance);
		editor.apply();
	}

	@Override
	public void clearWhiteBalancePref() {
		SharedPreferences sharedPreferences = PreferenceManager.getDefaultSharedPreferences(getContext());
		SharedPreferences.Editor editor = sharedPreferences.edit();
		editor.remove(PreferenceKeys.getWhiteBalancePreferenceKey());
		editor.apply();
	}

	@Override
	public void setWhiteBalanceTemperaturePref(int white_balance_temperature) {
		SharedPreferences sharedPreferences = PreferenceManager.getDefaultSharedPreferences(getContext());
		SharedPreferences.Editor editor = sharedPreferences.edit();
		editor.putInt(PreferenceKeys.getWhiteBalanceTemperaturePreferenceKey(), white_balance_temperature);
		editor.apply();
	}

	@Override
	public void setISOPref(String iso) {
		SharedPreferences sharedPreferences = PreferenceManager.getDefaultSharedPreferences(getContext());
		SharedPreferences.Editor editor = sharedPreferences.edit();
		editor.putString(PreferenceKeys.getISOPreferenceKey(), iso);
		editor.apply();
	}

	@Override
	public void clearISOPref() {
		SharedPreferences sharedPreferences = PreferenceManager.getDefaultSharedPreferences(getContext());
		SharedPreferences.Editor editor = sharedPreferences.edit();
		editor.remove(PreferenceKeys.getISOPreferenceKey());
		editor.apply();
	}

	@Override
	public void setExposureCompensationPref(int exposure) {
		SharedPreferences sharedPreferences = PreferenceManager.getDefaultSharedPreferences(getContext());
		SharedPreferences.Editor editor = sharedPreferences.edit();
		editor.putString(PreferenceKeys.getExposurePreferenceKey(), "" + exposure);
		editor.apply();
	}

	@Override
	public void clearExposureCompensationPref() {
		SharedPreferences sharedPreferences = PreferenceManager.getDefaultSharedPreferences(getContext());
		SharedPreferences.Editor editor = sharedPreferences.edit();
		editor.remove(PreferenceKeys.getExposurePreferenceKey());
		editor.apply();
	}

	@Override
	public void setCameraResolutionPref(int width, int height) {
		String resolution_value = width + " " + height;
		if( MyDebug.LOG ) {
			Log.d(TAG, "save new resolution_value: " + resolution_value);
		}
		SharedPreferences sharedPreferences = PreferenceManager.getDefaultSharedPreferences(getContext());
		SharedPreferences.Editor editor = sharedPreferences.edit();
		editor.putString(PreferenceKeys.getResolutionPreferenceKey(cameraId), resolution_value);
		editor.apply();
	}

	@Override
	public void setZoomPref(int zoom) {
		if( MyDebug.LOG )
			Log.d(TAG, "setZoomPref: " + zoom);
		this.zoom_factor = zoom;
	}

	@Override
	public void requestCameraPermission() {
		if( MyDebug.LOG )
			Log.d(TAG, "requestCameraPermission");
		main_activity.requestCameraPermission();
	}

	@Override
	public void requestStoragePermission() {
		if( MyDebug.LOG )
			Log.d(TAG, "requestStoragePermission");
		main_activity.requestStoragePermission();
	}

	@Override
	public void requestSettingsPermission() {
		if( MyDebug.LOG )
			Log.d(TAG, "requestSettingsPermission");
		main_activity.requestSettingsPermission();
	}

	@Override
	public void setExposureTimePref(long exposure_time) {
		SharedPreferences sharedPreferences = PreferenceManager.getDefaultSharedPreferences(getContext());
		SharedPreferences.Editor editor = sharedPreferences.edit();
		editor.putLong(PreferenceKeys.getExposureTimePreferenceKey(), exposure_time);
		editor.apply();
	}

	@Override
	public void clearExposureTimePref() {
		SharedPreferences sharedPreferences = PreferenceManager.getDefaultSharedPreferences(getContext());
		SharedPreferences.Editor editor = sharedPreferences.edit();
		editor.remove(PreferenceKeys.getExposureTimePreferenceKey());
		editor.apply();
	}

	@Override
	public void setFocusDistancePref(float focus_distance) {
		this.focus_distance = focus_distance;
	}

	private int getStampFontColor() {
		SharedPreferences sharedPreferences = PreferenceManager.getDefaultSharedPreferences(this.getContext());
		String color = sharedPreferences.getString(PreferenceKeys.getStampFontColorPreferenceKey(), "#ffffff");
		return Color.parseColor(color);
	}

	public enum Alignment {
		ALIGNMENT_TOP,
		ALIGNMENT_CENTRE,
		ALIGNMENT_BOTTOM
	}

	public void drawTextWithBackground(Canvas canvas, Paint paint, String text, int foreground, int background, int location_x, int location_y) {
		drawTextWithBackground(canvas, paint, text, foreground, background, location_x, location_y, Alignment.ALIGNMENT_BOTTOM);
	}

	public void drawTextWithBackground(Canvas canvas, Paint paint, String text, int foreground, int background, int location_x, int location_y, Alignment alignment_y) {
		drawTextWithBackground(canvas, paint, text, foreground, background, location_x, location_y, alignment_y, null, true);
	}

	public void drawTextWithBackground(Canvas canvas, Paint paint, String text, int foreground, int background, int location_x, int location_y, Alignment alignment_y, String ybounds_text, boolean shadow) {
		final float scale = getContext().getResources().getDisplayMetrics().density;
		paint.setStyle(Paint.Style.FILL);
		paint.setColor(background);
		paint.setAlpha(64);
		int alt_height = 0;
		if( ybounds_text != null ) {
			paint.getTextBounds(ybounds_text, 0, ybounds_text.length(), text_bounds);
			alt_height = text_bounds.bottom - text_bounds.top;
		}
		paint.getTextBounds(text, 0, text.length(), text_bounds);
		if( ybounds_text != null ) {
			text_bounds.bottom = text_bounds.top + alt_height;
		}
		final int padding = (int) (2 * scale + 0.5f); // convert dps to pixels
		if( paint.getTextAlign() == Paint.Align.RIGHT || paint.getTextAlign() == Paint.Align.CENTER ) {
			float width = paint.measureText(text); // n.b., need to use measureText rather than getTextBounds here
			/*if( MyDebug.LOG )
				Log.d(TAG, "width: " + width);*/
			if( paint.getTextAlign() == Paint.Align.CENTER )
				width /= 2.0f;
			text_bounds.left -= width;
			text_bounds.right -= width;
		}
		/*if( MyDebug.LOG )
			Log.d(TAG, "text_bounds left-right: " + text_bounds.left + " , " + text_bounds.right);*/
		text_bounds.left += location_x - padding;
		text_bounds.right += location_x + padding;
		// unclear why we need the offset of -1, but need this to align properly on Galaxy Nexus at least
		int top_y_diff = - text_bounds.top + padding - 1;
		if( alignment_y == Alignment.ALIGNMENT_TOP ) {
			int height = text_bounds.bottom - text_bounds.top + 2*padding;
			text_bounds.top = location_y - 1;
			text_bounds.bottom = text_bounds.top + height;
			location_y += top_y_diff;
		}
		else if( alignment_y == Alignment.ALIGNMENT_CENTRE ) {
			int height = text_bounds.bottom - text_bounds.top + 2*padding;
			int y_diff = - text_bounds.top + padding - 1;
			text_bounds.top = (int)(0.5 * ( (location_y - 1) + (text_bounds.top + location_y - padding) )); // average of ALIGNMENT_TOP and ALIGNMENT_BOTTOM
			text_bounds.bottom = text_bounds.top + height;
			location_y += (int)(0.5*top_y_diff); // average of ALIGNMENT_TOP and ALIGNMENT_BOTTOM
		}
		else {
			text_bounds.top += location_y - padding;
			text_bounds.bottom += location_y + padding;
		}
		if( shadow ) {
			canvas.drawRect(text_bounds, paint);
		}
		paint.setColor(foreground);
		canvas.drawText(text, location_x, location_y, paint);
	}

	private boolean saveInBackground(boolean image_capture_intent) {
		boolean do_in_background = true;
		SharedPreferences sharedPreferences = PreferenceManager.getDefaultSharedPreferences(getContext());
		if( !sharedPreferences.getBoolean(PreferenceKeys.getBackgroundPhotoSavingPreferenceKey(), true) )
			do_in_background = false;
		else if( image_capture_intent )
			do_in_background = false;
		else if( getPausePreviewPref() )
			do_in_background = false;
		return do_in_background;
	}

	private boolean isImageCaptureIntent() {
		boolean image_capture_intent = false;
		String action = main_activity.getIntent().getAction();
		if( MediaStore.ACTION_IMAGE_CAPTURE.equals(action) || MediaStore.ACTION_IMAGE_CAPTURE_SECURE.equals(action) ) {
			if( MyDebug.LOG )
				Log.d(TAG, "from image capture intent");
			image_capture_intent = true;
		}
		return image_capture_intent;
	}

	private boolean saveImage(boolean is_hdr, boolean save_expo, List<byte []> images, Date current_date, String timeStamp) {
		if( MyDebug.LOG )
			Log.d(TAG, "saveImage");

		System.gc();

		boolean image_capture_intent = isImageCaptureIntent();
		Uri image_capture_intent_uri = null;
		if( image_capture_intent ) {
			if( MyDebug.LOG )
				Log.d(TAG, "from image capture intent");
			Bundle myExtras = main_activity.getIntent().getExtras();
			if (myExtras != null) {
				image_capture_intent_uri = myExtras.getParcelable(MediaStore.EXTRA_OUTPUT);
				if( MyDebug.LOG )
					Log.d(TAG, "save to: " + image_capture_intent_uri);
			}
		}

		boolean using_camera2 = main_activity.getPreview().usingCamera2API();
		int image_quality = getSaveImageQualityPref();
		if( MyDebug.LOG )
			Log.d(TAG, "image_quality: " + image_quality);
		boolean do_auto_stabilise = getAutoStabilisePref() && main_activity.getPreview().hasLevelAngle();
		double level_angle = do_auto_stabilise ? main_activity.getPreview().getLevelAngle() : 0.0;

		// I have received crashes where camera_controller was null - could perhaps happen if this thread was running just as the camera is closing?
		boolean is_front_facing = main_activity.getPreview().getCameraController() != null && main_activity.getPreview().getCameraController().isFrontFacing();
		SharedPreferences sharedPreferences = PreferenceManager.getDefaultSharedPreferences(getContext());
		boolean mirror = is_front_facing && sharedPreferences.getString(PreferenceKeys.getFrontCameraMirrorKey(), "preference_front_camera_mirror_no").equals("preference_front_camera_mirror_photo");
		String preference_stamp = this.getStampPref();
		String preference_textstamp = this.getTextStampPref();
		int font_size = getTextStampFontSizePref();
		int color = getStampFontColor();
		String pref_style = sharedPreferences.getString(PreferenceKeys.getStampStyleKey(), "preference_stamp_style_shadowed");
		String preference_stamp_dateformat = this.getStampDateFormatPref();
		String preference_stamp_timeformat = this.getStampTimeFormatPref();
		String preference_stamp_gpsformat = this.getStampGPSFormatPref();
		boolean store_location = getGeotaggingPref() && getLocation() != null;
		Location location = store_location ? getLocation() : null;
		boolean store_geo_direction = main_activity.getPreview().hasGeoDirection() && getGeodirectionPref();
		double geo_direction = store_geo_direction ? main_activity.getPreview().getGeoDirection() : 0.0;
		boolean has_thumbnail_animation = getThumbnailAnimationPref();

		boolean do_in_background = saveInBackground(image_capture_intent);

		int sample_factor = 1;
		if( !this.getPausePreviewPref() ) {
			// if pausing the preview, we use the thumbnail also for the preview, so don't downsample
			// otherwise, we can downsample by 4 to increase performance, without noticeable loss in visual quality (even for the thumbnail animation)
			sample_factor *= 4;
			if( !has_thumbnail_animation ) {
				// can use even lower resolution if we don't have the thumbnail animation
				sample_factor *= 4;
			}
		}
		if( MyDebug.LOG )
			Log.d(TAG, "sample_factor: " + sample_factor);

		boolean success = imageSaver.saveImageJpeg(do_in_background, is_hdr, save_expo, images,
				image_capture_intent, image_capture_intent_uri,
				using_camera2, image_quality,
				do_auto_stabilise, level_angle,
				is_front_facing,
				mirror,
				current_date, timeStamp,
				preference_stamp, preference_textstamp, font_size, color, pref_style, preference_stamp_dateformat, preference_stamp_timeformat, preference_stamp_gpsformat,
				store_location, location, store_geo_direction, geo_direction,
				sample_factor);

		if( MyDebug.LOG )
			Log.d(TAG, "saveImage complete, success: " + success);

		return success;
	}

	@Override
	public boolean onPictureTaken(byte [] data, Date current_date, String timeStamp) {
		if( MyDebug.LOG )
			Log.d(TAG, "onPictureTaken");

		List<byte []> images = new ArrayList<>();
		images.add(data);

		boolean is_hdr = false;
		// note, multi-image HDR and expo is handled under onBurstPictureTaken; here we look for DRO, as that's the photo mode to set
		// single image HDR
		PhotoMode photo_mode = getPhotoMode();
		if( photo_mode == PhotoMode.DRO ) {
			is_hdr = true;
		}
		boolean success = saveImage(is_hdr, false, images, current_date, timeStamp);

		if( MyDebug.LOG )
			Log.d(TAG, "onPictureTaken complete, success: " + success);

		return success;
	}

	@Override
	public boolean onBurstPictureTaken(List<byte []> images, Date current_date, String timeStamp) {
		if( MyDebug.LOG )
			Log.d(TAG, "onBurstPictureTaken: received " + images.size() + " images");

		boolean success;

        if( MyDebug.LOG )
            Log.d(TAG, "HDR mode");
        SharedPreferences sharedPreferences = PreferenceManager.getDefaultSharedPreferences(getContext());
        boolean save_expo =  sharedPreferences.getBoolean(PreferenceKeys.getHDRSaveExpoPreferenceKey(), false);
        if( MyDebug.LOG )
            Log.d(TAG, "save_expo: " + save_expo);

        success = saveImage(true, save_expo, images, current_date, timeStamp);

		return success;
	}

	@Override
	public boolean onRawPictureTaken(DngCreator dngCreator, Image image, Date current_date, String timeStamp) {
		if( MyDebug.LOG )
			Log.d(TAG, "onRawPictureTaken");
		System.gc();

		boolean do_in_background = saveInBackground(false);

		boolean success = imageSaver.saveImageRaw(do_in_background, dngCreator, image, current_date, timeStamp);

		if( MyDebug.LOG )
			Log.d(TAG, "onRawPictureTaken complete");
		return success;
	}

    @Override
    public void sendImages(String timeStamp, int wahrsisModelNr)
    {
        serverClient = main_activity.getServerClient();

        if(serverClient != null)
        {
            serverClient.httpPOST(timeStamp, wahrsisModelNr);
        }
    }

	void addLastImage(File file, boolean share) {
		if( MyDebug.LOG ) {
			Log.d(TAG, "addLastImage: " + file);
			Log.d(TAG, "share?: " + share);
		}
		last_images_saf = false;
		LastImage last_image = new LastImage(file.getAbsolutePath(), share);
		last_images.add(last_image);
	}

	void addLastImageSAF(Uri uri, boolean share) {
		if( MyDebug.LOG ) {
			Log.d(TAG, "addLastImageSAF: " + uri);
			Log.d(TAG, "share?: " + share);
		}
		last_images_saf = true;
		LastImage last_image = new LastImage(uri, share);
		last_images.add(last_image);
	}

	void clearLastImages() {
		if( MyDebug.LOG )
			Log.d(TAG, "clearLastImages");
		last_images_saf = false;
		last_images.clear();
	}

	void shareLastImage() {
		if( MyDebug.LOG )
			Log.d(TAG, "shareLastImage");
		Preview preview  = main_activity.getPreview();
		if( preview.isPreviewPaused() ) {
			LastImage share_image = null;
			for(int i=0;i<last_images.size() && share_image == null;i++) {
				LastImage last_image = last_images.get(i);
				if( last_image.share ) {
					share_image = last_image;
				}
			}
			if( share_image != null ) {
				Uri last_image_uri = share_image.uri;
				if( MyDebug.LOG )
					Log.d(TAG, "Share: " + last_image_uri);
				Intent intent = new Intent(Intent.ACTION_SEND);
				intent.setType("image/jpeg");
				intent.putExtra(Intent.EXTRA_STREAM, last_image_uri);
				main_activity.startActivity(Intent.createChooser(intent, "Photo"));
			}
			clearLastImages();
			preview.startCameraPreview();
		}
	}

	@TargetApi(Build.VERSION_CODES.LOLLIPOP)
	private void trashImage(boolean image_saf, Uri image_uri, String image_name) {
		if( MyDebug.LOG )
			Log.d(TAG, "trashImage");
		Preview preview  = main_activity.getPreview();
		if( image_saf && image_uri != null ) {
			if( MyDebug.LOG )
				Log.d(TAG, "Delete: " + image_uri);
			File file = storageUtils.getFileFromDocumentUriSAF(image_uri, false); // need to get file before deleting it, as fileFromDocumentUriSAF may depend on the file still existing
			if( !DocumentsContract.deleteDocument(main_activity.getContentResolver(), image_uri) ) {
				if( MyDebug.LOG )
					Log.e(TAG, "failed to delete " + image_uri);
			}
			else {
				if( MyDebug.LOG )
					Log.d(TAG, "successfully deleted " + image_uri);
				if( file != null ) {
					// SAF doesn't broadcast when deleting them
					storageUtils.broadcastFile(file, false, false, true);
				}
			}
		}
		else if( image_name != null ) {
			if( MyDebug.LOG )
				Log.d(TAG, "Delete: " + image_name);
			File file = new File(image_name);
			if( !file.delete() ) {
				if( MyDebug.LOG )
					Log.e(TAG, "failed to delete " + image_name);
			}
			else {
				if( MyDebug.LOG )
					Log.d(TAG, "successfully deleted " + image_name);
				storageUtils.broadcastFile(file, false, false, true);
			}
		}
	}

//	void trashLastImage() {
//		if( MyDebug.LOG )
//			Log.d(TAG, "trashImage");
//		Preview preview  = main_activity.getPreview();
//		if( preview.isPreviewPaused() ) {
//			for(int i=0;i<last_images.size();i++) {
//				LastImage last_image = last_images.get(i);
//				trashImage(last_images_saf, last_image.uri, last_image.name);
//			}
//			clearLastImages();
//			preview.startCameraPreview();
//		}
//		// Calling updateGalleryIcon() immediately has problem that it still returns the latest image that we've just deleted!
//		// But works okay if we call after a delay. 100ms works fine on Nexus 7 and Galaxy Nexus, but set to 500 just to be safe.
//		final Handler handler = new Handler();
//		handler.postDelayed(new Runnable() {
//			@Override
//			public void run() {
//				main_activity.updateGalleryIcon();
//			}
//		}, 500);
//	}

	public HDRProcessor getHDRProcessor() {
		return imageSaver.getHDRProcessor();
	}

	public boolean test_set_available_memory = false;
	public long test_available_memory = 0;
}
