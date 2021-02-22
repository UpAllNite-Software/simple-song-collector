package org.schabi.newpipe.util;

//import com.grack.nanojson.JsonObject;
//import com.grack.nanojson.JsonParser;
//import com.grack.nanojson.JsonParserException;

//import org.schabi.newpipe.R;
import org.schabi.newpipe.extractor.ServiceList;
import org.schabi.newpipe.extractor.StreamingService;

        import java.util.concurrent.TimeUnit;

import static org.schabi.newpipe.extractor.ServiceList.SoundCloud;

public final class ServiceHelper {
    private static final StreamingService DEFAULT_FALLBACK_SERVICE = ServiceList.YouTube;

    private ServiceHelper() { }

    public static long getCacheExpirationMillis(final int serviceId) {
        if (serviceId == SoundCloud.getServiceId()) {
            return TimeUnit.MILLISECONDS.convert(5, TimeUnit.MINUTES);
        } else {
            return TimeUnit.MILLISECONDS.convert(1, TimeUnit.HOURS);
        }
    }

    public static boolean isBeta(final StreamingService s) {
        switch (s.getServiceInfo().getName()) {
            case "YouTube":
                return false;
            default:
                return true;
        }
    }

}
