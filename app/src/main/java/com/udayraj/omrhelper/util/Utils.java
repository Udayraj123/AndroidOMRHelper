package com.udayraj.omrhelper.util;

import android.app.Activity;
import android.graphics.Bitmap;
import android.graphics.BitmapFactory;
import android.graphics.Matrix;
import android.hardware.Camera;
import android.util.Log;
import android.view.Display;
import android.view.Surface;

import androidx.core.util.Pair;

import com.udayraj.omrhelper.constants.SC;
import com.udayraj.omrhelper.view.Quadrilateral;

import org.opencv.core.Core;
import org.opencv.core.CvType;
import org.opencv.core.Mat;
import org.opencv.core.MatOfInt;
import org.opencv.core.MatOfPoint;
import org.opencv.core.MatOfPoint2f;
import org.opencv.core.Point;
import org.opencv.core.Scalar;
import org.opencv.core.Size;
import org.opencv.imgproc.CLAHE;
import org.opencv.imgproc.Imgproc;
import org.opencv.utils.Converters;

import java.io.File;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collections;
import java.util.Comparator;
import java.util.List;

/**
 * This class provides utilities for camera.
 */

public class Utils {
    private static final String TAG = Utils.class.getSimpleName();

    public static boolean compareFloats(double left, double right) {
        double epsilon = 0.00000001;
        return Math.abs(left - right) < epsilon;
    }

    public static Camera.Size determinePictureSize(Camera camera, Camera.Size previewSize) {
        if (camera == null) return null;
        Camera.Parameters cameraParams = camera.getParameters();
        List<Camera.Size> pictureSizeList = cameraParams.getSupportedPictureSizes();
        Collections.sort(pictureSizeList, new Comparator<Camera.Size>() {
            @Override
            public int compare(Camera.Size size1, Camera.Size size2) {
                Double h1 = Math.sqrt(size1.width * size1.width + size1.height * size1.height);
                Double h2 = Math.sqrt(size2.width * size2.width + size2.height * size2.height);
                return h2.compareTo(h1);
            }
        });
        Camera.Size retSize = null;

        // if the preview size is not supported as a picture size
        float reqRatio = ((float) previewSize.width) / previewSize.height;
        float curRatio, deltaRatio;
        float deltaRatioMin = Float.MAX_VALUE;
        for (Camera.Size size : pictureSizeList) {
            curRatio = ((float) size.width) / size.height;
            deltaRatio = Math.abs(reqRatio - curRatio);
            if (deltaRatio < deltaRatioMin) {
                deltaRatioMin = deltaRatio;
                retSize = size;
            }
            if (Utils.compareFloats(deltaRatio, 0)) {
                break;
            }
        }

        return retSize;
    }

    public static Camera.Size getOptimalPreviewSize(Camera camera, int w, int h) {
        if (camera == null) return null;
        final double targetRatio = (double) h / w;
        Camera.Parameters cameraParams = camera.getParameters();
        List<Camera.Size> previewSizeList = cameraParams.getSupportedPreviewSizes();
        Collections.sort(previewSizeList, new Comparator<Camera.Size>() {
            @Override
            public int compare(Camera.Size size1, Camera.Size size2) {
                double ratio1 = (double) size1.width / size1.height;
                double ratio2 = (double) size2.width / size2.height;
                Double ratioDiff1 = Math.abs(ratio1 - targetRatio);
                Double ratioDiff2 = Math.abs(ratio2 - targetRatio);
                if (Utils.compareFloats(ratioDiff1, ratioDiff2)) {
                    Double h1 = Math.sqrt(size1.width * size1.width + size1.height * size1.height);
                    Double h2 = Math.sqrt(size2.width * size2.width + size2.height * size2.height);
                    return h2.compareTo(h1);
                }
                return ratioDiff1.compareTo(ratioDiff2);
            }
        });

        return previewSizeList.get(0);
    }

    public static int configureCameraAngle(Activity activity) {
        int angle;

        Display display = activity.getWindowManager().getDefaultDisplay();
        switch (display.getRotation()) {
            case Surface.ROTATION_0: // This is display orientation
                angle = 90; // This is camera orientation
                break;
                case Surface.ROTATION_90:
                angle = 0;
                break;
                case Surface.ROTATION_180:
                angle = 270;
                break;
                case Surface.ROTATION_270:
                angle = 180;
                break;
                default:
                angle = 90;
                break;
            }

            return angle;
        }

        public static Point[] sortPoints(Point[] src) {
            ArrayList<Point> srcPoints = new ArrayList<>(Arrays.asList(src));
            Point[] result = {null, null, null, null};

            Comparator<Point> sumComparator = new Comparator<Point>() {
                @Override
                public int compare(Point lhs, Point rhs) {
                    return Double.compare(lhs.y + lhs.x,rhs.y + rhs.x);
                }
            };

            Comparator<Point> diffComparator = new Comparator<Point>() {

                @Override
                public int compare(Point lhs, Point rhs) {
                    return Double.compare(lhs.y - lhs.x, rhs.y - rhs.x);
                }
            };

        // top-left corner = minimal sum
            result[0] = Collections.min(srcPoints, sumComparator);
        // top-right corner = minimal difference
            result[1] = Collections.min(srcPoints, diffComparator);
        // bottom-right corner = maximal sum
            result[2] = Collections.max(srcPoints, sumComparator);
        // bottom-left corner = maximal difference
            result[3] = Collections.max(srcPoints, diffComparator);

            return result;
        }

    //    needed coz of the mess opencv-java has made:
        private static MatOfPoint hull2Points(MatOfInt hull, MatOfPoint contour) {
            List<Integer> indexes = hull.toList();
            List<Point> points = new ArrayList<>();
            List<Point> ctrList = contour.toList();
            for(Integer index:indexes) {
                points.add(ctrList.get(index));
            }
            MatOfPoint point= new MatOfPoint();
            point.fromList(points);
            return point;
        }
        private static List<MatOfPoint> getTopContours(Mat inputMat, int MAX_TOP_CONTOURS) {
            Mat mHierarchy = new Mat();
            List<MatOfPoint> mContourList = new ArrayList<>();
        //finding contours - RETR_LIST is (faster, thus) better as we are sorting by area anyway
            Imgproc.findContours(inputMat, mContourList, mHierarchy, Imgproc.RETR_LIST, Imgproc.CHAIN_APPROX_SIMPLE);
        // convert contours to their convex hulls
            List<MatOfPoint> mHullList = new ArrayList<>();
            MatOfInt tempHullIndices = new MatOfInt();
            for (int i = 0; i < mContourList.size(); i++) {
                Imgproc.convexHull(mContourList.get(i), tempHullIndices);
                mHullList.add(hull2Points(tempHullIndices, mContourList.get(i)));
            }
            // Release mContourList as its job is done
            for (MatOfPoint c : mContourList)
                c.release();
            tempHullIndices.release();
            mHierarchy.release();
            
            if (mHullList.size() != 0) {
                Collections.sort(mHullList, new Comparator<MatOfPoint>() {
                    @Override
                    public int compare(MatOfPoint lhs, MatOfPoint rhs) {
                        return Double.compare(Imgproc.contourArea(rhs),Imgproc.contourArea(lhs));
                    }
                });
                return mHullList.subList(0, Math.min(mHullList.size(), MAX_TOP_CONTOURS));
            }
            return null;
        }

        public static void drawContours(Mat processedMat) {
            List<MatOfPoint> contours = getTopContours(processedMat,3);
            if(contours == null) {
                Log.d(TAG, "No Contours found! ");
                return;
            }
            for (int i = 0; i < contours.size(); i++) {
                Quadrilateral mLargestRect = findQuadrilateral(contours.get(i));
                if(mLargestRect != null){
                    List<MatOfPoint> mList = new ArrayList<>();
                    mList.add(new MatOfPoint(mLargestRect.contour.toArray()));
                    Imgproc.drawContours(processedMat, mList, 0, new Scalar(255, 255, 255), 5);
                }
                else {
                    Imgproc.drawContours(processedMat, contours, i, new Scalar(155, 155, 155), 3);
                }
            }
        }

        private static int distance(Point a,Point b) {
            double xDiff = a.x - b.x;
            double yDiff = a.y - b.y;
            return (int) Math.sqrt(Math.pow(xDiff,2) + Math.pow(yDiff, 2));
        }
        private static Quadrilateral findQuadrilateral(MatOfPoint c) {
            MatOfPoint2f c2f = new MatOfPoint2f(c.toArray());
            double peri = Imgproc.arcLength(c2f, true);
            MatOfPoint2f approx = new MatOfPoint2f();
            Imgproc.approxPolyDP(c2f, approx, 0.025 * peri, true);
            Point[] points = approx.toArray();
            // select biggest 4 angles polygon
            if (approx.rows() == 4) {
                Point[] foundPoints = sortPoints(points);
                return new Quadrilateral(approx, foundPoints);
            }
            return null;
        }


        private static byte saturate(double val) {
            int iVal = (int) Math.round(val);
            iVal = iVal > 255 ? 255 : (iVal < 0 ? 0 : iVal);
            return (byte) iVal;
        }

        public static void gamma(Mat mat, double gammaValue){
            Mat lookUpTable = new Mat(1, 256, CvType.CV_8U);
            byte[] lookUpTableData = new byte[(int) (lookUpTable.total()*lookUpTable.channels())];
            for (int i = 0; i < lookUpTable.cols(); i++) {
                lookUpTableData[i] = saturate(Math.pow(i / 255f, 1f/gammaValue) * 255f);
            }
            lookUpTable.put(0, 0, lookUpTableData);
            Core.LUT(mat, lookUpTable, mat);
        }
        private static CLAHE clahe = Imgproc.createCLAHE(1.1f, new Size(8, 8));

        public static Mat preProcessMat(Mat mat){
            Mat processedMat = Utils.resize_util(mat, SC.uniform_width_hd, SC.uniform_height_hd);
            if(SC.KSIZE_BLUR > 0) {
                Imgproc.blur(processedMat, processedMat, new Size(SC.KSIZE_BLUR, SC.KSIZE_BLUR));
            }
            normalize(processedMat);
            if(SC.CLAHE_ON) {
                clahe.apply(processedMat, processedMat);
            }
            if(SC.GAMMA_ON) {
                gamma(processedMat, SC.GAMMA_HIGH / 100f);
            }
            Imgproc.threshold(processedMat,processedMat, SC.TRUNC_THRESH,255,Imgproc.THRESH_TRUNC);
            normalize(processedMat);

            return processedMat;
        }

        public static void normalize(Mat processedMat){
            Core.normalize(processedMat, processedMat, 0, 255, Core.NORM_MINMAX);
        }
    // public static void threshBinary(Mat processedMat) {
    //     Imgproc.threshold(processedMat, processedMat, SC.CANNY_THRESH, 255, Imgproc.THRESH_BINARY + Imgproc.THRESH_OTSU);
    // }
        public static void canny(Mat processedMat) {
            Imgproc.Canny(processedMat, processedMat, SC.CANNY_THRESHOLD_U, SC.CANNY_THRESHOLD_L, 3, false);
        // threshBinary(processedMat);
        }
        private static Mat morph_kernel = new Mat(new Size(SC.KSIZE_CLOSE, SC.KSIZE_CLOSE), CvType.CV_8UC1, new Scalar(255));

        public static void thresh(Mat processedMat) {
            Imgproc.threshold(processedMat,processedMat,SC.ZERO_THRESH,255,Imgproc.THRESH_TOZERO);
        }
        public static void morph(Mat processedMat) {
        // Close the small holes, i.e. Complete the edges on canny image; ALSO closes stringy lines near edge of paper
            Imgproc.morphologyEx(processedMat, processedMat, Imgproc.MORPH_CLOSE, morph_kernel, new Point(-1,-1),1);
        }

        public static Quadrilateral findPage(Mat inputMat) {
            Mat processedMat = inputMat.clone();
        //Better results than just threshold : Canny then Morph
        //EVEN BETTER : Morph then Canny!
            morph(processedMat);
            thresh(processedMat);
            canny(processedMat);

            List<MatOfPoint> sortedContours = getTopContours(processedMat, 5);
            processedMat.release();
            if (null != sortedContours) {
                for (MatOfPoint c : sortedContours) {
                    Quadrilateral mLargestRect = findQuadrilateral(c);
                    if (mLargestRect != null)
                        return mLargestRect;
                }
            }
            return null;
        }

        public static void logShape(String name, Mat m) {
            Log.d("custom"+TAG, "matrix: "+name+" shape: "+m.rows()+"x"+m.cols());
        }
        public static Mat erodeSub(Mat warpLevel1){
            Mat warpErodedSub = new Mat();
            Imgproc.erode(warpLevel1, warpErodedSub, Imgproc.getStructuringElement(Imgproc.MORPH_RECT, new Size(5,5)), new Point(-1, -1), 5);
            Core.subtract(warpLevel1, warpErodedSub, warpErodedSub);
            normalize(warpErodedSub);
            return warpErodedSub;
        }
        private static Pair<Point,Double> getMaxLoc(Mat warpErodedSub, Mat marker){
            // matchOut will be a float image now!
            Mat matchOut = new Mat(new Size(warpErodedSub.cols() - marker.cols()+1,warpErodedSub.rows() - marker.rows()+1 ), CvType.CV_32FC1);
            //Template matching method : TM_CCOEFF_NORMED works best
            Imgproc.matchTemplate(warpErodedSub, marker, matchOut, Imgproc.TM_CCOEFF_NORMED);
            Core.MinMaxLocResult mmr = Core.minMaxLoc(matchOut);
            Log.d("custom", ""+matchOut.cols()+ "x" + matchOut.rows() + " : "+mmr.maxLoc.x+","+mmr.maxLoc.y);
            int channelNo = 0;
            Pair<Point,Double> ret = new Pair<>(mmr.maxLoc, matchOut.get((int)mmr.maxLoc.y,(int)mmr.maxLoc.x)[channelNo]);

            matchOut.release();
            return ret;
        }
    /**
     * Rotate an image by an angle (counterclockwise)
     *
     * @param image Transform matrix
     * @param angle Angle to rotate by (counterclockwise) from -360 to 360
     */
    public static void rotate(Mat image, double angle) {
        //Calculate size of new matrix
        double radians = Math.toRadians(angle);
        double sin = Math.abs(Math.sin(radians));
        double cos = Math.abs(Math.cos(radians));

        int newWidth = (int) (image.width() * cos + image.height() * sin);
        int newHeight = (int) (image.width() * sin + image.height() * cos);

        // rotating image
        Point center = new Point(newWidth / 2, newHeight / 2);
        Mat rotMatrix = Imgproc.getRotationMatrix2D(center, angle, 1.0); //1.0 means 100 % scale

        Size size = new Size(newWidth, newHeight);
        Imgproc.warpAffine(image, image, rotMatrix, image.size());
    }
        public static List<Point> findMarkers(Mat warpLevel1) {
            Mat warpErodedSub;
            Mat marker;
            if(SC.ERODE_ON) {
                warpErodedSub = erodeSub(warpLevel1);
                marker = SC.markerEroded;
            }
            else {
                warpErodedSub = warpLevel1;
                marker = SC.markerToMatch;
            }
        //TODO: make this part lightweight somehow!

        int curWidth = marker.width();//, best_scale=100;
        // double maxT=0;
        // for(int scale=120;scale>79;scale-=10) {
        //     Mat marker_res = resize_util(marker, (int) ((curWidth * scale)/100f));
        //     Pair<Point,Double> localMax = getMaxLoc(warpErodedSub,marker_res);
        //     marker_res.release();
        //     if(maxT < localMax.second){
        //         best_scale = scale;
        //         maxT = localMax.second;
        //     }
        // }
        // Log.d(TAG,"Best scale: "+best_scale + " maxT: "+maxT);
        Mat marker_best  = resize_util(marker, (int) ((curWidth * SC.MARKER_SCALE)/100f));
        // marker.release();
        int h1 = warpErodedSub.rows();
        int w1 = warpErodedSub.cols();
        int midh = h1/3;
        int midw = w1/2;
        Point[] origins = new Point[]{
            new Point(0,0),
            new Point(midw,0),
            new Point(0,midh),
            new Point(midw,midh),
        };
        Mat[] quads = new Mat[]{
            warpErodedSub.submat(0,midh,0,midw),
            warpErodedSub.submat(0,midh,midw,w1),
            warpErodedSub.submat(midh, h1,0,midw),
            warpErodedSub.submat(midh, h1,midw, w1)
        };
        List<Point> points = new ArrayList<>();
        Pair<Point,Double> allMax = getMaxLoc(warpErodedSub,marker_best);

        for (int k=0;k<4;k++){
            Pair<Point,Double> localMax = getMaxLoc(quads[k],marker_best);
            if((allMax.second - localMax.second) <= SC.thresholdVar){
                localMax.first.x += origins[k].x;
                localMax.first.y += origins[k].y;
                points.add(localMax.first);
            }
            quads[k].release();
        }

        if(SC.ERODE_ON) {
            for( Point matchLoc : points) {
                //Draw rectangle on result image
                Imgproc.rectangle(warpLevel1, matchLoc, new Point(matchLoc.x + marker_best.cols(), matchLoc.y + marker_best.rows()), new Scalar(5, 5, 5), 4);
            }
        }
        rotate(warpLevel1,270);
//        Core.rotate(warpLevel1,warpLevel1, Core.ROTATE_90_CLOCKWISE); //ROTATE_180 or ROTATE_90_COUNTERCLOCKWISE
        Imgproc.rectangle(warpLevel1, new Point(midw-10,h1/2-20), new Point(midw+150,h1/2), new Scalar(225, 225, 225), -1);

        Imgproc.putText(warpLevel1, "Match: "+ (int)(allMax.second*100)+ "%",
            new Point(midw,h1/2),
            Core.FONT_HERSHEY_SIMPLEX, 0.75, new Scalar(50,50,50), 2);
//        Core.rotate(warpLevel1,warpLevel1, Core.ROTATE_90_COUNTERCLOCKWISE); //ROTATE_180 or ROTATE_90_COUNTERCLOCKWISE
        rotate(warpLevel1,90);
        marker_best.release();
        if(allMax.second * 100 < SC.MATCH_PERCENT){
            return new ArrayList<>();
        }
        else {
            return points;
        }
    }
    public static Mat four_point_transform_scaled(Mat outMat, int inCols,int inRows, Point[] points) {
        float scaleW = outMat.cols()/(float)inCols;
        float scaleH = outMat.rows()/(float)inRows;
        Point[] scaled_pts = new Point[] {
            new Point(points[0].x*scaleW, points[0].y*scaleH),
            new Point(points[1].x*scaleW, points[1].y*scaleH),
            new Point(points[2].x*scaleW, points[2].y*scaleH),
            new Point(points[3].x*scaleW, points[3].y*scaleH)
        };
        return four_point_transform(outMat,scaled_pts);
    }

    public static Mat four_point_transform(Mat inputMat, Point[] points) {
        // points are wrt Mat indices _// (as Returned by approxPolyDP for eg) (x+Mat.cols() used in template matching)
        //obtain a consistent order of the points : (tl, tr, br, bl)
        points = sortPoints(points);
        // compute the width of the new image,
        int resultWidth = (int) Math.max(distance(points[3],points[2]), distance(points[1],points[0]));
        // compute the height of the new image,
        int resultHeight = (int) Math.max(distance(points[2],points[1]), distance(points[3],points[0]));
        /*
         * now that we have the dimensions of the new image, construct
         * the set of destination points to obtain a "birds eye view",
         * (i.e. top-down view) of the image, again specifying points
         * in the top-left, top-right, bottom-right, and bottom-left
         * order
         */
        Point[] dst = new Point[] {
            new Point(0, 0),
            new Point(resultWidth - 1, 0),
            new Point(resultWidth - 1 , resultHeight - 1),
            new Point(0, resultHeight - 1)
        };

        // Some Java excess code -
        List<Point> pointsList = Arrays.asList(points);
        List<Point> dest = Arrays.asList(dst);
        Mat startM = Converters.vector_Point2f_to_Mat(pointsList);
        Mat endM = Converters.vector_Point2f_to_Mat(dest);
        Mat outputMat = new Mat(resultWidth, resultHeight, CvType.CV_8UC1);

        //compute the perspective transform matrix and then apply it
        Mat perspectiveTransform = Imgproc.getPerspectiveTransform(startM, endM);
        Imgproc.warpPerspective(inputMat, outputMat, perspectiveTransform, new Size(resultWidth, resultHeight));

        return  outputMat;
    }

    public static Bitmap cropBitmap(Bitmap image, Point[] points) {
        points = sortPoints(points);
        // compute the width of the new image,
        int resultWidth = (int) Math.max(distance(points[3],points[2]), distance(points[1],points[0]));
        // compute the height of the new image,
        int resultHeight = (int) Math.max(distance(points[2],points[1]), distance(points[3],points[0]));
        // Some Android-java excess code -
        Mat inputMat = new Mat(image.getHeight(), image.getHeight(), CvType.CV_8UC1);
        org.opencv.android.Utils.bitmapToMat(image, inputMat);

        Mat outputMat = four_point_transform(inputMat, points);

        Bitmap output = Bitmap.createBitmap(resultWidth, resultHeight, Bitmap.Config.ARGB_8888);
        org.opencv.android.Utils.matToBitmap(outputMat, output);

        return output;
    }

    public static Bitmap decodeBitmapFromFile(String path, String imageName) {
        // First decode with inJustDecodeBounds=true to check dimensions
        final BitmapFactory.Options options = new BitmapFactory.Options();
        options.inPreferredConfig = Bitmap.Config.ARGB_8888;

        return BitmapFactory.decodeFile(new File(path, imageName).getAbsolutePath(),
            options);
    }


    public static double getMaxCosine(List<Point> approxPoints) {
        double maxCosine = 0;
        for (int i = 2; i < 5; i++) {
            double cosine = Math.abs(angle(approxPoints.get(i % 4), approxPoints.get(i - 2), approxPoints.get(i - 1)));
            maxCosine = Math.max(cosine, maxCosine);
        }
        return maxCosine;
    }

    private static double angle(Point p1, Point p2, Point p0) {
        double dx1 = p1.x - p0.x;
        double dy1 = p1.y - p0.y;
        double dx2 = p2.x - p0.x;
        double dy2 = p2.y - p0.y;
        return (dx1 * dx2 + dy1 * dy2) / Math.sqrt((dx1 * dx1 + dy1 * dy1) * (dx2 * dx2 + dy2 * dy2) + 1e-10);
    }

    public static Bitmap decodeBitmapFromByteArray(byte[] data, int reqWidth, int reqHeight) {
        // Raw height and width of image
        // First decode with inJustDecodeBounds=true to check dimensions
        final BitmapFactory.Options options = new BitmapFactory.Options();
        options.inJustDecodeBounds = true;
        BitmapFactory.decodeByteArray(data, 0, data.length, options);

        // Calculate inSampleSize
        final int height = options.outHeight;
        final int width = options.outWidth;
        int inSampleSize = 1;

        if (height > reqHeight || width > reqWidth) {
            final int halfHeight = height / 2;
            final int halfWidth = width / 2;

            // Calculate the largest inSampleSize value that is a power of 2 and keeps both
            // height and width larger than the requested height and width.
            while ((halfHeight / inSampleSize) >= reqHeight && (halfWidth / inSampleSize) >= reqWidth) {
                inSampleSize *= 2;
            }
        }
        options.inSampleSize = inSampleSize;

        // Decode bitmap with inSampleSize set
        options.inJustDecodeBounds = false;
        return BitmapFactory.decodeByteArray(data, 0, data.length, options);
    }

    @Deprecated
    public static Bitmap loadEfficientBitmap(byte[] data, int width, int height) {
        Bitmap bmp;

        // First decode with inJustDecodeBounds=true to check dimensions
        final BitmapFactory.Options options = new BitmapFactory.Options();
        options.inJustDecodeBounds = true;
        BitmapFactory.decodeByteArray(data, 0, data.length, options);

        // Calculate inSampleSize
        options.inSampleSize = calculateInSampleSize(options, width, height);

        // Decode bitmap with inSampleSize set
        options.inJustDecodeBounds = false;
        bmp = BitmapFactory.decodeByteArray(data, 0, data.length, options);
        return bmp;
    }

    private static int calculateInSampleSize(
        BitmapFactory.Options options, int reqWidth, int reqHeight) {
        // Raw height and width of image
        final int height = options.outHeight;
        final int width = options.outWidth;
        int inSampleSize = 1;

        if (height > reqHeight || width > reqWidth) {

            final int halfHeight = height / 2;
            final int halfWidth = width / 2;

            // Calculate the largest inSampleSize value that is a power of 2 and keeps both
            // height and width larger than the requested height and width.
            while ((halfHeight / inSampleSize) >= reqHeight
                && (halfWidth / inSampleSize) >= reqWidth) {
                inSampleSize *= 2;
        }
    }

    return inSampleSize;
}

public static Bitmap resizeToScreenContentSize(Bitmap bm, int newWidth, int newHeight) {
    int width = bm.getWidth();
    int height = bm.getHeight();
    float scaleWidth = ((float) newWidth) / width;
    float scaleHeight = ((float) newHeight) / height;
        // CREATE A MATRIX FOR THE MANIPULATION
    Matrix matrix = new Matrix();
        // RESIZE THE BIT MAP
    matrix.postScale(scaleWidth, scaleHeight);

        // "RECREATE" THE NEW BITMAP
    Bitmap resizedBitmap = Bitmap.createBitmap(
        bm, 0, 0, width, height, matrix, false);
    bm.recycle();
    return resizedBitmap;
}

public static Mat resize_util(Mat image, int u_width, int u_height) {
    Size sz = new Size(u_width,u_height);
    Mat resized = new Mat();
    if(image.cols() > u_width)
            // for downscaling
        Imgproc.resize(image,resized ,sz, 0,0 ,Imgproc.INTER_AREA);
    else
            // for upscaling
        Imgproc.resize(image,resized ,sz, 0,0 ,Imgproc.INTER_CUBIC);
    return resized;
}

public static Mat resize_util(Mat image, int u_width) {
    if(image.cols() == 0)return image;
    int u_height = (image.rows() * u_width)/image.cols();
    return resize_util(image,u_width,u_height);
}

public static void resize_util_inplace(Mat image, int u_width, int u_height) {
    Size sz = new Size(u_width,u_height);
    if(image.cols() > u_width)
            // for downscaling
        Imgproc.resize(image,image ,sz, 0,0 ,Imgproc.INTER_AREA);
    else
            // for upscaling
        Imgproc.resize(image,image ,sz, 0,0 ,Imgproc.INTER_CUBIC);
}

public static void resize_util_inplace(Mat image, int u_width) {
    if(image.cols() == 0)return;
    int u_height = (image.rows() * u_width)/image.cols();
    resize_util_inplace(image,u_width,u_height);
}

public static Bitmap matToBitmapRotate(Mat processedMat){
    Bitmap cameraBitmap = Bitmap.createBitmap(processedMat.cols(), processedMat.rows(), Bitmap.Config.ARGB_8888);
    org.opencv.android.Utils.matToBitmap(processedMat, cameraBitmap);
    Matrix rotateMatrix = new Matrix();
    rotateMatrix.postRotate(90);
        // filter = true does the applyTransform here!
    return Bitmap.createBitmap(cameraBitmap, 0, 0, cameraBitmap.getWidth(), cameraBitmap.getHeight(), rotateMatrix, true);
}


    //UNUSED -
public static Bitmap rotateBitmap(Bitmap cameraBitmap, int degrees){
    Matrix matrix = new Matrix();
    matrix.postRotate(degrees);
        // filter = true does the applyTransform here!
    return Bitmap.createBitmap(cameraBitmap, 0, 0, cameraBitmap.getWidth(), cameraBitmap.getHeight(), matrix, true);
}
public static Bitmap resizeBitmap(Bitmap image, int maxWidth, int maxHeight) {
    if (maxHeight > 0 && maxWidth > 0) {
        int width = image.getWidth();
        int height = image.getHeight();
        float ratioBitmap = (float) width / (float) height;
        float ratioMax = (float) maxWidth / (float) maxHeight;

        int finalWidth = maxWidth;
        int finalHeight = maxHeight;
        if (ratioMax > 1) {
            finalWidth = (int) ((float) maxHeight * ratioBitmap);
        } else {
            finalHeight = (int) ((float) maxWidth / ratioBitmap);
        }

        image = Bitmap.createScaledBitmap(image, finalWidth, finalHeight, true);
        return image;
    } else {
        return image;
    }
}


public static Point[] getPolygonDefaultPoints(int width, int height) {
    return new Point [] {
        new Point((int) (width * 0.14f), (int) (height * 0.13f)),
        new Point((int) (width * 0.84f), (int) (height * 0.13f)),
        new Point((int) (width * 0.14f), (int) (height * 0.83f)),
        new Point((int) (width * 0.84f), (int) (height * 0.83f))
    };
}
}
