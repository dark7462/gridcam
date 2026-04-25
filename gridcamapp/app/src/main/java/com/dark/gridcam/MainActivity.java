package com.dark.gridcam;

import android.Manifest;
import android.content.ContentUris;
import android.content.ContentValues;
import android.content.Intent;
import android.content.pm.PackageManager;
import android.database.Cursor;
import android.graphics.Bitmap;
import android.graphics.BitmapFactory;
import android.net.Uri;
import android.os.Build;
import android.os.Bundle;
import android.os.Handler;
import android.os.Looper;
import android.provider.MediaStore;
import android.util.Log;
import android.util.Size;
import android.view.MotionEvent;
import android.view.ScaleGestureDetector;
import android.view.View;
import android.view.animation.Animation;
import android.view.animation.AnimationUtils;
import android.widget.Toast;

import androidx.annotation.NonNull;
import androidx.appcompat.app.AppCompatActivity;
import androidx.camera.core.Camera;
import androidx.camera.core.CameraSelector;
import androidx.camera.core.ImageAnalysis;
import androidx.camera.core.ImageCapture;
import androidx.camera.core.ImageCaptureException;
import androidx.camera.core.ImageProxy;
import androidx.camera.core.Preview;
import androidx.camera.core.ZoomState;
import androidx.camera.lifecycle.ProcessCameraProvider;
import androidx.constraintlayout.widget.ConstraintLayout;
import androidx.core.app.ActivityCompat;
import androidx.core.content.ContextCompat;
import androidx.core.graphics.Insets;
import androidx.core.view.ViewCompat;
import androidx.core.view.WindowCompat;
import androidx.core.view.WindowInsetsCompat;
import androidx.core.view.WindowInsetsControllerCompat;

import com.dark.gridcam.databinding.ActivityMainBinding;
import com.google.common.util.concurrent.ListenableFuture;

import org.tensorflow.lite.DataType;
import org.tensorflow.lite.Interpreter;
import org.tensorflow.lite.support.common.FileUtil;
import org.tensorflow.lite.support.image.ImageProcessor;
import org.tensorflow.lite.support.image.TensorImage;
import org.tensorflow.lite.support.image.ops.ResizeOp;

import java.io.IOException;
import java.io.InputStream;
import java.nio.MappedByteBuffer;
import java.text.SimpleDateFormat;
import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import java.util.concurrent.ExecutionException;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;

public class MainActivity extends AppCompatActivity {

    private static final String TAG = "GridCam";
    private static final int REQUEST_CODE_PERMISSIONS = 10;
    private static final String FILENAME_FORMAT = "yyyy-MM-dd-HH-mm-ss-SSS";

    private static final String[] GRID_NAMES = {
            "Rule of Thirds", "Golden Spiral", "Leading Lines", "Symmetry"
    };

    // ── Camera ──────────────────────────────────────────────────────────────
    private ActivityMainBinding binding;
    private Camera camera;
    private ImageCapture imageCapture;
    private ProcessCameraProvider cameraProvider;
    private boolean isFrontCamera = false;
    private boolean isFlashOn = false;
    private float currentZoomRatio = 1.0f;
    private ScaleGestureDetector scaleGestureDetector;
    private Uri lastPhotoUri;

    // ── ML ───────────────────────────────────────────────────────────────────
    private Interpreter tflite;
    private ExecutorService cameraExecutor;
    private long lastAnalysisTime = 0;

    // ── UI helpers ────────────────────────────────────────────────────────────
    private Handler mainHandler;
    private Runnable hideLabelRunnable;

    // ── Permissions ───────────────────────────────────────────────────────────
    private String[] getRequiredPermissions() {
        List<String> perms = new ArrayList<>();
        perms.add(Manifest.permission.CAMERA);
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
            perms.add(Manifest.permission.READ_MEDIA_IMAGES);
        } else if (Build.VERSION.SDK_INT < Build.VERSION_CODES.Q) {
            perms.add(Manifest.permission.WRITE_EXTERNAL_STORAGE);
        }
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q
                && Build.VERSION.SDK_INT < Build.VERSION_CODES.TIRAMISU) {
            perms.add(Manifest.permission.READ_EXTERNAL_STORAGE);
        }
        return perms.toArray(new String[0]);
    }

    // ═════════════════════════════════════════════════════════════════════════
    // Lifecycle
    // ═════════════════════════════════════════════════════════════════════════

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        // Edge-to-edge before setContentView
        WindowCompat.setDecorFitsSystemWindows(getWindow(), false);
        super.onCreate(savedInstanceState);
        binding = ActivityMainBinding.inflate(getLayoutInflater());
        setContentView(binding.getRoot());

        mainHandler = new Handler(Looper.getMainLooper());

        enableImmersiveMode();
        setupNotchInsets();
        setupPinchToZoom();
        wireButtons();

        // Load TFLite model
        try {
            MappedByteBuffer tfliteModel = FileUtil.loadMappedFile(this, "grid_model.tflite");
            tflite = new Interpreter(tfliteModel);
        } catch (IOException e) {
            Log.e(TAG, "Error loading model", e);
            Toast.makeText(this, "Failed to load model", Toast.LENGTH_SHORT).show();
        }

        cameraExecutor = Executors.newSingleThreadExecutor();

        if (allPermissionsGranted()) {
            startCamera();
            loadLastPhotoThumbnail();
        } else {
            ActivityCompat.requestPermissions(this, getRequiredPermissions(), REQUEST_CODE_PERMISSIONS);
        }
    }

    @Override
    public void onWindowFocusChanged(boolean hasFocus) {
        super.onWindowFocusChanged(hasFocus);
        if (hasFocus) enableImmersiveMode();
    }

    @Override
    protected void onDestroy() {
        super.onDestroy();
        cameraExecutor.shutdown();
        if (tflite != null) tflite.close();
        if (hideLabelRunnable != null) mainHandler.removeCallbacks(hideLabelRunnable);
    }

    // ═════════════════════════════════════════════════════════════════════════
    // Setup helpers
    // ═════════════════════════════════════════════════════════════════════════

    private void enableImmersiveMode() {
        WindowInsetsControllerCompat controller =
                WindowCompat.getInsetsController(getWindow(), getWindow().getDecorView());
        if (controller != null) {
            controller.hide(WindowInsetsCompat.Type.systemBars());
            controller.setSystemBarsBehavior(
                    WindowInsetsControllerCompat.BEHAVIOR_SHOW_TRANSIENT_BARS_BY_SWIPE);
        }
    }

    private void setupNotchInsets() {
        ViewCompat.setOnApplyWindowInsetsListener(binding.getRoot(), (v, insets) -> {
            Insets cutout = insets.getInsets(WindowInsetsCompat.Type.displayCutout());
            Insets bars   = insets.getInsets(WindowInsetsCompat.Type.systemBars());
            int topPad = Math.max(cutout.top, bars.top);
            if (topPad > 0) {
                // Expand top bar to sit below the notch
                binding.topBarBg.post(() -> {
                    ConstraintLayout.LayoutParams lp =
                            (ConstraintLayout.LayoutParams) binding.topBarBg.getLayoutParams();
                    int dp56 = (int)(56 * getResources().getDisplayMetrics().density);
                    lp.height = topPad + dp56;
                    binding.topBarBg.setLayoutParams(lp);
                    binding.topBarBg.setPadding(0, topPad, 0, 0);
                });
            }
            return insets;
        });
    }

    private void setupPinchToZoom() {
        scaleGestureDetector = new ScaleGestureDetector(this,
                new ScaleGestureDetector.SimpleOnScaleGestureListener() {
                    @Override
                    public boolean onScale(ScaleGestureDetector detector) {
                        if (camera == null) return true;
                        currentZoomRatio *= detector.getScaleFactor();
                        ZoomState zoomState = camera.getCameraInfo().getZoomState().getValue();
                        if (zoomState != null) {
                            currentZoomRatio = Math.max(zoomState.getMinZoomRatio(),
                                    Math.min(currentZoomRatio, zoomState.getMaxZoomRatio()));
                        }
                        camera.getCameraControl().setZoomRatio(currentZoomRatio);
                        return true;
                    }
                });

        binding.previewView.setOnTouchListener((v, event) -> {
            scaleGestureDetector.onTouchEvent(event);
            return true;
        });
    }

    private void wireButtons() {
        // Shutter — scale-pulse animation + capture
        binding.captureButton.setOnTouchListener((v, event) -> {
            switch (event.getAction()) {
                case MotionEvent.ACTION_DOWN:
                    v.animate().scaleX(0.88f).scaleY(0.88f).setDuration(80).start();
                    break;
                case MotionEvent.ACTION_UP:
                    v.animate().scaleX(1f).scaleY(1f).setDuration(80).start();
                    takePhoto();
                    break;
                case MotionEvent.ACTION_CANCEL:
                    v.animate().scaleX(1f).scaleY(1f).setDuration(80).start();
                    break;
            }
            return true;
        });

        // Smart grid toggle
        binding.smartGridToggle.setOnCheckedChangeListener((buttonView, isChecked) -> {
            if (!isChecked) {
                binding.overlayImageView.setImageResource(0);
                updateConfidence(-1);
                if (hideLabelRunnable != null) mainHandler.removeCallbacks(hideLabelRunnable);
                binding.gridNameLabel.setVisibility(View.GONE);
            }
        });

        // Flip camera
        binding.flipCameraButton.setOnClickListener(v -> {
            v.animate().rotationBy(180f).setDuration(300).start();
            flipCamera();
        });

        // Flash toggle
        binding.flashButton.setOnClickListener(v -> cycleFlash());

        // Zoom pills
        binding.zoom1x.setOnClickListener(v -> setZoom(1));
        binding.zoom2x.setOnClickListener(v -> setZoom(2));

        // Thumbnail → open last photo directly (no app-chooser)
        binding.thumbnailView.setOnClickListener(v -> {
            if (lastPhotoUri != null) {
                Intent intent = new Intent(Intent.ACTION_VIEW);
                intent.setDataAndType(lastPhotoUri, "image/jpeg");
                intent.addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION);
                startActivity(intent);
            }
        });
    }

    // ═════════════════════════════════════════════════════════════════════════
    // Camera
    // ═════════════════════════════════════════════════════════════════════════

    private void startCamera() {
        ListenableFuture<ProcessCameraProvider> future =
                ProcessCameraProvider.getInstance(this);

        future.addListener(() -> {
            try {
                cameraProvider = future.get();

                Preview preview = new Preview.Builder().build();
                preview.setSurfaceProvider(binding.previewView.getSurfaceProvider());

                imageCapture = new ImageCapture.Builder()
                        .setCaptureMode(ImageCapture.CAPTURE_MODE_MINIMIZE_LATENCY)
                        .setFlashMode(isFlashOn
                                ? ImageCapture.FLASH_MODE_ON
                                : ImageCapture.FLASH_MODE_OFF)
                        .build();

                CameraSelector cameraSelector = isFrontCamera
                        ? CameraSelector.DEFAULT_FRONT_CAMERA
                        : CameraSelector.DEFAULT_BACK_CAMERA;

                // ImageAnalysis always active — AI works on both front and back camera
                ImageAnalysis imageAnalysis = new ImageAnalysis.Builder()
                        .setBackpressureStrategy(ImageAnalysis.STRATEGY_KEEP_ONLY_LATEST)
                        .build();

                imageAnalysis.setAnalyzer(cameraExecutor, image -> {
                    long now = System.currentTimeMillis();
                    if (binding.smartGridToggle.isChecked()
                            && (now - lastAnalysisTime >= 1500)) {
                        analyzeImage(image);
                        lastAnalysisTime = now;
                    } else {
                        image.close();
                    }
                });

                cameraProvider.unbindAll();
                camera = cameraProvider.bindToLifecycle(
                        this, cameraSelector, preview, imageCapture, imageAnalysis);

                // Reset zoom on camera switch
                currentZoomRatio = 1.0f;
                setZoom(1);

            } catch (ExecutionException | InterruptedException e) {
                Log.e(TAG, "Use case binding failed", e);
            }
        }, ContextCompat.getMainExecutor(this));
    }

    private void flipCamera() {
        isFrontCamera = !isFrontCamera;
        startCamera();
    }

    private void setZoom(int level) {
        if (camera == null) return;
        if (level == 1) {
            currentZoomRatio = 1.0f;
            camera.getCameraControl().setZoomRatio(1.0f);
            binding.zoom1x.setTextColor(getColor(R.color.accent_amber));
            binding.zoom2x.setTextColor(0xAAFFFFFF);
        } else {
            currentZoomRatio = 2.0f;
            camera.getCameraControl().setZoomRatio(2.0f);
            binding.zoom1x.setTextColor(0xAAFFFFFF);
            binding.zoom2x.setTextColor(getColor(R.color.accent_amber));
        }
    }

    private void cycleFlash() {
        isFlashOn = !isFlashOn;
        if (imageCapture != null) {
            imageCapture.setFlashMode(isFlashOn
                    ? ImageCapture.FLASH_MODE_ON
                    : ImageCapture.FLASH_MODE_OFF);
        }
        binding.flashButton.setImageResource(isFlashOn
                ? R.drawable.ic_flash_on
                : R.drawable.ic_flash_off);
    }

    // ═════════════════════════════════════════════════════════════════════════
    // Capture
    // ═════════════════════════════════════════════════════════════════════════

    private void takePhoto() {
        if (imageCapture == null) return;

        String name = new SimpleDateFormat(FILENAME_FORMAT, Locale.US)
                .format(System.currentTimeMillis());

        ContentValues cv = new ContentValues();
        cv.put(MediaStore.MediaColumns.DISPLAY_NAME, name);
        cv.put(MediaStore.MediaColumns.MIME_TYPE, "image/jpeg");
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
            cv.put(MediaStore.Images.Media.RELATIVE_PATH, "Pictures/GridCam");
        }

        ImageCapture.OutputFileOptions options =
                new ImageCapture.OutputFileOptions.Builder(
                        getContentResolver(),
                        MediaStore.Images.Media.EXTERNAL_CONTENT_URI, cv)
                        .build();

        imageCapture.takePicture(options, ContextCompat.getMainExecutor(this),
                new ImageCapture.OnImageSavedCallback() {
                    @Override
                    public void onImageSaved(@NonNull ImageCapture.OutputFileResults results) {
                        if (results.getSavedUri() != null) {
                            lastPhotoUri = results.getSavedUri();
                        }
                        Toast.makeText(MainActivity.this,
                                "Saved to gallery!", Toast.LENGTH_SHORT).show();
                        loadLastPhotoThumbnail();
                    }

                    @Override
                    public void onError(@NonNull ImageCaptureException e) {
                        Log.e(TAG, "Capture failed: " + e.getMessage(), e);
                        Toast.makeText(MainActivity.this,
                                "Capture failed", Toast.LENGTH_SHORT).show();
                    }
                });
    }

    // ═════════════════════════════════════════════════════════════════════════
    // Gallery thumbnail
    // ═════════════════════════════════════════════════════════════════════════

    private void loadLastPhotoThumbnail() {
        cameraExecutor.execute(() -> {
            String[] projection = {MediaStore.Images.Media._ID};
            String selection = null;
            String[] selectionArgs = null;

            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
                selection = MediaStore.Images.Media.RELATIVE_PATH + " LIKE ?";
                selectionArgs = new String[]{"Pictures/GridCam%"};
            }

            try (Cursor cursor = getContentResolver().query(
                    MediaStore.Images.Media.EXTERNAL_CONTENT_URI,
                    projection, selection, selectionArgs,
                    MediaStore.Images.Media.DATE_ADDED + " DESC")) {

                if (cursor != null && cursor.moveToFirst()) {
                    long id = cursor.getLong(
                            cursor.getColumnIndexOrThrow(MediaStore.Images.Media._ID));
                    Uri uri = ContentUris.withAppendedId(
                            MediaStore.Images.Media.EXTERNAL_CONTENT_URI, id);
                    lastPhotoUri = uri;

                    Bitmap thumb = null;
                    if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
                        try {
                            thumb = getContentResolver()
                                    .loadThumbnail(uri, new Size(128, 128), null);
                        } catch (IOException e) {
                            Log.w(TAG, "Thumbnail load failed", e);
                        }
                    } else {
                        try (InputStream in = getContentResolver().openInputStream(uri)) {
                            if (in != null) {
                                BitmapFactory.Options opts = new BitmapFactory.Options();
                                opts.inSampleSize = 4;
                                thumb = BitmapFactory.decodeStream(in, null, opts);
                            }
                        } catch (IOException e) {
                            Log.w(TAG, "Thumbnail load failed", e);
                        }
                    }

                    if (thumb != null) {
                        final Bitmap finalThumb = thumb;
                        runOnUiThread(() -> binding.thumbnailView.setImageBitmap(finalThumb));
                    }
                }
            }
        });
    }

    // ═════════════════════════════════════════════════════════════════════════
    // AI / Grid analysis
    // ═════════════════════════════════════════════════════════════════════════

    private void analyzeImage(ImageProxy imageProxy) {
        if (tflite == null) { imageProxy.close(); return; }

        runOnUiThread(() -> {
            Bitmap bitmap = binding.previewView.getBitmap();
            if (bitmap == null) { imageProxy.close(); return; }

            cameraExecutor.execute(() -> {
                try {
                    ImageProcessor processor = new ImageProcessor.Builder()
                            .add(new ResizeOp(224, 224, ResizeOp.ResizeMethod.BILINEAR))
                            .build();

                    TensorImage tensorImage = new TensorImage(DataType.FLOAT32);
                    tensorImage.load(bitmap);
                    tensorImage = processor.process(tensorImage);

                    float[][] output = new float[1][4];
                    tflite.run(tensorImage.getBuffer(), output);

                    float maxConf = -1;
                    int maxIdx = -1;
                    for (int i = 0; i < 4; i++) {
                        if (output[0][i] > maxConf) {
                            maxConf = output[0][i];
                            maxIdx = i;
                        }
                    }

                    final int finalIdx = maxIdx;
                    final float finalConf = maxConf;

                    runOnUiThread(() -> {
                        // Always show confidence (color-coded); grid only at >= 60%
                        updateConfidence(finalConf);
                        if (finalConf >= 0.60f) {
                            int[] grids = {
                                    R.drawable.grid_rule_of_thirds,
                                    R.drawable.grid_golden_spiral,
                                    R.drawable.grid_leading_lines,
                                    R.drawable.grid_symmetry
                            };
                            if (finalIdx >= 0 && finalIdx < grids.length) {
                                binding.overlayImageView.setImageResource(grids[finalIdx]);
                                showGridLabel(GRID_NAMES[finalIdx]);
                            }
                        } else {
                            binding.overlayImageView.setImageResource(0);
                        }
                    });
                } finally {
                    imageProxy.close();
                }
            });
        });
    }

    // ─── UI update helpers ────────────────────────────────────────────────────

    private void showGridLabel(String name) {
        binding.gridNameLabel.setText(name);
        binding.gridNameLabel.clearAnimation();
        binding.gridNameLabel.setVisibility(View.VISIBLE);

        Animation fadeIn = AnimationUtils.loadAnimation(this, R.anim.fade_in);
        binding.gridNameLabel.startAnimation(fadeIn);

        if (hideLabelRunnable != null) mainHandler.removeCallbacks(hideLabelRunnable);
        hideLabelRunnable = () -> {
            Animation fadeOut = AnimationUtils.loadAnimation(this, R.anim.fade_out);
            fadeOut.setAnimationListener(new Animation.AnimationListener() {
                @Override public void onAnimationStart(Animation a) {}
                @Override public void onAnimationRepeat(Animation a) {}
                @Override public void onAnimationEnd(Animation a) {
                    binding.gridNameLabel.setVisibility(View.GONE);
                }
            });
            binding.gridNameLabel.startAnimation(fadeOut);
        };
        mainHandler.postDelayed(hideLabelRunnable, 2500);
    }

    private void updateConfidence(float confidence) {
        if (confidence < 0) {
            binding.aiConfidenceText.setVisibility(View.GONE);
            return;
        }
        int pct = (int) (confidence * 100);
        binding.aiConfidenceText.setText("● " + pct + "%");
        binding.aiConfidenceText.setVisibility(View.VISIBLE);
        if (pct >= 75) {
            binding.aiConfidenceText.setTextColor(0xFF4CAF50); // green
        } else if (pct >= 40) {
            binding.aiConfidenceText.setTextColor(0xFFFFCA28); // yellow/amber
        } else {
            binding.aiConfidenceText.setTextColor(0xFFF44336); // red
        }
    }

    // ═════════════════════════════════════════════════════════════════════════
    // Permissions
    // ═════════════════════════════════════════════════════════════════════════

    private boolean allPermissionsGranted() {
        for (String p : getRequiredPermissions()) {
            if (ContextCompat.checkSelfPermission(this, p)
                    != PackageManager.PERMISSION_GRANTED) return false;
        }
        return true;
    }

    @Override
    public void onRequestPermissionsResult(int requestCode,
            @NonNull String[] permissions, @NonNull int[] grantResults) {
        super.onRequestPermissionsResult(requestCode, permissions, grantResults);
        if (requestCode == REQUEST_CODE_PERMISSIONS) {
            if (allPermissionsGranted()) {
                startCamera();
                loadLastPhotoThumbnail();
            } else {
                Toast.makeText(this, "Camera permission required.", Toast.LENGTH_SHORT).show();
                finish();
            }
        }
    }
}
