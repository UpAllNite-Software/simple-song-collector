package com.open.simplesongcollector;

import android.content.Context;
import android.graphics.Canvas;
import android.graphics.Color;
import android.graphics.Paint;
import android.graphics.Rect;
import android.util.AttributeSet;
import android.util.DisplayMetrics;
import android.view.View;

import androidx.annotation.Nullable;

import com.google.android.exoplayer2.ExoPlayer;
import com.google.android.exoplayer2.SimpleExoPlayer;

public class LevelMeterView extends View implements LevelMeterAudioBufferSink.LevelUpdateListener
{
    float[] levels;
    int bandPadding = 2;
    Paint paint = new Paint();
    Rect bounds = new Rect();

    private LevelMeterAudioBufferSink levelMeterAudioBufferSink = null;

    public LevelMeterView(Context context)
    {
        super(context);
    }

    public LevelMeterView(Context context, @Nullable AttributeSet attrs)
    {
        super(context, attrs);
    }

    public LevelMeterView(Context context, @Nullable AttributeSet attrs, int defStyleAttr)
    {
        super(context, attrs, defStyleAttr);
    }

    public LevelMeterView(Context context, @Nullable AttributeSet attrs, int defStyleAttr, int defStyleRes)
    {
        super(context, attrs, defStyleAttr, defStyleRes);
    }

    @Override
    protected void onDraw(Canvas canvas)
    {
        //super.onDraw(canvas);

        if (levels==null)
        {
            return;
        }

        DisplayMetrics dm = getResources().getDisplayMetrics() ;

        paint.setFlags(Paint.ANTI_ALIAS_FLAG);

//        paint.setStyle(Paint.Style.FILL);
        paint.setColor(Color.rgb(0xED,0xED,0xED));
//        canvas.drawPaint(paint);



        canvas.getClipBounds(bounds);
        int bandWidth = (bounds.width()-((levels.length - 1)*bandPadding)) / levels.length;
        float bandHeightIncrement = (float)bounds.height() / 100f;

        int left = 0;
        for (int b = 0;b<levels.length;b++)
        {
            canvas.drawRect(left, bounds.bottom - (levels[b] * bandHeightIncrement), left + bandWidth, bounds.bottom, paint);
            left += bandWidth + bandPadding;
        }

    }

    public void setAudioBufferSink(LevelMeterAudioBufferSink levelMeterAudioBufferSink)
    {
        //this.levelMeterAudioBufferSink = levelMeterAudioBufferSink;
        if (levelMeterAudioBufferSink != null)
        {
            levelMeterAudioBufferSink.setLevelUpdateListener(this);
        }
    }



    @Override
    public void onLevelsUpdate(float[] levels)
    {
        this.levels = levels;
        invalidate();
    }
}
