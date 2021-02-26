package com.open.simplesongcollector;


import android.os.Handler;

import com.google.android.exoplayer2.audio.TeeAudioProcessor;
import java.nio.ByteBuffer;
import java.nio.ShortBuffer;
import java.util.ArrayList;
import ddf.minim.analysis.*;



public class LevelMeterAudioBufferSink implements TeeAudioProcessor.AudioBufferSink
{
    private LevelUpdateListener levelUpdateListener;

    private int sampleRateHz;
    private int channelCount;
    private int encoding;

    final int bandCount = 11;
    final int rollingAverage = 3;
    final int bandRange = 1;
    final int displayRate = 10; //per second

    Handler handler = new Handler();

    private FFT fft;
    private int buffersProcessed;

    class myFloatArrayList extends ArrayList<Float> {}
    private myFloatArrayList[] valueLists;

    public LevelMeterAudioBufferSink()
    {
        valueLists = new myFloatArrayList[bandCount];
        for(int b=0;b<bandCount;b++)
        {
            valueLists[b] = new myFloatArrayList();
        }
    }


    public void setLevelUpdateListener(LevelUpdateListener levelUpdateListener)
    {
        this.levelUpdateListener = levelUpdateListener;
    }

    public void release()
    {
        this.levelUpdateListener = null;
    }

    public interface LevelUpdateListener
    {
        void onLevelsUpdate(float[] levels);
    }

    @Override
    public void flush(int sampleRateHz, int channelCount, int encoding)
    {
        System.out.printf("LevelMeterAudioBufferSink: flush %d %d %d \n",
                sampleRateHz,
                channelCount,
                encoding);

        this.sampleRateHz = sampleRateHz;
        this.channelCount = channelCount;
        this.encoding = encoding;

        buffersProcessed = 0;
        fft = null;
        for(int b=0;b<bandCount;b++)
        {
            valueLists[b] = new myFloatArrayList();
        }
    }

    private float[] shortToFloat(ShortBuffer shortBuffer) {

        int sampleCount = (shortBuffer.limit()-shortBuffer.position()) / channelCount;
        float[] converted = new float[sampleCount];

        for (int i = 0; i < sampleCount; i++) {
            // [-3276,32768] -> [-1,1]
            float avgSample = 0f;

            for(int j=0;j<channelCount;j++)
            {
//                Short s = Short.reverseBytes(shortBuffer.get());
                Short s = shortBuffer.get();
                float sample = (float)s;
                sample /= 32768f;
                avgSample += sample;
            }

            converted[i] = avgSample / channelCount;
        }

        return converted;
    }

    @Override
    public void handleBuffer(ByteBuffer buffer)
    {
        //System.out.println("Got buffer with size: "+buffer.limit());

        ShortBuffer shortBuffer = buffer.asShortBuffer();

        float[] floats = shortToFloat(shortBuffer);

        if (fft == null)
        {
            fft = new FFT(floats.length, sampleRateHz);
            fft.window(new HammingWindow());
            //fft.linAverages(4);
        }

        fft.forward(floats);
        int bandInterval = fft.specSize() / (bandCount+1);

        float[] accumulator = new float[bandCount];

        for (int i=0;i<bandCount;i++)
        {

            Float value = 0f;
            int bandMid = (i + 1) * bandInterval;
            for (int band=bandMid -bandRange ;band<bandMid + bandRange;band++)
            {
                Float bValue = fft.getBand(band);
                if (bValue > value)
                {
                    value = bValue;
                }
            }



            //Float value = fft.getFreq(freqs[i]);
            //Float value = fft.getAvg(i);
            valueLists[i].add(value);
            while (valueLists[i].size()>rollingAverage)
            {
                valueLists[i].remove(0);
            }
        }

        int samplesPerSec = sampleRateHz / floats.length;
        int displaySkip = samplesPerSec/displayRate;


        if ((buffersProcessed++ % displaySkip) == 0)
        {

            int averageCount = valueLists[0].size();

            for (int i=0;i<averageCount;i++)
            {
                for (int b=0;b<accumulator.length;b++)
                {
                    accumulator[b] += valueLists[b].get(i);
                }
            }

            for (int b=0;b<accumulator.length;b++)
            {
                accumulator[b] /= (float)averageCount; //get average
                accumulator[b] /= 20f; //normalize
                accumulator[b] *= 100f; //put in range 1 to 100;
            }

            if (this.levelUpdateListener != null)
            {
                handler.post(new Runnable() { @Override public void run() { LevelMeterAudioBufferSink.this.levelUpdateListener.onLevelsUpdate(accumulator);  } });
            }
        }
    }
}
