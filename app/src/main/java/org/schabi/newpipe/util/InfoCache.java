package org.schabi.newpipe.util;

import android.util.Log;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.collection.LruCache;

import com.open.simplesongcollector.MainActivity;

import org.schabi.newpipe.extractor.Info;
import org.schabi.newpipe.extractor.InfoItem;

import java.util.Map;

public final class InfoCache {
    private final String TAG = getClass().getSimpleName();
    private static final boolean DEBUG = MainActivity.DEBUG;

    private static final InfoCache INSTANCE = new InfoCache();
    private static final int MAX_ITEMS_ON_CACHE = 60;
    /**
     * Trim the cache to this size.
     */
    private static final int TRIM_CACHE_TO = 30;

    private static final LruCache<String, CacheData> LRU_CACHE = new LruCache<>(MAX_ITEMS_ON_CACHE);

    private InfoCache() {
        // no instance
    }

    public static InfoCache getInstance() {
        return INSTANCE;
    }

    @NonNull
    private static String keyOf(final int serviceId, @NonNull final String url,
                                @NonNull final InfoItem.InfoType infoType) {
        return serviceId + url + infoType.toString();
    }

    private static void removeStaleCache() {
        for (final Map.Entry<String, CacheData> entry : InfoCache.LRU_CACHE.snapshot().entrySet()) {
            final CacheData data = entry.getValue();
            if (data != null && data.isExpired()) {
                InfoCache.LRU_CACHE.remove(entry.getKey());
            }
        }
    }

    @Nullable
    private static Info getInfo(@NonNull final String key) {
        final CacheData data = InfoCache.LRU_CACHE.get(key);
        if (data == null) {
            return null;
        }

        if (data.isExpired()) {
            InfoCache.LRU_CACHE.remove(key);
            return null;
        }

        return data.info;
    }

    @Nullable
    public Info getFromKey(final int serviceId, @NonNull final String url,
                           @NonNull final InfoItem.InfoType infoType) {
        if (DEBUG) {
            Log.d(TAG, "getFromKey() called with: "
                    + "serviceId = [" + serviceId + "], url = [" + url + "]");
        }
        synchronized (LRU_CACHE) {
            return getInfo(keyOf(serviceId, url, infoType));
        }
    }

    public void putInfo(final int serviceId, @NonNull final String url, @NonNull final Info info,
                        @NonNull final InfoItem.InfoType infoType) {
        if (DEBUG) {
            Log.d(TAG, "putInfo() called with: info = [" + info + "]");
        }

        final long expirationMillis = ServiceHelper.getCacheExpirationMillis(info.getServiceId());
        synchronized (LRU_CACHE) {
            final CacheData data = new CacheData(info, expirationMillis);
            LRU_CACHE.put(keyOf(serviceId, url, infoType), data);
        }
    }

    public void removeInfo(final int serviceId, @NonNull final String url,
                           @NonNull final InfoItem.InfoType infoType) {
        if (DEBUG) {
            Log.d(TAG, "removeInfo() called with: "
                    + "serviceId = [" + serviceId + "], url = [" + url + "]");
        }
        synchronized (LRU_CACHE) {
            LRU_CACHE.remove(keyOf(serviceId, url, infoType));
        }
    }

    public void clearCache() {
        if (DEBUG) {
            Log.d(TAG, "clearCache() called");
        }
        synchronized (LRU_CACHE) {
            LRU_CACHE.evictAll();
        }
    }

    public void trimCache() {
        if (DEBUG) {
            Log.d(TAG, "trimCache() called");
        }
        synchronized (LRU_CACHE) {
            removeStaleCache();
            LRU_CACHE.trimToSize(TRIM_CACHE_TO);
        }
    }

    public long getSize() {
        synchronized (LRU_CACHE) {
            return LRU_CACHE.size();
        }
    }

    private static final class CacheData {
        private final long expireTimestamp;
        private final Info info;

        private CacheData(@NonNull final Info info, final long timeoutMillis) {
            this.expireTimestamp = System.currentTimeMillis() + timeoutMillis;
            this.info = info;
        }

        private boolean isExpired() {
            return System.currentTimeMillis() > expireTimestamp;
        }
    }
}
