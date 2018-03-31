package com.example.dickman.myapplication;

import android.util.Log;

import java.io.IOException;
import java.net.DatagramPacket;
import java.net.DatagramSocket;
import java.net.InetAddress;
import java.net.SocketException;
import java.util.ArrayList;
import java.util.LinkedList;
import java.util.List;

class SlidingWindow extends Thread{
    class Window {
        private static final int IDLE = 0;
        private static final int WAIT_FOR_RES = 1;
        private static final int END = 2;

        private byte stamp = 0;
        private byte recvStamp = 0;
        private final List<Integer> recvWindows = new LinkedList<>();
        private List<Integer> windows = new LinkedList<>();
        private List<Long> windowsTime = new LinkedList<>();
        private List<DatagramPacket> windowsPacket = new LinkedList<>();
        private long timeout;
        byte atkPacket[];
        Window(String header, byte windowSize, long timeout) {
            atkPacket = new byte[header.length() + 1];
            System.arraycopy(header.getBytes(), 0, atkPacket, 0, header.length());
            this.timeout = timeout;
            windows = new ArrayList<>();
            for (int i = 0; i < windowSize; ++i) {
                windows.add(IDLE);
                recvWindows.add(IDLE);
                windowsPacket.add(null);
                windowsTime.add(0L);
            }
        }

        private void init() {
            stamp = 0;
            recvStamp = 0;
            for (int i = 0; i < windows.size(); ++i) {
                windows.set(i, IDLE);
            }
        }

        boolean haveIdle() {
            int windowCount = -1;
            for (int i = 0; i < windows.size(); ++i) {
                if (windows.get(i) == IDLE) {
                    windowCount = (i + stamp) % 128;
                    break;
                }
            }
            return (windowCount != -1);
        }

        DatagramPacket pktData(byte[] data, int offset, int dataLength, InetAddress ip, int port) {
            synchronized (windows) {
                int windowCount = -1;
                int rawCount = -1;
                for (int i = 0; i < windows.size(); ++i) {
                    if (windows.get(i) == IDLE) {
                        windowCount = (i + stamp) % 128;
                        rawCount = i;
                        break;
                    }
                }
                if (windowCount == -1) {
                    return null;
                }

                if (data.length - dataLength - offset > 0) {
                    data[dataLength + offset] = (byte) windowCount;
                    dataLength += 1;
                } else {
                    byte[] dest = new byte[dataLength + 1];
                    System.arraycopy(data, offset, dest, 0, dataLength);
                    dest[dataLength] = (byte) windowCount;
                    dataLength += 1;
                    data = dest;
                    offset = 0;
                }
                DatagramPacket _dp = new DatagramPacket(data, offset, dataLength, ip, port);
                windowsPacket.set(rawCount, _dp);
                windowsTime.set(rawCount, System.currentTimeMillis());
                windows.set(rawCount, WAIT_FOR_RES);
                return _dp;
            }
        }

        DatagramPacket unpktData(DatagramSocket socket, DatagramPacket data) {
            synchronized (recvWindows) {
                atkPacket[atkPacket.length - 1] = data.getData()[data.getOffset() + data.getLength() - 1];
                DatagramPacket _dp = new DatagramPacket(atkPacket, atkPacket.length, data.getAddress(), data.getPort());
                try {
                    socket.send(_dp);
                } catch (IOException e) {
                    e.printStackTrace();
                }
                int index = confirmBorder(recvStamp, atkPacket[atkPacket.length - 1]);
                if (index == -1)
                    return null;
                if (recvWindows.get(index) == END) {
                    return null;
                }
                recvWindows.set(index, END);
                int counter = 0;
                for (int i = 0; i < recvWindows.size(); ++i) {
                    if (recvWindows.get(i) != END) {
                        break;
                    }
                    counter += 1;
                }
                for (int i = 0; i < counter; ++i) {
                    recvWindows.remove(0);
                    recvWindows.add(IDLE);
                }

                if (recvStamp + counter >= 128) {
                    recvStamp = (byte) ((recvStamp + counter) % 128);
                } else {
                    recvStamp += counter;
                }
                data.setLength(data.getLength() - 1);
                return data;
            }
        }

        void update(DatagramSocket socket) { // check atk timeout
            synchronized (windows) {
                for (int i = 0; i < windows.size(); ++i) {
                    if (windows.get(i) != WAIT_FOR_RES) {
                        continue;
                    }

                    if (System.currentTimeMillis() - windowsTime.get(i) > timeout) {
                        try {
                            socket.send(windowsPacket.get(i));
                            windowsTime.set(i, System.currentTimeMillis());
                            Log.d("WARNING", "time out, send atk: " + String.valueOf(windowsPacket.get(i).getData()[windowsPacket.get(i).getLength() - 1]));
                        } catch (IOException e) {
                            e.printStackTrace();
                        }
                    }
                }
            }
        }

        int confirmBorder(byte stamp, byte d) {
            int index;
            if (stamp + windows.size() > 127) {
                int min = (stamp + windows.size()) % 128;
                int top = stamp;
                if(min < d && d < top ) {
                    System.out.println("ERROR stamp: " + d);
                    return -1;
                }
                if (stamp <= d) {
                    index = d - stamp;
                } else {
                    index = d + (128 - stamp);
                }
            } else {
                int top = stamp + windows.size() - 1;
                int min = stamp - 1;
                if(d < min || top < d) {
                    System.out.println("ERROR stamp: " + d);
                    return -1;
                }
                index = d - stamp;
            }
            return index;
        }

        void recvATK(DatagramPacket data) {
            synchronized (windows) {
                byte d = data.getData()[data.getLength() - 1];
                if(d == -1) {
                    init();
                    System.out.println("window init");
                    return;
                }
                int index = confirmBorder(stamp, d);
                System.out.println("recv atk : "  + d);
                if(index == -1)
                    return;
                if(windows.get(index) != WAIT_FOR_RES)
                    return;
                windows.set(index, END);

                int counter = 0;
                for (int i = 0; i < windows.size(); ++i) {
                    if (windows.get(i) == END) {
                        counter++;
                    } else {
                        break;
                    }
                }

                for(int i = 0; i < counter; ++i) {
                    windows.remove(0);
                    windows.add(IDLE);
                    windowsTime.remove(0);
                    windowsTime.add(0L);
                    windowsPacket.remove(0);
                    windowsPacket.add(null);
                }

                if (stamp + counter > 127) {
                    stamp = (byte) ((stamp + counter) % 128);
                } else {
                    stamp += counter;
                }
            }
        }
    }
    private boolean isStop = false;
    private long timeout;
    private DatagramSocket socket;
    private Window window;
    InetAddress ip;
    int port;
    final private List<byte[]> buffer = new LinkedList<>();

    public SlidingWindow(String header, final DatagramSocket socket, final byte windowSize, final long timeout, InetAddress ip, int port) {
        this.timeout = timeout;
        this.socket = socket;
        this.ip = ip;
        this.port = port;
        try {
            this.socket.setSoTimeout(0);
        } catch (SocketException e) {
            e.printStackTrace();
        }
        window = new Window(header, windowSize, timeout);
        new Thread(new Runnable() {
            @Override
            public void run() {
                while(!isStop) {
                    try {
                        byte[] b = new byte[65505];
                        DatagramPacket pk = new DatagramPacket(b, b.length);
                        socket.receive(pk);

                        if(pk.getLength() == 1) {
                            window.recvATK(pk);
                            continue;
                        }

                        DatagramPacket _pk = window.unpktData(socket, pk);
                        if(_pk == null) {
                            continue;
                        }

                        byte[] data = new byte[_pk.getLength()];
                        System.arraycopy(_pk.getData(), 0, data, 0, _pk.getLength());
                        synchronized (buffer) {
                            buffer.add(data);
                        }
                    } catch (IOException e) {
                        e.printStackTrace();
                    }
                }
            }
        }).start();
        start();
    }

    public byte[] getData() {
        synchronized (buffer) {
            if(buffer.size() > 0) {
                return buffer.remove(0);
            }
            return null;
        }
    }

    public void sendData(byte[] data, int length) throws IOException {
        DatagramPacket pk = window.pktData(data, 0, length, ip, port);
        if(pk == null) {
            return;
        }
        socket.send(pk);
    }

    @Override
    public void run() {
        while(!isStop) {
            try {
                window.update(socket);
                Thread.sleep(timeout);
            } catch (InterruptedException e) {
                e.printStackTrace();
            }
        }
    }
}