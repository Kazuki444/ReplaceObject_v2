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
import java.util.ArrayList;
import java.util.Arrays;

public final class InpaintImage {
  private static final String TAG = InpaintImage.class.getSimpleName();

  // The parameters used for depth inpainting
  private static final int THETA_SLICE = 90;
  private static final int PHI_SLICE = 90;
  private static final int CONFIDENCE_THRESHOLD = 0;
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
    Arrays.fill(depthMask, false);
    DepthImageUtils.makeMask(depthMask, confidenceImageBytes, depthImageSize, location, CONFIDENCE_THRESHOLD);

    /**
     for (int y = 0; y < depthImageSize[1]; y++) {
     for (int x = 0; x < depthImageSize[0]; x++) {
     int position = depthImageSize[0] * y + x;
     if (depthMask[position]) depthArray[position] = 5000;
     }

     }*/

    // make normal map
    Arrays.fill(normalMap, 0f);
    DepthImageUtils.makeNormalMap(normalMap, depthArray, depthMask, depthImageSize, focalLength);

    // make cluster map and theta-phi-hist from normal map
    Arrays.fill(hist, 0);
    Arrays.fill(clusterMap, (short) 0);
    DepthImageUtils.calcClusterMapAndHist(clusterMap, hist, normalMap, depthMask, depthImageSize, THETA_SLICE, PHI_SLICE);

    // clustering hist
    ArrayList<int[]> clusterList = DepthImageUtils.quoits(hist, THETA_SLICE, PHI_SLICE, CORE_POINT_RADIUS, FREQUENCY_THRESHOLD, CORE_POINT_DIST);

    // inpaint depth image
    if (clusterList != null) {
      DepthImageUtils.inpaintDepthArray(depthArray, depthMask, clusterMap, clusterList, location, focalLength, principalPoint, depthImageSize, MASK_EXPAND);
    }
  }


}
