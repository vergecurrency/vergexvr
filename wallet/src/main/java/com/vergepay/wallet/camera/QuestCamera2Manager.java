package com.vergepay.wallet.camera;

import android.content.Context;
import android.graphics.ImageFormat;
import android.graphics.Rect;
import android.graphics.SurfaceTexture;
import android.hardware.camera2.CameraCaptureSession;
import android.hardware.camera2.CameraCharacteristics;
import android.hardware.camera2.CameraDevice;
import android.hardware.camera2.CameraManager;
import android.hardware.camera2.CaptureRequest;
import android.hardware.camera2.params.StreamConfigurationMap;
import android.media.Image;
import android.media.ImageReader;
import android.os.Build;
import android.os.Handler;
import android.util.Log;
import android.util.Size;
import android.view.Surface;
import android.view.TextureView;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.IOException;
import java.util.Arrays;

public final class QuestCamera2Manager {
    public interface Listener {
        void onCameraReady(@NonNull Rect frame, @NonNull Rect framePreview);

        void onPreviewFrame(@NonNull byte[] luminance, int width, int height);

        void onCameraError(@NonNull Exception error);
    }

    private static final String META_CAMERA_SOURCE_KEY = "com.meta.extra_metadata.camera_source";
    private static final byte META_CAMERA_SOURCE_PASSTHROUGH = 0;
    private static final int MIN_FRAME_SIZE = 180;
    private static final int MAX_FRAME_SIZE = 600;
    private static final String TAG = "QuestCamera2Manager";
    private static final int PREFERRED_WIDTH = 1280;
    private static final int PREFERRED_HEIGHT = 960;
    private static final int FALLBACK_WIDTH = 1280;
    private static final int FALLBACK_HEIGHT = 1280;

    private final Context context;
    private final TextureView previewView;
    private final Handler handler;
    private final Listener listener;

    @Nullable private CameraDevice cameraDevice;
    @Nullable private CameraCaptureSession captureSession;
    @Nullable private ImageReader imageReader;
    @Nullable private Surface previewSurface;

    private static final Logger log = LoggerFactory.getLogger(QuestCamera2Manager.class);

    public QuestCamera2Manager(@NonNull final Context context, @NonNull final TextureView previewView,
                               @NonNull final Handler handler, @NonNull final Listener listener) {
        this.context = context;
        this.previewView = previewView;
        this.handler = handler;
        this.listener = listener;
    }

    public static boolean isSupportedDevice() {
        return "Oculus".equalsIgnoreCase(Build.MANUFACTURER);
    }

    public void open() throws Exception {
        final CameraManager cameraManager = (CameraManager) context.getSystemService(Context.CAMERA_SERVICE);
        if (cameraManager == null) {
            throw new IOException("Camera service unavailable");
        }

        final String cameraId = findPassthroughCameraId(cameraManager);
        if (cameraId == null) {
            throw new IOException("No passthrough camera found");
        }
        Log.i(TAG, "Selected Quest scan camera " + cameraId);

        final CameraCharacteristics characteristics = cameraManager.getCameraCharacteristics(cameraId);
        final StreamConfigurationMap streamMap =
                characteristics.get(CameraCharacteristics.SCALER_STREAM_CONFIGURATION_MAP);
        if (streamMap == null) {
            throw new IOException("No stream configuration map for camera " + cameraId);
        }

        final Size previewSize = choosePreviewSize(streamMap.getOutputSizes(ImageFormat.YUV_420_888));
        if (previewSize == null) {
            throw new IOException("No preview size available for camera " + cameraId);
        }

        final SurfaceTexture surfaceTexture = previewView.getSurfaceTexture();
        if (surfaceTexture == null) {
            throw new IOException("Quest preview texture unavailable");
        }
        surfaceTexture.setDefaultBufferSize(previewSize.getWidth(), previewSize.getHeight());
        previewSurface = new Surface(surfaceTexture);

        final Rect surfaceFrame = new Rect(0, 0, Math.max(previewView.getWidth(), 1),
                Math.max(previewView.getHeight(), 1));
        final Rect frame = buildFrame(surfaceFrame);
        final Rect framePreview = buildPreviewFrame(frame, previewSize, surfaceFrame);

        listener.onCameraReady(frame, framePreview);

        imageReader = ImageReader.newInstance(previewSize.getWidth(), previewSize.getHeight(),
                ImageFormat.YUV_420_888, 2);
        imageReader.setOnImageAvailableListener(reader -> {
            final Image image = reader.acquireLatestImage();
            if (image == null) {
                return;
            }

            try {
                listener.onPreviewFrame(copyLuminancePlane(image), image.getWidth(), image.getHeight());
            } finally {
                image.close();
            }
        }, handler);

        log.info("opening Quest passthrough camera {} at {}x{}", cameraId,
                previewSize.getWidth(), previewSize.getHeight());
        Log.i(TAG, "Opening Quest scan camera " + cameraId + " at " + previewSize.getWidth() + "x"
                + previewSize.getHeight());
        cameraManager.openCamera(cameraId, new CameraDevice.StateCallback() {
            @Override
            public void onOpened(@NonNull final CameraDevice camera) {
                cameraDevice = camera;
                createCaptureSession(camera);
            }

            @Override
            public void onDisconnected(@NonNull final CameraDevice camera) {
                close();
            }

            @Override
            public void onError(@NonNull final CameraDevice camera, final int error) {
                close();
                listener.onCameraError(new IOException("Quest camera error " + error));
            }
        }, handler);
    }

    public void close() {
        if (captureSession != null) {
            try {
                captureSession.stopRepeating();
            } catch (final Exception ignored) {
            }
            captureSession.close();
            captureSession = null;
        }

        if (cameraDevice != null) {
            cameraDevice.close();
            cameraDevice = null;
        }

        if (imageReader != null) {
            imageReader.close();
            imageReader = null;
        }

        if (previewSurface != null) {
            previewSurface.release();
            previewSurface = null;
        }
    }

    private void createCaptureSession(@NonNull final CameraDevice camera) {
        final Surface currentPreviewSurface = previewSurface;
        if (imageReader == null || currentPreviewSurface == null || !currentPreviewSurface.isValid()) {
            final IOException error = new IOException("Preview surface unavailable");
            Log.e(TAG, error.getMessage(), error);
            listener.onCameraError(error);
            return;
        }

        try {
            final CaptureRequest.Builder requestBuilder =
                    camera.createCaptureRequest(CameraDevice.TEMPLATE_PREVIEW);
            requestBuilder.addTarget(currentPreviewSurface);
            requestBuilder.addTarget(imageReader.getSurface());
            requestBuilder.set(CaptureRequest.CONTROL_MODE, CaptureRequest.CONTROL_MODE_AUTO);
            requestBuilder.set(CaptureRequest.CONTROL_AF_MODE, CaptureRequest.CONTROL_AF_MODE_OFF);

            camera.createCaptureSession(Arrays.asList(currentPreviewSurface, imageReader.getSurface()),
                    new CameraCaptureSession.StateCallback() {
                        @Override
                        public void onConfigured(@NonNull final CameraCaptureSession session) {
                            captureSession = session;
                            try {
                                session.setRepeatingRequest(requestBuilder.build(), null, handler);
                            } catch (final Exception x) {
                                Log.e(TAG, "Could not start passthrough preview", x);
                                listener.onCameraError(new IOException("Could not start passthrough preview", x));
                            }
                        }

                        @Override
                        public void onConfigureFailed(@NonNull final CameraCaptureSession session) {
                            Log.e(TAG, "Could not configure passthrough preview");
                            listener.onCameraError(new IOException("Could not configure passthrough preview"));
                        }
                    }, handler);
        } catch (final Exception x) {
            Log.e(TAG, "Could not create passthrough capture session", x);
            listener.onCameraError(new IOException("Could not create passthrough capture session", x));
        }
    }

    @Nullable
    private static String findPassthroughCameraId(@NonNull final CameraManager cameraManager) throws Exception {
        String fallbackBackCameraId = null;

        for (final String cameraId : cameraManager.getCameraIdList()) {
            final CameraCharacteristics characteristics = cameraManager.getCameraCharacteristics(cameraId);
            final Integer lensFacing = characteristics.get(CameraCharacteristics.LENS_FACING);
            final Byte cameraSource = getVendorByte(characteristics, META_CAMERA_SOURCE_KEY);
            Log.i(TAG, "Quest camera " + cameraId + " lens=" + lensFacing + " source=" + cameraSource);

            if (lensFacing != null && lensFacing == CameraCharacteristics.LENS_FACING_BACK
                    && fallbackBackCameraId == null) {
                fallbackBackCameraId = cameraId;
            }

            if (cameraSource != null && cameraSource == META_CAMERA_SOURCE_PASSTHROUGH) {
                return cameraId;
            }
        }

        return fallbackBackCameraId;
    }

    @Nullable
    private static Size choosePreviewSize(@Nullable final Size[] sizes) {
        if (sizes == null || sizes.length == 0) {
            return null;
        }

        for (final Size size : sizes) {
            if (size.getWidth() == PREFERRED_WIDTH && size.getHeight() == PREFERRED_HEIGHT) {
                return size;
            }
        }

        for (final Size size : sizes) {
            if (size.getWidth() == FALLBACK_WIDTH && size.getHeight() == FALLBACK_HEIGHT) {
                return size;
            }
        }

        return sizes[0];
    }

    private static Rect buildFrame(@NonNull final Rect surfaceFrame) {
        final int surfaceWidth = surfaceFrame.width();
        final int surfaceHeight = surfaceFrame.height();
        final int rawSize = Math.min(surfaceWidth * 2 / 3, surfaceHeight * 2 / 3);
        final int frameSize = Math.max(MIN_FRAME_SIZE, Math.min(MAX_FRAME_SIZE, rawSize));

        final int leftOffset = (surfaceWidth - frameSize) / 2;
        final int topOffset = (surfaceHeight - frameSize) / 2;
        return new Rect(leftOffset, topOffset, leftOffset + frameSize, topOffset + frameSize);
    }

    private static Rect buildPreviewFrame(@NonNull final Rect frame, @NonNull final Size previewSize,
                                          @NonNull final Rect surfaceFrame) {
        final int surfaceWidth = surfaceFrame.width();
        final int surfaceHeight = surfaceFrame.height();
        return new Rect(
                frame.left * previewSize.getWidth() / surfaceWidth,
                frame.top * previewSize.getHeight() / surfaceHeight,
                frame.right * previewSize.getWidth() / surfaceWidth,
                frame.bottom * previewSize.getHeight() / surfaceHeight
        );
    }

    @NonNull
    private static byte[] copyLuminancePlane(@NonNull final Image image) {
        final Image.Plane plane = image.getPlanes()[0];
        final int width = image.getWidth();
        final int height = image.getHeight();
        final int rowStride = plane.getRowStride();
        final byte[] luminance = new byte[width * height];

        final java.nio.ByteBuffer buffer = plane.getBuffer();
        for (int row = 0; row < height; row++) {
            buffer.position(row * rowStride);
            buffer.get(luminance, row * width, width);
        }

        return luminance;
    }

    @SuppressWarnings({"rawtypes", "unchecked"})
    @Nullable
    private static Byte getVendorByte(@NonNull final CameraCharacteristics characteristics,
                                      @NonNull final String keyName) {
        for (final CameraCharacteristics.Key<?> key : characteristics.getKeys()) {
            if (keyName.equals(key.getName())) {
                final Object value = characteristics.get((CameraCharacteristics.Key) key);
                if (value instanceof Byte) {
                    return (Byte) value;
                } else if (value instanceof byte[] && ((byte[]) value).length > 0) {
                    return ((byte[]) value)[0];
                }
            }
        }

        return null;
    }
}
