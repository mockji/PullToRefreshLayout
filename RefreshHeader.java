package com.sohu.xzd.widget;

import android.content.Context;
import android.support.v4.content.ContextCompat;
import android.util.AttributeSet;
import android.view.Gravity;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.view.animation.Animation;
import android.view.animation.Interpolator;
import android.view.animation.LinearInterpolator;
import android.widget.ImageView;
import android.widget.LinearLayout;
import android.widget.TextView;

import com.sohu.xzd.R;

public class RefreshHeader extends LinearLayout implements SwipeRefresh.PullListener {
  static final Interpolator ROTATE_INTERPOLATOR = new LinearInterpolator();
  private static final String LOG_TAG = RefreshHeader.class.getSimpleName();
  View mContentView;
  ImageView mRotateView;
  TextView mTextView;

  private Animation.AnimationListener mListener;

  public RefreshHeader(Context context, AttributeSet attributeSet) {
    super(context, attributeSet);
  }

  public RefreshHeader(Context context, AttributeSet attributeSet, int defStyle) {
    super(context, attributeSet, defStyle);
  }

  public RefreshHeader(Context context) {
    super(context);
    ViewGroup.LayoutParams params = new ViewGroup.LayoutParams(ViewGroup.LayoutParams.WRAP_CONTENT,
        ViewGroup.LayoutParams.MATCH_PARENT);
    setLayoutParams(params);
    setGravity(Gravity.BOTTOM | Gravity.CENTER_HORIZONTAL);
    setOrientation(HORIZONTAL);
    LayoutInflater inflater =
        (LayoutInflater) context.getSystemService(Context.LAYOUT_INFLATER_SERVICE);
    mContentView = inflater.inflate(R.layout.refresh_header, null);
    mRotateView = (ImageView) mContentView.findViewById(R.id.rotate);
    mTextView = (TextView) mContentView.findViewById(R.id.text);
    addView(mContentView);
  }

  @Override protected void onLayout(boolean changed, int l, int t, int r, int b) {
    super.onLayout(changed, l, t, r, b);
    if (mContentView.getMeasuredHeight() > b) {
      setGravity(Gravity.BOTTOM | Gravity.CENTER_HORIZONTAL);
    } else {
      setGravity(Gravity.CENTER);
    }
  }

  @Override public void onReset() {
    mTextView.setText(R.string.csr_text_state_normal);
  }

  @Override public void onPull(float overScroll, float totalDragDistance, float rotation) {
    if (overScroll > totalDragDistance) {
      mTextView.setText(R.string.csr_text_state_ready);
    } else {
      mTextView.setText(R.string.csr_text_state_normal);
    }
  }

  @Override public void onRefreshing() {
    mTextView.setText(R.string.csr_text_state_refresh);
  }

  @Override public void onComplete(boolean isSuccess) {
    mTextView.setText(isSuccess ? R.string.csr_text_state_complete : R.string.csr_text_state_error);
  }

  public void setAnimationListener(Animation.AnimationListener listener) {
    mListener = listener;
  }

  @Override public void onAnimationStart() {
    super.onAnimationStart();
    if (mListener != null) {
      mListener.onAnimationStart(getAnimation());
    }
  }

  @Override public void onAnimationEnd() {
    super.onAnimationEnd();
    if (mListener != null) {
      mListener.onAnimationEnd(getAnimation());
    }
  }
}
