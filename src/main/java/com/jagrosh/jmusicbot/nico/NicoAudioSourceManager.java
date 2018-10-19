package com.jagrosh.jmusicbot.nico;

//
// Source code recreated from a .class file by IntelliJ IDEA
// (powered by Fernflower decompiler)
//

import com.sedmelluq.discord.lavaplayer.player.DefaultAudioPlayerManager;
import com.sedmelluq.discord.lavaplayer.source.AudioSourceManager;
import com.sedmelluq.discord.lavaplayer.tools.DataFormatTools;
import com.sedmelluq.discord.lavaplayer.tools.FriendlyException;
import com.sedmelluq.discord.lavaplayer.tools.FriendlyException.Severity;
import com.sedmelluq.discord.lavaplayer.tools.io.HttpClientTools;
import com.sedmelluq.discord.lavaplayer.tools.io.HttpConfigurable;
import com.sedmelluq.discord.lavaplayer.tools.io.HttpInterface;
import com.sedmelluq.discord.lavaplayer.tools.io.HttpInterfaceManager;
import com.sedmelluq.discord.lavaplayer.track.AudioItem;
import com.sedmelluq.discord.lavaplayer.track.AudioReference;
import com.sedmelluq.discord.lavaplayer.track.AudioTrack;
import com.sedmelluq.discord.lavaplayer.track.AudioTrackInfo;
import java.io.DataInput;
import java.io.DataOutput;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.util.Arrays;
import java.util.Iterator;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.function.Consumer;
import java.util.function.Function;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import org.apache.http.Header;
import org.apache.http.client.config.RequestConfig;
import org.apache.http.client.entity.UrlEncodedFormEntity;
import org.apache.http.client.methods.CloseableHttpResponse;
import org.apache.http.client.methods.HttpGet;
import org.apache.http.client.methods.HttpPost;
import org.apache.http.impl.client.HttpClientBuilder;
import org.apache.http.message.BasicNameValuePair;
import org.jsoup.Jsoup;
import org.jsoup.nodes.Document;
import org.jsoup.nodes.Element;
import org.jsoup.parser.Parser;

public class NicoAudioSourceManager implements AudioSourceManager, HttpConfigurable {
    private static final String TRACK_URL_REGEX = "^(?:http://|https://|)(?:www\\.|)nicovideo\\.jp/watch/(sm[0-9]+)(?:\\?.*|)$";
    private static final Pattern trackUrlPattern = Pattern.compile("^(?:http://|https://|)(?:www\\.|)nicovideo\\.jp/watch/(sm[0-9]+)(?:\\?.*|)$");
    private final String email;
    private final String password;
    private final HttpInterfaceManager httpInterfaceManager;
    private final AtomicBoolean loggedIn;

    public NicoAudioSourceManager(String email, String password) {
        this.email = email;
        this.password = password;
        this.httpInterfaceManager = HttpClientTools.createDefaultThreadLocalManager();
        this.loggedIn = new AtomicBoolean();
    }

    public String getSourceName() {
        return "niconico";
    }

    public AudioItem loadItem(DefaultAudioPlayerManager manager, AudioReference reference) {
        Matcher trackMatcher = trackUrlPattern.matcher(reference.identifier);
        return trackMatcher.matches() ? this.loadTrack(trackMatcher.group(1)) : null;
    }

    private AudioTrack loadTrack(String videoId) {
        this.checkLoggedIn();

        try {
            HttpInterface httpInterface = this.getHttpInterface();
            Throwable var3 = null;

            AudioTrack var8;
            try {
                CloseableHttpResponse response = httpInterface.execute(new HttpGet("http://ext.nicovideo.jp/api/getthumbinfo/" + videoId));
                Throwable var5 = null;

                try {
                    int statusCode = response.getStatusLine().getStatusCode();
                    if (statusCode != 200) {
                        throw new IOException("Unexpected response code from video info: " + statusCode);
                    }

                    Document document = Jsoup.parse(response.getEntity().getContent(), StandardCharsets.UTF_8.name(), "", Parser.xmlParser());
                    var8 = this.extractTrackFromXml(videoId, document);
                } catch (Throwable var33) {
                    var5 = var33;
                    throw var33;
                } finally {
                    if (response != null) {
                        if (var5 != null) {
                            try {
                                response.close();
                            } catch (Throwable var32) {
                                var5.addSuppressed(var32);
                            }
                        } else {
                            response.close();
                        }
                    }

                }
            } catch (Throwable var35) {
                var3 = var35;
                throw var35;
            } finally {
                if (httpInterface != null) {
                    if (var3 != null) {
                        try {
                            httpInterface.close();
                        } catch (Throwable var31) {
                            var3.addSuppressed(var31);
                        }
                    } else {
                        httpInterface.close();
                    }
                }

            }

            return var8;
        } catch (IOException var37) {
            throw new FriendlyException("Error occurred when extracting video info.", Severity.SUSPICIOUS, var37);
        }
    }

    private AudioTrack extractTrackFromXml(String videoId, Document document) {
        Iterator var3 = document.select(":root > thumb").iterator();
        if (var3.hasNext()) {
            Element element = (Element)var3.next();
            String uploader = element.select("user_nickname").first().text();
            String title = element.select("title").first().text();
            long duration = DataFormatTools.durationTextToMillis(element.select("length").first().text());
            if(element.select("movie_type").first().text().equals("flv") || element.select("no_live_play").first().text().equals("1")) {
                return new NicoAudioTrackOld(new AudioTrackInfo(title, uploader, duration, videoId, false, getWatchUrl(videoId)), this);
            } else {
                return new NicoAudioTrack(new AudioTrackInfo(title, uploader, duration, videoId, false, getWatchUrl(videoId)), this);
            }
        } else {
            return null;
        }
    }

    public boolean isTrackEncodable(AudioTrack track) {
        return true;
    }

    public void encodeTrack(AudioTrack track, DataOutput output) throws IOException {
    }

    public AudioTrack decodeTrack(AudioTrackInfo trackInfo, DataInput input) throws IOException {
        return new NicoAudioTrack(trackInfo, this);
    }

    public void shutdown() {
    }

    public HttpInterface getHttpInterface() {
        return this.httpInterfaceManager.getInterface();
    }

    public void configureRequests(Function<RequestConfig, RequestConfig> configurator) {
        this.httpInterfaceManager.configureRequests(configurator);
    }

    public void configureBuilder(Consumer<HttpClientBuilder> configurator) {
        this.httpInterfaceManager.configureBuilder(configurator);
    }

    void checkLoggedIn() {
        AtomicBoolean var1 = this.loggedIn;
        synchronized(this.loggedIn) {
            if (!this.loggedIn.get()) {
                HttpPost loginRequest = new HttpPost("https://secure.nicovideo.jp/secure/login");
                loginRequest.setEntity(new UrlEncodedFormEntity(Arrays.asList(new BasicNameValuePair("mail", this.email), new BasicNameValuePair("password", this.password)), StandardCharsets.UTF_8));

                try {
                    HttpInterface httpInterface = this.getHttpInterface();
                    Throwable var4 = null;

                    try {
                        CloseableHttpResponse response = httpInterface.execute(loginRequest);
                        Throwable var6 = null;

                        try {
                            int statusCode = response.getStatusLine().getStatusCode();
                            if (statusCode != 302) {
                                throw new IOException("Unexpected response code " + statusCode);
                            }

                            Header location = response.getFirstHeader("Location");
                            if (location == null || location.getValue().contains("message=")) {
                                throw new FriendlyException("Login details for NicoNico are invalid.", Severity.COMMON, (Throwable)null);
                            }

                            this.loggedIn.set(true);
                        } catch (Throwable var35) {
                            var6 = var35;
                            throw var35;
                        } finally {
                            if (response != null) {
                                if (var6 != null) {
                                    try {
                                        response.close();
                                    } catch (Throwable var34) {
                                        var6.addSuppressed(var34);
                                    }
                                } else {
                                    response.close();
                                }
                            }

                        }
                    } catch (Throwable var37) {
                        var4 = var37;
                        throw var37;
                    } finally {
                        if (httpInterface != null) {
                            if (var4 != null) {
                                try {
                                    httpInterface.close();
                                } catch (Throwable var33) {
                                    var4.addSuppressed(var33);
                                }
                            } else {
                                httpInterface.close();
                            }
                        }

                    }
                } catch (IOException var39) {
                    throw new FriendlyException("Exception when trying to log into NicoNico", Severity.SUSPICIOUS, var39);
                }

            }
        }
    }

    private static String getWatchUrl(String videoId) {
        return "http://www.nicovideo.jp/watch/" + videoId;
    }
}

