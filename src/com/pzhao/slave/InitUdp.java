package com.pzhao.slave;

import android.util.Log;

import java.io.IOException;
import java.net.DatagramPacket;
import java.net.DatagramSocket;
import java.net.InetAddress;
import java.net.SocketTimeoutException;

public class InitUdp implements Runnable {
    private final static int PORT=43709;//remote port
    private final static String TAG="InitUdp";
    private InetAddress remoteIp;
    private String sendMsg;
    private int tryCount=100;
    private boolean sendSucess;
    private SlavePlay mSlavePlay;
    public InitUdp(InetAddress ip,SlavePlay play, String msg ){
        this.sendMsg=msg;
        this.remoteIp=ip;
        this.mSlavePlay=play;
        Log.d(TAG, "pzhao->send udp "+UdpOrder.map.get(sendMsg));
    }

    @Override
    public void run() {
        // TODO Auto-generated method stub
        DatagramSocket socket=null;
        try{
            byte[] data=sendMsg.getBytes();
            byte[] rcvdata = new byte[10];
            socket=new DatagramSocket(0);
            socket.setReuseAddress(true);
            socket.setSoTimeout(1000);
            DatagramPacket request=new DatagramPacket(data,data.length, remoteIp,PORT);
            DatagramPacket responce=new DatagramPacket(rcvdata, rcvdata.length);
            while(--tryCount>=0){
                try{
                    socket.send(request);
                    socket.receive(responce);
                    String result=new String(responce.getData(),0,responce.getLength());
                    if(sendMsg.equals(UdpOrder.GET_FRAME_COUNT)){
                    	
                        int count=Integer.valueOf(result);
                        if(count>0&&count<Integer.MAX_VALUE && !sendSucess){
                            sendSucess=true;
                            mSlavePlay.afterGetFrameCount(count<<1);
                            Log.d(TAG, "pzhao->get frame count "+(count<<1));
                            break;
                        }
                    }else{
                    	sendSucess=true;
                    	mSlavePlay.initsuccess();
                        break;
                    }
                    
                }catch (SocketTimeoutException e) {
                    e.printStackTrace();
                } catch (IOException e) {
                    // TODO: handle exception
                    e.printStackTrace();
                    break;
                }
            }
            if(!sendSucess)
                Log.d(TAG, "pzhao->udp "+UdpOrder.map.get(sendMsg)+" send fail");
        }catch(IOException exception){
            exception.printStackTrace();
        }finally{
            if(socket!=null)
                socket.close();
        }
        
        
    }
}
