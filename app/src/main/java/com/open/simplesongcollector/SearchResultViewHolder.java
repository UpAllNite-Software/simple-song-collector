package com.open.simplesongcollector;

import android.content.res.ColorStateList;
import android.graphics.Color;
import android.graphics.drawable.Drawable;
import android.view.View;
import android.widget.ImageButton;
import android.widget.ImageView;
import android.widget.TextView;

import androidx.appcompat.content.res.AppCompatResources;
import androidx.appcompat.widget.AppCompatImageButton;
import androidx.cardview.widget.CardView;
import androidx.core.content.ContextCompat;
import androidx.lifecycle.LifecycleOwner;
import androidx.lifecycle.Observer;
import androidx.recyclerview.widget.RecyclerView;

import com.google.android.exoplayer2.ui.PlayerControlView;
import com.google.android.exoplayer2.ui.PlayerView;
import com.google.android.material.progressindicator.CircularProgressIndicator;

public class SearchResultViewHolder extends RecyclerView.ViewHolder
{
    public LifecycleOwner lifeCycleOwner;
    private CardView cardView;
    private TextView cardName;
    private TextView artistName;
    private PlayerView playerView;
    private ImageView thumbnailView;
    private TextView durationView;
    private ImageButton playButton;
    private CircularProgressIndicator progressIndicator;
    private LevelMeterView levelMeter;

    public SearchResultViewHolder(View itemView)
    {
        super(itemView);
        cardView = (CardView) itemView;
        cardName = itemView.findViewById(R.id.title);
        artistName = itemView.findViewById(R.id.artist);
        playerView = itemView.findViewById(R.id.video_player);

        thumbnailView = (ImageView)cardView.findViewById(R.id.thumbnail);
        durationView = (TextView)cardView.findViewById(R.id.duration);
        playButton = (ImageButton) cardView.findViewById(R.id.video_player_play);
        progressIndicator = (CircularProgressIndicator)cardView.findViewById(R.id.video_player_loading);
        levelMeter = (LevelMeterView)cardView.findViewById(R.id.level_meter);


    }


    public void bind(YouTubeSearchResult result)
    {
        //draw the whole item
        cardName.setText(result.title);
        artistName.setText(result.artist);

        TextView durationView = cardView.findViewById(R.id.duration);
        String durationString = "";

        if (result.durationSeconds > 0)
        {
            long hours = result.durationSeconds / 3600;
            long minutes = (result.durationSeconds % 3600) / 60;
            long seconds = result.durationSeconds % 60;

            if (hours > 0)
            {
                durationString = String.format("%d:%02d:%02d", hours, minutes, seconds);
            } else
            {
                durationString = String.format("%d:%02d", minutes, seconds);
            }
        }

        durationView.setText(durationString);

        update(result);
    }

    public void update(YouTubeSearchResult searchResult)
    {


        thumbnailView.setImageDrawable(searchResult.thumbnailImage);

        if (searchResult.exoPlayer != null)
        {
            if (playerView.getPlayer() == null)
            {
                //System.out.printf("setting player in playerview @%d %s\n",searchResult.index,playerView.toString());
                playerView.setPlayer(searchResult.exoPlayer);
                playerView.setControllerAutoShow(false);

                if (searchResult.audioOnly)
                {
                    playerView.setControllerVisibilityListener(new PlayerControlView.VisibilityListener()
                    {
                        @Override
                        public void onVisibilityChange(int visibility)
                        {
                            if (visibility == View.VISIBLE)
                            {
                                levelMeter.setVisibility(View.INVISIBLE);
                            } else
                            {
                                levelMeter.setVisibility(View.VISIBLE);

                            }
                        }
                    });
                }

            }
            if (searchResult.levelMeterAudioBufferSink != null)
            {
                levelMeter.setAudioBufferSink(searchResult.levelMeterAudioBufferSink);
            }
        }
        else
        {
            //System.out.printf("clearing player in playerview @%d %s\n",searchResult.index,playerView.toString());
            playerView.setPlayer(null);
            levelMeter.setAudioBufferSink(null);
        }

        boolean streaming = searchResult.exoPlayer != null;
        boolean audioOnly = streaming && searchResult.audioOnly; //todo determine

        thumbnailView.setVisibility(streaming ? View.INVISIBLE : View.VISIBLE);
        durationView.setVisibility(streaming ? View.INVISIBLE : View.VISIBLE);
        playerView.setVisibility(streaming ? View.VISIBLE : View.INVISIBLE);
        playButton.setVisibility(streaming || searchResult.streamLoading ? View.INVISIBLE : View.VISIBLE);
        progressIndicator.setVisibility(searchResult.streamLoading ? View.VISIBLE : View.INVISIBLE);
        levelMeter.setVisibility(audioOnly ? View.VISIBLE : View.INVISIBLE);

        AppCompatImageButton button = cardView.findViewById(R.id.download_button);
        CircularProgressIndicator circularProgressIndicator = cardView.findViewById(R.id.download_progress);

        if (searchResult.downloadedUri!=null)
        {
            circularProgressIndicator.hide();
            button.setImageResource(R.drawable.downloaded);
            button.setVisibility(View.VISIBLE);
            button.setEnabled(false);
            button.setColorFilter(Color.BLACK);
        }
        else if (searchResult.downloadtask!=null)
        {
            searchResult.downloadtask.getDownloadProgress().observe(lifeCycleOwner, new Observer<Integer>()
            {
                @Override
                public void onChanged(Integer progress)
                {
                    //System.out.printf("progress: %d\n",progress);
                    if (progress == 1)
                    {
                        button.setVisibility(View.INVISIBLE);
                        circularProgressIndicator.setIndeterminate(true);
                        circularProgressIndicator.show();
                    }
                    else
                    {
                        circularProgressIndicator.setIndeterminate(false);
                        circularProgressIndicator.setProgressCompat(progress, true);
                    }
                }
            });
        }
        else
        {
                circularProgressIndicator.hide();
                button.setEnabled(true);
                button.setVisibility(View.VISIBLE);
                button.setImageResource(R.drawable.download);
                button.setColorFilter(ContextCompat.getColor(cardView.getContext(),R.color.colorTint));
        }
    }

    public void clearPlayer()
    {
        playerView.setPlayer(null);
        playerView.setControllerVisibilityListener(null);
        levelMeter.setAudioBufferSink(null);
    }
}