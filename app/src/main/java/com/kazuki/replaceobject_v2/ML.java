package com.kazuki.replaceobject_v2;

import android.content.Context;
import android.graphics.Matrix;
import android.graphics.RectF;
import android.media.Image;
import android.os.SystemClock;
import android.util.Log;

import org.tensorflow.lite.DataType;
import org.tensorflow.lite.support.image.TensorImage;
import org.tensorflow.lite.task.core.BaseOptions;
import org.tensorflow.lite.task.core.vision.ImageProcessingOptions;
import org.tensorflow.lite.task.vision.detector.Detection;
import org.tensorflow.lite.task.vision.detector.ObjectDetector;

import java.io.IOException;
import java.util.List;

/**
 * Object detection in a Image.
 */
public class ML {
  private static final String TAG = ML.class.getSimpleName();

  private static final String TF_LITE_FILE = "tflite/spaghettinet_edgetpu_l_metadata.tflite";

  private ObjectDetector objectDetector;
  private static final int NUM_DETECTIONS = 1;  // The maximum number of top-scored detection results to return.
  private ImageProcessingOptions imageProcessingOptions;
  private TensorImage tensorImage = new TensorImage(DataType.UINT8);

  // result
  private String label;
  private float confidence;
  private RectF location;
  private final int[] locationIndex = new int[4];
  private int resultNum;

  private boolean isDisplayRotation = false;

  /**
   * Initialize ObjectDetector.
   * This function should be called #onSurfaceCreated()
   */
  public void initializeObjectDetector(Context context) throws IOException {
    BaseOptions.Builder baseOptionBuilder = BaseOptions.builder();
    baseOptionBuilder.useNnapi().setNumThreads(4);
    ObjectDetector.ObjectDetectorOptions options =
            ObjectDetector.ObjectDetectorOptions
                    .builder()
                    .setBaseOptions(baseOptionBuilder.build())
                    .setMaxResults(NUM_DETECTIONS)
                    .build();
    objectDetector = ObjectDetector.createFromFileAndOptions(context, TF_LITE_FILE, options);
  }

  /**
   * This function should be called #onSurfaceChanged()
   */
  public void isDisplayRotate() {
    isDisplayRotation = true;
  }

  /**
   * Object detection in a Image.
   * Image format should be YUV_420_88.
   */
  public void detect(Image image, int cameraSensorToDisplayRotation) {
    tensorImage.load(image);
    if (isDisplayRotation) {
      imageProcessingOptions = ImageProcessingOptions
              .builder()
              .setOrientation(getTensorImageRotation(cameraSensorToDisplayRotation))
              .build();
    }

    //long start = SystemClock.uptimeMillis();
    List<Detection> results = objectDetector.detect(tensorImage, imageProcessingOptions);
    //long processTime = SystemClock.uptimeMillis() - start;
    //Log.d(TAG, "detect: process time = "+processTime);

    // Size of results is same NUM_DETECTIONS, but this app need top1 score detection.
    if (results.size() != 0) {
      label = results.get(0).getCategories().get(0).getLabel();
      confidence = results.get(0).getCategories().get(0).getScore();
      location = results.get(0).getBoundingBox();

      int width = image.getWidth();
      int height = image.getHeight();

      // Adjust the BB location correctly.
      Matrix matrix = ImageUtils.getTransformationMatrix(
              width, height, cameraSensorToDisplayRotation);
      matrix.mapRect(location);

      // Calc BB location index of the image.  X=[0, image.width()] Y=[0, image.height()]
      locationIndex[0] = location.left < 0 ? 0 : (int) location.left;
      locationIndex[1] = location.top < 0 ? 0 : (int) location.top;
      locationIndex[2] = location.right >= width ? width - 1 : (int) location.right;
      locationIndex[3] = location.bottom >= height ? height - 1 : (int) location.bottom;

      resultNum = 1;
    } else {
      resultNum = 0;
    }

  }

  private ImageProcessingOptions.Orientation getTensorImageRotation(int rotation) {
    isDisplayRotation = false;
    switch (rotation) {
      case 0:
        return ImageProcessingOptions.Orientation.TOP_LEFT;
      case 90:
        return ImageProcessingOptions.Orientation.RIGHT_TOP;
      case 180:
        return ImageProcessingOptions.Orientation.BOTTOM_RIGHT;
      case 270:
        return ImageProcessingOptions.Orientation.LEFT_BOTTOM;
      default:
        return null;
    }
  }

  public String getLabel() {
    return label;
  }

  public float getConfidence() {
    return confidence;
  }

  public RectF getLocation() {
    return location;
  }

  public int getResultNum() {
    return resultNum;
  }

  public int[] getLocationIndex() {
    return locationIndex;
  }

  ;
}
