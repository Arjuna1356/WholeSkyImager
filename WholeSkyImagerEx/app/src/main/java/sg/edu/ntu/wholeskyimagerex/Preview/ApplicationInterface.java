package sg.edu.ntu.wholeskyimagerex.Preview;

import android.content.Context;
import android.location.Location;
import android.util.Pair;

import java.util.Date;
import java.util.List;

/** Provides communication between the Preview and the rest of the application
 *  - so in theory one can drop the Preview/ (and CameraController/) classes
 *  into a new application, by providing an appropriate implementation of this
 *  ApplicationInterface.
 */
public interface ApplicationInterface {
	class NoFreeStorageException extends Exception
    {
		private static final long serialVersionUID = -2021932609486148748L;
	}

	// methods that request information
	Context getContext(); // get the application context
	boolean useCamera2(); // should Android 5's Camera 2 API be used?
	Location getLocation(); // get current location - null if not available (or you don't care about geotagging)
	// for all of the get*Pref() methods, you can use Preview methods to get the supported values (e.g., getSupportedSceneModes())
	// if you just want a default or don't really care, see the comments for each method for a default or possible options
	// if Preview doesn't support the requested setting, it will check this, and choose its own
	int getCameraIdPref(); // camera to use, from 0 to getCameraControllerManager().getNumberOfCameras()
	String getFlashPref(); // flash_off, flash_auto, flash_on, flash_torch, flash_red_eye
	String getFocusPref(); // focus_mode_auto, focus_mode_infinity, focus_mode_macro, focus_mode_locked, focus_mode_fixed, focus_mode_manual2, focus_mode_edof, focus_mode_continuous_video
	String getSceneModePref(); // "auto" for default (strings correspond to Android's scene mode constants in android.hardware.Camera.Parameters)
	String getColorEffectPref(); // "node" for default (strings correspond to Android's color effect constants in android.hardware.Camera.Parameters)
	String getWhiteBalancePref(); // "auto" for default (strings correspond to Android's white balance constants in android.hardware.Camera.Parameters)
	int getWhiteBalanceTemperaturePref();
	String getISOPref(); // "auto" for auto-ISO, otherwise a numerical value; see documentation for Preview.supportsISORange().
	int getExposureCompensationPref(); // 0 for default
	Pair<Integer, Integer> getCameraResolutionPref(); // return null to let Preview choose size
	int getImageQualityPref(); // jpeg quality for taking photos; "90" is a recommended default
	String getPreviewSizePref(); // "preference_preview_size_wysiwyg" is recommended (preview matches aspect ratio of photo resolution as close as possible), but can also be "preference_preview_size_display" to maximise the preview size
	String getPreviewRotationPref(); // return "0" for default; use "180" to rotate the preview 180 degrees
	String getLockOrientationPref(); // return "none" for default; use "portrait" or "landscape" to lock photos/videos to that orientation
	boolean getPausePreviewPref(); // whether to pause the preview after taking a photo
	boolean getShutterSoundPref(); // whether to play sound when taking photo
	boolean getStartupFocusPref(); // whether to do autofocus on startup
	long getTimerPref(); // time in ms for timer (so 0 for off)
	String getRepeatPref(); // return number of times to repeat photo in a row (as a string), so "1" for default; return "unlimited" for unlimited
	long getRepeatIntervalPref(); // time in ms between repeat
	boolean getGeotaggingPref(); // whether to geotag photos
	boolean getRequireLocationPref(); // if getGeotaggingPref() returns true, and this method returns true, then phot/video will only be taken if location data is available
	double getCalibratedLevelAngle(); // set to non-zero to calibrate the accelerometer used for the level angles
	// Camera2 only modes:
	long getExposureTimePref(); // only called if getISOPref() is not "default"
	float getFocusDistancePref();
	boolean isExpoBracketingPref(); // whether to enable burst photos with expo bracketing
    int getExpoBracketingNImagesPref(); // how many images to take for exposure bracketing
    double getExpoBracketingStopsPref(); // stops per image for exposure bracketing
	boolean getOptimiseAEForDROPref(); // see CameraController doc for setOptimiseAEForDRO().
	boolean useCamera2FakeFlash(); // whether to enable CameraController.setUseCamera2FakeFlash() for Camera2 API
	boolean useCamera2FastBurst(); // whether to enable Camera2's captureBurst() for faster taking of expo-bracketing photos (generally should be true, but some devices have problems with captureBurst())

	// methods that transmit information/events (up to the Application whether to do anything or not)
	void onFailedStartPreview(); // called if failed to start camera preview
	void onCameraError(); // called if the camera closes due to serious error.
	void onPhotoError(); // callback for failing to take a photo

	// methods that request actions
	// the set/clear*Pref() methods are called if Preview decides to override the requested pref (because Camera device doesn't support requested pref) (clear*Pref() is called if the feature isn't supported at all)
	// the application can use this information to update its preferences
	void setCameraIdPref(int cameraId);
	void setFlashPref(String flash_value);
	void setFocusPref(String focus_value);
	void setSceneModePref(String scene_mode);
	void clearSceneModePref();
	void setColorEffectPref(String color_effect);
	void clearColorEffectPref();
	void setWhiteBalancePref(String white_balance);
	void clearWhiteBalancePref();
	void setWhiteBalanceTemperaturePref(int white_balance_temperature);
	void setISOPref(String iso);
	void clearISOPref();
	void setExposureCompensationPref(int exposure);
	void clearExposureCompensationPref();
	void setCameraResolutionPref(int width, int height);
	void requestCameraPermission(); // for Android 6+: called when trying to open camera, but CAMERA permission not available
	// Camera2 only modes:
	void setExposureTimePref(long exposure_time);
	void clearExposureTimePref();
	void setFocusDistancePref(float focus_distance);
	
	// callbacks
	boolean onPictureTaken(byte[] data, Date current_date, String timeStamp);
	boolean onBurstPictureTaken(List<byte[]> images, Date current_date, String timeStamp);

	void sendImages(String timeStamp, int wahrsisModelNr);
}
