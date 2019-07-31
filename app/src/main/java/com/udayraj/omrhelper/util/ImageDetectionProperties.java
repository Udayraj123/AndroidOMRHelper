package com.udayraj.omrhelper.util;

import org.opencv.core.Point;

import java.util.List;

/**
 * This class holds configuration of detected edges
 */
public class ImageDetectionProperties {
    private final double previewWidth;
    private final double previewHeight;
    private final double resultWidth;
    private final double resultHeight;
    private final Point topLeftPoint;
    private final Point bottomLeftPoint;
    private final Point bottomRightPoint;
    private final Point topRightPoint;
    private final double previewArea;
    private final double resultArea;
    private final double EDGE_MARGIN_FAC = 0.02;
    private final int EDGE_MARGIN_HZ;
    private final int EDGE_MARGIN_VT;
    public ImageDetectionProperties(double previewWidth, double previewHeight, double resultWidth,
                                    double resultHeight, double previewArea, double resultArea,
                                    Point topLeftPoint, Point bottomLeftPoint, Point bottomRightPoint,
                                    Point topRightPoint) {
        this.previewWidth   = previewWidth;
        this.previewHeight  = previewHeight;
        this.previewArea    = previewArea;
        this.resultWidth    = resultWidth;
        this.resultHeight   = resultHeight;
        this.resultArea     = resultArea;
        this.bottomLeftPoint    = bottomLeftPoint;
        this.bottomRightPoint   = bottomRightPoint;
        this.topLeftPoint       = topLeftPoint;
        this.topRightPoint      = topRightPoint;
        this.EDGE_MARGIN_HZ = (int)(previewWidth * EDGE_MARGIN_FAC);
        this.EDGE_MARGIN_VT = (int)(previewHeight * EDGE_MARGIN_FAC);
    }

    public boolean isDetectedAreaBeyondLimits() {
        return isDetectedAreaAboveLimit() || isDetectedAreaBelowLimits();
    }

    public boolean isDetectedWidthAboveLimit() {
        return resultWidth / previewWidth > 0.9;
    }

    public boolean isDetectedHeightAboveLimit() {
        return resultHeight / previewHeight > 0.9;
    }

    public boolean isDetectedAreaAboveLimit() { return resultArea > previewArea * 0.90; }

    public boolean isDetectedAreaBelowLimits() {
        return resultArea < previewArea * 0.15;
    }

    public boolean isAngleNotCorrect(List<Point> approx) {
        return isMaxCosineTooHigh(approx) || isLeftEdgeDistorted() || isRightEdgeDistorted();
    }

    private boolean isRightEdgeDistorted() {
        return Math.abs(topRightPoint.y - bottomRightPoint.y) > 100;
    }

    private boolean isLeftEdgeDistorted() {
        return Math.abs(topLeftPoint.y - bottomLeftPoint.y) > 100;
    }

    private boolean isMaxCosineTooHigh(List<Point> approxPoints) {
        return Utils.getMaxCosine(approxPoints) >= 0.30;
    }

    public boolean isEdgeTouching() {
        return isTopEdgeTouching() || isBottomEdgeTouching() || isLeftEdgeTouching() || isRightEdgeTouching();
    }

    private boolean isBottomEdgeTouching() {
        return (bottomLeftPoint.x >= previewHeight - EDGE_MARGIN_VT || bottomRightPoint.x >= previewHeight - EDGE_MARGIN_VT);
    }

    private boolean isTopEdgeTouching() {
        return (topLeftPoint.x <= EDGE_MARGIN_VT || topRightPoint.x <= EDGE_MARGIN_VT);
    }

    private boolean isRightEdgeTouching() {
        return (topRightPoint.y >= previewWidth - EDGE_MARGIN_HZ || bottomRightPoint.y >= previewWidth - EDGE_MARGIN_HZ);
    }

    private boolean isLeftEdgeTouching() {
        return (topLeftPoint.y <= EDGE_MARGIN_HZ || bottomLeftPoint.y <= EDGE_MARGIN_HZ);
    }
}
