package com.examples.mediacodecdemo;

import android.media.AudioFormat;
import android.media.AudioRecord;
import android.media.MediaCodec;
import android.media.MediaCodecInfo;
import android.media.MediaFormat;
import android.media.MediaRecorder;
import android.os.Bundle;
import android.os.Handler;
import android.os.Message;
import android.support.v7.app.AppCompatActivity;
import android.util.Log;
import android.view.View;
import android.widget.Button;
import android.widget.TextView;

import java.io.IOException;
import java.net.DatagramPacket;
import java.net.DatagramSocket;
import java.net.InetAddress;
import java.net.SocketException;
import java.net.UnknownHostException;
import java.nio.ByteBuffer;

public class AACRecorderAndSender extends AppCompatActivity {

    private String TAG = "AACRecorderAndSender";

    public static final int SAMPLE_RATE = 44100;  //44.1kHz
    public static final int BIT_RATE = 48000;
    public static final int CHANNEL_COUNT = 1;
    public static final short AUDIO_FORMAT = AudioFormat.ENCODING_PCM_16BIT;
    public static final short CHANNEL_CONFIG = AudioFormat.CHANNEL_IN_MONO;

    public static final int audioSource = MediaRecorder.AudioSource.MIC;
    public static int minBufferSize;


    private AudioRecord recorder;
    private MediaCodec encoder;


    private Button btnStart, btnStop;
    private TextView tvServerIp, tvServerPort;

    private TextView tvStatus;

    private Thread recorderThread;
    private boolean isRecording;
    private String forwardServer = "192.168.1.68";
    private int serverPort = 39000;


    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_aacrecorder_and_sender);

        tvServerIp = (TextView) findViewById(R.id.tvServerIp);
        tvServerPort = (TextView) findViewById(R.id.tvServerPort);

        btnStart = (Button) findViewById(R.id.btnStart);
        btnStop = (Button) findViewById(R.id.btnStop);
        tvStatus = (TextView) findViewById(R.id.tvStatus);

        tvServerIp.setText("Server ip:" + forwardServer);
        tvServerIp.setText("Server port:" + 39000);
        btnStart.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                recorderThread = new Thread(recorderTask);
                recorderThread.start();
            }
        });

        btnStop.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                isRecording = false;
            }
        });

    }


    private int findAudioRecord() {

        int bufferSize = 0;
        int audioFormat = AudioFormat.ENCODING_PCM_16BIT;
        int channelConfig = AudioFormat.CHANNEL_OUT_MONO;

        for (int rate : new int[]{44100}) {
            try {
                Log.v("==Attempting rate ", rate + "Hz, bits: " + audioFormat + ", channel: " + channelConfig);
                bufferSize = AudioRecord.getMinBufferSize(rate, channelConfig, audioFormat);

                if (bufferSize != AudioRecord.ERROR_BAD_VALUE) {
                    // check if we can instantiate and have a success
                    recorder = new AudioRecord(MediaRecorder.AudioSource.MIC, rate, channelConfig, audioFormat, bufferSize);

                    if (recorder.getState() == AudioRecord.STATE_INITIALIZED) {
                        Log.v("==final rate ", rate + "Hz, bits: " + audioFormat + ", channel: " + channelConfig);
                        return rate;
                    }
                }
            } catch (Exception e) {
                Log.v("error", "" + rate);
            }

        }
        return -1;
    }

    private boolean initRecorder() {

        minBufferSize = AudioRecord.getMinBufferSize(SAMPLE_RATE, CHANNEL_COUNT, AUDIO_FORMAT);
        if (minBufferSize != AudioRecord.ERROR_BAD_VALUE) {
            recorder = new AudioRecord(audioSource, SAMPLE_RATE, CHANNEL_CONFIG, AUDIO_FORMAT, minBufferSize);
        }

        if (recorder.getState() != AudioRecord.STATE_INITIALIZED) {
            Log.e(TAG, "init recorder failed!");
            Log.e(TAG, "audio recorder state = " + recorder.getState());
            return false;
        }

        return true;
    }


    private boolean initEncoder() {
        try {
            encoder = MediaCodec.createEncoderByType(MediaFormat.MIMETYPE_AUDIO_AAC);
            MediaFormat format = new MediaFormat();
            format.setString(MediaFormat.KEY_MIME, MediaFormat.MIMETYPE_AUDIO_AAC);
            format.setInteger(MediaFormat.KEY_CHANNEL_COUNT, CHANNEL_COUNT);
            format.setInteger(MediaFormat.KEY_SAMPLE_RATE, SAMPLE_RATE);
            format.setInteger(MediaFormat.KEY_BIT_RATE, BIT_RATE);
            format.setInteger(MediaFormat.KEY_AAC_PROFILE, MediaCodecInfo.CodecProfileLevel.AACObjectLC);
            encoder.configure(format, null, null, MediaCodec.CONFIGURE_FLAG_ENCODE);
            return true;
        } catch (IOException e) {
            Log.e(TAG, "init encoder failed.");
            e.printStackTrace();
        }

        return false;
    }


    private Runnable recorderTask = new Runnable() {
        @Override
        public void run() {

            int read;
            byte[] buffer1 = new byte[minBufferSize];

            ByteBuffer[] inputBuffers;
            ByteBuffer[] outputBuffers;

            ByteBuffer inputBuffer;
            ByteBuffer outputBuffer;

            MediaCodec.BufferInfo bufferInfo;
            int inputBufferIndex;
            int outputBufferIndex;

            byte[] outData;

            DatagramPacket packet;

            if (!initRecorder()) {
                return;
            }

            if (!initEncoder()) {
                return;
            }

            recorder.startRecording();
            encoder.start();

            isRecording = true;

            try {

                DatagramSocket socket = new DatagramSocket();

                while (!Thread.interrupted() && isRecording) {

                    read = recorder.read(buffer1, 0, minBufferSize);

                    inputBuffers = encoder.getInputBuffers();
                    outputBuffers = encoder.getOutputBuffers();
                    inputBufferIndex = encoder.dequeueInputBuffer(-1);

                    if (inputBufferIndex >= 0) {
                        inputBuffer = inputBuffers[inputBufferIndex];
                        inputBuffer.clear();

                        inputBuffer.put(buffer1);

                        encoder.queueInputBuffer(inputBufferIndex, 0, buffer1.length, 0, 0);
                    }

                    bufferInfo = new MediaCodec.BufferInfo();
                    outputBufferIndex = encoder.dequeueOutputBuffer(bufferInfo, 0);

                    while (outputBufferIndex >= 0) {
                        try {
                            outputBuffer = outputBuffers[outputBufferIndex];

                            outputBuffer.position(bufferInfo.offset);
                            outputBuffer.limit(bufferInfo.offset + bufferInfo.size);

                            outData = new byte[bufferInfo.size];
                            outputBuffer.get(outData);

                            Log.d("AudioEncoder", outData.length + " bytes encoded");
                            //-------------
                            packet = new DatagramPacket(outData, outData.length,
                                    InetAddress.getByName(forwardServer), serverPort);
                            socket.send(packet);
                            //------------
                            Message msg = handler.obtainMessage(0);
                            Bundle bundle = new Bundle();
                            bundle.putInt("count", packet.getLength());
                            msg.setData(bundle);
                            msg.sendToTarget();

                            encoder.releaseOutputBuffer(outputBufferIndex, false);
                            outputBufferIndex = encoder.dequeueOutputBuffer(bufferInfo, 0);
                        } catch (UnknownHostException e) {
                            e.printStackTrace();
                        } catch (IOException e) {
                            e.printStackTrace();
                        }
                    }
                    // ----------------------;
                }
            } catch (SocketException e) {
                e.printStackTrace();
            } catch (Exception e) {
                e.printStackTrace();
            } finally {
                if (recorder != null) {
                    recorder.stop();
                    recorder.release();
                    recorder = null;
                }

                if (encoder != null) {
                    encoder.stop();
                    encoder.release();
                    encoder = null;
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
