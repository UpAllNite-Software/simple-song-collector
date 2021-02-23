package com.open.simplesongcollector;

import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.ImageView;

import androidx.annotation.NonNull;
import androidx.appcompat.widget.AppCompatImageButton;
import androidx.lifecycle.LifecycleOwner;
import androidx.recyclerview.widget.RecyclerView;

import org.json.JSONArray;
import org.json.JSONException;
import org.json.JSONObject;
import org.schabi.newpipe.extractor.InfoItem;
import org.schabi.newpipe.extractor.search.SearchInfo;

import java.util.ArrayList;
import java.util.List;

import io.reactivex.rxjava3.core.Observable;
import io.reactivex.rxjava3.subjects.PublishSubject;

//import io.reactivex.Observable;
//import io.reactivex.subjects.PublishSubject;

public class SearchResultAdapter extends RecyclerView.Adapter<SearchResultViewHolder>
{
    public final String TAG = getClass().getSimpleName();

    private final PublishSubject<YouTubeSearchResult> onClickDownload = PublishSubject.create();
    private final PublishSubject<YouTubeSearchResult> onClickStream = PublishSubject.create();
    private final PublishSubject<YouTubeSearchResult> onNeedArtwork = PublishSubject.create();

    public void refresh(int index, YouTubeSearchResult result)
    {
        //System.out.printf("refresh: %d %d\n",index,result.index);
        resultArray.set(index,result);
        notifyItemChanged(index,result);
    }

    private ArrayList<YouTubeSearchResult> resultArray = new ArrayList<>();

    SearchInfo searchInfo;
    LifecycleOwner lifecycleOwner;

    public SearchResultAdapter(SearchInfo searchInfo, LifecycleOwner lifecycleOwner)
    {
        this.lifecycleOwner = lifecycleOwner;
        this.searchInfo = searchInfo;

        boolean audioOnly = searchInfo.getOriginalUrl().contains("music.youtube");

        for(InfoItem item:searchInfo.getRelatedItems())
        {
            YouTubeSearchResult youTubeSearchResult = new YouTubeSearchResult(item,resultArray.size());
            youTubeSearchResult.audioOnly = audioOnly;
            resultArray.add(youTubeSearchResult);
        }

    }

    JSONObject apiObject;

    public SearchResultAdapter(JSONObject jsonObject)
    {
        apiObject = jsonObject;

        try
        {
            JSONArray items = apiObject.getJSONArray("items");
            for(int i=0;i<items.length();i++)
            {
                JSONObject item = items.getJSONObject(i);
                YouTubeSearchResult youTubeSearchResult = new YouTubeSearchResult(item,i);
                resultArray.add(youTubeSearchResult);
            }
        }
        catch (JSONException e)
        {
            e.printStackTrace();
        }

    }

    @Override
    public SearchResultViewHolder onCreateViewHolder(final ViewGroup parent, int viewType)
    {
        //must make sure context theme has transparent background for exoplayer view
        LayoutInflater parentLayoutInflater = LayoutInflater.from(parent.getContext());

        View view = parentLayoutInflater.inflate(R.layout.base_card_view, parent, false);

        SearchResultViewHolder holder = new SearchResultViewHolder(view);
        holder.lifeCycleOwner = this.lifecycleOwner;

        return holder;
    }

    @Override
    public void onBindViewHolder(@NonNull SearchResultViewHolder holder, int position)
    {
        final YouTubeSearchResult result = resultArray.get(position);


        if (result.thumbnailImage==null && result.thumbnailUrl!=null)
        {
            onNeedArtwork.onNext(result);
            //new SearchResultAdapter.DownloadImageTask(result).execute(result.thumbnailUrl);
        }

        SearchResultViewHolder searchResultViewHolder = (SearchResultViewHolder)holder;
        searchResultViewHolder.bind(result);

        AppCompatImageButton button = holder.itemView.findViewById(R.id.download_button);
        button.setOnClickListener(new View.OnClickListener()
        {
            @Override
            public void onClick(View v)
            {
                onClickDownload.onNext(result);
                //startDownload(position);
            }
        });

        ImageView thumbnailView = (ImageView)holder.itemView.findViewById(R.id.thumbnail);
        thumbnailView.setOnClickListener(new View.OnClickListener()
        {
            @Override
            public void onClick(View v)
            {
                onClickStream.onNext(result);
                //startStream(position);
            }
        });
    }

    public Observable<YouTubeSearchResult> getDownloadClicks(){
        return onClickDownload.hide();
    }

    public Observable<YouTubeSearchResult> getStreamClicks(){
        return onClickStream.hide();
    }

    public Observable<YouTubeSearchResult> getNeedArtworkRequests(){
        return onNeedArtwork.hide();
    }


    @Override
    public void onBindViewHolder(@NonNull SearchResultViewHolder holder, int position, @NonNull List<Object> payloads)
    {
        super.onBindViewHolder(holder, position, payloads);

        if (!payloads.isEmpty())
        {
            SearchResultViewHolder searchResultViewHolder = (SearchResultViewHolder)holder;
            searchResultViewHolder.update((YouTubeSearchResult)payloads.get(0));
        }
    }

    @Override
    public void onViewRecycled(@NonNull SearchResultViewHolder holder)
    {
        super.onViewRecycled(holder);
    }

    @Override
    public int getItemCount() {
        return resultArray.size();
    }

    public YouTubeSearchResult getSearchResultAt(int position)
    {
        return resultArray.get(position);
    }
}
