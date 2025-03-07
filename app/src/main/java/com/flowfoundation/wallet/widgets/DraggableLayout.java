package com.flowfoundation.wallet.widgets;

import android.content.Context;
import android.content.res.Configuration;
import android.os.Handler;
import android.os.Looper;
import android.util.AttributeSet;
import android.view.MotionEvent;
import android.view.ViewConfiguration;
import android.view.ViewGroup;
import android.widget.FrameLayout;

import com.zackratos.ultimatebarx.ultimatebarx.UltimateBarXExposedKt;

import java.util.ArrayList;
import java.util.List;


public class DraggableLayout extends FrameLayout {

    public static final int MARGIN_EDGE = 0;
    private float mOriginalRawX;
    private float mOriginalRawY;
    private float mOriginalX;
    private float mOriginalY;
    private static final int TOUCH_TIME_THRESHOLD = 150;
    private long mLastTouchDownTime;
    protected MoveAnimator mMoveAnimator;
    protected int mScreenWidth;
    private int mScreenHeight;
    private int mStatusBarHeight;
    private boolean isNearestLeft = true;
    private float mPortraitY;
    private boolean dragEnable = true;
    private boolean autoMoveToEdge = true;

    private OnClickListener onClickListener;

    private final List<OnDragListener> onDragListeners = new ArrayList<>();

    public DraggableLayout(Context context) {
        this(context, null);
    }

    public DraggableLayout(Context context, AttributeSet attrs) {
        this(context, attrs, 0);
    }

    public DraggableLayout(Context context, AttributeSet attrs, int defStyleAttr) {
        super(context, attrs, defStyleAttr);
        init();
    }

    private void init() {
        mMoveAnimator = new MoveAnimator();
        mStatusBarHeight = UltimateBarXExposedKt.getStatusBarHeight();
        setClickable(true);
    }

    public void updateDragState(boolean dragEnable) {
        this.dragEnable = dragEnable;
    }

    public void setAutoMoveToEdge(boolean autoMoveToEdge) {
        this.autoMoveToEdge = autoMoveToEdge;
    }

    @Override
    public boolean onTouchEvent(MotionEvent event) {
        if (event == null) {
            return false;
        }
        switch (event.getAction()) {
            case MotionEvent.ACTION_DOWN:
                dealDragStartEvent();
                break;
            case MotionEvent.ACTION_MOVE:
                updateViewPosition(event);
                break;
            case MotionEvent.ACTION_CANCEL:
            case MotionEvent.ACTION_UP:
                clearPortraitY();
                if (autoMoveToEdge) {
                    moveToEdge();
                }
                if (isOnClickEvent()) {
                    dealClickEvent();
                }
                dealDragEndEvent();
                break;
        }
        return true;
    }

    protected void dealClickEvent() {
        if (onClickListener != null) {
            onClickListener.onClick(this);
        }
    }

    protected void dealDragStartEvent() {
        for (OnDragListener listener : onDragListeners) {
            listener.onDragStart();
        }
    }

    protected void dealDragMoveEvent() {
        for (OnDragListener listener : onDragListeners) {
            listener.onDrag(mOriginalX, mOriginalY, getX(), getY());
        }
    }

    protected void dealDragEndEvent() {
        for (OnDragListener listener : onDragListeners) {
            listener.onDragEnd();
        }
    }

    protected boolean isOnClickEvent() {
        return System.currentTimeMillis() - mLastTouchDownTime < TOUCH_TIME_THRESHOLD;
    }

    private void updateViewPosition(MotionEvent event) {
        //dragEnable
        if (!dragEnable) return;
        //占满width或height时不用变
        LayoutParams params = (LayoutParams) getLayoutParams();
        //限制不可超出屏幕宽度
        float desX = mOriginalX + event.getRawX() - mOriginalRawX;
        if (params.width == FrameLayout.LayoutParams.WRAP_CONTENT) {
            if (desX < 0) {
                desX = MARGIN_EDGE;
            }
            if (desX > mScreenWidth) {
                desX = mScreenWidth - MARGIN_EDGE;
            }
            setX(desX);
        }
        // 限制不可超出屏幕高度
        float desY = mOriginalY + event.getRawY() - mOriginalRawY;
        if (params.height == FrameLayout.LayoutParams.WRAP_CONTENT) {
            if (desY < mStatusBarHeight) {
                desY = mStatusBarHeight;
            }
            if (desY > mScreenHeight - getHeight()) {
                desY = mScreenHeight - getHeight();
            }
            setY(desY);
        }

        dealDragMoveEvent();
    }

    private void changeOriginalTouchParams(MotionEvent event) {
        mOriginalX = getX();
        mOriginalY = getY();
        mOriginalRawX = event.getRawX();
        mOriginalRawY = event.getRawY();
        mLastTouchDownTime = System.currentTimeMillis();
    }

    protected void updateSize() {
        ViewGroup viewGroup = (ViewGroup) getParent();
        if (viewGroup != null) {
            mScreenWidth = viewGroup.getWidth() - getWidth();
            mScreenHeight = viewGroup.getHeight();
        }
    }

    public void moveToEdge() {
        //dragEnable
        if (!dragEnable) return;
        moveToEdge(isNearestLeft(), false);
    }

    public void moveToEdge(boolean isLeft, boolean isLandscape) {
        float moveDistance = isLeft ? MARGIN_EDGE : mScreenWidth - MARGIN_EDGE;
        float y = getY();
        if (!isLandscape && mPortraitY != 0) {
            y = mPortraitY;
            clearPortraitY();
        }
        mMoveAnimator.start(moveDistance, Math.min(Math.max(0, y), mScreenHeight - getHeight()));
    }

    private void clearPortraitY() {
        mPortraitY = 0;
    }

    public boolean isNearestLeft() {
        int middle = mScreenWidth / 2;
        isNearestLeft = getX() < middle;
        return isNearestLeft;
    }

    protected class MoveAnimator implements Runnable {

        private final Handler handler = new Handler(Looper.getMainLooper());
        private float destinationX;
        private float destinationY;
        private long startingTime;

        void start(float x, float y) {
            this.destinationX = x;
            this.destinationY = y;
            startingTime = System.currentTimeMillis();
            handler.post(this);
        }

        @Override
        public void run() {
            if (getRootView() == null || getRootView().getParent() == null) {
                return;
            }
            float progress = Math.min(1, (System.currentTimeMillis() - startingTime) / 400f);
            float deltaX = (destinationX - getX()) * progress;
            float deltaY = (destinationY - getY()) * progress;
            move(deltaX, deltaY);
            if (progress < 1) {
                handler.post(this);
            }
        }

        private void stop() {
            handler.removeCallbacks(this);
        }
    }


    private void move(float deltaX, float deltaY) {
        setX(getX() + deltaX);
        setY(getY() + deltaY);
    }

    @Override
    protected void onConfigurationChanged(Configuration newConfig) {
        super.onConfigurationChanged(newConfig);
        if (getParent() != null) {
            final boolean isLandscape = newConfig.orientation == Configuration.ORIENTATION_LANDSCAPE;
            markPortraitY(isLandscape);
            ((ViewGroup) getParent()).post(new Runnable() {
                @Override
                public void run() {
                    updateSize();
                    moveToEdge(isNearestLeft, isLandscape);
                }
            });
        }
    }

    private void markPortraitY(boolean isLandscape) {
        if (isLandscape) {
            mPortraitY = getY();
        }
    }

    private float touchDownX;

    private void initTouchDown(MotionEvent ev) {
        changeOriginalTouchParams(ev);
        updateSize();
        mMoveAnimator.stop();
    }

    @Override
    public boolean onInterceptTouchEvent(MotionEvent ev) {
        boolean intercepted = false;
        switch (ev.getActionMasked()) {
            case MotionEvent.ACTION_DOWN:
                intercepted = false;
                touchDownX = ev.getX();
                initTouchDown(ev);
                break;
            case MotionEvent.ACTION_MOVE:
                intercepted = Math.abs(touchDownX - ev.getX()) >= ViewConfiguration.get(getContext()).getScaledTouchSlop();
                break;
            case MotionEvent.ACTION_UP:
                intercepted = false;
                break;
        }
        return intercepted;
    }

    @Override
    public void setOnClickListener(OnClickListener onClickListener) {
        this.onClickListener = onClickListener;
    }

    public void addOnDragListener(OnDragListener onDragListener) {
        this.onDragListeners.add(onDragListener);
    }

    public interface OnDragListener {
        void onDrag(float originX, float originY, float x, float y);

        void onDragEnd();

        void onDragStart();
    }
}

