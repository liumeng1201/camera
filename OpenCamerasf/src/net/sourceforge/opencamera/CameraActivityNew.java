package net.sourceforge.opencamera;

import java.io.File;
import java.text.SimpleDateFormat;
import java.util.Date;
import java.util.Hashtable;
import java.util.List;
import java.util.Locale;
import java.util.Map;

import net.sourceforge.opencamera.CameraController.Size;
import android.annotation.SuppressLint;
import android.annotation.TargetApi;
import android.app.Activity;
import android.content.Context;
import android.content.Intent;
import android.content.SharedPreferences;
import android.content.pm.ActivityInfo;
import android.graphics.Bitmap;
import android.hardware.Sensor;
import android.hardware.SensorEvent;
import android.hardware.SensorEventListener;
import android.hardware.SensorManager;
import android.os.Build;
import android.os.Bundle;
import android.os.Environment;
import android.os.Handler;
import android.preference.PreferenceManager;
import android.util.DisplayMetrics;
import android.util.Log;
import android.view.GestureDetector.SimpleOnGestureListener;
import android.view.MotionEvent;
import android.view.View;
import android.view.View.OnClickListener;
import android.view.ViewConfiguration;
import android.view.ViewGroup;
import android.view.WindowManager;
import android.widget.ImageButton;
import android.widget.RelativeLayout;

//import com.jizhi.android.qiujieda.R;
//import com.jizhi.android.qiujieda.component.camera.CropActivity;
//import com.jizhi.android.qiujieda.utils.Utils;

class MyDebug {
	static final boolean LOG = false;
}

public class CameraActivityNew extends Activity {
	private static final String TAG = "MainActivity";
	private SensorManager mSensorManager = null;
	private Sensor mSensorAccelerometer = null;
	private Preview preview = null;
	private boolean supports_auto_stabilise = false;
	private boolean camera_in_background = false;
	private Map<Integer, Bitmap> preloaded_bitmap_resources = new Hashtable<Integer, Bitmap>();

	private ToastBoxer screen_locked_toast = new ToastBoxer();
	ToastBoxer changed_auto_stabilise_toast = new ToastBoxer();

	// for testing:
	public boolean is_test = false;
	public Bitmap gallery_bitmap = null;
	public boolean failed_to_scan = false;
	
	private RelativeLayout camera_guide_layout;
	private ImageButton camera_i_know;
	private ImageButton camera_dontt;
	private ImageButton camera_close;
	private ImageButton take_photo;
	private ImageButton gallery;
	
	@Override
	protected void onCreate(Bundle savedInstanceState) {
		super.onCreate(savedInstanceState);
		setContentView(R.layout.activity_camera_new);

		setWindowFlagsForCamera();

		initViews();
		
		mSensorManager = (SensorManager) getSystemService(Context.SENSOR_SERVICE);
		if (mSensorManager.getDefaultSensor(Sensor.TYPE_ACCELEROMETER) != null) {
			mSensorAccelerometer = mSensorManager.getDefaultSensor(Sensor.TYPE_ACCELEROMETER);
		}

		DisplayMetrics dm = new DisplayMetrics();
		getWindowManager().getDefaultDisplay().getMetrics(dm);
		CameraController.Size screenSize = new Size(dm.widthPixels, dm.heightPixels);
		preview = new Preview(this, savedInstanceState, screenSize) {
			@Override
			void takePhotoDone(String filepath) {
				// 图片拍摄完成跳转到图片裁剪界面
//				Intent intent = new Intent(CameraActivityNew.this, CropActivity.class);
//				intent.putExtra("photo_orignal", filepath);
//				intent.putExtra("getPhotoFromGallery", false);
//				startActivity(intent);
			}
		};
		((ViewGroup) findViewById(R.id.preview)).addView(preview.getView());
	}
	
	private void initViews() {
//		camera_guide_layout = (RelativeLayout) findViewById(R.id.camera_guide_layout);
//		camera_i_know = (ImageButton) findViewById(R.id.camera_i_know);
//		camera_dontt = (ImageButton) findViewById(R.id.camera_dontt);
		camera_close = (ImageButton) findViewById(R.id.camera_close);
		take_photo = (ImageButton) findViewById(R.id.take_photo);
		gallery = (ImageButton) findViewById(R.id.gallery);
		
//		camera_i_know.setOnClickListener(new OnClickListener() {
//			@Override
//			public void onClick(View v) {
//				hideGuides(false);
//				camera_close.setClickable(true);
//				take_photo.setClickable(true);
//				gallery.setClickable(true);
//			}
//		});
//		camera_dontt.setOnClickListener(new OnClickListener() {
//			@Override
//			public void onClick(View v) {
//				hideGuides(true);
//				camera_close.setClickable(true);
//				take_photo.setClickable(true);
//				gallery.setClickable(true);
//			}
//		});
		
		showGuides();
	}
	
	/**
	 * 隐藏指导图片
	 * 
	 * @param neverShow
	 *            是否永久隐藏
	 */
	private void hideGuides(boolean neverShow) {
		camera_guide_layout.setVisibility(View.GONE);
		if (neverShow) {
//			SharedPreferences sharedPreferences = getSharedPreferences(Utils.APP_PREFERENCE, Activity.MODE_PRIVATE);
//			SharedPreferences.Editor editor = sharedPreferences.edit();
//			editor.putBoolean("show_camera_guide", false);
//			editor.commit();
		}
	}
	
	/**
	 * 读取配置文件，判断是否要显示拍照指导图片
	 */
	private void showGuides() {
//		SharedPreferences sharedPreferences = getSharedPreferences(Utils.APP_PREFERENCE, Activity.MODE_PRIVATE);
//		boolean showGuide = sharedPreferences.getBoolean("show_camera_guide", true);
//		if (showGuide) {
//			camera_guide_layout.setVisibility(View.VISIBLE);
//			camera_close.setClickable(false);
//			take_photo.setClickable(false);
//			gallery.setClickable(false);
//		}
	}

	@Override
	protected void onDestroy() {
		for (Map.Entry<Integer, Bitmap> entry : preloaded_bitmap_resources.entrySet()) {
			entry.getValue().recycle();
		}
		preloaded_bitmap_resources.clear();
		super.onDestroy();
	}

	private SensorEventListener accelerometerListener = new SensorEventListener() {
		@Override
		public void onAccuracyChanged(Sensor sensor, int accuracy) {
		}

		@Override
		public void onSensorChanged(SensorEvent event) {
			preview.onAccelerometerSensorChanged(event);
		}
	};

	@Override
	protected void onResume() {
		super.onResume();
		mSensorManager.registerListener(accelerometerListener, mSensorAccelerometer, SensorManager.SENSOR_DELAY_NORMAL);
		layoutUI();
		preview.onResume();
	}

	@Override
	public void onWindowFocusChanged(boolean hasFocus) {
		super.onWindowFocusChanged(hasFocus);
		if (!this.camera_in_background && hasFocus) {
			initImmersiveMode();
		}
	}

	@Override
	protected void onPause() {
		super.onPause();
		mSensorManager.unregisterListener(accelerometerListener);
		preview.resetLocation();
		preview.onPause();
	}

	public void layoutUI() {
		this.preview.updateUIPlacement();
		// TODO 仅横屏
		int ui_rotation = 0;// (360 - relative_orientation) % 360;
		preview.setUIRotation(ui_rotation);

		if (preview != null) {
			((ImageButton) findViewById(R.id.take_photo)).setImageResource(R.drawable.take_photo_selector);
		}
	}

	public void clickedTakePhoto(View view) {
		this.takePicture();
	}

	private String flash_option = "flash_off";

	public void clickedPopupSettings(View view) {
		if (preview.getCameraController() == null) {
			return;
		}

		List<String> supported_flash_values = preview.getSupportedFlashValues();
		if (supported_flash_values != null && supported_flash_values.size() > 0) {
			for (String option : supported_flash_values) {
				if (option.equalsIgnoreCase("flash_torch")) {
					if (flash_option.equalsIgnoreCase("flash_off")) {
						flash_option = "flash_torch";
					} else {
						flash_option = "flash_off";
					}
					preview.updateFlash(flash_option);
				}
			}
		}
	}

	public void updateForSettings() {
		updateForSettings(null);
	}

	public void updateForSettings(String toast_message) {
		String saved_focus_value = null;
		if (preview.getCameraController() != null && preview.isVideo()
				&& !preview.focusIsVideo()) {
			saved_focus_value = preview.getCurrentFocusValue();
		}

		boolean need_reopen = false;
		if (preview.getCameraController() != null) {
			String scene_mode = preview.getCameraController().getSceneMode();
			if (MyDebug.LOG) {
				Log.d(TAG, "scene mode was: " + scene_mode);
			}
			String key = getSceneModePreferenceKey();
			SharedPreferences sharedPreferences = PreferenceManager.getDefaultSharedPreferences(this);
			String value = sharedPreferences.getString(key, preview.getCameraController().getDefaultSceneMode());
			if (!value.equals(scene_mode)) {
				if (MyDebug.LOG) {
					Log.d(TAG, "scene mode changed to: " + value);
				}
				need_reopen = true;
			}
		}

		layoutUI(); // needed in case we've changed left/right handed UI
		if (need_reopen || preview.getCameraController() == null) {
			preview.onPause();
			preview.onResume(toast_message);
		} else {
			preview.setCameraDisplayOrientation();
			preview.pausePreview();
			preview.setupCamera(toast_message, false);
		}

		if (saved_focus_value != null) {
			if (MyDebug.LOG) {
				Log.d(TAG, "switch focus back to: " + saved_focus_value);
			}
			preview.updateFocus(saved_focus_value, true, false);
		}
	}

	boolean cameraInBackground() {
		return this.camera_in_background;
	}

	boolean usingKitKatImmersiveMode() {
		if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.KITKAT) {
			SharedPreferences sharedPreferences = PreferenceManager.getDefaultSharedPreferences(this);
			String immersive_mode = sharedPreferences.getString(
					getImmersiveModePreferenceKey(), "immersive_mode_low_profile");
			if (immersive_mode.equals("immersive_mode_gui")
					|| immersive_mode.equals("immersive_mode_everything")) {
				return true;
			}
		}
		return false;
	}

	private Handler immersive_timer_handler = null;
	private Runnable immersive_timer_runnable = null;
	private void setImmersiveTimer() {
		if (immersive_timer_handler != null && immersive_timer_runnable != null) {
			immersive_timer_handler.removeCallbacks(immersive_timer_runnable);
		}
		immersive_timer_handler = new Handler();
		immersive_timer_handler.postDelayed(
				immersive_timer_runnable = new Runnable() {
					@Override
					public void run() {
						if (!camera_in_background && usingKitKatImmersiveMode()) {
							setImmersiveMode(true);
						}
					}
				}, 5000);
	}

	void initImmersiveMode() {
		if (!usingKitKatImmersiveMode()) {
			setImmersiveMode(true);
		} else {
			// don't start in immersive mode, only after a timer
			setImmersiveTimer();
		}
	}

	@TargetApi(Build.VERSION_CODES.KITKAT)
	void setImmersiveMode(boolean on) {
		if (MyDebug.LOG) {
			Log.d(TAG, "setImmersiveMode: " + on);
		}
		if (on) {
			if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.KITKAT
					&& usingKitKatImmersiveMode()) {
				getWindow().getDecorView().setSystemUiVisibility(
						View.SYSTEM_UI_FLAG_IMMERSIVE
								| View.SYSTEM_UI_FLAG_LAYOUT_STABLE
								| View.SYSTEM_UI_FLAG_HIDE_NAVIGATION
								| View.SYSTEM_UI_FLAG_FULLSCREEN);
			} else {
				SharedPreferences sharedPreferences = PreferenceManager
						.getDefaultSharedPreferences(this);
				String immersive_mode = sharedPreferences.getString(
						getImmersiveModePreferenceKey(), "immersive_mode_low_profile");
				if (immersive_mode.equals("immersive_mode_low_profile")) {
					getWindow().getDecorView().setSystemUiVisibility(View.SYSTEM_UI_FLAG_LOW_PROFILE);
				} else {
					getWindow().getDecorView().setSystemUiVisibility(0);
				}
			}
		} else
			getWindow().getDecorView().setSystemUiVisibility(0);
	}

	private void setWindowFlagsForCamera() {
		if (MyDebug.LOG) {
			Log.d(TAG, "setWindowFlagsForCamera");
		}
		SharedPreferences sharedPreferences = PreferenceManager.getDefaultSharedPreferences(this);

		// force to landscape mode
		setRequestedOrientation(ActivityInfo.SCREEN_ORIENTATION_LANDSCAPE);
		// keep screen active - see
		// http://stackoverflow.com/questions/2131948/force-screen-on
		getWindow().addFlags(WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON);
		if (sharedPreferences.getBoolean(getShowWhenLockedPreferenceKey(), true)) {
			if (MyDebug.LOG) {
				Log.d(TAG, "do show when locked");
			}
			// keep Open Camera on top of screen-lock (will still need to unlock
			// when going to gallery or settings)
			getWindow().addFlags(WindowManager.LayoutParams.FLAG_SHOW_WHEN_LOCKED);
		} else {
			if (MyDebug.LOG) {
				Log.d(TAG, "don't show when locked");
			}
			getWindow().clearFlags(WindowManager.LayoutParams.FLAG_SHOW_WHEN_LOCKED);
		}

		// set screen to max brightness - see
		// http://stackoverflow.com/questions/11978042/android-screen-brightness-max-value
		// done here rather than onCreate, so that changing it in preferences
		// takes effect without restarting app
		{
			WindowManager.LayoutParams layout = getWindow().getAttributes();
			if (sharedPreferences.getBoolean(getMaxBrightnessPreferenceKey(), true)) {
				layout.screenBrightness = WindowManager.LayoutParams.BRIGHTNESS_OVERRIDE_FULL;
			} else {
				layout.screenBrightness = WindowManager.LayoutParams.BRIGHTNESS_OVERRIDE_NONE;
			}
			getWindow().setAttributes(layout);
		}

		initImmersiveMode();
		camera_in_background = false;
	}

	public void clickedGallery(View view) {
		// 从相册选取图片
//		Intent intent = new Intent(CameraActivityNew.this, CropActivity.class);
//		intent.putExtra("getPhotoFromGallery", true);
//		startActivity(intent);
	}
	
	public void clickedClose(View view) {
		finish();
	}

	private void takePicture() {
		this.preview.takePicturePressed();
	}

	class MyGestureDetector extends SimpleOnGestureListener {
		@Override
		public boolean onFling(MotionEvent e1, MotionEvent e2, float velocityX,
				float velocityY) {
			try {
				if (MyDebug.LOG) {
					Log.d(TAG, "from " + e1.getX() + " , " + e1.getY() + " to " + e2.getX() + " , " + e2.getY());
				}
				final ViewConfiguration vc = ViewConfiguration.get(CameraActivityNew.this);
				final float scale = getResources().getDisplayMetrics().density;
				final int swipeMinDistance = (int) (160 * scale + 0.5f);
				final int swipeThresholdVelocity = vc.getScaledMinimumFlingVelocity();
				if (MyDebug.LOG) {
					Log.d(TAG, "from " + e1.getX() + " , " + e1.getY() + " to " + e2.getX() + " , " + e2.getY());
					Log.d(TAG, "swipeMinDistance: " + swipeMinDistance);
				}
				float xdist = e1.getX() - e2.getX();
				float ydist = e1.getY() - e2.getY();
				float dist2 = xdist * xdist + ydist * ydist;
				float vel2 = velocityX * velocityX + velocityY * velocityY;
				if (dist2 > swipeMinDistance * swipeMinDistance && vel2 > swipeThresholdVelocity * swipeThresholdVelocity) {
					preview.showToast(screen_locked_toast, R.string.unlocked);
				}
			} catch (Exception e) {
			}
			return false;
		}

		@Override
		public boolean onDown(MotionEvent e) {
			preview.showToast(screen_locked_toast, R.string.screen_is_locked);
			return true;
		}
	}

	@Override
	protected void onSaveInstanceState(Bundle state) {
		if (MyDebug.LOG) {
			Log.d(TAG, "onSaveInstanceState");
		}
		super.onSaveInstanceState(state);
		if (this.preview != null) {
			preview.onSaveInstanceState(state);
		}
	}

	public static final int MEDIA_TYPE_IMAGE = 1;
	public static final int MEDIA_TYPE_VIDEO = 2;
	private String getSaveLocation() {
		SharedPreferences sharedPreferences = PreferenceManager.getDefaultSharedPreferences(this);
		String folder_name = sharedPreferences.getString(getSaveLocationPreferenceKey(), "qiujieda/toask");
		return folder_name;
	}

	static File getBaseFolder() {
		return Environment.getExternalStoragePublicDirectory(Environment.DIRECTORY_DCIM);
	}

	static File getImageFolder(String folder_name) {
		File file = null;
		if (folder_name.length() > 0
				&& folder_name.lastIndexOf('/') == folder_name.length() - 1) {
			// ignore final '/' character
			folder_name = folder_name.substring(0, folder_name.length() - 1);
		}
		if (folder_name.startsWith("/")) {
			file = new File(folder_name);
		} else {
			file = new File(getBaseFolder(), folder_name);
		}
		return file;
	}

	public File getImageFolder() {
		String folder_name = getSaveLocation();
		return getImageFolder(folder_name);
	}

	/** Create a File for saving an image or video */
	@SuppressLint("SimpleDateFormat")
	public File getOutputMediaFile(int type) {
		// To be safe, you should check that the SDCard is mounted
		// using Environment.getExternalStorageState() before doing this.
		File mediaStorageDir = getImageFolder();
		if (!mediaStorageDir.exists()) {
			if (!mediaStorageDir.mkdirs()) {
				if (MyDebug.LOG) {
					Log.e(TAG, "failed to create directory");
				}
				return null;
			}
		}

		// Create a media file name
		String timeStamp = new SimpleDateFormat("yyyyMMdd_HHmmss", Locale.US).format(new Date());
		String index = "";
		File mediaFile = null;
		for (int count = 1; count <= 100; count++) {
			if (type == MEDIA_TYPE_IMAGE) {
				mediaFile = new File(mediaStorageDir.getPath() + File.separator + "IMG_" + timeStamp + index + ".jpg");
			} else if (type == MEDIA_TYPE_VIDEO) {
				mediaFile = new File(mediaStorageDir.getPath() + File.separator + "VID_" + timeStamp + index + ".mp4");
			} else {
				return null;
			}
			if (!mediaFile.exists()) {
				break;
			}
			index = "_" + count;
		}

		if (MyDebug.LOG) {
			Log.d(TAG, "getOutputMediaFile returns: " + mediaFile);
		}
		// TODO filepath
		return mediaFile;
	}

	public boolean supportsAutoStabilise() {
		return this.supports_auto_stabilise;
	}

	public Preview getPreview() {
		return this.preview;
	}

	public static String getFirstTimePreferenceKey() {
		return "done_first_time";
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

	public static String getVideoQualityPreferenceKey(int cameraId) {
		return "video_quality_" + cameraId;
	}

	public static String getIsVideoPreferenceKey() {
		return "is_video";
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

	public static String getISOPreferenceKey() {
		return "preference_iso";
	}

	public static String getQualityPreferenceKey() {
		return "preference_quality";
	}

	public static String getAutoStabilisePreferenceKey() {
		return "preference_auto_stabilise";
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

	public static String getUIPlacementPreferenceKey() {
		return "preference_ui_placement";
	}

	public static String getPausePreviewPreferenceKey() {
		return "preference_pause_preview";
	}

	public static String getThumbnailAnimationPreferenceKey() {
		return "preference_thumbnail_animation";
	}

	public static String getShowWhenLockedPreferenceKey() {
		return "preference_show_when_locked";
	}

	public static String getMaxBrightnessPreferenceKey() {
		return "preference_max_brightness";
	}

	public static String getSaveLocationPreferenceKey() {
		return "preference_save_location";
	}

	public static String getShowZoomControlsPreferenceKey() {
		return "preference_show_zoom_controls";
	}

	public static String getShowZoomSliderControlsPreferenceKey() {
		return "preference_show_zoom_slider_controls";
	}

	public static String getShowAngleLinePreferenceKey() {
		return "preference_show_angle_line";
	}

	public static String getShowGridPreferenceKey() {
		return "preference_grid";
	}

	public static String getShowCropGuidePreferenceKey() {
		return "preference_crop_guide";
	}

	public static String getFaceDetectionPreferenceKey() {
		return "preference_face_detection";
	}

	public static String getVideoStabilizationPreferenceKey() {
		return "preference_video_stabilization";
	}

	public static String getVideoBitratePreferenceKey() {
		return "preference_video_bitrate";
	}

	public static String getVideoFPSPreferenceKey() {
		return "preference_video_fps";
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

	public static String getImmersiveModePreferenceKey() {
		return "preference_immersive_mode";
	}
}
