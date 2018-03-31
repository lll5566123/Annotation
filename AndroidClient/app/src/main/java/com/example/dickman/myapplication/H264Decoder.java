package com.example.dickman.myapplication;

import android.media.MediaCodec;
import android.media.MediaFormat;
import android.view.Surface;

import java.io.IOException;
import java.nio.ByteBuffer;
import java.util.LinkedList;
import java.util.List;

/**
 * Created by aeon on 2018/3/21.
 */

public class H264Decoder extends  Thread {
    MediaCodec mediaCodec;
    int bufferSize = 10;
    final List<byte[]> imageByteBuffer = new LinkedList<>();

    //從MainActivity呼叫，並顯示於SurfaceView上
    H264Decoder(Surface surface) throws IOException {
        final MediaFormat mediaFormat = MediaFormat.createVideoFormat("video/avc", 640, 480);//創立一個最小的視訊規模，MediaFormat.createVideoFormat(類型，寬，長)

        mediaCodec = MediaCodec.createDecoderByType("video/avc");//要實現的播放類型
        mediaCodec.configure(mediaFormat, surface, null, 0);//解碼配置，configure (輸入格式，指定的渲染介面，指定非安全編解碼，指定CRYPTO_MODE_UNENCRYPTED(代表數值為0))
        mediaCodec.start();//啟動解碼
        this.start();
    }

    @Override
    public void run() {
        MediaCodec.BufferInfo info = new MediaCodec.BufferInfo();//建立一個緩衝區數據，其中包含編解碼器、有效範圍的大小
        for (; ; ) {
            if (imageByteBuffer.size() == 0) {
                continue;//如果沒有圖片就重複讀，直到有圖片為止
            }
            byte[] data;
            synchronized (imageByteBuffer) {
                data = imageByteBuffer.remove(0);//每次都讀取一張圖，並將讀到的圖刪去
            }
            int i;
            do {
                i = mediaCodec.dequeueInputBuffer(1000);//等待可以使用緩衝區引索，1秒的等待時間，如果回傳-1，表示沒有可用的緩衝區
            } while (i == -1);//當沒有可用的緩衝區，則重複做，直到有緩衝區為止
            ByteBuffer inputBuffer = mediaCodec.getInputBuffer(i);//將前面找的引索做為inputBuffer的輸入引索
            inputBuffer.clear();//清除buffer
            inputBuffer.put(data, 0, data.length);//將取得的資料讀進這個budder裡面
            mediaCodec.queueInputBuffer(i, 0, data.length, 0, 0);//在指定索引處(i)填充輸入緩衝區的範圍(起點0到最後data的長度)後，將其提交給組件(0代表)

            int outIndex = mediaCodec.dequeueOutputBuffer(info, 1000);//輸出的緩衝區，用info填充這緩衝區，並每1秒一次
            switch (outIndex) {
                case MediaCodec.INFO_OUTPUT_FORMAT_CHANGED://獲取特定輸出緩衝區的格式
                case MediaCodec.INFO_TRY_AGAIN_LATER://表示呼叫超時
                case MediaCodec.INFO_OUTPUT_BUFFERS_CHANGED: // fuck this，此事件表示視頻縮放模式可能已重置為默認值，在API21已被棄用
                    break;

                default:
                    mediaCodec.releaseOutputBuffer(outIndex, info.presentationTimeUs);//已完成的緩衝區，將傳回解碼器並顯示出來，如果沒有給定顯示的表面，則只會傳回給解碼器

            }
        }
    }

    //解碼圖片，利用imageByteBuffer做為存的位置
    public void decodeByte(byte[] bytes) {
        synchronized (imageByteBuffer) {//synchronized不讓多重執行導致錯誤
            imageByteBuffer.add(bytes);//讀一張存一張
        }
    }
}
