package com.vergepay.wallet.camera;


/*
 * Copyright 2012-2014 the original author or authors.
 *
 * This program is free software: you can redistribute it and/or modify
 * it under the terms of the GNU General Public License as published by
 * the Free Software Foundation, either version 3 of the License, or
 * (at your option) any later version.
 *
 * This program is distributed in the hope that it will be useful,
 * but WITHOUT ANY WARRANTY; without even the implied warranty of
 * MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE.  See the
 * GNU General Public License for more details.
 *
 * You should have received a copy of the GNU General Public License
 * along with this program.  If not, see <http://www.gnu.org/licenses/>.
 */


import java.io.IOException;
import java.util.ArrayList;
import java.util.Collection;
import java.util.Collections;
import java.util.Comparator;
import java.util.List;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import android.annotation.SuppressLint;
import android.graphics.Rect;
import android.hardware.Camera;
import android.hardware.Camera.CameraInfo;
import android.hardware.Camera.PreviewCallback;
import android.view.Surface;
import android.view.SurfaceHolder;

import com.google.zxing.PlanarYUVLuminanceSource;

/**
 * @author Andreas Schildbach
 */
public final class CameraManager
{
    private static final int MIN_FRAME_SIZE = 180; // FIXME, was 240 but was failing in low-res screens
    private static final int MAX_FRAME_SIZE = 600;
    private static final int MIN_PREVIEW_PIXELS = 470 * 320; // normal screen
    private static final int MAX_PREVIEW_PIXELS = 1280 * 720;

    private Camera camera;
    private Camera.Size cameraResolution;
    private Rect frame;
    private Rect framePreview;
    private int cameraId = -1;
    private int previewOrientationDegrees = 0;
    private Rect previewDisplayRect;

    private static final Logger log = LoggerFactory.getLogger(CameraManager.class);

    public Rect getFrame()
    {
        return frame;
    }

    public Rect getFramePreview()
    {
        return framePreview;
    }

    public Rect getPreviewDisplayRect()
    {
        return previewDisplayRect;
    }

    public Camera open(final SurfaceHolder holder, final boolean continuousAutoFocus,
            final int displayRotation, final int containerWidth, final int containerHeight) throws IOException
    {
        final int cameraCount = Camera.getNumberOfCameras();
        final CameraInfo cameraInfo = new CameraInfo();

        // Prefer a rear camera for QR scanning. On some XR devices the API1 default
        // camera is a front/avatar feed, which makes scanning unusable.
        for (int i = 0; i < cameraCount; i++)
        {
            Camera.getCameraInfo(i, cameraInfo);
            if (cameraInfo.facing == Camera.CameraInfo.CAMERA_FACING_BACK)
            {
                try
                {
                    camera = Camera.open(i);
                    cameraId = i;
                    log.info("opened back-facing camera {}", i);
                    break;
                }
                catch (final RuntimeException x)
                {
                    log.info("problem opening back-facing camera {}", i, x);
                }
            }
        }

        if (camera == null)
        {
            try
            {
                camera = Camera.open();
                cameraId = 0;
                log.info("opened default camera");
            }
            catch (final RuntimeException x)
            {
                log.info("problem opening default camera", x);
            }
        }

        // fall back to using front-facing camera
        if (camera == null)
        {
            for (int i = 0; i < cameraCount; i++)
            {
                Camera.getCameraInfo(i, cameraInfo);
                if (cameraInfo.facing == Camera.CameraInfo.CAMERA_FACING_FRONT)
                {
                    try
                    {
                        camera = Camera.open(i);
                        cameraId = i;
                        log.info("opened front-facing camera {}", i);
                        break;
                    }
                    catch (final RuntimeException x)
                    {
                        log.info("problem opening front-facing camera {}", i, x);
                    }
                }
            }
        }

        if (camera == null)
            throw new IOException("No camera available");

        camera.setPreviewDisplay(holder);
        setDisplayOrientation(displayRotation);

        final Camera.Parameters parameters = camera.getParameters();
        final Rect containerFrame = new Rect(0, 0, Math.max(containerWidth, 1), Math.max(containerHeight, 1));
        cameraResolution = findBestPreviewSizeValue(parameters, containerFrame);
        previewDisplayRect = buildPreviewDisplayRect(cameraResolution, containerFrame, previewOrientationDegrees);
        frame = buildFrame(previewDisplayRect);
        framePreview = buildFramePreview(frame, previewDisplayRect, cameraResolution, previewOrientationDegrees);

        final String savedParameters = parameters == null ? null : parameters.flatten();

        try
        {
            setDesiredCameraParameters(camera, cameraResolution, continuousAutoFocus);
        }
        catch (final RuntimeException x)
        {
            if (savedParameters != null)
            {
                final Camera.Parameters parameters2 = camera.getParameters();
                parameters2.unflatten(savedParameters);
                try
                {
                    camera.setParameters(parameters2);
                    setDesiredCameraParameters(camera, cameraResolution, continuousAutoFocus);
                }
                catch (final RuntimeException x2)
                {
                    log.info("problem setting camera parameters", x2);
                }
            }
        }

        camera.startPreview();

        return camera;
    }

    public void close()
    {
        if (camera != null)
        {
            camera.stopPreview();
            camera.release();
            camera = null;
            cameraId = -1;
        }
    }

    private void setDisplayOrientation(final int displayRotation)
    {
        if (camera == null || cameraId < 0)
            return;

        final CameraInfo cameraInfo = new CameraInfo();
        Camera.getCameraInfo(cameraId, cameraInfo);

        final int degrees;
        switch (displayRotation)
        {
            case Surface.ROTATION_90:
                degrees = 90;
                break;
            case Surface.ROTATION_180:
                degrees = 180;
                break;
            case Surface.ROTATION_270:
                degrees = 270;
                break;
            case Surface.ROTATION_0:
            default:
                degrees = 0;
                break;
        }

        final int result;
        if (cameraInfo.facing == CameraInfo.CAMERA_FACING_FRONT)
            result = (360 - ((cameraInfo.orientation + degrees) % 360)) % 360;
        else
            result = (cameraInfo.orientation - degrees + 360) % 360;

        previewOrientationDegrees = result;
        camera.setDisplayOrientation(result);
    }

    private static final Comparator<Camera.Size> numPixelComparator = new Comparator<Camera.Size>()
    {
        @Override
        public int compare(final Camera.Size size1, final Camera.Size size2)
        {
            final int pixels1 = size1.height * size1.width;
            final int pixels2 = size2.height * size2.width;

            if (pixels1 < pixels2)
                return 1;
            else if (pixels1 > pixels2)
                return -1;
            else
                return 0;
        }
    };

    private static Camera.Size findBestPreviewSizeValue(final Camera.Parameters parameters, Rect surfaceResolution)
    {
        if (surfaceResolution.height() > surfaceResolution.width())
            surfaceResolution = new Rect(0, 0, surfaceResolution.height(), surfaceResolution.width());

        final float screenAspectRatio = (float) surfaceResolution.width() / (float) surfaceResolution.height();

        final List<Camera.Size> rawSupportedSizes = parameters.getSupportedPreviewSizes();
        if (rawSupportedSizes == null)
            return parameters.getPreviewSize();

        // sort by size, descending
        final List<Camera.Size> supportedPreviewSizes = new ArrayList<Camera.Size>(rawSupportedSizes);
        Collections.sort(supportedPreviewSizes, numPixelComparator);

        Camera.Size bestSize = null;
        float diff = Float.POSITIVE_INFINITY;

        for (final Camera.Size supportedPreviewSize : supportedPreviewSizes)
        {
            final int realWidth = supportedPreviewSize.width;
            final int realHeight = supportedPreviewSize.height;
            final int realPixels = realWidth * realHeight;
            if (realPixels < MIN_PREVIEW_PIXELS || realPixels > MAX_PREVIEW_PIXELS)
                continue;

            final boolean isCandidatePortrait = realWidth < realHeight;
            final int maybeFlippedWidth = isCandidatePortrait ? realHeight : realWidth;
            final int maybeFlippedHeight = isCandidatePortrait ? realWidth : realHeight;
            if (maybeFlippedWidth == surfaceResolution.width() && maybeFlippedHeight == surfaceResolution.height())
                return supportedPreviewSize;

            final float aspectRatio = (float) maybeFlippedWidth / (float) maybeFlippedHeight;
            final float newDiff = Math.abs(aspectRatio - screenAspectRatio);
            if (newDiff < diff)
            {
                bestSize = supportedPreviewSize;
                diff = newDiff;
            }
        }

        if (bestSize != null)
            return bestSize;
        else
            return parameters.getPreviewSize();
    }

    @SuppressLint("InlinedApi")
    private static void setDesiredCameraParameters(final Camera camera, final Camera.Size cameraResolution, final boolean continuousAutoFocus)
    {
        final Camera.Parameters parameters = camera.getParameters();
        if (parameters == null)
            return;

        final List<String> supportedFocusModes = parameters.getSupportedFocusModes();
        final String focusMode = continuousAutoFocus ? findValue(supportedFocusModes, Camera.Parameters.FOCUS_MODE_CONTINUOUS_PICTURE,
                Camera.Parameters.FOCUS_MODE_CONTINUOUS_VIDEO, Camera.Parameters.FOCUS_MODE_AUTO, Camera.Parameters.FOCUS_MODE_MACRO) : findValue(
                supportedFocusModes, Camera.Parameters.FOCUS_MODE_AUTO, Camera.Parameters.FOCUS_MODE_MACRO);
        if (focusMode != null)
            parameters.setFocusMode(focusMode);

        parameters.setPreviewSize(cameraResolution.width, cameraResolution.height);

        camera.setParameters(parameters);
    }

    public void requestPreviewFrame(final PreviewCallback callback)
    {
        camera.setOneShotPreviewCallback(callback);
    }

    public PlanarYUVLuminanceSource buildLuminanceSource(final byte[] data)
    {
        return new PlanarYUVLuminanceSource(data, cameraResolution.width, cameraResolution.height, framePreview.left, framePreview.top,
                framePreview.width(), framePreview.height(), false);
    }

    private static Rect buildFrame(final Rect previewRect)
    {
        final int rawSize = Math.min(previewRect.width() * 2 / 3, previewRect.height() * 2 / 3);
        final int frameSize = Math.max(MIN_FRAME_SIZE, Math.min(MAX_FRAME_SIZE, rawSize));

        final int leftOffset = previewRect.left + (previewRect.width() - frameSize) / 2;
        final int topOffset = previewRect.top + (previewRect.height() - frameSize) / 2;
        return new Rect(leftOffset, topOffset, leftOffset + frameSize, topOffset + frameSize);
    }

    private static Rect buildPreviewDisplayRect(final Camera.Size cameraResolution, final Rect containerFrame,
            final int previewOrientationDegrees)
    {
        final boolean rotated = previewOrientationDegrees == 90 || previewOrientationDegrees == 270;
        final int previewWidth = rotated ? cameraResolution.height : cameraResolution.width;
        final int previewHeight = rotated ? cameraResolution.width : cameraResolution.height;

        final float previewAspect = (float) previewWidth / (float) previewHeight;
        final int containerWidth = containerFrame.width();
        final int containerHeight = containerFrame.height();

        final int displayWidth;
        final int displayHeight;
        if (containerWidth / previewAspect <= containerHeight)
        {
            displayWidth = containerWidth;
            displayHeight = Math.round(containerWidth / previewAspect);
        }
        else
        {
            displayHeight = containerHeight;
            displayWidth = Math.round(containerHeight * previewAspect);
        }

        final int left = containerFrame.left + (containerWidth - displayWidth) / 2;
        final int top = containerFrame.top + (containerHeight - displayHeight) / 2;
        return new Rect(left, top, left + displayWidth, top + displayHeight);
    }

    private static Rect buildFramePreview(final Rect frame, final Rect previewDisplayRect,
            final Camera.Size cameraResolution, final int previewOrientationDegrees)
    {
        final int sensorWidth = cameraResolution.width;
        final int sensorHeight = cameraResolution.height;
        final int displayWidth = previewDisplayRect.width();
        final int displayHeight = previewDisplayRect.height();

        final int x1 = frame.left - previewDisplayRect.left;
        final int y1 = frame.top - previewDisplayRect.top;
        final int x2 = frame.right - previewDisplayRect.left;
        final int y2 = frame.bottom - previewDisplayRect.top;

        switch (previewOrientationDegrees)
        {
            case 90:
                return new Rect(
                        y1 * sensorWidth / displayHeight,
                        sensorHeight - (x2 * sensorHeight / displayWidth),
                        y2 * sensorWidth / displayHeight,
                        sensorHeight - (x1 * sensorHeight / displayWidth));
            case 180:
                return new Rect(
                        sensorWidth - (x2 * sensorWidth / displayWidth),
                        sensorHeight - (y2 * sensorHeight / displayHeight),
                        sensorWidth - (x1 * sensorWidth / displayWidth),
                        sensorHeight - (y1 * sensorHeight / displayHeight));
            case 270:
                return new Rect(
                        sensorWidth - (y2 * sensorWidth / displayHeight),
                        x1 * sensorHeight / displayWidth,
                        sensorWidth - (y1 * sensorWidth / displayHeight),
                        x2 * sensorHeight / displayWidth);
            case 0:
            default:
                return new Rect(
                        x1 * sensorWidth / displayWidth,
                        y1 * sensorHeight / displayHeight,
                        x2 * sensorWidth / displayWidth,
                        y2 * sensorHeight / displayHeight);
        }
    }

    public void setTorch(final boolean enabled)
    {
        if (enabled != getTorchEnabled(camera))
            setTorchEnabled(camera, enabled);
    }

    private static boolean getTorchEnabled(final Camera camera)
    {
        final Camera.Parameters parameters = camera.getParameters();
        if (parameters != null)
        {
            final String flashMode = camera.getParameters().getFlashMode();
            return (Camera.Parameters.FLASH_MODE_ON.equals(flashMode) || Camera.Parameters.FLASH_MODE_TORCH.equals(flashMode));
        }

        return false;
    }

    private static void setTorchEnabled(final Camera camera, final boolean enabled)
    {
        final Camera.Parameters parameters = camera.getParameters();

        final List<String> supportedFlashModes = parameters.getSupportedFlashModes();
        if (supportedFlashModes != null)
        {
            final String flashMode;
            if (enabled)
                flashMode = findValue(supportedFlashModes, Camera.Parameters.FLASH_MODE_TORCH, Camera.Parameters.FLASH_MODE_ON);
            else
                flashMode = findValue(supportedFlashModes, Camera.Parameters.FLASH_MODE_OFF);

            if (flashMode != null)
            {
                camera.cancelAutoFocus(); // autofocus can cause conflict

                parameters.setFlashMode(flashMode);
                camera.setParameters(parameters);
            }
        }
    }

    private static String findValue(final Collection<String> values, final String... valuesToFind)
    {
        for (final String valueToFind : valuesToFind)
            if (values.contains(valueToFind))
                return valueToFind;

        return null;
    }
}
