package com.flowfoundation.wallet.widgets.likebutton;


import androidx.annotation.DrawableRes;

/**
 * Created by Joel on 23/12/2015.
 */
public class Icon {
    private final int onIconResourceId;
    private final int offIconResourceId;
    private final IconType iconType;

    public Icon(@DrawableRes int onIconResourceId, @DrawableRes int offIconResourceId, IconType iconType) {
        this.onIconResourceId = onIconResourceId;
        this.offIconResourceId = offIconResourceId;
        this.iconType = iconType;
    }

    public int getOffIconResourceId() {
        return offIconResourceId;
    }

    public int getOnIconResourceId() {
        return onIconResourceId;
    }

    public IconType getIconType() {
        return iconType;
    }

}
