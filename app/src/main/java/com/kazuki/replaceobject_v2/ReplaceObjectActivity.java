package com.kazuki.replaceobject_v2;

import androidx.appcompat.app.AppCompatActivity;

import android.content.Intent;
import android.os.Bundle;
import android.widget.TextView;

public class ReplaceObjectActivity extends AppCompatActivity {
  private static final String TAG=ReplaceObjectActivity.class.getSimpleName();

  // from SelectItemActivity
  private static String[] selectItems;

  @Override
  protected void onCreate(Bundle savedInstanceState) {
    super.onCreate(savedInstanceState);
    setContentView(R.layout.activity_replace_object);

    Intent intent = getIntent();
    selectItems = intent.getStringArrayExtra(SelectItemActivity.EXTRA_SELECT_ITEMS);
    TextView textView=findViewById(R.id.textView);
    textView.setText(listToString(selectItems));
  }

  private String listToString(String[] list){
    StringBuilder sb = new StringBuilder();
    for (int i = 0; i<list.length; i++){
      sb.append(list[i]);
      sb.append("\n");
    }

    return sb.toString();
  }
}