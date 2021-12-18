package com.kazuki.replaceobject_v2;

import android.graphics.Bitmap;
import android.graphics.Canvas;
import android.graphics.Color;
import android.graphics.Paint;
import android.graphics.RectF;
import android.media.Image;
import android.util.Log;

import com.google.ar.core.Frame;
import com.google.ar.core.exceptions.NotYetAvailableException;

import java.nio.ByteBuffer;
import java.nio.ByteOrder;
import java.nio.ShortBuffer;

public class BackgroundTextureData {
  private static final String TAG = BackgroundTextureData.class.getSimpleName();

  // cpu image
  private int cpuImageWidth;
  private int cpuImageHeight;
  private Bitmap cpuImageBitmap;
  private int[] cpuImageBytes;
  private byte[][] yuvBytes = new byte[3][];

  // depth image
  private int depthImageWidth;
  private int depthImageHeight;
  private short[] depthArray;
  private ShortBuffer depthImageBytes;

  InpaintImage inpaintImage = new InpaintImage();

  public BackgroundTextureData(Frame frame) throws NotYetAvailableException {
    Image cpuImage = frame.acquireCameraImage();
    Image depthImage = frame.acquireDepthImage();
    cpuImageWidth = cpuImage.getWidth();
    cpuImageHeight = cpuImage.getHeight();
    cpuImageBitmap = Bitmap.createBitmap(cpuImageWidth, cpuImageHeight, Bitmap.Config.ARGB_8888);
    cpuImageBytes = new int[cpuImageWidth * cpuImageHeight];
    Image.Plane[] planes = cpuImage.getPlanes();
    for (int i = 0; i < planes.length; ++i) {
      ByteBuffer buffer = planes[i].getBuffer();
      yuvBytes[i] = new byte[buffer.capacity()];
    }

    depthImageWidth = depthImage.getWidth();
    depthImageHeight = depthImage.getHeight();
    depthImageBytes = ShortBuffer.allocate(depthImageWidth * depthImageHeight);
    depthArray = new short[depthImageWidth * depthImageHeight];
  }

  public void set(Frame frame) {
    try (Image cpuImage = frame.acquireCameraImage();
         Image depthImage = frame.acquireDepthImage()) {
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
              cpuImageWidth,
              cpuImageHeight,
              planes[0].getRowStride(),
              planes[1].getRowStride(),
              planes[1].getPixelStride(),
              cpuImageBytes);

      // -- set depthImageBytes

      // Depth16 to Short
      depthImage.getPlanes()[0].getBuffer().order(ByteOrder.nativeOrder()).asShortBuffer().get(depthArray);

    } catch (NotYetAvailableException e) {
      // This normally means that cpu image data is not available yet. This is normal so we will not
      // spam the logcat with this.
    }
  }

  public void update(RectF location, int mlNum) {
    // Update cpu image data
    cpuImageBitmap.setPixels(cpuImageBytes, 0, cpuImageWidth,
            0, 0, cpuImageWidth, cpuImageHeight);

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

  public void inpaintCpuImage(RectF location) {
    inpaintImage.inpaintCpuImage(cpuImageBytes, cpuImageWidth, cpuImageHeight, location);
  }

  public void inpaintDepthImage(RectF location) {
    inpaintImage.inpaintDepthImage(depthArray, depthImageWidth, depthImageHeight, location);
  }

  public Bitmap getCpuImageBitmap() {
    return cpuImageBitmap;
  }

  public ShortBuffer getDepthImageBytes() {
    return depthImageBytes;
  }

  public int getDepthImageWidth() {
    return depthImageWidth;
  }

  public int getDepthImageHeight() {
    return depthImageHeight;
  }
}
