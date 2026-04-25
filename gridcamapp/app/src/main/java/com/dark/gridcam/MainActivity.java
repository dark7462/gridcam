package com.dark.gridcam;

import android.Manifest;
import android.content.pm.PackageManager;
import android.graphics.Bitmap;
import android.os.Bundle;
import android.util.Log;
import android.widget.Toast;

import androidx.annotation.NonNull;
import androidx.appcompat.app.AppCompatActivity;
import androidx.camera.core.CameraSelector;
import androidx.camera.core.ImageAnalysis;
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
import java.util.concurrent.ExecutionException;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;

public class MainActivity extends AppCompatActivity {

    private static final String TAG = "GridCam";
    private static final int REQUEST_CODE_PERMISSIONS = 10;
    private static final String[] REQUIRED_PERMISSIONS = new String[]{Manifest.permission.CAMERA};

    private ActivityMainBinding binding;
    private Interpreter tflite;
    private ExecutorService cameraExecutor;
    private long lastAnalysisTime = 0;

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
    }

    private void startCamera() {
        ListenableFuture<ProcessCameraProvider> cameraProviderFuture = ProcessCameraProvider.getInstance(this);

        cameraProviderFuture.addListener(() -> {
            try {
                ProcessCameraProvider cameraProvider = cameraProviderFuture.get();

                Preview preview = new Preview.Builder().build();
                preview.setSurfaceProvider(binding.previewView.getSurfaceProvider());

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
                cameraProvider.bindToLifecycle(this, cameraSelector, preview, imageAnalysis);

            } catch (ExecutionException | InterruptedException e) {
                Log.e(TAG, "Use case binding failed", e);
            }
        }, ContextCompat.getMainExecutor(this));
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
