package com.flickerimageupload;

import android.Manifest;
import android.annotation.SuppressLint;
import android.app.Activity;
import android.content.ContentUris;
import android.content.DialogInterface;
import android.content.Intent;
import android.content.pm.PackageManager;
import android.database.Cursor;
import android.net.Uri;
import android.os.Build;
import android.os.Bundle;
import android.os.Environment;
import android.provider.DocumentsContract;
import android.provider.MediaStore;
import android.provider.Settings;
import android.support.annotation.NonNull;
import android.support.v4.app.ActivityCompat;
import android.support.v4.content.ContextCompat;
import android.support.v7.app.AlertDialog;
import android.support.v7.app.AppCompatActivity;
import android.util.Log;
import android.view.View;
import android.widget.TextView;
import android.widget.Toast;

import net.gotev.uploadservice.MultipartUploadRequest;
import net.gotev.uploadservice.ServerResponse;
import net.gotev.uploadservice.UploadInfo;
import net.gotev.uploadservice.UploadNotificationConfig;
import net.gotev.uploadservice.UploadStatusDelegate;

import org.json.JSONException;
import org.json.JSONObject;

import java.io.FileNotFoundException;
import java.net.MalformedURLException;
import java.net.URISyntaxException;
import java.util.Locale;
import java.util.Map;

public class MainActivity extends AppCompatActivity {
    private static final String TAG = "MainActivity";
    private static final int PERMISSION_REQUEST_READ_EXTERNAL_STORAGE = 100;
    private static final int REQUEST_PERMISSION_STORAGE_SETTING = 101;
    private static final int REQUEST_TAKE_IMAGE_FROM_GALLERY = 102;

    private TextView imageUploadedPathView;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_main);

        imageUploadedPathView = (TextView) findViewById(R.id.image_path);
    }

    public void takeImage(View v){
        if(checkIfStorageAccessPermissionsGranted()) {
            takeImageFromGallery();
        }
    }

    private void takeImageFromGallery() {
        Intent intent = new Intent();
        if (Build.VERSION.SDK_INT >= 19) {
            // For Android versions of KitKat or later, we use a
            // different intent to ensure
            // we can get the file path from the returned intent URI
            intent.setAction(Intent.ACTION_OPEN_DOCUMENT);
            intent.addCategory(Intent.CATEGORY_OPENABLE);
            intent.putExtra(Intent.EXTRA_LOCAL_ONLY, true);
        } else {
            intent.setAction(Intent.ACTION_GET_CONTENT);
        }

        intent.setType("image/*");
        startActivityForResult(intent, REQUEST_TAKE_IMAGE_FROM_GALLERY);
    }

    /**
     * Check if external storage read permission have been granted.
     * @return {@code true} if permissions grated, {@code false} otherwise
     */
    private boolean checkIfStorageAccessPermissionsGranted() {
        if (Build.VERSION.SDK_INT < Build.VERSION_CODES.M)
            return true;
        if (ContextCompat.checkSelfPermission(this, Manifest.permission.READ_EXTERNAL_STORAGE) == PackageManager.PERMISSION_GRANTED)
            return true;
        // Should we show an explanation?
        if (ActivityCompat.shouldShowRequestPermissionRationale(this, Manifest.permission.READ_EXTERNAL_STORAGE)) {
            new AlertDialog.Builder(this)
                    .setTitle("Storage access")
                    .setMessage("Storage access required to take image from the gallery")
                    .setCancelable(false)
                    .setPositiveButton(android.R.string.ok, new DialogInterface.OnClickListener() {
                        @Override
                        public void onClick(DialogInterface dialog, int which) {
                            requestStoragePermission();
                        }
                    }).create().show();
        } else {
            // No explanation needed, we can request the permission.
            requestStoragePermission();
        }
        return false;
    }

    private void requestStoragePermission() {
        ActivityCompat.requestPermissions(this,
                new String[]{Manifest.permission.READ_EXTERNAL_STORAGE},
                PERMISSION_REQUEST_READ_EXTERNAL_STORAGE);
    }


    @Override
    public void onRequestPermissionsResult(int requestCode, @NonNull String[] permissions, @NonNull int[] grantResults) {
        super.onRequestPermissionsResult(requestCode, permissions, grantResults);
        switch (requestCode){
            case PERMISSION_REQUEST_READ_EXTERNAL_STORAGE:
                // If request is cancelled, the result arrays are empty.
                if (grantResults.length > 0 && grantResults[0] == PackageManager.PERMISSION_GRANTED) {
                    // permission was granted,
                    takeImageFromGallery();
                } else {
                    // permission denied!
                    // next time use opens the app show him the empty screen with message to enable the location settings
                    new AlertDialog.Builder(this)
                            .setMessage("Please enable storage access.")
                            .setPositiveButton("Go to settings", new DialogInterface.OnClickListener() {
                                @Override
                                public void onClick(DialogInterface dialog, int which) {
                                    Intent intent = new Intent(Settings.ACTION_APPLICATION_DETAILS_SETTINGS);
                                    Uri uri = Uri.fromParts("package", getPackageName(), null);
                                    intent.setData(uri);
                                    startActivityForResult(intent, REQUEST_PERMISSION_STORAGE_SETTING);
                                }
                            })
                            .setNegativeButton("Exit", new DialogInterface.OnClickListener() {
                                @Override
                                public void onClick(DialogInterface dialog, int which) {
                                    finish();
                                }
                            }).create().show();
                }
                break;
        }
    }

    @Override
    protected void onActivityResult(int requestCode, int resultCode, Intent data) {
        if (resultCode == Activity.RESULT_OK) {
            if(requestCode == REQUEST_TAKE_IMAGE_FROM_GALLERY) {
                Uri uri = data.getData();
                try {
                    String path = getPath(uri);
                    beginUpload(path);
                } catch (URISyntaxException e) {
                    Toast.makeText(this,
                            "Unable to get the file from the given URI.  See error log for details",
                            Toast.LENGTH_LONG).show();
                    Log.e(TAG, "Unable to upload file from the given uri", e);
                }
            }else if(requestCode == REQUEST_PERMISSION_STORAGE_SETTING){
                ActivityCompat.requestPermissions(this,
                        new String[]{Manifest.permission.READ_EXTERNAL_STORAGE},
                        PERMISSION_REQUEST_READ_EXTERNAL_STORAGE);
            }
        }
    }

    private UploadNotificationConfig getNotificationConfig(String filename) {
        return new UploadNotificationConfig()
                .setIcon(R.drawable.ic_upload)
                .setTitle(filename)
                .setInProgressMessage(getString(R.string.uploading))
                .setCompletedMessage(getString(R.string.upload_success))
                .setErrorMessage(getString(R.string.upload_error))
                .setAutoClearOnSuccess(false)
                .setClickIntent(new Intent(this, MainActivity.class))
                .setClearOnAction(true)
                .setRingToneEnabled(true);
    }

    private void beginUpload(String path) {
        Log.d(TAG, "beginUpload: " + path);
        try {
            MultipartUploadRequest request = new MultipartUploadRequest(this, Constants.UPLOAD_IMAGE_URL);
            request.addHeader("Authorization", "Client-ID " + Constants.CLIENT_ID);
            request.addParameter("title", "Sample image title");
            request.addParameter("type", "file");
            request.addParameter("name", "Sample name");
            request.addParameter("description", "Sample description");
            request.addFileToUpload(path, "image");
            request.setNotificationConfig(getNotificationConfig(path));

            String uploadID = request.setDelegate(new UploadStatusDelegate() {
                @Override
                public void onProgress(UploadInfo uploadInfo) {
                    //Log.d(TAG, "onProgress() called with: " + "uploadInfo = [" + uploadInfo + "]");

                    Log.i(TAG, String.format(Locale.getDefault(), "ID: %1$s (%2$d%%) at %3$.2f Kbit/s",
                            uploadInfo.getUploadId(), uploadInfo.getProgressPercent(),
                            uploadInfo.getUploadRate()));
                }

                @Override
                public void onError(UploadInfo uploadInfo, Exception exception) {
                    //Log.d(TAG, "onError() called with: " + "uploadInfo = [" + uploadInfo + "], exception = [" + exception + "]");

                    Log.e(TAG, "Error with ID: " + uploadInfo.getUploadId() + ": "
                            + exception.getLocalizedMessage(), exception);
                }

                @Override
                public void onCompleted(UploadInfo uploadInfo, ServerResponse serverResponse) {
                    //Log.d(TAG, "onCompleted() called with: " + "uploadInfo = [" + uploadInfo + "], serverResponse = [" + serverResponse + "]");

                    Log.i(TAG, String.format(Locale.getDefault(),
                            "ID %1$s: completed in %2$ds at %3$.2f Kbit/s. Response code: %4$d, body:[%5$s]",
                            uploadInfo.getUploadId(), uploadInfo.getElapsedTime() / 1000,
                            uploadInfo.getUploadRate(), serverResponse.getHttpCode(),
                            serverResponse.getBodyAsString()));
                    for (Map.Entry<String, String> header : serverResponse.getHeaders().entrySet()) {
                        Log.i("Header", header.getKey() + ": " + header.getValue());
                    }

                    try {
                        JSONObject responseObject = new JSONObject(serverResponse.getBodyAsString());
                        JSONObject bodyObject = responseObject.getJSONObject("data");
                        final String uploadedImageUrl = bodyObject.getString("link");
                        runOnUiThread(new Runnable() {
                            @Override
                            public void run() {
                                imageUploadedPathView.setText("Image uploaded at: " + uploadedImageUrl);
                            }
                        });
                    } catch (JSONException e) {
                        e.printStackTrace();
                    }
                }

                @Override
                public void onCancelled(UploadInfo uploadInfo) {
                    Log.d(TAG, "onCancelled() called with: " + "uploadInfo = [" + uploadInfo + "]");
                }
            }).startUpload();
        } catch (FileNotFoundException e) {
            e.printStackTrace();
        } catch (MalformedURLException e) {
            e.printStackTrace();
        }
    }

    /*
     * Gets the file path of the given Uri.
     */
    @SuppressLint("NewApi")
    private String getPath(Uri uri) throws URISyntaxException {
        final boolean needToCheckUri = Build.VERSION.SDK_INT >= 19;
        String selection = null;
        String[] selectionArgs = null;
        // Uri is different in versions after KITKAT (Android 4.4), we need to
        // deal with different Uris.
        if (needToCheckUri && DocumentsContract.isDocumentUri(getApplicationContext(), uri)) {
            if (isExternalStorageDocument(uri)) {
                final String docId = DocumentsContract.getDocumentId(uri);
                final String[] split = docId.split(":");
                return Environment.getExternalStorageDirectory() + "/" + split[1];
            } else if (isDownloadsDocument(uri)) {
                final String id = DocumentsContract.getDocumentId(uri);
                uri = ContentUris.withAppendedId(
                        Uri.parse("content://downloads/public_downloads"), Long.valueOf(id));
            } else if (isMediaDocument(uri)) {
                final String docId = DocumentsContract.getDocumentId(uri);
                final String[] split = docId.split(":");
                final String type = split[0];
                if ("image".equals(type)) {
                    uri = MediaStore.Images.Media.EXTERNAL_CONTENT_URI;
                } else if ("video".equals(type)) {
                    uri = MediaStore.Video.Media.EXTERNAL_CONTENT_URI;
                } else if ("audio".equals(type)) {
                    uri = MediaStore.Audio.Media.EXTERNAL_CONTENT_URI;
                }
                selection = "_id=?";
                selectionArgs = new String[] {
                        split[1]
                };
            }
        }
        if ("content".equalsIgnoreCase(uri.getScheme())) {
            String[] projection = {
                    MediaStore.Images.Media.DATA
            };
            Cursor cursor = null;
            try {
                cursor = getContentResolver()
                        .query(uri, projection, selection, selectionArgs, null);
                int column_index = cursor.getColumnIndexOrThrow(MediaStore.Images.Media.DATA);
                if (cursor.moveToFirst()) {
                    return cursor.getString(column_index);
                }
            } catch (Exception e) {
            }
        } else if ("file".equalsIgnoreCase(uri.getScheme())) {
            return uri.getPath();
        }
        return null;
    }

    /**
     * @param uri The Uri to check.
     * @return Whether the Uri authority is ExternalStorageProvider.
     */
    public static boolean isExternalStorageDocument(Uri uri) {
        return "com.android.externalstorage.documents".equals(uri.getAuthority());
    }

    /**
     * @param uri The Uri to check.
     * @return Whether the Uri authority is DownloadsProvider.
     */
    public static boolean isDownloadsDocument(Uri uri) {
        return "com.android.providers.downloads.documents".equals(uri.getAuthority());
    }

    /**
     * @param uri The Uri to check.
     * @return Whether the Uri authority is MediaProvider.
     */
    public static boolean isMediaDocument(Uri uri) {
        return "com.android.providers.media.documents".equals(uri.getAuthority());
    }
}
