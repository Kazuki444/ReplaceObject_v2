package com.kazuki.replaceobject_v2;

import android.graphics.Bitmap;
import android.graphics.Color;
import android.graphics.Matrix;
import android.media.Image;
import android.util.Log;

import java.nio.ByteBuffer;
import java.nio.IntBuffer;
import java.sql.Array;

public class ImageUtils {
  // This value is 2 ^ 18 - 1, and is used to clamp the RGB values before their ranges
  // are normalized to eight bits.
  private static final int kMaxChannelValue = 262143;

  private static int YUV2RGB(int y, int u, int v) {
    // Adjust and check YUV values
    y = (y - 16) < 0 ? 0 : (y - 16);
    u -= 128;
    v -= 128;

    // This is the floating point equivalent. We do the conversion in integer
    // because some Android devices do not have floating point in hardware.
    // nR = (int)(1.164 * nY + 2.018 * nU);
    // nG = (int)(1.164 * nY - 0.813 * nV - 0.391 * nU);
    // nB = (int)(1.164 * nY + 1.596 * nV);
    int y1192 = 1192 * y;
    int r = (y1192 + 1634 * v);
    int g = (y1192 - 833 * v - 400 * u);
    int b = (y1192 + 2066 * u);

    // Clipping RGB values to be inside boundaries [ 0 , kMaxChannelValue ]
    r = r > kMaxChannelValue ? kMaxChannelValue : (r < 0 ? 0 : r);
    g = g > kMaxChannelValue ? kMaxChannelValue : (g < 0 ? 0 : g);
    b = b > kMaxChannelValue ? kMaxChannelValue : (b < 0 ? 0 : b);

    return 0xff000000 | ((r << 6) & 0xff0000) | ((g >> 2) & 0xff00) | ((b >> 10) & 0xff);
  }

  public static void convertYUV420ToARGB8888(
          byte[] yData,
          byte[] uData,
          byte[] vData,
          int width,
          int height,
          int yRowStride,
          int uvRowStride,
          int uvPixelStride,
          int[] out) {
    int yp = 0;
    for (int j = 0; j < height; j++) {
      int pY = yRowStride * j;
      int pUV = uvRowStride * (j >> 1);

      for (int i = 0; i < width; i++) {
        int uv_offset = pUV + (i >> 1) * uvPixelStride;

        out[yp++] = YUV2RGB(0xff & yData[pY + i], 0xff & uData[uv_offset], 0xff & vData[uv_offset]);
      }
    }
  }

  public static Matrix getTransformationMatrix(
          final int srcWidth,
          final int srcHeight,
          final int applyRotation) {
    final Matrix matrix = new Matrix();

    if (applyRotation == 90 || applyRotation == 270) {
      matrix.postTranslate(-srcWidth / 2.0f, -srcHeight / 2.0f);
      matrix.postRotate(180);
      matrix.postTranslate(srcWidth / 2.0f, srcHeight / 2.0f);
    }

    return matrix;
  }

  /*********************************************************/
  public static void inpaintCpuImage(short[] clusterMap, int[] clusterMapSize, int planeNum, int[] depthImageLocation,
                                     int[] rgbByte, int[] cpuImageSize, int[] cpuImageLocation) {
    // calc plane average color
    int[] averageColors = calcPlaneAverageColor(clusterMap, clusterMapSize, planeNum, depthImageLocation, rgbByte, cpuImageSize);

    // inpaint cpuImage
    for (int y = 60; y < 420; y++) {
      for (int x = 0; x < cpuImageSize[0]; x++) {
        if (cpuImageLocation[1] <= y && y <= cpuImageLocation[3]
                && cpuImageLocation[0] <= x && x <= cpuImageLocation[2]) {
          int planeIndex = nearestNeighborPositionDepthToCpu(x, y - 60, 4, clusterMap, clusterMapSize);
          if (planeIndex == -1) {
            rgbByte[y * cpuImageSize[0] + x] = Color.rgb(255, 255, 255);
            continue;
          }
          int color = averageColors[planeIndex];
          rgbByte[y * cpuImageSize[0] + x] = color;
        }

      }
    }
  }

  public static int[] calcPlaneAverageColor(short[] clusterMap, int[] clusterMapSize, int planeNum, int[] depthImageLocation, int[] rgbByte, int[] cpuImageSize) {
    int[][] colors = new int[planeNum][3];
    int[] nums = new int[planeNum];

    int[] color = new int[3];
    for (int y = 0; y < clusterMapSize[1]; y++) {
      for (int x = 0; x < clusterMapSize[0]; x++) {
        int position = y * clusterMapSize[0] + x;

        int planeIndex = clusterMap[position];

        if (planeIndex == -1) continue;
        if (depthImageLocation[1] <= y && y <= depthImageLocation[3]
                && depthImageLocation[0] <= x && x <= depthImageLocation[2]) continue;

        nearestNeighborPositionCpuToDepth(color, x, y, 0.25f, rgbByte, cpuImageSize);
        colors[planeIndex][0] += color[0];
        colors[planeIndex][1] += color[1];
        colors[planeIndex][2] += color[2];
        nums[planeIndex] += 1;
      }
    }

    int[] averageColors = new int[planeNum];
    for (int i = 0; i < planeNum; i++) {
      if(nums[i]==0){
        averageColors[i]=Color.rgb(255,255,255);
        continue;
      }
      int r = (int) (colors[i][0] / nums[i]);
      int g = (int) (colors[i][1] / nums[i]);
      int b = (int) (colors[i][2] / nums[i]);
      averageColors[i] = Color.rgb(r, g, b);
    }
    return averageColors;
  }

  public static void nearestNeighborPositionCpuToDepth(int[] color, int depthX, int depthY, float ratio, int[] rgbByte, int[] cpuImageSize) {
    int cpuX = Math.round(depthX / ratio);
    int cpuY = Math.round(depthY / ratio);
    int argb = rgbByte[cpuY * cpuImageSize[0] + cpuX];

    color[0] = (argb >> 16) & 0xff;
    color[1] = (argb >> 8) & 0xff;
    color[2] = (argb) & 0xff;
  }

  public static int nearestNeighborPositionDepthToCpu(int cpuX, int cpuY, float ratio, short[] clusterMap, int[] clusterMapSize) {
    int depthX = Math.round(cpuX / ratio);
    int depthY = Math.round(cpuY / ratio);
    if(depthX>=clusterMapSize[0]) depthX=clusterMapSize[0]-1;
    if(depthY>=clusterMapSize[1]) depthY=clusterMapSize[1]-1;
    return clusterMap[depthY * clusterMapSize[0] + depthX];
  }

}
