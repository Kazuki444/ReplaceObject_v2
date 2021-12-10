package com.kazuki.replaceobject_v2.helper;

import android.content.Context;
import android.util.Log;

import java.io.BufferedReader;
import java.io.IOException;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.util.HashMap;
import java.util.Map;

/**
 * Manage the 3D model and textures to be load.
 *
 * Each line of the file to be loaded has the following structure.
 * -> class:model.obj:model_albedo.png:model_pbr.png
 */
public final class ModelTableHelper {
  private static final String TAG = ModelTableHelper.class.getSimpleName();

  private final Context context;

  // key = class, value = {"model.obj", "model_albedo.png", "model_pbr.png"}
  private final Map<String, String[]> table = new HashMap<>();

  // load model table.
  public ModelTableHelper(Context context, String modelTableName) throws IOException {
    this.context = context;
    try(InputStream inputStream = context.getAssets().open(modelTableName);
        BufferedReader reader=new BufferedReader(new InputStreamReader(inputStream))){
      String[] fileNames;
      String line;
      String key;
      String[] values = new String[3];
      while ((line = reader.readLine()) != null){
        fileNames = line.split(":"); // split ":"
        key = fileNames[0];
        values[0] = fileNames[1];
        values[1] = fileNames[2];
        values[2] = fileNames[3];
        table.put(key, values);
      }
    }
  }

  /**
   * Return the file names of the class.
   * @return file names String[3]. if map don't have the key, return null.
   */
  public String[] getFileName(String key){
    return table.get(key);
  }

}
