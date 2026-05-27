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

import com.open.simplesongcollector.util.Globals;

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
import org.schabi.newpipe.extractor.stream.VideoStream;

import android.media.MediaExtractor;
import android.media.MediaFormat;
import android.media.MediaMuxer;
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

        // YouTube may serve SABR-only responses with no usable audio stream URLs.
        // Try audio streams first, then fall back to extracting audio from video stream.
        streamInfo = StreamInfo.getInfo(NewPipe.getService(0), result.videoUrl);

        // Log all available streams for diagnostics
        List<AudioStream> audioStreams = streamInfo.getAudioStreams();
        System.out.println("Audio streams: " + audioStreams.size());
        for (AudioStream as : audioStreams) {
            System.out.println("  Audio: " + as.getFormat().getSuffix() + " " + as.getBitrate() + "kbps url=" + as.getContent());
        }
        List<VideoStream> videoStreams = streamInfo.getVideoStreams();
        System.out.println("Video streams: " + videoStreams.size());
        for (VideoStream vs : videoStreams) {
            System.out.println("  Video: " + vs.getFormat().getSuffix() + " " + vs.getResolution() + " url=" + vs.getContent());
        }

        AudioStream selectedStream = null;
        int maxBitrate = 0;
        for (AudioStream audioStream : audioStreams) {
            if (audioStream.getFormat().getSuffix().compareToIgnoreCase("m4a") == 0) {
                if (audioStream.getBitrate() > maxBitrate) {
                    selectedStream = audioStream;
                    maxBitrate = audioStream.getBitrate();
                }
            }
        }

        // If no audio-only streams, fall back to video stream and extract audio
        if (selectedStream == null && !videoStreams.isEmpty()) {
            System.out.println("No audio streams available, falling back to video stream audio extraction");
            VideoStream videoStream = videoStreams.get(0);
            return downloadVideoAndExtractAudio(videoStream);
        }

        if (selectedStream == null) {
            throw new Exception("No audio or video streams available for source.");
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
        if(fM4a.exists())
        {
            boolean res = fM4a.delete();
            if (!res) {
                throw new Exception("Download failed... file already exists in downloads folder");
            }
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

        String searchType = Globals.getSearchType();
        if (searchType.equals("music_songs"))
        {
            Description description = streamInfo.getDescription();
            String content = description.getContent();
            String[] contentLines = content.contains("<br>") ? content.split("<br>") : content.split("/n/n");

            if (contentLines != null && contentLines.length > 2 && contentLines[2].contains("·"))
            {
                String[] parts = contentLines[2].split("·");
                if (parts.length > 1)
                {
                    title = parts[0].trim();
                    artist = parts[1].trim();
                }
                if (contentLines.length > 4)
                {
                    album = contentLines[4].trim();
                }
            }
        }
        else {
            if (title.contains("-"))
            {
                String[] parts = title.split("-", 2);
                if (parts.length > 1)
                {
                    artist = parts[0].trim();
                    title = parts[1].trim();
                }
            }
            else if (title.contains(":"))
            {
                String[] parts = title.split(":", 2);
                if (parts.length > 1)
                {
                    artist = parts[0].trim();
                    title = parts[1].trim();
                }
            }

        }



        File fM4a = new File(m4aFilePath);

        try {
            AudioFile af = AudioFileIO.read(fM4a);
            Tag tag = af.getTagAndConvertOrCreateAndSetDefault();
            String metaTitle = tag.getFirst(FieldKey.TITLE);
            String metaArtist = tag.getFirst(FieldKey.ARTIST);

            if (metaTitle.isEmpty()) {
                tag.setField(FieldKey.TITLE, title);
            }
            if (metaArtist.isEmpty()) {
                tag.setField(FieldKey.ARTIST, artist);
            }
            if (album != null) {
                tag.setField(FieldKey.ALBUM, album);
            }

            if (result.thumbnailImage != null) {
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
        } catch (Exception e) {
            // TODO: fix M4A structure so jaudiotagger can parse video-extracted files
            System.out.println("Warning: unable to write tags, file saved without metadata: " + e.getMessage());
        }

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

    private Uri downloadVideoAndExtractAudio(VideoStream videoStream) throws Exception {
        bytesWritten = 0;
        String videoUrl = videoStream.getContent();

        HttpURLConnection connection = openConnection(videoUrl, true, -1, -1);
        int statusCode = connection.getResponseCode();
        connection.getInputStream().close();

        if (statusCode != 200) {
            throw new Exception(String.format("Unable to open video stream: %d", statusCode));
        }

        fileSize = connection.getContentLength();
        if (fileSize == 0) {
            throw new Exception("Unable to open video stream. File size is zero");
        }

        System.out.printf("Downloading video file with size %d for audio extraction\n", fileSize);

        File downloadFolder = getPrivateDownloadLocation();
        File videoFile = new File(downloadFolder, "video_temp.mp4");

        // Simple single-threaded download for the video
        HttpURLConnection dlConn = openConnection(videoUrl, false, -1, -1);
        try (InputStream is = dlConn.getInputStream();
             java.io.FileOutputStream fos = new java.io.FileOutputStream(videoFile)) {
            byte[] buf = new byte[BUFFER_SIZE];
            int len;
            while ((len = is.read(buf)) != -1) {
                fos.write(buf, 0, len);
                bytesWritten += len;
                int progress = (int) ((bytesWritten * 100L) / fileSize);
                if (progress < 1) progress = 1;
                downloadProgress.postValue(progress);
            }
        }
        dlConn.getInputStream().close();

        System.out.println("Video download complete, extracting audio track");

        // Extract audio track from MP4 using MediaExtractor + MediaMuxer
        String fileName = streamInfo.getName();
        String nameUnique = streamInfo.getUploaderName();
        if (nameUnique != null && nameUnique.length() > 0) {
            nameUnique = nameUnique.replace(" - Topic", "");
        }
        if (nameUnique == null || nameUnique.length() == 0) {
            nameUnique = streamInfo.getId();
        }

        String sanitizedName = "";
        String rawName = fileName + "." + nameUnique;
        for (char ch : rawName.toCharArray()) {
            if (isValidFilenameChar(ch)) {
                sanitizedName += ch;
            }
        }
        if (sanitizedName.isEmpty()) {
            sanitizedName = "extracted_audio";
        }
        if (sanitizedName.startsWith(".")) {
            sanitizedName = "_" + sanitizedName;
        }

        File m4aFolderFile = getPublicDownloadLocation();
        File m4aFile = new File(m4aFolderFile, sanitizedName + ".m4a");
        if (m4aFile.exists()) {
            m4aFile.delete();
        }

        MediaExtractor extractor = new MediaExtractor();
        extractor.setDataSource(videoFile.getAbsolutePath());

        int audioTrackIndex = -1;
        for (int i = 0; i < extractor.getTrackCount(); i++) {
            MediaFormat format = extractor.getTrackFormat(i);
            String mime = format.getString(MediaFormat.KEY_MIME);
            System.out.println("  Track " + i + ": " + mime);
            if (mime != null && mime.startsWith("audio/")) {
                audioTrackIndex = i;
                break;
            }
        }

        if (audioTrackIndex < 0) {
            videoFile.delete();
            throw new Exception("No audio track found in video stream");
        }

        extractor.selectTrack(audioTrackIndex);
        MediaFormat audioFormat = extractor.getTrackFormat(audioTrackIndex);

        MediaMuxer muxer = new MediaMuxer(m4aFile.getAbsolutePath(), MediaMuxer.OutputFormat.MUXER_OUTPUT_MPEG_4);
        int outputTrackIndex = muxer.addTrack(audioFormat);
        muxer.start();

        java.nio.ByteBuffer buffer = java.nio.ByteBuffer.allocate(1024 * 1024);
        android.media.MediaCodec.BufferInfo bufferInfo = new android.media.MediaCodec.BufferInfo();

        while (true) {
            int sampleSize = extractor.readSampleData(buffer, 0);
            if (sampleSize < 0) break;

            bufferInfo.offset = 0;
            bufferInfo.size = sampleSize;
            bufferInfo.presentationTimeUs = extractor.getSampleTime();
            bufferInfo.flags = (extractor.getSampleFlags() & MediaExtractor.SAMPLE_FLAG_SYNC) != 0
                    ? android.media.MediaCodec.BUFFER_FLAG_KEY_FRAME : 0;

            muxer.writeSampleData(outputTrackIndex, buffer, bufferInfo);
            extractor.advance();
        }

        muxer.stop();
        muxer.release();
        extractor.release();
        videoFile.delete();

        System.out.println("Audio extraction complete, running faststart: " + m4aFile.getAbsolutePath());
        Mp4FastStart.process(m4aFile);

        return processSuccessfulDownloadWithPath(m4aFile.getAbsolutePath(), result);
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
