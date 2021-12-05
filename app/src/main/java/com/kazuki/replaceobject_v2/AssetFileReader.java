package com.kazuki.replaceobject_v2;

import android.content.Context;

import java.io.BufferedReader;
import java.io.IOException;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.util.ArrayList;

/**
 * helper function for reading from asset folder
 */
public class AssetFileReader {

  /**
   * Read label map text file into string[]
   * @param filename The filename of label map text file
   * @return The label list
   * @throws IOException
   */
  public static String[] readLabelmapFileFromAssets(Context context, String filename) throws IOException {
    try (InputStream inputStream = context.getAssets().open(filename);
         BufferedReader reader = new BufferedReader(new InputStreamReader(inputStream))) {
      ArrayList<String> labels = new ArrayList<>();
      String line;
      while ((line = reader.readLine()) != null) {
        if (line.equals("???")) continue;
        labels.add(line);
      }
      return labels.toArray(new String[labels.size()]);
    }

  }
}
