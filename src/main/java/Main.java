import javax.sound.sampled.*;

import com.google.api.client.auth.oauth2.Credential;
import com.google.api.client.extensions.java6.auth.oauth2.AuthorizationCodeInstalledApp;
import com.google.api.client.extensions.jetty.auth.oauth2.LocalServerReceiver;
import com.google.api.client.googleapis.auth.oauth2.GoogleAuthorizationCodeFlow;
import com.google.api.client.googleapis.auth.oauth2.GoogleClientSecrets;
import com.google.api.client.googleapis.javanet.GoogleNetHttpTransport;
import com.google.api.client.googleapis.json.GoogleJsonResponseException;
import com.google.api.client.http.javanet.NetHttpTransport;
import com.google.api.client.json.JsonFactory;
import com.google.api.client.json.jackson2.JacksonFactory;

import com.google.api.services.youtube.YouTube;
import com.google.api.services.youtube.model.SearchListResponse;

import com.google.api.services.youtube.model.SearchResult;
import com.sedmelluq.discord.lavaplayer.player.AudioLoadResultHandler;
import com.sedmelluq.discord.lavaplayer.player.AudioPlayer;
import com.sedmelluq.discord.lavaplayer.player.AudioPlayerManager;
import com.sedmelluq.discord.lavaplayer.player.DefaultAudioPlayerManager;
import com.sedmelluq.discord.lavaplayer.player.event.AudioEventAdapter;
import com.sedmelluq.discord.lavaplayer.source.AudioSourceManagers;
import com.sedmelluq.discord.lavaplayer.source.http.HttpAudioSourceManager;
import com.sedmelluq.discord.lavaplayer.tools.FriendlyException;
import com.sedmelluq.discord.lavaplayer.track.AudioPlaylist;
import com.sedmelluq.discord.lavaplayer.track.AudioTrack;
import com.sedmelluq.discord.lavaplayer.track.AudioTrackEndReason;
import com.sedmelluq.discord.lavaplayer.track.playback.AudioFrame;
import net.dv8tion.jda.core.AccountType;
import net.dv8tion.jda.core.JDABuilder;
import net.dv8tion.jda.core.audio.AudioSendHandler;
import net.dv8tion.jda.core.audio.hooks.ConnectionStatus;
import net.dv8tion.jda.core.EmbedBuilder;
import java.awt.Color;
import net.dv8tion.jda.core.entities.Channel;
import net.dv8tion.jda.core.entities.Message;
import net.dv8tion.jda.core.entities.MessageHistory;
import net.dv8tion.jda.core.entities.TextChannel;
import net.dv8tion.jda.core.events.message.MessageReceivedEvent;
import net.dv8tion.jda.core.events.message.guild.GuildMessageReceivedEvent;
import net.dv8tion.jda.core.hooks.ListenerAdapter;
import net.dv8tion.jda.core.managers.AudioManager;
import net.dv8tion.jda.core.requests.RestAction;
import netscape.javascript.JSObject;
import org.json.JSONObject;

import javax.security.auth.login.LoginException;
import java.io.IOException;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.net.MalformedURLException;
import java.net.URL;
import java.security.GeneralSecurityException;
import java.util.Arrays;
import java.util.Collection;
import java.util.concurrent.BlockingQueue;
import java.util.concurrent.LinkedBlockingQueue;
import java.util.concurrent.TimeUnit;
import java.util.function.Consumer;
import java.util.stream.Collectors;

public class Main extends ListenerAdapter {

    private static final String CLIENT_SECRETS= "client_secret.json";
    private static final Collection<String> SCOPES =
            Arrays.asList("https://www.googleapis.com/auth/youtube.readonly");
    private static final String APPLICATION_NAME = "DiscordBot";
    private static final JsonFactory JSON_FACTORY = JacksonFactory.getDefaultInstance();

    public static Credential authorize(final NetHttpTransport httpTransport) throws IOException {
        InputStream in = Main.class.getResourceAsStream(CLIENT_SECRETS);
        GoogleClientSecrets clientSecrets =
                GoogleClientSecrets.load(JSON_FACTORY, new InputStreamReader(in));
        GoogleAuthorizationCodeFlow flow =
                new GoogleAuthorizationCodeFlow.Builder(httpTransport, JSON_FACTORY, clientSecrets, SCOPES)
                        .build();
        Credential credential =
                new AuthorizationCodeInstalledApp(flow, new LocalServerReceiver()).authorize("user");
        return credential;
    }

    public static YouTube getService() throws GeneralSecurityException, IOException {
        final NetHttpTransport httpTransport = GoogleNetHttpTransport.newTrustedTransport();
        Credential credential = authorize(httpTransport);
        return new YouTube.Builder(httpTransport, JSON_FACTORY, credential)
                .setApplicationName(APPLICATION_NAME)
                .build();
    }

    static AudioPlayerManager playerManager = new DefaultAudioPlayerManager();
    static AudioPlayer player = playerManager.createPlayer();
    static YouTube youtubeService;
    TrackScheduler scheduler = new TrackScheduler(player);
    TextChannel currentChannel;
    boolean linked = false;
    boolean searchWait = false;
    public String authorID = "";
    SearchListResponse response;
    String useChannelID;

    public static void main(String[] args) throws GeneralSecurityException, IOException {
        youtubeService = getService();
        AudioSourceManagers.registerRemoteSources(playerManager);
        JDABuilder builder = new JDABuilder(AccountType.BOT);
        String token = "NTgzODE2NDE0NzY3MzQ5NzYw.XPB3fQ.qROR4hJLbbPjJQaWm5Bnp1CNDa8";
        builder.setToken(token);
        builder.addEventListener(new Main());
        builder.buildAsync();
        System.out.println("Started discord bot :3");
    }

    private void linkPlayer(){
        player.addListener(scheduler);
    }
    private void leaveChannel(){
        currentChannel.getGuild().getAudioManager().closeAudioConnection();
    }
    @Override
    public void onGuildMessageReceived(GuildMessageReceivedEvent event) {
        if(!linked){
            linkPlayer();
            linked = true;
        }
        if(!event.getChannel().getId().equals(useChannelID) && useChannelID != null){
            return;
        }
        AudioManager audioManager = event.getGuild().getAudioManager();
        if(searchWait && authorID.equals(event.getAuthor().getId())){
            System.out.println("Searching song rn");
            String tempId = new JSONObject(response.getItems().get(Integer.parseInt(event.getMessage().getContentStripped())-1).getId()).getString("videoId");
            playerManager.loadItem("https://www.youtube.com/watch?v=" + tempId, new AudioLoadResultHandler() {
                @Override
                public void trackLoaded(AudioTrack track) {
                    EmbedBuilder eb = new EmbedBuilder();
                    eb.setColor(Color.GREEN);
                    eb.setDescription(String.format("[%s](%s)", track.getInfo().title, track.getInfo().uri));
                    eb.setAuthor("Added to Queue", null, event.getGuild().getMembersWithRoles(event.getGuild().getRolesByName("bacon bot", true)).get(0).getUser().getDefaultAvatarUrl());
                    event.getChannel().sendMessage(eb.build()).queue();
                    if(audioManager.getConnectionStatus() == ConnectionStatus.NOT_CONNECTED) {
                        audioManager.openAudioConnection(event.getMember().getVoiceState().getChannel());
                        audioManager.setSendingHandler(new AudioPlayerSendHandler(player));
                    }
                    scheduler.queue(track);
                }

                @Override
                public void playlistLoaded(AudioPlaylist playlist){
                }

                @Override
                public void noMatches() {
                }

                @Override
                public void loadFailed(FriendlyException throwable) {
                }
            });
            searchWait = false;
        }
        if(event.getAuthor().isBot()) {
            return;
        }
        String[] ARGS = event.getMessage().getContentRaw().split(" ");
        System.out.println("We received a message from " +
                event.getAuthor().getName() + ":" +
                event.getMessage().getContentRaw()
        );
        try {
            switch (ARGS[0]) {
                case "!ping":
                    currentChannel = event.getChannel();
                    System.out.println("Got a ping in " + event.getChannel().getName());
                    event.getChannel().sendMessage(event.getMember().getAsMention() + ", Pong").queue();
                    break;
                case "!play":
                    currentChannel = event.getChannel();
                    if (event.getMember().getVoiceState().getChannel() == null) {
                        event.getChannel().sendMessage("You are not connected to a channel").queue();
                        return;
                    }
                    System.out.println("Got a request to play " + ARGS[1] + " in " + event.getMember().getVoiceState().getChannel().getName());
                    if (audioManager.isAttemptingToConnect()) {
                        event.getChannel().sendMessage("Attempting to connect already, chill out").queue();
                        return;
                    }
                    playerManager.loadItem(ARGS[1], new AudioLoadResultHandler() {
                                @Override
                                public void trackLoaded(AudioTrack track) {
                                    EmbedBuilder eb = new EmbedBuilder();
                                    eb.setColor(Color.GREEN);
                                    eb.setDescription(String.format("[%s](%s)", track.getInfo().title, track.getInfo().uri));
                                    eb.setAuthor("Added to Queue", null, currentChannel.getGuild().getMembersWithRoles(currentChannel.getGuild().getRolesByName("bacon bot", true)).get(0).getUser().getDefaultAvatarUrl());
                                    currentChannel.sendMessage(eb.build()).queue();
                                    if(audioManager.getConnectionStatus() == ConnectionStatus.NOT_CONNECTED) {
                                        audioManager.openAudioConnection(event.getMember().getVoiceState().getChannel());
                                        audioManager.setSendingHandler(new AudioPlayerSendHandler(player));
                                    }
                                    scheduler.queue(track);
                                }

                                @Override
                                public void playlistLoaded(AudioPlaylist playlist){
                                    EmbedBuilder eb = new EmbedBuilder();
                                    eb.setDescription("Loaded " + playlist.getTracks().size() + " tracks");
                                    eb.setColor(Color.GREEN);
                                    eb.setTitle(playlist.getName());
                                    eb.setAuthor("Queued Playlist", null, currentChannel.getGuild().getMembersWithRoles(currentChannel.getGuild().getRolesByName("bacon bot", true)).get(0).getUser().getDefaultAvatarUrl());
                                    currentChannel.sendMessage(eb.build()).queue();
                                    if(audioManager.getConnectionStatus() == ConnectionStatus.NOT_CONNECTED) {
                                        audioManager.openAudioConnection(event.getMember().getVoiceState().getChannel());
                                        audioManager.setSendingHandler(new AudioPlayerSendHandler(player));
                                    }
                                    for (AudioTrack track : playlist.getTracks()){
                                        scheduler.queue(track);
                                    }
                                }

                                @Override
                                public void noMatches() {
                                    System.out.println("Nothing found");
                                    event.getChannel().sendMessage("Nothing found for: " + ARGS[1]).queue();
                                }

                                @Override
                                public void loadFailed(FriendlyException throwable) {
                                    System.out.println("Failed to load");
                                    event.getChannel().sendMessage("Failed to load: " + ARGS[1]).queue();                                }
                            });
                    break;
                case "!leave":
                    currentChannel = event.getChannel();
                    if (audioManager.getConnectionStatus() == ConnectionStatus.NOT_CONNECTED) {
                        event.getChannel().sendMessage("Currently not connected to a voice channel").queue();
                        return;
                    }
                    audioManager.closeAudioConnection();
                    scheduler.queue.clear();
                    player.stopTrack();
                    event.getChannel().sendMessage("Disconnected from channel").queue();
                    break;
                case "!skip":
                    currentChannel = event.getChannel();
                    if (audioManager.getConnectionStatus() == ConnectionStatus.NOT_CONNECTED) {
                        event.getChannel().sendMessage("Currently not connected to a voice channel").queue();
                        return;
                    }
                    scheduler.nextTrack();
                    break;
                case "!clear":
                    currentChannel = event.getChannel();
                    if(audioManager.getConnectionStatus() == ConnectionStatus.NOT_CONNECTED){
                        event.getChannel().sendMessage("Currently not connected to a voice channel").queue();
                        return;
                    }
                    event.getChannel().sendMessage("Cleared queue of " + scheduler.queue.size() + " Songs").queue();
                    scheduler.queue.clear();
                    break;
                case "!queue":
                    currentChannel = event.getChannel();
                    if(audioManager.getConnectionStatus() == ConnectionStatus.NOT_CONNECTED){
                        event.getChannel().sendMessage("Currently not connected to a voice channel").queue();
                        return;
                    }
                    int index = 1;
                    EmbedBuilder eb = new EmbedBuilder();
                    eb.setTitle("Queued Songs", null);
                    eb.setColor(Color.BLUE);
                    eb.setThumbnail("http://img.youtube.com/vi/" + player.getPlayingTrack().getInfo().identifier + "/0.jpg");
                    eb.addField("Now Playing",String.format("[%s](%s) | `%02d:%02d`", player.getPlayingTrack().getInfo().title, player.getPlayingTrack().getInfo().uri, TimeUnit.MILLISECONDS.toMinutes(player.getPlayingTrack().getDuration()), TimeUnit.MILLISECONDS.toSeconds(player.getPlayingTrack().getDuration()) - TimeUnit.MINUTES.toSeconds(TimeUnit.MILLISECONDS.toMinutes(player.getPlayingTrack().getDuration()))), false);
                    String tempField = "";
                    for(AudioTrack track : scheduler.queue){
                        tempField += String.format("`%d.` [%s](%s) | `%02d:%02d`", index, track.getInfo().title, track.getInfo().uri, TimeUnit.MILLISECONDS.toMinutes(track.getDuration()), TimeUnit.MILLISECONDS.toSeconds(track.getDuration()) - TimeUnit.MINUTES.toSeconds(TimeUnit.MILLISECONDS.toMinutes(track.getDuration()))) + "\n";
                        index++;
                    }
                    eb.addField("Queued", tempField, false);
                    event.getChannel().sendMessage(eb.build()).queue();
                    break;
                case "!search":
                    YouTube.Search.List request = youtubeService.search()
                            .list("snippet");
                    response = request.setMaxResults(10L)
                            .setOrder("relevance")
                            .setQ(String.join("+", Arrays.copyOfRange(ARGS, 1, ARGS.length)))
                            .setType("video")
                            .execute();
                    eb = new EmbedBuilder();
                    eb.setTitle("Search Results", null);
                    eb.setColor(Color.DARK_GRAY);
                    index = 1;
                    eb.addField("Results", "Type the index number of the song you would like to select", false);
                    for(SearchResult result : response.getItems()){
                        eb.addField(" \u200F\u200F\u200E ", String.format("`%d.` [%s](%s)", index, result.getSnippet().getTitle(), "https://www.youtube.com/watch?v=" + new JSONObject(result.getId()).getString("videoId")) + "\n", false);
                        index++;
                    }
                    authorID = event.getAuthor().getId();
                    searchWait = true;
                    event.getChannel().sendMessage(eb.build()).queue();
                    break;
                case "!setchannel":
                    useChannelID = event.getChannel().getId();
                    event.getGuild().getTextChannelById(useChannelID).sendMessage("Now using this channel for bot commands").queue();
            }
        } catch (Exception e) {
            System.out.println(e.getMessage());
        }
    }
    public class AudioPlayerSendHandler implements AudioSendHandler {
        private final AudioPlayer audioPlayer;
        private AudioFrame lastFrame;

        public AudioPlayerSendHandler(AudioPlayer audioPlayer) {
            this.audioPlayer = audioPlayer;
        }

        @Override
        public boolean canProvide() {
            lastFrame = audioPlayer.provide();
            return lastFrame != null;
        }

        @Override
        public byte[] provide20MsAudio() {
            return lastFrame.getData();
        }

        @Override
        public boolean isOpus() {
            return true;
        }
    }

    public class TrackScheduler extends AudioEventAdapter {
        private final AudioPlayer player;
        private final BlockingQueue<AudioTrack> queue;

        public TrackScheduler(AudioPlayer player) {
            this.player = player;
            this.queue = new LinkedBlockingQueue<>();
        }

        @Override
        public void onTrackStart(AudioPlayer player, AudioTrack track) {
            EmbedBuilder eb = new EmbedBuilder();
            eb.setTitle(track.getInfo().author, null);
            eb.setColor(Color.RED);
            eb.setDescription(String.format("[%s](%s)", track.getInfo().title, track.getInfo().uri));
            eb.setAuthor("Now Playing", null, currentChannel.getGuild().getMembersWithRoles(currentChannel.getGuild().getRolesByName("bacon bot", true)).get(0).getUser().getDefaultAvatarUrl());
            eb.setThumbnail("http://img.youtube.com/vi/" + track.getInfo().identifier + "/0.jpg");
            currentChannel.sendMessage(eb.build()).queue();
        }

        @Override
        public void onTrackEnd(AudioPlayer player, AudioTrack track, AudioTrackEndReason endReason) {
            EmbedBuilder eb = new EmbedBuilder();
            eb.setTitle(track.getInfo().author, null);
            eb.setColor(Color.YELLOW);
            eb.setDescription(String.format("[%s](%s)", track.getInfo().title, track.getInfo().uri));
            eb.setAuthor("Finished Playing", null, currentChannel.getGuild().getMembersWithRoles(currentChannel.getGuild().getRolesByName("bacon bot", true)).get(0).getUser().getDefaultAvatarUrl());
            eb.setThumbnail("http://img.youtube.com/vi/" + track.getInfo().identifier + "/0.jpg");
            currentChannel.sendMessage(eb.build()).queue();
            if (endReason.mayStartNext) {
                nextTrack();
            }
        }
        public void queue(AudioTrack track) {
            if (!player.startTrack(track, true)) {
                queue.offer(track);
            }
        }
        public void nextTrack() {
            if(queue.peek() == null) {
                currentChannel.sendMessage("Left voice channel").queue();
                new Thread(() -> {
                    leaveChannel();
                }).start();
            }
            player.startTrack(queue.poll(), false);
        }
    }
}

