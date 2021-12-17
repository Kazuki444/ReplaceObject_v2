package com.kazuki.replaceobject_v2;

import android.graphics.Bitmap;
import android.graphics.Canvas;
import android.graphics.Color;
import android.graphics.Matrix;
import android.graphics.Paint;
import android.graphics.RectF;
import android.media.Image;
import android.os.SystemClock;
import android.util.Log;

import java.nio.ByteBuffer;

public final class InpaintImage {
  private static final String TAG = InpaintImage.class.getSimpleName();

  private Bitmap cpuImage;
  private boolean isCpuImageInit = false;
  private byte[][] yuvBytes = new byte[3][];
  private int[] cpuRgbBytes;

  private Bitmap depthImage;
  private boolean isDepthImageInit = false;
  private byte[] depth16Bytes;
  private int[] depthRgbBytes;

  public Bitmap inpaintCpuImage(Image image, RectF objectLocation) {
    if (!isCpuImageInit) initCpuImage(image);

    // Fill bytes.
    Image.Plane[] planes = image.getPlanes();
    for (int i = 0; i < planes.length; ++i) {
      ByteBuffer buffer = planes[i].getBuffer();
      buffer.get(yuvBytes[i]);
    }

    // yuv to rgb
    ImageUtils.convertYUV420ToARGB8888(
            yuvBytes[0],
            yuvBytes[1],
            yuvBytes[2],
            image.getWidth(),
            image.getHeight(),
            planes[0].getRowStride(),
            planes[1].getRowStride(),
            planes[1].getPixelStride(),
            cpuRgbBytes);
    cpuImage.setPixels(cpuRgbBytes, 0, image.getWidth(),
            0, 0, image.getWidth(), image.getHeight());

    // Inpaint cpu image.

    // check object detection result
    Canvas canvas = new Canvas(cpuImage);
    Paint paint = new Paint();
    paint.setColor(Color.argb(255, 255, 0, 255));
    paint.setStyle(Paint.Style.STROKE);
    canvas.drawRect(objectLocation, paint);


    // Update cpu Image.


    return cpuImage;
  }

  public Bitmap inpaintDepthImage(Image image) {
    if (!isDepthImageInit) initDepthImage(image);
    return depthImage;
  }

  private void initCpuImage(Image image) {
    int width = image.getWidth();
    int height = image.getHeight();
    cpuImage = Bitmap.createBitmap(width, height, Bitmap.Config.ARGB_8888);
    cpuRgbBytes = new int[width * height];
    Image.Plane[] planes = image.getPlanes();
    for (int i = 0; i < planes.length; ++i) {
      ByteBuffer buffer = planes[i].getBuffer();
      yuvBytes[i] = new byte[buffer.capacity()];
    }
    isCpuImageInit = true;
  }

  private void initDepthImage(Image image) {
    int width = image.getWidth();
    int height = image.getHeight();
    depthImage = Bitmap.createBitmap(width, height, Bitmap.Config.ARGB_8888);
    depthRgbBytes = new int[width * height];
    ByteBuffer buffer = image.getPlanes()[0].getBuffer();
    depth16Bytes = new byte[buffer.capacity()];
    isDepthImageInit = true;
  }

}
