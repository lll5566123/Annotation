package com.example.dickman.myapplication;

import android.Manifest;
import android.content.Context;
import android.content.pm.ActivityInfo;
import android.content.pm.PackageManager;
import android.hardware.camera2.CameraAccessException;
import android.hardware.camera2.CameraCharacteristics;
import android.hardware.camera2.CameraDevice;
import android.hardware.camera2.CameraManager;
import android.os.Bundle;
import android.os.Handler;
import android.os.Message;
import android.support.annotation.NonNull;
import android.support.v4.app.ActivityCompat;
import android.support.v7.app.AppCompatActivity;
import android.view.SurfaceView;
import android.view.View;
import android.widget.EditText;
import android.widget.Toast;

import java.io.IOException;
import java.io.Serializable;
import java.lang.ref.WeakReference;
import java.net.DatagramSocket;

public class MainActivity extends AppCompatActivity {
    final String PhoneKey = "Phone";
    final String RaspberryKey = "Raspberry";

    final String PhoneVideoKey = "VideoPhone";
    final String RaspberryVideoKey = "VideoRaspberry";

    private int serverPort = 7777;
    private int serverUdpPort = 8888;
    private int timeout = 0;
    private String serverHost = "140.128.88.166";
    private EditText passEdit = null;
    private SurfaceView surfaceView;
    final Object audioLock = new Object();
    Audio audio = null;
    VideoThread video = null;
    CameraDevice cameraDevice = null;
    TCP_Connect tcp_connect;

    //將socket跟token等資料藉由Java 序列化從物件轉變成資料流，達到簡易傳遞資料的效果
    private class PacketClass implements Serializable {
        public DatagramSocket socket;
        public String cientHost, token;
        public int SentPort;
    }

    //利用Handler將傳給主線程的資料儲存
    static class MyHandler extends Handler {
        static final int ON_AUDIO_START = 0;
        static final int ON_VIDEO_START = 1;
        static final int ON_IMAGE_AVAILABLE = 2;
        private WeakReference<MainActivity> mOuter;

        MyHandler(MainActivity activity) {
            mOuter = new WeakReference<>(activity);
        }

        /*將主線程需要的資料利用handleMessage作為傳輸管道
                    因為Thread不能刷新主線程，必須得用HandMessage做為傳輸的管道*/
        @Override
        public void handleMessage(Message msg) {
            MainActivity outer = mOuter.get();
            if (outer != null) {
                PacketClass packetClass;
                switch (msg.arg1) {
                    case ON_AUDIO_START:
                        packetClass = (PacketClass) msg.getData().getSerializable("audio");
                        if (packetClass == null)
                            break;
                        //啟動後呼叫音訊的傳輸功能，Audio(音訊資料, 手機端IP位址, 手機端傳輸port,outer.timeout, packetClass.token);
                        outer.audio = new Audio(packetClass.socket, packetClass.cientHost, packetClass.SentPort,
                                outer.timeout, packetClass.token);
                        break;
                    case ON_VIDEO_START:
                        //啟動後呼叫視訊的傳輸功能，VideoThread(tcp_connect的連接設定，cameraDevice啟動相機功能，surfaceView.getHolder().getSurface()將接收到的資料畫面渲染到surfaceView上)
                        outer.video = new VideoThread(outer.tcp_connect, outer.cameraDevice, outer.surfaceView.getHolder().getSurface(), 640, 480);
                        break;
                    case ON_IMAGE_AVAILABLE:
                        break;
                }
            }
        }
    }

    private MyHandler mHandler = new MyHandler(this);

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_main);
        setRequestedOrientation(ActivityInfo.SCREEN_ORIENTATION_LANDSCAPE);
        passEdit = findViewById(R.id.editText);
        surfaceView = findViewById(R.id.image);
        if (ActivityCompat.checkSelfPermission(MainActivity.this, Manifest.permission.CAMERA) != PackageManager.PERMISSION_GRANTED ||
                ActivityCompat.checkSelfPermission(MainActivity.this, Manifest.permission.RECORD_AUDIO) != PackageManager.PERMISSION_GRANTED) {
            requestPermissions(new String[]{Manifest.permission.CAMERA, Manifest.permission.RECORD_AUDIO}, 0);
        }
    }

    @Override
    //檢查並確認權限
    public void onRequestPermissionsResult(int requestCode, @NonNull String[] permissions, @NonNull int[] grantResults) {
        super.onRequestPermissionsResult(requestCode, permissions, grantResults);
        for (int res : grantResults) {
            if (res != PackageManager.PERMISSION_GRANTED) {
                requestPermissions(permissions, 0);
                return;
            }
        }
    }

    //關閉通話
    public void clickcall_end(View view) {
        synchronized (audioLock) {
            if (audio != null) {
                audio.close();
                audio = null;
            }
        }
    }

    //設定TCP等資料，PacketClass(TCP連結，連結從哪，連結到哪)
    public PacketClass getSetting(TCP_Connect tcp_connect, String from, String to) {
        PacketClass packetClass = new PacketClass();//建構PacketClass
        if (tcp_connect == null || tcp_connect.getToken() == null) {//當連結或權杖都為null時回傳null，表示沒有任何對象或是找不到對象(?)[我不太清楚這裡的意思]
            return null;
        }
        packetClass.socket = tcp_connect.getUdpSocket(from);
        while (packetClass.socket == null) {//得到UDP的socket才繼續做，否則一直等
            packetClass.socket = tcp_connect.getUdpSocket(from);
        }
        String tmp[] = tcp_connect.getSocketIpPort(to).split(" ");//將得到的IP跟PORT切割，因為進來的資料長OO.OO.OO.OO XXX，中間空白切掉，並放入原先準備好的陣列內
        packetClass.cientHost = tmp[0];//切過以後第一格為IP
        packetClass.SentPort = Integer.valueOf(tmp[1]);//第二格為port
        while (packetClass.cientHost.equals("0.0.0.0") || packetClass.SentPort == 0) {//確認Host跟port是否存在，若沒有拿到就繼續做，直到拿到為止
            tmp = tcp_connect.getSocketIpPort(to).split(" ");
            packetClass.cientHost = tmp[0];
            packetClass.SentPort = Integer.valueOf(tmp[1]);
            try {
                Thread.sleep(60);//等待時間，也許會有時間差，不一定是沒有對象
            } catch (InterruptedException e) {
                e.printStackTrace();
            }
        }
        packetClass.token = tcp_connect.getToken();
        return packetClass;
    }

    public void clickcall_start(View view) {
        final String password = passEdit.getText().toString();
        new Thread(new Runnable() {
            @Override
            public void run() {
                synchronized (audioLock) {//啟動通訊，並利用synchronized限制只會啟動一個，避免出現錯誤
                    if (audio == null) {
                        try {
                            tcp_connect = new TCP_Connect(serverHost, serverPort, serverUdpPort);//設定連線並實做出的TCP，TCP_Connect(伺服器端IP，伺服器端port，伺服器端UDP IP)
                            if (tcp_connect.inputPassword(password)) {//拚斷密碼正確性
                                PacketClass packetClass = getSetting(tcp_connect, PhoneKey, RaspberryKey);//設定tcp跟手機和TX2的金鑰
                                Message msg = new Message();//將資料傳給headle
                                Bundle bundle = new Bundle();
                                bundle.putSerializable("audio", packetClass);
                                msg.arg1 = MyHandler.ON_AUDIO_START;//啟動通話
                                msg.setData(bundle);
                                mHandler.sendMessage(msg);

                                PacketClass packetClass1 = getSetting(tcp_connect, PhoneVideoKey, RaspberryVideoKey);
                                CameraManager manager = ((CameraManager) getSystemService(Context.CAMERA_SERVICE));//開啟相機管理
                                try {
                                    for (String cameraId : manager.getCameraIdList()) {//前置鏡頭還是後製鏡頭(這裡採用前鏡頭)
                                        CameraCharacteristics characteristics = manager.getCameraCharacteristics(cameraId);
                                        if (characteristics.get(CameraCharacteristics.LENS_FACING) == CameraCharacteristics.LENS_FACING_FRONT) {
                                            if (ActivityCompat.checkSelfPermission(MainActivity.this, Manifest.permission.CAMERA) != PackageManager.PERMISSION_GRANTED) {
                                                requestPermissions(new String[] { Manifest.permission.CAMERA }, 0);
                                                return;
                                            }
                                            manager.openCamera(cameraId, new CameraDevice.StateCallback() {//開啟相機並實做
                                                @Override
                                                public void onOpened(@NonNull CameraDevice camera) {//開啟相機，並將視訊藉由Handler開啟，因為從Thread開啟會有跟主線程衝突的問題
                                                    cameraDevice = camera;
                                                    Message msg = new Message();
                                                    msg.arg1 = MyHandler.ON_VIDEO_START;
                                                    mHandler.sendMessage(msg);

                                                }

                                                @Override
                                                public void onDisconnected(@NonNull CameraDevice camera) {

                                                }

                                                @Override
                                                public void onError(@NonNull CameraDevice camera, int error) {

                                                }
                                            }, mHandler);
                                    }
                                }
                            } catch (CameraAccessException e) {
                                e.printStackTrace();
                            }
                        } else {
                            Toast.makeText(MainActivity.this, "password error or network is unavailable", Toast.LENGTH_LONG);
                        }
                    } catch (IOException e) {
                        e.printStackTrace();
                    }
                }
            }
            }
        }).start();
    }

}




