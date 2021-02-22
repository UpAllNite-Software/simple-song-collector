package com.open.simplesongcollector;

import android.app.Application;
import android.content.SharedPreferences;
import android.preference.PreferenceManager;
import android.util.Log;

import org.schabi.newpipe.extractor.NewPipe;
import org.schabi.newpipe.extractor.downloader.Downloader;
import org.schabi.newpipe.extractor.localization.ContentCountry;
import org.schabi.newpipe.extractor.localization.Localization;
import org.schabi.newpipe.DownloaderImpl;

import java.util.Locale;

import io.reactivex.exceptions.UndeliverableException;
import io.reactivex.plugins.RxJavaPlugins;

public class SimpleSongCollectorApp extends Application
{
    private static SimpleSongCollectorApp INSTANCE = null;

    public final String TAG = getClass().getSimpleName();

    public SimpleSongCollectorApp() {

    };

    public static SimpleSongCollectorApp getInstance() {
        return(INSTANCE);
    }

    @Override
    public void onCreate() {
        INSTANCE = this;
        super.onCreate();

        configureRxJavaErrorHandler();

        NewPipe.init(getDownloader(),
                Localization.fromLocale(Locale.ENGLISH),
                ContentCountry.DEFAULT);
    }

    protected Downloader getDownloader() {
        final DownloaderImpl downloader = DownloaderImpl.init(null);
        setCookiesToDownloader(downloader);
        return downloader;
    }

    protected void setCookiesToDownloader(final DownloaderImpl downloader) {
        final SharedPreferences prefs = PreferenceManager.getDefaultSharedPreferences(
                getApplicationContext());
        final String key = getApplicationContext().getString(R.string.recaptcha_cookies_key);
        downloader.setCookie(DownloaderImpl.RECAPTCHA_COOKIES_KEY, prefs.getString(key, ""));
        downloader.updateYoutubeRestrictedModeCookies(getApplicationContext());
    }


    // other instance methods can follow

    private void configureRxJavaErrorHandler() {
        RxJavaPlugins.setErrorHandler(e -> {

            if (e instanceof UndeliverableException) {
                // As UndeliverableException is a wrapper, get the cause of it to get the "real" exception
                e = e.getCause();
            }

            if (e instanceof InterruptedException) {
                // fine, some blocking code was interrupted by a dispose call
                return;
            }

            Log.e(TAG, "Undeliverable exception received, not sure what to do", e);
        });
    }
}

