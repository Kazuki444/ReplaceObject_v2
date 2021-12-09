package com.kazuki.replaceobject_v2.myrender;

import android.opengl.Matrix;

import com.google.ar.core.LightEstimate;

import java.io.IOException;
import java.util.HashMap;

public class VirtualObjectRenderer {
  private static final String TAG = BackgroundRenderer.class.getSimpleName();

  private final Mesh virtualObjectMesh;
  private final Texture virtualObjectAlbedoTexture;
  private final Texture virtualObjectPbrTexture;
  private Shader virtualObjectShader;


  /**
   * Allocates and initialized OpenGL resources needed by the Object renderer.
   */
  public VirtualObjectRenderer(
          MyRender render, String objectFileName, String albedoTextureFileName, String pbrTextureFileName)
          throws IOException{
    virtualObjectMesh = Mesh.createFromAsset(render, objectFileName);
    virtualObjectAlbedoTexture =
            Texture.createFromAsset(
                    render,
                    albedoTextureFileName,
                    Texture.WrapMode.CLAMP_TO_EDGE,
                    Texture.ColorFormat.SRGB);
    virtualObjectPbrTexture =
            Texture.createFromAsset(
                    render,
                    pbrTextureFileName,
                    Texture.WrapMode.CLAMP_TO_EDGE,
                    Texture.ColorFormat.LINEAR);
    virtualObjectShader =
            Shader.createFromAssets(
                    render,
                    "shader/environmental_hdr.vert",
                    "shader/environmental_hdr.frag",
                    null)
                    .setTexture("u_AlbedoTexture", virtualObjectAlbedoTexture)
                    .setTexture("u_RoughnessMetallicAmbientOcclusionTexture", virtualObjectPbrTexture);

  }

  // Update shader uniform about light.
  public void updateLightEstimation(
          LightEstimate lightEstimate ,float[] viewInverseMatrix,
          float[] viewLightDirection, float[] intensity, float[] sphericalHarmonicsCoefficients){
    if (lightEstimate.getState() != LightEstimate.State.VALID) {
      virtualObjectShader.setBool("u_LightEstimateIsValid", false);
      return;
    }
    virtualObjectShader.setBool("u_LightEstimateIsValid", true);
    virtualObjectShader.setMat4("u_ViewInverse", viewInverseMatrix);
    virtualObjectShader.setVec4("u_ViewLightDirection", viewLightDirection);
    virtualObjectShader.setVec3("u_LightIntensity", intensity);
    virtualObjectShader.setVec3Array(
            "u_SphericalHarmonicsCoefficients", sphericalHarmonicsCoefficients);
  }

  // Update shader uniform about model view.
  public void updateModelView(float[] modelViewMatrix, float[] modelViewProjectionMatrix){
    virtualObjectShader.setMat4("u_ModelView", modelViewMatrix);
    virtualObjectShader.setMat4("u_ModelViewProjection", modelViewProjectionMatrix);
  }

  public Mesh getVirtualObjectMesh() {
    return virtualObjectMesh;
  }

  public Shader getVirtualObjectShader() {
    return virtualObjectShader;
  }
}
