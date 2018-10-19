package com.jagrosh.jmusicbot.nico;

import com.sedmelluq.discord.lavaplayer.source.stream.ExtendedM3uParser;
import com.sedmelluq.discord.lavaplayer.source.stream.M3uStreamSegmentUrlProvider;
import com.sedmelluq.discord.lavaplayer.tools.JsonBrowser;
import com.sedmelluq.discord.lavaplayer.tools.io.HttpClientTools;
import com.sedmelluq.discord.lavaplayer.tools.io.HttpInterface;
import org.apache.http.client.methods.HttpGet;
import org.apache.http.client.methods.HttpUriRequest;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.IOException;
import java.util.List;

import static com.sedmelluq.discord.lavaplayer.tools.io.HttpClientTools.fetchResponseLines;

/**
 * Provider for Beam segment URLs from a channel.
 */
public class NicoSegmentUrlProvider extends M3uStreamSegmentUrlProvider {
    private static final Logger log = LoggerFactory.getLogger(NicoSegmentUrlProvider.class);

    private String masterUrl = null;
    private NicoAudioTrack track;
    private String streamSegmentPlaylistUrl;

    public NicoSegmentUrlProvider(NicoAudioTrack track) {
        this.track = track;
    }

    @Override
    protected String getQualityFromM3uDirective(ExtendedM3uParser.Line directiveLine) {
        return directiveLine.directiveArguments.get("RESOLUTION");
    }

    @Override
    protected String fetchSegmentPlaylistUrl(HttpInterface httpInterface) throws IOException {
        this.track.heartbeat();

        if (streamSegmentPlaylistUrl != null) {
            return streamSegmentPlaylistUrl;
        }
        if(this.masterUrl == null) {
            this.masterUrl = this.track.get_video_url();
        }

        HttpUriRequest manifestRequest = new HttpGet(this.masterUrl);
        List<ChannelStreamInfo> streams = loadChannelStreamsList(fetchResponseLines(httpInterface, manifestRequest,
                "nicovideo streams list"));

        if (streams.isEmpty()) {
            throw new IllegalStateException("No streams available on channel.");
        }

        ChannelStreamInfo stream = streams.get(0);

        streamSegmentPlaylistUrl = this.masterUrl.split("master.m3u8")[0] + stream.url;
        log.debug("Chose stream with quality {} from url {}", stream.quality, streamSegmentPlaylistUrl);
        return streamSegmentPlaylistUrl;
    }

    @Override
    protected HttpUriRequest createSegmentGetRequest(String url) {
        return new HttpGet(url);
    }
}