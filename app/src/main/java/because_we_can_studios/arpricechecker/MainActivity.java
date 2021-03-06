package because_we_can_studios.arpricechecker;

import android.Manifest;
import android.content.Context;
import android.content.pm.PackageManager;
import android.hardware.Camera;
import android.os.Handler;
import android.support.annotation.NonNull;
import android.support.v4.app.ActivityCompat;
import android.support.v7.app.AppCompatActivity;
import android.os.Bundle;
import android.util.Log;
import android.view.MotionEvent;
import android.view.WindowManager;
import android.widget.Toast;

import com.google.android.gms.vision.MultiProcessor;
import com.google.android.gms.vision.barcode.Barcode;
import com.google.android.gms.vision.barcode.BarcodeDetector;

import java.io.IOException;

import static because_we_can_studios.arpricechecker.CameraSource.cameraFocus;

public class MainActivity extends AppCompatActivity {
    private static final String TAG = "MainActivity";

    private static final int RC_HANDLE_CAMERA_PERM = 2;

    private boolean isTorchOn = false;
    private boolean blocked = false;
    private Handler handler = new Handler();

    private CameraSource mCameraSource = null;
    private CameraSourcePreview mCameraSourcePreview;
    private GraphicOverlay mGraphicOverlay;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_main);
        mCameraSourcePreview = (CameraSourcePreview) findViewById(R.id.preview);
        mGraphicOverlay = (GraphicOverlay) findViewById(R.id.faceOverlay);
        if (ActivityCompat.checkSelfPermission(this, Manifest.permission.CAMERA) ==
                PackageManager.PERMISSION_GRANTED) {
            Log.d(TAG, "Permission is already granted");
            buildCameraSource();
        }
        else {
            Log.w(TAG, "Camera permission is not granted. Requesting permission");
            ActivityCompat.requestPermissions(this, new String[]{Manifest.permission.CAMERA},
                    RC_HANDLE_CAMERA_PERM);
        }
        getWindow().addFlags(WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON);
    }

    @Override
    public void onRequestPermissionsResult(int requestCode,
                                           @NonNull String[] permissions,
                                           @NonNull int[] grantResults) {
        if (requestCode != RC_HANDLE_CAMERA_PERM) {
            super.onRequestPermissionsResult(requestCode, permissions, grantResults);
            return;
        }
        if (grantResults.length != 0 && grantResults[0] == PackageManager.PERMISSION_GRANTED) {
            Log.d(TAG, "Camera permission granted - initialize the camera source");
            buildCameraSource();
            return;
        }
        Log.e(TAG, "Permission hasn't been granted");
    }

    private void buildCameraSource() {
        Log.d(TAG, "Building Camera");
        Context context = getApplicationContext();
        BarcodeDetector detector = new BarcodeDetector.Builder(context)
                .setBarcodeFormats(Barcode.QR_CODE)
                .build();
        PriceDatabase database = new PriceDatabase("Server address should go here");
        BarcodeTrackerFactory qrCodeFactory = new BarcodeTrackerFactory(mGraphicOverlay, database);
        detector.setProcessor(new MultiProcessor.Builder<>(qrCodeFactory).build());
        mCameraSource = new CameraSource.Builder(context, detector)
                .setFacing(CameraSource.CAMERA_FACING_BACK)
                .setRequestedPreviewSize(1920, 1080)
                .setRequestedFps(60.0f)
                .setFlashMode(isTorchOn ? Camera.Parameters.FLASH_MODE_TORCH : null)
                .build();
        try {
            mCameraSourcePreview.start(mCameraSource, mGraphicOverlay);
            cameraFocus(mCameraSource, Camera.Parameters.FOCUS_MODE_CONTINUOUS_VIDEO);
        } catch (IOException e) {
            Log.e(TAG, "Unable to start camera source.", e);
            mCameraSource.release();
            mCameraSource = null;
        }
    }

    @Override
    protected void onDestroy() {
        super.onDestroy();
        if (mCameraSource != null)
            mCameraSource.release();
    }

    @Override
    protected void onPause() {
        super.onPause();
        mCameraSourcePreview.stop();
    }

    @Override
    protected void onResume() {
        super.onResume();
        if (mCameraSource != null) {
            try {
                mCameraSourcePreview.start(mCameraSource, mGraphicOverlay);
            } catch (IOException e) {
                Log.e(TAG, "Unable to start camera source.", e);
                mCameraSource.release();
                mCameraSource = null;
            }
        } else
            Log.w(TAG, "Camera source was null");
    }

    @Override
    public boolean onTouchEvent(MotionEvent e) {
        if (e.getAction() == MotionEvent.ACTION_DOWN) {
            if (!blocked) {
                blocked = true;
                handler.postDelayed(new Runnable() {
                    @Override
                    public void run() {
                        blocked = false;
                        Boolean isFlashAvailable = getApplicationContext().getPackageManager()
                                .hasSystemFeature(PackageManager.FEATURE_CAMERA_FLASH);
                        if (!isFlashAvailable) {
                            Toast.makeText(MainActivity.this, "Your device doesn't support flashlight ( ͡° ͜ʖ ͡°)",Toast.LENGTH_SHORT).show();
                        }
                        isTorchOn = isTorchOn ? false : true;
                        mCameraSource.setFlashMode(isTorchOn ? Camera.Parameters.FLASH_MODE_TORCH : Camera.Parameters.FLASH_MODE_OFF);
                    }
                }, 100);
            } else {
                return false;
            }
        }
        return super.onTouchEvent(e);
    }

}
