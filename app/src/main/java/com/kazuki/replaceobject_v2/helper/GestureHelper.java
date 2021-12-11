package com.kazuki.replaceobject_v2.helper;

import android.app.Activity;
import android.content.Context;
import android.view.GestureDetector;
import android.view.MotionEvent;
import android.view.View;

import java.util.ArrayList;
import java.util.concurrent.ArrayBlockingQueue;
import java.util.concurrent.BlockingQueue;

/**
 * Helper to detect taps using Android GestureDetector, and pass the taps between UI thread and
 * render thread.
 */
public final class GestureHelper implements View.OnTouchListener {
  private final GestureDetector gestureDetector;
  private final BlockingQueue<MotionEvent> queuedSingleTaps = new ArrayBlockingQueue<>(16);

  // UI
  private boolean isHideUI = true;
  private final Activity activity;
  private final ArrayList<View> viewComponents;

  /**
   * Creates the tap helper.
   */
  public GestureHelper(Context context, Activity activity, ArrayList<View> viewComponents) {
    this.activity = activity;
    this.viewComponents = viewComponents;
    gestureDetector =
            new GestureDetector(
                    context,
                    new GestureDetector.SimpleOnGestureListener() {
                      @Override
                      public boolean onSingleTapUp(MotionEvent e) {
                        // Queue tap if there is space. Tap is lost if queue is full.
                        queuedSingleTaps.offer(e);
                        return true;
                      }

                      @Override
                      public void onLongPress(MotionEvent e) {
                        super.onLongPress(e);
                        visibleUIs();
                      }

                      @Override
                      public boolean onDown(MotionEvent e) {
                        return true;
                      }
                    });
  }

  /**
   * Polls for a tap.
   *
   * @return if a tap was queued, a MotionEvent for the tap. Otherwise null if no taps are queued.
   */
  public MotionEvent poll() {
    return queuedSingleTaps.poll();
  }

  @Override
  public boolean onTouch(View view, MotionEvent motionEvent) {
    return gestureDetector.onTouchEvent(motionEvent);
  }

  /**
   * Show or Hide UIs.
   */
  private void visibleUIs() {
    if (isHideUI) {
      activity.runOnUiThread(new Runnable() {
        @Override
        public void run() {
          viewComponents.forEach(view -> view.setVisibility(View.INVISIBLE));
        }
      });
      isHideUI = false;
    } else {
      activity.runOnUiThread(new Runnable() {
        @Override
        public void run() {
          viewComponents.forEach(view -> view.setVisibility(View.VISIBLE));
        }
      });
      isHideUI = true;
    }
  }
}
