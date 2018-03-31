package com.example.dickman.myapplication;

import android.util.Log;

import java.io.IOException; //例外功能
import java.net.DatagramPacket;
import java.net.DatagramSocket;
import java.net.InetAddress;
import java.net.UnknownHostException;

public class UDP_Request {
    private String Host;
    private int SentPort;
    private DatagramSocket socket;
    private DatagramPacket rceivePacket;

    //Audio聲音的UDP管道 UDP_Request （連線的socket從MainActivity獲得金鑰，伺服器IP，伺服器Port，timeout，緩衝大小）
    public UDP_Request(DatagramSocket socket, String Host, int SentPort, int timeout, int bufferSize) throws IOException {
        byte receiveBuffer[] = new byte[bufferSize];//開一個接收的buffer
        this.Host = Host;
        this.SentPort = SentPort;
        this.socket = socket;
        if (timeout != 0) //如果時間不是0，那就把等待時間傳給socket當作他的等待時間
            socket.setSoTimeout(timeout);
        rceivePacket = new DatagramPacket(receiveBuffer, bufferSize); //https://developer.android.com/reference/java/net/DatagramPacket.html，將接收端的接口建立出來
    }

    public void send (final String input) throws UnknownHostException {//沒有人用他，這段是不是要刪掉?

        byte data[] = input.getBytes();
        final DatagramPacket packet = new DatagramPacket(data, data.length, InetAddress.getByName(Host), this.SentPort);
        try {
            socket.send(packet);
        } catch (IOException e) {
            e.printStackTrace();
        }
    }

    public void sendBytes(final byte data[]) throws UnknownHostException {//傳送音訊的通道，在Audio.class呼叫

        /*將音訊的檔案利用這條通道傳出，使用UDP是為了速度上的效率，而且就算音訊上有封包不小心遺失，人耳也聽不出來
                    DatagramPacket(音訊資料, 資料的長度, 伺服器IP位址, );*/
        final DatagramPacket packet = new DatagramPacket(data, data.length, InetAddress.getByName(Host), this.SentPort);
        try {
            socket.send(packet);//將包好的資料傳送出去
        } catch (IOException e) {
            e.printStackTrace();
        }
    }

    public void error(IOException e) {
        Log.d("error", e.toString());
        // TODO
    }

    public byte[] receive() throws IOException {//接收資料
        socket.receive(rceivePacket);
        return rceivePacket.getData();//接收到資料後回傳
    }

    public DatagramPacket receivePkt() throws IOException {//這沒用到
        socket.receive(rceivePacket);
        return rceivePacket;
    }

    public byte[] receive_() throws IOException {//這也沒用到
        socket.receive(rceivePacket);
        byte b[] = new byte[rceivePacket.getLength()];
        System.arraycopy(rceivePacket.getData(), rceivePacket.getOffset(), b, 0, rceivePacket.getLength());
        return b;
    }
}

