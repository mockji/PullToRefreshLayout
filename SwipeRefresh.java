/*
 * Copyright (C) 2013 The Android Open Source Project
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *      http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */

package com.sohu.xzd.widget;

import android.content.Context;
import android.content.res.TypedArray;
import android.support.v4.view.MotionEventCompat;
import android.support.v4.view.ViewCompat;
import android.util.AttributeSet;
import android.util.DisplayMetrics;
import android.util.Log;
import android.view.MotionEvent;
import android.view.View;
import android.view.ViewConfiguration;
import android.view.ViewGroup;
import android.view.animation.Animation;
import android.view.animation.Animation.AnimationListener;
import android.view.animation.DecelerateInterpolator;
import android.view.animation.Transformation;
import android.widget.AbsListView;

/**
 * The SwipeRefreshLayout should be used whenever the user can refresh the
 * contents of a view via a vertical swipe gesture. The activity that
 * instantiates this view should add an OnRefreshListener to be notified
 * whenever the swipe to refresh gesture is completed. The SwipeRefreshLayout
 * will notify the listener each and every time the gesture is completed again;
 * the listener is responsible for correctly determining when to actually
 * initiate a refresh of its content. If the listener determines there should
 * not be a refresh, it must call setRefreshing(false) to cancel any visual
 * indication of a refresh. If an activity wishes to show just the progress
 * animation, it should call setRefreshing(true). To disable the gesture and
 * progress animation, call setEnabled(false) on the view.
 * <p>
 * This layout should be made the parent of the view that will be refreshed as a
 * result of the gesture and can only support one direct child. This view will
 * also be made the target of the gesture and will be forced to match both the
 * width and the height supplied in this layout. The SwipeRefreshLayout does not
 * provide accessibility events; instead, a menu_normal item must be provided to allow
 * refresh of the content wherever this gesture is used.
 * </p>
 */
public class SwipeRefresh extends ViewGroup {

  private static final String LOG_TAG = SwipeRefresh.class.getSimpleName();
  private static final float DECELERATE_INTERPOLATION_FACTOR = 2f;
  private static final int INVALID_POINTER = -1;
  private static final float DRAG_RATE = .5f;
  private static final int SCALE_DOWN_DURATION = 150;
  private static final int ANIMATE_TO_TRIGGER_DURATION = 200;
  private static final int ANIMATE_TO_START_DURATION = 200;
  // Default offset in dips from the top of the view to where the progress spinner should stop
  private static final int DEFAULT_CIRCLE_TARGET = 50;
  private static final int[] LAYOUT_ATTRS = new int[] {
      android.R.attr.enabled
  };
  private final DecelerateInterpolator mDecelerateInterpolator;
  protected int mFrom;
  protected int mOriginalTargetOffsetTop;
  protected int mOriginHeaderOffset;
  private View mTarget; // the target of the gesture
  private OnRefreshListener mListener;
  private int mTouchSlop;
  private float mTotalDragDistance = -1;
  private int mCurrentTargetOffsetTop;
  // Whether or not the starting offset has been determined.
  private boolean mOriginalOffsetCalculated = false;
  private float mInitialMotionY;
  private boolean mIsBeingDragged;
  private int mActivePointerId = INVALID_POINTER;
  private boolean mReturningToStart;
  private RefreshHeader mRefreshHeader;
  private final Animation mAnimateToStartPosition = new Animation() {
    @Override public void applyTransformation(float interpolatedTime, Transformation t) {
      moveToStart(interpolatedTime);
    }
  };
  private int mHeaderViewIndex = -1;
  private Animation mScaleDownAnimation;

  private float mSpinnerFinalOffset;
  private final Animation mAnimateToCorrectPosition = new Animation() {
    @Override public void applyTransformation(float interpolatedTime, Transformation t) {
      int targetTop = 0;
      int endTarget = (int) mSpinnerFinalOffset;
      targetTop = (mFrom + (int) ((endTarget - mFrom) * interpolatedTime));
      int offset = targetTop - mTarget.getTop();
      setTargetOffsetTopAndBottom(offset, false /* requires update */);
    }
  };
  private boolean mNotify;
  private volatile boolean mRefreshing = false;
  private volatile boolean mIsSuccess = false;
  private AnimationListener mRefreshListener = new AnimationListener() {
    @Override public void onAnimationStart(Animation animation) {
    }

    @Override public void onAnimationRepeat(Animation animation) {
    }

    @Override public void onAnimationEnd(Animation animation) {
      if (mRefreshing) {
        mRefreshHeader.onRefreshing();
        if (mNotify) {
          if (mListener != null) {
            mListener.onRefresh();
          }
        }
      } else {
        mRefreshHeader.onReset();
        mIsSuccess = false;
        setTargetOffsetTopAndBottom(mOriginalTargetOffsetTop - mCurrentTargetOffsetTop, true /* requires update */);
      }
      mCurrentTargetOffsetTop = mTarget.getTop();
    }
  };

  public SwipeRefresh(Context context) {
    this(context, null);
  }

  /**
   * Constructor that is called when inflating SwipeRefreshLayout from XML.
   */
  public SwipeRefresh(Context context, AttributeSet attrs) {
    super(context, attrs);

    mTouchSlop = ViewConfiguration.get(context).getScaledTouchSlop();

    setWillNotDraw(false);
    mDecelerateInterpolator = new DecelerateInterpolator(DECELERATE_INTERPOLATION_FACTOR);

    final TypedArray a = context.obtainStyledAttributes(attrs, LAYOUT_ATTRS);
    setEnabled(a.getBoolean(0, true));
    a.recycle();

    final DisplayMetrics metrics = getResources().getDisplayMetrics();

    createProgressView();

    ViewCompat.setChildrenDrawingOrderEnabled(this, true);
    // the absolute offset has to take into account that the circle starts at an offset
    mSpinnerFinalOffset = DEFAULT_CIRCLE_TARGET * metrics.density;
    mTotalDragDistance = mSpinnerFinalOffset;
  }

  protected int getChildDrawingOrder(int childCount, int i) {
    if (mHeaderViewIndex < 0) {
      return i;
    } else if (i == childCount - 1) {
      // Draw the selected child last
      return mHeaderViewIndex;
    } else if (i >= mHeaderViewIndex) {
      // Move the children after the selected child earlier one
      return i + 1;
    } else {
      // Keep the children before the selected child the same
      return i;
    }
  }

  private void createProgressView() {
    mRefreshHeader = new RefreshHeader(getContext());
    mRefreshHeader.setVisibility(View.GONE);
    addView(mRefreshHeader);
  }

  /**
   * Set the listener to be notified when a refresh is triggered via the swipe
   * gesture.
   */
  public void setOnRefreshListener(OnRefreshListener listener) {
    mListener = listener;
  }

  /**
   * Pre API 11, alpha is used to make the progress circle appear instead of scale.
   */
  private boolean isAlphaUsedForScale() {
    return android.os.Build.VERSION.SDK_INT < 11;
  }

  public void onRefreshingComplete(boolean isSuccess) {
    mIsSuccess = isSuccess;
    setRefreshing(false);
  }

  private void startAlphaInAnimation(AnimationListener listener) {
    mRefreshHeader.setVisibility(View.VISIBLE);
    Animation scaleAnimation = new Animation() {
      @Override public void applyTransformation(float interpolatedTime, Transformation t) {
        //                setAnimationProgress(interpolatedTime);
      }
    };
    scaleAnimation.setDuration(200L);
    if (listener != null) {
      mRefreshHeader.setAnimationListener(listener);
    }
    mRefreshHeader.clearAnimation();
    mRefreshHeader.startAnimation(scaleAnimation);
  }

  private void setRefreshing(boolean refreshing, final boolean notify) {
    mNotify = notify;
    ensureTarget();
    mRefreshing = refreshing;
    if (mRefreshing) {
      mRefreshHeader.onRefreshing();
      animateOffsetToCorrectPosition(mCurrentTargetOffsetTop, mRefreshListener);
    } else {
      mRefreshHeader.onComplete(mIsSuccess);
      mRefreshHeader.postDelayed(new Runnable() {
        @Override public void run() {
          animateOffsetToStartPosition(mCurrentTargetOffsetTop, mRefreshListener);
        }
      }, 500L);
    }
  }

  private void startScaleDownAnimation(AnimationListener listener) {
    mScaleDownAnimation = new Animation() {
      @Override public void applyTransformation(float interpolatedTime, Transformation t) {

      }
    };
    mScaleDownAnimation.setDuration(SCALE_DOWN_DURATION);
    mRefreshHeader.setAnimationListener(listener);
    mRefreshHeader.clearAnimation();
    mRefreshHeader.startAnimation(mScaleDownAnimation);
  }

  /**
   * @return Whether the SwipeRefreshWidget is actively showing refresh
   * progress.
   */
  public boolean isRefreshing() {
    return mRefreshing;
  }

  /**
   * Notify the widget that refresh state has changed. Do not call this when
   * refresh is triggered by a swipe gesture.
   *
   * @param refreshing Whether or not the view should show refresh progress.
   */
  public void setRefreshing(final boolean refreshing) {
    if (refreshing && mRefreshing != refreshing) {
      // scale and show
      mRefreshing = refreshing;
      int endTarget = (int) mTotalDragDistance;
      setTargetOffsetTopAndBottom(endTarget - mCurrentTargetOffsetTop, true /* requires update */);
      mNotify = true;
      startAlphaInAnimation(mRefreshListener);
    } else {
      if (refreshing) {
        setRefreshing(refreshing, true);
      } else {
        setRefreshing(refreshing, false /* notify */);
      }
    }
  }

  private void ensureTarget() {
    // Don't bother getting the parent height if the parent hasn't been laid
    // out yet.
    if (mTarget == null) {
      for (int i = 0; i < getChildCount(); i++) {
        View child = getChildAt(i);
        if (!child.equals(mRefreshHeader)) {
          mTarget = child;
          break;
        }
      }
    }
  }

  public void setTarget(View target) {
    mTarget = target;
  }

  @Override protected void onLayout(boolean changed, int left, int top, int right, int bottom) {
    final int width = getMeasuredWidth();
    final int height = getMeasuredHeight();
    if (getChildCount() == 0) {
      return;
    }
    if (mTarget == null) {
      ensureTarget();
    }
    if (mTarget == null) {
      return;
    }
    final View child = mTarget;
    final int childLeft = getPaddingLeft();
    final int childTop = getPaddingTop();
    final int childWidth = width - getPaddingLeft() - getPaddingRight();
    final int childHeight = height - getPaddingTop() - getPaddingBottom();
    child.layout(childLeft, mCurrentTargetOffsetTop, childLeft + childWidth,
        mCurrentTargetOffsetTop + childHeight);

    mRefreshHeader.layout(childLeft, mOriginHeaderOffset, childLeft + childWidth, mTarget.getTop());
  }

  @Override public void onMeasure(int widthMeasureSpec, int heightMeasureSpec) {
    super.onMeasure(widthMeasureSpec, heightMeasureSpec);
    if (mTarget == null) {
      ensureTarget();
    }
    if (mTarget == null) {
      return;
    }
    mTarget.measure(
        MeasureSpec.makeMeasureSpec(getMeasuredWidth() - getPaddingLeft() - getPaddingRight(),
            MeasureSpec.EXACTLY),
        MeasureSpec.makeMeasureSpec(getMeasuredHeight() - getPaddingTop() - getPaddingBottom(),
            MeasureSpec.EXACTLY));

    mRefreshHeader.measure(MeasureSpec.makeMeasureSpec(MEASURED_SIZE_MASK, MeasureSpec.AT_MOST),
        MeasureSpec.makeMeasureSpec(MEASURED_SIZE_MASK, MeasureSpec.AT_MOST));

    if (!mOriginalOffsetCalculated) {
      mOriginalOffsetCalculated = true;
      mOriginHeaderOffset = 0;
      mOriginalTargetOffsetTop = mCurrentTargetOffsetTop = 0;
    }
    mHeaderViewIndex = -1;
    // Get the index of the headerView.
    for (int index = 0; index < getChildCount(); index++) {
      if (getChildAt(index) == mRefreshHeader) {
        mHeaderViewIndex = index;
        break;
      }
    }
  }

  /**
   * @return Whether it is possible for the child view of this layout to
   * scroll up. Override this if the child view is a custom view.
   */
  public boolean canChildScrollUp() {
    if (android.os.Build.VERSION.SDK_INT < 14) {
      if (mTarget instanceof AbsListView) {
        final AbsListView absListView = (AbsListView) mTarget;
        return absListView.getChildCount() > 0
                && (absListView.getFirstVisiblePosition() > 0 || absListView.getChildAt(0)
                .getTop() < absListView.getPaddingTop());
      } else {
        return ViewCompat.canScrollVertically(mTarget, -1) || mTarget.getScrollY() > 0;
      }
    } else {
      return ViewCompat.canScrollVertically(mTarget, -1);
    }
  }

  @Override public boolean onInterceptTouchEvent(MotionEvent ev) {
    ensureTarget();

    final int action = MotionEventCompat.getActionMasked(ev);

    if (mReturningToStart && action == MotionEvent.ACTION_DOWN) {
      mReturningToStart = false;
    }

    if (!isEnabled() || mReturningToStart || canChildScrollUp() || mRefreshing) {
      // Fail fast if we're not in a state where a swipe is possible
      return false;
    }

    switch (action) {
      case MotionEvent.ACTION_DOWN:
        setTargetOffsetTopAndBottom(mOriginalTargetOffsetTop - mTarget.getTop(), true);
        mActivePointerId = MotionEventCompat.getPointerId(ev, 0);
        mIsBeingDragged = false;
        final float initialMotionY = getMotionEventY(ev, mActivePointerId);
        if (initialMotionY == -1) {
          return false;
        }
        mInitialMotionY = initialMotionY;

      case MotionEvent.ACTION_MOVE:
        if (mActivePointerId == INVALID_POINTER) {
          Log.e(LOG_TAG, "Got ACTION_MOVE event but don't have an active pointer id.");
          return false;
        }

        final float y = getMotionEventY(ev, mActivePointerId);
        if (y == -1) {
          return false;
        }
        final float yDiff = y - mInitialMotionY;
        if (yDiff > mTouchSlop && !mIsBeingDragged) {
          mIsBeingDragged = true;
          mIsSuccess = false;
          mRefreshHeader.onReset();
        }
        break;

      case MotionEventCompat.ACTION_POINTER_UP:
        onSecondaryPointerUp(ev);
        break;

      case MotionEvent.ACTION_UP:
      case MotionEvent.ACTION_CANCEL:
        mIsBeingDragged = false;
        mActivePointerId = INVALID_POINTER;
        break;
    }
    return mIsBeingDragged;
  }

  private float getMotionEventY(MotionEvent ev, int activePointerId) {
    final int index = MotionEventCompat.findPointerIndex(ev, activePointerId);
    if (index < 0) {
      return -1;
    }
    return MotionEventCompat.getY(ev, index);
  }

  @Override public void requestDisallowInterceptTouchEvent(boolean b) {
    // Nope.
  }

  private boolean isAnimationRunning(Animation animation) {
    return animation != null && animation.hasStarted() && !animation.hasEnded();
  }

  @Override public boolean onTouchEvent(MotionEvent ev) {
    final int action = MotionEventCompat.getActionMasked(ev);

    if (mReturningToStart && action == MotionEvent.ACTION_DOWN) {
      mReturningToStart = false;
    }

    if (!isEnabled() || mReturningToStart || canChildScrollUp()) {
      // Fail fast if we're not in a state where a swipe is possible
      return false;
    }

    switch (action) {
      case MotionEvent.ACTION_DOWN:
        mActivePointerId = MotionEventCompat.getPointerId(ev, 0);
        mIsBeingDragged = false;
        break;

      case MotionEvent.ACTION_MOVE: {
        final int pointerIndex = MotionEventCompat.findPointerIndex(ev, mActivePointerId);
        if (pointerIndex < 0) {
          Log.e(LOG_TAG, "Got ACTION_MOVE event but have an invalid active pointer id.");
          return false;
        }

        final float y = MotionEventCompat.getY(ev, pointerIndex);
        final float overscrollTop = (y - mInitialMotionY) * DRAG_RATE;
        if (mIsBeingDragged) {
          float originalDragPercent = overscrollTop / mTotalDragDistance;
          if (originalDragPercent < 0) {
            return false;
          }
          float dragPercent = Math.min(1f, Math.abs(originalDragPercent));
          float adjustedPercent = (float) Math.max(dragPercent - .4, 0) * 5 / 3;
          float extraOS = Math.abs(overscrollTop) - mTotalDragDistance;
          float slingshotDist = mSpinnerFinalOffset;
          float tensionSlingshotPercent =
              Math.max(0, Math.min(extraOS, slingshotDist * 2) / slingshotDist);
          float tensionPercent =
              (float) ((tensionSlingshotPercent / 4) - Math.pow((tensionSlingshotPercent / 4), 2))
                  * 2f;
          float extraMove = (slingshotDist) * tensionPercent * 2;

          int targetY =
              mOriginalTargetOffsetTop + (int) ((slingshotDist * dragPercent) + extraMove);
          if (mRefreshHeader.getVisibility() != View.VISIBLE) {
            mRefreshHeader.setVisibility(View.VISIBLE);
          }
          if (overscrollTop < mTotalDragDistance) {

          } else {

          }
          float rotation = (-0.25f + .4f * adjustedPercent + tensionPercent * 2) * .5f;
          mRefreshHeader.onPull(overscrollTop, mTotalDragDistance, rotation);
          setTargetOffsetTopAndBottom(targetY - mCurrentTargetOffsetTop,
              true /* requires update */);
        }
        break;
      }
      case MotionEventCompat.ACTION_POINTER_DOWN: {
        final int index = MotionEventCompat.getActionIndex(ev);
        mActivePointerId = MotionEventCompat.getPointerId(ev, index);
        break;
      }

      case MotionEventCompat.ACTION_POINTER_UP:
        onSecondaryPointerUp(ev);
        break;

      case MotionEvent.ACTION_UP:
      case MotionEvent.ACTION_CANCEL: {
        if (mActivePointerId == INVALID_POINTER) {
          if (action == MotionEvent.ACTION_UP) {
            Log.e(LOG_TAG, "Got ACTION_UP event but don't have an active pointer id.");
          }
          return false;
        }
        final int pointerIndex = MotionEventCompat.findPointerIndex(ev, mActivePointerId);
        final float y = MotionEventCompat.getY(ev, pointerIndex);
        final float overscrollTop = (y - mInitialMotionY) * DRAG_RATE;
        mIsBeingDragged = false;
        if (overscrollTop > mTotalDragDistance) {
          setRefreshing(true, true /* notify */);
        } else {
          mRefreshing = false;
          AnimationListener listener = null;
          listener = new AnimationListener() {

            @Override public void onAnimationStart(Animation animation) {
            }

            @Override public void onAnimationEnd(Animation animation) {
              startScaleDownAnimation(null);
            }

            @Override public void onAnimationRepeat(Animation animation) {
            }
          };
          animateOffsetToStartPosition(mCurrentTargetOffsetTop, listener);
        }
        mActivePointerId = INVALID_POINTER;
        return false;
      }
    }

    return true;
  }

  private void animateOffsetToCorrectPosition(int from, AnimationListener listener) {
    mFrom = from;
    mAnimateToCorrectPosition.reset();
    mAnimateToCorrectPosition.setDuration(ANIMATE_TO_TRIGGER_DURATION);
    mAnimateToCorrectPosition.setInterpolator(mDecelerateInterpolator);
    if (listener != null) {
      mRefreshHeader.setAnimationListener(listener);
    }
    mRefreshHeader.clearAnimation();
    mRefreshHeader.startAnimation(mAnimateToCorrectPosition);
  }

  private void animateOffsetToStartPosition(int from, AnimationListener listener) {
    mFrom = from;
    mAnimateToStartPosition.reset();
    mAnimateToStartPosition.setDuration(ANIMATE_TO_START_DURATION);
    mAnimateToStartPosition.setInterpolator(mDecelerateInterpolator);
    if (listener != null) {
      mRefreshHeader.setAnimationListener(listener);
    }
    mRefreshHeader.clearAnimation();
    mRefreshHeader.startAnimation(mAnimateToStartPosition);
  }

  private void moveToStart(float interpolatedTime) {
    int targetTop = 0;
    targetTop = (mFrom + (int) ((mOriginalTargetOffsetTop - mFrom) * interpolatedTime));
    int offset = targetTop - mTarget.getTop();
    setTargetOffsetTopAndBottom(offset, false /* requires update */);
  }

  private void setTargetOffsetTopAndBottom(int offset, boolean requiresUpdate) {
    mRefreshHeader.bringToFront();
    mTarget.offsetTopAndBottom(offset);
    mCurrentTargetOffsetTop = mTarget.getTop();
    if (requiresUpdate && android.os.Build.VERSION.SDK_INT < 11) {
      invalidate();
    }
  }

  private void onSecondaryPointerUp(MotionEvent ev) {
    final int pointerIndex = MotionEventCompat.getActionIndex(ev);
    final int pointerId = MotionEventCompat.getPointerId(ev, pointerIndex);
    if (pointerId == mActivePointerId) {
      // This was our active pointer going up. Choose a new
      // active pointer and adjust accordingly.
      final int newPointerIndex = pointerIndex == 0 ? 1 : 0;
      mActivePointerId = MotionEventCompat.getPointerId(ev, newPointerIndex);
    }
  }

  public interface PullListener {
    public void onReset();

    public void onPull(float overScroll, float totalDragDistance, float rotation);

    public void onRefreshing();

    public void onComplete(boolean isSuccess);
  }

  /**
   * Classes that wish to be notified when the swipe gesture correctly
   * triggers a refresh should implement this interface.
   */
  public interface OnRefreshListener {
    public void onRefresh();
  }
}