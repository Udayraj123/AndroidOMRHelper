package com.udayraj.omrhelper.interfaces;
import com.udayraj.omrhelper.constants.ScanHint;

/**
 * Interface between activity and surface view
 */

public interface IScanner {
    void displayHint(ScanHint scanHint);
    // void onPictureClicked(Bitmap bitmap);
}
