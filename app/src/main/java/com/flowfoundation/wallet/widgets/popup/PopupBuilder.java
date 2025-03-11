package com.flowfoundation.wallet.widgets.popup;

import android.content.Context;
import android.view.View;

import com.lxj.xpopup.core.PopupInfo;

public class PopupBuilder {
    public final PopupInfo popupInfo = new PopupInfo();
    private final Context context;

    public PopupBuilder(Context context) {
        this.context = context;
    }

    public PopupBuilder hasShadowBg(Boolean hasShadowBg) {
        this.popupInfo.hasShadowBg = hasShadowBg;
        return this;
    }

    public PopupBuilder atView(View atView) {
        popupInfo.atView = atView;
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


    public PopupBuilder offsetX(int offsetX) {
        this.popupInfo.offsetX = offsetX;
        return this;
    }

    public PopupBuilder offsetY(int offsetY) {
        this.popupInfo.offsetY = offsetY;
        return this;
    }


    public PopupBuilder isDestroyOnDismiss(boolean isDestroyOnDismiss) {
        this.popupInfo.isDestroyOnDismiss = isDestroyOnDismiss;
        return this;
    }


    public PopupBuilder isViewMode(boolean viewMode) {
        this.popupInfo.isViewMode = viewMode;
        return this;
    }


}
