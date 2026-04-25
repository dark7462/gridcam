package com.dark.gridcam;

import android.Manifest;
import android.content.ContentValues;
import android.content.pm.PackageManager;
import android.graphics.Bitmap;
import android.os.Build;
import android.os.Bundle;
import android.provider.MediaStore;
import android.util.Log;
import android.widget.Toast;

import androidx.annotation.NonNull;
import androidx.appcompat.app.AppCompatActivity;
import androidx.camera.core.CameraSelector;
import androidx.camera.core.ImageAnalysis;
import androidx.camera.core.ImageCapture;
import androidx.camera.core.ImageCaptureException;
import androidx.camera.core.ImageProxy;
import androidx.camera.core.Preview;
import androidx.camera.lifecycle.ProcessCameraProvider;
import androidx.core.app.ActivityCompat;
import androidx.core.content.ContextCompat;

import com.dark.gridcam.databinding.ActivityMainBinding;
import com.google.common.util.concurrent.ListenableFuture;

import org.tensorflow.lite.DataType;
import org.tensorflow.lite.Interpreter;
import org.tensorflow.lite.support.common.FileUtil;
import org.tensorflow.lite.support.image.ImageProcessor;
import org.tensorflow.lite.support.image.TensorImage;
import org.tensorflow.lite.support.image.ops.ResizeOp;

import java.io.IOException;
import java.nio.MappedByteBuffer;
import java.text.SimpleDateFormat;
import java.util.Locale;
import java.util.concurrent.ExecutionException;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;

public class MainActivity extends AppCompatActivity {

    private static final String TAG = "GridCam";
    private static final int REQUEST_CODE_PERMISSIONS = 10;
    private static final String[] REQUIRED_PERMISSIONS;

    static {
        if (Build.VERSION.SDK_INT <= Build.VERSION_CODES.P) {
            REQUIRED_PERMISSIONS = new String[]{
                    Manifest.permission.CAMERA,
                    Manifest.permission.WRITE_EXTERNAL_STORAGE
            };
        } else {
            REQUIRED_PERMISSIONS = new String[]{Manifest.permission.CAMERA};
        }
    }

    private static final String FILENAME_FORMAT = "yyyy-MM-dd-HH-mm-ss-SSS";

    private ActivityMainBinding binding;
    private Interpreter tflite;
    private ExecutorService cameraExecutor;
    private long lastAnalysisTime = 0;
    private ImageCapture imageCapture;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        binding = ActivityMainBinding.inflate(getLayoutInflater());
        setContentView(binding.getRoot());

        if (allPermissionsGranted()) {
            startCamera();
        } else {
            ActivityCompat.requestPermissions(this, REQUIRED_PERMISSIONS, REQUEST_CODE_PERMISSIONS);
        }

        try {
            MappedByteBuffer tfliteModel = FileUtil.loadMappedFile(this, "grid_model.tflite");
            tflite = new Interpreter(tfliteModel);
        } catch (IOException e) {
            Log.e(TAG, "Error loading model", e);
            Toast.makeText(this, "Failed to load model", Toast.LENGTH_SHORT).show();
        }

        cameraExecutor = Executors.newSingleThreadExecutor();

        binding.smartGridToggle.setOnCheckedChangeListener((buttonView, isChecked) -> {
            if (!isChecked) {
                binding.overlayImageView.setImageResource(0);
            }
        });

        // Capture button click → save to gallery
        binding.captureButton.setOnClickListener(v -> takePhoto());
    }

    private void startCamera() {
        ListenableFuture<ProcessCameraProvider> cameraProviderFuture = ProcessCameraProvider.getInstance(this);

        cameraProviderFuture.addListener(() -> {
            try {
                ProcessCameraProvider cameraProvider = cameraProviderFuture.get();

                Preview preview = new Preview.Builder().build();
                preview.setSurfaceProvider(binding.previewView.getSurfaceProvider());

                // ImageCapture use case — same CameraX version (1.3.0), no extra dep needed
                imageCapture = new ImageCapture.Builder()
                        .setCaptureMode(ImageCapture.CAPTURE_MODE_MINIMIZE_LATENCY)
                        .build();

                ImageAnalysis imageAnalysis = new ImageAnalysis.Builder()
                        .setBackpressureStrategy(ImageAnalysis.STRATEGY_KEEP_ONLY_LATEST)
                        .build();

                imageAnalysis.setAnalyzer(cameraExecutor, image -> {
                    long currentTime = System.currentTimeMillis();
                    if (binding.smartGridToggle.isChecked() && (currentTime - lastAnalysisTime >= 1500)) {
                        analyzeImage(image);
                        lastAnalysisTime = currentTime;
                    } else {
                        image.close();
                    }
                });

                CameraSelector cameraSelector = CameraSelector.DEFAULT_BACK_CAMERA;

                cameraProvider.unbindAll();
                // Bind Preview + ImageCapture + ImageAnalysis together
                cameraProvider.bindToLifecycle(this, cameraSelector, preview, imageCapture, imageAnalysis);

            } catch (ExecutionException | InterruptedException e) {
                Log.e(TAG, "Use case binding failed", e);
            }
        }, ContextCompat.getMainExecutor(this));
    }

    private void takePhoto() {
        if (imageCapture == null) return;

        // Build MediaStore output options
        String name = new SimpleDateFormat(FILENAME_FORMAT, Locale.US)
                .format(System.currentTimeMillis());

        ContentValues contentValues = new ContentValues();
        contentValues.put(MediaStore.MediaColumns.DISPLAY_NAME, name);
        contentValues.put(MediaStore.MediaColumns.MIME_TYPE, "image/jpeg");
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
            contentValues.put(MediaStore.Images.Media.RELATIVE_PATH, "Pictures/GridCam");
        }

        ImageCapture.OutputFileOptions outputOptions = new ImageCapture.OutputFileOptions.Builder(
                getContentResolver(),
                MediaStore.Images.Media.EXTERNAL_CONTENT_URI,
                contentValues
        ).build();

        imageCapture.takePicture(
                outputOptions,
                ContextCompat.getMainExecutor(this),
                new ImageCapture.OnImageSavedCallback() {
                    @Override
                    public void onImageSaved(@NonNull ImageCapture.OutputFileResults outputFileResults) {
                        String msg = "Photo saved to gallery!";
                        Toast.makeText(MainActivity.this, msg, Toast.LENGTH_SHORT).show();
                        Log.d(TAG, msg);
                    }

                    @Override
                    public void onError(@NonNull ImageCaptureException exception) {
                        Log.e(TAG, "Photo capture failed: " + exception.getMessage(), exception);
                        Toast.makeText(MainActivity.this, "Capture failed: " + exception.getMessage(),
                                Toast.LENGTH_SHORT).show();
                    }
                }
        );
    }

    private void analyzeImage(ImageProxy imageProxy) {
        if (tflite == null) {
            imageProxy.close();
            return;
        }

        // Must get bitmap on main thread
        runOnUiThread(() -> {
            Bitmap bitmap = binding.previewView.getBitmap();
            if (bitmap == null) {
                imageProxy.close();
                return;
            }

            // Run inference on background thread
            cameraExecutor.execute(() -> {
                try {
                    ImageProcessor imageProcessor = new ImageProcessor.Builder()
                            .add(new ResizeOp(224, 224, ResizeOp.ResizeMethod.BILINEAR))
                            .build();

                    TensorImage tensorImage = new TensorImage(DataType.FLOAT32);
                    tensorImage.load(bitmap);
                    tensorImage = imageProcessor.process(tensorImage);

                    float[][] output = new float[1][4];
                    tflite.run(tensorImage.getBuffer(), output);

                    float maxConfidence = -1;
                    int maxIndex = -1;
                    for (int i = 0; i < 4; i++) {
                        if (output[0][i] > maxConfidence) {
                            maxConfidence = output[0][i];
                            maxIndex = i;
                        }
                    }

                    final int finalMaxIndex = maxIndex;
                    final float finalMaxConfidence = maxConfidence;

                    runOnUiThread(() -> {
                        if (finalMaxConfidence >= 0.60f) {
                            switch (finalMaxIndex) {
                                case 0:
                                    binding.overlayImageView.setImageResource(R.drawable.grid_rule_of_thirds);
                                    break;
                                case 1:
                                    binding.overlayImageView.setImageResource(R.drawable.grid_golden_spiral);
                                    break;
                                case 2:
                                    binding.overlayImageView.setImageResource(R.drawable.grid_leading_lines);
                                    break;
                                case 3:
                                    binding.overlayImageView.setImageResource(R.drawable.grid_symmetry);
                                    break;
                                default:
                                    binding.overlayImageView.setImageResource(0);
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

    private boolean allPermissionsGranted() {
        for (String permission : REQUIRED_PERMISSIONS) {
            if (ContextCompat.checkSelfPermission(this, permission) != PackageManager.PERMISSION_GRANTED) {
                return false;
            }
        }
        return true;
    }

    @Override
    public void onRequestPermissionsResult(int requestCode, @NonNull String[] permissions, @NonNull int[] grantResults) {
        super.onRequestPermissionsResult(requestCode, permissions, grantResults);
        if (requestCode == REQUEST_CODE_PERMISSIONS) {
            if (allPermissionsGranted()) {
                startCamera();
            } else {
                Toast.makeText(this, "Permissions not granted by the user.", Toast.LENGTH_SHORT).show();
                finish();
            }
        }
    }

    @Override
    protected void onDestroy() {
        super.onDestroy();
        cameraExecutor.shutdown();
        if (tflite != null) {
            tflite.close();
        }
    }
}
