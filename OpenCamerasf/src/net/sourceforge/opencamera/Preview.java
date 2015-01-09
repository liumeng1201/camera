package net.sourceforge.opencamera;

import java.io.File;
import java.io.FileNotFoundException;
import java.io.FileOutputStream;
import java.io.IOException;
import java.io.OutputStream;
import java.text.DateFormat;
import java.util.ArrayList;
import java.util.Collections;
import java.util.Comparator;
import java.util.Date;
import java.util.List;
import java.util.Locale;
import java.util.Vector;

import android.app.Activity;
import android.content.Context;
import android.content.Intent;
import android.content.SharedPreferences;
import android.content.res.Configuration;
import android.content.res.Resources;
import android.graphics.Bitmap;
import android.graphics.BitmapFactory;
import android.graphics.Canvas;
import android.graphics.Color;
import android.graphics.Matrix;
import android.graphics.Paint;
import android.graphics.Paint.Align;
import android.graphics.Point;
import android.graphics.Rect;
import android.graphics.RectF;
import android.hardware.SensorEvent;
import android.hardware.SensorManager;
import android.location.Location;
import android.media.CamcorderProfile;
import android.media.ExifInterface;
import android.net.Uri;
import android.os.Build;
import android.os.Bundle;
import android.os.Handler;
import android.preference.PreferenceManager;
import android.provider.MediaStore;
import android.util.Log;
import android.util.Pair;
import android.util.SparseArray;
import android.view.Display;
import android.view.MotionEvent;
import android.view.OrientationEventListener;
import android.view.ScaleGestureDetector;
import android.view.Surface;
import android.view.SurfaceHolder;
import android.view.SurfaceView;
import android.view.View;
import android.view.WindowManager;
import android.widget.ImageButton;
import android.widget.SeekBar;
import android.widget.SeekBar.OnSeekBarChangeListener;
import android.widget.Toast;
import android.widget.ZoomControls;

public class Preview implements SurfaceHolder.Callback {
	private static final String TAG = "Preview";

	private boolean using_android_l = false;

	private SurfaceView surfaceView = null;

	private Paint p = new Paint();
	private Matrix camera_to_preview_matrix = new Matrix();
	private Matrix preview_to_camera_matrix = new Matrix();
	private RectF face_rect = new RectF();
	private Rect text_bounds = new Rect();
	private double preview_targetRatio = 0.0;

	private boolean ui_placement_right = true;

	private boolean app_is_paused = true;
	private SurfaceHolder mHolder = null;
	private boolean has_surface = false;
	private boolean has_aspect_ratio = false;
	private double aspect_ratio = 0.0f;
	private CameraControllerManager camera_controller_manager = null;
	private CameraController camera_controller = null;
	private int cameraId = 0;
	private boolean is_video = false;
	private boolean has_current_fps_range = false;

	private final int PHASE_NORMAL = 0;
	private final int PHASE_TIMER = 1;
	private final int PHASE_TAKING_PHOTO = 2;
	private final int PHASE_PREVIEW_PAUSED = 3; // the paused state after taking
												// a photo
	private int phase = PHASE_NORMAL;
	private long take_photo_time = 0;
	private int remaining_burst_photos = 0;

	private boolean requested_preview_size = false; // Android L only
	private int requested_preview_size_w = 0; // Android L only
	private int requested_preview_size_h = 0; // Android L only
	private int surface_holder_w = 0;
	private int surface_holder_h = 0;
	private boolean is_preview_started = false;
	// private boolean is_preview_paused = false; // whether we are in the
	// paused state after taking a photo
	private String preview_image_name = null;
	private Bitmap thumbnail = null; // thumbnail of last picture taken
	private boolean thumbnail_anim = false; // whether we are displaying the
											// thumbnail animation
	private long thumbnail_anim_start_ms = -1; // time that the thumbnail
												// animation started
	private RectF thumbnail_anim_src_rect = new RectF();
	private RectF thumbnail_anim_dst_rect = new RectF();
	private Matrix thumbnail_anim_matrix = new Matrix();
	private int[] gui_location = new int[2];

	private int current_orientation = 0; // orientation received by
											// onOrientationChanged
	private int current_rotation = 0; // orientation relative to camera's
										// orientation (used for
										// parameters.setRotation())
	private boolean has_level_angle = false;
	private double level_angle = 0.0f;
	private double orig_level_angle = 0.0f;

	private boolean has_zoom = false;
	private int zoom_factor = 0;
	private int max_zoom_factor = 0;
	private ScaleGestureDetector scaleGestureDetector;
	private List<Integer> zoom_ratios = null;
	private boolean touch_was_multitouch = false;

	private List<String> supported_flash_values = null; // our "values" format
	private int current_flash_index = -1; // this is an index into the
											// supported_flash_values array, or
											// -1 if no flash modes available

	private List<String> supported_focus_values = null; // our "values" format
	private int current_focus_index = -1; // this is an index into the
											// supported_focus_values array, or
											// -1 if no focus modes available
	private int max_num_focus_areas = 0;

	private boolean is_exposure_lock_supported = false;

	private List<String> color_effects = null;
	private List<String> scene_modes = null;
	private List<String> white_balances = null;
	private List<String> isos = null;
	private List<String> exposures = null;
	private int min_exposure = 0;
	private int max_exposure = 0;

	private List<CameraController.Size> supported_preview_sizes = null;

	private List<CameraController.Size> sizes = null;
	private int current_size_index = -1; // this is an index into the sizes
											// array, or -1 if sizes not yet set

	// video_quality can either be:
	// - an int, in which case it refers to a CamcorderProfile
	// - of the form [CamcorderProfile]_r[width]x[height] - we use the
	// CamcorderProfile as a base, and override the video resolution - this is
	// needed to support resolutions which don't have corresponding camcorder
	// profiles
	private List<String> video_quality = null;
	private int current_video_quality = -1; // this is an index into the
											// video_quality array, or -1 if not
											// found (though this shouldn't
											// happen?)
	private List<CameraController.Size> video_sizes = null;

	private Location location = null;
	private boolean has_set_location = false;
	private float location_accuracy = 0.0f;
	private Bitmap location_bitmap = null;
	private Bitmap location_off_bitmap = null;
	private Rect location_dest = new Rect();

	private ToastBoxer flash_toast = new ToastBoxer();
	private ToastBoxer focus_toast = new ToastBoxer();
	private ToastBoxer take_photo_toast = new ToastBoxer();
	private ToastBoxer change_exposure_toast = new ToastBoxer();

	private int ui_rotation = 0;

	private boolean supports_face_detection = false;
	private boolean using_face_detection = false;
	private CameraController.Face[] faces_detected = null;
	private boolean supports_video_stabilization = false;
	private boolean can_disable_shutter_sound = false;
	private boolean has_focus_area = false;
	private int focus_screen_x = 0;
	private int focus_screen_y = 0;
	private long focus_complete_time = -1;
	private int focus_success = FOCUS_DONE;
	private static final int FOCUS_WAITING = 0;
	private static final int FOCUS_SUCCESS = 1;
	private static final int FOCUS_FAILED = 2;
	private static final int FOCUS_DONE = 3;
	private String set_flash_value_after_autofocus = "";
	private boolean successfully_focused = false;
	private long successfully_focused_time = -1;

	// accelerometer and geomagnetic sensor info
	private final float sensor_alpha = 0.8f; // for filter
	private boolean has_gravity = false;
	private float[] gravity = new float[3];
	private boolean has_geomagnetic = false;
	private float[] geomagnetic = new float[3];
	private float[] deviceRotation = new float[9];
	private float[] cameraRotation = new float[9];
	private float[] deviceInclination = new float[9];
	private float[] geo_direction = new float[3];

	// for testing:
	public int count_cameraStartPreview = 0;
	public int count_cameraAutoFocus = 0;
	public int count_cameraTakePicture = 0;
	public boolean test_has_received_location = false;
	public boolean test_fail_open_camera = false;
	public boolean test_have_angle = false;
	public float test_angle = 0.0f;
	public String test_last_saved_image = null;

	@SuppressWarnings("deprecation")
	Preview(Context context, Bundle savedInstanceState) {
		if (MyDebug.LOG) {
			Log.d(TAG, "new Preview");
		}

		this.surfaceView = new MySurfaceView(context, savedInstanceState, this);

		if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.LOLLIPOP) {
			// this.using_android_l = true;
		}
		if (MyDebug.LOG) {
			Log.d(TAG, "using_android_l?: " + using_android_l);
		}

		if (using_android_l)
			camera_controller_manager = new CameraControllerManager2(context);
		else
			camera_controller_manager = new CameraControllerManager1();

		// Install a SurfaceHolder.Callback so we get notified when the
		// underlying surface is created and destroyed.
		mHolder = surfaceView.getHolder();
		mHolder.addCallback(this);
		// deprecated setting, but required on Android versions prior to 3.0
		mHolder.setType(SurfaceHolder.SURFACE_TYPE_PUSH_BUFFERS); // deprecated

		scaleGestureDetector = new ScaleGestureDetector(context, new ScaleListener());

		if (savedInstanceState != null) {
			if (MyDebug.LOG)
				Log.d(TAG, "have savedInstanceState");
			cameraId = savedInstanceState.getInt("cameraId", 0);
			if (MyDebug.LOG)
				Log.d(TAG, "found cameraId: " + cameraId);
			if (cameraId < 0
					|| cameraId >= camera_controller_manager
							.getNumberOfCameras()) {
				if (MyDebug.LOG)
					Log.d(TAG, "cameraID not valid for "
							+ camera_controller_manager.getNumberOfCameras()
							+ " cameras!");
				cameraId = 0;
			}
			zoom_factor = savedInstanceState.getInt("zoom_factor", 0);
			if (MyDebug.LOG)
				Log.d(TAG, "found zoom_factor: " + zoom_factor);
		}

	}

	private Resources getResources() {
		return surfaceView.getResources();
	}

	public View getView() {
		return surfaceView;
	}

	private void calculateCameraToPreviewMatrix() {
		if (camera_controller == null)
			return;
		camera_to_preview_matrix.reset();
		// from
		// http://developer.android.com/reference/android/hardware/Camera.Face.html#rect
		// Need mirror for front camera
		boolean mirror = camera_controller.isFrontFacing();
		camera_to_preview_matrix.setScale(mirror ? -1 : 1, 1);
		// This is the value for android.hardware.Camera.setDisplayOrientation.
		camera_to_preview_matrix.postRotate(camera_controller
				.getDisplayOrientation());
		// Camera driver coordinates range from (-1000, -1000) to (1000, 1000).
		// UI coordinates range from (0, 0) to (width, height).
		camera_to_preview_matrix.postScale(surfaceView.getWidth() / 2000f,
				surfaceView.getHeight() / 2000f);
		camera_to_preview_matrix.postTranslate(surfaceView.getWidth() / 2f,
				surfaceView.getHeight() / 2f);
	}

	private void calculatePreviewToCameraMatrix() {
		if (camera_controller == null)
			return;
		calculateCameraToPreviewMatrix();
		if (!camera_to_preview_matrix.invert(preview_to_camera_matrix)) {
			if (MyDebug.LOG)
				Log.d(TAG, "calculatePreviewToCameraMatrix failed to invert matrix!?");
		}
	}

	private ArrayList<CameraController.Area> getAreas(float x, float y) {
		float[] coords = { x, y };
		calculatePreviewToCameraMatrix();
		preview_to_camera_matrix.mapPoints(coords);
		float focus_x = coords[0];
		float focus_y = coords[1];

		int focus_size = 50;
		if (MyDebug.LOG) {
			Log.d(TAG, "x, y: " + x + ", " + y);
			Log.d(TAG, "focus x, y: " + focus_x + ", " + focus_y);
		}
		Rect rect = new Rect();
		rect.left = (int) focus_x - focus_size;
		rect.right = (int) focus_x + focus_size;
		rect.top = (int) focus_y - focus_size;
		rect.bottom = (int) focus_y + focus_size;
		if (rect.left < -1000) {
			rect.left = -1000;
			rect.right = rect.left + 2 * focus_size;
		} else if (rect.right > 1000) {
			rect.right = 1000;
			rect.left = rect.right - 2 * focus_size;
		}
		if (rect.top < -1000) {
			rect.top = -1000;
			rect.bottom = rect.top + 2 * focus_size;
		} else if (rect.bottom > 1000) {
			rect.bottom = 1000;
			rect.top = rect.bottom - 2 * focus_size;
		}

		ArrayList<CameraController.Area> areas = new ArrayList<CameraController.Area>();
		areas.add(new CameraController.Area(rect, 1000));
		return areas;
	}

	boolean touchEvent(MotionEvent event) {
		scaleGestureDetector.onTouchEvent(event);
		if (camera_controller == null) {
			if (MyDebug.LOG)
				Log.d(TAG, "try to reopen camera due to touch");
			this.openCamera();
			return true;
		}
		CameraActivityNew main_activity = (CameraActivityNew) this.getContext();
		if (main_activity.usingKitKatImmersiveMode()) {
			main_activity.setImmersiveMode(false);
		}
		if (event.getPointerCount() != 1) {
			// multitouch_time = System.currentTimeMillis();
			touch_was_multitouch = true;
			return true;
		}
		if (event.getAction() != MotionEvent.ACTION_UP) {
			if (event.getAction() == MotionEvent.ACTION_DOWN
					&& event.getPointerCount() == 1) {
				touch_was_multitouch = false;
			}
			return true;
		}
		if (touch_was_multitouch) {
			return true;
		}
		if (!this.is_video && this.isTakingPhotoOrOnTimer()) {
			// if video, okay to refocus when recording
			return true;
		}

		// note, we always try to force start the preview (in case
		// is_preview_paused has become false)
		// except if recording video (firstly, the preview should be running;
		// secondly, we don't want to reset the phase!)
		if (!this.is_video) {
			startCameraPreview();
		}
		cancelAutoFocus();

		if (camera_controller != null && !this.using_face_detection) {
			this.has_focus_area = false;
			ArrayList<CameraController.Area> areas = getAreas(event.getX(),
					event.getY());
			if (camera_controller.setFocusAndMeteringArea(areas)) {
				if (MyDebug.LOG)
					Log.d(TAG, "set focus (and metering?) area");
				this.has_focus_area = true;
				this.focus_screen_x = (int) event.getX();
				this.focus_screen_y = (int) event.getY();
			} else {
				if (MyDebug.LOG)
					Log.d(TAG, "didn't set focus area in this mode, may have set metering");
				// don't set has_focus_area in this mode
			}
		}

		tryAutoFocus(false, true);
		return true;
	}

	// @SuppressLint("ClickableViewAccessibility") @Override

	private class ScaleListener extends ScaleGestureDetector.SimpleOnScaleGestureListener {
		@Override
		public boolean onScale(ScaleGestureDetector detector) {
			if (Preview.this.camera_controller != null && Preview.this.has_zoom) {
				Preview.this.scaleZoom(detector.getScaleFactor());
			}
			return true;
		}
	}

	public void clearFocusAreas() {
		if (MyDebug.LOG)
			Log.d(TAG, "clearFocusAreas()");
		if (camera_controller == null) {
			if (MyDebug.LOG)
				Log.d(TAG, "camera not opened!");
			return;
		}
		cancelAutoFocus();
		camera_controller.clearFocusAndMetering();
		has_focus_area = false;
		focus_success = FOCUS_DONE;
		successfully_focused = false;
	}

	public void surfaceCreated(SurfaceHolder holder) {
		if (MyDebug.LOG)
			Log.d(TAG, "surfaceCreated()");
		// The Surface has been created, acquire the camera and tell it where
		// to draw.
		this.has_surface = true;
		this.openCamera();
		surfaceView.setWillNotDraw(false); // see
											// http://stackoverflow.com/questions/2687015/extended-surfaceviews-ondraw-method-never-called
	}

	public void surfaceDestroyed(SurfaceHolder holder) {
		if (MyDebug.LOG)
			Log.d(TAG, "surfaceDestroyed()");
		// Surface will be destroyed when we return, so stop the preview.
		// Because the CameraDevice object is not a shared resource, it's very
		// important to release it when the activity is paused.
		this.has_surface = false;
		this.surface_holder_w = 0;
		this.surface_holder_h = 0;
		this.closeCamera();
	}

	private Context getContext() {
		return surfaceView.getContext();
	}

	private void closeCamera() {
		if (MyDebug.LOG) {
			Log.d(TAG, "closeCamera()");
		}
		has_focus_area = false;
		focus_success = FOCUS_DONE;
		set_flash_value_after_autofocus = "";
		successfully_focused = false;
		preview_targetRatio = 0.0;
		if (camera_controller != null) {
			// need to check for camera being non-null again - if an error
			// occurred stopping the video, we will have closed the camera, and
			// may not be able to reopen
			if (camera_controller != null) {
				// camera.setPreviewCallback(null);
				pausePreview();
				camera_controller.release();
				camera_controller = null;
			}
		}
	}

	void pausePreview() {
		if (camera_controller == null) {
			return;
		}
		this.setPreviewPaused(false);
		camera_controller.stopPreview();
		this.phase = PHASE_NORMAL;
		this.is_preview_started = false;
		showGUI(true);
	}

	private void openCamera() {
		openCamera(null);
	}

	private void openCamera(String toast_message) {
		long debug_time = 0;
		if (MyDebug.LOG) {
			Log.d(TAG, "openCamera()");
			Log.d(TAG, "cameraId: " + cameraId);
			debug_time = System.currentTimeMillis();
		}
		// need to init everything now, in case we don't open the camera (but
		// these may already be initialised from an earlier call - e.g., if we
		// are now switching to another camera)
		// n.b., don't reset has_set_location, as we can remember the location
		// when switching camera
		has_focus_area = false;
		focus_success = FOCUS_DONE;
		set_flash_value_after_autofocus = "";
		successfully_focused = false;
		preview_targetRatio = 0.0;
		scene_modes = null;
		has_zoom = false;
		max_zoom_factor = 0;
		zoom_ratios = null;
		faces_detected = null;
		supports_face_detection = false;
		using_face_detection = false;
		supports_video_stabilization = false;
		can_disable_shutter_sound = false;
		color_effects = null;
		white_balances = null;
		isos = null;
		exposures = null;
		min_exposure = 0;
		max_exposure = 0;
		sizes = null;
		current_size_index = -1;
		video_quality = null;
		current_video_quality = -1;
		supported_flash_values = null;
		current_flash_index = -1;
		supported_focus_values = null;
		current_focus_index = -1;
		max_num_focus_areas = 0;
		showGUI(true);
		if (MyDebug.LOG)
			Log.d(TAG, "done showGUI");
		if (!this.has_surface) {
			if (MyDebug.LOG) {
				Log.d(TAG, "preview surface not yet available");
			}
			return;
		}
		if (this.app_is_paused) {
			if (MyDebug.LOG) {
				Log.d(TAG, "don't open camera as app is paused");
			}
			return;
		}
		try {
			if (MyDebug.LOG)
				Log.d(TAG, "try to open camera: " + cameraId);
			if (test_fail_open_camera) {
				if (MyDebug.LOG)
					Log.d(TAG, "test failing to open camera");
				throw new RuntimeException();
			}
			if (using_android_l)
				camera_controller = new CameraController2(this.getContext(), cameraId);
			else
				camera_controller = new CameraController1(cameraId);
		} catch (RuntimeException e) {
			if (MyDebug.LOG)
				Log.e(TAG, "Failed to open camera: " + e.getMessage());
			e.printStackTrace();
			camera_controller = null;
		}
		if (MyDebug.LOG) {
			Log.d(TAG, "time after opening camera: " + (System.currentTimeMillis() - debug_time));
		}
		boolean take_photo = false;
		if (camera_controller != null) {
			Activity activity = (Activity) this.getContext();
			if (MyDebug.LOG)
				Log.d(TAG, "intent: " + activity.getIntent());
			if (activity.getIntent() != null && activity.getIntent().getExtras() != null) {
				take_photo = activity.getIntent().getExtras().getBoolean(TakePhoto.TAKE_PHOTO);
				activity.getIntent().removeExtra(TakePhoto.TAKE_PHOTO);
			} else {
				if (MyDebug.LOG)
					Log.d(TAG, "no intent data");
			}
			if (MyDebug.LOG)
				Log.d(TAG, "take_photo?: " + take_photo);

			this.setCameraDisplayOrientation();
			new OrientationEventListener(activity) {
				@Override
				public void onOrientationChanged(int orientation) {
					Preview.this.onOrientationChanged(orientation);
				}
			}.enable();

			if (MyDebug.LOG)
				Log.d(TAG, "call setPreviewDisplay");
			try {
				camera_controller.setPreviewDisplay(mHolder);
			} catch (IOException e) {
				if (MyDebug.LOG)
					Log.e(TAG, "Failed to set preview display: " + e.getMessage());
				e.printStackTrace();
			}

			setupCamera(toast_message, take_photo);
		}
		setPopupIcon();

		if (MyDebug.LOG) {
			Log.d(TAG, "total time: " + (System.currentTimeMillis() - debug_time));
		}

	}

	/*
	 * Should only be called after camera first opened, or after preview is
	 * paused.
	 */
	void setupCamera(String toast_message, boolean take_photo) {
		if (MyDebug.LOG)
			Log.d(TAG, "setupCamera()");
		if (camera_controller == null) {
			if (MyDebug.LOG)
				Log.d(TAG, "camera not opened!");
			return;
		}

		setupCameraParameters();

		// now switch to video if saved
		SharedPreferences sharedPreferences = PreferenceManager
				.getDefaultSharedPreferences(this.getContext());
		boolean saved_is_video = sharedPreferences.getBoolean(
				CameraActivityNew.getIsVideoPreferenceKey(), false);
		if (MyDebug.LOG) {
			Log.d(TAG, "saved_is_video: " + saved_is_video);
		} else if (toast_message != null) {
			if (toast_message.length() > 0)
				showToast(null, toast_message);
		}

		// Must set preview size before starting camera preview
		// and must do it after setting photo vs video mode
		if (!this.using_android_l) {
			setPreviewSize(); // need to call this when we switch cameras, not
								// just when we run for the first time
			// Must call startCameraPreview after checking if face detection is
			// present - probably best to call it after setting all parameters
			// that we want
			startCameraPreview();
		} else {
			this.requested_preview_size = false;
			if (MyDebug.LOG)
				Log.d(TAG, "set requested_preview_size to false");
			setPreviewSize();
			// if surface isn't yet the correct size, we have to wait until
			// surfaceChanged() is called with correct size
			if (surface_holder_w == this.requested_preview_size_w
					&& surface_holder_h == this.requested_preview_size_h) {
				if (MyDebug.LOG)
					Log.d(TAG, "surface already correct size, so start preview");
				startCameraPreview();
			}
		}
		if (MyDebug.LOG) {
			// Log.d(TAG, "time after starting camera preview: " +
			// (System.currentTimeMillis() - debug_time));
		}

		// must be done after setting parameters, as this function may set
		// parameters
		// also needs to be done after starting preview for some devices (e.g.,
		// Nexus 7)
		if (this.has_zoom && zoom_factor != 0) {
			int new_zoom_factor = zoom_factor;
			zoom_factor = 0; // force zoomTo to actually update the zoom!
			zoomTo(new_zoom_factor, true);
		}

		if (take_photo) {
			// take photo after a delay - otherwise we sometimes get a black
			// image?!
			final Handler handler = new Handler();
			handler.postDelayed(new Runnable() {
				@Override
				public void run() {
					if (MyDebug.LOG)
						Log.d(TAG, "do automatic take picture");
					takePicture();
				}
			}, 500);
		} else {
			final Handler handler = new Handler();
			handler.postDelayed(new Runnable() {
				@Override
				public void run() {
					if (MyDebug.LOG)
						Log.d(TAG, "do startup autofocus");
					tryAutoFocus(true, false);
				}
			}, 500);
		}
	}

	private void setupCameraParameters() {
		Activity activity = (Activity) this.getContext();
		SharedPreferences sharedPreferences = PreferenceManager.getDefaultSharedPreferences(this.getContext());

		{
			if (MyDebug.LOG)
				Log.d(TAG, "set up scene mode");
			String value = sharedPreferences.getString(
					CameraActivityNew.getSceneModePreferenceKey(),
					camera_controller.getDefaultSceneMode());
			if (MyDebug.LOG)
				Log.d(TAG, "saved scene mode: " + value);

			CameraController.SupportedValues supported_values = camera_controller
					.setSceneMode(value);
			if (supported_values != null) {
				scene_modes = supported_values.values;
				// now save, so it's available for PreferenceActivity
				SharedPreferences.Editor editor = sharedPreferences.edit();
				editor.putString(CameraActivityNew.getSceneModePreferenceKey(),
						supported_values.selected_value);
				editor.apply();
			}
		}

		{
			// grab all read-only info from parameters
			if (MyDebug.LOG)
				Log.d(TAG, "grab info from parameters");
			CameraController.CameraFeatures camera_features = camera_controller
					.getCameraFeatures();
			this.has_zoom = camera_features.is_zoom_supported;
			if (this.has_zoom) {
				this.max_zoom_factor = camera_features.max_zoom;
				this.zoom_ratios = camera_features.zoom_ratios;
			}
			this.supports_face_detection = camera_features.supports_face_detection;
			this.sizes = camera_features.picture_sizes;
			this.has_current_fps_range = camera_features.has_current_fps_range;
			supported_flash_values = camera_features.supported_flash_values;
			supported_focus_values = camera_features.supported_focus_values;
			this.max_num_focus_areas = camera_features.max_num_focus_areas;
			this.is_exposure_lock_supported = camera_features.is_exposure_lock_supported;
			this.supports_video_stabilization = camera_features.is_video_stabilization_supported;
			this.can_disable_shutter_sound = camera_features.can_disable_shutter_sound;
			this.min_exposure = camera_features.min_exposure;
			this.max_exposure = camera_features.max_exposure;
			this.video_sizes = camera_features.video_sizes;
			this.supported_preview_sizes = camera_features.preview_sizes;
		}

		{
			if (MyDebug.LOG)
				Log.d(TAG, "set up zoom");
			if (MyDebug.LOG)
				Log.d(TAG, "has_zoom? " + has_zoom);
			ZoomControls zoomControls = (ZoomControls) activity
					.findViewById(R.id.zoom);
			SeekBar zoomSeekBar = (SeekBar) activity
					.findViewById(R.id.zoom_seekbar);

			if (this.has_zoom) {
				if (sharedPreferences.getBoolean(
						CameraActivityNew.getShowZoomControlsPreferenceKey(), false)) {
					zoomControls.setIsZoomInEnabled(true);
					zoomControls.setIsZoomOutEnabled(true);
					zoomControls.setZoomSpeed(20);

					zoomControls
							.setOnZoomInClickListener(new View.OnClickListener() {
								public void onClick(View v) {
									zoomIn();
								}
							});
					zoomControls
							.setOnZoomOutClickListener(new View.OnClickListener() {
								public void onClick(View v) {
									zoomOut();
								}
							});
					if (!immersive_mode) {
						zoomControls.setVisibility(View.VISIBLE);
					}
				} else {
					zoomControls.setVisibility(View.INVISIBLE); // must be
																// INVISIBLE not
																// GONE, so we
																// can still
																// position the
																// zoomSeekBar
																// relative to
																// it
				}

				zoomSeekBar.setMax(max_zoom_factor);
				zoomSeekBar.setProgress(max_zoom_factor - zoom_factor);
				zoomSeekBar
						.setOnSeekBarChangeListener(new OnSeekBarChangeListener() {
							@Override
							public void onProgressChanged(SeekBar seekBar,
									int progress, boolean fromUser) {
								zoomTo(max_zoom_factor - progress, false);
							}

							@Override
							public void onStartTrackingTouch(SeekBar seekBar) {
							}

							@Override
							public void onStopTrackingTouch(SeekBar seekBar) {
							}
						});

				if (sharedPreferences.getBoolean(
						CameraActivityNew.getShowZoomSliderControlsPreferenceKey(),
						true)) {
					if (!immersive_mode) {
						zoomSeekBar.setVisibility(View.VISIBLE);
					}
				} else {
					zoomSeekBar.setVisibility(View.INVISIBLE);
				}
			} else {
				zoomControls.setVisibility(View.GONE);
				zoomSeekBar.setVisibility(View.GONE);
			}
		}

		{
			if (MyDebug.LOG)
				Log.d(TAG, "set up face detection");
			// get face detection supported
			this.faces_detected = null;
			if (this.supports_face_detection) {
				this.using_face_detection = sharedPreferences.getBoolean(
						CameraActivityNew.getFaceDetectionPreferenceKey(), false);
			} else {
				this.using_face_detection = false;
			}
			if (MyDebug.LOG) {
				Log.d(TAG, "supports_face_detection?: "
						+ supports_face_detection);
				Log.d(TAG, "using_face_detection?: " + using_face_detection);
			}
			if (this.using_face_detection) {
				class MyFaceDetectionListener implements
						CameraController.FaceDetectionListener {
					@Override
					public void onFaceDetection(CameraController.Face[] faces) {
						faces_detected = new CameraController.Face[faces.length];
						System.arraycopy(faces, 0, faces_detected, 0,
								faces.length);
					}
				}
				camera_controller
						.setFaceDetectionListener(new MyFaceDetectionListener());
			}
		}

		{
			if (MyDebug.LOG)
				Log.d(TAG, "set up video stabilization");
			if (this.supports_video_stabilization) {
				boolean using_video_stabilization = sharedPreferences
						.getBoolean(CameraActivityNew
								.getVideoStabilizationPreferenceKey(), false);
				if (MyDebug.LOG)
					Log.d(TAG, "using_video_stabilization?: "
							+ using_video_stabilization);
				camera_controller
						.setVideoStabilization(using_video_stabilization);
			}
			if (MyDebug.LOG)
				Log.d(TAG, "supports_video_stabilization?: "
						+ supports_video_stabilization);
		}

		{
			if (MyDebug.LOG)
				Log.d(TAG, "set up color effect");
			String value = sharedPreferences.getString(
					CameraActivityNew.getColorEffectPreferenceKey(),
					camera_controller.getDefaultColorEffect());
			if (MyDebug.LOG)
				Log.d(TAG, "saved color effect: " + value);

			CameraController.SupportedValues supported_values = camera_controller
					.setColorEffect(value);
			if (supported_values != null) {
				color_effects = supported_values.values;
				// now save, so it's available for PreferenceActivity
				SharedPreferences.Editor editor = sharedPreferences.edit();
				editor.putString(CameraActivityNew.getColorEffectPreferenceKey(),
						supported_values.selected_value);
				editor.apply();
			}
		}

		{
			if (MyDebug.LOG)
				Log.d(TAG, "set up white balance");
			String value = sharedPreferences.getString(
					CameraActivityNew.getWhiteBalancePreferenceKey(),
					camera_controller.getDefaultWhiteBalance());
			if (MyDebug.LOG)
				Log.d(TAG, "saved white balance: " + value);

			CameraController.SupportedValues supported_values = camera_controller
					.setWhiteBalance(value);
			if (supported_values != null) {
				white_balances = supported_values.values;
				// now save, so it's available for PreferenceActivity
				SharedPreferences.Editor editor = sharedPreferences.edit();
				editor.putString(CameraActivityNew.getWhiteBalancePreferenceKey(),
						supported_values.selected_value);
				editor.apply();
			}
		}

		{
			if (MyDebug.LOG)
				Log.d(TAG, "set up iso");
			String value = sharedPreferences.getString(
					CameraActivityNew.getISOPreferenceKey(),
					camera_controller.getDefaultISO());
			if (MyDebug.LOG)
				Log.d(TAG, "saved iso: " + value);

			CameraController.SupportedValues supported_values = camera_controller
					.setISO(value);
			if (supported_values != null) {
				isos = supported_values.values;
				// now save, so it's available for PreferenceActivity
				SharedPreferences.Editor editor = sharedPreferences.edit();
				editor.putString(CameraActivityNew.getISOPreferenceKey(),
						supported_values.selected_value);
				editor.apply();
			}
		}

		{
			if (MyDebug.LOG)
				Log.d(TAG, "set up exposure compensation");
			// get min/max exposure
			exposures = null;
			if (min_exposure != 0 || max_exposure != 0) {
				exposures = new Vector<String>();
				for (int i = min_exposure; i <= max_exposure; i++) {
					exposures.add("" + i);
				}
				String value = sharedPreferences.getString(CameraActivityNew.getExposurePreferenceKey(), "0");
				if (MyDebug.LOG)
					Log.d(TAG, "saved exposure value: " + value);
				int exposure = 0;
				try {
					exposure = Integer.parseInt(value);
					if (MyDebug.LOG)
						Log.d(TAG, "exposure: " + exposure);
				} catch (NumberFormatException exception) {
					if (MyDebug.LOG)
						Log.d(TAG, "exposure invalid format, can't parse to int");
				}
				if (exposure < min_exposure || exposure > max_exposure) {
					exposure = 0;
					if (MyDebug.LOG)
						Log.d(TAG, "saved exposure not supported, reset to 0");
					if (exposure < min_exposure || exposure > max_exposure) {
						if (MyDebug.LOG)
							Log.d(TAG, "zero isn't an allowed exposure?! reset to min " + min_exposure);
						exposure = min_exposure;
					}
				}
				camera_controller.setExposureCompensation(exposure);
				// now save, so it's available for PreferenceActivity
				SharedPreferences.Editor editor = sharedPreferences.edit();
				editor.putString(CameraActivityNew.getExposurePreferenceKey(), "" + exposure);
				editor.apply();
			}
		}

		{
			if (MyDebug.LOG)
				Log.d(TAG, "set up picture sizes");
			if (MyDebug.LOG) {
				for (int i = 0; i < sizes.size(); i++) {
					CameraController.Size size = sizes.get(i);
					Log.d(TAG, "supported picture size: " + size.width + " , " + size.height);
				}
			}
			current_size_index = -1;
			String resolution_value = sharedPreferences.getString(CameraActivityNew.getResolutionPreferenceKey(cameraId), "");
			if (MyDebug.LOG)
				Log.d(TAG, "resolution_value: " + resolution_value);
			if (resolution_value.length() > 0) {
				// parse the saved size, and make sure it is still valid
				int index = resolution_value.indexOf(' ');
				if (index == -1) {
					if (MyDebug.LOG)
						Log.d(TAG, "resolution_value invalid format, can't find space");
				} else {
					String resolution_w_s = resolution_value.substring(0, index);
					String resolution_h_s = resolution_value.substring(index + 1);
					if (MyDebug.LOG) {
						Log.d(TAG, "resolution_w_s: " + resolution_w_s);
						Log.d(TAG, "resolution_h_s: " + resolution_h_s);
					}
					try {
						int resolution_w = Integer.parseInt(resolution_w_s);
						if (MyDebug.LOG)
							Log.d(TAG, "resolution_w: " + resolution_w);
						int resolution_h = Integer.parseInt(resolution_h_s);
						if (MyDebug.LOG)
							Log.d(TAG, "resolution_h: " + resolution_h);
						// now find size in valid list
						for (int i = 0; i < sizes.size() && current_size_index == -1; i++) {
							CameraController.Size size = sizes.get(i);
							if (size.width == resolution_w && size.height == resolution_h) {
								current_size_index = i;
								if (MyDebug.LOG)
									Log.d(TAG, "set current_size_index to: " + current_size_index);
							}
						}
						if (current_size_index == -1) {
							if (MyDebug.LOG)
								Log.e(TAG, "failed to find valid size");
						}
					} catch (NumberFormatException exception) {
						if (MyDebug.LOG)
							Log.d(TAG, "resolution_value invalid format, can't parse w or h to int");
					}
				}
			}

			if (current_size_index == -1) {
				// set to largest
				CameraController.Size current_size = null;
				for (int i = 0; i < sizes.size(); i++) {
					CameraController.Size size = sizes.get(i);
					if (current_size == null || size.width * size.height > current_size.width * current_size.height) {
						current_size_index = i;
						current_size = size;
					}
				}
			}
			if (current_size_index != -1) {
				CameraController.Size current_size = sizes.get(current_size_index);
				if (MyDebug.LOG)
					Log.d(TAG, "Current size index " + current_size_index + ": " + current_size.width + ", " + current_size.height);

				// now save, so it's available for PreferenceActivity
				resolution_value = current_size.width + " " + current_size.height;
				if (MyDebug.LOG) {
					Log.d(TAG, "save new resolution_value: " + resolution_value);
				}
				SharedPreferences.Editor editor = sharedPreferences.edit();
				editor.putString(CameraActivityNew.getResolutionPreferenceKey(cameraId), resolution_value);
				editor.apply();
			}
			// size set later in setPreviewSize()
		}

		{
			if (MyDebug.LOG)
				Log.d(TAG, "set up jpeg quality");
			int image_quality = getImageQuality();
			camera_controller.setJpegQuality(image_quality);
			if (MyDebug.LOG)
				Log.d(TAG, "image quality: " + image_quality);
		}

		// get available sizes
		initialiseVideoSizes();
		initialiseVideoQuality();

		current_video_quality = -1;
		String video_quality_value_s = sharedPreferences.getString(CameraActivityNew.getVideoQualityPreferenceKey(cameraId), "");
		if (MyDebug.LOG)
			Log.d(TAG, "video_quality_value: " + video_quality_value_s);
		if (video_quality_value_s.length() > 0) {
			// parse the saved video quality, and make sure it is still valid
			// now find value in valid list
			for (int i = 0; i < video_quality.size() && current_video_quality == -1; i++) {
				if (video_quality.get(i).equals(video_quality_value_s)) {
					current_video_quality = i;
					if (MyDebug.LOG)
						Log.d(TAG, "set current_video_quality to: " + current_video_quality);
				}
			}
			if (current_video_quality == -1) {
				if (MyDebug.LOG)
					Log.e(TAG, "failed to find valid video_quality");
			}
		}
		if (current_video_quality == -1 && video_quality.size() > 0) {
			// default to highest quality
			current_video_quality = 0;
			if (MyDebug.LOG)
				Log.d(TAG, "set video_quality value to " + video_quality.get(current_video_quality));
		}
		if (current_video_quality != -1) {
			// now save, so it's available for PreferenceActivity
			SharedPreferences.Editor editor = sharedPreferences.edit();
			editor.putString(CameraActivityNew.getVideoQualityPreferenceKey(cameraId), video_quality.get(current_video_quality));
			editor.apply();
		}

		{
			if (MyDebug.LOG)
				Log.d(TAG, "set up flash");
			current_flash_index = -1;
			if (supported_flash_values != null && supported_flash_values.size() > 1) {
				if (MyDebug.LOG)
					Log.d(TAG, "flash values: " + supported_flash_values);

				String flash_value = sharedPreferences.getString(CameraActivityNew.getFlashPreferenceKey(cameraId), "");
				if (flash_value.length() > 0) {
					if (MyDebug.LOG)
						Log.d(TAG, "found existing flash_value: " + flash_value);
					if (!updateFlash(flash_value, false)) {
						if (MyDebug.LOG)
							Log.d(TAG, "flash value no longer supported!");
						updateFlash(0, true);
					}
				} else {
					if (MyDebug.LOG)
						Log.d(TAG, "found no existing flash_value");
					updateFlash("flash_auto", true);
				}
			} else {
				if (MyDebug.LOG)
					Log.d(TAG, "flash not supported");
				supported_flash_values = null;
			}
		}

		{
			if (MyDebug.LOG)
				Log.d(TAG, "set up focus");
			current_focus_index = -1;
			if (supported_focus_values != null && supported_focus_values.size() > 1) {
				if (MyDebug.LOG)
					Log.d(TAG, "focus values: " + supported_focus_values);

				String focus_value = sharedPreferences.getString(
						CameraActivityNew.getFocusPreferenceKey(cameraId), "");
				if (focus_value.length() > 0) {
					if (MyDebug.LOG)
						Log.d(TAG, "found existing focus_value: " + focus_value);
					if (!updateFocus(focus_value, false, false, true)) {
						if (MyDebug.LOG)
							Log.d(TAG, "focus value no longer supported!");
						updateFocus(0, false, true, true);
					}
				} else {
					if (MyDebug.LOG)
						Log.d(TAG, "found no existing focus_value");
					updateFocus("focus_mode_auto", false, true, true);
				}
			} else {
				if (MyDebug.LOG)
					Log.d(TAG, "focus not supported");
				supported_focus_values = null;
			}
		}

	}

	public void surfaceChanged(SurfaceHolder holder, int format, int w, int h) {
		if (MyDebug.LOG)
			Log.d(TAG, "surfaceChanged " + w + ", " + h);
		this.surface_holder_w = w;
		this.surface_holder_h = h;
		// surface size is now changed to match the aspect ratio of camera
		// preview - so we shouldn't change the preview to match the surface
		// size, so no need to restart preview here
		// update: except for Android L, where we must start the preview after
		// the surface has changed size

		if (mHolder.getSurface() == null) {
			// preview surface does not exist
			return;
		}
		if (camera_controller == null) {
			if (MyDebug.LOG)
				Log.d(TAG, "camera not opened!");
			return;
		}

		if (using_android_l) {
			if (this.requested_preview_size
					&& this.requested_preview_size_w == w
					&& this.requested_preview_size_h == h) {
				if (MyDebug.LOG)
					Log.d(TAG,
							"have now set preview size, so can start camera preview");
				startCameraPreview();
			}
		}
		CameraActivityNew main_activity = (CameraActivityNew) Preview.this.getContext();
		main_activity.layoutUI(); // need to force a layoutUI update (e.g., so
									// UI is oriented correctly when app goes
									// idle, device is then rotated, and app is
									// then resumed
	}

	private void setPreviewSize() {
		if (MyDebug.LOG)
			Log.d(TAG, "setPreviewSize()");
		// also now sets picture size
		if (camera_controller == null) {
			if (MyDebug.LOG)
				Log.d(TAG, "camera not opened!");
			return;
		}
		if (is_preview_started) {
			if (MyDebug.LOG)
				Log.d(TAG,
						"setPreviewSize() shouldn't be called when preview is running");
			throw new RuntimeException();
		}
		this.cancelAutoFocus();
		// first set picture size (for photo mode, must be done now so we can
		// set the picture size from this; for video, doesn't really matter when
		// we set it)
		CameraController.Size new_size = null;
		if (this.is_video) {
			// In theory, the picture size shouldn't matter in video mode, but
			// the stock Android camera sets a picture size
			// which is the largest that matches the video's aspect ratio.
			// This seems necessary to work around an aspect ratio bug
			// introduced in Android 4.4.3 (on Nexus 7 at least):
			// http://code.google.com/p/android/issues/detail?id=70830
			// which results in distorted aspect ratio on preview and recorded
			// video!
			CamcorderProfile profile = getCamcorderProfile();
			if (MyDebug.LOG)
				Log.d(TAG, "video size: " + profile.videoFrameWidth + " x "
						+ profile.videoFrameHeight);
			double targetRatio = ((double) profile.videoFrameWidth)
					/ (double) profile.videoFrameHeight;
			new_size = getOptimalVideoPictureSize(sizes, targetRatio);
		} else {
			if (current_size_index != -1) {
				new_size = sizes.get(current_size_index);
			}
		}
		if (new_size != null) {
			camera_controller.setPictureSize(new_size.width, new_size.height);
		}
		// set optimal preview size
		if (supported_preview_sizes != null
				&& supported_preview_sizes.size() > 0) {
			CameraController.Size best_size = getOptimalPreviewSize(supported_preview_sizes);
			camera_controller.setPreviewSize(best_size.width, best_size.height);
			if (this.using_android_l) {
				// in Android L, calling setPreviewSize changes the size of the
				// SurfaceHolder
				this.requested_preview_size = true;
				this.requested_preview_size_w = best_size.width;
				this.requested_preview_size_h = best_size.height;
			}
			this.setAspectRatio(((double) best_size.width)
					/ (double) best_size.height);
		}
	}

	private void sortVideoSizes() {
		if (MyDebug.LOG)
			Log.d(TAG, "sortVideoSizes()");
		Collections.sort(this.video_sizes,
				new Comparator<CameraController.Size>() {
					public int compare(final CameraController.Size a,
							final CameraController.Size b) {
						return b.width * b.height - a.width * a.height;
					}
				});
	}

	// for testing
	public void setVideoSizes(List<CameraController.Size> video_sizes) {
		this.video_sizes = video_sizes;
		this.sortVideoSizes();
	}

	private void initialiseVideoSizes() {
		if (camera_controller == null) {
			if (MyDebug.LOG)
				Log.d(TAG, "camera not opened!");
			return;
		}
		this.sortVideoSizes();
		if (MyDebug.LOG) {
			for (CameraController.Size size : video_sizes) {
				Log.d(TAG, "    supported video size: " + size.width + ", "
						+ size.height);
			}
		}
	}

	private void initialiseVideoQuality() {
		SparseArray<Pair<Integer, Integer>> profiles = new SparseArray<Pair<Integer, Integer>>();
		if (CamcorderProfile
				.hasProfile(cameraId, CamcorderProfile.QUALITY_HIGH)) {
			CamcorderProfile profile = CamcorderProfile.get(cameraId,
					CamcorderProfile.QUALITY_HIGH);
			profiles.put(CamcorderProfile.QUALITY_HIGH,
					new Pair<Integer, Integer>(profile.videoFrameWidth,
							profile.videoFrameHeight));
		}
		if (CamcorderProfile.hasProfile(cameraId,
				CamcorderProfile.QUALITY_1080P)) {
			CamcorderProfile profile = CamcorderProfile.get(cameraId,
					CamcorderProfile.QUALITY_1080P);
			profiles.put(CamcorderProfile.QUALITY_1080P,
					new Pair<Integer, Integer>(profile.videoFrameWidth,
							profile.videoFrameHeight));
		}
		if (CamcorderProfile
				.hasProfile(cameraId, CamcorderProfile.QUALITY_720P)) {
			CamcorderProfile profile = CamcorderProfile.get(cameraId,
					CamcorderProfile.QUALITY_720P);
			profiles.put(CamcorderProfile.QUALITY_720P,
					new Pair<Integer, Integer>(profile.videoFrameWidth,
							profile.videoFrameHeight));
		}
		if (CamcorderProfile
				.hasProfile(cameraId, CamcorderProfile.QUALITY_480P)) {
			CamcorderProfile profile = CamcorderProfile.get(cameraId,
					CamcorderProfile.QUALITY_480P);
			profiles.put(CamcorderProfile.QUALITY_480P,
					new Pair<Integer, Integer>(profile.videoFrameWidth,
							profile.videoFrameHeight));
		}
		if (CamcorderProfile.hasProfile(cameraId, CamcorderProfile.QUALITY_CIF)) {
			CamcorderProfile profile = CamcorderProfile.get(cameraId,
					CamcorderProfile.QUALITY_CIF);
			profiles.put(CamcorderProfile.QUALITY_CIF,
					new Pair<Integer, Integer>(profile.videoFrameWidth,
							profile.videoFrameHeight));
		}
		if (CamcorderProfile
				.hasProfile(cameraId, CamcorderProfile.QUALITY_QVGA)) {
			CamcorderProfile profile = CamcorderProfile.get(cameraId,
					CamcorderProfile.QUALITY_QVGA);
			profiles.put(CamcorderProfile.QUALITY_QVGA,
					new Pair<Integer, Integer>(profile.videoFrameWidth,
							profile.videoFrameHeight));
		}
		if (CamcorderProfile
				.hasProfile(cameraId, CamcorderProfile.QUALITY_QCIF)) {
			CamcorderProfile profile = CamcorderProfile.get(cameraId,
					CamcorderProfile.QUALITY_QCIF);
			profiles.put(CamcorderProfile.QUALITY_QCIF,
					new Pair<Integer, Integer>(profile.videoFrameWidth,
							profile.videoFrameHeight));
		}
		if (CamcorderProfile.hasProfile(cameraId, CamcorderProfile.QUALITY_LOW)) {
			CamcorderProfile profile = CamcorderProfile.get(cameraId,
					CamcorderProfile.QUALITY_LOW);
			profiles.put(CamcorderProfile.QUALITY_LOW,
					new Pair<Integer, Integer>(profile.videoFrameWidth,
							profile.videoFrameHeight));
		}
		initialiseVideoQualityFromProfiles(profiles);
	}

	private void addVideoResolutions(boolean done_video_size[],
			int base_profile, int min_resolution_w, int min_resolution_h) {
		if (video_sizes == null) {
			return;
		}
		if (MyDebug.LOG)
			Log.d(TAG, "profile " + base_profile + " is resolution "
					+ min_resolution_w + " x " + min_resolution_h);
		for (int i = 0; i < video_sizes.size(); i++) {
			if (done_video_size[i])
				continue;
			CameraController.Size size = video_sizes.get(i);
			if (size.width == min_resolution_w
					&& size.height == min_resolution_h) {
				String str = "" + base_profile;
				video_quality.add(str);
				done_video_size[i] = true;
				if (MyDebug.LOG)
					Log.d(TAG, "added: " + str);
			} else if (base_profile == CamcorderProfile.QUALITY_LOW
					|| size.width * size.height >= min_resolution_w
							* min_resolution_h) {
				String str = "" + base_profile + "_r" + size.width + "x"
						+ size.height;
				video_quality.add(str);
				done_video_size[i] = true;
				if (MyDebug.LOG)
					Log.d(TAG, "added: " + str);
			}
		}
	}

	public void initialiseVideoQualityFromProfiles(
			SparseArray<Pair<Integer, Integer>> profiles) {
		if (MyDebug.LOG)
			Log.d(TAG, "initialiseVideoQuality()");
		video_quality = new Vector<String>();
		boolean done_video_size[] = null;
		if (video_sizes != null) {
			done_video_size = new boolean[video_sizes.size()];
			for (int i = 0; i < video_sizes.size(); i++)
				done_video_size[i] = false;
		}
		if (profiles.get(CamcorderProfile.QUALITY_HIGH) != null) {
			if (MyDebug.LOG)
				Log.d(TAG, "supports QUALITY_HIGH");
			Pair<Integer, Integer> pair = profiles
					.get(CamcorderProfile.QUALITY_HIGH);
			addVideoResolutions(done_video_size, CamcorderProfile.QUALITY_HIGH,
					pair.first, pair.second);
		}
		if (profiles.get(CamcorderProfile.QUALITY_1080P) != null) {
			if (MyDebug.LOG)
				Log.d(TAG, "supports QUALITY_1080P");
			Pair<Integer, Integer> pair = profiles
					.get(CamcorderProfile.QUALITY_1080P);
			addVideoResolutions(done_video_size,
					CamcorderProfile.QUALITY_1080P, pair.first, pair.second);
		}
		if (profiles.get(CamcorderProfile.QUALITY_720P) != null) {
			if (MyDebug.LOG)
				Log.d(TAG, "supports QUALITY_720P");
			Pair<Integer, Integer> pair = profiles
					.get(CamcorderProfile.QUALITY_720P);
			addVideoResolutions(done_video_size, CamcorderProfile.QUALITY_720P,
					pair.first, pair.second);
		}
		if (profiles.get(CamcorderProfile.QUALITY_480P) != null) {
			if (MyDebug.LOG)
				Log.d(TAG, "supports QUALITY_480P");
			Pair<Integer, Integer> pair = profiles
					.get(CamcorderProfile.QUALITY_480P);
			addVideoResolutions(done_video_size, CamcorderProfile.QUALITY_480P,
					pair.first, pair.second);
		}
		if (profiles.get(CamcorderProfile.QUALITY_CIF) != null) {
			if (MyDebug.LOG)
				Log.d(TAG, "supports QUALITY_CIF");
			Pair<Integer, Integer> pair = profiles
					.get(CamcorderProfile.QUALITY_CIF);
			addVideoResolutions(done_video_size, CamcorderProfile.QUALITY_CIF,
					pair.first, pair.second);
		}
		if (profiles.get(CamcorderProfile.QUALITY_QVGA) != null) {
			if (MyDebug.LOG)
				Log.d(TAG, "supports QUALITY_QVGA");
			Pair<Integer, Integer> pair = profiles
					.get(CamcorderProfile.QUALITY_QVGA);
			addVideoResolutions(done_video_size, CamcorderProfile.QUALITY_QVGA,
					pair.first, pair.second);
		}
		if (profiles.get(CamcorderProfile.QUALITY_QCIF) != null) {
			if (MyDebug.LOG)
				Log.d(TAG, "supports QUALITY_QCIF");
			Pair<Integer, Integer> pair = profiles
					.get(CamcorderProfile.QUALITY_QCIF);
			addVideoResolutions(done_video_size, CamcorderProfile.QUALITY_QCIF,
					pair.first, pair.second);
		}
		if (profiles.get(CamcorderProfile.QUALITY_LOW) != null) {
			if (MyDebug.LOG)
				Log.d(TAG, "supports QUALITY_LOW");
			Pair<Integer, Integer> pair = profiles
					.get(CamcorderProfile.QUALITY_LOW);
			addVideoResolutions(done_video_size, CamcorderProfile.QUALITY_LOW,
					pair.first, pair.second);
		}
		if (MyDebug.LOG) {
			for (int i = 0; i < video_quality.size(); i++) {
				Log.d(TAG, "supported video quality: " + video_quality.get(i));
			}
		}
	}

	private CamcorderProfile getCamcorderProfile(String quality) {
		if (MyDebug.LOG)
			Log.d(TAG, "getCamcorderProfile(): " + quality);
		CamcorderProfile camcorder_profile = CamcorderProfile.get(cameraId,
				CamcorderProfile.QUALITY_HIGH); // default
		try {
			String profile_string = quality;
			int index = profile_string.indexOf('_');
			if (index != -1) {
				profile_string = quality.substring(0, index);
				if (MyDebug.LOG)
					Log.d(TAG, "    profile_string: " + profile_string);
			}
			int profile = Integer.parseInt(profile_string);
			camcorder_profile = CamcorderProfile.get(cameraId, profile);
			if (index != -1 && index + 1 < quality.length()) {
				String override_string = quality.substring(index + 1);
				if (MyDebug.LOG)
					Log.d(TAG, "    override_string: " + override_string);
				if (override_string.charAt(0) == 'r'
						&& override_string.length() >= 4) {
					index = override_string.indexOf('x');
					if (index == -1) {
						if (MyDebug.LOG)
							Log.d(TAG,
									"override_string invalid format, can't find x");
					} else {
						String resolution_w_s = override_string.substring(1,
								index); // skip first 'r'
						String resolution_h_s = override_string
								.substring(index + 1);
						if (MyDebug.LOG) {
							Log.d(TAG, "resolution_w_s: " + resolution_w_s);
							Log.d(TAG, "resolution_h_s: " + resolution_h_s);
						}
						// copy to local variable first, so that if we fail to
						// parse height, we don't set the width either
						int resolution_w = Integer.parseInt(resolution_w_s);
						int resolution_h = Integer.parseInt(resolution_h_s);
						camcorder_profile.videoFrameWidth = resolution_w;
						camcorder_profile.videoFrameHeight = resolution_h;
					}
				} else {
					if (MyDebug.LOG)
						Log.d(TAG,
								"unknown override_string initial code, or otherwise invalid format");
				}
			}
		} catch (NumberFormatException e) {
			if (MyDebug.LOG)
				Log.e(TAG, "failed to parse video quality: " + quality);
			e.printStackTrace();
		}
		return camcorder_profile;
	}

	public CamcorderProfile getCamcorderProfile() {
		// 4K UHD video is not yet supported by Android API (at least testing on
		// Samsung S5 and Note 3, they do not return it via
		// getSupportedVideoSizes(), nor via a CamcorderProfile (either
		// QUALITY_HIGH, or anything else)
		// but it does work if we explicitly set the resolution (at least tested
		// on an S5)
		CameraActivityNew main_activity = (CameraActivityNew) Preview.this.getContext();
		SharedPreferences sharedPreferences = PreferenceManager.getDefaultSharedPreferences(main_activity);
		CamcorderProfile profile = null;
		if (current_video_quality != -1) {
			profile = getCamcorderProfile(video_quality.get(current_video_quality));
		} else {
			profile = CamcorderProfile.get(cameraId, CamcorderProfile.QUALITY_HIGH);
		}

		String bitrate_value = sharedPreferences.getString(
				CameraActivityNew.getVideoBitratePreferenceKey(), "default");
		if (!bitrate_value.equals("default")) {
			try {
				int bitrate = Integer.parseInt(bitrate_value);
				if (MyDebug.LOG)
					Log.d(TAG, "bitrate: " + bitrate);
				profile.videoBitRate = bitrate;
			} catch (NumberFormatException exception) {
				if (MyDebug.LOG)
					Log.d(TAG, "bitrate invalid format, can't parse to int: "
							+ bitrate_value);
			}
		}
		String fps_value = sharedPreferences.getString(
				CameraActivityNew.getVideoFPSPreferenceKey(), "default");
		if (!fps_value.equals("default")) {
			try {
				int fps = Integer.parseInt(fps_value);
				if (MyDebug.LOG)
					Log.d(TAG, "fps: " + fps);
				profile.videoFrameRate = fps;
			} catch (NumberFormatException exception) {
				if (MyDebug.LOG)
					Log.d(TAG, "fps invalid format, can't parse to int: "
							+ fps_value);
			}
		}
		return profile;
	}

	private static String formatFloatToString(final float f) {
		final int i = (int) f;
		if (f == i)
			return Integer.toString(i);
		return String.format(Locale.getDefault(), "%.2f", f);
	}

	private static int greatestCommonFactor(int a, int b) {
		while (b > 0) {
			int temp = b;
			b = a % b;
			a = temp;
		}
		return a;
	}

	private static String getAspectRatio(int width, int height) {
		int gcf = greatestCommonFactor(width, height);
		width /= gcf;
		height /= gcf;
		return width + ":" + height;
	}

	static String getAspectRatioMPString(int width, int height) {
		float mp = (width * height) / 1000000.0f;
		return "(" + getAspectRatio(width, height) + ", "
				+ formatFloatToString(mp) + "MP)";
	}

	String getCamcorderProfileDescription(String quality) {
		CamcorderProfile profile = getCamcorderProfile(quality);
		String highest = "";
		if (profile.quality == CamcorderProfile.QUALITY_HIGH) {
			highest = "Highest: ";
		}
		String type = "";
		if (profile.videoFrameWidth == 3840 && profile.videoFrameHeight == 2160) {
			type = "4K Ultra HD ";
		} else if (profile.videoFrameWidth == 1920
				&& profile.videoFrameHeight == 1080) {
			type = "Full HD ";
		} else if (profile.videoFrameWidth == 1280
				&& profile.videoFrameHeight == 720) {
			type = "HD ";
		} else if (profile.videoFrameWidth == 720
				&& profile.videoFrameHeight == 480) {
			type = "SD ";
		} else if (profile.videoFrameWidth == 640
				&& profile.videoFrameHeight == 480) {
			type = "VGA ";
		} else if (profile.videoFrameWidth == 352
				&& profile.videoFrameHeight == 288) {
			type = "CIF ";
		} else if (profile.videoFrameWidth == 320
				&& profile.videoFrameHeight == 240) {
			type = "QVGA ";
		} else if (profile.videoFrameWidth == 176
				&& profile.videoFrameHeight == 144) {
			type = "QCIF ";
		}
		String desc = highest
				+ type
				+ profile.videoFrameWidth
				+ "x"
				+ profile.videoFrameHeight
				+ " "
				+ getAspectRatioMPString(profile.videoFrameWidth,
						profile.videoFrameHeight);
		return desc;
	}

	public double getTargetRatioForPreview(Point display_size) {
		double targetRatio = 0.0f;
		Activity activity = (Activity) this.getContext();
		SharedPreferences sharedPreferences = PreferenceManager
				.getDefaultSharedPreferences(activity);
		String preview_size = sharedPreferences.getString(
				CameraActivityNew.getPreviewSizePreferenceKey(),
				"preference_preview_size_wysiwyg");
		// should always use wysiwig for video mode, otherwise we get incorrect
		// aspect ratio shown when recording video (at least on Galaxy Nexus,
		// e.g., at 640x480)
		// also not using wysiwyg mode with video caused corruption on Samsung
		// cameras (tested with Samsung S3, Android 4.3, front camera, infinity
		// focus)
		if (preview_size.equals("preference_preview_size_wysiwyg")
				|| this.is_video) {
			if (this.is_video) {
				if (MyDebug.LOG)
					Log.d(TAG,
							"set preview aspect ratio from video size (wysiwyg)");
				CamcorderProfile profile = getCamcorderProfile();
				if (MyDebug.LOG)
					Log.d(TAG, "video size: " + profile.videoFrameWidth + " x "
							+ profile.videoFrameHeight);
				targetRatio = ((double) profile.videoFrameWidth)
						/ (double) profile.videoFrameHeight;
			} else {
				if (MyDebug.LOG)
					Log.d(TAG,
							"set preview aspect ratio from photo size (wysiwyg)");
				CameraController.Size picture_size = camera_controller
						.getPictureSize();
				if (MyDebug.LOG)
					Log.d(TAG, "picture_size: " + picture_size.width + " x "
							+ picture_size.height);
				targetRatio = ((double) picture_size.width)
						/ (double) picture_size.height;
			}
		} else {
			if (MyDebug.LOG)
				Log.d(TAG, "set preview aspect ratio from display size");
			// base target ratio from display size - means preview will fill the
			// device's display as much as possible
			// but if the preview's aspect ratio differs from the actual
			// photo/video size, the preview will show a cropped version of what
			// is actually taken
			targetRatio = ((double) display_size.x) / (double) display_size.y;
		}
		this.preview_targetRatio = targetRatio;
		if (MyDebug.LOG)
			Log.d(TAG, "targetRatio: " + targetRatio);
		return targetRatio;
	}

	public CameraController.Size getClosestSize(
			List<CameraController.Size> sizes, double targetRatio) {
		if (MyDebug.LOG)
			Log.d(TAG, "getClosestSize()");
		CameraController.Size optimalSize = null;
		double minDiff = Double.MAX_VALUE;
		for (CameraController.Size size : sizes) {
			double ratio = (double) size.width / size.height;
			if (Math.abs(ratio - targetRatio) < minDiff) {
				optimalSize = size;
				minDiff = Math.abs(ratio - targetRatio);
			}
		}
		return optimalSize;
	}

	public CameraController.Size getOptimalPreviewSize(
			List<CameraController.Size> sizes) {
		if (MyDebug.LOG)
			Log.d(TAG, "getOptimalPreviewSize()");
		final double ASPECT_TOLERANCE = 0.05;
		if (sizes == null)
			return null;
		CameraController.Size optimalSize = null;
		double minDiff = Double.MAX_VALUE;
		Point display_size = new Point();
		Activity activity = (Activity) this.getContext();
		{
			Display display = activity.getWindowManager().getDefaultDisplay();
			display.getSize(display_size);
			if (MyDebug.LOG)
				Log.d(TAG, "display_size: " + display_size.x + " x "
						+ display_size.y);
		}
		double targetRatio = getTargetRatioForPreview(display_size);
		int targetHeight = Math.min(display_size.y, display_size.x);
		if (targetHeight <= 0) {
			targetHeight = display_size.y;
		}
		// Try to find the size which matches the aspect ratio, and is closest
		// match to display height
		for (CameraController.Size size : sizes) {
			if (MyDebug.LOG)
				Log.d(TAG, "    supported preview size: " + size.width + ", "
						+ size.height);
			double ratio = (double) size.width / size.height;
			if (Math.abs(ratio - targetRatio) > ASPECT_TOLERANCE)
				continue;
			if (Math.abs(size.height - targetHeight) < minDiff) {
				optimalSize = size;
				minDiff = Math.abs(size.height - targetHeight);
			}
		}
		if (optimalSize == null) {
			// can't find match for aspect ratio, so find closest one
			if (MyDebug.LOG)
				Log.d(TAG, "no preview size matches the aspect ratio");
			optimalSize = getClosestSize(sizes, targetRatio);
		}
		if (MyDebug.LOG) {
			Log.d(TAG, "chose optimalSize: " + optimalSize.width + " x "
					+ optimalSize.height);
			Log.d(TAG, "optimalSize ratio: "
					+ ((double) optimalSize.width / optimalSize.height));
		}
		return optimalSize;
	}

	public CameraController.Size getOptimalVideoPictureSize(
			List<CameraController.Size> sizes, double targetRatio) {
		if (MyDebug.LOG)
			Log.d(TAG, "getOptimalVideoPictureSize()");
		final double ASPECT_TOLERANCE = 0.05;
		if (sizes == null)
			return null;
		CameraController.Size optimalSize = null;
		// Try to find largest size that matches aspect ratio
		for (CameraController.Size size : sizes) {
			if (MyDebug.LOG)
				Log.d(TAG, "    supported preview size: " + size.width + ", "
						+ size.height);
			double ratio = (double) size.width / size.height;
			if (Math.abs(ratio - targetRatio) > ASPECT_TOLERANCE)
				continue;
			if (optimalSize == null || size.width > optimalSize.width) {
				optimalSize = size;
			}
		}
		if (optimalSize == null) {
			// can't find match for aspect ratio, so find closest one
			if (MyDebug.LOG)
				Log.d(TAG, "no picture size matches the aspect ratio");
			optimalSize = getClosestSize(sizes, targetRatio);
		}
		if (MyDebug.LOG) {
			Log.d(TAG, "chose optimalSize: " + optimalSize.width + " x "
					+ optimalSize.height);
			Log.d(TAG, "optimalSize ratio: "
					+ ((double) optimalSize.width / optimalSize.height));
		}
		return optimalSize;
	}

	private void setAspectRatio(double ratio) {
		if (ratio <= 0.0)
			throw new IllegalArgumentException();

		has_aspect_ratio = true;
		if (aspect_ratio != ratio) {
			aspect_ratio = ratio;
			if (MyDebug.LOG)
				Log.d(TAG, "new aspect ratio: " + aspect_ratio);
			surfaceView.requestLayout();
		}
	}

	boolean hasAspectRatio() {
		return has_aspect_ratio;
	}

	double getAspectRatio() {
		return aspect_ratio;
	}

	// for the Preview - from
	// http://developer.android.com/reference/android/hardware/Camera.html#setDisplayOrientation(int)
	// note, if orientation is locked to landscape this is only called when
	// setting up the activity, and will always have the same orientation
	void setCameraDisplayOrientation() {
		if (MyDebug.LOG)
			Log.d(TAG, "setCameraDisplayOrientation()");
		if (camera_controller == null) {
			if (MyDebug.LOG)
				Log.d(TAG, "camera not opened!");
			return;
		}
		Activity activity = (Activity) this.getContext();
		int rotation = activity.getWindowManager().getDefaultDisplay()
				.getRotation();
		int degrees = 0;
		switch (rotation) {
		case Surface.ROTATION_0:
			degrees = 0;
			break;
		case Surface.ROTATION_90:
			degrees = 90;
			break;
		case Surface.ROTATION_180:
			degrees = 180;
			break;
		case Surface.ROTATION_270:
			degrees = 270;
			break;
		}
		if (MyDebug.LOG)
			Log.d(TAG, "    degrees = " + degrees);

		SharedPreferences sharedPreferences = PreferenceManager
				.getDefaultSharedPreferences(this.getContext());
		String rotate_preview = sharedPreferences.getString(
				CameraActivityNew.getRotatePreviewPreferenceKey(), "0");
		if (MyDebug.LOG)
			Log.d(TAG, "    rotate_preview = " + rotate_preview);
		if (rotate_preview.equals("180")) {
			degrees = (degrees + 180) % 360;
		}

		camera_controller.setDisplayOrientation(degrees);
	}

	// for taking photos - from
	// http://developer.android.com/reference/android/hardware/Camera.Parameters.html#setRotation(int)
	private void onOrientationChanged(int orientation) {
		/*
		 * if( MyDebug.LOG ) { Log.d(TAG, "onOrientationChanged()"); Log.d(TAG,
		 * "orientation: " + orientation); }
		 */
		if (orientation == OrientationEventListener.ORIENTATION_UNKNOWN)
			return;
		if (camera_controller == null) {
			/*
			 * if( MyDebug.LOG ) Log.d(TAG, "camera not opened!");
			 */
			return;
		}
		orientation = (orientation + 45) / 90 * 90;
		this.current_orientation = orientation % 360;
		int new_rotation = 0;
		int camera_orientation = camera_controller.getCameraOrientation();
		if (camera_controller.isFrontFacing()) {
			new_rotation = (camera_orientation - orientation + 360) % 360;
		} else {
			new_rotation = (camera_orientation + orientation) % 360;
		}
		if (new_rotation != current_rotation) {
			/*
			 * if( MyDebug.LOG ) { Log.d(TAG, "    current_orientation is " +
			 * current_orientation); Log.d(TAG, "    info orientation is " +
			 * camera_orientation); Log.d(TAG, "    set Camera rotation from " +
			 * current_rotation + " to " + new_rotation); }
			 */
			this.current_rotation = new_rotation;
		}
	}

	private int getDeviceDefaultOrientation() {
		WindowManager windowManager = (WindowManager) this.getContext()
				.getSystemService(Context.WINDOW_SERVICE);
		Configuration config = getResources().getConfiguration();
		int rotation = windowManager.getDefaultDisplay().getRotation();
		if (((rotation == Surface.ROTATION_0 || rotation == Surface.ROTATION_180) && config.orientation == Configuration.ORIENTATION_LANDSCAPE)
				|| ((rotation == Surface.ROTATION_90 || rotation == Surface.ROTATION_270) && config.orientation == Configuration.ORIENTATION_PORTRAIT)) {
			return Configuration.ORIENTATION_LANDSCAPE;
		} else {
			return Configuration.ORIENTATION_PORTRAIT;
		}
	}

	/*
	 * Returns the rotation to use for images/videos, taking the
	 * preference_lock_orientation into account.
	 */
	private int getImageVideoRotation() {
		if (MyDebug.LOG)
			Log.d(TAG, "getImageVideoRotation() from current_rotation "
					+ current_rotation);
		SharedPreferences sharedPreferences = PreferenceManager
				.getDefaultSharedPreferences(this.getContext());
		String lock_orientation = sharedPreferences.getString(
				CameraActivityNew.getLockOrientationPreferenceKey(), "none");
		if (lock_orientation.equals("landscape")) {
			int camera_orientation = camera_controller.getCameraOrientation();
			int device_orientation = getDeviceDefaultOrientation();
			int result = 0;
			if (device_orientation == Configuration.ORIENTATION_PORTRAIT) {
				// should be equivalent to onOrientationChanged(270)
				if (camera_controller.isFrontFacing()) {
					result = (camera_orientation + 90) % 360;
				} else {
					result = (camera_orientation + 270) % 360;
				}
			} else {
				// should be equivalent to onOrientationChanged(0)
				result = camera_orientation;
			}
			if (MyDebug.LOG)
				Log.d(TAG,
						"getImageVideoRotation() lock to landscape, returns "
								+ result);
			return result;
		} else if (lock_orientation.equals("portrait")) {
			int camera_orientation = camera_controller.getCameraOrientation();
			int result = 0;
			int device_orientation = getDeviceDefaultOrientation();
			if (device_orientation == Configuration.ORIENTATION_PORTRAIT) {
				// should be equivalent to onOrientationChanged(0)
				result = camera_orientation;
			} else {
				// should be equivalent to onOrientationChanged(90)
				if (camera_controller.isFrontFacing()) {
					result = (camera_orientation + 270) % 360;
				} else {
					result = (camera_orientation + 90) % 360;
				}
			}
			if (MyDebug.LOG)
				Log.d(TAG, "getImageVideoRotation() lock to portrait, returns "
						+ result);
			return result;
		}
		if (MyDebug.LOG)
			Log.d(TAG, "getImageVideoRotation() returns current_rotation "
					+ current_rotation);
		return this.current_rotation;
	}

	void draw(Canvas canvas) {
		if (this.app_is_paused) {
			return;
		}

		CameraActivityNew main_activity = (CameraActivityNew) this.getContext();
		SharedPreferences sharedPreferences = PreferenceManager
				.getDefaultSharedPreferences(this.getContext());
		if (immersive_mode) {
			String immersive_mode = sharedPreferences.getString(
					CameraActivityNew.getImmersiveModePreferenceKey(),
					"immersive_mode_low_profile");
			if (immersive_mode.equals("immersive_mode_everything")) {
				// exit, to ensure we don't display anything!
				return;
			}
		}
		final float scale = getResources().getDisplayMetrics().density;
		String preference_grid = sharedPreferences
				.getString(CameraActivityNew.getShowGridPreferenceKey(),
						"preference_grid_none");
		if (camera_controller != null
				&& preference_grid.equals("preference_grid_3x3")) {
			p.setColor(Color.WHITE);
			canvas.drawLine(canvas.getWidth() / 3.0f, 0.0f,
					canvas.getWidth() / 3.0f, canvas.getHeight() - 1.0f, p);
			canvas.drawLine(2.0f * canvas.getWidth() / 3.0f, 0.0f,
					2.0f * canvas.getWidth() / 3.0f, canvas.getHeight() - 1.0f,
					p);
			canvas.drawLine(0.0f, canvas.getHeight() / 3.0f,
					canvas.getWidth() - 1.0f, canvas.getHeight() / 3.0f, p);
			canvas.drawLine(0.0f, 2.0f * canvas.getHeight() / 3.0f,
					canvas.getWidth() - 1.0f, 2.0f * canvas.getHeight() / 3.0f,
					p);
		}
		if (camera_controller != null
				&& preference_grid.equals("preference_grid_4x2")) {
			p.setColor(Color.GRAY);
			canvas.drawLine(canvas.getWidth() / 4.0f, 0.0f,
					canvas.getWidth() / 4.0f, canvas.getHeight() - 1.0f, p);
			canvas.drawLine(canvas.getWidth() / 2.0f, 0.0f,
					canvas.getWidth() / 2.0f, canvas.getHeight() - 1.0f, p);
			canvas.drawLine(3.0f * canvas.getWidth() / 4.0f, 0.0f,
					3.0f * canvas.getWidth() / 4.0f, canvas.getHeight() - 1.0f,
					p);
			canvas.drawLine(0.0f, canvas.getHeight() / 2.0f,
					canvas.getWidth() - 1.0f, canvas.getHeight() / 2.0f, p);
			p.setColor(Color.WHITE);
			int crosshairs_radius = (int) (20 * scale + 0.5f); // convert dps to
																// pixels
			canvas.drawLine(canvas.getWidth() / 2.0f, canvas.getHeight() / 2.0f
					- crosshairs_radius, canvas.getWidth() / 2.0f,
					canvas.getHeight() / 2.0f + crosshairs_radius, p);
			canvas.drawLine(canvas.getWidth() / 2.0f - crosshairs_radius,
					canvas.getHeight() / 2.0f, canvas.getWidth() / 2.0f
							+ crosshairs_radius, canvas.getHeight() / 2.0f, p);
		}
		if (this.is_video
				|| sharedPreferences.getString(
						CameraActivityNew.getPreviewSizePreferenceKey(),
						"preference_preview_size_wysiwyg").equals(
						"preference_preview_size_wysiwyg")) {
			String preference_crop_guide = sharedPreferences.getString(
					CameraActivityNew.getShowCropGuidePreferenceKey(),
					"crop_guide_none");
			if (camera_controller != null && preview_targetRatio > 0.0
					&& !preference_crop_guide.equals("crop_guide_none")) {
				p.setStyle(Paint.Style.STROKE);
				p.setColor(Color.rgb(255, 235, 59)); // Yellow 500
				double crop_ratio = -1.0;
				if (preference_crop_guide.equals("crop_guide_1.33")) {
					crop_ratio = 1.33333333;
				} else if (preference_crop_guide.equals("crop_guide_1.78")) {
					crop_ratio = 1.77777778;
				} else if (preference_crop_guide.equals("crop_guide_1.85")) {
					crop_ratio = 1.85;
				} else if (preference_crop_guide.equals("crop_guide_2.33")) {
					crop_ratio = 2.33333333;
				} else if (preference_crop_guide.equals("crop_guide_2.35")) {
					crop_ratio = 2.35006120; // actually 1920:817
				} else if (preference_crop_guide.equals("crop_guide_2.4")) {
					crop_ratio = 2.4;
				}
				if (crop_ratio > 0.0
						&& Math.abs(preview_targetRatio - crop_ratio) > 1.0e-5) {
					int left = 1, top = 1, right = canvas.getWidth() - 1, bottom = canvas
							.getHeight() - 1;
					if (crop_ratio > preview_targetRatio) {
						// crop ratio is wider, so we have to crop top/bottom
						double new_hheight = ((double) canvas.getWidth())
								/ (2.0f * crop_ratio);
						top = (int) (canvas.getHeight() / 2 - new_hheight);
						bottom = (int) (canvas.getHeight() / 2 + new_hheight);
					} else {
						// crop ratio is taller, so we have to crop left/right
						double new_hwidth = (((double) canvas.getHeight()) * crop_ratio) / 2.0f;
						left = (int) (canvas.getWidth() / 2 - new_hwidth);
						right = (int) (canvas.getWidth() / 2 + new_hwidth);
					}
					canvas.drawRect(left, top, right, bottom, p);
				}
			}
		}

		// note, no need to check preferences here, as we do that when setting
		// thumbnail_anim
		if (camera_controller != null && this.thumbnail_anim
				&& this.thumbnail != null) {
			long time = System.currentTimeMillis()
					- this.thumbnail_anim_start_ms;
			final long duration = 500;
			if (time > duration) {
				this.thumbnail_anim = false;
			} else {
				thumbnail_anim_src_rect.left = 0;
				thumbnail_anim_src_rect.top = 0;
				thumbnail_anim_src_rect.right = this.thumbnail.getWidth();
				thumbnail_anim_src_rect.bottom = this.thumbnail.getHeight();
				View galleryButton = (View) main_activity
						.findViewById(R.id.gallery);
				float alpha = ((float) time) / (float) duration;

				int st_x = canvas.getWidth() / 2;
				int st_y = canvas.getHeight() / 2;
				int nd_x = galleryButton.getLeft() + galleryButton.getWidth()
						/ 2;
				int nd_y = galleryButton.getTop() + galleryButton.getHeight()
						/ 2;
				int thumbnail_x = (int) ((1.0f - alpha) * st_x + alpha * nd_x);
				int thumbnail_y = (int) ((1.0f - alpha) * st_y + alpha * nd_y);

				float st_w = canvas.getWidth();
				float st_h = canvas.getHeight();
				float nd_w = galleryButton.getWidth();
				float nd_h = galleryButton.getHeight();
				// int thumbnail_w = (int)( (1.0f-alpha)*st_w + alpha*nd_w );
				// int thumbnail_h = (int)( (1.0f-alpha)*st_h + alpha*nd_h );
				float correction_w = st_w / nd_w - 1.0f;
				float correction_h = st_h / nd_h - 1.0f;
				int thumbnail_w = (int) (st_w / (1.0f + alpha * correction_w));
				int thumbnail_h = (int) (st_h / (1.0f + alpha * correction_h));
				thumbnail_anim_dst_rect.left = thumbnail_x - thumbnail_w / 2;
				thumbnail_anim_dst_rect.top = thumbnail_y - thumbnail_h / 2;
				thumbnail_anim_dst_rect.right = thumbnail_x + thumbnail_w / 2;
				thumbnail_anim_dst_rect.bottom = thumbnail_y + thumbnail_h / 2;
				// canvas.drawBitmap(this.thumbnail, thumbnail_anim_src_rect,
				// thumbnail_anim_dst_rect, p);
				thumbnail_anim_matrix.setRectToRect(thumbnail_anim_src_rect,
						thumbnail_anim_dst_rect, Matrix.ScaleToFit.FILL);
				// thumbnail_anim_matrix.reset();
				if (ui_rotation == 90 || ui_rotation == 270) {
					float ratio = ((float) thumbnail.getWidth())
							/ (float) thumbnail.getHeight();
					thumbnail_anim_matrix
							.preScale(ratio, 1.0f / ratio,
									thumbnail.getWidth() / 2,
									thumbnail.getHeight() / 2);
				}
				thumbnail_anim_matrix.preRotate(ui_rotation,
						thumbnail.getWidth() / 2, thumbnail.getHeight() / 2);
				canvas.drawBitmap(this.thumbnail, thumbnail_anim_matrix, p);
			}
		}

		canvas.save();
		canvas.rotate(ui_rotation, canvas.getWidth() / 2,
				canvas.getHeight() / 2);

		int text_y = (int) (20 * scale + 0.5f); // convert dps to pixels
		// fine tuning to adjust placement of text with respect to the GUI,
		// depending on orientation
		int text_base_y = 0;
		if (ui_rotation == (ui_placement_right ? 0 : 180)) {
			text_base_y = canvas.getHeight() - (int) (0.5 * text_y);
		} else if (ui_rotation == (ui_placement_right ? 180 : 0)) {
			text_base_y = canvas.getHeight() - (int) (2.5 * text_y);
		} else if (ui_rotation == 90 || ui_rotation == 270) {
			// text_base_y = canvas.getHeight() + (int)(0.5*text_y);
			ImageButton view = (ImageButton) main_activity
					.findViewById(R.id.take_photo);
			// align with "top" of the take_photo button, but remember to take
			// the rotation into account!
			view.getLocationOnScreen(gui_location);
			int view_left = gui_location[0];
			surfaceView.getLocationOnScreen(gui_location);
			int this_left = gui_location[0];
			int diff_x = view_left - (this_left + canvas.getWidth() / 2);
			int max_x = canvas.getWidth();
			if (ui_rotation == 90) {
				// so we don't interfere with the top bar info (time, etc)
				max_x -= (int) (1.5 * text_y);
			}
			if (canvas.getWidth() / 2 + diff_x > max_x) {
				diff_x = max_x - canvas.getWidth() / 2;
			}
			text_base_y = canvas.getHeight() / 2 + diff_x
					- (int) (0.5 * text_y);
		}
		final int top_y = (int) (5 * scale + 0.5f); // convert dps to pixels

		final double close_angle = 1.0f;
		// TODO
		if (camera_controller != null && this.phase != PHASE_PREVIEW_PAUSED) {
			if (this.isOnTimer()) {
				long remaining_time = (take_photo_time - System.currentTimeMillis() + 999) / 1000;
				if (MyDebug.LOG)
					Log.d(TAG, "remaining_time: " + remaining_time);
				if (remaining_time >= 0) {
					p.setTextSize(42 * scale + 0.5f); // convert dps to pixels
					p.setTextAlign(Paint.Align.CENTER);
					drawTextWithBackground(canvas, p, "" + remaining_time,
							Color.rgb(229, 28, 35), Color.BLACK, canvas.getWidth() / 2, canvas.getHeight() / 2);
				}
			}
		} else if (camera_controller == null) {
			p.setColor(Color.WHITE);
			p.setTextSize(14 * scale + 0.5f); // convert dps to pixels
			p.setTextAlign(Paint.Align.CENTER);
			int pixels_offset = (int) (20 * scale + 0.5f); // convert dps to
															// pixels
			canvas.drawText(
					getResources().getString(R.string.failed_to_open_camera_1),
					canvas.getWidth() / 2, canvas.getHeight() / 2, p);
			canvas.drawText(
					getResources().getString(R.string.failed_to_open_camera_2),
					canvas.getWidth() / 2, canvas.getHeight() / 2
							+ pixels_offset, p);
			canvas.drawText(
					getResources().getString(R.string.failed_to_open_camera_3),
					canvas.getWidth() / 2, canvas.getHeight() / 2 + 2
							* pixels_offset, p);
		}

		boolean store_location = sharedPreferences.getBoolean(
				CameraActivityNew.getLocationPreferenceKey(), false);
		final int location_size = (int) (20 * scale + 0.5f); // convert dps to
																// pixels
		if (store_location) {
			int location_x = (int) (20 * scale + 0.5f); // convert dps to pixels
			int location_y = top_y;
			if (ui_rotation == 90 || ui_rotation == 270) {
				int diff = canvas.getWidth() - canvas.getHeight();
				location_x += diff / 2;
				location_y -= diff / 2;
			}
			if (ui_rotation == 90) {
				location_y = canvas.getHeight() - location_y - location_size;
			}
			if (ui_rotation == 180) {
				location_x = canvas.getWidth() - location_x - location_size;
			}
			location_dest.set(location_x, location_y, location_x
					+ location_size, location_y + location_size);
			if (has_set_location) {
				canvas.drawBitmap(location_bitmap, null, location_dest, p);
				int location_radius = location_size / 10;
				int indicator_x = location_x + location_size;
				int indicator_y = location_y + location_radius / 2 + 1;
				p.setStyle(Paint.Style.FILL_AND_STROKE);
				p.setColor(location_accuracy < 25.01f ? Color.rgb(37, 155, 36)
						: Color.rgb(255, 235, 59)); // Green 500 or Yellow 500
				canvas.drawCircle(indicator_x, indicator_y, location_radius, p);
			} else {
				canvas.drawBitmap(location_off_bitmap, null, location_dest, p);
			}
		}

		canvas.restore();

		if (camera_controller != null
				&& this.phase != PHASE_PREVIEW_PAUSED
				&& has_level_angle
				&& sharedPreferences.getBoolean(
						CameraActivityNew.getShowAngleLinePreferenceKey(), false)) {
			// n.b., must draw this without canvas rotation
			int radius_dps = (ui_rotation == 90 || ui_rotation == 270) ? 60 : 80;
			int radius = (int) (radius_dps * scale + 0.5f); // convert dps to
															// pixels
			double angle = -this.orig_level_angle;
			// see http://android-developers.blogspot.co.uk/2010/09/one-screen-turn-deserves-another.html
			int rotation = main_activity.getWindowManager().getDefaultDisplay()
					.getRotation();
			switch (rotation) {
			case Surface.ROTATION_90:
			case Surface.ROTATION_270:
				angle += 90.0;
				break;
			}
			int off_x = (int) (radius * Math.cos(Math.toRadians(angle)));
			int off_y = (int) (radius * Math.sin(Math.toRadians(angle)));
			int cx = canvas.getWidth() / 2;
			int cy = canvas.getHeight() / 2;
			if (Math.abs(this.level_angle) <= close_angle) { // n.b., use
																// level_angle,
																// not angle or
																// orig_level_angle
				p.setColor(Color.rgb(20, 231, 21)); // Green A400
			} else {
				p.setColor(Color.WHITE);
			}
			canvas.drawLine(cx - off_x, cy - off_y, cx + off_x, cy + off_y, p);
		}

		if (this.focus_success != FOCUS_DONE) {
			int size = (int) (50 * scale + 0.5f); // convert dps to pixels
			if (this.focus_success == FOCUS_SUCCESS)
				p.setColor(Color.rgb(20, 231, 21)); // Green A400
			else if (this.focus_success == FOCUS_FAILED)
				p.setColor(Color.rgb(229, 28, 35)); // Red 500
			else
				p.setColor(Color.WHITE);
			p.setStyle(Paint.Style.STROKE);
			int pos_x = 0;
			int pos_y = 0;
			if (has_focus_area) {
				pos_x = focus_screen_x;
				pos_y = focus_screen_y;
			} else {
				pos_x = canvas.getWidth() / 2;
				pos_y = canvas.getHeight() / 2;
			}
			canvas.drawRect(pos_x - size, pos_y - size, pos_x + size, pos_y
					+ size, p);
			if (focus_complete_time != -1
					&& System.currentTimeMillis() > focus_complete_time + 1000) {
				focus_success = FOCUS_DONE;
			}
			p.setStyle(Paint.Style.FILL); // reset
		}
		if (this.using_face_detection && this.faces_detected != null) {
			p.setColor(Color.rgb(255, 235, 59)); // Yellow 500
			p.setStyle(Paint.Style.STROKE);
			for (CameraController.Face face : faces_detected) {
				// Android doc recommends filtering out faces with score less
				// than 50
				if (face.score >= 50) {
					calculateCameraToPreviewMatrix();
					face_rect.set(face.rect);
					this.camera_to_preview_matrix.mapRect(face_rect);
					canvas.drawRect(face_rect, p);
				}
			}
			p.setStyle(Paint.Style.FILL); // reset
		}
	}

	private void drawTextWithBackground(Canvas canvas, Paint paint,
			String text, int foreground, int background, int location_x,
			int location_y) {
		drawTextWithBackground(canvas, paint, text, foreground, background,
				location_x, location_y, false);
	}

	private void drawTextWithBackground(Canvas canvas, Paint paint,
			String text, int foreground, int background, int location_x,
			int location_y, boolean align_top) {
		drawTextWithBackground(canvas, paint, text, foreground, background,
				location_x, location_y, align_top, null);
	}

	private void drawTextWithBackground(Canvas canvas, Paint paint,
			String text, int foreground, int background, int location_x,
			int location_y, boolean align_top, String ybounds_text) {
		final float scale = getResources().getDisplayMetrics().density;
		p.setStyle(Paint.Style.FILL);
		paint.setColor(background);
		paint.setAlpha(64);
		int alt_height = 0;
		if (ybounds_text != null) {
			paint.getTextBounds(ybounds_text, 0, ybounds_text.length(),
					text_bounds);
			alt_height = text_bounds.bottom - text_bounds.top;
		}
		paint.getTextBounds(text, 0, text.length(), text_bounds);
		if (ybounds_text != null) {
			text_bounds.bottom = text_bounds.top + alt_height;
		}
		final int padding = (int) (2 * scale + 0.5f); // convert dps to pixels
		if (paint.getTextAlign() == Paint.Align.RIGHT
				|| paint.getTextAlign() == Paint.Align.CENTER) {
			float width = paint.measureText(text); // n.b., need to use
													// measureText rather than
													// getTextBounds here
			if (paint.getTextAlign() == Paint.Align.CENTER)
				width /= 2.0f;
			text_bounds.left -= width;
			text_bounds.right -= width;
		}
		text_bounds.left += location_x - padding;
		text_bounds.right += location_x + padding;
		if (align_top) {
			int height = text_bounds.bottom - text_bounds.top + 2 * padding;
			// unclear why we need the offset of -1, but need this to align
			// properly on Galaxy Nexus at least
			int y_diff = -text_bounds.top + padding - 1;
			text_bounds.top = location_y - 1;
			text_bounds.bottom = text_bounds.top + height;
			location_y += y_diff;
		} else {
			text_bounds.top += location_y - padding;
			text_bounds.bottom += location_y + padding;
		}
		canvas.drawRect(text_bounds, paint);
		paint.setColor(foreground);
		canvas.drawText(text, location_x, location_y, paint);
	}

	public void scaleZoom(float scale_factor) {
		if (MyDebug.LOG)
			Log.d(TAG, "scaleZoom() " + scale_factor);
		if (this.camera_controller != null && this.has_zoom) {
			float zoom_ratio = this.zoom_ratios.get(zoom_factor) / 100.0f;
			zoom_ratio *= scale_factor;

			int new_zoom_factor = zoom_factor;
			if (zoom_ratio <= 1.0f) {
				new_zoom_factor = 0;
			} else if (zoom_ratio >= zoom_ratios.get(max_zoom_factor) / 100.0f) {
				new_zoom_factor = max_zoom_factor;
			} else {
				// find the closest zoom level
				if (scale_factor > 1.0f) {
					// zooming in
					for (int i = zoom_factor; i < zoom_ratios.size(); i++) {
						if (zoom_ratios.get(i) / 100.0f >= zoom_ratio) {
							if (MyDebug.LOG)
								Log.d(TAG,
										"zoom int, found new zoom by comparing "
												+ zoom_ratios.get(i) / 100.0f
												+ " >= " + zoom_ratio);
							new_zoom_factor = i;
							break;
						}
					}
				} else {
					// zooming out
					for (int i = zoom_factor; i >= 0; i--) {
						if (zoom_ratios.get(i) / 100.0f <= zoom_ratio) {
							if (MyDebug.LOG)
								Log.d(TAG,
										"zoom out, found new zoom by comparing "
												+ zoom_ratios.get(i) / 100.0f
												+ " <= " + zoom_ratio);
							new_zoom_factor = i;
							break;
						}
					}
				}
			}
			if (MyDebug.LOG) {
				Log.d(TAG, "ScaleListener.onScale zoom_ratio is now "
						+ zoom_ratio);
				Log.d(TAG, "    old zoom_factor " + zoom_factor + " ratio "
						+ zoom_ratios.get(zoom_factor) / 100.0f);
				Log.d(TAG, "    chosen new zoom_factor " + new_zoom_factor
						+ " ratio " + zoom_ratios.get(new_zoom_factor) / 100.0f);
			}
			zoomTo(new_zoom_factor, true);
		}
	}

	public void zoomIn() {
		if (MyDebug.LOG)
			Log.d(TAG, "zoomIn()");
		if (zoom_factor < max_zoom_factor) {
			zoomTo(zoom_factor + 1, true);
		}
	}

	public void zoomOut() {
		if (MyDebug.LOG)
			Log.d(TAG, "zoomOut()");
		if (zoom_factor > 0) {
			zoomTo(zoom_factor - 1, true);
		}
	}

	public void zoomTo(int new_zoom_factor, boolean update_seek_bar) {
		if (MyDebug.LOG)
			Log.d(TAG, "ZoomTo(): " + new_zoom_factor);
		if (new_zoom_factor < 0)
			new_zoom_factor = 0;
		if (new_zoom_factor > max_zoom_factor)
			new_zoom_factor = max_zoom_factor;
		// problem where we crashed due to calling this function with null
		// camera should be fixed now, but check again just to be safe
		if (new_zoom_factor != zoom_factor && camera_controller != null) {
			if (this.has_zoom) {
				camera_controller.setZoom(new_zoom_factor);
				zoom_factor = new_zoom_factor;
				if (update_seek_bar) {
					Activity activity = (Activity) this.getContext();
					SeekBar zoomSeekBar = (SeekBar) activity
							.findViewById(R.id.zoom_seekbar);
					zoomSeekBar.setProgress(max_zoom_factor - zoom_factor);
				}
				clearFocusAreas();
			}
		}
	}

	public void changeExposure(int change, boolean update_seek_bar) {
		if (MyDebug.LOG)
			Log.d(TAG, "changeExposure(): " + change);
		if (change != 0 && camera_controller != null
				&& (min_exposure != 0 || max_exposure != 0)) {
			int current_exposure = camera_controller.getExposureCompensation();
			int new_exposure = current_exposure + change;
			setExposure(new_exposure, update_seek_bar);
		}
	}

	public void setExposure(int new_exposure, boolean update_seek_bar) {
		if (MyDebug.LOG)
			Log.d(TAG, "setExposure(): " + new_exposure);
		if (camera_controller != null
				&& (min_exposure != 0 || max_exposure != 0)) {
			cancelAutoFocus();
			if (new_exposure < min_exposure)
				new_exposure = min_exposure;
			if (new_exposure > max_exposure)
				new_exposure = max_exposure;
			if (camera_controller.setExposureCompensation(new_exposure)) {
				// now save
				SharedPreferences sharedPreferences = PreferenceManager
						.getDefaultSharedPreferences(this.getContext());
				SharedPreferences.Editor editor = sharedPreferences.edit();
				editor.putString(CameraActivityNew.getExposurePreferenceKey(), ""
						+ new_exposure);
				editor.apply();
				showToast(change_exposure_toast,
						getResources()
								.getString(R.string.exposure_compensation)
								+ " "
								+ (new_exposure > 0 ? "+" : "")
								+ new_exposure);
			}
		}
	}

	private void matchPreviewFpsToVideo() {
		if (MyDebug.LOG)
			Log.d(TAG, "matchPreviewFpsToVideo()");
		if (!has_current_fps_range) {
			// exit, as we don't have a current fps to reset back to later
			if (MyDebug.LOG)
				Log.d(TAG, "current fps not available");
			return;
		}
		CamcorderProfile profile = getCamcorderProfile();
		List<int[]> fps_ranges = camera_controller
				.getSupportedPreviewFpsRange();
		int selected_min_fps = -1, selected_max_fps = -1, selected_diff = -1;
		for (int[] fps_range : fps_ranges) {
			if (MyDebug.LOG) {
				Log.d(TAG, "    supported fps range: " + fps_range[0] + " to "
						+ fps_range[1]);
			}
			int min_fps = fps_range[0];
			int max_fps = fps_range[1];
			if (min_fps <= profile.videoFrameRate * 1000
					&& max_fps >= profile.videoFrameRate * 1000) {
				int diff = max_fps - min_fps;
				if (selected_diff == -1 || diff < selected_diff) {
					selected_min_fps = min_fps;
					selected_max_fps = max_fps;
					selected_diff = diff;
				}
			}
		}
		if (selected_min_fps == -1) {
			selected_diff = -1;
			int selected_dist = -1;
			for (int[] fps_range : fps_ranges) {
				int min_fps = fps_range[0];
				int max_fps = fps_range[1];
				int diff = max_fps - min_fps;
				int dist = -1;
				if (max_fps < profile.videoFrameRate * 1000)
					dist = profile.videoFrameRate * 1000 - max_fps;
				else
					dist = min_fps - profile.videoFrameRate * 1000;
				if (MyDebug.LOG) {
					Log.d(TAG, "    supported fps range: " + min_fps + " to "
							+ max_fps + " has dist " + dist + " and diff "
							+ diff);
				}
				if (selected_dist == -1 || dist < selected_dist
						|| (dist == selected_dist && diff < selected_diff)) {
					selected_min_fps = min_fps;
					selected_max_fps = max_fps;
					selected_dist = dist;
					selected_diff = diff;
				}
			}
			if (MyDebug.LOG)
				Log.d(TAG,
						"    can't find match for fps range, so choose closest: "
								+ selected_min_fps + " to " + selected_max_fps);
			camera_controller.setPreviewFpsRange(selected_min_fps,
					selected_max_fps);
		} else {
			if (MyDebug.LOG) {
				Log.d(TAG, "    chosen fps range: " + selected_min_fps + " to "
						+ selected_max_fps);
			}
			camera_controller.setPreviewFpsRange(selected_min_fps,
					selected_max_fps);
		}
	}

	boolean focusIsVideo() {
		if (camera_controller != null) {
			return camera_controller.focusIsVideo();
		}
		return false;
	}

	void cycleFlash() {
		if (MyDebug.LOG)
			Log.d(TAG, "cycleFlash()");
		// if( is_taking_photo && !is_taking_photo_on_timer ) {
		if (this.phase == PHASE_TAKING_PHOTO && !is_video) {
			// just to be safe - risk of cancelling the autofocus before taking
			// a photo, or otherwise messing things up
			if (MyDebug.LOG)
				Log.d(TAG, "currently taking a photo");
			return;
		}
		if (this.supported_flash_values != null && this.supported_flash_values.size() > 1) {
			int new_flash_index = (current_flash_index + 1) % this.supported_flash_values.size();
			updateFlash(new_flash_index, true);
		}
	}

	void updateFlash(String focus_value) {
		if (MyDebug.LOG)
			Log.d(TAG, "updateFlash(): " + focus_value);
		if (this.phase == PHASE_TAKING_PHOTO && !is_video) {
			// just to be safe - risk of cancelling the autofocus before taking
			// a photo, or otherwise messing things up
			if (MyDebug.LOG)
				Log.d(TAG, "currently taking a photo");
			return;
		}
		updateFlash(focus_value, true);
	}

	private boolean updateFlash(String flash_value, boolean save) {
		if (MyDebug.LOG)
			Log.d(TAG, "updateFlash(): " + flash_value);
		if (supported_flash_values != null) {
			int new_flash_index = supported_flash_values.indexOf(flash_value);
			if (MyDebug.LOG)
				Log.d(TAG, "new_flash_index: " + new_flash_index);
			if (new_flash_index != -1) {
				updateFlash(new_flash_index, save);
				return true;
			}
		}
		return false;
	}

	private void updateFlash(int new_flash_index, boolean save) {
		if (MyDebug.LOG)
			Log.d(TAG, "updateFlash(): " + new_flash_index);
		// updates the Flash button, and Flash camera mode
		if (supported_flash_values != null && new_flash_index != current_flash_index) {
			boolean initial = current_flash_index == -1;
			current_flash_index = new_flash_index;
			if (MyDebug.LOG)
				Log.d(TAG, "    current_flash_index is now " + current_flash_index + " (initial " + initial + ")");

			String[] flash_entries = getResources().getStringArray(R.array.flash_entries);
			String flash_value = supported_flash_values.get(current_flash_index);
			if (MyDebug.LOG)
				Log.d(TAG, "    flash_value: " + flash_value);
			String[] flash_values = getResources().getStringArray(R.array.flash_values);
			for (int i = 0; i < flash_values.length; i++) {
				if (flash_value.equals(flash_values[i])) {
					if (MyDebug.LOG)
						Log.d(TAG, "    found entry: " + i);
					if (!initial) {
						showToast(flash_toast, flash_entries[i]);
					}
					break;
				}
			}
			this.setPopupIcon();
			this.setFlash(flash_value);
			if (save) {
				// now save
				SharedPreferences sharedPreferences = PreferenceManager.getDefaultSharedPreferences(this.getContext());
				SharedPreferences.Editor editor = sharedPreferences.edit();
				editor.putString(CameraActivityNew.getFlashPreferenceKey(cameraId), flash_value);
				editor.apply();
			}
		}
	}

	private void setFlash(String flash_value) {
		if (MyDebug.LOG)
			Log.d(TAG, "setFlash() " + flash_value);
		set_flash_value_after_autofocus = "";
		if (camera_controller == null) {
			if (MyDebug.LOG)
				Log.d(TAG, "camera not opened!");
			return;
		}
		cancelAutoFocus();
		camera_controller.setFlashValue(flash_value);
	}

	// this returns the flash value indicated by the UI, rather than from the
	// camera parameters (may be different, e.g., in startup autofocus!)
	public String getCurrentFlashValue() {
		if (this.current_flash_index == -1)
			return null;
		return this.supported_flash_values.get(current_flash_index);
	}

	// this returns the flash mode indicated by the UI, rather than from the
	// camera parameters (may be different, e.g., in startup autofocus!)
	void cycleFocusMode() {
		if (MyDebug.LOG)
			Log.d(TAG, "cycleFocusMode()");
		// if( is_taking_photo && !is_taking_photo_on_timer ) {
		if (this.phase == PHASE_TAKING_PHOTO) {
			// just to be safe - otherwise problem that changing the focus mode
			// will cancel the autofocus before taking a photo, so we never take
			// a photo, but is_taking_photo remains true!
			if (MyDebug.LOG)
				Log.d(TAG, "currently taking a photo");
			return;
		}
		if (this.supported_focus_values != null
				&& this.supported_focus_values.size() > 1) {
			int new_focus_index = (current_focus_index + 1)
					% this.supported_focus_values.size();
			updateFocus(new_focus_index, false, true, true);
		}
	}

	void updateFocus(String focus_value, boolean quiet, boolean auto_focus) {
		if (MyDebug.LOG)
			Log.d(TAG, "updateFocus(): " + focus_value);
		if (this.phase == PHASE_TAKING_PHOTO) {
			// just to be safe - otherwise problem that changing the focus mode
			// will cancel the autofocus before taking a photo, so we never take
			// a photo, but is_taking_photo remains true!
			if (MyDebug.LOG)
				Log.d(TAG, "currently taking a photo");
			return;
		}
		updateFocus(focus_value, quiet, true, auto_focus);
	}

	private boolean updateFocus(String focus_value, boolean quiet,
			boolean save, boolean auto_focus) {
		if (MyDebug.LOG)
			Log.d(TAG, "updateFocus(): " + focus_value);
		if (this.supported_focus_values != null) {
			int new_focus_index = supported_focus_values.indexOf(focus_value);
			if (MyDebug.LOG)
				Log.d(TAG, "new_focus_index: " + new_focus_index);
			if (new_focus_index != -1) {
				updateFocus(new_focus_index, quiet, save, auto_focus);
				return true;
			}
		}
		return false;
	}

	private void updateFocus(int new_focus_index, boolean quiet, boolean save,
			boolean auto_focus) {
		if (MyDebug.LOG)
			Log.d(TAG, "updateFocus(): " + new_focus_index
					+ " current_focus_index: " + current_focus_index);
		// updates the Focus button, and Focus camera mode
		if (this.supported_focus_values != null
				&& new_focus_index != current_focus_index) {
			boolean initial = current_focus_index == -1;
			current_focus_index = new_focus_index;
			if (MyDebug.LOG)
				Log.d(TAG, "    current_focus_index is now "
						+ current_focus_index + " (initial " + initial + ")");

			// Activity activity = (Activity)this.getContext();
			String[] focus_entries = getResources().getStringArray(
					R.array.focus_mode_entries);
			// String [] focus_icons =
			// getResources().getStringArray(R.array.focus_mode_icons);
			String focus_value = supported_focus_values
					.get(current_focus_index);
			if (MyDebug.LOG)
				Log.d(TAG, "    focus_value: " + focus_value);
			String[] focus_values = getResources().getStringArray(
					R.array.focus_mode_values);
			for (int i = 0; i < focus_values.length; i++) {
				if (MyDebug.LOG)
					Log.d(TAG, "    compare to: " + focus_values[i]);
				if (focus_value.equals(focus_values[i])) {
					if (MyDebug.LOG)
						Log.d(TAG, "    found entry: " + i);
					if (!initial && !quiet) {
						showToast(focus_toast, focus_entries[i]);
					}
					break;
				}
			}
			this.setFocusValue(focus_value, auto_focus);

			if (save) {
				// now save
				SharedPreferences sharedPreferences = PreferenceManager
						.getDefaultSharedPreferences(this.getContext());
				SharedPreferences.Editor editor = sharedPreferences.edit();
				editor.putString(CameraActivityNew.getFocusPreferenceKey(cameraId),
						focus_value);
				editor.apply();
			}
		}
	}

	// this returns the flash mode indicated by the UI, rather than from the
	// camera parameters
	public String getCurrentFocusValue() {
		if (MyDebug.LOG)
			Log.d(TAG, "getCurrentFocusValue()");
		if (camera_controller == null) {
			if (MyDebug.LOG)
				Log.d(TAG, "camera not opened!");
			return null;
		}
		if (this.supported_focus_values != null
				&& this.current_focus_index != -1)
			return this.supported_focus_values.get(current_focus_index);
		return null;
	}

	private void setFocusValue(String focus_value, boolean auto_focus) {
		if (MyDebug.LOG)
			Log.d(TAG, "setFocusValue() " + focus_value);
		if (camera_controller == null) {
			if (MyDebug.LOG)
				Log.d(TAG, "camera not opened!");
			return;
		}
		cancelAutoFocus();
		camera_controller.setFocusValue(focus_value);
		clearFocusAreas();
		// n.b., we reset even for manual focus mode
		if (auto_focus) {
			tryAutoFocus(false, false);
		}
	}

	void takePicturePressed() {
		if (camera_controller == null) {
			this.phase = PHASE_NORMAL;
			return;
		}
		if (!this.has_surface) {
			this.phase = PHASE_NORMAL;
			return;
		}
		if (this.isOnTimer()) {
			showToast(take_photo_toast, R.string.cancelled_timer);
			return;
		}
		if (this.phase == PHASE_TAKING_PHOTO) {
			if (is_video) {
			} else {
				if (remaining_burst_photos != 0) {
					remaining_burst_photos = 0;
					showToast(take_photo_toast, R.string.cancelled_burst_mode);
				}
			}
			return;
		}

		this.startCameraPreview();

		SharedPreferences sharedPreferences = PreferenceManager.getDefaultSharedPreferences(this.getContext());
		String burst_mode_value = sharedPreferences.getString(CameraActivityNew.getBurstModePreferenceKey(), "1");
		int n_burst = 1;
		if (burst_mode_value.equals("unlimited")) {
			n_burst = -1;
			remaining_burst_photos = -1;
		} else {
			try {
				n_burst = Integer.parseInt(burst_mode_value);
			} catch (NumberFormatException e) {
				e.printStackTrace();
				n_burst = 1;
			}
			remaining_burst_photos = n_burst - 1;
		}

		takePicture();
	}

	private void takePicture() {
		this.thumbnail_anim = false;
		this.phase = PHASE_TAKING_PHOTO;
		if (camera_controller == null) {
			this.phase = PHASE_NORMAL;
			showGUI(true);
			return;
		}
		if (!this.has_surface) {
			this.phase = PHASE_NORMAL;
			showGUI(true);
			return;
		}

		updateParametersFromLocation();

		showGUI(false);
		String focus_value = current_focus_index != -1 ? supported_focus_values.get(current_focus_index) : null;

		if (this.successfully_focused
				&& System.currentTimeMillis() < this.successfully_focused_time + 5000) {
			takePictureWhenFocused();
		} else if (focus_value != null
				&& (focus_value.equals("focus_mode_auto") || focus_value.equals("focus_mode_macro"))) {
			focus_success = FOCUS_DONE; // clear focus rectangle for new refocus
			CameraController.AutoFocusCallback autoFocusCallback = new CameraController.AutoFocusCallback() {
				@Override
				public void onAutoFocus(boolean success) {
					takePictureWhenFocused();
				}
			};
			try {
				camera_controller.autoFocus(autoFocusCallback);
				count_cameraAutoFocus++;
			} catch (RuntimeException e) {
				autoFocusCallback.onAutoFocus(false);
				e.printStackTrace();
			}
		} else {
			takePictureWhenFocused();
		}
	}

	private void takePictureWhenFocused() {
		// should be called when auto-focused
		if (camera_controller == null) {
			this.phase = PHASE_NORMAL;
			showGUI(true);
			return;
		}
		if (!this.has_surface) {
			this.phase = PHASE_NORMAL;
			showGUI(true);
			return;
		}

		String focus_value = current_focus_index != -1 ? supported_focus_values.get(current_focus_index) : null;
		if (MyDebug.LOG) {
			Log.d(TAG, "focus_value is " + focus_value);
			Log.d(TAG, "focus_success is " + focus_success);
		}

		if (focus_value != null && focus_value.equals("focus_mode_manual")
				&& focus_success == FOCUS_WAITING) {
			cancelAutoFocus();
		}
		focus_success = FOCUS_DONE; // clear focus rectangle if not already done
		successfully_focused = false; // so next photo taken will require an autofocus

		CameraController.PictureCallback jpegPictureCallback = new CameraController.PictureCallback() {
			public void onPictureTaken(byte[] data) {
				System.gc();

				CameraActivityNew main_activity = (CameraActivityNew) Preview.this.getContext();
				boolean image_capture_intent = false;
				Uri image_capture_intent_uri = null;
				String action = main_activity.getIntent().getAction();
				if (MediaStore.ACTION_IMAGE_CAPTURE.equals(action)) {
					image_capture_intent = true;
					Bundle myExtras = main_activity.getIntent().getExtras();
					if (myExtras != null) {
						image_capture_intent_uri = (Uri) myExtras.getParcelable(MediaStore.EXTRA_OUTPUT);
					}
				}

				boolean success = false;
				Bitmap bitmap = null;
				SharedPreferences sharedPreferences = PreferenceManager.getDefaultSharedPreferences(Preview.this.getContext());
				boolean auto_stabilise = sharedPreferences.getBoolean(
						CameraActivityNew.getAutoStabilisePreferenceKey(), false);
				if (auto_stabilise && has_level_angle
						&& main_activity.supportsAutoStabilise()) {
					if (test_have_angle)
						level_angle = test_angle;
					while (level_angle < -90)
						level_angle += 180;
					while (level_angle > 90)
						level_angle -= 180;
					BitmapFactory.Options options = new BitmapFactory.Options();
					options.inPurgeable = true;
					bitmap = BitmapFactory.decodeByteArray(data, 0, data.length, options);
					if (bitmap == null) {
						showToast(null, R.string.failed_to_auto_stabilise);
						System.gc();
					} else {
						int width = bitmap.getWidth();
						int height = bitmap.getHeight();
						if (MyDebug.LOG) {
							Log.d(TAG, "level_angle: " + level_angle);
							Log.d(TAG, "decoded bitmap size " + width + ", " + height);
							Log.d(TAG, "bitmap size: " + width * height * 4);
						}
						Matrix matrix = new Matrix();
						double level_angle_rad_abs = Math.abs(Math.toRadians(level_angle));
						int w1 = width, h1 = height;
						double w0 = (w1 * Math.cos(level_angle_rad_abs) + h1 * Math.sin(level_angle_rad_abs));
						double h0 = (w1 * Math.sin(level_angle_rad_abs) + h1 * Math.cos(level_angle_rad_abs));
						float orig_size = w1 * h1;
						float rotated_size = (float) (w0 * h0);
						float scale = (float) Math.sqrt(orig_size / rotated_size);
						if (MyDebug.LOG) {
							Log.d(TAG, "w0 = " + w0 + " , h0 = " + h0);
							Log.d(TAG, "w1 = " + w1 + " , h1 = " + h1);
							Log.d(TAG, "scale = sqrt " + orig_size + " / " + rotated_size + " = " + scale);
						}
						matrix.postScale(scale, scale);
						w0 *= scale;
						h0 *= scale;
						w1 *= scale;
						h1 *= scale;
						if (MyDebug.LOG) {
							Log.d(TAG, "after scaling: w0 = " + w0 + " , h0 = " + h0);
							Log.d(TAG, "after scaling: w1 = " + w1 + " , h1 = " + h1);
						}
						// I have received crashes where camera_controller was
						// null - could perhaps happen if this thread was
						// running just as the camera is closing?
						if (camera_controller != null && camera_controller.isFrontFacing()) {
							matrix.postRotate((float) -level_angle);
						} else {
							matrix.postRotate((float) level_angle);
						}
						Bitmap new_bitmap = Bitmap.createBitmap(bitmap, 0, 0, width, height, matrix, true);
						// careful, as new_bitmap is sometimes not a copy!
						if (new_bitmap != bitmap) {
							bitmap.recycle();
							bitmap = new_bitmap;
						}
						System.gc();
						if (MyDebug.LOG) {
							Log.d(TAG, "rotated and scaled bitmap size "
									+ bitmap.getWidth() + ", " + bitmap.getHeight());
							Log.d(TAG, "rotated and scaled bitmap size: "
									+ bitmap.getWidth() * bitmap.getHeight() * 4);
						}
						double tan_theta = Math.tan(level_angle_rad_abs);
						double sin_theta = Math.sin(level_angle_rad_abs);
						double denom = (double) (h0 / w0 + tan_theta);
						double alt_denom = (double) (w0 / h0 + tan_theta);
						if (denom == 0.0 || denom < 1.0e-14) {
							if (MyDebug.LOG)
								Log.d(TAG, "zero denominator?!");
						} else if (alt_denom == 0.0 || alt_denom < 1.0e-14) {
							if (MyDebug.LOG)
								Log.d(TAG, "zero alt denominator?!");
						} else {
							int w2 = (int) ((h0 + 2.0 * h1 * sin_theta * tan_theta - w0 * tan_theta) / denom);
							int h2 = (int) (w2 * h0 / (double) w0);
							int alt_h2 = (int) ((w0 + 2.0 * w1 * sin_theta * tan_theta - h0 * tan_theta) / alt_denom);
							int alt_w2 = (int) (alt_h2 * w0 / (double) h0);
							if (MyDebug.LOG) {
								Log.d(TAG, "w2 = " + w2 + " , h2 = " + h2);
								Log.d(TAG, "alt_w2 = " + alt_w2 + " , alt_h2 = " + alt_h2);
							}
							if (alt_w2 < w2) {
								if (MyDebug.LOG) {
									Log.d(TAG, "chose alt!");
								}
								w2 = alt_w2;
								h2 = alt_h2;
							}
							if (w2 <= 0)
								w2 = 1;
							else if (w2 >= bitmap.getWidth())
								w2 = bitmap.getWidth() - 1;
							if (h2 <= 0)
								h2 = 1;
							else if (h2 >= bitmap.getHeight())
								h2 = bitmap.getHeight() - 1;
							int x0 = (bitmap.getWidth() - w2) / 2;
							int y0 = (bitmap.getHeight() - h2) / 2;
							if (MyDebug.LOG) {
								Log.d(TAG, "x0 = " + x0 + " , y0 = " + y0);
							}
							new_bitmap = Bitmap.createBitmap(bitmap, x0, y0, w2, h2);
							if (new_bitmap != bitmap) {
								bitmap.recycle();
								bitmap = new_bitmap;
							}
							System.gc();
						}
					}
				}
				String preference_stamp = sharedPreferences.getString(
						CameraActivityNew.getStampPreferenceKey(), "preference_stamp_no");
				if (preference_stamp.equals("preference_stamp_yes")) {
					if (bitmap == null) {
						if (MyDebug.LOG)
							Log.d(TAG, "decode bitmap in order to stamp info");
						BitmapFactory.Options options = new BitmapFactory.Options();
						options.inMutable = true;
						options.inPurgeable = true;
						bitmap = BitmapFactory.decodeByteArray(data, 0, data.length, options);
						if (bitmap == null) {
							showToast(null, R.string.failed_to_stamp);
							System.gc();
						}
					}
					if (bitmap != null) {
						if (MyDebug.LOG)
							Log.d(TAG, "stamp info to bitmap");
						int width = bitmap.getWidth();
						int height = bitmap.getHeight();
						if (MyDebug.LOG) {
							Log.d(TAG, "decoded bitmap size " + width + ", " + height);
							Log.d(TAG, "bitmap size: " + width * height * 4);
						}
						Canvas canvas = new Canvas(bitmap);
						final float scale = getResources().getDisplayMetrics().density;
						p.setColor(Color.WHITE);
						p.setTextSize(20 * scale + 0.5f);
						String time_stamp = DateFormat.getDateTimeInstance().format(new Date());
						int offset_x = (int) (8 * scale + 0.5f);
						int offset_y = (int) (8 * scale + 0.5f);
						int diff_y = (int) (24 * scale + 0.5f);
						p.setTextAlign(Align.RIGHT);
						drawTextWithBackground(canvas, p, time_stamp, Color.WHITE, Color.BLACK, width - offset_x, height - offset_y);
					}
				}

				String exif_orientation_s = null;
				String picFileName = null;
				File picFile = null;
				try {
					OutputStream outputStream = null;
					if (image_capture_intent) {
						if (MyDebug.LOG)
							Log.d(TAG, "image_capture_intent");
						if (image_capture_intent_uri != null) {
							// Save the bitmap to the specified URI (use a
							// try/catch block)
							if (MyDebug.LOG)
								Log.d(TAG, "save to: " + image_capture_intent_uri);
							outputStream = main_activity.getContentResolver().openOutputStream(image_capture_intent_uri);
						} else {
							// If the intent doesn't contain an URI, send the
							// bitmap as a parcel
							// (it is a good idea to reduce its size to ~50k
							// pixels before)
							if (MyDebug.LOG)
								Log.d(TAG, "sent to intent via parcel");
							if (bitmap == null) {
								if (MyDebug.LOG)
									Log.d(TAG, "create bitmap");
								BitmapFactory.Options options = new BitmapFactory.Options();
								// options.inMutable = true;
								options.inPurgeable = true;
								bitmap = BitmapFactory.decodeByteArray(data, 0, data.length, options);
							}
							if (bitmap != null) {
								int width = bitmap.getWidth();
								int height = bitmap.getHeight();
								if (MyDebug.LOG) {
									Log.d(TAG, "decoded bitmap size " + width + ", " + height);
									Log.d(TAG, "bitmap size: " + width * height * 4);
								}
								final int small_size_c = 128;
								if (width > small_size_c) {
									float scale = ((float) small_size_c) / (float) width;
									if (MyDebug.LOG)
										Log.d(TAG, "scale to " + scale);
									Matrix matrix = new Matrix();
									matrix.postScale(scale, scale);
									Bitmap new_bitmap = Bitmap.createBitmap(bitmap, 0, 0, width, height, matrix, true);
									// careful, as new_bitmap is sometimes not a copy!
									if (new_bitmap != bitmap) {
										bitmap.recycle();
										bitmap = new_bitmap;
									}
								}
							}
							if (MyDebug.LOG) {
								Log.d(TAG, "returned bitmap size " + bitmap.getWidth() + ", " + bitmap.getHeight());
								Log.d(TAG, "returned bitmap size: " + bitmap.getWidth() * bitmap.getHeight() * 4);
							}
							main_activity.setResult(Activity.RESULT_OK, new Intent("inline-data").putExtra("data", bitmap));
							main_activity.finish();
						}
					} else {
						picFile = main_activity.getOutputMediaFile(CameraActivityNew.MEDIA_TYPE_IMAGE);
						if (picFile == null) {
							Log.e(TAG, "Couldn't create media image file; check storage permissions?");
							showToast(null, R.string.failed_to_save_image);
						} else {
							picFileName = picFile.getAbsolutePath();
							if (MyDebug.LOG)
								Log.d(TAG, "save to: " + picFileName);
							outputStream = new FileOutputStream(picFile);
						}
					}

					if (outputStream != null) {
						if (bitmap != null) {
							int image_quality = getImageQuality();
							bitmap.compress(Bitmap.CompressFormat.JPEG, image_quality, outputStream);
						} else {
							outputStream.write(data);
						}
						outputStream.close();
						if (MyDebug.LOG)
							Log.d(TAG, "onPictureTaken saved photo");

						success = true;
						if (picFile != null) {
							if (bitmap != null) {
								// need to update EXIF data!
								if (MyDebug.LOG)
									Log.d(TAG, "write temp file to record EXIF data");
								File tempFile = File.createTempFile("opencamera_exif", "");
								OutputStream tempOutputStream = new FileOutputStream(tempFile);
								tempOutputStream.write(data);
								tempOutputStream.close();
								if (MyDebug.LOG)
									Log.d(TAG, "read back EXIF data");
								ExifInterface exif = new ExifInterface(tempFile.getAbsolutePath());
								String exif_aperture = exif.getAttribute(ExifInterface.TAG_APERTURE);
								String exif_datetime = exif.getAttribute(ExifInterface.TAG_DATETIME);
								String exif_exposure_time = exif.getAttribute(ExifInterface.TAG_EXPOSURE_TIME);
								String exif_flash = exif.getAttribute(ExifInterface.TAG_FLASH);
								String exif_focal_length = exif.getAttribute(ExifInterface.TAG_FOCAL_LENGTH);
								// leave width/height, as this will have
								// changed!
								String exif_iso = exif.getAttribute(ExifInterface.TAG_ISO);
								String exif_make = exif.getAttribute(ExifInterface.TAG_MAKE);
								String exif_model = exif.getAttribute(ExifInterface.TAG_MODEL);
								String exif_orientation = exif.getAttribute(ExifInterface.TAG_ORIENTATION);
								exif_orientation_s = exif_orientation; 
								String exif_white_balance = exif.getAttribute(ExifInterface.TAG_WHITE_BALANCE);

								if (!tempFile.delete()) {
									if (MyDebug.LOG)
										Log.e(TAG, "failed to delete temp " + tempFile.getAbsolutePath());
								}
								if (MyDebug.LOG)
									Log.d(TAG, "now write new EXIF data");
								ExifInterface exif_new = new ExifInterface(picFile.getAbsolutePath());
								if (exif_aperture != null)
									exif_new.setAttribute(ExifInterface.TAG_APERTURE, exif_aperture);
								if (exif_datetime != null)
									exif_new.setAttribute(ExifInterface.TAG_DATETIME, exif_datetime);
								if (exif_exposure_time != null)
									exif_new.setAttribute(ExifInterface.TAG_EXPOSURE_TIME, exif_exposure_time);
								if (exif_flash != null)
									exif_new.setAttribute(ExifInterface.TAG_FLASH, exif_flash);
								if (exif_focal_length != null)
									exif_new.setAttribute(ExifInterface.TAG_FOCAL_LENGTH, exif_focal_length);
								// leave width/height, as this will have
								// changed!
								if (exif_iso != null)
									exif_new.setAttribute(ExifInterface.TAG_ISO, exif_iso);
								if (exif_make != null)
									exif_new.setAttribute(ExifInterface.TAG_MAKE, exif_make);
								if (exif_model != null)
									exif_new.setAttribute(ExifInterface.TAG_MODEL, exif_model);
								if (exif_orientation != null)
									exif_new.setAttribute(ExifInterface.TAG_ORIENTATION, exif_orientation);
								if (exif_white_balance != null)
									exif_new.setAttribute(ExifInterface.TAG_WHITE_BALANCE, exif_white_balance);
								setDateTimeExif(exif_new);
								exif_new.saveAttributes();
								if (MyDebug.LOG)
									Log.d(TAG, "now saved EXIF data");
							}

							test_last_saved_image = picFileName;
						}
						if (image_capture_intent) {
							main_activity.setResult(Activity.RESULT_OK);
							main_activity.finish();
						}
					}
				} catch (FileNotFoundException e) {
					if (MyDebug.LOG)
						Log.e(TAG, "File not found: " + e.getMessage());
					e.getStackTrace();
					showToast(null, R.string.failed_to_save_photo);
				} catch (IOException e) {
					if (MyDebug.LOG)
						Log.e(TAG, "I/O error writing file: " + e.getMessage());
					e.getStackTrace();
					showToast(null, R.string.failed_to_save_photo);
				}

				is_preview_started = false;
				phase = PHASE_NORMAL;
				if (remaining_burst_photos == -1 || remaining_burst_photos > 0) {
					startCameraPreview();
					if (MyDebug.LOG)
						Log.d(TAG, "burst mode photos remaining: onPictureTaken started preview");
				} else {
					phase = PHASE_NORMAL;
					boolean pause_preview = sharedPreferences.getBoolean(
							CameraActivityNew.getPausePreviewPreferenceKey(), false);
					if (MyDebug.LOG)
						Log.d(TAG, "pause_preview? " + pause_preview);
					if (pause_preview && success) {
						setPreviewPaused(true);
						preview_image_name = picFileName;
					} else {
						startCameraPreview();
						showGUI(true);
						if (MyDebug.LOG)
							Log.d(TAG, "onPictureTaken started preview");
					}
				}

				if (bitmap != null) {
					bitmap.recycle();
					bitmap = null;
				}

				System.gc();

				if (MyDebug.LOG)
					Log.d(TAG, "remaining_burst_photos: " + remaining_burst_photos);
				if (remaining_burst_photos == -1 || remaining_burst_photos > 0) {
					if (remaining_burst_photos > 0)
						remaining_burst_photos--;

					String timer_value = sharedPreferences.getString(
							CameraActivityNew.getBurstIntervalPreferenceKey(), "0");
					long timer_delay = 0;
					try {
						timer_delay = Integer.parseInt(timer_value) * 1000;
					} catch (NumberFormatException e) {
						if (MyDebug.LOG)
							Log.e(TAG, "failed to parse preference_burst_interval value: " + timer_value);
						e.printStackTrace();
						timer_delay = 0;
					}

					if (timer_delay == 0) {
						phase = PHASE_TAKING_PHOTO;
						showGUI(false);
						takePictureWhenFocused();
					}
				}
				
				// TODO 拍照成功
				if (success && picFile != null) {
					
				}
			}
		};
		{
			camera_controller.setRotation(getImageVideoRotation());

			SharedPreferences sharedPreferences = PreferenceManager
					.getDefaultSharedPreferences(this.getContext());
			boolean enable_sound = sharedPreferences.getBoolean(
					CameraActivityNew.getShutterSoundPreferenceKey(), true);
			if (MyDebug.LOG)
				Log.d(TAG, "enable_sound? " + enable_sound);
			camera_controller.enableShutterSound(enable_sound);
			if (MyDebug.LOG)
				Log.d(TAG, "about to call takePicture");
			try {
				camera_controller.takePicture(null, jpegPictureCallback);
				count_cameraTakePicture++;
				// showToast(take_photo_toast, toast_text);
			} catch (RuntimeException e) {
				// just in case? We got a RuntimeException report here from 1
				// user on Google Play; I also encountered it myself once of
				// Galaxy Nexus when starting up
				if (MyDebug.LOG)
					Log.e(TAG, "runtime exception from takePicture");
				e.printStackTrace();
				showToast(null, R.string.failed_to_take_picture);
				this.phase = PHASE_NORMAL;
				startCameraPreview();
				showGUI(true);
			}
		}
		if (MyDebug.LOG)
			Log.d(TAG, "takePicture exit");
	}

	private void setDateTimeExif(ExifInterface exif) {
		String exif_datetime = exif.getAttribute(ExifInterface.TAG_DATETIME);
		if (exif_datetime != null) {
			if (MyDebug.LOG)
				Log.d(TAG, "write datetime tags: " + exif_datetime);
			exif.setAttribute("DateTimeOriginal", exif_datetime);
			exif.setAttribute("DateTimeDigitized", exif_datetime);
		}
	}

	void clickedShare() {
		if (MyDebug.LOG)
			Log.d(TAG, "clickedShare");
		// if( is_preview_paused ) {
		if (this.phase == PHASE_PREVIEW_PAUSED) {
			if (preview_image_name != null) {
				if (MyDebug.LOG)
					Log.d(TAG, "Share: " + preview_image_name);
				Intent intent = new Intent(Intent.ACTION_SEND);
				intent.setType("image/jpeg");
				intent.putExtra(Intent.EXTRA_STREAM, Uri.parse("file://" + preview_image_name));
				Activity activity = (Activity) this.getContext();
				activity.startActivity(Intent.createChooser(intent, "Photo"));
			}
			startCameraPreview();
			tryAutoFocus(false, false);
		}
	}

	void requestAutoFocus() {
		if (MyDebug.LOG)
			Log.d(TAG, "requestAutoFocus");
		cancelAutoFocus();
		tryAutoFocus(false, true);
	}

	private void tryAutoFocus(final boolean startup, final boolean manual) {
		// manual: whether user has requested autofocus (e.g., by touching
		// screen, or volume focus, or hardware focus button)
		// consider whether you want to call requestAutoFocus() instead (which
		// properly cancels any in-progress auto-focus first)
		if (MyDebug.LOG) {
			Log.d(TAG, "tryAutoFocus");
			Log.d(TAG, "startup? " + startup);
			Log.d(TAG, "manual? " + manual);
		}
		if (camera_controller == null) {
			if (MyDebug.LOG)
				Log.d(TAG, "camera not opened!");
		} else if (!this.has_surface) {
			if (MyDebug.LOG)
				Log.d(TAG, "preview surface not yet available");
		} else if (!this.is_preview_started) {
			if (MyDebug.LOG)
				Log.d(TAG, "preview not yet started");
		}
		// else if( is_taking_photo ) {
		else if (!(manual && this.is_video) && this.isTakingPhotoOrOnTimer()) {
			// if taking a video, we allow manual autofocuses
			// autofocus may cause problem if there is a video corruption
			// problem, see testTakeVideoBitrate() on Nexus 7 at 30Mbs or 50Mbs,
			// where the startup autofocus would cause a problem here
			if (MyDebug.LOG)
				Log.d(TAG, "currently taking a photo");
		} else {
			// it's only worth doing autofocus when autofocus has an effect
			// (i.e., auto or macro mode)
			if (camera_controller.supportsAutoFocus()) {
				if (MyDebug.LOG)
					Log.d(TAG, "try to start autofocus");
				set_flash_value_after_autofocus = "";
				String old_flash_value = camera_controller.getFlashValue();
				// getFlashValue() may return "" if flash not supported!
				if (startup && old_flash_value.length() > 0
						&& !old_flash_value.equals("flash_off")) {
					set_flash_value_after_autofocus = old_flash_value;
					camera_controller.setFlashValue("flash_off");
				}
				CameraController.AutoFocusCallback autoFocusCallback = new CameraController.AutoFocusCallback() {
					@Override
					public void onAutoFocus(boolean success) {
						if (MyDebug.LOG)
							Log.d(TAG, "autofocus complete: " + success);
						autoFocusCompleted(manual, success, false);
					}
				};

				this.focus_success = FOCUS_WAITING;
				if (MyDebug.LOG)
					Log.d(TAG, "set focus_success to " + focus_success);
				this.focus_complete_time = -1;
				this.successfully_focused = false;
				try {
					camera_controller.autoFocus(autoFocusCallback);
					count_cameraAutoFocus++;
					if (MyDebug.LOG)
						Log.d(TAG, "autofocus started");
				} catch (RuntimeException e) {
					// just in case? We got a RuntimeException report here from
					// 1 user on Google Play
					autoFocusCallback.onAutoFocus(false);

					if (MyDebug.LOG)
						Log.e(TAG, "runtime exception from autoFocus");
					e.printStackTrace();
				}
			} else if (has_focus_area) {
				// do this so we get the focus box, for focus modes that support
				// focus area, but don't support autofocus
				focus_success = FOCUS_SUCCESS;
				focus_complete_time = System.currentTimeMillis();
			}
		}
	}

	private void cancelAutoFocus() {
		if (MyDebug.LOG)
			Log.d(TAG, "cancelAutoFocus");
		if (camera_controller != null) {
			try {
				camera_controller.cancelAutoFocus();
			} catch (RuntimeException e) {
				// had a report of crash on some devices, see comment at
				// https://sourceforge.net/p/opencamera/tickets/4/ made on
				// 20140520
				if (MyDebug.LOG)
					Log.d(TAG, "cancelAutoFocus() failed");
				e.printStackTrace();
			}
			autoFocusCompleted(false, false, true);
		}
	}

	private void autoFocusCompleted(boolean manual, boolean success,
			boolean cancelled) {
		if (MyDebug.LOG) {
			Log.d(TAG, "autoFocusCompleted");
			Log.d(TAG, "    manual? " + manual);
			Log.d(TAG, "    success? " + success);
			Log.d(TAG, "    cancelled? " + cancelled);
		}
		if (cancelled) {
			focus_success = FOCUS_DONE;
		} else {
			focus_success = success ? FOCUS_SUCCESS : FOCUS_FAILED;
			focus_complete_time = System.currentTimeMillis();
		}
		CameraActivityNew main_activity = (CameraActivityNew) Preview.this.getContext();
		if (manual && !cancelled && (success || main_activity.is_test)) {
			successfully_focused = true;
			successfully_focused_time = focus_complete_time;
		}
		if (set_flash_value_after_autofocus.length() > 0
				&& camera_controller != null) {
			if (MyDebug.LOG)
				Log.d(TAG, "set flash back to: "
						+ set_flash_value_after_autofocus);
			camera_controller.setFlashValue(set_flash_value_after_autofocus);
			set_flash_value_after_autofocus = "";
		}
		if (this.using_face_detection && !cancelled) {
			// On some devices such as mtk6589, face detection does not resume
			// as written in documentation so we have
			// to cancelfocus when focus is finished
			if (camera_controller != null) {
				try {
					camera_controller.cancelAutoFocus();
				} catch (RuntimeException e) {
					if (MyDebug.LOG)
						Log.d(TAG, "cancelAutoFocus() failed");
					e.printStackTrace();
				}
			}
		}
	}

	private void startCameraPreview() {
		long debug_time = 0;
		if (MyDebug.LOG) {
			Log.d(TAG, "startCameraPreview");
			debug_time = System.currentTimeMillis();
		}
		// if( camera != null && !is_taking_photo && !is_preview_started ) {
		if (camera_controller != null && !this.isTakingPhotoOrOnTimer()
				&& !is_preview_started) {
			if (MyDebug.LOG)
				Log.d(TAG, "starting the camera preview");
			{
				if (MyDebug.LOG)
					Log.d(TAG, "setRecordingHint: " + is_video);
				camera_controller.setRecordingHint(this.is_video);
			}
			if (this.is_video) {
				matchPreviewFpsToVideo();
			}
			// else, we reset the preview fps to default in switchVideo
			try {
				camera_controller.startPreview();
				count_cameraStartPreview++;
			} catch (RuntimeException e) {
				if (MyDebug.LOG)
					Log.d(TAG, "RuntimeException trying to startPreview");
				e.printStackTrace();
				showToast(null, R.string.failed_to_start_camera_preview);
				return;
			}
			this.is_preview_started = true;
			if (MyDebug.LOG) {
				Log.d(TAG,
						"time after starting camera preview: "
								+ (System.currentTimeMillis() - debug_time));
			}
			if (this.using_face_detection) {
				if (MyDebug.LOG)
					Log.d(TAG, "start face detection");
				try {
					camera_controller.startFaceDetection();
				} catch (RuntimeException e) {
					// I didn't think this could happen, as we only call
					// startFaceDetection() after we've called takePicture() or
					// stopPreview(), which the Android docs say stops the face
					// detection
					// however I had a crash reported on Google Play for Open
					// Camera v1.4
					// 2 Jan 2014, "maxx_ax5", Android 4.0.3-4.0.4
					// startCameraPreview() was called after taking photo in
					// burst mode, but I tested with burst mode and face
					// detection, and can't reproduce the crash on Galaxy Nexus
					if (MyDebug.LOG)
						Log.d(TAG, "face detection already started");
				}
				faces_detected = null;
			}
		}
		this.setPreviewPaused(false);
	}

	private void setPreviewPaused(boolean paused) {
		if (MyDebug.LOG)
			Log.d(TAG, "setPreviewPaused: " + paused);
		if (paused) {
			this.phase = PHASE_PREVIEW_PAUSED;
		} else {
			this.phase = PHASE_NORMAL;
			preview_image_name = null;
			showGUI(true);
		}
	}

	private boolean immersive_mode = false;

	void setImmersiveMode(final boolean immersive_mode) {
		if (MyDebug.LOG)
			Log.d(TAG, "setImmersiveMode: " + immersive_mode);
		this.immersive_mode = immersive_mode;
		final CameraActivityNew main_activity = (CameraActivityNew) this.getContext();
		main_activity.runOnUiThread(new Runnable() {
			public void run() {
				SharedPreferences sharedPreferences = PreferenceManager
						.getDefaultSharedPreferences(main_activity);
				// if going into immersive mode, the we should set GONE the ones
				// that are set GONE in showGUI(false)
				// final int visibility_gone = immersive_mode ? View.GONE :
				// View.VISIBLE;
				final int visibility = immersive_mode ? View.GONE
						: View.VISIBLE;
				if (MyDebug.LOG)
					Log.d(TAG, "setImmersiveMode: set visibility: "
							+ visibility);
				// n.b., don't hide share and trash buttons, as they require
				// immediate user input for us to continue
				View popupButton = (View) main_activity
						.findViewById(R.id.popup);
				View galleryButton = (View) main_activity
						.findViewById(R.id.gallery);
				View zoomControls = (View) main_activity
						.findViewById(R.id.zoom);
				View zoomSeekBar = (View) main_activity
						.findViewById(R.id.zoom_seekbar);
				if (supported_flash_values == null)
					popupButton.setVisibility(visibility);
				galleryButton.setVisibility(visibility);
				if (MyDebug.LOG) {
					Log.d(TAG, "has_zoom: " + has_zoom);
				}
				if (has_zoom
						&& sharedPreferences.getBoolean(
								CameraActivityNew.getShowZoomControlsPreferenceKey(),
								false)) {
					zoomControls.setVisibility(visibility);
				}
				if (has_zoom
						&& sharedPreferences.getBoolean(CameraActivityNew
								.getShowZoomSliderControlsPreferenceKey(), true)) {
					zoomSeekBar.setVisibility(visibility);
				}
				String pref_immersive_mode = sharedPreferences.getString(
						CameraActivityNew.getImmersiveModePreferenceKey(),
						"immersive_mode_low_profile");
				if (pref_immersive_mode.equals("immersive_mode_everything")) {
					View takePhotoButton = (View) main_activity
							.findViewById(R.id.take_photo);
					takePhotoButton.setVisibility(visibility);
				}
				if (!immersive_mode) {
					// make sure the GUI is set up as expected
					showGUI(show_gui);
				}
			}
		});
	}

	private boolean show_gui = true; // result of call to showGUI() - false
										// means a "reduced" GUI is displayed,
										// whilst taking photo or video

	private void showGUI(final boolean show) {
		if (MyDebug.LOG)
			Log.d(TAG, "showGUI: " + show);
		this.show_gui = show;
		if (immersive_mode)
			return;
		final CameraActivityNew main_activity = (CameraActivityNew) this.getContext();
		if (show && main_activity.usingKitKatImmersiveMode()) {
			// call to reset the timer
			main_activity.initImmersiveMode();
		}
		main_activity.runOnUiThread(new Runnable() {
			public void run() {
				final int visibility = show ? View.VISIBLE : View.GONE;
				View popupButton = (View) main_activity
						.findViewById(R.id.popup);
				if (!show) {
				}
				if (!is_video || supported_flash_values == null)
					popupButton.setVisibility(visibility); 
			}
		});
	}

	private void setPopupIcon() {
		if (MyDebug.LOG)
			Log.d(TAG, "setPopupIcon");
		CameraActivityNew main_activity = (CameraActivityNew) this.getContext();
		ImageButton popup = (ImageButton) main_activity.findViewById(R.id.popup);
		String flash_value = getCurrentFlashValue();
		if (MyDebug.LOG)
			Log.d(TAG, "flash_value: " + flash_value);
		if (flash_value != null && flash_value.equals("flash_torch")) {
			popup.setImageResource(R.drawable.popup_flash_torch);
		} else if (flash_value != null && flash_value.equals("flash_auto")) {
			popup.setImageResource(R.drawable.popup_flash_auto);
		} else if (flash_value != null && flash_value.equals("flash_on")) {
			popup.setImageResource(R.drawable.popup_flash_on);
		} else {
			popup.setImageResource(R.drawable.popup);
		}
	}

	void onAccelerometerSensorChanged(SensorEvent event) {

		this.has_gravity = true;
		for (int i = 0; i < 3; i++) {
			// this.gravity[i] = event.values[i];
			this.gravity[i] = sensor_alpha * this.gravity[i]
					+ (1.0f - sensor_alpha) * event.values[i];
		}
		calculateGeoDirection();

		double x = gravity[0];
		double y = gravity[1];
		this.has_level_angle = true;
		this.level_angle = Math.atan2(-x, y) * 180.0 / Math.PI;
		if (this.level_angle < -0.0) {
			this.level_angle += 360.0;
		}
		this.orig_level_angle = this.level_angle;
		this.level_angle -= (float) this.current_orientation;
		if (this.level_angle < -180.0) {
			this.level_angle += 360.0;
		} else if (this.level_angle > 180.0) {
			this.level_angle -= 360.0;
		}

		surfaceView.invalidate();
	}

	void onMagneticSensorChanged(SensorEvent event) {
		this.has_geomagnetic = true;
		for (int i = 0; i < 3; i++) {
			// this.geomagnetic[i] = event.values[i];
			this.geomagnetic[i] = sensor_alpha * this.geomagnetic[i]
					+ (1.0f - sensor_alpha) * event.values[i];
		}
		calculateGeoDirection();
	}

	private void calculateGeoDirection() {
		if (!this.has_gravity || !this.has_geomagnetic) {
			return;
		}
		if (!SensorManager.getRotationMatrix(this.deviceRotation,
				this.deviceInclination, this.gravity, this.geomagnetic)) {
			return;
		}
		SensorManager
				.remapCoordinateSystem(this.deviceRotation,
						SensorManager.AXIS_X, SensorManager.AXIS_Z,
						this.cameraRotation);
		SensorManager.getOrientation(cameraRotation, geo_direction);
	}

	public boolean supportsFaceDetection() {
		if (MyDebug.LOG)
			Log.d(TAG, "supportsFaceDetection");
		return supports_face_detection;
	}

	public boolean supportsVideoStabilization() {
		if (MyDebug.LOG)
			Log.d(TAG, "supportsVideoStabilization");
		return supports_video_stabilization;
	}

	boolean canDisableShutterSound() {
		if (MyDebug.LOG)
			Log.d(TAG, "canDisableShutterSound");
		return can_disable_shutter_sound;
	}

	public List<String> getSupportedColorEffects() {
		if (MyDebug.LOG)
			Log.d(TAG, "getSupportedColorEffects");
		return this.color_effects;
	}

	public List<String> getSupportedSceneModes() {
		if (MyDebug.LOG)
			Log.d(TAG, "getSupportedSceneModes");
		return this.scene_modes;
	}

	public List<String> getSupportedWhiteBalances() {
		if (MyDebug.LOG)
			Log.d(TAG, "getSupportedWhiteBalances");
		return this.white_balances;
	}

	String getISOKey() {
		if (MyDebug.LOG)
			Log.d(TAG, "getISOKey");
		return camera_controller == null ? "" : camera_controller.getISOKey();
	}

	public List<String> getSupportedISOs() {
		if (MyDebug.LOG)
			Log.d(TAG, "getSupportedISOs");
		return this.isos;
	}

	public boolean supportsExposures() {
		if (MyDebug.LOG)
			Log.d(TAG, "supportsExposures");
		return this.exposures != null;
	}

	public int getMinimumExposure() {
		if (MyDebug.LOG)
			Log.d(TAG, "getMinimumExposure");
		return this.min_exposure;
	}

	public int getMaximumExposure() {
		if (MyDebug.LOG)
			Log.d(TAG, "getMaximumExposure");
		return this.max_exposure;
	}

	public int getCurrentExposure() {
		if (MyDebug.LOG)
			Log.d(TAG, "getCurrentExposure");
		if (camera_controller == null) {
			if (MyDebug.LOG)
				Log.d(TAG, "camera not opened!");
			return 0;
		}
		int current_exposure = camera_controller.getExposureCompensation();
		return current_exposure;
	}

	List<String> getSupportedExposures() {
		if (MyDebug.LOG)
			Log.d(TAG, "getSupportedExposures");
		return this.exposures;
	}

	public List<CameraController.Size> getSupportedPreviewSizes() {
		if (MyDebug.LOG)
			Log.d(TAG, "getSupportedPreviewSizes");
		return this.supported_preview_sizes;
	}

	public List<CameraController.Size> getSupportedPictureSizes() {
		if (MyDebug.LOG)
			Log.d(TAG, "getSupportedPictureSizes");
		return this.sizes;
	}

	int getCurrentPictureSizeIndex() {
		if (MyDebug.LOG)
			Log.d(TAG, "getCurrentPictureSizeIndex");
		return this.current_size_index;
	}

	public List<String> getSupportedVideoQuality() {
		if (MyDebug.LOG)
			Log.d(TAG, "getSupportedVideoQuality");
		return this.video_quality;
	}

	List<CameraController.Size> getSupportedVideoSizes() {
		if (MyDebug.LOG)
			Log.d(TAG, "getSupportedVideoSizes");
		return this.video_sizes;
	}

	public List<String> getSupportedFlashValues() {
		return supported_flash_values;
	}

	public List<String> getSupportedFocusValues() {
		return supported_focus_values;
	}

	public int getCameraId() {
		return this.cameraId;
	}

	private int getImageQuality() {
		if (MyDebug.LOG)
			Log.d(TAG, "getImageQuality");
		SharedPreferences sharedPreferences = PreferenceManager
				.getDefaultSharedPreferences(this.getContext());
		String image_quality_s = sharedPreferences.getString(
				CameraActivityNew.getQualityPreferenceKey(), "90");
		int image_quality = 0;
		try {
			image_quality = Integer.parseInt(image_quality_s);
		} catch (NumberFormatException exception) {
			if (MyDebug.LOG)
				Log.e(TAG, "image_quality_s invalid format: " + image_quality_s);
			image_quality = 90;
		}
		return image_quality;
	}

	void onResume() {
		onResume(null);
	}

	void onResume(String toast_message) {
		if (MyDebug.LOG)
			Log.d(TAG, "onResume");
		this.app_is_paused = false;
		this.openCamera(toast_message);
	}

	void onPause() {
		if (MyDebug.LOG)
			Log.d(TAG, "onPause");
		this.app_is_paused = true;
		this.closeCamera();
	}

	void updateUIPlacement() {
		// we cache the preference_ui_placement to save having to check it in
		// the draw() method
		SharedPreferences sharedPreferences = PreferenceManager
				.getDefaultSharedPreferences(this.getContext());
		String ui_placement = sharedPreferences.getString(
				CameraActivityNew.getUIPlacementPreferenceKey(), "ui_right");
		this.ui_placement_right = ui_placement.equals("ui_right");
	}

	void onSaveInstanceState(Bundle state) {
		if (MyDebug.LOG)
			Log.d(TAG, "onSaveInstanceState");
		if (MyDebug.LOG)
			Log.d(TAG, "save cameraId: " + cameraId);
		state.putInt("cameraId", cameraId);
		if (MyDebug.LOG)
			Log.d(TAG, "save zoom_factor: " + zoom_factor);
		state.putInt("zoom_factor", zoom_factor);
	}

	public void showToast(final ToastBoxer clear_toast, final int message_id) {
		showToast(clear_toast, getResources().getString(message_id));
	}

	public void showToast(final ToastBoxer clear_toast, final String message) {
		showToast(clear_toast, message, Toast.LENGTH_SHORT);
	}

	public void showToast(final ToastBoxer clear_toast, final String message,
			final int duration) {
		class RotatedTextView extends View {
			private String[] lines = null;
			private Paint paint = new Paint();
			private Rect bounds = new Rect();
			private Rect sub_bounds = new Rect();
			private RectF rect = new RectF();

			public RotatedTextView(String text, Context context) {
				super(context);

				this.lines = text.split("\n");
			}

			@Override
			protected void onDraw(Canvas canvas) {
				final float scale = getResources().getDisplayMetrics().density;
				paint.setTextSize(14 * scale + 0.5f); // convert dps to pixels
				paint.setShadowLayer(1, 0, 1, Color.BLACK);
				// paint.getTextBounds(text, 0, text.length(), bounds);
				boolean first_line = true;
				for (String line : lines) {
					paint.getTextBounds(line, 0, line.length(), sub_bounds);
					/*
					 * if( MyDebug.LOG ) { Log.d(TAG, "line: " + line +
					 * " sub_bounds: " + sub_bounds); }
					 */
					if (first_line) {
						bounds.set(sub_bounds);
						first_line = false;
					} else {
						bounds.top = Math.min(sub_bounds.top, bounds.top);
						bounds.bottom = Math.max(sub_bounds.bottom,
								bounds.bottom);
						bounds.left = Math.min(sub_bounds.left, bounds.left);
						bounds.right = Math.max(sub_bounds.right, bounds.right);
					}
				}
				/*
				 * if( MyDebug.LOG ) { Log.d(TAG, "bounds: " + bounds); }
				 */
				int height = bounds.bottom - bounds.top + 2;
				bounds.bottom += ((lines.length - 1) * height) / 2;
				bounds.top -= ((lines.length - 1) * height) / 2;
				final int padding = (int) (14 * scale + 0.5f); // convert dps to
																// pixels
				final int offset_y = (int) (32 * scale + 0.5f); // convert dps
																// to pixels
				canvas.save();
				canvas.rotate(ui_rotation, canvas.getWidth() / 2,
						canvas.getHeight() / 2);

				rect.left = canvas.getWidth() / 2 - bounds.width() / 2
						+ bounds.left - padding;
				rect.top = canvas.getHeight() / 2 + bounds.top - padding
						+ offset_y;
				rect.right = canvas.getWidth() / 2 - bounds.width() / 2
						+ bounds.right + padding;
				rect.bottom = canvas.getHeight() / 2 + bounds.bottom + padding
						+ offset_y;

				paint.setStyle(Paint.Style.FILL);
				paint.setColor(Color.rgb(50, 50, 50));
				// canvas.drawRect(rect, paint);
				final float radius = (24 * scale + 0.5f); // convert dps to
															// pixels
				canvas.drawRoundRect(rect, radius, radius, paint);

				paint.setColor(Color.WHITE);
				int ypos = canvas.getHeight() / 2 + offset_y
						- ((lines.length - 1) * height) / 2;
				for (String line : lines) {
					canvas.drawText(line,
							canvas.getWidth() / 2 - bounds.width() / 2, ypos,
							paint);
					ypos += height;
				}
				canvas.restore();
			}
		}

		if (MyDebug.LOG)
			Log.d(TAG, "showToast: " + message);
		final Activity activity = (Activity) this.getContext();
		// We get a crash on emulator at least if Toast constructor isn't run on
		// main thread (e.g., the toast for taking a photo when on timer).
		// Also see
		// http://stackoverflow.com/questions/13267239/toast-from-a-non-ui-thread
		activity.runOnUiThread(new Runnable() {
			public void run() {
				if (clear_toast != null && clear_toast.toast != null)
					clear_toast.toast.cancel();
				/*
				 * clear_toast =
				 * Toast.makeText(activity.getApplicationContext(), message,
				 * Toast.LENGTH_SHORT); clear_toast.show();
				 */

				Toast toast = new Toast(activity);
				if (clear_toast != null)
					clear_toast.toast = toast;
				View text = new RotatedTextView(message, activity);
				toast.setView(text);
				toast.setDuration(duration);
				toast.show();
			}
		});
	}

	void setUIRotation(int ui_rotation) {
		if (MyDebug.LOG)
			Log.d(TAG, "setUIRotation");
		this.ui_rotation = ui_rotation;
	}

	void locationChanged(Location location) {
		if (MyDebug.LOG)
			Log.d(TAG, "locationChanged");
		this.test_has_received_location = true;
		SharedPreferences sharedPreferences = PreferenceManager
				.getDefaultSharedPreferences(this.getContext());
		boolean store_location = sharedPreferences.getBoolean(
				CameraActivityNew.getLocationPreferenceKey(), false);
		if (store_location) {
			this.location = location;
			// Android camera source claims we need to check lat/long != 0.0d
			if (location != null
					&& (location.getLatitude() != 0.0d || location
							.getLongitude() != 0.0d)) {
				if (MyDebug.LOG) {
					Log.d(TAG, "received location:");
					Log.d(TAG,
							"lat " + location.getLatitude() + " long "
									+ location.getLongitude() + " accuracy "
									+ location.getAccuracy());
				}
				this.has_set_location = true;
				this.location_accuracy = location.getAccuracy();
			}
		}
	}

	void resetLocation() {
		this.has_set_location = false;
		this.test_has_received_location = false;
	}

	private void updateParametersFromLocation() {
		if (camera_controller != null) {
			SharedPreferences sharedPreferences = PreferenceManager
					.getDefaultSharedPreferences(this.getContext());
			boolean store_location = sharedPreferences.getBoolean(
					CameraActivityNew.getLocationPreferenceKey(), false);
			// Android camera source claims we need to check lat/long != 0.0d
			if (store_location
					&& location != null
					&& (location.getLatitude() != 0.0d || location
							.getLongitude() != 0.0d)) {
				if (MyDebug.LOG) {
					Log.d(TAG, "updating parameters from location...");
					Log.d(TAG,
							"lat " + location.getLatitude() + " long "
									+ location.getLongitude() + " accuracy "
									+ location.getAccuracy());
				}
				camera_controller.setLocationInfo(location);
			} else {
				if (MyDebug.LOG)
					Log.d(TAG, "removing location data from parameters...");
				camera_controller.removeLocationInfo();
				this.has_set_location = false;
				test_has_received_location = false;
			}
		}
	}

	public boolean isVideo() {
		return is_video;
	}

	public boolean isTakingPhoto() {
		return this.phase == PHASE_TAKING_PHOTO;
	}

	// for testing:
	public CameraController getCameraController() {
		return this.camera_controller;
	}

	public CameraControllerManager getCameraControllerManager() {
		return this.camera_controller_manager;
	}

	public boolean supportsFocus() {
		return this.supported_focus_values != null;
	}

	public boolean supportsFlash() {
		return this.supported_flash_values != null;
	}

	public boolean supportsExposureLock() {
		return this.is_exposure_lock_supported;
	}

	public boolean supportsZoom() {
		return this.has_zoom;
	}

	public int getMaxZoom() {
		return this.max_zoom_factor;
	}

	public boolean hasFocusArea() {
		return this.has_focus_area;
	}

	public int getMaxNumFocusAreas() {
		return this.max_num_focus_areas;
	}

	public boolean isTakingPhotoOrOnTimer() {
		// return this.is_taking_photo;
		return this.phase == PHASE_TAKING_PHOTO || this.phase == PHASE_TIMER;
	}

	public boolean isOnTimer() {
		// return this.is_taking_photo_on_timer;
		return this.phase == PHASE_TIMER;
	}

	public boolean isPreviewStarted() {
		return this.is_preview_started;
	}

	public boolean hasSetLocation() {
		return this.has_set_location;
	}

	public int getDisplayOrientation() {
		return camera_controller == null ? 0 : camera_controller
				.getDisplayOrientation();
	}

	public boolean isFocusWaiting() {
		return focus_success == FOCUS_WAITING;
	}
}
