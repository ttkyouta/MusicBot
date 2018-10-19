package com.jagrosh.jmusicbot.nico;

//
// Source code recreated from a .class file by IntelliJ IDEA
// (powered by Fernflower decompiler)
//
import com.sedmelluq.discord.lavaplayer.container.mpeg.MpegAudioTrack;
import com.sedmelluq.discord.lavaplayer.source.AudioSourceManager;
import com.sedmelluq.discord.lavaplayer.tools.io.HttpInterface;
import com.sedmelluq.discord.lavaplayer.tools.io.PersistentHttpStream;
import com.sedmelluq.discord.lavaplayer.track.AudioTrack;
import com.sedmelluq.discord.lavaplayer.track.AudioTrackInfo;
import com.sedmelluq.discord.lavaplayer.track.DelegatedAudioTrack;
import com.sedmelluq.discord.lavaplayer.track.playback.LocalAudioTrackExecutor;
import org.apache.commons.text.StringEscapeUtils;
import org.apache.http.client.methods.CloseableHttpResponse;
import org.apache.http.client.methods.HttpGet;
import org.apache.http.client.utils.URLEncodedUtils;
import org.apache.http.util.EntityUtils;
import org.json.JSONObject;
import org.jsoup.Jsoup;
import org.jsoup.nodes.Document;
import org.jsoup.nodes.Element;
import org.jsoup.select.Elements;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.IOException;
import java.net.URI;
import java.nio.charset.StandardCharsets;
import java.util.Map;

import static com.sedmelluq.discord.lavaplayer.tools.DataFormatTools.convertToMapLayout;

/**
 * Audio track that handles processing NicoNico tracks.
 */
public class NicoAudioTrackOld extends DelegatedAudioTrack {
    private static final Logger log = LoggerFactory.getLogger(NicoAudioTrackOld.class);

    private final NicoAudioSourceManager sourceManager;

    /**
     * @param trackInfo Track info
     * @param sourceManager Source manager which was used to find this track
     */
    public NicoAudioTrackOld(AudioTrackInfo trackInfo, NicoAudioSourceManager sourceManager) {
        super(trackInfo);

        this.sourceManager = sourceManager;
    }

    @Override
    public void process(LocalAudioTrackExecutor localExecutor) throws Exception {
        sourceManager.checkLoggedIn();

        try (HttpInterface httpInterface = sourceManager.getHttpInterface()) {
            loadVideoMainPage(httpInterface);
            String playbackUrl = loadPlaybackUrl(httpInterface);

            log.debug("Starting NicoNico track from URL: {}", playbackUrl);

            try (PersistentHttpStream stream = new PersistentHttpStream(httpInterface, new URI(playbackUrl), null)) {
                processDelegate(new MpegAudioTrack(trackInfo, stream), localExecutor);
            }
        }
    }

    private void loadVideoMainPage(HttpInterface httpInterface) throws IOException {
        HttpGet request = new HttpGet("http://www.nicovideo.jp/watch/" + trackInfo.identifier);

        try (CloseableHttpResponse response = httpInterface.execute(request)) {
            int statusCode = response.getStatusLine().getStatusCode();
            if (statusCode != 200) {
                throw new IOException("Unexpected status code from video main page: " + statusCode);
            }

            EntityUtils.consume(response.getEntity());
        }
    }

    private String loadPlaybackUrl(HttpInterface httpInterface) throws IOException {
        HttpGet request = new HttpGet("https://www.nicovideo.jp/watch/" + this.trackInfo.identifier);
        request.setHeader("User-Agent", "Mozilla/5.0 (Windows NT 10.0; Win64; x64) AppleWebKit/537.36 (KHTML, like Gecko) Chrome/70.0.3538.54 Safari/537.36");
        CloseableHttpResponse response = httpInterface.execute(request);
        int statusCode = response.getStatusLine().getStatusCode();
        if (statusCode != 200) {
            throw new IOException("Unexpected status code from playback parameters page: " + statusCode);
        }

        Document doc = Jsoup.parse(convertStreamToString(response.getEntity().getContent()));
        Elements et = doc.select("#js-initial-watch-data");
        for (Element headline : et) {
            String data = headline.attr("data-api-data");
            String escape = StringEscapeUtils.unescapeJava(data);
            //System.out.println(escape);

            JSONObject json = new JSONObject(data);
            JSONObject video = json.getJSONObject("video");
            return video.getJSONObject("smileInfo").getString("url");
        }
        return "";
    }

    @Override
    public AudioTrack makeClone() {
        return new NicoAudioTrack(trackInfo, sourceManager);
    }

    @Override
    public AudioSourceManager getSourceManager() {
        return sourceManager;
    }

        static String convertStreamToString(java.io.InputStream is) {
            java.util.Scanner s = new java.util.Scanner(is).useDelimiter("\\A");
            return s.hasNext() ? s.next() : "";
        }
}