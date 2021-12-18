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
import java.nio.ByteOrder;
import java.nio.IntBuffer;
import java.nio.ShortBuffer;

public final class InpaintImage {
  private static final String TAG = InpaintImage.class.getSimpleName();

  private boolean isPrepareInpaintCpuImage = false;
  private boolean isPrepareInpaintDepthImage = false;

  public void inpaintCpuImage(int[] rgbByte, int imageWidth, int imageHeight, RectF location) {
    if(!isPrepareInpaintCpuImage) prepareInpaintCpuImage();
  }

  public void inpaintDepthImage(short[] depthArray,  int imageWidth, int imageHeight, RectF location) {
    if(!isPrepareInpaintDepthImage) prepareInpaintDepthImage();
  }

  private void prepareInpaintCpuImage(){
    isPrepareInpaintCpuImage = true;
  }

  private void prepareInpaintDepthImage(){
    isPrepareInpaintDepthImage=true;
  }


}
