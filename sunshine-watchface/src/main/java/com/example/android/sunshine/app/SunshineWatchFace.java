/*
 * Copyright (C) 2014 The Android Open Source Project
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

package com.example.android.sunshine.app;

import android.content.BroadcastReceiver;
import android.content.Context;
import android.content.Intent;
import android.content.IntentFilter;
import android.content.res.Resources;
import android.graphics.Bitmap;
import android.graphics.BitmapFactory;
import android.graphics.Canvas;
import android.graphics.Color;
import android.graphics.Paint;
import android.graphics.Rect;
import android.graphics.Typeface;
import android.graphics.drawable.Drawable;
import android.os.Bundle;
import android.os.Handler;
import android.os.Message;
import android.support.wearable.watchface.CanvasWatchFaceService;
import android.support.wearable.watchface.WatchFaceStyle;
import android.view.SurfaceHolder;
import android.view.WindowInsets;
import android.widget.ImageView;
import android.widget.Toast;

import java.lang.ref.WeakReference;
import java.text.SimpleDateFormat;
import java.util.Calendar;
import java.util.Date;
import java.util.TimeZone;
import java.util.concurrent.TimeUnit;

/**
 * Digital watch face with seconds. In ambient mode, the seconds aren't displayed. On devices with
 * low-bit ambient mode, the text is drawn without anti-aliasing in ambient mode.
 *
 * Displays sunshine weather updates on watchface.
 *
 * @author Sagar Rathod
 */
public class SunshineWatchFace extends CanvasWatchFaceService {
    private static final Typeface NORMAL_TYPEFACE =
            Typeface.create(Typeface.SANS_SERIF, Typeface.NORMAL);

    /**
     * Update rate in milliseconds for interactive mode. We update once a second since seconds are
     * displayed in interactive mode.
     */
    private static final long INTERACTIVE_UPDATE_RATE_MS = TimeUnit.SECONDS.toMillis(1);

    /**
     * Handler message id for updating the time periodically in interactive mode.
     */
    private static final int MSG_UPDATE_TIME = 0;

    @Override
    public Engine onCreateEngine() {
        return new Engine();
    }

    private static class EngineHandler extends Handler {
        private final WeakReference<SunshineWatchFace.Engine> mWeakReference;

        public EngineHandler(SunshineWatchFace.Engine reference) {
            mWeakReference = new WeakReference<>(reference);
        }

        @Override
        public void handleMessage(Message msg) {
            SunshineWatchFace.Engine engine = mWeakReference.get();
            if (engine != null) {
                switch (msg.what) {
                    case MSG_UPDATE_TIME:
                        engine.handleUpdateTimeMessage();
                        break;
                }
            }
        }
    }

    private class Engine extends CanvasWatchFaceService.Engine {
        final Handler mUpdateTimeHandler = new EngineHandler(this);
        boolean mRegisteredTimeZoneReceiver = false;
        Paint mBackgroundPaint;
        Paint mTextPaint, mAmPmTextPaint;
        Paint mDatePaint;
        Paint mWeatherMessagePaint,mMaxTemperaturePaint, mMinTemperaturePaint;
        boolean mAmbient;
        Calendar mCalendar;
        float mXCenter,mYCenter;
        String weatherMessage = "Scattered Clouds";
        private WeatherData mWeatherData = WeatherData.getInstance();

        SimpleDateFormat mModeInteractiveTimeFormat = new SimpleDateFormat("hh:mm:ss");
        SimpleDateFormat mModeAmbientTimeFormat = new SimpleDateFormat("hh:mm");
        SimpleDateFormat mAMorPMFormat = new SimpleDateFormat("a");
        SimpleDateFormat mDateFormat = new SimpleDateFormat("EEE, MMM dd y");

        final BroadcastReceiver mTimeZoneReceiver = new BroadcastReceiver() {
            @Override
            public void onReceive(Context context, Intent intent) {
                mCalendar.setTimeZone(TimeZone.getDefault());
                invalidate();
            }
        };

        float mMaxTemperature = 45, mMinTemperature = 22;
        boolean isMetric = false;
        float spaceX;
        /**
         * Whether the display supports fewer bits for each color in ambient mode. When true, we
         * disable anti-aliasing in ambient mode.
         */
        boolean mLowBitAmbient;
        Resources resources;
        boolean isRound;

        @Override
        public void onCreate(SurfaceHolder holder) {
            super.onCreate(holder);

            setWatchFaceStyle(new WatchFaceStyle.Builder(SunshineWatchFace.this)
                    .setCardPeekMode(WatchFaceStyle.PEEK_MODE_VARIABLE)
                    .setBackgroundVisibility(WatchFaceStyle.BACKGROUND_VISIBILITY_INTERRUPTIVE)
                    .setShowSystemUiTime(false)
                    .setAcceptsTapEvents(true)
                    .build());
            Resources resources = SunshineWatchFace.this.getResources();


            mBackgroundPaint = new Paint();
            mBackgroundPaint.setColor(resources.getColor(com.example.android.sunshine.app.R.color.background));

            int digitalTextColor = resources.getColor(com.example.android.sunshine.app.R.color.digital_text);

            mTextPaint = createTextPaint(digitalTextColor);
            mAmPmTextPaint = createTextPaint(digitalTextColor);
            mDatePaint = createTextPaint(digitalTextColor);
            mWeatherMessagePaint = createTextPaint(digitalTextColor);

            mMaxTemperaturePaint = createTextPaint(resources.getColor(R.color.digital_max_temperature));
            mMinTemperaturePaint = createTextPaint(resources.getColor(R.color.digital_text_date));

            mCalendar = Calendar.getInstance();

        }

        @Override
        public void onDestroy() {
            mUpdateTimeHandler.removeMessages(MSG_UPDATE_TIME);
            super.onDestroy();
        }

        private Paint createTextPaint(int textColor) {
            Paint paint = new Paint();
            paint.setColor(textColor);
            paint.setTypeface(NORMAL_TYPEFACE);
            paint.setAntiAlias(true);
            return paint;
        }

        @Override
        public void onVisibilityChanged(boolean visible) {
            super.onVisibilityChanged(visible);

            if (visible) {
                registerReceiver();

                // Update time zone in case it changed while we weren't visible.
                mCalendar.setTimeZone(TimeZone.getDefault());
                invalidate();
            } else {
                unregisterReceiver();
            }

            // Whether the timer should be running depends on whether we're visible (as well as
            // whether we're in ambient mode), so we may need to start or stop the timer.
            updateTimer();
        }

        private void registerReceiver() {
            if (mRegisteredTimeZoneReceiver) {
                return;
            }
            mRegisteredTimeZoneReceiver = true;
            IntentFilter filter = new IntentFilter(Intent.ACTION_TIMEZONE_CHANGED);
            SunshineWatchFace.this.registerReceiver(mTimeZoneReceiver, filter);
        }

        private void unregisterReceiver() {
            if (!mRegisteredTimeZoneReceiver) {
                return;
            }
            mRegisteredTimeZoneReceiver = false;
            SunshineWatchFace.this.unregisterReceiver(mTimeZoneReceiver);
        }

        @Override
        public void onApplyWindowInsets(WindowInsets insets) {
            super.onApplyWindowInsets(insets);

            // Load resources that have alternate values for round watches.
            resources = SunshineWatchFace.this.getResources();
            isRound = insets.isRound();

            float textSize = resources.getDimension(isRound
                    ? com.example.android.sunshine.app.R.dimen.digital_text_size_round :
                    com.example.android.sunshine.app.R.dimen.digital_text_size);

            mTextPaint.setTextSize(textSize);

            textSize = resources.getDimension(isRound
            ? com.example.android.sunshine.app.R.dimen.digital_date_text_size_round : com.example.android.sunshine.app.R.dimen.digital_date_text_size);

            mDatePaint.setTextSize(textSize);
            mWeatherMessagePaint.setTextSize(textSize);

            textSize = resources.getDimension(isRound
            ? com.example.android.sunshine.app.R.dimen.digital_ap_pm_text_size_round :
                    com.example.android.sunshine.app.R.dimen.digital_ap_pm_text_size);

            mAmPmTextPaint.setTextSize(textSize);

            textSize = resources.getDimension(isRound ?
                    R.dimen.digital_temperature_text_size_round :
                    R.dimen.digital_temperature_text_size);

            mMaxTemperaturePaint.setTextSize(textSize);
            mMinTemperaturePaint.setTextSize(textSize);

        }

        @Override
        public void onPropertiesChanged(Bundle properties) {
            super.onPropertiesChanged(properties);
            mLowBitAmbient = properties.getBoolean(PROPERTY_LOW_BIT_AMBIENT, false);
        }

        @Override
        public void onTimeTick() {
            super.onTimeTick();
            invalidate();
        }

        @Override
        public void onAmbientModeChanged(boolean inAmbientMode) {
            super.onAmbientModeChanged(inAmbientMode);
            if (mAmbient != inAmbientMode) {
                mAmbient = inAmbientMode;
                if (mLowBitAmbient) {
                    mTextPaint.setAntiAlias(!inAmbientMode);
                    mAmPmTextPaint.setAntiAlias(!inAmbientMode);
                    mMinTemperaturePaint.setAntiAlias(!inAmbientMode);
                    mMaxTemperaturePaint.setAntiAlias(!inAmbientMode);
                    mWeatherMessagePaint.setAntiAlias(!inAmbientMode);
                }
                invalidate();
            }

            // Whether the timer should be running depends on whether we're visible (as well as
            // whether we're in ambient mode), so we may need to start or stop the timer.
            updateTimer();
        }

        @Override
        public void onDraw(Canvas canvas, Rect bounds) {
            // Draw the background.
            if (isInAmbientMode()) {
                canvas.drawColor(Color.BLACK);
            } else {
                canvas.drawRect(0, 0, bounds.width(), bounds.height(), mBackgroundPaint);
            }

            // Draw H:MM in ambient mode or H:MM:SS in interactive mode.
            long now = System.currentTimeMillis();
            mCalendar.setTimeInMillis(now);
            Date date = mCalendar.getTime();

            String text = mAmbient
                    ? mModeAmbientTimeFormat.format(date)
                    : mModeInteractiveTimeFormat.format(date);

            String amOrPm = mAMorPMFormat.format(date);
            String day =  mDateFormat.format(date);

            /** find the center */
            mXCenter = bounds.exactCenterX();
            mYCenter = bounds.exactCenterY();

            /** draw a line at the center */
            float lineLength = 20;
            canvas.drawLine(mXCenter - lineLength, mYCenter, mXCenter + lineLength, mYCenter, mTextPaint);

            /** measure the time width and draw half way left from mXCenter */
            float textWidth = mTextPaint.measureText(text);
            float halfWay = textWidth / 2;

            float timeX = mXCenter - halfWay;

            /** to draw time 45% up from mYCenter */
            float timeY = mYCenter - (0.30f * mYCenter);
            canvas.drawText(text, timeX, timeY, mTextPaint);

            int space = 3;
            canvas.drawText(amOrPm, timeX + textWidth + space, timeY, mAmPmTextPaint);

            /** measure the date width and draw half way left from mXCenter */
            textWidth = mDatePaint.measureText(day);
            halfWay = textWidth / 2;
            /** draw date 80% up from mYCenter */
            canvas.drawText(day, mXCenter - halfWay, 0.90f * mYCenter, mDatePaint);


            float spaceY = resources.getDimension(isRound ?
                R.dimen.digital_y_offset_round : R.dimen.digital_y_offset);

            float iconY = mYCenter + spaceY;
            float componentWidth = 0;
            int textSpaceYFromCenter = 70;

            spaceX = resources.getDimension(isRound ? R.dimen.digital_x_offset_round:
                    R.dimen.digital_x_offset);

            /** if Watch is in interactive mode, draw weather icon, else draw weather message */
            if(!isInAmbientMode()) {
                Bitmap bitmap = mWeatherData.getWeatherImage();
                if(bitmap != null) {
                    Bitmap icon = Bitmap.createScaledBitmap(bitmap, 100, 100, true);
                    componentWidth = icon.getWidth();
                    canvas.drawBitmap(icon, 0.35f * mXCenter, iconY, mWeatherMessagePaint);
                }
            }else{
                componentWidth = 0;
                spaceX = mXCenter - (0.40f * mXCenter);
            }

            /** draw maximum and minimum temperature. */
            String maxTemp = mWeatherData.getMaxTemperature();
            if(maxTemp != null) {
                canvas.drawText(maxTemp, componentWidth + spaceX, iconY + textSpaceYFromCenter,
                        mMaxTemperaturePaint);
            }

            String minTemp = mWeatherData.getMinTemperature();
            if(minTemp != null) {
                canvas.drawText(minTemp, componentWidth + mMinTemperaturePaint.measureText(maxTemp) + spaceX + 5,
                        iconY + textSpaceYFromCenter, mMinTemperaturePaint);
            }

            weatherMessage = mWeatherData.getWeatherCondition();
            if(weatherMessage != null) {
                float weatherMessageWidth = mWeatherMessagePaint.measureText(weatherMessage);
                halfWay = weatherMessageWidth / 2;
                float ySpace = mYCenter + resources.getDimension(isRound ?
                R.dimen.weather_description_y_offset_round : R.dimen.weather_description_y_offset);
                canvas.drawText(weatherMessage, mXCenter - halfWay, ySpace,
                        mWeatherMessagePaint);
            }

        }

        /**
         * Starts the {@link #mUpdateTimeHandler} timer if it should be running and isn't currently
         * or stops it if it shouldn't be running but currently is.
         */
        private void updateTimer() {
            mUpdateTimeHandler.removeMessages(MSG_UPDATE_TIME);
            if (shouldTimerBeRunning()) {
                mUpdateTimeHandler.sendEmptyMessage(MSG_UPDATE_TIME);
            }
        }

        /**
         * Returns whether the {@link #mUpdateTimeHandler} timer should be running. The timer should
         * only run when we're visible and in interactive mode.
         */
        private boolean shouldTimerBeRunning() {
            return isVisible() && !isInAmbientMode();
        }

        /**
         * Handle updating the time periodically in interactive mode.
         */
        private void handleUpdateTimeMessage() {
            invalidate();
            if (shouldTimerBeRunning()) {
                long timeMs = System.currentTimeMillis();
                long delayMs = INTERACTIVE_UPDATE_RATE_MS
                        - (timeMs % INTERACTIVE_UPDATE_RATE_MS);
                mUpdateTimeHandler.sendEmptyMessageDelayed(MSG_UPDATE_TIME, delayMs);
            }
        }
    }
}
