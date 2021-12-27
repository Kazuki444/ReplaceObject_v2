package com.kazuki.replaceobject_v2;

import android.util.Log;
import android.view.MotionEvent;

import com.google.ar.core.Camera;
import com.google.ar.core.Pose;

import java.lang.reflect.Array;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collections;
import java.util.HashMap;
import java.util.Map;

public class DepthImageUtils {

  public static void makeMask(boolean[] mask, byte[] confidenceBytes,
                              int[] size, int[] locationIndex, int confThreshold) {
    for (int y = 0; y < size[1]; y++) {
      for (int x = 0; x < size[0]; x++) {
        int position = y * size[0] + x;
        if (confidenceBytes[position] != -1) {
          mask[position] = true;
          continue;
        }
        if (locationIndex[1] <= y && y <= locationIndex[3]
                && locationIndex[0] <= x && x <= locationIndex[2]) {
          mask[position] = true;
        }
      }
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

    Map<Integer, ArrayList<Integer>> corePoints = new HashMap<>();
    int toCluster = (int) (Math.pow(2, clusterDigit)) - 1;
    for (int candidate : candidates) {
      int corePoint = candidate & toCluster;
      if (hist[corePoint] != 0) {
        ArrayList<Integer> belongCluster = findBelongCluster(hist, corePoint, corePointRadius, frequencyThreshold, thetaSlice, phiSlice);
        corePoints.put(corePoint, belongCluster);
      }
    }

    if (corePoints.size() == 0) return null;

    ArrayList<int[]> clusterList = mergeCorePoint(corePoints, corePointDist, thetaSlice);

    return clusterList.size() != 0 ? clusterList : null;
  }

  public static void inpaintDepthArray(
          short[] depthArray, boolean[] mask, short[] clusterMap, ArrayList<int[]> clusterList,
          int[] location, float[] focalLength, float[] principalPoint, int[] size, int expand) {
    float[][] planes = estimatePlanes(depthArray, mask, focalLength, principalPoint, location, size, clusterMap, clusterList, expand);
    /**
     for (int y = location[1]; y < location[3] + 1; y++) {
     for (int x = location[0]; x < location[2] + 1; x++) {
     int position = y * size[0] + x;
     short originalZ = depthArray[position];
     depthArray[position] = inpaintDepth(originalZ, x, y, planes, focalLength, principalPoint, clusterMap);
     }
     }*/
    Arrays.fill(clusterMap, (short) -1);
    for (int y = 0; y < size[1]; y++) {
      for (int x = 0; x < size[0]; x++) {
        int position = y * size[0] + x;
        short originalZ = depthArray[position];
        if (location[1] <= y && y <= location[3] && location[0] <= x && x <= location[2]) {
          depthArray[position] = inpaintDepth(originalZ, x, y, planes, focalLength, principalPoint);
          clusterMap[position] = whichPlane(originalZ, x, y, planes, focalLength, principalPoint);
        } else if (mask[position]) {
          continue;
        }
        clusterMap[position] = whichPlane(originalZ, x, y, planes, focalLength, principalPoint);
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

  public static ArrayList<Integer> findBelongCluster(
          int[] hist, int corePoint, int corePointRadius, int frequencyThreshold,
          int thetaSlice, int phiSlice) {
    ArrayList<Integer> belongCluster = new ArrayList<>();
    int[] radian = new int[2];

    cluster2radian(radian, corePoint, thetaSlice);
    for (int y = radian[1] - corePointRadius; y < radian[1] + corePointRadius + 1; y++) {
      if (y < 0 || phiSlice - 1 < y) continue;
      for (int x = radian[0] - corePointRadius; x < radian[0] + corePointRadius + 1; x++) {
        if (x < 0 || thetaSlice - 1 < x) continue;
        int cluster = y * thetaSlice + x;
        if (hist[cluster] >= frequencyThreshold) belongCluster.add(cluster);
        hist[cluster] = 0;
      }
    }

    return belongCluster;
  }

  public static void cluster2radian(int[] dst, int cluster, int thetaSlice) {
    dst[0] = cluster % thetaSlice;  // theta
    dst[1] = cluster / thetaSlice;  // phi
  }

  public static ArrayList<int[]> mergeCorePoint(Map<Integer, ArrayList<Integer>> corePoints, int corePointDist, int thetaSlice) {

    ArrayList<Integer> points = new ArrayList<>(corePoints.keySet());
    int pointNum = points.size();
    int dist = corePointDist * corePointDist;

    Map<Integer, ArrayList<Integer>> adjList = new HashMap<>();
    int[] radian0 = new int[2];
    int[] radian1 = new int[2];
    for (int key : points) {
      ArrayList<Integer> list = new ArrayList<>();
      adjList.put(key, list);
    }
    for (int i = 0; i < pointNum - 1; i++) {
      cluster2radian(radian0, points.get(i), thetaSlice);
      for (int j = i + 1; j < pointNum; j++) {
        cluster2radian(radian1, points.get(j), thetaSlice);
        int dTheta = radian0[0] - radian1[0];
        int dPhi = radian0[1] - radian1[1];
        int d = dTheta * dTheta + dPhi * dPhi;
        if (d < dist) {
          adjList.get(points.get(i)).add(points.get(j));
          adjList.get(points.get(j)).add(points.get(i));
        }
      }
    }

    boolean[] f = new boolean[pointNum];

    ArrayList<ArrayList<Integer>> mergedPoints = new ArrayList<>();
    for (int point : points) {
      ArrayList<Integer> cluster = new ArrayList<>();
      dfs(point, points, adjList, f, cluster);
      if (cluster.size() > 0) mergedPoints.add(cluster);
    }

    ArrayList<int[]> clusterList = new ArrayList<>();
    for (ArrayList<Integer> ps : mergedPoints) {
      ArrayList<Integer> indexList = new ArrayList<>();
      for (int key : ps) {
        indexList.addAll(corePoints.get(key));
      }
      clusterList.add(toPrimitive(indexList));
    }

    return clusterList;
  }


  public static void dfs(int point, ArrayList<Integer> points, Map<Integer, ArrayList<Integer>> adjList,
                         boolean[] f, ArrayList<Integer> cluster) {
    int i = points.indexOf(point);
    if (!f[i]) {
      f[i] = true;
      cluster.add(point);
      for (int p : adjList.get(point)) {
        int j = points.indexOf(p);
        if (!f[j]) dfs(p, points, adjList, f, cluster);
      }
    }
  }

  public static float[][] estimatePlanes(
          short[] depthArray, boolean[] mask, float[] focalLength, float[] principalPoint,
          int[] location, int[] size, short[] clusterMap, ArrayList<int[]> clusterList, int expand) {
    int clusterNum = clusterList.size();
    int[] clustersCount = new int[clusterNum];
    float[][] clustersElementSum = new float[clusterNum][8]; //[x, y, z, xx, yy, xy, yz, zx]

    for (int[] list : clusterList) Arrays.sort(list);

    boolean[] exist = new boolean[1];
    int[] clusterIndex = new int[1];
    for (int y = 0; y < size[1]; y++) {
      for (int x = 0; x < size[0]; x++) {
        int position = y * size[0] + x;
        if (mask[position]) continue;

        if (location[1] - expand <= y && y <= location[3] + expand
                && location[0] - expand <= x && x <= location[2] + expand) {
          continue;
        }

        exist[0] = false;
        clusterIndex[0] = 0;
        binarySearch(exist, clusterIndex, clusterList, clusterMap[position]);

        if (exist[0]) {
          clustersCount[clusterIndex[0]] += 1;
          calcClusterElementSum(clustersElementSum, clusterIndex[0], depthArray[position], x, y, focalLength, principalPoint);
        }
      }
    }

    float[][] param = new float[clusterNum][3];
    calcPlaneParam(param, clustersElementSum, clustersCount, clusterNum);

    return param;
  }

  public static void binarySearch(boolean[] exist, int[] clusterIndex, ArrayList<int[]> clusterList, short findElement) {
    for (int[] cluster : clusterList) {
      if (findElement < cluster[0] || cluster[cluster.length - 1] < findElement) {
        clusterIndex[0] += 1;
        continue;
      }

      int left = 0;
      int mid = 0;
      int right = cluster.length;
      while (left < right) {
        mid = (int) ((left + right) * 0.5);
        if (findElement == cluster[mid]) {
          exist[0] = true;
          break;
        }
        if (findElement < cluster[mid]) right = mid;
        if (findElement > cluster[mid]) left = mid + 1;
      }

      if (exist[0]) break;

      clusterIndex[0] += 1;
    }
  }

  public static void calcClusterElementSum(
          float[][] clustersElementSum, int clusterIndex, short depth, int xIndex, int yIndex,
          float[] focalLength, float[] principalPoint) {
    float cameraSpacePointX = depth * (xIndex - principalPoint[0]) / focalLength[0];
    float cameraSpacePointY = depth * (yIndex - principalPoint[1]) / focalLength[1];
    float cameraSpacePointZ = (float) depth;
    clustersElementSum[clusterIndex][0] += cameraSpacePointX;
    clustersElementSum[clusterIndex][1] += cameraSpacePointY;
    clustersElementSum[clusterIndex][2] += cameraSpacePointZ;
    clustersElementSum[clusterIndex][3] += cameraSpacePointX * cameraSpacePointX;
    clustersElementSum[clusterIndex][4] += cameraSpacePointY * cameraSpacePointY;
    clustersElementSum[clusterIndex][5] += cameraSpacePointX * cameraSpacePointY;
    clustersElementSum[clusterIndex][6] += cameraSpacePointY * cameraSpacePointZ;
    clustersElementSum[clusterIndex][7] += cameraSpacePointZ * cameraSpacePointX;
  }

  public static void calcPlaneParam(
          float[][] param, float[][] clustersElementSum, int[] clustersCount, int clusterNum) {
    for (int i = 0; i < clusterNum; i++) {
      int num = clustersCount[i];
      float averageX = clustersElementSum[i][0] / num;
      float averageY = clustersElementSum[i][1] / num;
      float averageZ = clustersElementSum[i][2] / num;
      float varianceXX = clustersElementSum[i][3] / num - averageX * averageX;
      float varianceYY = clustersElementSum[i][4] / num - averageY * averageY;
      float varianceXY = clustersElementSum[i][5] / num - averageX * averageY;
      float varianceYZ = clustersElementSum[i][6] / num - averageY * averageZ;
      float varianceZX = clustersElementSum[i][7] / num - averageZ * averageX;

      float b = (varianceZX * varianceYY - varianceXY * varianceYZ) / (varianceXX * varianceYY - varianceXY * varianceXY);
      float c = (varianceXX * varianceYZ - varianceXY * varianceZX) / (varianceXX * varianceYY - varianceXY * varianceXY);
      float a = -b * averageX - c * averageY + averageZ;

      param[i][0] = a;
      param[i][1] = b;
      param[i][2] = c;
    }
  }

  public static short inpaintDepth(
          short originalZ, int xIndex, int yIndex, float[][] planes,
          float[] focalLength, float[] principalPoint) {
    float inpaintZ = 8000;

    for (float[] plane : planes) {
      float ff = focalLength[0] * focalLength[1];
      float a = plane[0];
      float xcx = xIndex - principalPoint[0];
      float bfy = plane[1] * focalLength[1];
      float ycy = yIndex - principalPoint[1];
      float cfx = plane[2] * focalLength[0];
      float z = -a * ff / (-ff + xcx * bfy + ycy * cfx);

      float error = 300f;
      if (z > originalZ - error) {
        if (z < inpaintZ) {
          inpaintZ = z;
        }
      }
    }

    return (short) inpaintZ;
  }

  public static short whichPlane(
          short originalZ, int xIndex, int yIndex, float[][] planes,
          float[] focalLength, float[] principalPoint) {
    float inpaintZ = 8000;
    short planeIndex = -1;

    short index = 0;
    for (float[] plane : planes) {
      float ff = focalLength[0] * focalLength[1];
      float a = plane[0];
      float xcx = xIndex - principalPoint[0];
      float bfy = plane[1] * focalLength[1];
      float ycy = yIndex - principalPoint[1];
      float cfx = plane[2] * focalLength[0];
      float z = -a * ff / (-ff + xcx * bfy + ycy * cfx);

      float error = 300f;
      if (z > originalZ - error) {
        if (z < inpaintZ) {
          inpaintZ = z;
          planeIndex=index;
        }
      }

      index+=1;
    }

    return planeIndex;
  }

  /*******************************************************************/
  public static int[] toPrimitive(ArrayList<Integer> list) {
    final int[] result = new int[list.size()];
    for (int i = 0; i < result.length; i++) result[i] = list.get(i);
    return result;
  }

  /********************************************************************/

  public static Pose getPoseInWorldSpaceFromScreenTap(
          Camera camera, float[] screenUV, int[] depthImageSize, short[] depthArray, float[] focalLength, float[] principalPoint){
    float[] transform = getVertexInWorldSpaceFromScreenUV(camera, screenUV, depthImageSize, depthArray, focalLength, principalPoint);
    float[] quaternion = getRotationQuaternionInWorldSpaceFromScreenUV(screenUV, camera,depthImageSize, depthArray,focalLength);
    if (quaternion == null) {
      return null;
    }
    Pose pose = new Pose(transform, quaternion);
    return pose;
  }

  public static float[] getVertexInWorldSpaceFromScreenUV(
          Camera camera,float[] screenUV, int[] depthImageSize, short[] depthArray, float[] focalLength, float[] principalPoint) {
    int[] depthImageXY = screenToDepthXY(screenUV, depthImageSize);
    float depth = getDepthFromXY(depthImageXY, depthImageSize, depthArray);
    float[] vertex = computeVertex(depthImageXY[0], depthImageXY[1], depth,focalLength,principalPoint);
    return transformVertexToWorldSpace(vertex, camera);
  }

  // Convert the screen UV coordinates to depth image XY coordinates
  public static int[] screenToDepthXY(float[] screenUV, int[] depthImageSize) {
    int[] depthXY = {
            (int) (screenUV[0] * (depthImageSize[0] - 1)),
            (int) (screenUV[1] * (depthImageSize[1] - 1))
    };
    return depthXY;
  }

  // Obtain the depth value in meters at the specified x,y location
  public static float getDepthFromXY(int[] depthImageXY, int[] depthImageSize, short[] depthArray) {
    int depthIndex = depthImageXY[1] * depthImageSize[0] + depthImageXY[0];
    short depthInShort = depthArray[depthIndex];
    float depthInMeter = depthInShort * 0.001f;
    return depthInMeter;
  }

  // Reprojects a depth point to 3D vertex given the depth image XY and depth z
  // return camera-space coordinate
  // https://mem-archive.com/2018/02/21/post-157/
  public static float[] computeVertex(int x, int y, float z, float[] focalLength, float[] principalPoint) {
    float[] vertex = new float[3];

    if (z > 0) {
      float vertex_x = (x - principalPoint[0]) * z / focalLength[0];
      float vertex_y = (y - principalPoint[1]) * z / focalLength[1];
      vertex[0] = vertex_x;
      vertex[1] = -vertex_y;
      vertex[2] = -z;
    }

    return vertex;
  }

  // Transform a camera-space vertex into world space
  public static float[] transformVertexToWorldSpace(float[] vertex, Camera camera) {
    camera.getPose().transformPoint(vertex, 0, vertex, 0);
    return vertex;
  }

  public static float[] getRotationQuaternionInWorldSpaceFromScreenUV(float[] screenUV, Camera camera, int[] depthImageSize, short[] depthArray, float[] focalLength) {
    float[] cameraSpaceNormal = computeNormalVectorFromDepthWeightedMeanGradient(screenUV,depthImageSize, depthArray, focalLength);
    if (cameraSpaceNormal == null) {
      return null;
    }
    float[] worldSpaceNormal = camera.getPose().rotateVector(cameraSpaceNormal);

    float[] quaternion = new float[4];
    // cosθ = Dot((0,1,0),(x,y,z) / |(0,1,0)||(x,y,z)|
    double theta = (float) Math.acos(worldSpaceNormal[1]);
    // Cross((0,1,0),(x,y,z))
    float[] rotateAxis = {worldSpaceNormal[2], 0, -worldSpaceNormal[0]};
    VectorUtils.normalizeVector3f(rotateAxis);
    quaternion[0] = rotateAxis[0] * (float) Math.sin(theta / 2);
    quaternion[1] = rotateAxis[1] * (float) Math.sin(theta / 2);
    quaternion[2] = rotateAxis[2] * (float) Math.sin(theta / 2);
    quaternion[3] = (float) Math.cos(theta / 2);
    return quaternion;
  }

  public static float[] computeNormalVectorFromDepthWeightedMeanGradient(float[] screenUV, int[] depthImageSize, short[] depthArray, float[] focalLength) {

    // Get tap coordinate and depth.
    int[] depthImageXY = screenToDepthXY(screenUV, depthImageSize);
    float dp = getDepthFromXY(depthImageXY, depthImageSize, depthArray);
    float outlierDistance = dp * 0.2f;

    int radius=2;

    // Determine whether the tap coordinate can calculate world-space pose.
    if (depthImageXY[0] <= radius || depthImageSize[0] - radius < depthImageXY[0]
            || depthImageXY[1] <= radius || depthImageSize[1] - radius < depthImageXY[1]) {
      return null;
    }

    // Iterates over neighbors to compute normal vector.
    int countX = 0;
    int countY = 0;
    float correlationX = 0;
    float correlationY = 0;


    for (int dy = -radius; dy <= radius; ++dy) {
      for (int dx = -radius; dx <= radius; ++dx) {
        // Self is not neigbor
        if (dx == 0 && dy == 0) {
          continue;
        }

        // Retrieves neighbor depth value
        int[] neighborXY = {depthImageXY[0] + dx, depthImageXY[1] + dy};
        float dq = getDepthFromXY(neighborXY, depthImageSize, depthArray);

        // Confidence is not currently being packed yet, so for now this hardcoded.
        float distance = dq - dp;
        if (distance == 0 || Math.abs(distance) > outlierDistance) {
          continue; // neighbor does not exist.
        }

        // Updates correlations in each dimension
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

    // Computes estimate of normal vector by finding weighted averages of the surface gradient in XY.
    // Note - convert camera-space coordinates from image-space coordinates.
    float[] normalVector = new float[3];
    float[] pixelSize = {dp / focalLength[0], dp / focalLength[1]};

    normalVector[0] = correlationX / (pixelSize[0] * countX);
    normalVector[1] = -correlationY / (pixelSize[1] * countY);
    normalVector[2] = 1.0f;

    VectorUtils.normalizeVector3f(normalVector);
    return normalVector;
  }

}
