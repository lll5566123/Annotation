package com.example.dickman.myapplication;

import android.hardware.camera2.CameraAccessException;
import android.hardware.camera2.CameraCaptureSession;
import android.hardware.camera2.CameraDevice;
import android.hardware.camera2.CaptureRequest;
import android.media.MediaCodec;
import android.media.MediaCodecInfo;
import android.media.MediaFormat;
import android.support.annotation.NonNull;
import android.view.Surface;

import java.io.IOException;
import java.nio.ByteBuffer;
import java.util.Collections;
import java.util.LinkedList;
import java.util.List;

/**
 * Created by aeon on 2018/3/21.
 */

public class H264Encoder extends MediaCodec.Callback{
    private MediaCodec mediaCodec;
    private Surface surface;
    private CameraDevice cameraDevice;
    private int offset;
    private final List<byte[]> byteBuffers = new LinkedList<>();

    class MyCameraCaptureSessionCallBack extends CameraCaptureSession.StateCallback{

        @Override
        public void onConfigured(@NonNull CameraCaptureSession session) {

            try {
                CaptureRequest.Builder request = cameraDevice.createCaptureRequest(CameraDevice.TEMPLATE_PREVIEW);//建立一個新的要求，並將TEMPLATE_PREVIEW初始化，TEMPLATE_PREVIEW為適合相機預覽窗口的請求
                request.set(CaptureRequest.JPEG_ORIENTATION, 270);//設定圖像的方向，轉270度，讓圖片翻轉成方便直視
                request.addTarget(surface);//請求的輸出目標

                CaptureRequest captureRequest = request.build();//從相機設備捕捉單個圖像所需的一整套設置和輸出建立出來
                session.setRepeatingRequest(captureRequest, null, null);//不斷重複的捕捉圖像，達到預覽跟連續影像等目的
            } catch (CameraAccessException e) {
                e.printStackTrace();
            }
        }

        @Override
        public void onConfigureFailed(@NonNull CameraCaptureSession session) {

        }
    }

    MyCameraCaptureSessionCallBack cameraCallback = new MyCameraCaptureSessionCallBack();//建構預覽相機的設置

    //從MainActivity呼叫編碼，透過相機、畫面大小、起始位置
    public H264Encoder(final CameraDevice cameraDevice, int width, int height, int offset) throws IOException {
        this.cameraDevice = cameraDevice;
        MediaFormat mediaFormat = MediaFormat.createVideoFormat("video/avc", width, height);//編碼的類型跟大小
        this.offset = offset;

        //設定mediaFormat相關資訊
        mediaFormat.setInteger(MediaFormat.KEY_COLOR_FORMAT,//以影像格式描述內容的顏色格式
                MediaCodecInfo.CodecCapabilities.COLOR_FormatSurface);//
        mediaFormat.setInteger(MediaFormat.KEY_BIT_RATE, 400000);//平均bits/sec的密鑰
        mediaFormat.setInteger(MediaFormat.KEY_FRAME_RATE, 15);//平均 frames/sec的密鑰
        mediaFormat.setInteger(MediaFormat.KEY_I_FRAME_INTERVAL, 5);//關鍵FRAME/秒的密鑰

        mediaCodec = MediaCodec.createEncoderByType("video/avc");//要實現的編碼類型
        mediaCodec.setCallback(this);//指定這個mediaCodec做異步處理
        mediaCodec.configure(mediaFormat, null, null, MediaCodec.CONFIGURE_FLAG_ENCODE);//解碼配置，configure (輸入格式，指定的渲染介面，指定非安全編解碼，指定CONFIGURE_FLAG_ENCODE為編碼器
        surface = mediaCodec.createInputSurface();//請求Surface用作編碼器的輸入，以代替輸入緩衝區。
        mediaCodec.start();//啟動編碼


        try {
            cameraDevice.createCaptureSession(Collections.singletonList(surface), cameraCallback, null);//通過向相機設備提供Surfaces的目標輸出 createCaptureSession(outputs輸出surface，接收有關相機狀態更新的回調對象，handler)
        } catch (CameraAccessException e) {
            e.printStackTrace();
        }
    }

    @Override
    public void onInputBufferAvailable(@NonNull MediaCodec codec, int index) {

    }

    //輸出緩衝區變為可用時呼叫 onOutputBufferAvailable(MediaCodec對象，可用輸出緩衝區的索引，關於可用輸出緩衝區的信息)
    @Override
    public void onOutputBufferAvailable(@NonNull MediaCodec codec, int index, @NonNull MediaCodec.BufferInfo info) {
        ByteBuffer byteBuffer =mediaCodec.getOutputBuffer(index);//讀取輸出緩衝區的索引位置
        if(byteBuffer == null) {//如果沒有資料的畫
            mediaCodec.releaseOutputBuffer(index, false);//將緩衝區返回給編解碼器或將其呈現在輸出表面上，false則是不將畫面輸出
            return;
        }
        byte[] bytes = new byte[byteBuffer.remaining() + offset + 1];//bytes = [實際讀取的數據長度+MainActivity傳來的起始值+1空白]
        byteBuffer.get(bytes, offset, byteBuffer.remaining());//從bytes讀取，bytes長度，從MainActivity傳來的起始值開始讀起
        synchronized (byteBuffers) {
            byteBuffers.add(bytes);//每次只加入一張，利用synchronized避免重複呼叫導致錯誤
        }
        mediaCodec.releaseOutputBuffer(index, false);
    }

    @Override
    public void onError(@NonNull MediaCodec codec, @NonNull MediaCodec.CodecException e) {

    }

    @Override
    public void onOutputFormatChanged(@NonNull MediaCodec codec, @NonNull MediaFormat format) {

    }

    //被VideoThread呼叫
    public byte[] getEncodeedImage() {
        synchronized (byteBuffers) {
            if (byteBuffers.size() != 0)
                return byteBuffers.remove(0);//只要有圖片被拿走，就把buffer內的第一個圖刪去
            return null;//如果沒有圖片就傳null回去
        }
    }
}
