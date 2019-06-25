import net.dv8tion.jda.core.audio.AudioSendHandler;

import javax.sound.sampled.AudioInputStream;

public class AudioPlayerSendHandler implements AudioSendHandler {
    private final AudioInputStream audioPlayer;
    private final byte[] lastFrame = {};

    public AudioPlayerSendHandler(AudioInputStream audioPlayer) {
        this.audioPlayer = audioPlayer;
    }

    @Override
    public boolean canProvide() {
        try {
            int count = audioPlayer.read(lastFrame);
            return count < 0;
        } catch (Exception e){
            System.out.println(e.getMessage());
            return false;
        }
    }

    @Override
    public byte[] provide20MsAudio() {
        return lastFrame;
    }

    @Override
    public boolean isOpus() {
        return false;
    }
}
