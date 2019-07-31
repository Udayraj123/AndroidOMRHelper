package com.udayraj.omrhelper.util;
import android.graphics.Bitmap;
import android.util.Log;

import java.io.File;
import java.io.FileOutputStream;
import java.io.IOException;

public class FileUtils {
    private static final String TAG = FileUtils.class.getSimpleName();

    public static boolean saveBitmap(Bitmap bitmap, String folder, String name) {
        // default args
        return saveBitmap(bitmap, folder, name, 95);
    }
    private static File touchFile(String folderpath, String filename) throws IOException {
        File file = new File(folderpath,filename);
        if(!file.exists())
            file.createNewFile();
        return file;
    }
    public static void checkMakeDirs(String folderpath){
        File folder = new File(folderpath);
        if(!folder.exists()) {
            Log.d("custom" + TAG, "Making new directories for" + folderpath);

        }
        folder.mkdirs();
    }
    public static boolean saveBitmap(Bitmap bitmap, String folderpath, String filename, int mQuality) {
        Log.d("custom"+TAG, "saveBitmap: Saving image: " + folderpath+filename);

        try {
            checkMakeDirs(folderpath);
            File file = touchFile(folderpath, filename);
            FileOutputStream mFileOutputStream = new FileOutputStream(file);
            //Compress method used on the Bitmap object to write  image to output stream
            bitmap.compress(Bitmap.CompressFormat.JPEG, mQuality, mFileOutputStream);
            mFileOutputStream.close();
        } catch (Exception e) {
            Log.e("custom"+TAG, e.getMessage(), e);
            return false;
        }
        return true;
    }
}
