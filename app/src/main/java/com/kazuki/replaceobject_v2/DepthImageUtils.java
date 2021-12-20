package com.kazuki.replaceobject_v2;

import android.util.Log;

import java.lang.reflect.Array;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collections;
import java.util.HashMap;
import java.util.Map;

public class DepthImageUtils {

  public static void makeMask(boolean[] mask, byte[] confidenceBytes,
                              int[] size, int[] locationIndex, int confThreshold) {
    int i = 0;
    for (byte conf : confidenceBytes) {
      if (conf < confThreshold) {
        mask[i++] = true;
        continue;
      }

      int y = i / size[0];
      int x = i % size[0];
      if (locationIndex[1] <= y && y <= locationIndex[3]
              && locationIndex[0] <= x && x <= locationIndex[2]) {
        mask[i++] = true;
        continue;
      }

      i += 1;
    }
  }

  public static void makeNormalMap(
          float[] normalMap, short[] depthArray, boolean[] mask, int[] size, float[] focalLength) {
    float[] normal = new float[3];
    for (int y = 0; y < size[1]; y++) {
      for (int x = 0; x < size[0]; x++) {
        if (mask[y * size[0] + x]) continue;
        computeNormal(normal, depthArray, size, focalLength, y, x);
        int position = (y * size[0] + x) * 3;
        normalMap[position + 0] = normal[0];
        normalMap[position + 1] = normal[1];
        normalMap[position + 2] = normal[2];
      }
    }
  }

  public static void calcClusterMapAndHist(
          short[] clusterMap, int[] hist, float[] normalMap, boolean[] mask, int[] size,
          int thetaSlice, int phiSlice) {
    hist[0] = -thetaSlice * phiSlice;
    for (int y = 0; y < size[1]; y++) {
      for (int x = 0; x < size[0]; x++) {
        if (mask[y * size[0] + x]) continue;
        int position = (y * size[0] + x) * 3;
        int cluster = vectorToCluster(normalMap, position, thetaSlice, phiSlice);
        hist[cluster] += 1;
        clusterMap[y * size[0] + x] = (short) cluster;
      }
    }
  }

  public static ArrayList<int[]> quoits(
          int[] hist, int thetaSlice, int phiSlice,
          int corePointRadius, int frequencyThreshold, int corePointDist) {

    ArrayList<Integer> candidates = new ArrayList<>();
    int clusterDigit = (int) (Math.log(thetaSlice * phiSlice) / Math.log(2) + 1);

    int i = 0;
    for (int frequency : hist) {
      if (frequency >= frequencyThreshold + 2) {
        int candidate = (frequency << clusterDigit) | i;
        candidates.add(candidate);
      }
      i += 1;
    }
    Collections.sort(candidates, Collections.reverseOrder());

    Map<Integer, int[]> corePoints = new HashMap<>();
    int toCluster = (int) (Math.pow(2, clusterDigit)) - 1;
    for (int candidate : candidates) {
      int corePoint = candidate & toCluster;
      if (hist[corePoint] != 0) {
        int[] belongCluster = findBelongCluster(
                hist, thetaSlice, phiSlice, corePointRadius, frequencyThreshold, corePointDist);
        corePoints.put(corePoint, belongCluster);
      }
    }



  }


  /****************************************************/

  public static void computeNormal(
          float[] normal, short[] depthArray, int[] size, float[] focalLength, int yIndex, int xIndex) {
    short dp = depthArray[yIndex * size[0] + xIndex];
    float outlierDistance = 0.2f * dp;
    int radius = 2;

    int countX = 0;
    int countY = 0;
    float correlationX = 0;
    float correlationY = 0;
    for (int dy = -radius; dy < radius + 1; dy++) {
      for (int dx = -radius; dx < radius + 1; dx++) {
        if (dy == 0 && dx == 0) continue;

        if (yIndex + dy < 0 || size[1] - 1 < yIndex + dy) continue;
        if (xIndex + dx < 0 || size[0] - 1 < xIndex + dx) continue;

        short dq = depthArray[(yIndex + dy) * size[0] + (xIndex + dx)];
        float distance = (float) (dq - dp);
        if (distance * distance > outlierDistance * outlierDistance) continue;

        if (dx != 0) {
          countX += 1;
          correlationX += distance / dx;
        }
        if (dy != 0) {
          countY += 1;
          correlationY += distance / dy;
        }
      }
    }

    if (countX == 0 && countY == 0) {
      normal[0] = 0;
      normal[1] = 0;
      normal[2] = 0;
    }

    float pixelSizeX = dp / focalLength[0];
    float pixelSizeY = dp / focalLength[1];
    normal[0] = countX != 0 ? correlationX / (pixelSizeX * countX) : 0;
    normal[1] = countY != 0 ? correlationY / (pixelSizeY * countY) : 0;
    normal[2] = -1.0f;

    VectorUtils.normalizeVector3f(normal);
  }

  public static int vectorToCluster(float[] normalMap, int position, int thetaSlice, int phiSlice) {
    double theta = Math.atan2(normalMap[position + 2], normalMap[position + 0]);
    double phi = Math.asin(normalMap[position + 1]);
    int histX = -(int) (theta * thetaSlice / Math.PI);
    int histY = (int) ((phi + Math.PI * 0.5) * phiSlice / Math.PI);
    return histY * thetaSlice + histX;
  }

  public static int[] findBelongCluster(
          int[] hist, int corePoint, int corePointRadius, int frequencyThreshold,
          int thetaSlice, int phiSlice) {
    int[] belongCluster = new int[(2 * corePointRadius + 1) * (2 * corePointRadius + 1)];
    int position = 0;
    int[] radian = new int[2];

    cluster2radian(radian, corePoint, thetaSlice);
    for (int y = radian[1] - corePointRadius; y < radian[1] + corePointRadius + 1; y++) {
      if (y < 0 || phiSlice - 1 < y) continue;
      for (int x = radian[0] - corePointRadius; x < radian[0] + corePointRadius + 1; x++) {
        if (x < 0 || thetaSlice - 1 < x) continue;
        int cluster = y * thetaSlice + x;
        if (hist[cluster] >= frequencyThreshold) belongCluster[position++] = cluster;
        hist[cluster] = 0;
      }
    }

    int[] array = Arrays.copyOfRange(belongCluster,0,position+1);
    return array;
  }

  public static void cluster2radian(int[] dst, int cluster, int thetaSlice) {
    dst[0] = cluster % thetaSlice;  // theta
    dst[1] = cluster / thetaSlice;  // phi
  }

  public static ArrayList<int[]> mergeCorePoint(Map<Integer,int[]> corePoints, int corePointDist, int thetaSlice){

    ArrayList<Integer> points = new ArrayList<>(corePoints.keySet());
    int pointNum=points.size();
    int dist = corePointDist*corePointDist;

    Map<Integer, int[]> adjList=new HashMap<>();
  }

}
