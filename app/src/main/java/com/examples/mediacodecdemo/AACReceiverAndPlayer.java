package com.examples.mediacodecdemo;

import android.media.AudioFormat;
import android.media.AudioManager;
import android.media.AudioTrack;
import android.media.MediaCodec;
import android.media.MediaCodecInfo;
import android.media.MediaFormat;
import android.media.MediaRecorder;
import android.os.Handler;
import android.os.Message;
import android.support.v7.app.AppCompatActivity;
import android.os.Bundle;
import android.util.Log;
import android.view.View;
import android.widget.Button;
import android.widget.TextView;


import java.io.IOException;
import java.net.DatagramPacket;
import java.net.DatagramSocket;
import java.net.SocketAddress;
import java.nio.ByteBuffer;

public class AACReceiverAndPlayer extends AppCompatActivity {

    private String TAG = "AACReceiverAndPlayer";

    public static final int SAMPLE_RATE = 44100;  //44.1kHz
    public static final int BIT_RATE = 48000;
    public static final int CHANNEL_COUNT = 1;
    public static final short AUDIO_FORMAT = AudioFormat.ENCODING_PCM_16BIT;
    public static final short CHANNEL_CONFIG = AudioFormat.CHANNEL_IN_MONO;

    public static final int audioSource = MediaRecorder.AudioSource.MIC;

    private Button btnStart, btnStop;
    private TextView tvServerIp;
    private TextView tvStatus;


    public static int minBufferSize;

    private MediaCodec decoder;
    private AudioTrack player;

    private Thread playThread;
    private String forwardServer = "192.168.1.68";
    private int localPort = 39000;

    private boolean isPlaying = false;



    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_aacreceiver_and_player);

        btnStart = (Button) findViewById(R.id.btnStart);
        btnStop = (Button) findViewById(R.id.btnStop);
        tvStatus = (TextView) findViewById(R.id.tvStatus);
        tvServerIp = (TextView) findViewById(R.id.tvServerIp);

        tvServerIp.setText("Server ip:" + forwardServer);
        tvServerIp.setText("Server port:" + 39000);
        btnStart.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View v) {

                if(!initDecoder()) {
                    Log.e(TAG, "====decoder inited failed.====");
                    return;
                }

                if (!initPlayer()) {
                    Log.e(TAG, "====player inited failed.====");
                    return;
                }

                playThread = new Thread(playTask);
                playThread.start();
            }
        });

        btnStop.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                isPlaying = false;
                playThread.interrupt();
            }
        });

    }


    private boolean initDecoder() {

        try {
            decoder = MediaCodec.createDecoderByType(MediaFormat.MIMETYPE_AUDIO_AAC);
            MediaFormat format = new MediaFormat();
            format.setString(MediaFormat.KEY_MIME, MediaFormat.MIMETYPE_AUDIO_AAC);
            format.setInteger(MediaFormat.KEY_CHANNEL_COUNT, CHANNEL_COUNT);
            format.setInteger(MediaFormat.KEY_SAMPLE_RATE, SAMPLE_RATE);
            format.setInteger(MediaFormat.KEY_BIT_RATE, BIT_RATE);
            format.setInteger(MediaFormat.KEY_AAC_PROFILE, MediaCodecInfo.CodecProfileLevel.AACObjectLC);

            byte[] bytes = new byte[]{(byte) 0x12, (byte) 0x12};
            ByteBuffer bb = ByteBuffer.wrap(bytes);
            format.setByteBuffer("csd-0", bb);

            decoder.configure(format, null, null, 0);

            return true;

        } catch (IOException e) {
            e.printStackTrace();
        }

        return false;
    }

    private boolean initPlayer() {

        int bufferSizePlayer = AudioTrack.getMinBufferSize(SAMPLE_RATE, AudioFormat.CHANNEL_OUT_MONO, AUDIO_FORMAT);
        Log.d("====buffer Size player ", String.valueOf(bufferSizePlayer));

        player = new AudioTrack(AudioManager.STREAM_MUSIC, SAMPLE_RATE, AudioFormat.CHANNEL_OUT_MONO, AUDIO_FORMAT, bufferSizePlayer, AudioTrack.MODE_STREAM);


        if (player.getState() == AudioTrack.STATE_INITIALIZED) {

            return true;
        } else {
            return false;
        }

    }


    private Runnable playTask = new Runnable() {
        @Override
        public void run() {

            SocketAddress sockAddress;
            String address;
            DatagramSocket socket = null;

            int len = 1024;
            byte[] buffer2 = new byte[len];
            DatagramPacket packet;

            byte[] data;

            ByteBuffer[] inputBuffers;
            ByteBuffer[] outputBuffers;

            ByteBuffer inputBuffer;
            ByteBuffer outputBuffer;

            MediaCodec.BufferInfo bufferInfo;
            int inputBufferIndex;
            int outputBufferIndex;
            byte[] outData;
            try {

                socket = new DatagramSocket(localPort);
                player.play();
                decoder.start();
                Thread.sleep(2000);
                isPlaying = true;
                while (isPlaying) {
                    try {
                        packet = new DatagramPacket(buffer2, len);
                        socket.receive(packet);

                        sockAddress = packet.getSocketAddress();
                        address = sockAddress.toString();

                        Log.d("UDP Receiver", " received !!! from " + address);
                        Log.d("UDP Receiver", " received !!! len " + packet.getLength());

                        data = new byte[packet.getLength()];
                        System.arraycopy(packet.getData(), packet.getOffset(), data, 0, packet.getLength());

                        // Log.d("UDP Receiver",  data.length + " bytes received");

                        //===========
                        inputBuffers = decoder.getInputBuffers();
                        outputBuffers = decoder.getOutputBuffers();
                        inputBufferIndex = decoder.dequeueInputBuffer(-1);
                        if (inputBufferIndex >= 0) {
                            inputBuffer = inputBuffers[inputBufferIndex];
                            inputBuffer.clear();

                            inputBuffer.put(data);

                            decoder.queueInputBuffer(inputBufferIndex, 0, data.length, System.nanoTime() / 1000, 0);
                        }

                        bufferInfo = new MediaCodec.BufferInfo();
                        outputBufferIndex = decoder.dequeueOutputBuffer(bufferInfo, 10000);
                        System.out.println("==========outputBufferIndex=" + outputBufferIndex);
                        while (outputBufferIndex >= 0) {
                            outputBuffer = outputBuffers[outputBufferIndex];

                            outputBuffer.position(bufferInfo.offset);
                            outputBuffer.limit(bufferInfo.offset + bufferInfo.size);

                            outData = new byte[bufferInfo.size];
                            outputBuffer.get(outData);

                            //  Log.d("AudioDecoder", outData.length + " bytes decoded");

                            player.write(outData, 0, outData.length);

                            decoder.releaseOutputBuffer(outputBufferIndex, false);
                            outputBufferIndex = decoder.dequeueOutputBuffer(bufferInfo, 10000);

                        }

                        //===========

                    } catch (IOException e) {
                        e.printStackTrace();
                    }
                }
            } catch (Exception e) {
                e.printStackTrace();
            } finally {

                if (player != null) {
                    player.stop();
                    player.release();
                    player = null;
                }

                if (decoder == null) {
                    decoder.stop();
                    decoder.release();
                    decoder = null;
                }

                if (socket != null) {
                    socket.close();
                }

            }
        }
    };


    private Handler handler = new Handler() {
        @Override
        public void handleMessage(Message msg) {

            if (msg.what == 0) {
                int number = (int) msg.getData().get("count");
                tvStatus.setText("send " + number + " bytes.");
            }
        }
    };
}
