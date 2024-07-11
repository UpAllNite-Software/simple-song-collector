package com.open.simplesongcollector;

import android.content.Context;
import android.graphics.Bitmap;
import android.graphics.drawable.BitmapDrawable;
import android.media.MediaScannerConnection;
import android.net.Uri;
import android.os.Environment;
import android.os.Handler;
import android.os.Looper;
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

import java.io.ByteArrayOutputStream;
import java.io.File;
import java.io.IOException;
import java.io.InputStream;
import java.io.RandomAccessFile;
import java.net.HttpURLConnection;
import java.net.URL;
import java.util.List;
import java.util.Random;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.TimeUnit;

import us.shandian.giga.io.FileStream;

public class DownloadTask
{
    public final String TAG = getClass().getSimpleName();
    private YouTubeSearchResult result;
    private Context context;
    static final int BUFFER_SIZE = 16 * 1024;

    protected MutableLiveData<Integer> downloadProgress;


    private Handler mHandler = new Handler(Looper.getMainLooper());
    private StreamInfo streamInfo;
    private int bytesWritten;
    private int fileSize;
    private int chunkSize = 300 * 1024;


    public DownloadTask(Context context, YouTubeSearchResult result)
    {
        this.context = context;
        this.result = result;
        downloadProgress = new MutableLiveData<Integer>();
        downloadProgress.postValue(1);
    }

    public LiveData<Integer> getDownloadProgress() { return downloadProgress; }

    private static boolean isValidFilenameChar(char c) {
        if ((0x00 <= c && c <= 0x1f)) {
            return false;
        }
        switch (c) {
            case '"':
            case '*':
            case '/':
            case ':':
            case '<':
            case '>':
            case '?':
            case '\\':
            case '|':
            case 0x7F:
                return false;
            default:
                return true;
        }
    }


    @NonNull
    private File getPrivateDownloadLocation() {
        File downloadsDir = SimpleSongCollectorApp.getInstance().getFilesDir();
        return downloadsDir;
    }

    @NonNull
    private File getPublicDownloadLocation() {
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

        File downloadFolder = getPrivateDownloadLocation();
        String fileName = streamInfo.getName();

        String nameUnique = streamInfo.getUploaderName();
        if (nameUnique != null && nameUnique.length()>0)
        {
            nameUnique = nameUnique.replace(" - Topic","");
        }

        if (nameUnique == null || nameUnique.length()==0)
        {
            nameUnique = streamInfo.getId();
        }

        if (nameUnique != null && nameUnique.length() > 0)
        {
            fileName += ".";
            fileName += nameUnique;
        }

        String sanitizedName = "";
        for(char ch: fileName.toCharArray())
        {
            if (isValidFilenameChar(ch))
            {
                sanitizedName+=ch;
            }
        }

        if (sanitizedName.isEmpty())
        {
            Random r = new Random(System.nanoTime());
            for(int i=0;i<fileName.length();i++)
            {
                int rnd = (int) (Math.random() * 52); // or use Random or whatever
                char base = (rnd < 26) ? 'A' : 'a';
                sanitizedName+=(char) (base + rnd % 26);
            }
        }

        if (sanitizedName.startsWith("."))
        {
            //avoid hidden file starting with dot
            sanitizedName = "_" + sanitizedName;
        }

        sanitizedName = String.format("%s.%s.dash", sanitizedName,selectedStream.getFormat().getSuffix());


        return directDownload(selectedStream, downloadFolder,sanitizedName);

    }

    private Uri directDownload(AudioStream selectedStream, File downloadFolder, String fileName) throws Exception
    {
        bytesWritten = 0;
        HttpURLConnection connection = openConnection(selectedStream.getContent(),true,-1,-1);
        int statusCode = connection.getResponseCode();
        connection.getInputStream().close();

        if (statusCode != 200)
        {
            throw new Exception(String.format("Unable to open audio file stream: %d",statusCode));
        }

        fileSize = connection.getContentLength();

        if (fileSize == 0)
        {
            throw new Exception(String.format("Unable to open audio file stream. File size is zero"));
        }

        System.out.printf("Retreiving file with size %d from server\n",fileSize);

        File fDash = new File(downloadFolder,fileName);
        RandomAccessFile f = new RandomAccessFile(fDash,"rw");
        f.setLength(fileSize);

        Random rand = new Random();
        chunkSize += rand.nextInt(64) * 1024;

        //split into chunks
        int chunkCount = (fileSize / chunkSize) + ((fileSize % chunkSize) > 0 ? 1 : 0);
        CountDownLatch latch = new CountDownLatch(chunkCount);
        ExecutorService pool = Executors.newFixedThreadPool(4);

        for(int chunkStart = 0;chunkStart<fileSize;chunkStart += chunkSize)
        {
            int chunkEnd = Math.min(chunkStart + chunkSize,fileSize);


            Thread.sleep(100);
            int finalChunkStart = chunkStart;
            Runnable task = () -> {
                try
                {
                    downloadFileChunk(f,selectedStream, finalChunkStart,chunkEnd - 1);

                } catch (Exception e)
                {
                    e.printStackTrace();
                }
                latch.countDown();

            };

            pool.execute(task);
        }

        latch.await(180,TimeUnit.SECONDS);

        pool.shutdown();

        f.close();

        if (bytesWritten != fileSize)
        {
            throw new Exception(String.format("Download failed. Received %d bytes of %d",bytesWritten,fileSize));
        }


        SharpStream dashStream = new FileStream(fDash);

        String m4aFileName = fDash.getName();
        m4aFileName = m4aFileName.replace(".dash","");

        File m4aFolderFile = getPublicDownloadLocation();

        File fM4a = new File(m4aFolderFile,m4aFileName);
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

    private void downloadFileChunk(RandomAccessFile f, AudioStream selectedStream, int chunkStart, int chunkEnd) throws Exception
    {
        HttpURLConnection connection = openConnection(selectedStream.getUrl(),false,chunkStart,chunkEnd);
        int statusCode = connection.getResponseCode();

        if (statusCode != 206)
        {
            throw new Exception(String.format("Unable to open audio file stream chunk: %d",statusCode));
        }

        int chunkNumber = chunkStart / chunkSize;

        System.out.printf("Getfile request status for chunk %d from byte %d to %d is %d\n",chunkNumber, chunkStart, chunkEnd, statusCode);

        int pos = chunkStart;
        int end = chunkEnd;

        try (InputStream is = connection.getInputStream()) {
            byte[] buf = new byte[BUFFER_SIZE];
            int len;

            // use always start <= end
            // fixes a deadlock because in some videos, youtube is sending one byte alone
            while (pos <= end && (len = is.read(buf, 0, buf.length)) != -1) {
                synchronized (f)
                {
                    f.seek(pos);
                    f.write(buf, 0, len);
                    pos += len;
                    bytesWritten += len;
                    int newProgress = (bytesWritten * 100) / fileSize;
                    if (newProgress < 1)
                    {
                        newProgress = 1;
                    }
                    System.out.printf("download progress: %d of %d is %d\n", bytesWritten, fileSize, newProgress);
                    downloadProgress.postValue(newProgress);
                }

            }
        }

        connection.getInputStream().close();

        System.out.printf("Chunk %d completed\n",chunkNumber);

    }

    private Uri processSuccessfulDownloadWithPath(@NonNull String m4aFilePath, @NonNull YouTubeSearchResult result) throws TagException, ReadOnlyFileException, CannotReadException, InvalidAudioFrameException, IOException, CannotWriteException, InterruptedException
    {

        String title = streamInfo.getName();
        String artist = streamInfo.getUploaderName();
        artist = artist.replace(" - Topic","");
        String album = null;
        Description description = streamInfo.getDescription();
        String content = description.getContent();
        String[] contentLines = content.contains("<br>") ? content.split("<br>") : content.split("/n/n");
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

        String[] filePaths = new String[]{m4aFilePath};
        String[] mimeTypes = new String[]{"audio/m4a"};
        CountDownLatch latch = new CountDownLatch(1);
        final Uri[] updated = new Uri[1];

        MediaScannerConnection.scanFile(SimpleSongCollectorApp.getInstance().getApplicationContext(), filePaths, mimeTypes, new MediaScannerConnection.OnScanCompletedListener()
        {
            @Override
            public void onScanCompleted(String path, Uri uri)
            {
                downloadProgress.postValue(100);
                updated[0] = uri;
                latch.countDown();
            }
        });

        latch.await();
        return updated[0];
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
