package com.kazuki.replaceobject_v2;

import androidx.appcompat.app.AppCompatActivity;

import android.content.Context;
import android.content.Intent;
import android.os.Bundle;
import android.util.Log;
import android.widget.Button;
import android.widget.CheckBox;
import android.widget.LinearLayout;
import android.widget.TextView;

import java.io.BufferedReader;
import java.io.IOException;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.util.ArrayList;
import java.util.Arrays;

public class SelectItemActivity extends AppCompatActivity {
  private static final String TAG = SelectItemActivity.class.getSimpleName();

  public static final String EXTRA_SELECT_ITEMS = "replaceobject_v2.SELECT_ITEMS";

  private static final String LABELMAP_NAME = "tflite/labelmap.txt";

  private String[] labels;
  private boolean isSelectsChanged = false;
  private ArrayList<String> selectItems = new ArrayList<>();

  @Override
  protected void onCreate(Bundle savedInstanceState) {
    super.onCreate(savedInstanceState);
    setContentView(R.layout.activity_select_item);

    // read label map
    try {
      labels = readLabelmapFileFromAssets(this, LABELMAP_NAME);
    } catch (IOException e) {
      Log.e(TAG, "Failed to read an asset file", e);
    }

    // make checkbox of labels
    LinearLayout replaceItemListLayout = findViewById(R.id.linearLayout_replaceItemList);
    int label_num = labels.length;
    for (int i = 0; i < label_num; i++) {
      CheckBox checkBox = new CheckBox(this);
      checkBox.setText(labels[i]);
      checkBox.setOnCheckedChangeListener((compoundButton, isChecked) -> {
        // update Select Items TextView
        String text = compoundButton.getText().toString();
        TextView selectItemsTextView = findViewById(R.id.textView_selectItems);
        if (isChecked) {
          selectItems.add(text);
          selectItemsTextView.setText(updateSelectItemsTextView(selectItems));
        } else {
          selectItems.remove(text);
          selectItemsTextView.setText(updateSelectItemsTextView(selectItems));
        }
      });
      replaceItemListLayout.addView(checkBox, i);
    }

    // setting start button
    Button startButton = findViewById(R.id.button_start);
    startButton.setOnClickListener(view -> {
      if (selectItems.size() != 0) {
        // start ReplaceObject Activity
        Intent intent = new Intent(getApplication(), ReplaceObjectActivity.class);
        intent.putExtra(EXTRA_SELECT_ITEMS, selectItems.toArray(new String[selectItems.size()]));
        startActivity(intent);
      }
    });
  }

  private String updateSelectItemsTextView(ArrayList<String> array) {
    StringBuilder sb = new StringBuilder();
    array.forEach(item -> {
      sb.append("・").append(item).append("\n");
    });
    return sb.toString();
  }

  private static String[] readLabelmapFileFromAssets(Context context, String filename) throws IOException {
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