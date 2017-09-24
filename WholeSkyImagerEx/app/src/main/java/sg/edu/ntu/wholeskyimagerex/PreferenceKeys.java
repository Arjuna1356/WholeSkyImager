package sg.edu.ntu.wholeskyimagerex;

/** Stores all of the string keys used for SharedPreferences.
 */
public class PreferenceKeys
{
    // must be static, to safely call from other Activities
	
	// arguably the static methods here that don't receive an argument could just be static final strings? Though we may want to change some of them to be cameraId-specific in future

	/** If this preference is set, no longer show the intro dialog.
	 */
    public static String getFirstTimePreferenceKey() {
        return "done_first_time";
    }
    
	/** If this preference is set, no longer show the auto-stabilise info dialog.
	 */
    public static String getAutoStabiliseInfoPreferenceKey() {
        return "done_auto_stabilise_info";
    }
    
	/** If this preference is set, no longer show the HDR info dialog.
	 */
    public static String getHDRInfoPreferenceKey() {
        return "done_hdr_info";
    }

    public static String getUseCamera2PreferenceKey() {
    	return "preference_use_camera2";
    }

    public static String getFlashPreferenceKey(int cameraId) {
    	return "flash_value_" + cameraId;
    }

    public static String getFocusPreferenceKey(int cameraId) {
    	return "focus_value_" + cameraId;
    }

    public static String getResolutionPreferenceKey(int cameraId) {
    	return "camera_resolution_" + cameraId;
    }

    public static String getExposurePreferenceKey() {
    	return "preference_exposure";
    }

    public static String getColorEffectPreferenceKey() {
    	return "preference_color_effect";
    }

    public static String getSceneModePreferenceKey() {
    	return "preference_scene_mode";
    }

    public static String getWhiteBalancePreferenceKey() {
    	return "preference_white_balance";
    }

    public static String getWhiteBalanceTemperaturePreferenceKey() {
        return "preference_white_balance_temperature";
    }

    public static String getISOPreferenceKey() {
    	return "preference_iso";
    }
    
    public static String getExposureTimePreferenceKey() {
    	return "preference_exposure_time";
    }
    
    public static String getRawPreferenceKey() {
    	return "preference_raw";
    }
    
    public static String getExpoBracketingNImagesPreferenceKey() {
    	return "preference_expo_bracketing_n_images";
    }

    public static String getExpoBracketingStopsPreferenceKey() {
    	return "preference_expo_bracketing_stops";
    }
    
    public static String getQualityPreferenceKey() {
    	return "preference_quality";
    }
    
    public static String getPhotoModePreferenceKey() {
    	return "preference_photo_mode";
    }

    public static String getHDRSaveExpoPreferenceKey() {
    	return "preference_hdr_save_expo";
    }

    public static String getLocationPreferenceKey() {
    	return "preference_location";
    }
    
    public static String getGPSDirectionPreferenceKey() {
    	return "preference_gps_direction";
    }
    
    public static String getRequireLocationPreferenceKey() {
    	return "preference_require_location";
    }
    
    public static String getStampPreferenceKey() {
    	return "preference_stamp";
    }

    public static String getStampDateFormatPreferenceKey() {
    	return "preference_stamp_dateformat";
    }

    public static String getStampTimeFormatPreferenceKey() {
    	return "preference_stamp_timeformat";
    }

    public static String getStampGPSFormatPreferenceKey() {
    	return "preference_stamp_gpsformat";
    }

    public static String getTextStampPreferenceKey() {
    	return "preference_textstamp";
    }

    public static String getStampFontSizePreferenceKey() {
    	return "preference_stamp_fontsize";
    }

    public static String getStampFontColorPreferenceKey() {
    	return "preference_stamp_font_color";
    }

    public static String getStampStyleKey() {
    	return "preference_stamp_style";
    }

    public static String getBackgroundPhotoSavingPreferenceKey() {
    	return "preference_background_photo_saving";
    }
    
    public static String getCamera2FakeFlashPreferenceKey() {
    	return "preference_camera2_fake_flash";
    }

    public static String getCamera2FastBurstPreferenceKey() {
        return "preference_camera2_fast_burst";
    }

    public static String getPausePreviewPreferenceKey() {
    	return "preference_pause_preview";
    }

    public static String getThumbnailAnimationPreferenceKey() {
    	return "preference_thumbnail_animation";
    }

    public static String getStartupFocusPreferenceKey() {
    	return "preference_startup_focus";
    }
    
    public static String getUsingSAFPreferenceKey() {
    	return "preference_using_saf";
    }

    public static String getSaveLocationPreferenceKey() {
    	return "preference_save_location";
    }

    public static String getSaveLocationSAFPreferenceKey() {
    	return "preference_save_location_saf";
    }

    public static String getSavePhotoPrefixPreferenceKey() {
    	return "preference_save_photo_prefix";
    }
    
    public static String getSaveZuluTimePreferenceKey() {
    	return "preference_save_zulu_time";
    }

    public static String getCalibratedLevelAnglePreferenceKey() {
        return "preference_calibrate_level_angle";
    }

    public static String getPreviewSizePreferenceKey() {
    	return "preference_preview_size";
    }

    public static String getRotatePreviewPreferenceKey() {
    	return "preference_rotate_preview";
    }

    public static String getLockOrientationPreferenceKey() {
    	return "preference_lock_orientation";
    }

    public static String getTimerPreferenceKey() {
    	return "preference_timer";
    }
    
    public static String getBurstModePreferenceKey() {
    	return "preference_burst_mode";
    }
    
    public static String getBurstIntervalPreferenceKey() {
    	return "preference_burst_interval";
    }
    
    public static String getShutterSoundPreferenceKey() {
    	return "preference_shutter_sound";
    }
}
