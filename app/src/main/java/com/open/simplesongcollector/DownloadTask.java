package com.open.simplesongcollector;

import android.content.ContentValues;
import android.content.Context;
import android.database.Cursor;
import android.graphics.Bitmap;
import android.graphics.drawable.BitmapDrawable;
import android.net.Uri;
import android.os.Environment;
import android.os.Handler;
import android.os.Looper;
import android.provider.MediaStore;
import android.util.Log;

import androidx.annotation.NonNull;
import androidx.lifecycle.LiveData;
import androidx.lifecycle.MutableLiveData;

import org.jaudiotagger.audio.AudioFile;
import org.jaudiotagger.audio.AudioFileIO;
import org.jaudiotagger.audio.exceptions.CannotReadException;
import org.jaudiotagger.audio.exceptions.CannotWriteException;
import org.jaudiotagger.audio.exceptions.InvalidAudioFrameException;
import org.jaudiotagger.audio.exceptions.ReadOnlyFileException;
import org.jaudiotagger.tag.FieldKey;
import org.jaudiotagger.tag.Tag;
import org.jaudiotagger.tag.TagException;
import org.jaudiotagger.tag.images.Artwork;
import org.jaudiotagger.tag.images.ArtworkFactory;
import org.schabi.newpipe.DownloaderImpl;
import org.schabi.newpipe.extractor.NewPipe;
import org.schabi.newpipe.extractor.stream.AudioStream;
import org.schabi.newpipe.extractor.stream.Description;
import org.schabi.newpipe.extractor.stream.StreamInfo;
import org.schabi.newpipe.streams.Mp4FromDashWriter;
import org.schabi.newpipe.streams.io.SharpStream;

import java.io.BufferedReader;
import java.io.ByteArrayOutputStream;
import java.io.File;
import java.io.FileOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.net.HttpURLConnection;
import java.net.URL;
import java.util.List;

import us.shandian.giga.io.FileStream;

public class DownloadTask
{
    public final String TAG = getClass().getSimpleName();
    private YouTubeSearchResult result;
    private Context context;
    static final int BUFFER_SIZE = 64 * 1024;

    protected MutableLiveData<Integer> downloadProgress;


    private Handler mHandler = new Handler(Looper.getMainLooper());
    private StreamInfo streamInfo;

    public DownloadTask(Context context, YouTubeSearchResult result)
    {
        this.context = context;
        this.result = result;
        downloadProgress = new MutableLiveData<Integer>();
        downloadProgress.postValue(1);
    }

    public LiveData<Integer> getDownloadProgress() { return downloadProgress; }


    @NonNull
    private File getDownloadLocation() {
        File downloadsDir = Environment.getExternalStoragePublicDirectory(Environment.DIRECTORY_DOWNLOADS);
        File youtubeDLDir = new File(downloadsDir, "SimpleSongFinder");
        if (!youtubeDLDir.exists())
        {
            youtubeDLDir.mkdir();
        }
        return youtubeDLDir;
    }

    @NonNull
    public Uri execute() throws Exception
    {

        streamInfo = StreamInfo.getInfo(NewPipe.getService(0), result.videoUrl);

        AudioStream selectedStream = null;
        List<AudioStream> audioStreams = streamInfo.getAudioStreams();
        for(AudioStream audioStream: audioStreams)
        {
            if (audioStream.getFormat().getSuffix().compareToIgnoreCase("m4a")==0)
            {
                System.out.println("Found m4a stream at url: "+audioStream.getUrl());
                selectedStream = audioStream;
                break;
            }
        }

        if (selectedStream==null)
        {
            throw new Exception("No audio streams available for source.");
        }

        File downloadFolder = getDownloadLocation();
        String fileName = String.format("%s.%s.dash", streamInfo.getName(),selectedStream.getFormat().getSuffix());

        return directDownload(selectedStream, downloadFolder,fileName);

    }

    private Uri directDownload(AudioStream selectedStream, File downloadFolder, String fileName) throws Exception
    {
        HttpURLConnection connection = openConnection(selectedStream.getUrl(),true,-1,-1);
        int statusCode = connection.getResponseCode();
        connection.getInputStream().close();

        if (statusCode != 200)
        {
            throw new Exception(String.format("Unable to open audio file stream: %d",statusCode));
        }

        int fileSize = connection.getContentLength();

        System.out.printf("Retreiving file with size %d from server\n",fileSize);

        connection = openConnection(selectedStream.getUrl(),false,0,fileSize);
        statusCode = connection.getResponseCode();

        System.out.printf("Getfile request status is %d\n",statusCode);

        //todo read and write to file
        int pos = 0;
        int end = fileSize;
        File fDash = new File(downloadFolder,fileName);
        FileOutputStream f = new FileOutputStream(fDash);

        try (InputStream is = connection.getInputStream()) {
            byte[] buf = new byte[BUFFER_SIZE];
            int len;

            // use always start <= end
            // fixes a deadlock because in some videos, youtube is sending one byte alone
            while (pos <= end && (len = is.read(buf, 0, buf.length)) != -1) {
                f.write(buf, 0, len);
                pos += len;
                int newProgress = (pos * 100)/end;
                if (newProgress < 1)
                {
                    newProgress = 1;
                }
                System.out.printf("download progress: %d of %d is %d\n",pos,end,newProgress);
                downloadProgress.postValue(newProgress);

            }
        }

        f.close();
        connection.getInputStream().close();

        SharpStream dashStream = new FileStream(fDash);

        String m4aFilePath = fDash.getAbsolutePath().replace(".dash","");
        File fM4a = new File(m4aFilePath);
        if (fM4a.exists())
        {
            fM4a.delete();
        }
        SharpStream m4aStream = new FileStream(fM4a);

        //todo: mp4 from dash processing
        Mp4FromDashWriter muxer = new Mp4FromDashWriter(dashStream);
        muxer.setMainBrand(0x4D344120);// binary string "M4A "
        muxer.parseSources();
        muxer.selectTracks(0);
        muxer.build(m4aStream);

        fDash.delete();
        m4aStream.close();



        return processSuccessfulDownloadWithPath(fM4a.getAbsolutePath(),result);

    }

    private Uri processSuccessfulDownloadWithPath(@NonNull String m4aFilePath, @NonNull YouTubeSearchResult result) throws TagException, ReadOnlyFileException, CannotReadException, InvalidAudioFrameException, IOException, CannotWriteException
    {

        String title = streamInfo.getName();
        String artist = streamInfo.getUploaderName();
        String album = null;
        Description description = streamInfo.getDescription();
        String content = description.getContent();
        String[] contentLines = content.split("<br>");
        if (contentLines!= null && contentLines.length > 1 && contentLines[2].contains("·"))
        {
            String[] parts = contentLines[2].split("·");
            title = parts[0].trim();
            artist = parts[1].trim();
            if (contentLines.length>3)
            {
                album = contentLines[4].trim();
            }
        }



        File fM4a = new File(m4aFilePath);

        AudioFile af = AudioFileIO.read(fM4a);
        Tag tag = af.getTag();
        String metaTitle = tag.getFirst(FieldKey.TITLE);
        String metaArtist = tag.getFirst(FieldKey.ARTIST);

        if (metaTitle.isEmpty())
        {
            tag.setField(FieldKey.TITLE, title);
        }
        if (metaArtist.isEmpty())
        {
            tag.setField(FieldKey.ARTIST, artist);
        }
        if (album != null)
        {
            tag.setField(FieldKey.ALBUM,album);
        }

        if (result.thumbnailImage != null)
        {
            Bitmap bitmap = ((BitmapDrawable) result.thumbnailImage).getBitmap();
            ByteArrayOutputStream stream = new ByteArrayOutputStream();
            bitmap.compress(Bitmap.CompressFormat.JPEG, 100, stream);
            byte[] bitmapdata = stream.toByteArray();
            Artwork artwork = ArtworkFactory.getNew();
            artwork.setBinaryData(bitmapdata);
            int i = artwork.getPictureType();
            Log.d(TAG, "image type " + i);
            tag.addField(artwork);
        }

        AudioFileIO.write(af);

        ContentValues contentValues = new ContentValues();
        contentValues.put(MediaStore.Audio.Media.IS_MUSIC,1);
        contentValues.put(MediaStore.MediaColumns.TITLE,title);
        if (artist!=null)
        {
            contentValues.put(MediaStore.MediaColumns.ARTIST,artist);
        }
        if (result.durationSeconds > 0)
        {
            contentValues.put(MediaStore.MediaColumns.DURATION,result.durationSeconds * 1000);
        }
        contentValues.put(MediaStore.MediaColumns.DATE_ADDED, System.currentTimeMillis());
        contentValues.put(MediaStore.MediaColumns.DATA,m4aFilePath);
        contentValues.put(MediaStore.MediaColumns.MIME_TYPE,"audio/m4a");
        Uri newUri = context.getContentResolver().insert(MediaStore.Audio.Media.EXTERNAL_CONTENT_URI,contentValues);
        if (newUri == null)
        {
            Uri audioUri = MediaStore.Audio.Media.getContentUri("external");
            String[] projection = {MediaStore.MediaColumns._ID };
            String selection = MediaStore.MediaColumns.DATA + " LIKE ?";
            String[] selectionArgs = new String[] { m4aFilePath };
            Cursor cursor = context.getContentResolver().query(audioUri,projection,selection,selectionArgs,null);
            if (cursor.getCount()!=0)
            {
                cursor.moveToFirst();
                String songId = cursor.getString(0);
                newUri = Uri.parse(audioUri.toString() + "/" + songId);
            }
            cursor.close();
        }

        downloadProgress.postValue(100);
        return newUri;
    }

    HttpURLConnection openConnection(String url, boolean headRequest, long rangeStart, long rangeEnd) throws IOException {
        HttpURLConnection conn = (HttpURLConnection) new URL(url).openConnection();
        conn.setInstanceFollowRedirects(true);
        conn.setRequestProperty("User-Agent", DownloaderImpl.USER_AGENT);
        conn.setRequestProperty("Accept", "*/*");
        conn.setRequestProperty("Accept-Encoding", "*");

        if (headRequest) conn.setRequestMethod("HEAD");

        // BUG workaround: switching between networks can freeze the download forever
        conn.setConnectTimeout(30000);

        if (rangeStart >= 0) {
            String req = "bytes=" + rangeStart + "-";
            if (rangeEnd > 0) req += rangeEnd;

            conn.setRequestProperty("Range", req);
        }

        return conn;
    }

}
