package com.jagrosh.jmusicbot.nico;

//
// Source code recreated from a .class file by IntelliJ IDEA
// (powered by Fernflower decompiler)
//

import com.sedmelluq.discord.lavaplayer.container.mpeg.MpegAudioTrack;
import com.sedmelluq.discord.lavaplayer.source.AudioSourceManager;
import com.sedmelluq.discord.lavaplayer.source.stream.M3uStreamAudioTrack;
import com.sedmelluq.discord.lavaplayer.source.stream.M3uStreamSegmentUrlProvider;
import com.sedmelluq.discord.lavaplayer.tools.DataFormatTools;
import com.sedmelluq.discord.lavaplayer.tools.io.HttpInterface;
import com.sedmelluq.discord.lavaplayer.tools.io.PersistentHttpStream;
import com.sedmelluq.discord.lavaplayer.track.AudioTrack;
import com.sedmelluq.discord.lavaplayer.track.AudioTrackInfo;
import com.sedmelluq.discord.lavaplayer.track.DelegatedAudioTrack;
import com.sedmelluq.discord.lavaplayer.track.playback.LocalAudioTrackExecutor;

import java.io.IOException;
import java.net.URI;
import java.nio.charset.StandardCharsets;
import java.util.Map;

import org.apache.commons.text.StringEscapeUtils;
import org.apache.http.client.methods.*;
import org.apache.http.client.utils.URLEncodedUtils;
import org.apache.http.entity.StringEntity;
import org.apache.http.util.EntityUtils;
import org.json.JSONArray;
import org.json.JSONObject;
import org.jsoup.Connection;
import org.jsoup.Jsoup;
import org.jsoup.nodes.Document;
import org.jsoup.nodes.Element;
import org.jsoup.select.Elements;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

public class NicoAudioTrack extends M3uStreamAudioTrack {
    private static final Logger log = LoggerFactory.getLogger(NicoAudioTrack.class);
    private final NicoAudioSourceManager sourceManager;
    private final M3uStreamSegmentUrlProvider segmentUrlProvider;
    private String videoUrl = null;
    private JSONObject session = null;
    private int heartbeat_lifetime = 0;
    private long tokenExpirationTime = 0;

    public NicoAudioTrack(AudioTrackInfo trackInfo, NicoAudioSourceManager sourceManager) {
        super(trackInfo);
        this.sourceManager = sourceManager;
        this.segmentUrlProvider = new NicoSegmentUrlProvider(this);
    }

    protected M3uStreamSegmentUrlProvider getSegmentUrlProvider() {
        return segmentUrlProvider;
    }

    protected HttpInterface getHttpInterface() {
        return sourceManager.getHttpInterface();
    }

    private String getVideoId() {
        return this.trackInfo.identifier;
    }

    public void process(LocalAudioTrackExecutor localExecutor) throws Exception {
        log.debug("Starting NicoNico track from URL: {}", get_video_url());

        super.process(localExecutor);
    }

    public String get_video_url() throws IOException {
        if(this.videoUrl != null) {
            return this.videoUrl;
        }

        this.sourceManager.checkLoggedIn();
        HttpInterface httpInterface = this.sourceManager.getHttpInterface();

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

            JSONObject json = new JSONObject(data);
            JSONObject video = json.getJSONObject("video");
            JSONObject dmc = video.getJSONObject("dmcInfo");
            JSONObject api = dmc.getJSONObject("session_api");

            JSONObject session = new JSONObject();

            JSONObject client_info = new JSONObject();
            client_info.put("player_id", api.getString("player_id"));
            session.put("client_info", client_info);

            JSONObject content_auth = new JSONObject();
            content_auth.put("auth_type", api.getJSONObject("auth_types").getString("http"));
            content_auth.put("content_key_timeout", api.getInt("content_key_timeout"));
            content_auth.put("service_id", "nicovideo");
            content_auth.put("service_user_id", api.getString("service_user_id"));
            session.put("content_auth", content_auth);

            session.put("content_id", api.getString("content_id"));

            JSONArray audio_src_ids = new JSONArray();
            audio_src_ids.put(api.getJSONArray("audios").get(0));

            JSONArray video_src_ids = new JSONArray();
            JSONArray videos = api.getJSONArray("videos");
            video_src_ids.put(videos.get(videos.length() -1));

            JSONObject src_id_to_mux_data = new JSONObject();
            src_id_to_mux_data.put("audio_src_ids", audio_src_ids);
            src_id_to_mux_data.put("video_src_ids", video_src_ids);

            JSONObject src_id_to_mux = new JSONObject();
            src_id_to_mux.put("src_id_to_mux", src_id_to_mux_data);

            JSONArray content_src_ids = new JSONArray();
            content_src_ids.put(src_id_to_mux);

            JSONObject content_src_ids_object = new JSONObject();
            content_src_ids_object.put("content_src_ids", content_src_ids);

            JSONArray content_src_id_sets = new JSONArray();
            content_src_id_sets.put(content_src_ids_object);
            session.put("content_src_id_sets", content_src_id_sets);

            session.put("content_type", "movie");
            session.put("content_uri", "");

            JSONObject heartbeat = new JSONObject();
            heartbeat_lifetime = api.getInt("heartbeat_lifetime");
            heartbeat.put("lifetime", heartbeat_lifetime);

            JSONObject keep_method = new JSONObject();
            keep_method.put("heartbeat", heartbeat);
            session.put("keep_method", keep_method);

            session.put("priority", api.getDouble("priority"));

            JSONObject hls_parameters = new JSONObject();
            JSONObject urls = api.getJSONArray("urls").getJSONObject(0);
            hls_parameters.put("segment_duration", 5000);
            hls_parameters.put("transfer_preset", "standard2");
            hls_parameters.put("use_ssl", urls.getBoolean("is_ssl") ? "yes" : "no");
            hls_parameters.put("use_well_known_port", urls.getBoolean("is_well_known_port") ? "yes" : "no");

            JSONObject http_parameters_parameters = new JSONObject();
            http_parameters_parameters.put("hls_parameters", hls_parameters);

            JSONObject http_parameters = new JSONObject();
            http_parameters.put("parameters", http_parameters_parameters);

            JSONObject parameters = new JSONObject();
            parameters.put("http_parameters", http_parameters);

            JSONObject protocol = new JSONObject();
            protocol.put("name", "http");
            protocol.put("parameters", parameters);
            session.put("protocol", protocol);

            session.put("recipe_id", api.getString("recipe_id"));

            JSONObject session_operation_auth_by_signature = new JSONObject();
            session_operation_auth_by_signature.put("signature", api.getString("signature"));
            session_operation_auth_by_signature.put("token", api.getString("token"));

            JSONObject session_operation_auth = new JSONObject();
            session_operation_auth.put("session_operation_auth_by_signature", session_operation_auth_by_signature);
            session.put("session_operation_auth", session_operation_auth);

            session.put("timing_constraint", "unlimited");

            JSONObject postData = new JSONObject();
            postData.put("session", session);

            HttpPost request2 = new HttpPost("https://api.dmc.nico/api/sessions?_format=json");
            request2.setHeader("Content-Type", "application/json");
            StringEntity entity = new StringEntity(postData.toString(), "UTF-8");
            request2.setEntity(entity);
            CloseableHttpResponse response2 = httpInterface.execute(request2);
            int statusCode2 = response2.getStatusLine().getStatusCode();
            if (statusCode2 < 200 || statusCode2 >= 300) {
                throw new IOException("Unexpected status code from playback parameters page: " + statusCode2);
            }

            String content = convertStreamToString(response2.getEntity().getContent());
            JSONObject res_dmc = new JSONObject(content);
            this.session = res_dmc.getJSONObject("data").getJSONObject("session");
            this.videoUrl = res_dmc.getJSONObject("data").getJSONObject("session").getString("content_uri");
        }
        return this.videoUrl;
    }

    public void heartbeat() throws IOException {
        if(System.currentTimeMillis() < this.tokenExpirationTime) {
            return;
        }
        String id = this.session.getString("id");
        String url = "http://api.dmc.nico/api/sessions/"+id+"?_format=json&_method=PUT";
        CloseableHttpResponse response;
        if(this.tokenExpirationTime == 0) {
            HttpOptions request = new HttpOptions(url);
            response = this.getHttpInterface().execute(request);
        } else {
            HttpPost request = new HttpPost(url);
            request.setHeader("Content-Type", "application/json");
            JSONObject data = new JSONObject();
            data.put("session", session);
            StringEntity entity = new StringEntity(data.toString(), "UTF-8");
            request.setEntity(entity);
            response = this.getHttpInterface().execute(request);
        }

        int statusCode2 = response.getStatusLine().getStatusCode();
        if (statusCode2 < 200 || statusCode2 >= 300) {
            throw new IOException("Unexpected status code from playback parameters page: " + statusCode2);
        }
        this.tokenExpirationTime = System.currentTimeMillis() + 30000;
    }

    public AudioTrack makeClone() {
        return new NicoAudioTrack(this.trackInfo, this.sourceManager);
    }

    public AudioSourceManager getSourceManager() {
        return this.sourceManager;
    }

    static String convertStreamToString(java.io.InputStream is) {
        java.util.Scanner s = new java.util.Scanner(is).useDelimiter("\\A");
        return s.hasNext() ? s.next() : "";
    }
}

