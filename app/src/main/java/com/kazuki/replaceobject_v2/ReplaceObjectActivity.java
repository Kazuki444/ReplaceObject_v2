package com.kazuki.replaceobject_v2;

import androidx.annotation.NonNull;
import androidx.appcompat.app.AppCompatActivity;

import android.content.Intent;
import android.media.Image;
import android.opengl.GLSurfaceView;
import android.opengl.Matrix;
import android.os.Bundle;
import android.util.Log;
import android.view.MotionEvent;
import android.view.View;
import android.widget.Switch;
import android.widget.Toast;

import com.google.ar.core.Anchor;
import com.google.ar.core.ArCoreApk;
import com.google.ar.core.Camera;
import com.google.ar.core.Config;
import com.google.ar.core.DepthPoint;
import com.google.ar.core.Frame;
import com.google.ar.core.HitResult;
import com.google.ar.core.LightEstimate;
import com.google.ar.core.Plane;
import com.google.ar.core.Point;
import com.google.ar.core.Session;
import com.google.ar.core.Trackable;
import com.google.ar.core.TrackingState;
import com.google.ar.core.exceptions.CameraNotAvailableException;
import com.google.ar.core.exceptions.NotYetAvailableException;
import com.google.ar.core.exceptions.UnavailableApkTooOldException;
import com.google.ar.core.exceptions.UnavailableArcoreNotInstalledException;
import com.google.ar.core.exceptions.UnavailableDeviceNotCompatibleException;
import com.google.ar.core.exceptions.UnavailableSdkTooOldException;
import com.google.ar.core.exceptions.UnavailableUserDeclinedInstallationException;
import com.kazuki.replaceobject_v2.helper.CameraPermissionHelper;
import com.kazuki.replaceobject_v2.helper.DisplayRotationHelper;
import com.kazuki.replaceobject_v2.helper.FullScreenHelper;
import com.kazuki.replaceobject_v2.helper.ModelTableHelper;
import com.kazuki.replaceobject_v2.helper.GestureHelper;
import com.kazuki.replaceobject_v2.helper.TrackingStateHelper;
import com.kazuki.replaceobject_v2.myrender.BackgroundRenderer;
import com.kazuki.replaceobject_v2.myrender.Framebuffer;
import com.kazuki.replaceobject_v2.myrender.MyRender;
import com.kazuki.replaceobject_v2.myrender.VirtualObjectRenderer;

import java.io.IOException;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

/**
 * Replace a real object with a virtual object of the same class as the real object.
 */
public class ReplaceObjectActivity extends AppCompatActivity implements MyRender.Renderer {
  private static final String TAG = ReplaceObjectActivity.class.getSimpleName();

  private static final String MODEL_FILE_PATH = "model/";
  private static final String MODEL_TABLE = MODEL_FILE_PATH + "model_table.txt";

  private static final float Z_NEAR = 0.1f;
  private static final float Z_FAR = 100f;

  // See the definition of updateSphericalHarmonicsCoefficients for an explanation of these constants.
  private static final float[] sphericalHarmonicFactors = {
          0.282095f,
          -0.325735f,
          0.325735f,
          -0.325735f,
          0.273137f,
          -0.273137f,
          0.078848f,
          -0.273137f,
          0.136569f,
  };

  private GLSurfaceView surfaceView;

  // UI
  private Switch showDepthMapSwitch;
  private Switch inpaintSwitch;
  private final ArrayList<View> ui = new ArrayList<>();

  private boolean installRequested;

  private Session session;
  private GestureHelper gestureHelper;
  private DisplayRotationHelper displayRotationHelper;
  private final TrackingStateHelper trackingStateHelper = new TrackingStateHelper(this);
  private MyRender render;

  private BackgroundRenderer backgroundRenderer;
  private boolean isShowDepthMap = false;
  private Framebuffer virtualSceneFramebuffer;
  private boolean hasSetTextureNames = false;

  // Virtual object
  private final Map<String, VirtualObjectRenderer> virtualObjectRenderers = new HashMap<>();
  private final ArrayList<Anchor> anchors = new ArrayList<>();

  // Temporary matrix allocated here to reduce number of allocations for each frame.
  private final float[] modelMatrix = new float[16];
  private final float[] viewMatrix = new float[16];
  private final float[] projectionMatrix = new float[16];
  private final float[] modelViewMatrix = new float[16]; // view x model
  private final float[] modelViewProjectionMatrix = new float[16]; // projection x view x model
  private final float[] sphericalHarmonicsCoefficients = new float[9 * 3];
  private final float[] viewInverseMatrix = new float[16];
  private final float[] worldLightDirection = {0.0f, 0.0f, 0.0f, 0.0f};
  private final float[] viewLightDirection = new float[4]; // view x world light direction
  private final float[] lightIntensity = new float[3];  // main light intensity

  // from SelectItemActivity
  private String[] selectItems;
  private ModelTableHelper modelTableHelper;

  @Override
  protected void onCreate(Bundle savedInstanceState) {
    super.onCreate(savedInstanceState);
    setContentView(R.layout.activity_replace_object);
    surfaceView = findViewById(R.id.surfaceview);
    displayRotationHelper = new DisplayRotationHelper(/*context=*/ this);
    showDepthMapSwitch = findViewById(R.id.switch_showDepthMap);
    inpaintSwitch = findViewById(R.id.switch_inpaint);

    // set up touch listener
    ui.add(showDepthMapSwitch);
    ui.add(inpaintSwitch);
    gestureHelper = new GestureHelper(this, this, ui);
    surfaceView.setOnTouchListener(gestureHelper);

    // set up switch
    showDepthMapSwitch.setOnCheckedChangeListener((compoundButton, isChecked) -> {
      isShowDepthMap = isChecked ? true : false;
    });

    // set up renderer
    render = new MyRender(surfaceView, this, getAssets());

    installRequested = false;

    // get select items
    Intent intent = getIntent();
    selectItems = intent.getStringArrayExtra(SelectItemActivity.EXTRA_SELECT_ITEMS);
    try {
      modelTableHelper = new ModelTableHelper(this, MODEL_TABLE);
    } catch (IOException e) {
      Log.e(TAG, "Failed to read a required asset file", e);
    }

  }

  @Override
  protected void onDestroy() {
    if (session != null) {
      session.close();
      session = null;
    }

    super.onDestroy();
  }

  @Override
  protected void onResume() {
    super.onResume();

    if (session == null) {
      Exception exception = null;
      String message = null;
      try {
        switch (ArCoreApk.getInstance().requestInstall(this, !installRequested)) {
          case INSTALL_REQUESTED:
            installRequested = true;
            return;
          case INSTALLED:
            break;
        }

        // ARCore requires camera permissions to operate. If we did not yet obtain runtime
        // permission on Android M and above, now is a good time to ask the user for it.
        if (!CameraPermissionHelper.hasCameraPermission(this)) {
          CameraPermissionHelper.requestCameraPermission(this);
          return;
        }

        // Create the session.
        session = new Session(/* context= */ this);
      } catch (UnavailableArcoreNotInstalledException
              | UnavailableUserDeclinedInstallationException e) {
        message = "Please install ARCore";
        exception = e;
      } catch (UnavailableApkTooOldException e) {
        message = "Please update ARCore";
        exception = e;
      } catch (UnavailableSdkTooOldException e) {
        message = "Please update this app";
        exception = e;
      } catch (UnavailableDeviceNotCompatibleException e) {
        message = "This device does not support AR";
        exception = e;
      } catch (Exception e) {
        message = "Failed to create AR session";
        exception = e;
      }

      if (message != null) {
        Toast.makeText(this, message, Toast.LENGTH_LONG).show();
        return;
      }
    }

    try {
      configureSession();
      session.resume();
    } catch (CameraNotAvailableException e) {
      Log.e(TAG, "onResume: Camera not available. Try restarting the app.", e);
      session = null;
      return;
    }

    surfaceView.onResume();
    displayRotationHelper.onResume();
  }

  @Override
  protected void onPause() {
    super.onPause();
    if (session != null) ;
    {
      displayRotationHelper.onPause();
      surfaceView.onPause();
      session.pause();
    }
  }

  @Override
  public void onRequestPermissionsResult(int requestCode, @NonNull String[] permissions, @NonNull int[] grantResults) {
    super.onRequestPermissionsResult(requestCode, permissions, grantResults);
    if (!CameraPermissionHelper.hasCameraPermission(this)) {
      Toast.makeText(this, "Camera permission is needed to run this application", Toast.LENGTH_LONG)
              .show();
      if (!CameraPermissionHelper.shouldShowRequestPermissionRationale(this)) {
        // Permission denied with checking "Do not ask again".
        CameraPermissionHelper.launchPermissionSettings(this);
      }
      finish();
    }
  }

  @Override
  public void onWindowFocusChanged(boolean hasFocus) {
    super.onWindowFocusChanged(hasFocus);
    FullScreenHelper.setFullScreenOnWindowFocusChanged(this, hasFocus);
  }

  @Override
  public void onSurfaceCreated(MyRender render) {
    // Prepare the rendering objects.
    // This involves reading shader and 3d model files, so may throw an IOException.
    try {
      backgroundRenderer = new BackgroundRenderer(render);
      virtualSceneFramebuffer = new Framebuffer(render, /*width=*/ 1, /*height=*/ 1);
      for (String key : selectItems) {
        String[] fileNames = modelTableHelper.getFileName(key);
        if (fileNames != null) {
          VirtualObjectRenderer virtualObjectRenderer = new VirtualObjectRenderer(
                  render,
                  MODEL_FILE_PATH + fileNames[0],
                  MODEL_FILE_PATH + fileNames[1],
                  MODEL_FILE_PATH + fileNames[2]);
          virtualObjectRenderers.put(key, virtualObjectRenderer);
        }
      }
    } catch (IOException e) {
      Log.e(TAG, "Failed to read a required asset file", e);
    }

  }

  @Override
  public void onSurfaceChanged(MyRender render, int width, int height) {
    displayRotationHelper.onSurfaceChanged(width, height);
    virtualSceneFramebuffer.resize(width, height);
  }

  @Override
  public void onDrawFrame(MyRender render) {
    if (session == null) return;

    // Texture names should only be set once on a GL thread unless they change. This is done during
    // onDrawFrame rather than onSurfaceCreated since the session is not guaranteed to have been
    // initialized during the execution of onSurfaceCreated.
    if (!hasSetTextureNames) {
      session.setCameraTextureNames(
              new int[]{backgroundRenderer.getCameraColorTexture().getTextureId()});
      hasSetTextureNames = true;
    }

    // -- Update per-frame state

    // Notify ARCore session that the view size changed so that the perspective matrix and
    // the video background can be properly adjusted.
    displayRotationHelper.updateSessionIfNeeded(session);

    Frame frame;
    try {
      frame = session.update();
    } catch (CameraNotAvailableException e) {
      Log.e(TAG, "Camera not available during onDrawFrame", e);
      return;
    }
    Camera camera = frame.getCamera();

    // Update BackgroundRenderer state to match the depth settings.
    try {
      backgroundRenderer.setUseDepthVisualization(
              render, isShowDepthMap);
    } catch (IOException e) {
      Log.e(TAG, "Failed to read a required asset file", e);
      return;
    }
    // BackgroundRenderer.updateDisplayGeometry must be called every frame to update the coordinates
    // used to draw the background camera image.
    backgroundRenderer.updateDisplayGeometry(frame);

    if (camera.getTrackingState() == TrackingState.TRACKING) {
      try (Image depthImage = frame.acquireDepthImage()) {
        backgroundRenderer.updateCameraDepthTexture(depthImage);
      } catch (NotYetAvailableException e) {
        // This normally means that depth data is not available yet. This is normal so we will not
        // spam the logcat with this.
      }
    }

    // Handle one tap per frame.
    handleTap(frame, camera);

    // Keep the screen unlocked while tracking, but allow it to lock when tracking stops.
    trackingStateHelper.updateKeepScreenOnFlag(camera.getTrackingState());

    // -- Draw background

    if (frame.getTimestamp() != 0) {
      // Suppress rendering if the camera did not produce the first frame yet. This is to avoid
      // drawing possible leftover data from previous sessions if the texture is reused.
      backgroundRenderer.drawBackground(render);
    }

    // If not tracking, don't draw 3D objects.
    if (camera.getTrackingState() == TrackingState.PAUSED) {
      return;
    }

    // -- Draw occluded virtual objects

    // Get projection matrix.
    camera.getProjectionMatrix(projectionMatrix, 0, Z_NEAR, Z_FAR);

    // Get camera matrix and draw.
    camera.getViewMatrix(viewMatrix, 0);

    LightEstimate lightEstimate = frame.getLightEstimate();

    // Get light estimate matrix.
    getLightEstimation(lightEstimate, viewMatrix);

    // update lighting parameter in the shader
    virtualObjectRenderers.forEach((className, virtualObjectRenderer) ->
            virtualObjectRenderer.updateLightEstimation(
                    lightEstimate, viewInverseMatrix, viewLightDirection,
                    lightIntensity, sphericalHarmonicsCoefficients));

    // Visualize anchors created by touch.
    render.clear(virtualSceneFramebuffer, 0f, 0f, 0f, 0f);
    for (Anchor anchor : anchors) {
      if (anchor.getTrackingState() != TrackingState.TRACKING) {
        continue;
      }

      // Get the current pose of an Anchor in world space. The Anchor pose is updated
      // during calls to session.update() as ARCore refines its estimate of the world.
      anchor.getPose().toMatrix(modelMatrix, 0);

      // Calculate model/view/projection matrices
      Matrix.multiplyMM(modelViewMatrix, 0, viewMatrix, 0, modelMatrix, 0);
      Matrix.multiplyMM(modelViewProjectionMatrix, 0, projectionMatrix, 0, modelViewMatrix, 0);

      // Update shader properties and draw
      virtualObjectRenderers.forEach((className, virtualObjectRenderer) -> {
        virtualObjectRenderer.updateModelView(modelViewMatrix, modelViewProjectionMatrix);
        render.draw(virtualObjectRenderer.getVirtualObjectMesh(),
                virtualObjectRenderer.getVirtualObjectShader(), virtualSceneFramebuffer);
      });

    }

    // Compose the virtual scene with the background.
    backgroundRenderer.drawVirtualScene(render, virtualSceneFramebuffer, Z_NEAR, Z_FAR);


  }

  /**
   * Configures the session with feature settings.
   */
  private void configureSession() {
    Config config = session.getConfig();
    config.setLightEstimationMode(Config.LightEstimationMode.ENVIRONMENTAL_HDR);
    if (session.isDepthModeSupported(Config.DepthMode.AUTOMATIC)) {
      config.setDepthMode(Config.DepthMode.AUTOMATIC);
    } else {
      config.setDepthMode(Config.DepthMode.DISABLED);
    }
    config.setFocusMode(Config.FocusMode.FIXED);
    //config.setPlaneFindingMode(Config.PlaneFindingMode.DISABLED);
    session.configure(config);
  }

  // Handle only one tap per frame, as taps are usually low frequency compared to frame rate.
  private void handleTap(Frame frame, Camera camera) {
    MotionEvent tap = gestureHelper.poll();
    if (tap != null && camera.getTrackingState() == TrackingState.TRACKING) {
      List<HitResult> hitResultList;
      hitResultList = frame.hitTest(tap);
      for (HitResult hit : hitResultList) {
        // If any plane, Oriented Point, or Instant Placement Point was hit, create an anchor.
        Trackable trackable = hit.getTrackable();
        // If a plane was hit, check that it was hit inside the plane polygon.
        // DepthPoints are only returned if Config.DepthMode is set to AUTOMATIC.
        if ((trackable instanceof Plane
                && ((Plane) trackable).isPoseInPolygon(hit.getHitPose()))
                || (trackable instanceof Point
                && ((Point) trackable).getOrientationMode()
                == Point.OrientationMode.ESTIMATED_SURFACE_NORMAL)
                || (trackable instanceof DepthPoint)) {
          // Cap the number of objects created. This avoids overloading both the
          // rendering system and ARCore.
          if (anchors.size() >= 20) {
            anchors.get(0).detach();
            anchors.remove(0);
          }

          // Adding an Anchor tells ARCore that it should track this position in
          // space. This anchor is created on the Plane to place the 3D model
          // in the correct position relative both to the world and to the plane.
          anchors.add(hit.createAnchor());

          // Hits are sorted by depth. Consider only closest hit on a plane, Oriented Point, or
          // Instant Placement Point.
          break;
        }
      }
    }
  }

  /**
   * Update state based on the current frame's light estimation.
   */
  private void getLightEstimation(LightEstimate lightEstimate, float[] viewMatrix) {
    float[] direction = lightEstimate.getEnvironmentalHdrMainLightDirection();
    float[] intensity = lightEstimate.getEnvironmentalHdrMainLightIntensity();
    float[] coefficients = lightEstimate.getEnvironmentalHdrAmbientSphericalHarmonics();

    // Calculate view inverse matrix.
    Matrix.invertM(viewInverseMatrix, 0, viewMatrix, 0);

    // We need the direction in a vec4 with 0.0 as the final component to transform it to view space
    worldLightDirection[0] = direction[0];
    worldLightDirection[1] = direction[1];
    worldLightDirection[2] = direction[2];
    Matrix.multiplyMV(viewLightDirection, 0, viewMatrix, 0, worldLightDirection, 0);

    // Set main light intensity.
    lightIntensity[0] = intensity[0];
    lightIntensity[1] = intensity[1];
    lightIntensity[2] = intensity[2];

    // Pre-multiply the spherical harmonics coefficients before passing them to the shader. The
    // constants in sphericalHarmonicFactors were derived from three terms:
    //
    // 1. The normalized spherical harmonics basis functions (y_lm)
    //
    // 2. The lambertian diffuse BRDF factor (1/pi)
    //
    // 3. A <cos> convolution. This is done to so that the resulting function outputs the irradiance
    // of all incoming light over a hemisphere for a given surface normal, which is what the shader
    // (environmental_hdr.frag) expects.
    //
    // You can read more details about the math here:
    // https://google.github.io/filament/Filament.html#annex/sphericalharmonics

    if (coefficients.length != 9 * 3) {
      throw new IllegalArgumentException(
              "The given coefficients array must be of length 27 (3 components per 9 coefficients");
    }

    // Apply each factor to every component of each coefficient
    for (int i = 0; i < 9 * 3; ++i) {
      sphericalHarmonicsCoefficients[i] = coefficients[i] * sphericalHarmonicFactors[i / 3];
    }
  }
}