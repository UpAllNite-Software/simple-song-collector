package com.open.simplesongcollector.util;

import android.content.Context;
import android.content.SharedPreferences;

import com.open.simplesongcollector.SimpleSongCollectorApp;

public class Globals
{

    public static final String PreferenceFile = "com.open.simplesongcollector.shared.preferences";


    public static String getSearchType()
    {
        Context context = SimpleSongCollectorApp.getInstance();

        String key = "search_type";

        SharedPreferences sharedPref = context.getSharedPreferences(PreferenceFile,Context.MODE_PRIVATE);

        String defaultValue = "music_songs";

        String result = sharedPref.getString(key,defaultValue);

        return result;

    }

    public static void setSearchType(String searchType)
    {
        Context context = SimpleSongCollectorApp.getInstance();

        String key = "search_type";

        SharedPreferences sharedPref = context.getSharedPreferences(PreferenceFile,Context.MODE_PRIVATE);

        SharedPreferences.Editor editor = sharedPref.edit();
        editor.putString(key,searchType);
        editor.commit();

    }

}
