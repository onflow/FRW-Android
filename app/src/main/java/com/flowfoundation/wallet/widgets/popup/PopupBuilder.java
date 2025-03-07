package com.flowfoundation.wallet.widgets.popup;

import android.content.Context;
import android.graphics.Color;
import android.graphics.PointF;
import android.view.Gravity;
import android.view.MotionEvent;
import android.view.View;
import android.widget.ImageView;

import androidx.lifecycle.Lifecycle;

import com.lxj.xpopup.animator.PopupAnimator;
import com.lxj.xpopup.core.BasePopupView;
import com.lxj.xpopup.core.ImageViewerPopupView;
import com.lxj.xpopup.core.PopupInfo;
import com.lxj.xpopup.enums.PopupAnimation;
import com.lxj.xpopup.enums.PopupPosition;
import com.lxj.xpopup.impl.AttachListPopupView;
import com.lxj.xpopup.impl.BottomListPopupView;
import com.lxj.xpopup.impl.CenterListPopupView;
import com.lxj.xpopup.impl.ConfirmPopupView;
import com.lxj.xpopup.impl.InputConfirmPopupView;
import com.lxj.xpopup.impl.LoadingPopupView;
import com.lxj.xpopup.interfaces.OnCancelListener;
import com.lxj.xpopup.interfaces.OnConfirmListener;
import com.lxj.xpopup.interfaces.OnImageViewerLongPressListener;
import com.lxj.xpopup.interfaces.OnInputConfirmListener;
import com.lxj.xpopup.interfaces.OnSelectListener;
import com.lxj.xpopup.interfaces.OnSrcViewUpdateListener;
import com.lxj.xpopup.interfaces.XPopupCallback;
import com.lxj.xpopup.interfaces.XPopupImageLoader;
import com.lxj.xpopup.util.XPopupUtils;

import java.util.ArrayList;
import java.util.List;

public class PopupBuilder {
    public final PopupInfo popupInfo = new PopupInfo();
    private final Context context;

    public PopupBuilder(Context context) {
        this.context = context;
    }

    public PopupBuilder dismissOnBackPressed(Boolean isDismissOnBackPressed) {
        this.popupInfo.isDismissOnBackPressed = isDismissOnBackPressed;
        return this;
    }

    public PopupBuilder dismissOnTouchOutside(Boolean isDismissOnTouchOutside) {
        this.popupInfo.isDismissOnTouchOutside = isDismissOnTouchOutside;
        return this;
    }

    public PopupBuilder autoDismiss(Boolean autoDismiss) {
        this.popupInfo.autoDismiss = autoDismiss;
        return this;
    }

    public PopupBuilder hasShadowBg(Boolean hasShadowBg) {
        this.popupInfo.hasShadowBg = hasShadowBg;
        return this;
    }

    public PopupBuilder hasBlurBg(boolean hasBlurBg) {
        this.popupInfo.hasBlurBg = hasBlurBg;
        return this;
    }

    public PopupBuilder atView(View atView) {
        popupInfo.atView = atView;
        return this;
    }

    public PopupBuilder watchView(View watchView) {
        watchView.setOnTouchListener(new View.OnTouchListener() {
            @Override
            public boolean onTouch(View v, MotionEvent event) {
                if (event.getAction() == MotionEvent.ACTION_DOWN) {
                    popupInfo.touchPoint = new PointF(event.getRawX(), event.getRawY());
                }
                return false;
            }
        });
        return this;
    }

    public PopupBuilder popupAnimation(PopupAnimation popupAnimation) {
        this.popupInfo.popupAnimation = popupAnimation;
        return this;
    }

    public PopupBuilder customAnimator(PopupAnimator customAnimator) {
        this.popupInfo.customAnimator = customAnimator;
        return this;
    }

    public PopupBuilder popupHeight(int height) {
        this.popupInfo.popupHeight = height;
        return this;
    }

    public PopupBuilder popupWidth(int width) {
        this.popupInfo.popupWidth = width;
        return this;
    }

    public PopupBuilder maxWidth(int maxWidth) {
        this.popupInfo.maxWidth = maxWidth;
        return this;
    }

    public PopupBuilder maxHeight(int maxHeight) {
        this.popupInfo.maxHeight = maxHeight;
        return this;
    }


    public PopupBuilder autoOpenSoftInput(Boolean autoOpenSoftInput) {
        this.popupInfo.autoOpenSoftInput = autoOpenSoftInput;
        return this;
    }

    public PopupBuilder moveUpToKeyboard(Boolean isMoveUpToKeyboard) {
        this.popupInfo.isMoveUpToKeyboard = isMoveUpToKeyboard;
        return this;
    }

    public PopupBuilder popupPosition(PopupPosition popupPosition) {
        this.popupInfo.popupPosition = popupPosition;
        return this;
    }

    public PopupBuilder hasStatusBarShadow(boolean hasStatusBarShadow) {
        this.popupInfo.hasStatusBarShadow = hasStatusBarShadow;
        return this;
    }

    public PopupBuilder hasStatusBar(boolean hasStatusBar) {
        this.popupInfo.hasStatusBar = hasStatusBar;
        return this;
    }

    public PopupBuilder hasNavigationBar(boolean hasNavigationBar) {
        this.popupInfo.hasNavigationBar = hasNavigationBar;
        return this;
    }

    public PopupBuilder navigationBarColor(int navigationBarColor) {
        this.popupInfo.navigationBarColor = navigationBarColor;
        return this;
    }

    public PopupBuilder isLightNavigationBar(boolean isLightNavigationBar) {
        this.popupInfo.isLightNavigationBar = isLightNavigationBar ? 1 : -1;
        return this;
    }

    public PopupBuilder isLightStatusBar(boolean isLightStatusBar) {
        this.popupInfo.isLightStatusBar = isLightStatusBar ? 1 : -1;
        return this;
    }

    public PopupBuilder statusBarBgColor(int statusBarBgColor) {
        this.popupInfo.statusBarBgColor = statusBarBgColor;
        return this;
    }

    public PopupBuilder offsetX(int offsetX) {
        this.popupInfo.offsetX = offsetX;
        return this;
    }

    public PopupBuilder offsetY(int offsetY) {
        this.popupInfo.offsetY = offsetY;
        return this;
    }

    public PopupBuilder enableDrag(boolean enableDrag) {
        this.popupInfo.enableDrag = enableDrag;
        return this;
    }

    public PopupBuilder isCenterHorizontal(boolean isCenterHorizontal) {
        this.popupInfo.isCenterHorizontal = isCenterHorizontal;
        return this;
    }

    public PopupBuilder isRequestFocus(boolean isRequestFocus) {
        this.popupInfo.isRequestFocus = isRequestFocus;
        return this;
    }

    public PopupBuilder autoFocusEditText(boolean autoFocusEditText) {
        this.popupInfo.autoFocusEditText = autoFocusEditText;
        return this;
    }

    public PopupBuilder isDarkTheme(boolean isDarkTheme) {
        this.popupInfo.isDarkTheme = isDarkTheme;
        return this;
    }

    public PopupBuilder isClickThrough(boolean isClickThrough) {
        this.popupInfo.isClickThrough = isClickThrough;
        return this;
    }

    public PopupBuilder isTouchThrough(boolean isTouchThrough) {
        this.popupInfo.isTouchThrough = isTouchThrough;
        return this;
    }

    public PopupBuilder enableShowWhenAppBackground(boolean enableShowWhenAppBackground) {
        this.popupInfo.enableShowWhenAppBackground = enableShowWhenAppBackground;
        return this;
    }

    public PopupBuilder isThreeDrag(boolean isThreeDrag) {
        this.popupInfo.isThreeDrag = isThreeDrag;
        return this;
    }

    public PopupBuilder isDestroyOnDismiss(boolean isDestroyOnDismiss) {
        this.popupInfo.isDestroyOnDismiss = isDestroyOnDismiss;
        return this;
    }

    public PopupBuilder borderRadius(float borderRadius) {
        this.popupInfo.borderRadius = borderRadius;
        return this;
    }

    public PopupBuilder positionByWindowCenter(boolean positionByWindowCenter) {
        this.popupInfo.positionByWindowCenter = positionByWindowCenter;
        return this;
    }

    public PopupBuilder isViewMode(boolean viewMode) {
        this.popupInfo.isViewMode = viewMode;
        return this;
    }

    public PopupBuilder shadowBgColor(int shadowBgColor) {
        this.popupInfo.shadowBgColor = shadowBgColor;
        return this;
    }

    public PopupBuilder animationDuration(int animationDuration) {
        this.popupInfo.animationDuration = animationDuration;
        return this;
    }

    public PopupBuilder keepScreenOn(boolean keepScreenOn) {
        this.popupInfo.keepScreenOn = keepScreenOn;
        return this;
    }

    public PopupBuilder notDismissWhenTouchInView(View view) {
        if (this.popupInfo.notDismissWhenTouchInArea == null) {
            this.popupInfo.notDismissWhenTouchInArea = new ArrayList<>();
        }
        this.popupInfo.notDismissWhenTouchInArea.add(XPopupUtils.getViewRect(view));
        return this;
    }

    public PopupBuilder customHostLifecycle(Lifecycle lifecycle) {
        this.popupInfo.hostLifecycle = lifecycle;
        return this;
    }

    public PopupBuilder setPopupCallback(XPopupCallback xPopupCallback) {
        this.popupInfo.xPopupCallback = xPopupCallback;
        return this;
    }

    public ConfirmPopupView asConfirm(CharSequence title, CharSequence content, CharSequence cancelBtnText, CharSequence confirmBtnText, OnConfirmListener confirmListener, OnCancelListener cancelListener, boolean isHideCancel,
                                      int bindLayoutId) {
        ConfirmPopupView popupView = new ConfirmPopupView(this.context, bindLayoutId);
        popupView.setTitleContent(title, content, null);
        popupView.setCancelText(cancelBtnText);
        popupView.setConfirmText(confirmBtnText);
        popupView.setListener(confirmListener, cancelListener);
        popupView.isHideCancel = isHideCancel;
        popupView.popupInfo = this.popupInfo;
        return popupView;
    }

    public ConfirmPopupView asConfirm(CharSequence title, CharSequence content, CharSequence cancelBtnText, CharSequence confirmBtnText, OnConfirmListener confirmListener, OnCancelListener cancelListener, boolean isHideCancel) {
        return asConfirm(title, content, cancelBtnText, confirmBtnText, confirmListener, cancelListener, isHideCancel, 0);
    }

    public ConfirmPopupView asConfirm(CharSequence title, CharSequence content, OnConfirmListener confirmListener, OnCancelListener cancelListener) {
        return asConfirm(title, content, null, null, confirmListener, cancelListener, false, 0);
    }

    public ConfirmPopupView asConfirm(CharSequence title, CharSequence content, OnConfirmListener confirmListener) {
        return asConfirm(title, content, null, null, confirmListener, null, false, 0);
    }

    public InputConfirmPopupView asInputConfirm(CharSequence title, CharSequence content, CharSequence inputContent, CharSequence hint, OnInputConfirmListener confirmListener, OnCancelListener cancelListener, int bindLayoutId) {
        InputConfirmPopupView popupView = new InputConfirmPopupView(this.context, bindLayoutId);
        popupView.setTitleContent(title, content, hint);
        popupView.inputContent = inputContent;
        popupView.setListener(confirmListener, cancelListener);
        popupView.popupInfo = this.popupInfo;
        return popupView;
    }

    public InputConfirmPopupView asInputConfirm(CharSequence title, CharSequence content, CharSequence inputContent, CharSequence hint, OnInputConfirmListener confirmListener) {
        return asInputConfirm(title, content, inputContent, hint, confirmListener, null, 0);
    }

    public InputConfirmPopupView asInputConfirm(CharSequence title, CharSequence content, CharSequence hint, OnInputConfirmListener confirmListener) {
        return asInputConfirm(title, content, null, hint, confirmListener, null, 0);
    }

    public InputConfirmPopupView asInputConfirm(CharSequence title, CharSequence content, OnInputConfirmListener confirmListener) {
        return asInputConfirm(title, content, null, null, confirmListener, null, 0);
    }

    public CenterListPopupView asCenterList(CharSequence title, String[] data, int[] iconIds, int checkedPosition, OnSelectListener selectListener, int bindLayoutId,
                                            int bindItemLayoutId) {
        CenterListPopupView popupView = new CenterListPopupView(this.context, bindLayoutId, bindItemLayoutId)
                .setStringData(title, data, iconIds)
                .setCheckedPosition(checkedPosition)
                .setOnSelectListener(selectListener);
        popupView.popupInfo = this.popupInfo;
        return popupView;
    }

    public CenterListPopupView asCenterList(CharSequence title, String[] data, int[] iconIds, int checkedPosition, OnSelectListener selectListener) {
        return asCenterList(title, data, iconIds, checkedPosition, selectListener, 0, 0);
    }

    public CenterListPopupView asCenterList(CharSequence title, String[] data, OnSelectListener selectListener) {
        return asCenterList(title, data, null, -1, selectListener);
    }

    public CenterListPopupView asCenterList(CharSequence title, String[] data, int[] iconIds, OnSelectListener selectListener) {
        return asCenterList(title, data, iconIds, -1, selectListener);
    }

    public LoadingPopupView asLoading(CharSequence title, int bindLayoutId) {
        LoadingPopupView popupView = new LoadingPopupView(this.context, bindLayoutId)
                .setTitle(title);
        popupView.popupInfo = this.popupInfo;
        return popupView;
    }

    public LoadingPopupView asLoading(CharSequence title) {
        return asLoading(title, 0);
    }

    public LoadingPopupView asLoading() {
        return asLoading(null);
    }

    public BottomListPopupView asBottomList(CharSequence title, String[] data, int[] iconIds, int checkedPosition, boolean enableDrag, OnSelectListener selectListener, int bindLayoutId,
                                            int bindItemLayoutId) {
        BottomListPopupView popupView = new BottomListPopupView(this.context, bindLayoutId, bindItemLayoutId)
                .setStringData(title, data, iconIds)
                .setCheckedPosition(checkedPosition)
                .setOnSelectListener(selectListener);
        popupView.popupInfo = this.popupInfo;
        return popupView;
    }

    public BottomListPopupView asBottomList(CharSequence title, String[] data, int[] iconIds, int checkedPosition, boolean enableDrag, OnSelectListener selectListener) {
        return asBottomList(title, data, iconIds, checkedPosition, enableDrag, selectListener, 0, 0);
    }

    public BottomListPopupView asBottomList(CharSequence title, String[] data, OnSelectListener selectListener) {
        return asBottomList(title, data, null, -1, true, selectListener);
    }

    public BottomListPopupView asBottomList(CharSequence title, String[] data, int[] iconIds, OnSelectListener selectListener) {
        return asBottomList(title, data, iconIds, -1, true, selectListener);
    }

    public BottomListPopupView asBottomList(CharSequence title, String[] data, int[] iconIds, int checkedPosition, OnSelectListener selectListener) {
        return asBottomList(title, data, iconIds, checkedPosition, true, selectListener);
    }

    public BottomListPopupView asBottomList(CharSequence title, String[] data, int[] iconIds, boolean enableDrag, OnSelectListener selectListener) {
        return asBottomList(title, data, iconIds, -1, enableDrag, selectListener);
    }


    public AttachListPopupView asAttachList(String[] data, int[] iconIds, OnSelectListener selectListener, int bindLayoutId,
                                            int bindItemLayoutId, int contentGravity) {
        AttachListPopupView popupView = new AttachListPopupView(this.context, bindLayoutId, bindItemLayoutId)
                .setStringData(data, iconIds)
                .setContentGravity(contentGravity)
                .setOnSelectListener(selectListener);
        popupView.popupInfo = this.popupInfo;
        return popupView;
    }

    public AttachListPopupView asAttachList(String[] data, int[] iconIds, OnSelectListener selectListener, int bindLayoutId,
                                            int bindItemLayoutId) {
        return asAttachList(data, iconIds, selectListener, bindLayoutId, bindItemLayoutId, Gravity.CENTER);
    }

    public AttachListPopupView asAttachList(String[] data, int[] iconIds, OnSelectListener selectListener) {
        return asAttachList(data, iconIds, selectListener, 0, 0, Gravity.CENTER);
    }

    public ImageViewerPopupView asImageViewer(ImageView srcView, Object url, XPopupImageLoader imageLoader) {
        ImageViewerPopupView popupView = new ImageViewerPopupView(this.context)
                .setSingleSrcView(srcView, url)
                .setXPopupImageLoader(imageLoader);
        popupView.popupInfo = this.popupInfo;
        return popupView;
    }

    public ImageViewerPopupView asImageViewer(ImageView srcView, Object url, boolean isInfinite, int placeholderColor, int placeholderStroke, int placeholderRadius,
                                              boolean isShowSaveBtn, int bgColor, XPopupImageLoader imageLoader, OnImageViewerLongPressListener longPressListener) {
        ImageViewerPopupView popupView = new ImageViewerPopupView(this.context)
                .setSingleSrcView(srcView, url)
                .isInfinite(isInfinite)
                .setPlaceholderColor(placeholderColor)
                .setPlaceholderStrokeColor(placeholderStroke)
                .setPlaceholderRadius(placeholderRadius)
                .isShowSaveButton(isShowSaveBtn)
                .setBgColor(bgColor)
                .setXPopupImageLoader(imageLoader)
                .setLongPressListener(longPressListener);
        popupView.popupInfo = this.popupInfo;
        return popupView;
    }

    public ImageViewerPopupView asImageViewer(ImageView srcView, int currentPosition, List<Object> urls,
                                              OnSrcViewUpdateListener srcViewUpdateListener, XPopupImageLoader imageLoader) {
        return asImageViewer(srcView, currentPosition, urls, false, true, -1, -1, -1, true,
                Color.rgb(32, 36, 46), srcViewUpdateListener, imageLoader, null);
    }

    public ImageViewerPopupView asImageViewer(ImageView srcView, int currentPosition, List<Object> urls,
                                              boolean isInfinite, boolean isShowPlaceHolder,
                                              int placeholderColor, int placeholderStroke, int placeholderRadius, boolean isShowSaveBtn,
                                              int bgColor, OnSrcViewUpdateListener srcViewUpdateListener, XPopupImageLoader imageLoader,
                                              OnImageViewerLongPressListener longPressListener) {
        ImageViewerPopupView popupView = new ImageViewerPopupView(this.context)
                .setSrcView(srcView, currentPosition)
                .setImageUrls(urls)
                .isInfinite(isInfinite)
                .isShowPlaceholder(isShowPlaceHolder)
                .setPlaceholderColor(placeholderColor)
                .setPlaceholderStrokeColor(placeholderStroke)
                .setPlaceholderRadius(placeholderRadius)
                .isShowSaveButton(isShowSaveBtn)
                .setBgColor(bgColor)
                .setSrcViewUpdateListener(srcViewUpdateListener)
                .setXPopupImageLoader(imageLoader)
                .setLongPressListener(longPressListener);
        popupView.popupInfo = this.popupInfo;
        return popupView;
    }

    public BasePopupView asCustom(BasePopupView popupView) {
        popupView.popupInfo = this.popupInfo;
        return popupView;
    }

}
