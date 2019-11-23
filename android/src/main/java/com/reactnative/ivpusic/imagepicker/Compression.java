package com.reactnative.ivpusic.imagepicker;

import android.graphics.Bitmap;
import android.graphics.BitmapFactory;
import android.graphics.Matrix;
import android.media.ExifInterface;
import android.net.Uri;
import android.os.Environment;
import android.util.Log;

import androidx.annotation.NonNull;

import com.facebook.react.bridge.Promise;
import com.facebook.react.bridge.ReactContext;
import com.facebook.react.bridge.ReadableMap;
import com.reactnative.ivpusic.imagepicker.VideoCompressionTask.VideoCompressionListener;

import java.io.BufferedOutputStream;
import java.io.File;
import java.io.FileOutputStream;
import java.io.IOException;
import java.io.OutputStream;
import java.util.Arrays;
import java.util.List;
import java.util.UUID;

/**
 * Created by ipusic on 12/27/16.
 */

class Compression {

    File resize(String originalImagePath, int maxWidth, int maxHeight, int quality) throws IOException {
        Bitmap original = BitmapFactory.decodeFile(originalImagePath);

        int width = original.getWidth();
        int height = original.getHeight();

        // Use original image exif orientation data to preserve image orientation for the resized bitmap
        ExifInterface originalExif = new ExifInterface(originalImagePath);
        int originalOrientation = originalExif.getAttributeInt(ExifInterface.TAG_ORIENTATION, 1);

        Matrix rotationMatrix = new Matrix();
        int rotationAngleInDegrees = getRotationInDegreesForOrientationTag(originalOrientation);
        rotationMatrix.postRotate(rotationAngleInDegrees);

        float ratioBitmap = (float) width / (float) height;
        float ratioMax = (float) maxWidth / (float) maxHeight;

        int finalWidth = maxWidth;
        int finalHeight = maxHeight;

        if (ratioMax > 1) {
            finalWidth = (int) ((float) maxHeight * ratioBitmap);
        } else {
            finalHeight = (int) ((float) maxWidth / ratioBitmap);
        }

        Bitmap resized = Bitmap.createScaledBitmap(original, finalWidth, finalHeight, true);
        resized = Bitmap.createBitmap(resized, 0, 0, finalWidth, finalHeight, rotationMatrix, true);
        
        File imageDirectory = Environment.getExternalStoragePublicDirectory(
                Environment.DIRECTORY_PICTURES);

        if(!imageDirectory.exists()) {
            Log.d("image-crop-picker", "Pictures Directory is not existing. Will create this directory.");
            imageDirectory.mkdirs();
        }

        File resizeImageFile = new File(imageDirectory, UUID.randomUUID() + ".jpg");

        OutputStream os = new BufferedOutputStream(new FileOutputStream(resizeImageFile));
        resized.compress(Bitmap.CompressFormat.JPEG, quality, os);

        os.close();
        original.recycle();
        resized.recycle();

        return resizeImageFile;
    }

    int getRotationInDegreesForOrientationTag(int orientationTag) {
        switch(orientationTag){
            case ExifInterface.ORIENTATION_ROTATE_90:
                return 90;
            case ExifInterface.ORIENTATION_ROTATE_270:
                return -90;
            case ExifInterface.ORIENTATION_ROTATE_180:
                return 180;
            default:
                return 0;
        }
    }

    File compressImage(final ReadableMap options, final String originalImagePath, final BitmapFactory.Options bitmapOptions) throws IOException {
        Integer maxWidth = options.hasKey("compressImageMaxWidth") ? options.getInt("compressImageMaxWidth") : null;
        Integer maxHeight = options.hasKey("compressImageMaxHeight") ? options.getInt("compressImageMaxHeight") : null;
        Double quality = options.hasKey("compressImageQuality") ? options.getDouble("compressImageQuality") : null;

        boolean isLossLess = (quality == null || quality == 1.0);
        boolean useOriginalWidth = (maxWidth == null || maxWidth >= bitmapOptions.outWidth);
        boolean useOriginalHeight = (maxHeight == null || maxHeight >= bitmapOptions.outHeight);

        List knownMimes = Arrays.asList("image/jpeg", "image/jpg", "image/png", "image/gif", "image/tiff");
        boolean isKnownMimeType = (bitmapOptions.outMimeType != null && knownMimes.contains(bitmapOptions.outMimeType.toLowerCase()));

        if (isLossLess && useOriginalWidth && useOriginalHeight && isKnownMimeType) {
            Log.d("image-crop-picker", "Skipping image compression");
            return new File(originalImagePath);
        }

        Log.d("image-crop-picker", "Image compression activated");

        // compression quality
        int targetQuality = quality != null ? (int) (quality * 100) : 100;
        Log.d("image-crop-picker", "Compressing image with quality " + targetQuality);

        if (maxWidth == null) {
            maxWidth = bitmapOptions.outWidth;
        } else {
            maxWidth = Math.min(maxWidth, bitmapOptions.outWidth);
        }

        if (maxHeight == null) {
            maxHeight = bitmapOptions.outHeight;
        } else {
            maxHeight = Math.min(maxHeight, bitmapOptions.outHeight);
        }

        return resize(originalImagePath, maxWidth, maxHeight, targetQuality);
    }

    synchronized void compressVideo(final ReactContext reactContext, final ReadableMap options, final String originalVideo, final String compressedVideoPath, final Promise promise) {
        // todo: video compression
        // failed attempt 1: ffmpeg => slow and licensing issues

        final String inputUri = Uri.parse(originalVideo).getPath();
//        final File outputDir = reactContext.getCacheDir();

        final String outputUri = compressedVideoPath;

        final String quality = options.hasKey("compressVideoPreset") ? options.getString("compressVideoPreset") : "LowQuality";
        final long startTime = options.hasKey("startTime") ? (long) options.getDouble("startTime") : -1;
        final long endTime = options.hasKey("endTime") ? (long) options.getDouble("endTime") : -1;
//        cancelExistingTaskIfExists();

        try {
            VideoCompressionTask videoCompressTask = new VideoCompressionTask(
                    inputUri,
                    outputUri,
                    quality,
                    startTime,
                    endTime,
                    createListener(promise, outputUri, reactContext)
            );
            videoCompressTask.execute();
        } catch (Throwable e) {
//            Log.e(TAG, e.getMessage(), e);
        }

//        promise.resolve(originalVideo);
    }

    @NonNull
    private VideoCompressionListener createListener(final Promise pm, final String outputUri, final ReactContext reactContext) {
        return new VideoCompressionListener() {
            @Override
            public void onStart() {
                //Start Compress
                Log.d("INFO", "Compression started");
            }

            @Override
            public void onSuccess() {
                //Finish successfully
                pm.resolve(outputUri);
            }

            @Override
            public void onFail() {
                //Failed
                pm.reject("ERROR", "Failed to compress video");
            }

            @Override
            public void onProgress(float percent) {
                Log.d("INFO", "Compression started" + percent / 100);
//                sendProgress(reactContext, percent / 100);
            }
        };
    }
}
