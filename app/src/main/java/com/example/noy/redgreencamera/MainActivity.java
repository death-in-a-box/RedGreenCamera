/*
 *  UVCCamera
 *  library and sample to access to UVC web camera on non-rooted Android device
 *
 * Copyright (c) 2014-2017 saki t_saki@serenegiant.com
 *
 *  Licensed under the Apache License, Version 2.0 (the "License");
 *  you may not use this file except in compliance with the License.
 *   You may obtain a copy of the License at
 *
 *      http://www.apache.org/licenses/LICENSE-2.0
 *
 *   Unless required by applicable law or agreed to in writing, software
 *   distributed under the License is distributed on an "AS IS" BASIS,
 *   WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 *   See the License for the specific language governing permissions and
 *   limitations under the License.
 *
 *  All files in the folder are under this Apache License, Version 2.0.
 *  Files in the libjpeg-turbo, libusb, libuvc, rapidjson folder
 *  may have a different license, see the respective files.
 */

package com.example.noy.redgreencamera;

import java.io.File;
import java.io.FileNotFoundException;
import java.io.FileOutputStream;
import java.io.IOException;
import java.nio.ByteBuffer;
import java.text.SimpleDateFormat;
import java.util.Date;

import android.graphics.Bitmap;
import android.graphics.Camera;
import android.graphics.Canvas;
import android.graphics.Matrix;
import android.graphics.Paint;
import android.graphics.PaintFlagsDrawFilter;
import android.graphics.SurfaceTexture;
import android.hardware.usb.UsbDevice;
import android.os.Bundle;
import android.os.Environment;
import android.util.Log;
import android.view.Surface;
import android.view.SurfaceHolder;
import android.view.SurfaceView;
import android.view.TextureView;
import android.view.View;
import android.view.View.OnClickListener;
import android.view.View.OnLongClickListener;
import android.widget.CompoundButton;
import android.widget.ImageButton;
import android.widget.ImageView;
import android.widget.Toast;
import android.widget.ToggleButton;
import org.opencv.android.*;
import org.opencv.core.Mat;
import android.view.Surface;
import android.view.SurfaceHolder;
import android.view.SurfaceView;

import com.serenegiant.common.BaseActivity;
import com.serenegiant.encoder.MediaMuxerWrapper;

import com.serenegiant.opencv.ImageProcessor;
import com.serenegiant.usb.CameraDialog;
import com.serenegiant.usb.USBMonitor;
import com.serenegiant.usb.USBMonitor.OnDeviceConnectListener;
import com.serenegiant.usb.USBMonitor.UsbControlBlock;
import com.serenegiant.usb.UVCCamera;
import com.serenegiant.usbcameracommon.UVCCameraHandlerMultiSurface;
import com.serenegiant.widget.CameraViewInterface;
import com.serenegiant.widget.UVCCameraTextureView;

import static android.provider.MediaStore.Files.FileColumns.MEDIA_TYPE_IMAGE;

public final class MainActivity extends BaseActivity implements CameraDialog.CameraDialogParent {
	private static final boolean DEBUG = true;	// TODO set false on release
	private static final String TAG = "MainActivity";


	protected SurfaceView mResultViewL;
	protected SurfaceView mResultViewR;
	/**
	 * preview resolution(width)
	 * if your camera does not support specific resolution and mode,
	 * {@link UVCCamera#setPreviewSize(int, int, int)} throw exception
	 */
	private static final int PREVIEW_WIDTH = 640;
	/**
	 * preview resolution(height)
	 * if your camera does not support specific resolution and mode,
	 * {@link UVCCamera#setPreviewSize(int, int, int)} throw exception
	 */
	private static final int PREVIEW_HEIGHT = 480;

	private final Object mSync = new Object();
	/**
	 * for accessing USB
	 */
	private USBMonitor mUSBMonitor;
	/**
	 * Handler to execute camera releated methods sequentially on private thread
	 */
	private UVCCameraHandlerMultiSurface mCameraHandler;
	/**
	 * for camera preview display
	 */
	private CameraViewInterface mUVCCameraViewL;
	private CameraViewInterface mUVCCameraViewR;
	/**
	 * for camera preview display
	 */
	private UVCCameraTextureView mUVCCameraView;
	/**
	 * for open&start / stop&close camera preview
	 */
	private ToggleButton mCameraButton;
	/**
	 * button for start/stop recording
	 */
	private ImageButton mCaptureButton;
	protected ImageProcessor mImageProcessor;//TODO: move


	@Override
	protected void onCreate(final Bundle savedInstanceState) {
		super.onCreate(savedInstanceState);
		if (DEBUG) Log.v(TAG, "onCreate:");
		setContentView(R.layout.activity_main);
		mCameraButton = (ToggleButton)findViewById(R.id.camera_button);
		mCameraButton.setOnCheckedChangeListener(mOnCheckedChangeListener);
		mCaptureButton = (ImageButton)findViewById(R.id.capture_button);
		mCaptureButton.setOnClickListener(mOnClickListener);
		mCaptureButton.setVisibility(View.INVISIBLE);
		mResultViewL = (SurfaceView)findViewById(R.id.result_view_L);
		mResultViewR = (SurfaceView) findViewById(R.id.result_view_R);
		mUVCCameraViewL = (CameraViewInterface)findViewById(R.id.camera_view_L);


		mUVCCameraView = (UVCCameraTextureView)findViewById(R.id.camera_view_R);
		mUVCCameraView.setOnLongClickListener(mOnLongClickListener);
		mUVCCameraView.setAspectRatio(PREVIEW_WIDTH / (float)PREVIEW_HEIGHT);


		mUVCCameraViewL.setAspectRatio(UVCCamera.DEFAULT_PREVIEW_WIDTH / (float)UVCCamera.DEFAULT_PREVIEW_HEIGHT);
		mUVCCameraViewL.setCallback(mCallback);
		((View)mUVCCameraViewL).setOnLongClickListener(mOnLongClickListener);

		mUVCCameraViewR = (CameraViewInterface)findViewById(R.id.camera_view_R);
		mUVCCameraViewR.setAspectRatio(UVCCamera.DEFAULT_PREVIEW_WIDTH / (float)UVCCamera.DEFAULT_PREVIEW_HEIGHT);
		mUVCCameraViewR.setCallback(mCallback);
		((View)mUVCCameraViewL).setOnLongClickListener(mOnLongClickListener);

		synchronized (mSync) {
			mUSBMonitor = new USBMonitor(this, mOnDeviceConnectListener);
			mCameraHandler = UVCCameraHandlerMultiSurface.createHandler(this, mUVCCameraViewL, 1,
					UVCCamera.DEFAULT_PREVIEW_WIDTH, UVCCamera.DEFAULT_PREVIEW_HEIGHT);
		}
	}

	@Override
	protected void onStart() {
		super.onStart();
		if (DEBUG) Log.v(TAG, "onStart:");
		synchronized (mSync) {
			mUSBMonitor.register();
		}
		if (mUVCCameraViewL != null) {
			mUVCCameraViewL.onResume();
		}
		if (mUVCCameraViewR != null) {
			mUVCCameraViewR.onResume();
		}
	}

	@Override
	protected void onStop() {
		if (DEBUG) Log.v(TAG, "onStop:");
		synchronized (mSync) {
//			mCameraHandler.stopRecording();
//			mCameraHandler.stopPreview();
			mCameraHandler.close();	// #close include #stopRecording and #stopPreview
			mUSBMonitor.unregister();
		}
		if (mUVCCameraViewL != null) {
			mUVCCameraViewL.onPause();
		}
		if (mUVCCameraViewR != null) {
			mUVCCameraViewR.onPause();
		}
		setCameraButton(false);
		super.onStop();
	}

	@Override
	public void onDestroy() {
		if (DEBUG) Log.v(TAG, "onDestroy:");
		synchronized (mSync) {
			if (mCameraHandler != null) {
				mCameraHandler.release();
				mCameraHandler = null;
			}
			if (mUSBMonitor != null) {
				mUSBMonitor.destroy();
				mUSBMonitor = null;
			}
		}
		mUVCCameraViewL = null;
		mUVCCameraViewR = null;
		mCameraButton = null;
		mCaptureButton = null;
		super.onDestroy();
	}

	/**
	 * event handler when click camera / capture button
	 */
	private final OnClickListener mOnClickListener = new OnClickListener() {
		@Override
		public void onClick(final View view) {
			switch (view.getId()) {
				case R.id.capture_button:
					synchronized (mSync) {
						if ((mCameraHandler != null) && mCameraHandler.isOpened()) {
							if (checkPermissionWriteExternalStorage() && checkPermissionAudio()) {
								if (!mCameraHandler.isRecording()) {
									mCaptureButton.setColorFilter(0xffff0000);	// turn red
									mCameraHandler.startRecording();
								} else {
									mCaptureButton.setColorFilter(0);	// return to default color
									mCameraHandler.stopRecording();
								}
							}
						}
					}
					break;
			}
		}
	};

	private final CompoundButton.OnCheckedChangeListener mOnCheckedChangeListener
			= new CompoundButton.OnCheckedChangeListener() {
		@Override
		public void onCheckedChanged(final CompoundButton compoundButton, final boolean isChecked) {
			switch (compoundButton.getId()) {
				case R.id.camera_button:
					synchronized (mSync) {
						if (isChecked && (mCameraHandler != null) && !mCameraHandler.isOpened()) {
							CameraDialog.showDialog(MainActivity.this);
						} else {
							mCameraHandler.close();
							setCameraButton(false);
							stopPreview();
						}
					}
					break;
			}
		}
	};

	private void stopPreview() {
		if (DEBUG) Log.v(TAG, "stopPreview:");
		stopImageProcessor();
		if (mPreviewSurfaceId != 0) {
			mCameraHandler.removeSurface(mPreviewSurfaceId);
			mPreviewSurfaceId = 0;
		}
		mCameraHandler.close();
		setCameraButton(false);
	}


	/**
	 * capture still image when you long click on preview image(not on buttons)
	 */
	private final OnLongClickListener mOnLongClickListener = new OnLongClickListener() {
		@Override
		public boolean onLongClick(final View view) {
			switch (view.getId()) {
				case R.id.camera_view_L:
				case R.id.camera_view_R:
					synchronized (mSync) {
						if ((mCameraHandler != null) && mCameraHandler.isOpened()) {
							if (checkPermissionWriteExternalStorage()) {
								final File outputFile = MediaMuxerWrapper.getCaptureFile(Environment.DIRECTORY_DCIM, ".png");
								mCameraHandler.captureStill(outputFile.toString());
							}
							return true;
						}
					}
			}
			return false;
		}
	};

	private void setCameraButton(final boolean isOn) {
		if (DEBUG) Log.v(TAG, "setCameraButton:isOn=" + isOn);

		runOnUiThread(new Runnable() {
			@Override
			public void run() {
				if (mCameraButton != null) {
					try {
						mCameraButton.setOnCheckedChangeListener(null);
						mCameraButton.setChecked(isOn);
					} finally {
						mCameraButton.setOnCheckedChangeListener(mOnCheckedChangeListener);
					}
				}
				if (!isOn && (mCaptureButton != null)) {
					mCaptureButton.setVisibility(View.INVISIBLE);
				}
			}
		}, 0);
	}

	private int mPreviewSurfaceId;

	private void startPreview() {
		synchronized (mSync) {
			if (mCameraHandler != null) {
				mCameraHandler.startPreview();
			}
		}
		runOnUiThread(new Runnable() {
			@Override
			public void run() {
				try {
					final SurfaceTexture st = mUVCCameraView.getSurfaceTexture();
					if (st != null) {
						final Surface surface = new Surface(st);
						mPreviewSurfaceId = surface.hashCode();
						mCameraHandler.addSurface(mPreviewSurfaceId, surface, false);
					}
					mCaptureButton.setVisibility(View.VISIBLE);
					startImageProcessor(PREVIEW_WIDTH, PREVIEW_HEIGHT);
				} catch (final Exception e) {
					Log.w(TAG, e);
				}
			}
		});

	}

	private volatile boolean mIsRunning;
	private int mImageProcessorSurfaceId;

	/**
	 * start image processing
	 * @param processing_width
	 * @param processing_height
	 */
	protected void startImageProcessor(final int processing_width, final int processing_height) {
		if (DEBUG) Log.v(TAG, "startImageProcessor:");
		mIsRunning = true;
		if (mImageProcessor == null) {
			mImageProcessor = new ImageProcessor(processing_width, processing_height,	// src size
					new MyImageProcessorCallback(processing_width, processing_height));	// processing size
			mImageProcessor.start(processing_width, processing_height);	// processing size
			final Surface surface = mImageProcessor.getSurface();
			mImageProcessorSurfaceId = surface != null ? surface.hashCode() : 0;
			if (mImageProcessorSurfaceId != 0) {
				mCameraHandler.addSurface(mImageProcessorSurfaceId, surface, false);
			}
		}
	}

	/**
	 * stop image processing
	 */
	protected void stopImageProcessor() {
		if (DEBUG) Log.v(TAG, "stopImageProcessor:");
		if (mImageProcessorSurfaceId != 0) {
			mCameraHandler.removeSurface(mImageProcessorSurfaceId);
			mImageProcessorSurfaceId = 0;
		}
		if (mImageProcessor != null) {
			mImageProcessor.release();
			mImageProcessor = null;
		}
	}


	private final OnDeviceConnectListener mOnDeviceConnectListener = new OnDeviceConnectListener() {
		@Override
		public void onAttach(final UsbDevice device) {
			Toast.makeText(MainActivity.this, "USB_DEVICE_ATTACHED", Toast.LENGTH_SHORT).show();
		}

		@Override
		public void onConnect(final UsbDevice device, final UsbControlBlock ctrlBlock, final boolean createNew) {
			if (DEBUG) Log.v(TAG, "onConnect:");
			synchronized (mSync) {
				if (mCameraHandler != null) {
					mCameraHandler.open(ctrlBlock);
					startPreview();
				}
			}
		}

		@Override
		public void onDisconnect(final UsbDevice device, final UsbControlBlock ctrlBlock) {
			if (DEBUG) Log.v(TAG, "onDisconnect:");
			synchronized (mSync) {
				if (mCameraHandler != null) {
					queueEvent(new Runnable() {
						@Override
						public void run() {
							synchronized (mSync) {
								if (mCameraHandler != null) {
									mCameraHandler.close();
								}
							}
						}
					}, 0);
				}
			}
			setCameraButton(false);
		}

		@Override
		public void onDettach(final UsbDevice device) {
			Toast.makeText(MainActivity.this, "USB_DEVICE_DETACHED", Toast.LENGTH_SHORT).show();
		}

		@Override
		public void onCancel(final UsbDevice device) {
			setCameraButton(false);
		}
	};

	/**
	 * to access from CameraDialog
	 * @return
	 */
	@Override
	public USBMonitor getUSBMonitor() {
		synchronized (mSync) {
			return mUSBMonitor;
		}
	}

	@Override
	public void onDialogResult(boolean canceled) {
		if (canceled) {
			setCameraButton(false);
		}
	}

	private final CameraViewInterface.Callback
			mCallback = new CameraViewInterface.Callback() {
		@Override
		public void onSurfaceCreated(final CameraViewInterface view, final Surface surface) {
			mCameraHandler.addSurface(surface.hashCode(), surface, false);
		}

		@Override
		public void onSurfaceChanged(final CameraViewInterface view, final Surface surface, final int width, final int height) {

		}

		@Override
		public void onSurfaceDestroy(final CameraViewInterface view, final Surface surface) {
			synchronized (mSync) {
				if (mCameraHandler != null) {
					mCameraHandler.removeSurface(surface.hashCode());
				}
			}
		}
	};

	protected class MyImageProcessorCallback implements ImageProcessor.ImageProcessorCallback {
		private final int width, height;
		private final Matrix matrix = new Matrix();
		private Bitmap mFrame;
		private Mat mMat = new Mat();

		protected MyImageProcessorCallback(final int processing_width, final int processing_height) {
			width = processing_width;
			height = processing_height;
		}


		@Override
		public void onFrame(final ByteBuffer frame) {

			if ((mResultViewR != null) || (mResultViewL != null)) {
				final SurfaceHolder holder1 = mResultViewL.getHolder();
				final SurfaceHolder holder2 = mResultViewR.getHolder();
				if ((holder1 == null)
						|| (holder1.getSurface() == null)
						|| (frame == null)) return;
				if ((holder2 == null)
						|| (holder2.getSurface() == null))
						 return;
//--------------------------------------------------------------------------------
// Using SurfaceView and Bitmap to draw resulted images is inefficient way,
// but functions onOpenCV are relatively heavy and expect slower than source
// frame rate. So currently just use the way to simply this sample app.
// If you want to use much efficient way, try to use as same way as
// UVCCamera class use to receive images from UVC camera.
//--------------------------------------------------------------------------------
				if (mFrame == null) {
					mFrame = Bitmap.createBitmap(width, height, Bitmap.Config.ARGB_8888);
					final float scaleX_r = mResultViewR.getWidth() / (float)width;
					final float scaleY_r = mResultViewR.getHeight() / (float)height;
					final float scaleX_l = mResultViewL.getWidth() / (float)width;
					final float scaleY_l = mResultViewL.getHeight() / (float)height;
					matrix.reset();
					matrix.postScale(scaleX_r, scaleY_r);
				}
				try {
					frame.clear();
					mFrame.copyPixelsFromBuffer(frame.position(0));
					storeImage(mFrame);
					Canvas canvas = new Canvas(mFrame);
					canvas = holder1.lockCanvas();
					if (canvas != null) {
						try {
							//xxx();
							canvas.drawBitmap(mFrame, matrix,new Paint(Paint.FILTER_BITMAP_FLAG) );
							canvas.setDrawFilter(new PaintFlagsDrawFilter(Paint.DITHER_FLAG,Paint.DITHER_FLAG));
						} catch (final Exception e) {
							Log.w(TAG, e);
						} finally {
							holder1.unlockCanvasAndPost(canvas);
						}
					}
				} catch (final Exception e) {
					Log.w(TAG, e);
				}
			}
		}

		@Override
		public void onResult(final int type, final float[] result) {
			// do something
		}

		private void xxx(){

		}

		private void storeImage(Bitmap image) {
			File pictureFile = getOutputMediaFile(MEDIA_TYPE_IMAGE);
			if (pictureFile == null) {
				Log.d(TAG,
						"Error creating media file, check storage permissions: ");// e.getMessage());
				return;
			}
			try {
				FileOutputStream fos = new FileOutputStream(pictureFile);
				image.compress(Bitmap.CompressFormat.PNG, 90, fos);
				fos.close();
			} catch (FileNotFoundException e) {
				Log.d(TAG, "File not found: " + e.getMessage());
			} catch (IOException e) {
				Log.d(TAG, "Error accessing file: " + e.getMessage());
			}
		}

		public static final int MEDIA_TYPE_IMAGE = 1;
		public static final int MEDIA_TYPE_VIDEO = 2;

		/** Create a File for saving an image or video */
		private File getOutputMediaFile(int type){
			// To be safe, you should check that the SDCard is mounted
			// using Environment.getExternalStorageState() before doing this.

			File mediaStorageDir = new File(Environment.getExternalStoragePublicDirectory(
					Environment.DIRECTORY_PICTURES), "MyCameraApp");
			// This location works best if you want the created images to be shared
			// between applications and persist after your app has been uninstalled.

			// Create the storage directory if it does not exist
			if (! mediaStorageDir.exists()){
				if (! mediaStorageDir.mkdirs()){
					Log.d("MyCameraApp", "failed to create directory");
					return null;
				}
			}

			// Create a media file name
			String timeStamp = new SimpleDateFormat("yyyyMMdd_HHmmss").format(new Date());
			File mediaFile;
			if (type == MEDIA_TYPE_IMAGE){
				mediaFile = new File(mediaStorageDir.getPath() + File.separator +
						"IMG_"+ timeStamp + ".jpg");
			} else if(type == MEDIA_TYPE_VIDEO) {
				mediaFile = new File(mediaStorageDir.getPath() + File.separator +
						"VID_"+ timeStamp + ".mp4");
			} else {
				return null;
			}

			return mediaFile;
		}

	}

}