package com.open.simplesongcollector;

import android.Manifest;
import android.content.ContentValues;
import android.content.Context;
import android.content.pm.PackageManager;
import android.graphics.Bitmap;
import android.graphics.BitmapFactory;
import android.graphics.drawable.BitmapDrawable;
import android.graphics.drawable.Drawable;
import android.net.Uri;
import android.os.Build;
import android.os.Bundle;

//import com.google.api.client.googleapis.json.GoogleJsonResponseException;
//import com.google.api.client.http.HttpRequest;
//import com.google.api.client.http.HttpRequestInitializer;
//import com.google.api.client.http.HttpTransport;
//import com.google.api.client.http.javanet.NetHttpTransport;
//import com.google.api.client.json.JsonFactory;

//import com.google.api.client.json.jackson2.JacksonFactory;
//import com.google.api.services.youtube.YouTube;
//import com.google.api.services.youtube.model.SearchListResponse;
//import com.google.api.services.youtube.model.SearchResult;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.appcompat.app.AppCompatActivity;
import androidx.appcompat.widget.SearchView;
import androidx.appcompat.widget.Toolbar;
import androidx.core.app.ActivityCompat;
import androidx.lifecycle.Observer;
import androidx.lifecycle.ViewModelProvider;
import androidx.recyclerview.widget.LinearLayoutManager;
import androidx.recyclerview.widget.RecyclerView;


import android.os.Environment;
import android.provider.MediaStore;
import android.text.TextUtils;
import android.util.Log;
import android.view.Menu;
import android.view.MenuItem;
import android.view.View;
import android.widget.Toast;

import com.google.android.exoplayer2.DefaultRenderersFactory;
import com.google.android.exoplayer2.ExoPlayer;
import com.google.android.exoplayer2.MediaItem;
import com.google.android.exoplayer2.RenderersFactory;
import com.google.android.exoplayer2.SimpleExoPlayer;
import com.google.android.exoplayer2.audio.AudioCapabilities;
import com.google.android.exoplayer2.audio.AudioProcessor;
import com.google.android.exoplayer2.audio.AudioSink;
import com.google.android.exoplayer2.audio.DefaultAudioSink;
import com.google.android.exoplayer2.audio.TeeAudioProcessor;
import com.google.android.exoplayer2.source.DefaultMediaSourceFactory;
import com.google.android.exoplayer2.upstream.DataSource;
import com.google.android.exoplayer2.upstream.DefaultDataSourceFactory;
import com.google.android.material.progressindicator.CircularProgressIndicator;
import com.open.simplesongcollector.util.Globals;

import org.jaudiotagger.audio.AudioFile;
import org.jaudiotagger.audio.AudioFileIO;
import org.jaudiotagger.tag.FieldKey;
import org.jaudiotagger.tag.Tag;
import org.jaudiotagger.tag.images.Artwork;
import org.jaudiotagger.tag.images.ArtworkFactory;
import org.json.JSONObject;
import org.schabi.newpipe.extractor.NewPipe;
import org.schabi.newpipe.extractor.search.SearchInfo;
import org.schabi.newpipe.extractor.stream.AudioStream;
import org.schabi.newpipe.extractor.stream.Stream;
import org.schabi.newpipe.extractor.stream.StreamInfo;
import org.schabi.newpipe.extractor.stream.VideoStream;

import java.io.BufferedReader;
import java.io.ByteArrayOutputStream;
import java.io.File;
import java.io.IOException;
import java.io.InputStream;
import java.io.StringReader;
import java.util.ArrayList;
import java.util.List;
import java.util.TreeMap;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

import io.reactivex.rxjava3.android.schedulers.AndroidSchedulers;
import io.reactivex.rxjava3.core.Observable;
import io.reactivex.rxjava3.disposables.CompositeDisposable;
import io.reactivex.rxjava3.disposables.Disposable;
import io.reactivex.rxjava3.schedulers.Schedulers;

//import io.reactivex.Observable;
//import io.reactivex.android.schedulers.AndroidSchedulers;
//import io.reactivex.disposables.CompositeDisposable;
//import io.reactivex.disposables.Disposable;
//import io.reactivex.schedulers.Schedulers;

public class MainActivity extends AppCompatActivity
{
    public final String TAG = getClass().getSimpleName();
    public static final boolean DEBUG = !BuildConfig.BUILD_TYPE.equals("release");

    private SimpleExoPlayer exoPlayer;
    private LevelMeterAudioBufferSink levelMeterAudioBufferSink;
    private int playerPosition = -1;

    private SearchView searchView;

    private RecyclerView recyclerView;
    private SearchResultAdapter searchResultAdapter;

    private LinearLayoutManager layoutManager;

    public CompositeDisposable compositeDisposable = new CompositeDisposable();
    private CircularProgressIndicator progressIndicator;


    @Override
    protected void onCreate(Bundle savedInstanceState)
    {
        super.onCreate(savedInstanceState);

        setContentView(R.layout.activity_scrolling);
        Toolbar toolbar = (Toolbar) findViewById(R.id.toolbar);
        setSupportActionBar(toolbar);

        searchView = (SearchView) findViewById(R.id.search_view);

        recyclerView = (RecyclerView) findViewById(R.id.recycler_view);

        progressIndicator = (CircularProgressIndicator) findViewById(R.id.search_result_loading);

        SearchViewModel searchViewModel = new ViewModelProvider(MainActivity.this).get(SearchViewModel.class);

        searchViewModel.getSearchInfo().observe(MainActivity.this, new Observer<SearchInfo>()
        {
            @Override
            public void onChanged(SearchInfo searchInfo)
            {
                searchResultAdapter = new SearchResultAdapter(searchInfo,MainActivity.this);
                recyclerView.swapAdapter(searchResultAdapter,true);

                progressIndicator.setVisibility(View.INVISIBLE);

                Disposable disposable = searchResultAdapter.getDownloadClicks().subscribeOn(Schedulers.newThread())
                    .observeOn(AndroidSchedulers.mainThread())
                    .subscribe(youTubeSearchResult -> {
                        startDownload(youTubeSearchResult);
                    }, e ->
                    {
                        Toast.makeText(MainActivity.this, e.getMessage(), Toast.LENGTH_LONG).show();
                    });

                compositeDisposable.add(disposable);

                disposable = searchResultAdapter.getNeedArtworkRequests().subscribeOn(Schedulers.newThread())
                        .observeOn(AndroidSchedulers.mainThread())
                        .subscribe(youTubeSearchResult -> {
                            startGetArtwork(youTubeSearchResult);
                        }, e ->
                        {
                            Toast.makeText(MainActivity.this, e.getMessage(), Toast.LENGTH_LONG).show();
                        });

                compositeDisposable.add(disposable);

                disposable = searchResultAdapter.getStreamClicks().subscribeOn(Schedulers.newThread())
                        .observeOn(AndroidSchedulers.mainThread())
                        .subscribe(youTubeSearchResult -> {
                            startStream(youTubeSearchResult);
                        }, e ->
                        {
                            Toast.makeText(MainActivity.this, e.getMessage(), Toast.LENGTH_LONG).show();
                        });

                compositeDisposable.add(disposable);


            }
        });

        searchViewModel.getSearchResponse().observe(MainActivity.this, new Observer<JSONObject>()
        {
            @Override
            public void onChanged(JSONObject jsonResponse)
            {
                searchResultAdapter = new SearchResultAdapter(jsonResponse);
                recyclerView.swapAdapter(searchResultAdapter,true);

                progressIndicator.setVisibility(View.INVISIBLE);

                Disposable disposable = searchResultAdapter.getDownloadClicks().subscribeOn(Schedulers.newThread())
                        .observeOn(AndroidSchedulers.mainThread())
                        .subscribe(youTubeSearchResult -> {
                            startDownload(youTubeSearchResult);
                        }, e ->
                        {
                            Toast.makeText(MainActivity.this, e.getMessage(), Toast.LENGTH_LONG).show();
                        });

                compositeDisposable.add(disposable);

                disposable = searchResultAdapter.getNeedArtworkRequests().subscribeOn(Schedulers.newThread())
                        .observeOn(AndroidSchedulers.mainThread())
                        .subscribe(youTubeSearchResult -> {
                            startGetArtwork(youTubeSearchResult);
                        }, e ->
                        {
                            Toast.makeText(MainActivity.this, e.getMessage(), Toast.LENGTH_LONG).show();
                        });

                compositeDisposable.add(disposable);

                disposable = searchResultAdapter.getStreamClicks().subscribeOn(Schedulers.newThread())
                        .observeOn(AndroidSchedulers.mainThread())
                        .subscribe(youTubeSearchResult -> {
                            startStream(youTubeSearchResult);
                        }, e ->
                        {
                            Toast.makeText(MainActivity.this, e.getMessage(), Toast.LENGTH_LONG).show();
                        });

                compositeDisposable.add(disposable);


            }
        });


        searchViewModel.getSearchError().observe(MainActivity.this, new Observer<Throwable>()
        {
            @Override
            public void onChanged(Throwable error)
            {
                Toast.makeText(MainActivity.this, error.toString(), Toast.LENGTH_SHORT).show();
                progressIndicator.setVisibility(View.INVISIBLE);
            }
        });



        searchView.setOnQueryTextListener(new SearchView.OnQueryTextListener() {
            @Override
            public boolean onQueryTextSubmit(String query) {

                exoPlayer.stop(true);
                levelMeterAudioBufferSink.setLevelUpdateListener(null);

                recyclerView.swapAdapter(null,true);

                progressIndicator.setVisibility(View.VISIBLE);

                searchViewModel.setQuery(query);

                try
                {
                    searchViewModel.refresh();
                }
                catch (Exception e)
                {
                    Toast.makeText(MainActivity.this, e.toString(),Toast.LENGTH_SHORT).show();
                    e.printStackTrace();
                }

                return false;
            }
            @Override
            public boolean onQueryTextChange(String newText) {
                //adapter.getFilter().filter(newText);
                //todo: add search hints
                return false;
            }
        });

        initView();

        levelMeterAudioBufferSink = new LevelMeterAudioBufferSink();

        RenderersFactory renderersFactory = new DefaultRenderersFactory(this){
            @Nullable
            @Override
            protected AudioSink buildAudioSink(Context context, boolean enableFloatOutput, boolean enableAudioTrackPlaybackParams, boolean enableOffload)
            {
                TeeAudioProcessor teeAudioProcessor = new TeeAudioProcessor(levelMeterAudioBufferSink);
                return new DefaultAudioSink(AudioCapabilities.DEFAULT_AUDIO_CAPABILITIES, new DefaultAudioSink.DefaultAudioProcessorChain( teeAudioProcessor ).getAudioProcessors());
                //return super.buildAudioSink(context, enableFloatOutput, enableAudioTrackPlaybackParams, enableOffload);
            }
        };

        SimpleExoPlayer.Builder builder = new SimpleExoPlayer.Builder(this, renderersFactory);
        DataSource.Factory dsf = new DefaultDataSourceFactory(this, "Agent User");
        builder.setMediaSourceFactory(new DefaultMediaSourceFactory(dsf));

        exoPlayer = builder.build();

    }

    private Drawable continueGetArtwork(YouTubeSearchResult youTubeSearchResult)
    {
        String urldisplay = youTubeSearchResult.thumbnailUrl;
        Bitmap bitmap = null;
        try {
            InputStream in = new java.net.URL(urldisplay).openStream();
            bitmap = BitmapFactory.decodeStream(in);
        } catch (Exception e) {
            Log.e("Error", e.getMessage());
            e.printStackTrace();
        }
        if (bitmap!=null)
        {
            return new BitmapDrawable(getResources(), bitmap);
        }

        return null;
    }

    private void startGetArtwork(YouTubeSearchResult youTubeSearchResult)
    {
        Disposable disposable = Observable.fromCallable(() -> continueGetArtwork(youTubeSearchResult))
                .subscribeOn(Schedulers.newThread())
                .observeOn(AndroidSchedulers.mainThread())
                .subscribe(theArtwork -> {
                    runOnUiThread(() -> {
                        youTubeSearchResult.thumbnailImage = theArtwork;
                        searchResultAdapter.refresh(youTubeSearchResult.index,youTubeSearchResult);
                    });
                }, e ->
                {
                    if(BuildConfig.DEBUG) Log.e(TAG,  "failed to download", e);
                });
        compositeDisposable.add(disposable);

    }

    private void initView()
    {
        layoutManager = new LinearLayoutManager(this, RecyclerView.VERTICAL,false);
        recyclerView.setLayoutManager(layoutManager);

    }

    @Override
    public boolean onCreateOptionsMenu(Menu menu)
    {
        // Inflate the menu; this adds items to the action bar if it is present.
        getMenuInflater().inflate(R.menu.menu_scrolling, menu);

        return true;
    }

    @Override
    public boolean onMenuOpened(int featureId, Menu menu)
    {
        String searchType = Globals.getSearchType();
        if (searchType.equals("music_songs"))
        {
            menu.findItem(R.id.action_songs).setChecked(true);
        }
        else if (searchType.equals("videos"))
        {
            menu.findItem(R.id.action_videos).setChecked(true);
        }


        return super.onMenuOpened(featureId, menu);
    }

    @Override
    public boolean onOptionsItemSelected(MenuItem item)
    {
        // Handle action bar item clicks here. The action bar will
        // automatically handle clicks on the Home/Up button, so long
        // as you specify a parent activity in AndroidManifest.xml.
        int id = item.getItemId();

        //noinspection SimplifiableIfStatement
        if (id == R.id.action_songs)
        {
            Globals.setSearchType("music_songs");
            return true;
        }
        else if (id == R.id.action_videos)
        {
            Globals.setSearchType("videos");
            return true;
        }
        return super.onOptionsItemSelected(item);
    }

    @Override
    protected void onDestroy() {
        compositeDisposable.dispose();
        if (exoPlayer!=null)
        {
            exoPlayer.release();
            exoPlayer = null;
        }


        super.onDestroy();
    }

    public boolean isStoragePermissionGranted()
    {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.M) {
            if (checkSelfPermission(Manifest.permission.WRITE_EXTERNAL_STORAGE) == PackageManager.PERMISSION_GRANTED)
            {
                return true;
            }
            else
            {
                ActivityCompat.requestPermissions(this, new String[]{Manifest.permission.WRITE_EXTERNAL_STORAGE}, 1);
                return false;
            }
        }
        else
        {
            return true;
        }
    }


    public void startDownload(YouTubeSearchResult result)
    {
        if (!isStoragePermissionGranted())
        {
            Toast.makeText(this, "grant storage permission and retry", Toast.LENGTH_LONG).show();
            return;
        }

        if (TextUtils.isEmpty(result.videoUrl))
        {
            Toast.makeText(this, "cannot start download. missing url in search result", Toast.LENGTH_LONG).show();
            return;
        }

        result.downloadtask = new DownloadTask(this, result);
        searchResultAdapter.refresh(result.index,result);

        Disposable disposable = Observable.fromCallable(() -> result.downloadtask.execute())
                .subscribeOn(Schedulers.newThread())
                .observeOn(AndroidSchedulers.mainThread())
                .subscribe(uri -> {
                    result.downloadedUri = uri;
                    searchResultAdapter.refresh(result.index,result);
                    Toast.makeText(this, "download successful", Toast.LENGTH_LONG).show();
                }, e ->
                {
                    result.downloadtask = null;
                    searchResultAdapter.refresh(result.index,result);

                    if(BuildConfig.DEBUG) Log.e(TAG,  "failed to download", e);
                    //pbLoading.setVisibility(View.GONE);
                    //tvDownloadStatus.setText(getString(R.string.download_failed));
                    Log.d(TAG,e.getMessage());
                    //tvCommandOutput.setText(e.getMessage());
                    Toast.makeText(this, e.getMessage(), Toast.LENGTH_LONG).show();
                });

        compositeDisposable.add(disposable);

    }

    private void startStream(YouTubeSearchResult result)
    {
        exoPlayer.stop(true);
        levelMeterAudioBufferSink.setLevelUpdateListener(null);

        if (playerPosition != -1)
        {
            YouTubeSearchResult oldResult = searchResultAdapter.getSearchResultAt(playerPosition);
            oldResult.exoPlayer = null;
            oldResult.levelMeterAudioBufferSink = null;
            runOnUiThread(() -> {
                searchResultAdapter.refresh(oldResult.index,oldResult);
            });
            playerPosition = -1;
        }

        String url = result.videoUrl;

        //todo: show loading indicator
        result.streamLoading = true;
        runOnUiThread(() -> {
            searchResultAdapter.refresh(result.index,result);
        });

        Disposable disposable = Observable.fromCallable(() -> {

            StreamInfo streamInfo = StreamInfo.getInfo(NewPipe.getService(0), result.videoUrl);

            TreeMap<String,VideoStream> orderedMp4Streams = new TreeMap<>();
            List<VideoStream> videoStreams = streamInfo.getVideoStreams();
            for(VideoStream videoStream: videoStreams)
            {
                if (videoStream.getFormat().getSuffix().compareToIgnoreCase("mp4")==0)
                {
                    orderedMp4Streams.put(videoStream.resolution,videoStream);
                }
            }

            /*
            YoutubeDLRequest request = new YoutubeDLRequest(url);
            // best stream containing video+audio
            request.addOption("-f", "best");
            return YoutubeDL.getInstance().getInfo(request);
            */

            if (orderedMp4Streams.size() > 0)
            {
                return orderedMp4Streams.firstEntry().getValue();
            }
            return null;
        })
                .subscribeOn(Schedulers.newThread())
                .observeOn(AndroidSchedulers.mainThread())
                .subscribe(streamInfo -> {

                    String videoUrl = streamInfo.getUrl();
                    if (TextUtils.isEmpty(videoUrl)) {
                        Toast.makeText(this, "failed to get stream url", Toast.LENGTH_LONG).show();
                        runOnUiThread(() -> {
                            result.streamLoading = false;
                            searchResultAdapter.refresh(result.index, result);
                        });
                    }
                    else
                    {
                        MediaItem mediaItem = MediaItem.fromUri(videoUrl);
                        exoPlayer.setMediaItem(mediaItem);
                        exoPlayer.setPlayWhenReady(true);
                        exoPlayer.prepare();

                        playerPosition = result.index;
                        result.exoPlayer = exoPlayer;
                        result.levelMeterAudioBufferSink = levelMeterAudioBufferSink;

                        runOnUiThread(() -> {
                            result.streamLoading = false;
                            searchResultAdapter.refresh(result.index, result);
                        });

                    }
                }, e ->
                {
                    if(BuildConfig.DEBUG) Log.e(TAG,  "failed to get stream info", e);
                    runOnUiThread(() -> {
                        result.streamLoading = false;
                        searchResultAdapter.refresh(result.index, result);
                    });
                    Toast.makeText(this, "streaming failed. failed to get stream info", Toast.LENGTH_LONG).show();
                });
        compositeDisposable.add(disposable);
    }




}