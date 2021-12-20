package com.kazuki.replaceobject_v2;

public class VectorUtils {

  // Multiply Vector2 * Vector2
  public static void multiplyVector2(float[] dst, float[] v0, float[] v1) {
    dst[0] = v0[0] * v1[0];
    dst[1] = v0[1] * v1[1];
  }

  public static void normalizeVector3f(float[] vector) {
    float vv = dotVector3f(vector,vector);
    double root = Math.sqrt(vv);
    vector[0] = (float)(vector[0]/root);
    vector[1] = (float)(vector[1]/root);
    vector[2] = (float)(vector[2]/root);
  }

  public static float dotVector3f(float[] a, float[] b) {
    return a[0] * b[0] + a[1] * b[1] + a[2] * b[2];
  }
}
