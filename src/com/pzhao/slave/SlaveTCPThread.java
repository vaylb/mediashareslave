
package com.pzhao.slave;

import android.os.Message;
import android.util.Log;

import java.io.BufferedInputStream;
import java.io.IOException;
import java.net.ServerSocket;
import java.net.Socket;
import java.net.SocketException;
import java.net.UnknownHostException;

public class SlaveTCPThread extends Thread {
    private static final String TAG = "SlaveTCP";
    public static final int DEFAULT_PORT_TCP = 43709;
    public SlavePlay mSlavePlay;
    public boolean isRunning = true;
    public boolean slaveTcpFlag = true;
    public boolean firstToConnect = true;
    public boolean runFlag = false;
    public ServerSocket serverSocket = null;
    public Socket socket = null;
    protected byte[] buffer;
    public String hostIpsString;
    public BufferedInputStream inputStream;
    
    private int writepos;
    private  int mCount=480;
    private final int bufferCount;

    public SlaveTCPThread(SlavePlay object) {
        this.mSlavePlay =  object;
        this.hostIpsString = mSlavePlay.hostIp;
        this.mCount=SlavePlay.frameCount;
        this.buffer = mSlavePlay.buffer.array();
        this.bufferCount=SlavePlay.DEFAULT_BUFFER*SlavePlay.frameCount;
        Log.d(TAG, "pzhao->create SlaveTCPThread mcount="+mCount+" bufferCount"+bufferCount);
    }

    public void run() {
        try{
        	socket = new Socket(hostIpsString, DEFAULT_PORT_TCP);
            socket.setSoTimeout(30000);
            while (mSlavePlay.tcpFlag) {
                try {
                    if (!mSlavePlay.standbyFlag && !runFlag) {
                        Log.d(TAG, "pzhao->TCP to connect");
                        SlavePlay.setPlayingUriAudioPlayer(true);
                        //socket.setSoTimeout(5000);
                        inputStream = new BufferedInputStream(socket.getInputStream());
                        Log.d(TAG, "pzhao->TCP connect");
                        writepos=0;
                        mSlavePlay.socketFlag = true;
                        runFlag = true;
                    }
                    else if(mSlavePlay.standbyFlag && runFlag) {
                        Log.d(TAG, "pzhao->To close TCP standbyFlag "+mSlavePlay.standbyFlag+" lockFlag "+runFlag);
                        socket.close();
                        inputStream.close();
                        inputStream = null;
                        socket = null;
                        mSlavePlay.socketFlag = false;
                        SlavePlay.setPlayingUriAudioPlayer(false);
                        runFlag = false;
                        Log.d(TAG, "pzhao->TCP close");
                    }
                    if (mSlavePlay.socketFlag && SlavePlay.checkWrite()) {
                        try{
                            int writeOffset=writepos%bufferCount;
                            int readcount=0;
                            while(readcount<mCount){
                                int count=inputStream.read(buffer, writeOffset+readcount, mCount-readcount);
                                if(count==-1)
                                    break;
                                readcount+=count;
                            }
                            writepos+=mCount;
                            SlavePlay.setWritePos(writepos);
                        }catch(IOException e){
                           e.printStackTrace();
                           Log.d(TAG, "vaylb->tcp IOException");
                           Message msg=new Message();
                           msg.what=7;                        
                           mSlavePlay.mHandler.sendMessage(msg);
                        }
                    }
                    if(!runFlag){
                        sleep(10);;
                    }

                } catch (IOException e) {
                    e.printStackTrace();
                } catch (InterruptedException e) {
                    e.printStackTrace();
                }
            }
            Log.d(TAG, "vaylb-->Tcp thread end");
        } catch (SocketException e1) {
			e1.printStackTrace();
		} catch (UnknownHostException e1) {
			e1.printStackTrace();
		} catch (IOException e1) {
			e1.printStackTrace();
		}finally{
            if(socket!=null)
                try {
                    socket.close(); 
                } catch (IOException e) {
                    e.printStackTrace();
                }
        }

    }

}
