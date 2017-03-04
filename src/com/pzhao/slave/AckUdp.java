
package com.pzhao.slave;

import android.util.Log;

import java.io.IOException;
import java.net.DatagramPacket;
import java.net.DatagramSocket;
import java.net.InetAddress;

public class AckUdp implements Runnable {
    private static final String TAG = "AckUdp";
    private int port;
    private InetAddress remoteIp;
    private String ackMsg;

    public AckUdp(String msg, InetAddress ip, int port) {
        if (msg != null && msg.equals(UdpOrder.GET_WRITED))
            this.ackMsg = String.valueOf(SlavePlay.haswrite());
        else {
            this.ackMsg = msg;
        }
        this.remoteIp = ip;
        this.port = port;
        Log.i(TAG, "pzhao->Ack udp msg "+ackMsg);
    }

    @Override
    public void run() {
        // TODO Auto-generated method stub
        DatagramSocket socket = null;
        try {
            byte[] data = ackMsg.getBytes();
            socket = new DatagramSocket(0);
            socket.setReuseAddress(true);
            DatagramPacket request = new DatagramPacket(data, data.length, remoteIp, port);
            socket.send(request);
        } catch (IOException exception) {
            exception.printStackTrace();
        }

    }

}
