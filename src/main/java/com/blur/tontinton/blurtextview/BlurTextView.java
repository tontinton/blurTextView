package com.blur.tontinton.blurtextview;

import android.content.Context;
import android.content.res.TypedArray;
import android.graphics.Bitmap;
import android.graphics.Canvas;
import android.graphics.Paint;
import android.graphics.drawable.BitmapDrawable;
import android.os.Build;
import android.util.AttributeSet;
import android.util.Log;
import android.view.View;
import android.view.ViewTreeObserver;
import android.widget.TextView;

public class BlurTextView extends TextView {

    private static final String TAG = BlurTextView.class.getSimpleName();

    public static final int ERROR_CODE = -69;
    private boolean mIsChanging;
    private boolean mIsAsync;
    private float mRadius;
    private float mScaleFactor;
    private View mBackgroundView;
    private int mBackViewId;

    public BlurTextView(Context context, AttributeSet attrs) {
        super(context, attrs);

        TypedArray a = context.getTheme().obtainStyledAttributes(
                attrs,
                R.styleable.BlurTextView,
                0, 0);

        try {
            mIsChanging = a.getBoolean(R.styleable.BlurTextView_changing, false);
            mIsAsync = a.getBoolean(R.styleable.BlurTextView_isAsync, true);
            mRadius = a.getFloat(R.styleable.BlurTextView_radius, 20) / 10;
            mScaleFactor = a.getFloat(R.styleable.BlurTextView_scaleFactor, 1) * 8;
            mBackViewId = a.getResourceId(R.styleable.BlurTextView_backgroundView, ERROR_CODE);
        } finally {
            a.recycle();
        }

        if (mBackViewId == ERROR_CODE) {
            throw new IllegalStateException("You didn't input app:backgroundView ???");
        }
    }

    @Override
    protected void onAttachedToWindow() {
        super.onAttachedToWindow();

        mBackgroundView = getRootView().findViewById(mBackViewId);

        if (mBackgroundView == null) {
            throw new IllegalStateException("Wrong id reference set as app:backgroundView.");
        }

        startBlur();
    }

    private void startBlur() {
        mBackgroundView.getViewTreeObserver().addOnPreDrawListener(new ViewTreeObserver.OnPreDrawListener() {
            @Override
            public boolean onPreDraw() {
                Bitmap bmp;
                if (!mIsChanging) {
                    mBackgroundView.getViewTreeObserver().removeOnPreDrawListener(this);
                    mBackgroundView.buildDrawingCache();
                    bmp = mBackgroundView.getDrawingCache();
                } else {
                    bmp = ((BitmapDrawable) mBackgroundView.getBackground()).getBitmap();
                }

                blur(bmp);
                return true;
            }
        });
    }

    private void blur(final Bitmap bkg) {
        final long startMs = System.currentTimeMillis();

        Bitmap overlay = Bitmap.createBitmap((int) (getMeasuredWidth() / mScaleFactor),
                (int) (getMeasuredHeight() / mScaleFactor), Bitmap.Config.ARGB_8888);
        Canvas canvas = new Canvas(overlay);
        canvas.translate(-getLeft() / mScaleFactor, -getTop() / mScaleFactor);
        canvas.scale(1 / mScaleFactor, 1 / mScaleFactor);
        Paint paint = new Paint();
        paint.setFlags(Paint.FILTER_BITMAP_FLAG);
        canvas.drawBitmap(bkg, 0, 0, paint);

        if (mIsAsync) {
            BlurUtils.asyncFastblur(overlay, 4 / mScaleFactor, (int) mRadius, new BitmapLoader() {
                @Override
                public void onSuccess(final Bitmap bitmap) {
                    if (bitmap == null) {
                        return;
                    }

                    post(new Runnable() {
                        @Override
                        public void run() {
                            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.JELLY_BEAN) {
                                setBackground(new BitmapDrawable(getResources(), bitmap));
                            } else {
                                setBackgroundDrawable(new BitmapDrawable(getResources(), bitmap));
                            }
                        }
                    });

                    Log.wtf(TAG, "Time of blur: " + String.valueOf(System.currentTimeMillis() - startMs) + " MS");
                }
            });
        } else {
            Bitmap bitmap = BlurUtils.syncFastblur(overlay, 4 / mScaleFactor, (int) mRadius);

            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.JELLY_BEAN) {
                setBackground(new BitmapDrawable(getResources(), bitmap));
            } else {
                setBackgroundDrawable(new BitmapDrawable(getResources(), bitmap));
            }
            Log.wtf(TAG, "Time of blur: " + String.valueOf(System.currentTimeMillis() - startMs) + " MS");
        }
    }
}
