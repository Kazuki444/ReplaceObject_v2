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

  // The parameters used for depth inpainting
  private static final int THETA_SLICE = 90;
  private static final int PHI_SLICE = 90;
  private static final int CONFIDENCE_THRESHOLD = 255 - 128;
  private static final int FREQUENCY_THRESHOLD = 5;
  private static final int CORE_POINT_RADIUS = 2;
  private static final int CORE_POINT_DIST = 10;
  private static final int MASK_EXPAND = 10;

  // Allocate memory
  private final boolean[] depthMask;
  private final float[] normalMap;
  private final int[] hist = new int[THETA_SLICE * PHI_SLICE];
  private final short[] clusterMap;

  public InpaintImage(int[] depthImageSize) {
    depthMask = new boolean[depthImageSize[0] * depthImageSize[1]];
    normalMap = new float[depthImageSize[0] * depthImageSize[1] * 3];
    clusterMap = new short[depthImageSize[0] * depthImageSize[1]];
  }

  public void inpaintCpuImage(int[] rgbByte, int[] cpuImageSize, int[] location) {

  }

  public void inpaintDepthImage(
          short[] depthArray, int[] depthImageSize,
          int[] location, byte[] confidenceImageBytes,
          float[] focalLength, float[] principalPoint) {

    // make depth mask
    DepthImageUtils.makeMask(depthMask,confidenceImageBytes,depthImageSize,location,CONFIDENCE_THRESHOLD);

    // make normal map
    DepthImageUtils.makeNormalMap(normalMap,depthArray,depthMask,depthImageSize,focalLength);

    // make cluster map and theta-phi-hist from normal map
    DepthImageUtils.calcClusterMapAndHist(
            clusterMap,hist,normalMap,depthMask,depthImageSize,THETA_SLICE,PHI_SLICE);

    // clustering hist

    // inpaint depth image

  }


}
