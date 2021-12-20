package com.kazuki.replaceobject_v2;

import android.graphics.Bitmap;
import android.graphics.Canvas;
import android.graphics.Color;
import android.graphics.Paint;
import android.graphics.RectF;
import android.media.Image;
import android.util.Log;

import com.google.ar.core.Camera;
import com.google.ar.core.CameraIntrinsics;
import com.google.ar.core.Frame;
import com.google.ar.core.exceptions.NotYetAvailableException;

import java.nio.ByteBuffer;
import java.nio.ByteOrder;
import java.nio.ShortBuffer;

public class BackgroundTextureData {
  private static final String TAG = BackgroundTextureData.class.getSimpleName();

  // cpu image
  private final int[] cpuImageSize = new int[2];  // The order of values is {width, height}.
  private Bitmap cpuImageBitmap;
  private int[] cpuImageBytes;
  private byte[][] yuvBytes = new byte[3][];

  // depth image
  private final int[] depthImageSize = new int[2];  // The order of values is {width, height}.
  private short[] depthArray;
  private ShortBuffer depthImageBytes;

  // confidence image
  private byte[] confidenceImageBytes;

  private InpaintImage inpaintImage;

  // Camera info
  private final float[] focalLength = new float[2]; // The order of values is {fx,fy}.
  private final float[] principalPoint = new float[2];  // The order of values is {cx, cy}.


  public BackgroundTextureData(Camera camera, Frame frame) throws NotYetAvailableException {
    Image cpuImage = frame.acquireCameraImage();
    cpuImageSize[0] = cpuImage.getWidth();
    cpuImageSize[1] = cpuImage.getHeight();
    cpuImageBitmap = Bitmap.createBitmap(cpuImageSize[0], cpuImageSize[1], Bitmap.Config.ARGB_8888);
    cpuImageBytes = new int[cpuImageSize[0] * cpuImageSize[1]];
    Image.Plane[] planes = cpuImage.getPlanes();
    for (int i = 0; i < planes.length; ++i) {
      ByteBuffer buffer = planes[i].getBuffer();
      yuvBytes[i] = new byte[buffer.capacity()];
    }

    Image depthImage = frame.acquireDepthImage();
    depthImageSize[0] = depthImage.getWidth();
    depthImageSize[1] = depthImage.getHeight();
    depthImageBytes = ShortBuffer.allocate(depthImageSize[0] * depthImageSize[1]);
    depthArray = new short[depthImageSize[0] * depthImageSize[1]];

    Image confidenceImage = frame.acquireRawDepthConfidenceImage();
    confidenceImageBytes = new byte[depthImageSize[0] * depthImageSize[1]];

    CameraIntrinsics cameraIntrinsics = camera.getTextureIntrinsics();
    float[] scale = {
            depthImageSize[0] / (float) cameraIntrinsics.getImageDimensions()[0],
            depthImageSize[1] / (float) cameraIntrinsics.getImageDimensions()[1]
    };
    VectorUtils.multiplyVector2(focalLength, scale, cameraIntrinsics.getFocalLength());
    VectorUtils.multiplyVector2(principalPoint, scale, cameraIntrinsics.getPrincipalPoint());

    inpaintImage = new InpaintImage(depthImageSize);
  }

  public void set(Frame frame) {
    try (Image cpuImage = frame.acquireCameraImage();
         Image depthImage = frame.acquireDepthImage();
         Image confidenceImage = frame.acquireRawDepthConfidenceImage()) {
      // -- set cpuImageBytes

      // Fill bytes.
      Image.Plane[] planes = cpuImage.getPlanes();
      for (int i = 0; i < planes.length; ++i) {
        ByteBuffer buffer = planes[i].getBuffer();
        buffer.get(yuvBytes[i]);
      }

      // YUV to RGB
      ImageUtils.convertYUV420ToARGB8888(
              yuvBytes[0],
              yuvBytes[1],
              yuvBytes[2],
              cpuImageSize[0],
              cpuImageSize[1],
              planes[0].getRowStride(),
              planes[1].getRowStride(),
              planes[1].getPixelStride(),
              cpuImageBytes);

      // -- set depthImageBytes

      // Depth16 to Short
      depthImage.getPlanes()[0].getBuffer().order(ByteOrder.nativeOrder()).asShortBuffer().get(depthArray);

      // -- set confidenceImageBytes
      confidenceImage.getPlanes()[0].getBuffer().order(ByteOrder.nativeOrder()).get(confidenceImageBytes);

    } catch (NotYetAvailableException e) {
      // This normally means that cpu image data is not available yet. This is normal so we will not
      // spam the logcat with this.
    }
  }

  public void update(RectF location, int mlNum) {
    // Update cpu image data
    cpuImageBitmap.setPixels(cpuImageBytes, 0, cpuImageSize[0],
            0, 0, cpuImageSize[0], cpuImageSize[1]);

    // check object detection result
    if (mlNum != 0) {
      Canvas canvas = new Canvas(cpuImageBitmap);
      Paint paint = new Paint();
      paint.setColor(Color.argb(255, 255, 0, 255));
      paint.setStyle(Paint.Style.STROKE);
      canvas.drawRect(location, paint);
    }

    // Update depth image data
    depthImageBytes.clear();
    depthImageBytes.put(depthArray);
    depthImageBytes.flip();
  }

  public void inpaintCpuImage(int[] location) {
    inpaintImage.inpaintCpuImage(cpuImageBytes, cpuImageSize, location);
  }

  public void inpaintDepthImage(int[] location) {
    inpaintImage.inpaintDepthImage(depthArray, depthImageSize, location, confidenceImageBytes, focalLength, principalPoint);
  }

  public Bitmap getCpuImageBitmap() {
    return cpuImageBitmap;
  }

  public ShortBuffer getDepthImageBytes() {
    return depthImageBytes;
  }

  public int getDepthImageWidth() {
    return depthImageSize[0];
  }

  public int getDepthImageHeight() {
    return depthImageSize[1];
  }

  public float[] getFocalLength() {
    return focalLength;
  }

  public float[] getPrincipalPoint() {
    return principalPoint;
  }
}
