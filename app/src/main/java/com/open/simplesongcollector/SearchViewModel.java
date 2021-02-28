package com.open.simplesongcollector;

import android.text.TextUtils;

import androidx.lifecycle.LiveData;
import androidx.lifecycle.MutableLiveData;
import androidx.lifecycle.ViewModel;

import org.json.JSONObject;

//import io.reactivex.disposables.CompositeDisposable;
import org.schabi.newpipe.extractor.search.SearchInfo;
import androidx.annotation.NonNull;
import org.schabi.newpipe.util.ExtractorHelper;
import com.open.simplesongcollector.util.Globals;

import java.util.Arrays;
import java.util.concurrent.atomic.AtomicBoolean;

import io.reactivex.rxjava3.android.schedulers.AndroidSchedulers;
import io.reactivex.rxjava3.disposables.CompositeDisposable;
import io.reactivex.rxjava3.disposables.Disposable;
import io.reactivex.rxjava3.schedulers.Schedulers;

//import io.reactivex.android.schedulers.AndroidSchedulers;
//import io.reactivex.disposables.Disposable;
//import io.reactivex.schedulers.Schedulers;

public class SearchViewModel extends ViewModel
{
    protected MutableLiveData<JSONObject> searchResponse;
    protected MutableLiveData<Throwable> searchError;
    protected MutableLiveData<SearchInfo> searchInfo;

    protected AtomicBoolean isLoading = new AtomicBoolean();


    protected String query;
    protected String[] queryWords;
    protected String queryFull;

    protected CompositeDisposable compositeDisposable = new CompositeDisposable();

    public SearchViewModel()
    {
        searchInfo = new MutableLiveData<SearchInfo>();
        searchError = new MutableLiveData<Throwable>();
        searchResponse = new MutableLiveData<JSONObject>();
    }

    public LiveData<Throwable> getSearchError()
    {
        return searchError;
    }

    public LiveData<SearchInfo> getSearchInfo()
    {
        return searchInfo;
    }

    public void setQuery(String query)
    {
        this.query = query;

        queryWords = query.split(" ");
        queryFull = TextUtils.join("+",queryWords);
    }

    public void refresh()
    {
        int serviceId = 0;
        String searchString = query;
        String[] contentFilter= {Globals.getSearchType()};
        String sortFilter = "";

        Disposable searchDisposable = ExtractorHelper.searchFor(serviceId,
                searchString,
                Arrays.asList(contentFilter),
                sortFilter)
                .subscribeOn(Schedulers.io())
                .observeOn(AndroidSchedulers.mainThread())
                .doOnEvent((searchResult, throwable) -> isLoading.set(false))
                .subscribe(this::handleResult, this::onError);



        compositeDisposable.add(searchDisposable);

    }

    public void handleResult(@NonNull final SearchInfo result)
    {
        searchInfo.postValue(result);
    }

    protected boolean onError(final Throwable exception)
    {
        searchError.postValue(exception);
        return true;
    }


    @Override
    protected void onCleared()
    {
        super.onCleared();
        compositeDisposable.dispose();
    }
}
