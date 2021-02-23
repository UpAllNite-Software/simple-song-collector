package org.schabi.newpipe.util;

import org.schabi.newpipe.extractor.NewPipe;
import org.schabi.newpipe.extractor.search.SearchInfo;

import java.util.List;

import io.reactivex.rxjava3.core.Single;

//import io.reactivex.Single;

public final class ExtractorHelper
{

    private ExtractorHelper() {
        //no instance
    }

    private static void checkServiceId(final int serviceId) {
        if (serviceId == Constants.NO_SERVICE_ID) {
            throw new IllegalArgumentException("serviceId is NO_SERVICE_ID");
        }
    }



    public static Single<SearchInfo> searchFor(final int serviceId, final String searchString,
                                               final List<String> contentFilter,
                                               final String sortFilter) {
        checkServiceId(serviceId);
        return Single.fromCallable(() ->
                SearchInfo.getInfo(NewPipe.getService(serviceId),
                        NewPipe.getService(serviceId)
                                .getSearchQHFactory()
                                .fromQuery(searchString, contentFilter, sortFilter)));
    }

}
