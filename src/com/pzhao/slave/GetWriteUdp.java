
package com.pzhao.slave;

import android.os.Message;
import android.util.Log;

import java.io.IOException;
import java.net.DatagramPacket;
import java.net.DatagramSocket;
import java.net.InetAddress;
import java.net.SocketException;
import java.net.SocketTimeoutException;

public class GetWriteUdp implements Runnable {
    private final static int PORT = 43709;// remote port
    private final static String TAG = "GetWriteUdp";
    private InetAddress remoteIp;
    private SlavePlay mSlavePlay;
    private int getWriteCount;
    private boolean runflag;
    private boolean stopFlag;
    private long startGetHost;
    private long endGetHost;
    private int startSlaveWrite;
    private int endSlaveWrite;
    private int hostHasWrite;
    private byte[] data;
    private byte[] revdata;
    private DatagramPacket request;
    private DatagramPacket responce;
    private DatagramSocket socket;

    public GetWriteUdp(InetAddress ip, SlavePlay play) {
        this.remoteIp = ip;
        this.mSlavePlay = play;
        this.getWriteCount = 0;
        this.runflag = true;
        this.stopFlag=true;
        this.data = UdpOrder.GET_WRITED.getBytes();
        this.revdata = new byte[10];
        this.request = new DatagramPacket(data, data.length, remoteIp, PORT);
        this.responce = new DatagramPacket(revdata, revdata.length);
    }

    public synchronized void setCount(int count) {
        boolean notify = (getWriteCount < 0);
        getWriteCount = count;
        if (notify)
            notify();
    }

    private synchronized void getCount() throws InterruptedException {
        if (--getWriteCount < 0) {
       //     Log.d(TAG, "pzhao->wait in get count");
            wait(10000);
        }
    }
    public synchronized void start(){
        stopFlag=false;
        notify();
    }
    public synchronized void stop(){
        stopFlag=true;
    }
    
    private synchronized void checkStop() throws InterruptedException{
        while(stopFlag)
            wait();
    }
    
    public synchronized void quit() {
        runflag = false;
        stopFlag=false;
        if (socket != null)
            socket.close();
        getWriteCount = 2;
        notify();
    }

    @Override
    public void run() {
        // TODO Auto-generated method stub
        try {
            socket = new DatagramSocket(0);
            socket.setReuseAddress(true);
            socket.setSoTimeout(1000);
            while (runflag) {
                try{ 
                    getCount();
                    checkStop();
                    if (!runflag)
                        return;
                    startGetHost = System.currentTimeMillis();
                    startSlaveWrite = SlavePlay.haswrite();
                    socket.send(request);
                    socket.receive(responce);
                    String result = new String(responce.getData(), 0, responce.getLength());
                    hostHasWrite = Integer.valueOf(result);
                    endSlaveWrite = SlavePlay.haswrite();
                    endGetHost = System.currentTimeMillis();
                    int dif, readahead;
                    dif = hostHasWrite - ((endSlaveWrite + startSlaveWrite) >> 1);
//                    Log.d(TAG, "pzhao->udp cost:" + (endGetSlave - startGetSlave)
//                            / 1000000 + "ms, slave ahead host " + dif);
                    if (endGetHost - startGetHost > 15) {
                        Log.i(TAG, "pzhao->Udp cost too much time, pass!");
                        getWriteCount++;
                        continue;
                    }   
                    readahead = (dif + SlavePlay.slave_host) >> 1;
                    //readahead = (SlavePlay.slave_host - dif) >> 1;
                    if (readahead > 5 || readahead < -5) {
                        getWriteCount++;
                        readahead=readahead>0?5:-5;
                    }
                    if (dif > SlavePlay.check_begin || dif < SlavePlay.check_end) {
                        //Log.i(TAG, "vaylbpzhao->slave read ahead " + readahead);
                        SlavePlay.readAhead(readahead);
                    }
                    Thread.sleep(200);
                    
                }catch (SocketTimeoutException e) {
                    //timeout declare slave leave
                    e.printStackTrace();
                    Message message=new Message();
                    message.what=6;
                    mSlavePlay.mHandler.sendMessage(message);
                }
                catch (InterruptedException e) {
                    e.printStackTrace();
                }catch (IOException exception) {
                    exception.printStackTrace();
                } 
            }
        } catch (SocketException exception) {
            exception.printStackTrace();
            Message message=new Message();
            message.what=6;
            mSlavePlay.mHandler.sendMessage(message);
        } finally {
            if (socket != null)
                socket.close();
        }

    }

}
