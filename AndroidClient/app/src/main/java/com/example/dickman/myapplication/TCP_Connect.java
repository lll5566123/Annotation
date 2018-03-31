package com.example.dickman.myapplication;

import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.net.DatagramPacket;
import java.net.DatagramSocket;
import java.net.Socket;
import java.net.InetAddress;



public class TCP_Connect {
    private Socket clientSocket;//客戶端的socket
    private int serverUdpPort;
    private OutputStream bw;//取得網路輸出串流
    private InputStream br;//取得網路輸入串流
    private String pass = null;
    private InetAddress serverIp;
    private int serverPort;
    private String token = null;
    private DatagramSocket socket = null;

    public TCP_Connect(String serverHost, int serverPort, int serverUdpPort) throws IOException {
        serverIp = InetAddress.getByName(serverHost);
        this.serverPort = serverPort;
        this.serverUdpPort = serverUdpPort;
        clientSocket = new Socket(serverIp, serverPort);
        clientSocket.setSoTimeout(1000);
        bw = clientSocket.getOutputStream();// 取得網路輸出串流
        br = clientSocket.getInputStream();//取得網路輸入串流
        socket = new DatagramSocket();
    }

    public boolean inputPassword(String pass) {//確認密碼是否正確，從MainActivity傳進來
        try {
            bw.write(pass.getBytes());//將這資料傳遞出去給伺服器判斷
            byte[] buffer = new byte[256];//設定緩衝期
            int length = br.read(buffer);
            String data = new String(buffer, 0, length);
            if(data.length() < 16){//如果接到的資料小於原定大小，表示錯誤
                return false;
            }
            //否則就是密碼正確，將接收到的data寫入權杖內，並回傳true
            token = data;
            return true;
        } catch (IOException e) {
            return false;
        }
    }

    public String getToken() {
        return token;
    }

    public String getSocketIpPort(String key){//設定金鑰傳出，並獲得IP跟Port，主要做為雙方在連線上的交流
        try {
            if (socket == null)//如果沒收到就傳null回去，MainActivity接收到後會等待60ms在詢問第二次，以此類推
                return "";
            String str = token + " g " + key;//傳出去的格式 token (也就是剛剛上面得到的data)，g (表示得到get的意思)，key (從MainActivity進來的金鑰)

            //傳送給目標端，DatagramPacket(傳送的資料, 資料長度, serverIp, serverUdpPort)
            DatagramPacket pk = new DatagramPacket(str.getBytes(), str.getBytes().length, serverIp, serverUdpPort);
            socket.send(pk);//傳出
            socket.receive(pk);//接收
            return new String(pk.getData(), 0, pk.getLength());//回傳給MainActivity，String(資料源, 從哪理起頭, 哪裡結尾)
        }catch (IOException e) {
            return "0.0.0.0 0";
        }
    }

    public DatagramSocket getUdpSocket(String key) {//設定金鑰傳出，此時雙方已經完成連線的狀態，主要做為雙方在連線後創立UDP通道
        DatagramSocket socket = null;
        try {
            socket = new DatagramSocket();//創建一個DatagramSocket，並綁到本機上默認的接口
            socket.setSoTimeout(1000);
            String str = token + " s " + key;//傳出去的格式 token (也就是剛剛上面得到的data)，s (表示得到set的意思)，key (從MainActivity進來的金鑰)

            //傳送給目標端，DatagramPacket(傳送的資料, 資料長度, serverIp, serverUdpPort)
            DatagramPacket pk = new DatagramPacket(str.getBytes(), str.getBytes().length, serverIp, serverUdpPort);
            socket.send(pk);
            socket.receive(pk);
        } catch (IOException e) {
            return null;
        }
        //全部完成後回傳socket的接口，讓APP知道udp傳輸的位址
        return socket;
    }

    public String getSlidingIp_Port(String key) {//此為視訊所用的連線
        try {
            if (socket == null)
                return "";
            String str = token + " w " + key;//都跟前面一樣，只有 w (是指write)
            DatagramPacket pk = new DatagramPacket(str.getBytes(), str.getBytes().length, serverIp, serverUdpPort);
            socket.send(pk);
            socket.receive(pk);
            return new String(pk.getData(), 0, pk.getLength());//回傳給VideoThread，String(資料源, 從哪理起頭, 哪裡結尾)
        }catch (IOException e) {
            return "0.0.0.0 0";
        }
    }
}