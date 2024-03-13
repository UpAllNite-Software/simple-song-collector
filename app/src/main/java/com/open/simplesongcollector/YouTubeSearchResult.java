package com.open.simplesongcollector;

import android.graphics.drawable.Drawable;
import android.net.Uri;

import com.google.android.exoplayer2.SimpleExoPlayer;
import com.open.simplesongcollector.util.IsoDuration;

import org.json.JSONObject;
import org.apache.commons.lang3.StringEscapeUtils;
import org.schabi.newpipe.extractor.Image;
import org.schabi.newpipe.extractor.exceptions.ParsingException;
import org.schabi.newpipe.extractor.stream.StreamInfoItem;
import org.schabi.newpipe.extractor.InfoItem;

import java.util.List;


public class YouTubeSearchResult
{
    public int index;
    public String title;
    public String artist;
    public String videoUrl;
    public String thumbnailUrl;
    public long durationSeconds = 0;
    public DownloadTask downloadtask;
    public boolean downloadComplete;
    public Drawable thumbnailImage;

    public SimpleExoPlayer exoPlayer = null;
    public boolean streamLoading = false;
    public LevelMeterAudioBufferSink levelMeterAudioBufferSink;
    public boolean audioOnly = false;
    public Uri downloadedUri;


    public YouTubeSearchResult(JSONObject jsonObject, int index)
    {
        this.index = index;
        String videoId = jsonObject.optString("id");
        videoUrl = String.format("https://youtu.be/%s",videoId);

        JSONObject snippet = jsonObject.optJSONObject("snippet");
        if (snippet!=null)
        {
            title = snippet.optString("title");
            if (title!=null)
            {
                title = StringEscapeUtils.unescapeHtml4(title);
            }

            JSONObject thumbNails = snippet.optJSONObject("thumbnails");
            if (thumbNails != null)
            {
                JSONObject defaultThumbNail = thumbNails.optJSONObject("default");
                if (defaultThumbNail != null)
                {
                    thumbnailUrl = defaultThumbNail.optString("url");
                }
            }
        }

        JSONObject contentDetails = jsonObject.optJSONObject("contentDetails");
        if (contentDetails!=null)
        {
            String duration8601 = contentDetails.optString("duration");
            if (duration8601 != null)
            {
                try
                {
                    durationSeconds = (int) IsoDuration.parseToSeconds(duration8601);
                }
                catch (ParsingException e)
                {
                    e.printStackTrace();
                }
            }
        }
    }

    public YouTubeSearchResult(InfoItem item, int index)
    {
        this.index = index;
        title = item.getName();

        videoUrl = item.getUrl();
        List<Image> thumbnails = item.getThumbnails();
        if (!thumbnails.isEmpty())
        {
            thumbnailUrl =thumbnails.get(0).getUrl();
        }
        if (item instanceof StreamInfoItem)
        {
            artist = ((StreamInfoItem) item).getUploaderName();
            durationSeconds = ((StreamInfoItem) item).getDuration();
        }
    }


}
