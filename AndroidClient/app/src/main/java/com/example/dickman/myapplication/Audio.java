package com.example.dickman.myapplication;

import android.media.AudioFormat;
import android.media.AudioManager;
import android.media.AudioRecord;
import android.media.AudioTrack;
import android.media.MediaRecorder;
import android.util.Log;
import java.io.IOException;
import java.net.DatagramSocket;

class ThreadKeepRunning extends Thread{
    private boolean stopped = false;
    public void close() {
        stopped = true;
    }
}

public class Audio extends Thread
{
    private final String RaspberryKey = "Raspberry";

    private int client_frame_size = 20480; // dont cnahge it，客戶端的偵數大小
    private ThreadKeepRunning recieveAudio;
    private boolean stopped = false;
    private String token;
    private int rate = 44100;
    private UDP_Request udp_connect ;
    private AudioRecord recorder = null;
    private AudioTrack track = null;

    /**
     * Give the thread high priority so that it's not canceled unexpectedly, and start it
     */
    public Audio(DatagramSocket socket, String Host, int SentPort, int timeoutAudio, String token)
    {
        try {
            udp_connect = new UDP_Request (socket, Host, SentPort, timeoutAudio, client_frame_size);//建構UDP_Request連線（連線的socket從MainActivity獲得金鑰，伺服器IP，伺服器Port，timeout，緩衝大小）
        } catch (IOException e) {
            e.printStackTrace();
        }
        this.token = token;
        android.os.Process.setThreadPriority(android.os.Process.THREAD_PRIORITY_URGENT_AUDIO);//設定音頻線程的標準優先級

        recieveAudio = new ThreadKeepRunning() {
            @Override
            public void run() {
                super.run();
                while(!stopped){//當起動沒有停止時
                    try {
                        if(track != null && track.getState() == AudioTrack.STATE_INITIALIZED) {//沒有將AudioTrack狀態啟用而且track 不存在，都不將執行
                            byte[] recieve_data = udp_connect.receive();//將接到到的資料放入陣列中
                            if(recieve_data != null)//如果陣列內沒資料則不做
                                track.write(recieve_data, 0, recieve_data.length);//將音訊寫入 track.write(我接收到的資料, 起點0, 結尾位置recieve_data.length)
                        }
                    } catch (IOException e) {
                        e.printStackTrace();
                    }
                }
            }
        };
        start();
        recieveAudio.start();//起動接收的音訊
    }

    @Override
    public void run()
    {
        try
        {
            //創建對象所需的最小緩衝區大小 getMinBufferSize (採樣率[44100Hz是目前唯一保證可在所有設備上工作的速率]，音頻通道配置，音頻數據格式)
            int N = AudioRecord.getMinBufferSize(rate,AudioFormat.CHANNEL_IN_MONO,AudioFormat.ENCODING_PCM_16BIT);

            //建構AudioRecord (錄音源，採樣率[44100Hz是目前唯一保證可在所有設備上工作的速率]，音頻通道配置，音頻數據格式，記錄期間寫入音頻數據的緩衝區的總大小)
            recorder = new AudioRecord(MediaRecorder.AudioSource.MIC, rate, AudioFormat.CHANNEL_IN_MONO, AudioFormat.ENCODING_PCM_16BIT, N * 10);

            //建構AudioTrack (音流類型STREAM_MUSIC[用於識別音樂播放音頻流的音量]，採樣率[44100Hz是目前唯一保證可在所有設備上工作的速率]，音頻通道配置，音頻數據格式，記錄期間寫入音頻數據的緩衝區的總大小，流媒體或靜態緩衝區MODE_STREAM[音頻數據從Java流式傳輸到本地的創建模式])
            track = new AudioTrack(AudioManager.STREAM_MUSIC, rate, AudioFormat.CHANNEL_OUT_MONO, AudioFormat.ENCODING_PCM_16BIT, N * 10, AudioTrack.MODE_STREAM);

            recorder.startRecording();//從AudioRecord實例開始錄製
            track.play();//開始播放音軌

            byte[] token = this.token.getBytes();
            byte[] RaspberryKey = this.RaspberryKey.getBytes();
            int offset = token.length + RaspberryKey.length + 2;//設定起始位置token跟key之後兩格，因為有兩個空白
            final byte[] send_buffer = new byte[client_frame_size + offset];//創建一個傳送用緩衝區
            System.arraycopy(token, 0, send_buffer, 0, token.length);//複製陣列，先複製token
            send_buffer[token.length] = ' ';//加上空白
            System.arraycopy(RaspberryKey, 0, send_buffer, token.length + 1, RaspberryKey.length);//接著複製key
            send_buffer[offset - 1] = ' ';//最後一個空白區分資料源
            while(!stopped)  //Sent data frame: token <key> <data>，當通話沒有停時
            {
                recorder.read(send_buffer, offset, send_buffer.length - offset);//將音訊寫入key之後的位置
                udp_connect.sendBytes(send_buffer);//利用udp傳送出去

            }
            recieveAudio.close();
        }
        catch(Throwable x)
        {
            Log.w("Audio", "Error reading voice audio", x);
        }
        /*
         * Frees the thread's resources after the loop completes so that it can be run again
         */
        finally//一定會做到
        {
            if(recorder != null) {//如果沒有傳送，關閉並且釋放資源
                recorder.stop();
                recorder.release();
            }
            if(track != null) {//如果沒有接收，關閉並且釋放資源
                track.stop();
                track.release();
            }
        }
    }

    /**
     * Called from outside of the thread in order to stop the recording/playback loop
     */
    public void close()//關閉音訊
    {
        recieveAudio.close();
        stopped = true;
    }

}