package com.kazuki.replaceobject_v2.myrender;

import android.graphics.Bitmap;
import android.media.Image;
import android.opengl.GLES30;
import android.opengl.GLUtils;

import com.google.ar.core.Coordinates2d;
import com.google.ar.core.Frame;

import java.io.IOException;
import java.nio.ByteBuffer;
import java.nio.ByteOrder;
import java.nio.FloatBuffer;
import java.util.HashMap;

/**
 * This class both renders the AR camera background and composes the a scene foreground. The camera
 * background can be rendered as either camera image data or camera depth data. The virtual scene
 * can be composited with or without depth occlusion.
 */
public class BackgroundRenderer {
  private static final String TAG = BackgroundRenderer.class.getSimpleName();

  // components_per_vertex * number_of_vertices * float_size
  private static final int COORDS_BUFFER_SIZE = 2 * 4 * 4;

  private static final FloatBuffer NDC_QUAD_COORDS_BUFFER =
          ByteBuffer.allocateDirect(COORDS_BUFFER_SIZE).order(ByteOrder.nativeOrder()).asFloatBuffer();

  private static final FloatBuffer VIRTUAL_SCENE_TEX_COORDS_BUFFER =
          ByteBuffer.allocateDirect(COORDS_BUFFER_SIZE).order(ByteOrder.nativeOrder()).asFloatBuffer();

  static {
    NDC_QUAD_COORDS_BUFFER.put(
            new float[]{
                    /*0:*/ -1f, -1f, /*1:*/ +1f, -1f, /*2:*/ -1f, +1f, /*3:*/ +1f, +1f,
            });
    VIRTUAL_SCENE_TEX_COORDS_BUFFER.put(
            new float[]{
                    /*0:*/ 0f, 0f, /*1:*/ 1f, 0f, /*2:*/ 0f, 1f, /*3:*/ 1f, 1f,
            });
  }

  private final FloatBuffer cameraTexCoords =
          ByteBuffer.allocateDirect(COORDS_BUFFER_SIZE).order(ByteOrder.nativeOrder()).asFloatBuffer();

  private final Mesh mesh;
  private final VertexBuffer cameraTexCoordsVertexBuffer;
  private Shader backgroundShader;
  private Shader occlusionShader;
  private final Texture cameraDepthTexture;
  private final Texture cameraColorTexture;
  private final Texture cpuImageTexture;

  private boolean useDepthVisualization;
  private float aspectRatio;

  /**
   * Allocates and initializes OpenGL resources needed by the background renderer.
   */
  public BackgroundRenderer(MyRender render) throws IOException {
    cameraColorTexture =
            new Texture(
                    render,
                    Texture.Target.TEXTURE_EXTERNAL_OES,
                    Texture.WrapMode.CLAMP_TO_EDGE,
                    /*useMipmaps=*/ false);
    cameraDepthTexture =
            new Texture(
                    render,
                    Texture.Target.TEXTURE_2D,
                    Texture.WrapMode.CLAMP_TO_EDGE,
                    /*useMipmaps=*/ false);

    cpuImageTexture =
            new Texture(
                    render,
                    Texture.Target.TEXTURE_2D,
                    Texture.WrapMode.CLAMP_TO_EDGE,
                    /*useMipmaps=*/ false);

    // Create a Mesh with three vertex buffers: one for the screen coordinates (normalized device
    // coordinates), one for the camera texture coordinates (to be populated with proper data later
    // before drawing), and one for the virtual scene texture coordinates (unit texture quad)
    VertexBuffer screenCoordsVertexBuffer =
            new VertexBuffer(render, /* numberOfEntriesPerVertex=*/ 2, NDC_QUAD_COORDS_BUFFER);
    cameraTexCoordsVertexBuffer =
            new VertexBuffer(render, /*numberOfEntriesPerVertex=*/ 2, /*entries=*/ null);
    VertexBuffer virtualSceneTexCoordsVertexBuffer =
            new VertexBuffer(render, /* numberOfEntriesPerVertex=*/ 2, VIRTUAL_SCENE_TEX_COORDS_BUFFER);
    VertexBuffer[] vertexBuffers = {
            screenCoordsVertexBuffer, cameraTexCoordsVertexBuffer, virtualSceneTexCoordsVertexBuffer,
    };
    mesh =
            new Mesh(render, Mesh.PrimitiveMode.TRIANGLE_STRIP, /*indexBuffer=*/ null, vertexBuffers);

    // use occlusion
    occlusionShader =
            Shader.createFromAssets(render, "shader/occlusion.vert", "shader/occlusion.frag", null)
                    .setDepthTest(false)
                    .setDepthWrite(false)
                    .setBlend(Shader.BlendFactor.SRC_ALPHA, Shader.BlendFactor.ONE_MINUS_SRC_ALPHA)
                    .setTexture("u_CameraDepthTexture", cameraDepthTexture)
                    .setFloat("u_DepthAspectRatio", aspectRatio);
  }

  /**
   * Sets whether the background camera image should be replaced with a depth visualization instead.
   * This reloads the corresponding shader code, and must be called on the GL thread.
   */
  public void setUseDepthVisualization(MyRender render, boolean useDepthVisualization)
          throws IOException {
    if (backgroundShader != null) {
      if (this.useDepthVisualization == useDepthVisualization) {
        return;
      }
      backgroundShader.close();
      backgroundShader = null;
      this.useDepthVisualization = useDepthVisualization;
    }
    if (useDepthVisualization) {
      backgroundShader =
              Shader.createFromAssets(
                      render,
                      "shader/background_show_depth_color_visualization.vert",
                      "shader/background_show_depth_color_visualization.frag",
                      /*defines=*/ null)
                      .setTexture("u_CameraDepthTexture", cameraDepthTexture)
                      .setDepthTest(false)
                      .setDepthWrite(false);
    } else {
      backgroundShader =
              Shader.createFromAssets(
                      render,
                      "shader/background_show_camera.vert",
                      "shader/background_show_camera.frag",
                      /*defines=*/ null)
                      .setTexture("u_CameraColorTexture", cameraColorTexture)
                      .setTexture("u_CpuImageTexture", cpuImageTexture)
                      .setDepthTest(false)
                      .setDepthWrite(false);
    }
  }

  /**
   * Updates the display geometry. This must be called every frame before calling either of
   * BackgroundRenderer's draw methods.
   */
  public void updateDisplayGeometry(Frame frame) {
    if (frame.hasDisplayGeometryChanged()) {
      // If display rotation changed (also includes view size change), we need to re-query the UV
      // coordinates for the screen rect, as they may have changed as well.
      frame.transformCoordinates2d(
              Coordinates2d.OPENGL_NORMALIZED_DEVICE_COORDINATES,
              NDC_QUAD_COORDS_BUFFER,
              Coordinates2d.TEXTURE_NORMALIZED,
              cameraTexCoords);
      cameraTexCoordsVertexBuffer.set(cameraTexCoords);
    }
  }

  /**
   * Update depth texture with Image contents.
   */
  public void updateCameraDepthTexture(Image image) {
    // MyRender abstraction leaks here
    GLES30.glBindTexture(GLES30.GL_TEXTURE_2D, cameraDepthTexture.getTextureId());
    GLES30.glTexImage2D(
            GLES30.GL_TEXTURE_2D,
            0,
            GLES30.GL_RG8,
            image.getWidth(),
            image.getHeight(),
            0,
            GLES30.GL_RG,
            GLES30.GL_UNSIGNED_BYTE,
            image.getPlanes()[0].getBuffer());

    aspectRatio = (float) image.getWidth() / (float) image.getHeight();
    occlusionShader.setFloat("u_DepthAspectRatio", aspectRatio);

  }

  /**
   * Draws the AR background image. The image will be drawn such that virtual content rendered with
   * the matrices provided by {@link com.google.ar.core.Camera#getViewMatrix(float[], int)} and
   * {@link com.google.ar.core.Camera#getProjectionMatrix(float[], int, float, float)} will
   * accurately follow static physical objects.
   */
  public void drawBackground(MyRender render) {
    render.draw(mesh, backgroundShader);
  }

  /**
   * Draws the virtual scene. Any objects rendered in the given {@link Framebuffer} will be drawn
   * given the previously specified {@link OcclusionMode}.
   *
   * <p>Virtual content should be rendered using the matrices provided by {@link
   * com.google.ar.core.Camera#getViewMatrix(float[], int)} and {@link
   * com.google.ar.core.Camera#getProjectionMatrix(float[], int, float, float)}.
   */
  public void drawVirtualScene(
          MyRender render, Framebuffer virtualSceneFramebuffer, float zNear, float zFar) {
    occlusionShader.setTexture(
            "u_VirtualSceneColorTexture", virtualSceneFramebuffer.getColorTexture());
    occlusionShader
            .setTexture("u_VirtualSceneDepthTexture", virtualSceneFramebuffer.getDepthTexture())
            .setFloat("u_ZNear", zNear)
            .setFloat("u_ZFar", zFar);
    render.draw(mesh, occlusionShader);
  }

  /**
   * Return the camera color texture generated by this object.
   */
  public Texture getCameraColorTexture() {
    return cameraColorTexture;
  }

  /**
   * Return the camera depth texture generated by this object.
   */
  public Texture getCameraDepthTexture() {
    return cameraDepthTexture;
  }

  public void updateCpuImageTexture(Bitmap rgbFrameBitmap){
    GLES30.glActiveTexture(GLES30.GL_TEXTURE1);
    GLES30.glBindTexture(GLES30.GL_TEXTURE_2D, cpuImageTexture.getTextureId());
    GLUtils.texImage2D(GLES30.GL_TEXTURE_2D,0,rgbFrameBitmap,0);
    GLES30.glActiveTexture(GLES30.GL_TEXTURE0);
    backgroundShader.setBool("u_isCpuImage", true);
  }
  public void no(){
    backgroundShader.setBool("u_isCpuImage", false);
  }
}
