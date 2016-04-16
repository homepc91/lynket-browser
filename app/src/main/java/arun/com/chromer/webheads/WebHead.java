package arun.com.chromer.webheads;

import android.animation.ValueAnimator;
import android.annotation.SuppressLint;
import android.content.Context;
import android.graphics.Canvas;
import android.graphics.Paint;
import android.graphics.PixelFormat;
import android.graphics.Point;
import android.graphics.PorterDuff;
import android.graphics.Rect;
import android.graphics.Typeface;
import android.graphics.drawable.Drawable;
import android.support.annotation.NonNull;
import android.util.DisplayMetrics;
import android.view.GestureDetector;
import android.view.Gravity;
import android.view.LayoutInflater;
import android.view.MotionEvent;
import android.view.View;
import android.view.ViewConfiguration;
import android.view.WindowManager;
import android.view.animation.BounceInterpolator;
import android.widget.FrameLayout;
import android.widget.ImageView;

import com.facebook.rebound.SimpleSpringListener;
import com.facebook.rebound.Spring;
import com.facebook.rebound.SpringConfig;
import com.facebook.rebound.SpringSystem;

import java.net.URL;

import arun.com.chromer.R;
import arun.com.chromer.preferences.Preferences;
import arun.com.chromer.util.Util;
import timber.log.Timber;

/**
 * Created by Arun on 30/01/2016.
 */
@SuppressLint("ViewConstructor")
public class WebHead extends FrameLayout {

    private static int WEB_HEAD_COUNT = 0;

    private static final int STACKING_GAP_PX = Util.dpToPx(6);

    private static final double MAGNETISM_THRESHOLD = Util.dpToPx(120);

    private static WindowManager sWindowManager;

    private static Point sCentreLockPoint;

    private static int sDispHeight, sDispWidth;

    private final String mUrl;

    private final GestureDetector mGestDetector = new GestureDetector(getContext(), new GestureTapListener());

    private final GestureDetector mFlingDetector = new GestureDetector(getContext(), new FlingListener());

    private final int mTouchSlop = ViewConfiguration.get(getContext()).getScaledTouchSlop();

    private float posX, posY;

    private int initialDownX, initialDownY;

    private int mXVelocity, mYVelocity;

    private WindowManager.LayoutParams mWindowParams;

    private SpringSystem mSpringSystem;

    private Spring mScaleSpring, mWallAttachSpring, mXSpring, mYSpring;

    private SpringConfig mSnapSpringConfig = SpringConfig.fromOrigamiTensionAndFriction(100, 7);

    private SpringConfig mFlingSpringConfig = SpringConfig.fromOrigamiTensionAndFriction(20, 5);

    private boolean mDragging;

    private boolean mWasRemoveLocked;

    private boolean mDimmed;

    private boolean mUserManuallyMoved;

    private boolean isBeingDestroyed;

    private WebHeadCircle circleView;

    private ImageView mFavicon;

    private ImageView mAppIcon;

    private WebHeadInteractionListener mInteractionListener;

    private MovementTracker mMovementTracker;

    public WebHead(@NonNull Context context, @NonNull String url) {
        super(context);
        mUrl = url;

        if (sWindowManager == null) {
            sWindowManager = (WindowManager) context.getSystemService(Context.WINDOW_SERVICE);
        }

        init(context, url);

        WEB_HEAD_COUNT++;

        Timber.d("Created %d webheads", WEB_HEAD_COUNT);
    }


    private void init(Context context, String url) {
        circleView = new WebHeadCircle(context, url);
        addView(circleView);

        mWindowParams = new WindowManager.LayoutParams(
                WindowManager.LayoutParams.WRAP_CONTENT,
                WindowManager.LayoutParams.WRAP_CONTENT,
                WindowManager.LayoutParams.TYPE_SYSTEM_ALERT,
                WindowManager.LayoutParams.FLAG_NOT_FOCUSABLE | WindowManager.LayoutParams.FLAG_LAYOUT_NO_LIMITS,
                PixelFormat.TRANSLUCENT);

        setDisplayMetrics();
        setSpawnLocation();
        setUpSprings();
    }

    private void initFavicon() {
        if (mFavicon == null) {
            mFavicon = (ImageView) LayoutInflater.from(getContext()).inflate(R.layout.favicon_layout, this, false);
            addView(mFavicon);
        }
    }

    private void initAppIcon() {
        if (mAppIcon == null) {
            mAppIcon = (ImageView) LayoutInflater.from(getContext()).inflate(R.layout.web_head_app_indicator_layout, this, false);
            addView(mAppIcon);
        }
    }

    private void setUpSprings() {
        mSpringSystem = SpringSystem.create();

        mScaleSpring = mSpringSystem.createSpring();
        mScaleSpring.addListener(new SimpleSpringListener() {
            @Override
            public void onSpringUpdate(Spring spring) {
                float value = (float) spring.getCurrentValue();
                circleView.setScaleX(value);
                circleView.setScaleY(value);
                if (mFavicon != null) {
                    mFavicon.setScaleY(value);
                    mFavicon.setScaleX(value);
                }
            }
        });

        mWallAttachSpring = mSpringSystem.createSpring();
        mWallAttachSpring.setSpringConfig(SpringConfig.fromOrigamiTensionAndFriction(40, 6));
        mWallAttachSpring.addListener(new SimpleSpringListener() {
            @Override
            public void onSpringUpdate(Spring spring) {
                if (!mDragging) {
                    mWindowParams.x = (int) spring.getCurrentValue();
                    sWindowManager.updateViewLayout(WebHead.this, mWindowParams);
                }
            }
        });

        mYSpring = mSpringSystem.createSpring();
        mYSpring.setSpringConfig(mSnapSpringConfig);
        mYSpring.addListener(new SimpleSpringListener() {
            @Override
            public void onSpringUpdate(Spring spring) {
                mWindowParams.y = (int) spring.getCurrentValue();
                sWindowManager.updateViewLayout(WebHead.this, mWindowParams);
            }
        });

        mXSpring = mSpringSystem.createSpring();
        mYSpring.setSpringConfig(mSnapSpringConfig);
        mXSpring.addListener(new SimpleSpringListener() {
            @Override
            public void onSpringUpdate(Spring spring) {
                mWindowParams.x = (int) spring.getCurrentValue();
                sWindowManager.updateViewLayout(WebHead.this, mWindowParams);
            }
        });
    }

    private void setDisplayMetrics() {
        final DisplayMetrics metrics = new DisplayMetrics();
        sWindowManager.getDefaultDisplay().getMetrics(metrics);
        sDispWidth = metrics.widthPixels;
        sDispHeight = metrics.heightPixels;

        mMovementTracker = new MovementTracker(20, sDispHeight, sDispWidth, WebHeadCircle.getSizePx());
    }

    @SuppressLint("RtlHardcoded")
    private void setSpawnLocation() {
        mWindowParams.gravity = Gravity.TOP | Gravity.LEFT;
        if (Preferences.webHeadsSpawnLocation(getContext()) == 1) {
            mWindowParams.x = (int) (sDispWidth - WebHeadCircle.getSizePx() * 0.8);
        } else {
            mWindowParams.x = (int) (0 - WebHeadCircle.getSizePx() * 0.2);
        }
        mWindowParams.y = sDispHeight / 3;
    }

    private RemoveWebHead getRemoveWebHead() {
        return RemoveWebHead.get(getContext());
    }

    @Override
    public boolean onTouchEvent(MotionEvent event) {
        // Don't react to any touch event and consume it when we are being destroyed
        if (isBeingDestroyed) return true;
        try {
            mGestDetector.onTouchEvent(event);

            boolean wasFlung = mFlingDetector.onTouchEvent(event);

            switch (event.getAction()) {
                case MotionEvent.ACTION_DOWN:
                    mMovementTracker.onDown();

                    initialDownX = mWindowParams.x;
                    initialDownY = mWindowParams.y;

                    posX = event.getRawX();
                    posY = event.getRawY();

                    // Shrink on touch
                    setTouchingScale();

                    // transparent on touch
                    setTouchingAlpha();

                    break;
                case MotionEvent.ACTION_UP:
                    mMovementTracker.onUp();

                    mDragging = false;

                    if (mWasRemoveLocked) {
                        // If head was locked onto a remove bubble before, then kill ourselves
                        destroySelf(true);
                        return true;
                    }

                    // Expand on release
                    setReleaseScale();

                    // opaque on release
                    setReleaseAlpha();

                    // If we were not flung, go to nearest side and rest there
                    if (!wasFlung)
                        stickToWall();

                    // hide remove view
                    RemoveWebHead.hideSelf();

                    break;
                case MotionEvent.ACTION_MOVE:
                    mMovementTracker.addMovement(event);

                    if (Math.hypot(event.getRawX() - posX, event.getRawY() - posY) > mTouchSlop) {
                        mDragging = true;
                    }

                    if (mDragging) {
                        move(event);
                        // Compute velocity
                        // mVelocityTracker.computeCurrentVelocity(1000);
                    }
                default:
                    break;
            }
        } catch (NullPointerException e) {
            destroySelf(true);
        }
        return true;
    }

    @Override
    protected void onAttachedToWindow() {
        super.onAttachedToWindow();

        // Spring scale animation when getting attached to window
        mScaleSpring.setCurrentValue(0);
        mScaleSpring.setEndValue(1f);
    }

    private void stickToWall() {
        int x = mWindowParams.x;
        int dispCentre = sDispWidth / 2;

        mWallAttachSpring.setCurrentValue(x, true);

        int xOffset = (getAdaptWidth() / 2);

        if ((x + xOffset) >= dispCentre) {
            // move to right wall
            mWallAttachSpring.setEndValue(sDispWidth - (getAdaptWidth() * 0.8));
        } else {
            // move to left wall
            mWallAttachSpring.setEndValue(0 - (getAdaptWidth() * 0.2));
        }
    }

    private void move(@NonNull MotionEvent event) {
        getRemoveWebHead().reveal();

        mUserManuallyMoved = true;

        mWindowParams.x = (int) (initialDownX + (event.getRawX() - posX));
        mWindowParams.y = (int) (initialDownY + (event.getRawY() - posY));

        if (isNearRemoveCircle()) {
            getRemoveWebHead().grow();
            setReleaseAlpha();
            setReleaseScale();

            mXSpring.setSpringConfig(mSnapSpringConfig);
            mYSpring.setSpringConfig(mSnapSpringConfig);

            mXSpring.setEndValue(getCentreLockPoint().x);
            mYSpring.setEndValue(getCentreLockPoint().y);
        } else {
            getRemoveWebHead().shrink();

            mXSpring.setCurrentValue(mWindowParams.x);
            mYSpring.setCurrentValue(mWindowParams.y);

            setTouchingAlpha();
            setTouchingScale();
        }
    }

    private boolean isNearRemoveCircle() {
        Point p = getRemoveWebHead().getCenterCoordinates();
        int rX = p.x;
        int rY = p.y;

        int offset = getAdaptWidth() / 2;
        int x = mWindowParams.x + offset;
        int y = mWindowParams.y + offset;

        if (euclideanDist(rX, rY, x, y) < MAGNETISM_THRESHOLD) {
            mWasRemoveLocked = true;
            return true;
        } else {
            mWasRemoveLocked = false;
            return false;
        }
    }

    private void setReleaseScale() {
        mScaleSpring.setEndValue(1f);
    }

    private void setReleaseAlpha() {
        if (!mDimmed) {
            circleView.setAlpha(1f);
            if (mFavicon != null) {
                mFavicon.setAlpha(1f);
            }
        }
    }

    private void setTouchingAlpha() {
        if (!mDimmed) {
            circleView.setAlpha(0.7f);
            if (mFavicon != null) {
                mFavicon.setAlpha(0.7f);
            }
        }
    }

    private void setTouchingScale() {
        mScaleSpring.setEndValue(0.8f);
    }

    private int getAdaptWidth() {
        return Math.max(getWidth(), WebHeadCircle.getSizePx());
    }

    private Point getCentreLockPoint() {
        if (sCentreLockPoint == null) {
            Point removeCentre = getRemoveWebHead().getCenterCoordinates();
            int offset = getAdaptWidth() / 2;
            int x = removeCentre.x - offset;
            int y = removeCentre.y - offset;
            sCentreLockPoint = new Point(x, y);
        }
        return sCentreLockPoint;
    }

    private double euclideanDist(int x1, int y1, int x2, int y2) {
        double x = x1 - x2;
        double y = y1 - y2;
        return Math.sqrt(x * x + y * y);
    }

    public ValueAnimator getStackDistanceAnimator() {
        ValueAnimator animator = null;
        if (!mUserManuallyMoved) {
            animator = ValueAnimator.ofInt(mWindowParams.y, mWindowParams.y + STACKING_GAP_PX);
            animator.setInterpolator(new BounceInterpolator());
            animator.addUpdateListener(new ValueAnimator.AnimatorUpdateListener() {
                @Override
                public void onAnimationUpdate(ValueAnimator animation) {
                    mWindowParams.y = (int) animation.getAnimatedValue();
                    sWindowManager.updateViewLayout(WebHead.this, mWindowParams);
                }
            });
        }
        return animator;
    }

    @NonNull
    public ImageView getFaviconView() {
        initFavicon();
        return mFavicon;
    }

    public WindowManager.LayoutParams getWindowParams() {
        return mWindowParams;
    }

    public String getUrl() {
        return mUrl;
    }

    public void dim() {
        if (!mDimmed) {
            circleView.setAlpha(0.3f);
            if (mFavicon != null) {
                mFavicon.setAlpha(0.3f);
            }
            mDimmed = true;
        }
    }

    public void bright() {
        if (mDimmed) {
            circleView.setAlpha(1f);
            if (mFavicon != null) {
                mFavicon.setAlpha(0.1f);
            }
            mDimmed = false;
        }
    }

    public void setWebHeadInteractionListener(WebHeadInteractionListener listener) {
        mInteractionListener = listener;
    }

    public void setFaviconDrawable(@NonNull Drawable drawable) {
        circleView.clearUrlIndicator();
        initFavicon();
        mFavicon.setImageDrawable(drawable);
        initAppIcon();
    }

    private boolean isLastWebHead() {
        return WEB_HEAD_COUNT - 1 == 0;
    }

    public void destroySelf(boolean shouldReceiveCallback) {
        isBeingDestroyed = true;

        if (mInteractionListener != null && shouldReceiveCallback) {
            mInteractionListener.onWebHeadDestroy(this, isLastWebHead());
        }

        WEB_HEAD_COUNT--;

        Timber.d("%d Webheads remaining", WEB_HEAD_COUNT);

        if (mWallAttachSpring != null) {
            mWallAttachSpring.setAtRest().destroy();
            mWallAttachSpring = null;
        }

        if (mScaleSpring != null) {
            mScaleSpring.setAtRest().destroy();
            mScaleSpring = null;
        }

        if (mYSpring != null) {
            mYSpring.setAtRest().destroy();
            mYSpring = null;
        }

        if (mXSpring != null) {
            mXSpring.setAtRest().destroy();
            mXSpring = null;
        }

        mSpringSystem = null;

        setWebHeadInteractionListener(null);

        RemoveWebHead.hideSelf();

        removeView(circleView);

        if (mFavicon != null) removeView(mFavicon);
        if (mAppIcon != null) removeView(mAppIcon);

        circleView = null;
        mFavicon = null;
        mAppIcon = null;

        if (sWindowManager != null)
            sWindowManager.removeView(this);
    }

    public interface WebHeadInteractionListener {
        void onWebHeadClick(@NonNull WebHead webHead);

        void onWebHeadDestroy(@NonNull WebHead webHead, boolean isLastWebHead);
    }

    private class GestureTapListener extends GestureDetector.SimpleOnGestureListener {
        @Override
        public boolean onSingleTapConfirmed(MotionEvent e) {
            if (mInteractionListener != null) mInteractionListener.onWebHeadClick(WebHead.this);

            RemoveWebHead.hideSelf();

            return super.onSingleTapConfirmed(e);
        }

    }

    /**
     * A gesture listener class to monitor standard fling events on the web head view.
     */
    private class FlingListener extends GestureDetector.SimpleOnGestureListener {

        /**
         * The event is used as a trigger to calculate the fling end point and then animate to
         * the end point. If the movement tracker object gives a proper projection then use it.
         * Else manually calculate projection using @param e1 and @param @e2.
         *
         * @param e1        fling starting event
         * @param e2        fling ending event
         * @param velocityX velocity in x direction
         * @param velocityY velocity in y direction
         * @return true if the fling was successful
         */
        @Override
        public boolean onFling(MotionEvent e1, MotionEvent e2, float velocityX, float velocityY) {
            // double velocity = Math.sqrt(velocityX * velocityX + velocityY * velocityY);

            // if (velocity < 500) return false;

            Coordinate down = Coordinate.FromMotionEvent(e1);
            Coordinate up = Coordinate.FromMotionEvent(e2);
            Coordinate projectedPoint = mMovementTracker.getProjection();

            if (projectedPoint == null) {
                // Timber.v("Calculating projection with fling events");
                projectedPoint = MovementTracker.calculateTrajectory(down, up);
            } else {
                //  Timber.v("Using predicted trajectory");
            }

            if (projectedPoint != null) {
                mXSpring.setSpringConfig(mFlingSpringConfig);
                mYSpring.setSpringConfig(mFlingSpringConfig);

                mXSpring.setAtRest();
                mYSpring.setAtRest();

                mXSpring.setCurrentValue(mWindowParams.x);
                mYSpring.setCurrentValue(mWindowParams.y);

                mXSpring.setEndValue(projectedPoint.x);
                mYSpring.setEndValue(projectedPoint.y);
                return true;
            }
            return false;
        }
    }

    @SuppressLint("ViewConstructor")
    public static class WebHeadCircle extends View {

        private final String mUrl;
        private final Paint mBgPaint;
        private final Paint mTextPaint;
        private boolean mShouldDrawText = true;
        private static int sSizePx;
        private static int sDiameterPx;

        public WebHeadCircle(Context context, String url) {
            super(context);
            mUrl = url;

            int webHeadsColor = Preferences.webHeadColor(context);
            float shadwR = context.getResources().getDimension(R.dimen.web_head_shadow_radius);
            float shadwDx = context.getResources().getDimension(R.dimen.web_head_shadow_dx);
            float shadwDy = context.getResources().getDimension(R.dimen.web_head_shadow_dy);
            float textSize = context.getResources().getDimension(R.dimen.web_head_text_indicator);

            mBgPaint = new Paint(Paint.ANTI_ALIAS_FLAG);
            mBgPaint.setColor(webHeadsColor);
            mBgPaint.setStyle(Paint.Style.FILL);
            mBgPaint.setShadowLayer(shadwR, shadwDx, shadwDy, 0x75000000);

            sSizePx = context.getResources().getDimensionPixelSize(R.dimen.web_head_size_normal);

            mTextPaint = new Paint(Paint.ANTI_ALIAS_FLAG);
            mTextPaint.setTypeface(Typeface.defaultFromStyle(Typeface.NORMAL));
            mTextPaint.setTextSize(textSize);
            mTextPaint.setColor(Util.getForegroundTextColor(webHeadsColor));
            mTextPaint.setStyle(Paint.Style.FILL);

            setLayerType(View.LAYER_TYPE_SOFTWARE, null);
        }

        @Override
        protected void onMeasure(int widthMeasureSpec, int heightMeasureSpec) {
            super.onMeasure(widthMeasureSpec, heightMeasureSpec);
            setMeasuredDimension(sSizePx, sSizePx);
        }

        @Override
        protected void onDraw(Canvas canvas) {
            canvas.drawColor(0, PorterDuff.Mode.CLEAR);
            float radius = (float) (getWidth() / 2.4);
            canvas.drawCircle(getWidth() / 2, getHeight() / 2, radius, mBgPaint);
            if (mShouldDrawText) {
                drawText(canvas);
            }
            sDiameterPx = (int) (2 * radius);
        }

        public void clearUrlIndicator() {
            if (mShouldDrawText) {
                mShouldDrawText = false;
                invalidate();
            }
        }

        @SuppressWarnings("unused")
        public void showUrlIndicator() {
            if (!mShouldDrawText) {
                mShouldDrawText = true;
                invalidate();
            }
        }

        private void drawText(Canvas canvas) {
            String indicator = getUrlIndicator();
            if (indicator != null) drawTextInCanvasCentre(canvas, mTextPaint, indicator);
        }

        private String getUrlIndicator() {
            String result = "x";
            if (mUrl != null) {
                try {
                    URL url = new URL(mUrl);
                    String host = url.getHost();
                    if (host != null && host.length() != 0) {
                        if (host.startsWith("www")) {
                            String[] splits = host.split("\\.");
                            if (splits.length > 1) result = String.valueOf(splits[1].charAt(0));
                            else result = String.valueOf(splits[0].charAt(0));
                        } else
                            result = String.valueOf(host.charAt(0));
                    }
                } catch (Exception e) {
                    return result;
                }
            }
            return result.toUpperCase();
        }

        private void drawTextInCanvasCentre(Canvas canvas, Paint paint, String text) {
            int cH = canvas.getClipBounds().height();
            int cW = canvas.getClipBounds().width();
            Rect rect = new Rect();
            paint.setTextAlign(Paint.Align.LEFT);
            paint.getTextBounds(text, 0, text.length(), rect);
            float x = cW / 2f - rect.width() / 2f - rect.left;
            float y = cH / 2f + rect.height() / 2f - rect.bottom;
            canvas.drawText(text, x, y, paint);
        }

        public static int getSizePx() {
            return sSizePx;
        }

        public static int getDiameterPx() {
            return sDiameterPx;
        }
    }
}
