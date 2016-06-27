package com.flickerimageupload;

import android.app.Application;

import net.gotev.uploadservice.UploadService;

/**
 * Created by preetam on 27/06/16.
 */
public class ImageUploadApp extends Application {
    @Override
    public void onCreate() {
        super.onCreate();
        UploadService.NAMESPACE = "com.flickerimageupload";
    }
}
