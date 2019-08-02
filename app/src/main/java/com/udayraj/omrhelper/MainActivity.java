package com.udayraj.omrhelper;

import androidx.annotation.NonNull;
import androidx.appcompat.app.AppCompatActivity;

import android.content.pm.ActivityInfo;
import android.content.res.Resources;
import android.graphics.Bitmap;
import android.graphics.BitmapFactory;
import android.graphics.Color;
import android.graphics.Paint;
import android.graphics.Path;
import android.graphics.drawable.shapes.PathShape;
import android.os.Build;
import android.os.Bundle;
import android.os.CountDownTimer;
import android.os.Handler;
import android.transition.TransitionManager;
import android.util.Log;
import android.view.SurfaceView;
import android.view.View;
import android.view.ViewGroup;
import android.widget.Button;
import android.widget.FrameLayout;
import android.widget.LinearLayout;
import android.widget.TextView;
import android.widget.Toast;

import com.crashlytics.android.Crashlytics;
import com.google.firebase.FirebaseApp;
import com.google.firebase.analytics.FirebaseAnalytics;
import com.nightonke.jellytogglebutton.JellyToggleButton;
import com.nightonke.jellytogglebutton.State;
import com.rbddevs.splashy.Splashy;
import com.udayraj.omrhelper.constants.SC;
import com.udayraj.omrhelper.constants.ScanHint;
import com.udayraj.omrhelper.interfaces.IScanner;
import com.udayraj.omrhelper.util.FileUtils;
import com.udayraj.omrhelper.util.ImageDetectionProperties;
import com.udayraj.omrhelper.util.SimplePermissions;
import com.udayraj.omrhelper.util.Utils;
import com.udayraj.omrhelper.view.Quadrilateral;
import com.udayraj.omrhelper.view.ScanCanvasView;
import com.yarolegovich.lovelydialog.LovelyTextInputDialog;

import org.opencv.android.CameraBridgeViewBase;
import org.opencv.android.JavaCameraView;
import org.opencv.core.Mat;
import org.opencv.core.Point;
import org.opencv.core.Size;
import org.opencv.imgcodecs.Imgcodecs;
import org.opencv.imgproc.Imgproc;

import java.io.File;
import java.util.Arrays;
import java.util.List;

import static android.view.View.GONE;

public class MainActivity extends AppCompatActivity implements IScanner, CameraBridgeViewBase.CvCameraViewListener2 {

    //    // required for Imread etc: https://stackoverflow.com/questions/35090838/no-implementation-found-for-long-org-opencv-core-mat-n-mat-error-using-opencv
    private static final String mOpenCvLibrary = "opencv_java3";
    static {
        System.loadLibrary(mOpenCvLibrary);
//        System.loadLibrary(Core.NATIVE_LIBRARY_NAME);
    }

    private static final String TAG = "MainActivity";
    private Resources res;
    private Bitmap tempBitmap;
    private Mat outMat;
    private Mat saveOutMat;
    private int saveRows, saveCols;
    private Point[] savePoints;
    private ViewGroup containerScan;
    private FrameLayout acceptLayout;
    private LinearLayout captureHintLayout;
    private View cropAcceptBtn;
    private View cropRejectBtn;
    private TextView captureHintText;
    private TextView timeElapsedText;
    // private ImageView cropImageView;
    // private ScanCameraBridgeView mImageSurfaceView;
    private FrameLayout cameraPreviewLayout;
    CameraBridgeViewBase mOpenCvCameraView;
    SC configController;
    private SimplePermissions permHandler;
    private ScanCanvasView scanCanvasView;

    public boolean acceptLayoutShowing = false, checkMarkerBegun=false, canCheckMarker=false;

    private CountDownTimer autoCaptureTimer, checkMarkerTimer;
    private int secondsLeft;
    private boolean isCapturing = false;

    private Button storage_btn;
    private FirebaseAnalytics mFirebaseAnalytics;
    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setRequestedOrientation(ActivityInfo.SCREEN_ORIENTATION_PORTRAIT);

        new Splashy(this)
                .setLogo(R.drawable.splash_guide)
                .setLogoWHinDp(350,350) // default is 200x200
                .setTitle(R.string.app_name)
                // .setTitleColor("#FFFFFF")
                .setSubTitle("Simple. Fast. Open Source.")
                .setTitleSize(25f)
                .setSubTitleSize(20f)
                // .setProgressColor(R.color.black)
                .showProgress(true)
                .setFullScreen(true)
                .setTime(2000)
                .show();


        FirebaseApp.initializeApp(this);
        mFirebaseAnalytics = FirebaseAnalytics.getInstance(this);
//        TODO: verify this comes into the analytics portal
        new Handler().post(new Runnable() {
            @Override
            public void run() {
                Bundle bundle = new Bundle();
                bundle.putString(FirebaseAnalytics.Param.METHOD, "App Opened");
                mFirebaseAnalytics.logEvent(FirebaseAnalytics.Event.LOGIN, bundle);
            }
        });

        // Crashlytics.setUserIdentifier("user123456789");

//                Bundle params = new Bundle();
//                params.putString("image_name", "some name");
//                params.putString("full_path", "File path with counter");
//                mFirebaseAnalytics.logEvent("save_image", params);
//                Crashlytics.getInstance().crash();
//                return false;


        setContentView(R.layout.activity_main);

        res = getResources();
        configController = new SC(MainActivity.this);
        // outermost view = containerScan
        containerScan = findViewById(R.id.container_scan);
        cameraPreviewLayout = findViewById(R.id.camera_preview);
        mOpenCvCameraView = findViewById(R.id.java_camera_view);
        // Not much change in FPS! cool! : 1072x1072
        // to ensure highest frame in all phones TODO: Tell to check the toast
        mOpenCvCameraView.setMaxFrameSize(3000, 3000);
//        mOpenCvCameraView.enableFpsMeter();
        // custom implemented feature - https://stackoverflow.com/questions/16669779/opencv-camera-orientation-issue
        // mOpenCvCameraView.setUserRotation(90);
        captureHintLayout = findViewById(R.id.capture_hint_layout);
        timeElapsedText = findViewById(R.id.time_elapsed_text);
        captureHintText = findViewById(R.id.capture_hint_text);

        // Contains the accept/reject buttons -
        acceptLayout = findViewById(R.id.crop_layout);
        cropAcceptBtn = findViewById(R.id.crop_accept_btn);
        cropAcceptBtn.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                v.setEnabled(false);
                v.setClickable(false);
                Log.d("custom"+TAG, "Image Accepted.");
                // Done: check existing IMAGE_CTR there
                FileUtils.checkMakeDirs(SC.CURR_DIR);
                FileUtils.checkMakeDirs(SC.CURR_ORIG_DIR);
                SC.IMAGE_CTR = new File(SC.CURR_DIR).listFiles(SC.jpgFilter).length + 1;
                final String IMAGE_NAME = SC.IMAGE_PREFIX +SC.IMAGE_CTR+".jpg";
                Toast.makeText(MainActivity.this, "Saving to: " + IMAGE_NAME, Toast.LENGTH_SHORT).show();
                new Thread(new Runnable() {
                    @Override
                    public void run() {
                        Mat warpOriginal = Utils.four_point_transform_scaled(saveOutMat, saveCols, saveRows, savePoints);
                        tempBitmap = Utils.matToBitmapRotate(warpOriginal);
                        warpOriginal.release();
                        boolean success = FileUtils.saveBitmap(tempBitmap, SC.CURR_DIR, IMAGE_NAME);
                        tempBitmap = Utils.matToBitmapRotate(saveOutMat);
                        FileUtils.saveBitmap(tempBitmap, SC.CURR_ORIG_DIR, IMAGE_NAME);
                        Log.d("custom"+TAG, "Image Saved.");
                    }
                }).start();
                Log.d("custom"+TAG, "Save Thread started.");
                cancelAutoCapture();
                v.setEnabled(true);
                v.setClickable(true);
            }
        });

        cropRejectBtn = findViewById(R.id.crop_reject_btn);
        cropRejectBtn.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                cancelAutoCapture();
            }
        });
        for( int id : new int []{R.id.xray_btn,R.id.clahe_btn,R.id.gamma_btn,R.id.contour_btn} ){
            JellyToggleButton btn = findViewById(id);
            btn.setChecked(false);

            btn.setOnStateChangeListener(new JellyToggleButton.OnStateChangeListener() {
                @Override
                public void onStateChange(float process, State state, JellyToggleButton jtb) {
                    cancelAutoCapture();
                }
            });
        }
        for( int id : new int []{R.id.hover_btn,R.id.canny_btn,R.id.morph_btn} ){
            JellyToggleButton btn = findViewById(id);
            btn.setChecked(true);
            btn.setOnStateChangeListener(new JellyToggleButton.OnStateChangeListener() {
                @Override
                public void onStateChange(float process, State state, JellyToggleButton jtb) {
                    cancelAutoCapture();
                }
            });
        }

        // flash functionality
        JellyToggleButton flash = findViewById(R.id.flash_btn);
        flash.setOnStateChangeListener(new JellyToggleButton.OnStateChangeListener() {
            @Override
            public void onStateChange(float process, State state, JellyToggleButton jtb) {
                switch (state){
                    case LEFT:
                        ((JavaCameraView)mOpenCvCameraView).turnOffTheFlash();
                        break;
                    case RIGHT:
                        ((JavaCameraView)mOpenCvCameraView).turnOnTheFlash();
                        break;
                }
            }
        });


        storage_btn = findViewById(R.id.storage_btn);
        final int IMAGE_CTR = SC.IMAGE_CTR;
        storage_btn.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View v) {

                v.setEnabled(false);
                v.setClickable(false);
                new LovelyTextInputDialog(MainActivity.this, R.style.TintTheme)
                        .setTopColorRes(R.color.dark_gray)
                        .setTitle(R.string.storage_folder)
                        .setIcon(R.drawable.ic_storage)
                        .setInitialInput(SC.INPUT_DIR+SC.IMAGE_PREFIX)
                        .setHint(getString(R.string.storage_hint,IMAGE_CTR))
                        .setInputFilter(R.string.storage_invalid, new LovelyTextInputDialog.TextFilter() {
                            @Override
                            public boolean check(String text) {
                                // TODO: update this filter
                                return text.matches("[\\w\\d]+[/\\w\\d]*");
                            }
                        })
                        .setConfirmButton(android.R.string.ok, new LovelyTextInputDialog.OnTextInputConfirmListener() {
                            @Override
                            public void onTextInputConfirmed(String text) {
                                Log.d(TAG,"Received text: "+text);
                                if(!text.endsWith("/"))
                                    text+="/";
                                String [] splits = text.split("/");
                                String tmp = "";
                                SC.INPUT_DIR = splits[0]+"/";
                                int ctr = 0;
                                for(String s : splits){
                                    tmp = tmp+s+"/";
                                    ctr++;
                                    if(ctr == splits.length-1){
                                        SC.INPUT_DIR = tmp;
                                        SC.INPUT_ORIG_DIR = "Original_"+SC.INPUT_DIR;
                                    }
                                    if(ctr == splits.length){
                                        SC.IMAGE_PREFIX = s;
                                    }
                                }
//                                TODO: everything should be inside STORAGE_TECHNO
                                SC.CURR_DIR =  SC.STORAGE_HOME +"/" + SC.INPUT_DIR;
                                SC.CURR_ORIG_DIR =  SC.STORAGE_HOME +"/" + SC.INPUT_ORIG_DIR;
                                // checkmakeDirs will be called before saving.
                                Toast.makeText(MainActivity.this, SC.INPUT_DIR+SC.IMAGE_PREFIX, Toast.LENGTH_SHORT).show();
                            }
                        })
                        .show();

                v.setEnabled(true);
                v.setClickable(true);
            }
        });
        storage_btn.setOnLongClickListener(new View.OnLongClickListener() {
            @Override
            public boolean onLongClick(View v) {
                Crashlytics.getInstance().crash();
                return false;
            }
        });

        permHandler = new SimplePermissions(MainActivity.this, new String[]{
                android.Manifest.permission.WRITE_EXTERNAL_STORAGE,
                android.Manifest.permission.CAMERA
        });
//        TODO: check if this loader works fine -> They say static linking is better. Don't do both
//        if (!OpenCVLoader.initDebug()) {
//            Log.d(TAG, "Internal OpenCV library not found. Using OpenCV Manager for initialization");
//            OpenCVLoader.initAsync(OpenCVLoader.OPENCV_VERSION_3_4_0, this, mLoaderCallback);
//        } else {
//            Log.d(TAG, "OpenCV library found inside package. Using it!");
//            mLoaderCallback.onManagerConnected(LoaderCallbackInterface.SUCCESS);
//        }
        permHandler.grantPermissions();
    }

    @Override
    public void onPause()
    {
        super.onPause();
        Log.d(TAG, "onPause activity.");
        if (mOpenCvCameraView != null)
            mOpenCvCameraView.disableView();
    }

    @Override
    public void onResume()
    {
        super.onResume();
//        Log.i(TAG, "OpenCV loaded successfully");
        mOpenCvCameraView.enableView();
    }
//
//    private BaseLoaderCallback mLoaderCallback = new BaseLoaderCallback(this) {
//        @Override
//        public void onManagerConnected(int status) {
//            switch (status) {
//                case LoaderCallbackInterface.SUCCESS:
//                {
//                    Log.i(TAG, "OpenCV loaded successfully");
//                    permHandler.grantPermissions();
//                } break;
//                default:
//                {
//                    super.onManagerConnected(status);
//                } break;
//            }
//        }
//    };
    //    callback from ActivityCompat.requestPermissions
    @Override
    public void onRequestPermissionsResult(int requestCode, @NonNull String PermissionsList[], @NonNull int[] grantResults) {
        // https://stackoverflow.com/questions/34342816/android-6-0-multiple-PermissionsList
        if (permHandler.hasAllPermissions()) {
            // allGrantedCallback();
            Toast.makeText(MainActivity.this, "Permissions granted", Toast.LENGTH_SHORT).show();
            onCameraGranted();
            onStorageGranted();
        }
        else {
            // rejectedCallback();
            Toast.makeText(MainActivity.this, "Please manually enable the permissions from settings. Exiting App!", Toast.LENGTH_LONG).show();
            exitApp();
        }
    }
    private void onCameraGranted() {
        new Handler().postDelayed(new Runnable() {
            @Override
            public void run() {
                runOnUiThread(new Runnable() {
                    @Override
                    public void run() {
                        ScanCanvasView scanCanvasView = new ScanCanvasView(MainActivity.this);
                        cameraPreviewLayout.addView(scanCanvasView);
                        MainActivity.this.scanCanvasView = scanCanvasView;
//                        mOpenCvCameraView.setCameraIndex(1); // <- Front camera
                        mOpenCvCameraView.setCvCameraViewListener(MainActivity.this);

                        mOpenCvCameraView.enableView();
                        mOpenCvCameraView.setVisibility(SurfaceView.VISIBLE);
                    }
                });
            }
//        TODO: Find out why this delay is there
        }, 500);
    }
    private void onStorageGranted() {
        // create intermediate directories
        // FileUtils.checkMakeDirs(SC.APPDATA_DIR);
        FileUtils.checkMakeDirs(SC.STORAGE_TECHNO);
        getOrMakeMarker();
        storage_btn.performClick();
    }
    private void getOrMakeMarker() {
        File mFile = new File(SC.STORAGE_TECHNO, SC.MARKER_NAME);
        if(! mFile.exists()){
            Bitmap bm = BitmapFactory.decodeResource( getResources(), R.drawable.default_omr_marker);
            boolean success = FileUtils.saveBitmap(bm, SC.STORAGE_TECHNO, SC.MARKER_NAME);
            if(success) {
                Log.d("custom" + TAG, "Marker copied successfully to storage folder.");
                Toast.makeText(MainActivity.this, "Marker created at: " + SC.STORAGE_TECHNO, Toast.LENGTH_SHORT).show();
            }
            else
                Log.d("custom"+TAG,"Error copying Marker to storage folder.");
        }
        else{
            Log.d("custom"+TAG,"Marker found in storage folder: "+SC.STORAGE_TECHNO);
            Toast.makeText(MainActivity.this, "Marker Loaded from: "+SC.TECHNO_DIR, Toast.LENGTH_SHORT).show();
        }

        SC.markerToMatch = Utils.resize_util(Imgcodecs.imread(mFile.getAbsolutePath(), Imgcodecs.IMREAD_GRAYSCALE), (int) SC.uniform_width/SC.marker_scale_fac);
        Imgproc.blur(SC.markerToMatch, SC.markerToMatch, new Size(2,2));
        SC.markerEroded = Utils.erodeSub(SC.markerToMatch);
        Utils.logShape("markerToMatch", SC.markerToMatch);
    }

    //    a Runnable named CameraWorker from JavaCameraView calls deliverAndDrawFrame(..), thus the separate thread
    //  So the 'JavaCameraView' view actually instantiates JavaCameraView class!
    public Mat onCameraFrame(CameraBridgeViewBase.CvCameraViewFrame inputFrame) {
        // internally it is a mat-
        outMat = inputFrame.rgba();
        Imgproc.cvtColor(outMat, outMat, Imgproc.COLOR_BGR2GRAY, 4);
        // outMat = inputFrame.gray(); //<-- MUCH SLOWER THAN CVTCOLOR!
        configController.updateConfig();
        // Utils.logShape("outMat",outMat);
        SC.CLAHE_ON = checkBtn(R.id.clahe_btn);
        SC.GAMMA_ON = checkBtn(R.id.gamma_btn);
        SC.ERODE_ON = checkBtn(R.id.hover_btn);

        Mat processedMat = Utils.preProcessMat(outMat);
        // Core.rotate(processedMat, processedMat, Core.ROTATE_90_CLOCKWISE);
//        Utils.logShape("processedMat",processedMat);
        scanCanvasView.unsetCameraBitmap();
        scanCanvasView.unsetHoverBitmap();
        if (!checkBtn(R.id.xray_btn)) {
            try {
                Quadrilateral page = Utils.findPage(processedMat);
                if (null != page) {
                    Size originalPreviewSize = processedMat.size();
                    int originalPreviewArea = processedMat.rows() * processedMat.cols();
                    double contourArea = Math.abs(Imgproc.contourArea(page.contour));
                    guidedDrawRect(processedMat, page.points, contourArea, originalPreviewSize, originalPreviewArea);
                } else {
                    displayHint(ScanHint.FIND_RECT);
                    cancelAutoCapture();
                }
            } catch (Exception e) {
                Log.d(TAG, "Uh oh.. Camera error?", e);
                displayHint(ScanHint.ERROR_RECT);
                cancelAutoCapture();
            }
        }
        else{
            // clear the shapes
            scanCanvasView.clear();
            displayHint(ScanHint.NO_MESSAGE);
            if(checkBtn(R.id.morph_btn))
                Utils.morph(processedMat);

            Utils.thresh(processedMat);

            if(checkBtn(R.id.canny_btn))
                Utils.canny(processedMat);
            if(checkBtn(R.id.contour_btn))
                Utils.drawContours(processedMat);
            // TODO : templateMatching output here?!
        }

        // THE MEMORY CONSUMING PART :
        // rotate the bitmap for portrait
        tempBitmap = Utils.matToBitmapRotate(processedMat);
        scanCanvasView.setCameraBitmap(tempBitmap);
        // set to render frame again
        invalidateCanvas();
        processedMat.release();

        return outMat;
    }
    private Boolean checkBtn(Integer k){
        return ((JellyToggleButton)findViewById(k)).isChecked();
    }

    @Override
    public void displayHint (ScanHint scanHint) {
        runOnUiThread(new ScanHinter(scanHint));
    }

    public class ScanHinter implements Runnable {
        private ScanHint scanHint;
        public ScanHinter(ScanHint scanHint) {
            this.scanHint = scanHint;
        }

        @Override
        public void run() {
            captureHintLayout.setVisibility(View.VISIBLE);
            switch (scanHint) {
                case MOVE_CLOSER:
                    captureHintText.setText(res.getString(R.string.move_closer));
                    captureHintLayout.setBackground(res.getDrawable(R.drawable.hint_red));
                    break;
                case MOVE_AWAY:
                    captureHintText.setText(res.getString(R.string.move_away));
                    captureHintLayout.setBackground(res.getDrawable(R.drawable.hint_red));
                    break;
                case ADJUST_ANGLE:
                    captureHintText.setText(res.getString(R.string.adjust_angle));
                    captureHintLayout.setBackground(res.getDrawable(R.drawable.hint_red));
                    break;
                case ERROR_RECT:
                    captureHintText.setText(res.getString(R.string.error_rect));
                    captureHintLayout.setBackground(res.getDrawable(R.drawable.hint_red));
                    break;
                case FIND_RECT:
                    captureHintText.setText(res.getString(R.string.finding_rect));
                    captureHintLayout.setBackground(res.getDrawable(R.drawable.hint_white));
                    break;
                case FINDING_MARKERS:
                    captureHintText.setText(res.getString(R.string.finding_markers));
                    captureHintLayout.setBackground(res.getDrawable(R.drawable.hint_green));
                    break;
                case CAPTURING_IMAGE:
                    captureHintText.setText(res.getString(R.string.capturing));
                    captureHintLayout.setBackground(res.getDrawable(R.drawable.hint_green));
                    break;
                case NO_MESSAGE:
                    captureHintLayout.setVisibility(GONE);
                    break;
                default:
                    break;
            }
        }
    }
    private void guidedDrawRect(Mat processedMat, Point[] points, double contourArea, Size stdSize, int previewArea) {
        // ATTENTION: axes are swapped
        float previewWidth = (float) stdSize.height;
        float previewHeight = (float) stdSize.width;
        //Height calculated on Y axis
        double resultHeight = Math.max(points[1].x - points[0].x, points[2].x - points[3].x);
        //Width calculated on X axis
        double resultWidth = Math.max(points[3].y - points[0].y, points[2].y - points[1].y);
        List<Point> pointsList = Arrays.asList(points);
        ImageDetectionProperties imgDetectionPropsObj
                = new ImageDetectionProperties(previewWidth, previewHeight, resultWidth, resultHeight,
                previewArea, contourArea, points[0], points[1], points[2], points[3]);

        final ScanHint scanHint;

        if (imgDetectionPropsObj.isDetectedAreaBeyondLimits()) {
            scanHint = ScanHint.FIND_RECT;
            cancelAutoCapture();
        }
        else if (imgDetectionPropsObj.isDetectedAreaBelowLimits()) {
            cancelAutoCapture();
            if (imgDetectionPropsObj.isEdgeTouching()) {
                scanHint = ScanHint.MOVE_AWAY;
            } else {
                scanHint = ScanHint.MOVE_CLOSER;
            }
        }
        else if (imgDetectionPropsObj.isDetectedHeightAboveLimit()
                || imgDetectionPropsObj.isDetectedWidthAboveLimit()
                || imgDetectionPropsObj.isDetectedAreaAboveLimit()
                || imgDetectionPropsObj.isEdgeTouching()) {
            cancelAutoCapture();
            scanHint = ScanHint.MOVE_AWAY;
        }
        else {
            if (imgDetectionPropsObj.isAngleNotCorrect(pointsList)) {
                cancelAutoCapture();
                scanHint = ScanHint.ADJUST_ANGLE;
            }
            else {
                // startCheckMarker();
                Mat warpLevel1 = Utils.four_point_transform(processedMat, points);
                Utils.resize_util_inplace(warpLevel1, SC.uniform_width, SC.uniform_height);
                List<Point> markerPts = Utils.findMarkers(warpLevel1);
                // warpLevel1 will have debug info drawn on it

                Bitmap cameraBitmap = Utils.matToBitmapRotate(warpLevel1);
                scanCanvasView.setHoverBitmap(cameraBitmap);

                // need 4 points above threshold - GG with extensibility here
                if(markerPts.size()==4 && Utils.getMaxCosine(markerPts) >= 0.30){
                    scanHint = ScanHint.CAPTURING_IMAGE;
                    // (low FPS target) creating a bitmap every frame is MEMORY HOGGING!
                    // run less times
                    if(acceptLayoutShowing) {
//                        TODO: operations repeating here. add single operation condition to save some>
//                        if(saveOutMat != null)
//                            saveOutMat.release();
                        saveOutMat = outMat.clone();
                        saveCols = processedMat.cols();
                        saveRows = processedMat.rows();
                        savePoints = points.clone();
                    }
                    tryAutoCapture(scanHint);
                }
                else{
                    scanHint = ScanHint.FINDING_MARKERS;
                }
            }
        }
        // ATTENTION: axes are swapped
        Path path = new Path();
        //Points are drawn in anticlockwise direction
        path.moveTo(previewWidth - (float) points[0].y, (float) points[0].x);
        path.lineTo(previewWidth - (float) points[1].y, (float) points[1].x);
        path.lineTo(previewWidth - (float) points[2].y, (float) points[2].x);
        path.lineTo(previewWidth - (float) points[3].y, (float) points[3].x);
        path.close();

        PathShape newBox = new PathShape(path, previewWidth, previewHeight);
        Paint paint = new Paint();
        Paint border = new Paint();

        border.setStrokeWidth(7);
        displayHint(scanHint);
        setPaintAndBorder(scanHint, paint, border);
        // clear previous shapes
        scanCanvasView.clear();
        // add new shape
        scanCanvasView.addShape(newBox, paint, border);
    }

    private void setPaintAndBorder(ScanHint scanHint, Paint paint, Paint border) {
        int paintColor = 0;
        int borderColor = 0;

        switch (scanHint) {
            case MOVE_CLOSER:
            case MOVE_AWAY:
            case ADJUST_ANGLE:
            case ERROR_RECT:
                paintColor = Color.argb(30, 255, 38, 0);
                borderColor = Color.rgb(255, 38, 0);
                break;
            case FIND_RECT:
                paintColor = Color.argb(0, 0, 0, 0);
                borderColor = Color.argb(0, 0, 0, 0);
                break;
            case CAPTURING_IMAGE:
                paintColor = Color.argb(30, 38, 216, 76);
                borderColor = Color.rgb(38, 216, 76);
                break;
        }

        paint.setColor(paintColor);
        border.setColor(borderColor);
    }


    private void doAutoCapture(ScanHint scanHint) {
        Log.d(TAG,"autoCapture called.");
        if (isCapturing) return;
        Log.d(TAG,"autoCapture check.");
        if (ScanHint.CAPTURING_IMAGE.equals(scanHint)) {
            try {
                Log.d(TAG,"autoCapture action.");
                isCapturing = true;
                displayHint(ScanHint.CAPTURING_IMAGE);
//                Do a capture animation:
                //        There's option to enable Shutter Sound as well
                if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.KITKAT)
                    TransitionManager.beginDelayedTransition(containerScan);
                cropAcceptBtn.performClick();

            } catch (Exception e) {
                e.printStackTrace();
            }
            isCapturing = false;
        }
    }

    private void tryAutoCapture(final ScanHint scanHint) {
        if(!acceptLayoutShowing) {
            acceptLayoutShowing = true;
            secondsLeft = 0;
            runOnUiThread(new Runnable() {
                @Override
                public void run() {

                    acceptLayout.setVisibility(View.VISIBLE);

                    autoCaptureTimer = new CountDownTimer(SC.AUTOCAP_TIMER * 1000, 1000) {
                        public void onTick(long millisUntilFinished) {
                            secondsLeft = Math.round((float) millisUntilFinished / 1000.0f);
                            timeElapsedText.setText( res.getString(R.string.timer_text,secondsLeft));
                        }

                        public void onFinish() {
                            secondsLeft = 0;
                            acceptLayoutShowing = false;
                            acceptLayout.setVisibility(View.GONE);
                            timeElapsedText.setText( res.getString(R.string.timer_text,0));
                            doAutoCapture(scanHint);
                        }
                    };
                    autoCaptureTimer.start();
                }
            });
        }
    }
    public void cancelAutoCapture() {
        if (acceptLayoutShowing) {
            runOnUiThread(new Runnable() {
                @Override
                public void run() {
                    acceptLayoutShowing = false;
                    autoCaptureTimer.cancel();
                    secondsLeft = 0;
                    acceptLayout.setVisibility(View.GONE);
                }
            });
        }
    }
    private void stopCheckMarker() {
        // for check marker limiting
        if(checkMarkerBegun){
            runOnUiThread(new Runnable() {
                @Override
                public void run() {
                    checkMarkerBegun = false;
                    checkMarkerTimer.cancel();
                }
            });
        }
    }
    private void startCheckMarker() {
        if(!checkMarkerBegun) {
            checkMarkerBegun = true;
            canCheckMarker = false;
            runOnUiThread(new Runnable() {
                @Override
                public void run() {

                    checkMarkerTimer = new CountDownTimer(SC.HOLD_TIMER * 1000 , 500) {
                        public void onTick(long millisUntilFinished) {
                            // secondsLeft = Math.round((float) millisUntilFinished / 1000.0f);
                            // timeElapsedText.setText( res.getString(R.string.timer_text,secondsLeft));
                        }

                        public void onFinish() {
                            canCheckMarker = true;
                        }
                    };
                    checkMarkerTimer.start();
                }
            });
        }
    }


    public void invalidateCanvas() {
        // scanCanvasView.invalidate();
        scanCanvasView.postInvalidate(); // on UI thread
    }

    // After invoking this the frames will start to be delivered to client via the onCameraFrame() callback.
    public void onCameraViewStarted(int width, int height) {
        Log.d(TAG, "onCameraViewStarted ");

        Size frameSize = ((JavaCameraView)mOpenCvCameraView).frameSize;
//        analytics data- (more in JavaCameraView)
//        frameSize
//        android.os.Build.MODEL
        Toast.makeText(MainActivity.this, "Camera frame size: " + (int)frameSize.width + "x" + (int)frameSize .height, Toast.LENGTH_SHORT).show();
        Log.d(TAG, "Resolution Used: " + frameSize.width + "x" + frameSize.height);
    }
    public void onCameraViewStopped() {
        Log.d(TAG, "onCameraViewStopped ");
    }

    public void onDestroy() {
        super.onDestroy();
        if (mOpenCvCameraView != null)
            mOpenCvCameraView.disableView();
    }
    public void exitApp(){
        System.gc();
        finish();
    }
}
