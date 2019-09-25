/**
 * Copyright (c) Facebook, Inc. and its affiliates.
 *
 * This source code is licensed under the MIT license found in the
 * LICENSE file in the root directory of this source tree.
 */

package com.facebook.react.views.textinput;

import android.annotation.TargetApi;
import android.os.Build;
import androidx.core.view.ViewCompat;
import android.text.Layout;
import android.util.TypedValue;
import android.view.ViewGroup;
import android.widget.EditText;
import com.facebook.infer.annotation.Assertions;
import com.facebook.react.bridge.JSApplicationIllegalArgumentException;
import com.facebook.react.common.annotations.VisibleForTesting;
import com.facebook.react.uimanager.LayoutShadowNode;
import com.facebook.react.uimanager.PixelUtil;
import com.facebook.react.uimanager.ReactShadowNodeImpl;
import com.facebook.react.uimanager.Spacing;
import com.facebook.react.uimanager.ThemedReactContext;
import com.facebook.react.uimanager.UIViewOperationQueue;
import com.facebook.react.uimanager.ViewDefaults;
import com.facebook.react.uimanager.annotations.ReactProp;
import com.facebook.react.views.text.ReactBaseTextShadowNode;
import com.facebook.react.views.text.ReactTextUpdate;
import com.facebook.react.views.view.MeasureUtil;
import com.facebook.yoga.YogaMeasureFunction;
import com.facebook.yoga.YogaMeasureMode;
import com.facebook.yoga.YogaMeasureOutput;
import com.facebook.yoga.YogaNode;
import javax.annotation.Nullable;

@VisibleForTesting
@TargetApi(Build.VERSION_CODES.M)
public class ReactTextInputShadowNode extends ReactBaseTextShadowNode
    implements YogaMeasureFunction {

  private int mMostRecentEventCount = UNSET;
  private @Nullable EditText mDummyEditText;
  private @Nullable ReactTextInputLocalData mLocalData;

  @VisibleForTesting public static final String PROP_TEXT = "text";
  @VisibleForTesting public static final String PROP_PLACEHOLDER = "placeholder";

  // Represents the {@code text} property only, not possible nested content.
  private @Nullable String mText = null;
  private @Nullable String mPlaceholder = null;

  public ReactTextInputShadowNode() {
    mTextBreakStrategy = (Build.VERSION.SDK_INT < Build.VERSION_CODES.M) ?
      Layout.BREAK_STRATEGY_SIMPLE : Layout.BREAK_STRATEGY_HIGH_QUALITY;

    initMeasureFunction();
  }

  private void initMeasureFunction() {
    setMeasureFunction(this);
  }

  @Override
  public void setThemedContext(ThemedReactContext themedContext) {
    super.setThemedContext(themedContext);

    // {@code EditText} has by default a border at the bottom of its view
    // called "underline". To have a native look and feel of the TextEdit
    // we have to preserve it at least by default.
    // The border (underline) has its padding set by the background image
    // provided by the system (which vary a lot among versions and vendors
    // of Android), and it cannot be changed.
    // So, we have to enforce it as a default padding.
    // TODO #7120264: Cache this stuff better.
    EditText editText = new EditText(getThemedContext());
    setDefaultPadding(Spacing.START, ViewCompat.getPaddingStart(editText));
    setDefaultPadding(Spacing.TOP, editText.getPaddingTop());
    setDefaultPadding(Spacing.END, ViewCompat.getPaddingEnd(editText));
    setDefaultPadding(Spacing.BOTTOM, editText.getPaddingBottom());

    mDummyEditText = editText;

    // We must measure the EditText without paddings, so we have to reset them.
    mDummyEditText.setPadding(0, 0, 0, 0);

    // This is needed to fix an android bug since 4.4.3 which will throw an NPE in measure,
    // setting the layoutParams fixes it: https://code.google.com/p/android/issues/detail?id=75877
    mDummyEditText.setLayoutParams(
        new ViewGroup.LayoutParams(
            ViewGroup.LayoutParams.WRAP_CONTENT, ViewGroup.LayoutParams.WRAP_CONTENT));
  }

  @Override
  public long measure(
      YogaNode node,
      float width,
      YogaMeasureMode widthMode,
      float height,
      YogaMeasureMode heightMode) {
    // measure() should never be called before setThemedContext()
    EditText editText = Assertions.assertNotNull(mDummyEditText);

    if (mLocalData != null) {
      mLocalData.apply(editText);
    } else {
      editText.setTextSize(TypedValue.COMPLEX_UNIT_PX, mTextAttributes.getEffectiveFontSize());

      if (mNumberOfLines != UNSET) {
        editText.setLines(mNumberOfLines);
      }

      if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.M &&
          editText.getBreakStrategy() != mTextBreakStrategy) {
        editText.setBreakStrategy(mTextBreakStrategy);
      }
    }

     // make sure the placeholder content is also being measured
     editText.setHint(getPlaceholder());
     editText.measure(
        MeasureUtil.getMeasureSpec(width, widthMode),
        MeasureUtil.getMeasureSpec(height, heightMode));

    return YogaMeasureOutput.make(editText.getMeasuredWidth(), editText.getMeasuredHeight());
  }

  @Override
  public boolean isVirtualAnchor() {
    return true;
  }

  @Override
  public boolean isYogaLeafNode() {
    return true;
  }

  @Override
  public void setLocalData(Object data) {
    Assertions.assertCondition(data instanceof ReactTextInputLocalData);
    mLocalData = (ReactTextInputLocalData) data;

    // Telling to Yoga that the node should be remeasured on next layout pass.
    dirty();

    // Note: We should NOT mark the node updated (by calling {@code markUpdated}) here
    // because the state remains the same.
  }

  @ReactProp(name = "mostRecentEventCount")
  public void setMostRecentEventCount(int mostRecentEventCount) {
    mMostRecentEventCount = mostRecentEventCount;
  }

  @ReactProp(name = PROP_TEXT)
  public void setText(@Nullable String text) {
    mText = text;
    markUpdated();
  }

  public @Nullable String getText() {
    return mText;
  }

  @ReactProp(name = PROP_PLACEHOLDER)
  public void setPlaceholder(@Nullable String placeholder) {
    mPlaceholder = placeholder;
    markUpdated();
  }

  public @Nullable String getPlaceholder() {
    return mPlaceholder;
  }

  @Override
  public void setTextBreakStrategy(@Nullable String textBreakStrategy) {
    if (Build.VERSION.SDK_INT < Build.VERSION_CODES.M) {
      return;
    }

    if (textBreakStrategy == null || "simple".equals(textBreakStrategy)) {
      mTextBreakStrategy = Layout.BREAK_STRATEGY_SIMPLE;
    } else if ("highQuality".equals(textBreakStrategy)) {
      mTextBreakStrategy = Layout.BREAK_STRATEGY_HIGH_QUALITY;
    } else if ("balanced".equals(textBreakStrategy)) {
      mTextBreakStrategy = Layout.BREAK_STRATEGY_BALANCED;
    } else {
      throw new JSApplicationIllegalArgumentException("Invalid textBreakStrategy: " + textBreakStrategy);
    }
  }

  @Override
  public void onCollectExtraUpdates(UIViewOperationQueue uiViewOperationQueue) {
    super.onCollectExtraUpdates(uiViewOperationQueue);

    if (mMostRecentEventCount != UNSET) {
      ReactTextUpdate reactTextUpdate =
          new ReactTextUpdate(
              spannedFromShadowNode(this, getText()),
              mMostRecentEventCount,
              mContainsImages,
              getPadding(Spacing.LEFT),
              getPadding(Spacing.TOP),
              getPadding(Spacing.RIGHT),
              getPadding(Spacing.BOTTOM),
              mTextAlign,
              mTextBreakStrategy,
              mJustificationMode);
      uiViewOperationQueue.enqueueUpdateExtraData(getReactTag(), reactTextUpdate);
    }
  }

  @Override
  public void setPadding(int spacingType, float padding) {
    super.setPadding(spacingType, padding);
    markUpdated();
  }
}
