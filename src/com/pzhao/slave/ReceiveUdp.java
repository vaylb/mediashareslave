
package com.pzhao.slave;

import android.media.AudioManager;
import android.os.Message;
import android.util.Log;

import java.io.IOException;
import java.net.DatagramPacket;
import java.net.DatagramSocket;
import java.util.concurrent.Executor;
import java.util.concurrent.Executors;

public class ReceiveUdp implements Runnable {
    private final static int PORT = 43709; // local port
    private final static String TAG = "ReceiveUdp";
    private int preVolume = 14;
    private Executor udpExecutor;
    public volatile boolean runFlag = true;
    private DatagramSocket socket = null;
    private SlavePlay mSlavePlay;

    public ReceiveUdp(SlavePlay play) {
        this.mSlavePlay = play;
    }

    public void stop() {
        runFlag = false;
        if (socket != null)
            socket.close();
    }

    @Override
    public void run() {
        // TODO Auto-generated method stub
        udpExecutor = Executors.newCachedThreadPool();
        try {
            socket = new DatagramSocket(PORT);
            socket.setReuseAddress(true);
            byte[] data = new byte[10];
            DatagramPacket receive = new DatagramPacket(data, data.length);
            while (runFlag) {
                Log.d(TAG, "pzhao-> udp listen");
                socket.receive(receive);
                String result = new String(receive.getData(), 0, receive.getLength());
                udpExecutor.execute(new AckUdp(result, receive.getAddress(), receive.getPort()));
                Log.d(TAG, "vaylbpzhao->receive udp " + UdpOrder.map.get(result));
                // 判断信令
                
                if (result.equals(UdpOrder.VIDEO_PREPARE)) {
                    mSlavePlay.prepare_video();
                } 
                else if(result.equals(UdpOrder.VIDEO_START)){
                	Message message = new Message();
                    message.what = 10;
                    mSlavePlay.mHandler.sendMessage(message);
                }
                else if(result.equals(UdpOrder.VIDEO_STOP)){
                	Message message = new Message();
                    message.what = 8;
                    mSlavePlay.mHandler.sendMessage(message);
                }
                else if (result.equals(UdpOrder.STANDBY_FALSE)) {
                    mSlavePlay.standbyFlag = false;
                    if(!mSlavePlay.teamshare_audio_getframecount_flag){
                    	mSlavePlay.teamshare_audio_getframecount_flag = true;
                    	mSlavePlay.getAudioFrameCount();
                    }
                }
                else if (result.equals(UdpOrder.STANDBY_TRUE)) {
                    mSlavePlay.standbyFlag = true;
                    mSlavePlay.isStart=false;
                    SlavePlay.setPlayingUriAudioPlayer(false);
                }

                else if (result.equals(UdpOrder.HOST_CALL_COME)) {
                    Message message = new Message();
                    message.what = 1;
                    mSlavePlay.mHandler.sendMessage(message);
                    preVolume = mSlavePlay.mAudioManager.getStreamVolume(AudioManager.STREAM_MUSIC);
                    mSlavePlay.mAudioManager.setStreamMute(AudioManager.STREAM_MUSIC, true);
                }
                else if (result.equals(UdpOrder.HOST_CALL_GO)) {
                    Message message = new Message();
                    message.what = 2;
                    mSlavePlay.mHandler.sendMessage(message);
                    mSlavePlay.mAudioManager.setStreamMute(AudioManager.STREAM_MUSIC, false);
                    mSlavePlay.mAudioManager.setStreamVolume(AudioManager.STREAM_MUSIC, preVolume,
                            AudioManager.FLAG_SHOW_UI);
                }
                else if (result.equals(UdpOrder.SCREEN_ON)) {
                    Log.d(TAG, "pzhao->screen on");
                    mSlavePlay.laus.unLockScreen();
                }
                else if (result.equals(UdpOrder.SCREEN_OFF)) {
                    Log.d(TAG, "pzhao->screen off");
                    mSlavePlay.laus.lockScreen();
                }
                else if (result.equals(UdpOrder.HOST_EXIT)) {
                	//vaylb
                    //SlavePlay.setPlayingUriAudioPlayer(false);
                    Log.d(TAG, "receive exit exitingState= " + mSlavePlay.exitingState);
                    if (!mSlavePlay.exitingState) {
                        mSlavePlay.stopSlave();
                        Message msg = new Message();
                        msg.what = 4;
                        mSlavePlay.mHandler.sendMessage(msg);
                    }

                }
                else if (result.equals(UdpOrder.START_RETURN)) {
                    mSlavePlay.revd = System.currentTimeMillis();
                    mSlavePlay.recUdpTime = System.nanoTime();
                    SlavePlay.setPlaybackStat(false);//false 为开始播放
                    mSlavePlay.startgetSlaveWrite();
                    mSlavePlay.getSlaveWrite();
                    
                }
				else if (result.equals(UdpOrder.MODE_SYNC)) {
                    mSlavePlay.defaultMode();
                }
                else if (result.equals(UdpOrder.MODE_REVERB)) {
                    mSlavePlay.delay50ms();
                }
                else if (result.equals(UdpOrder.MODE_KARA)) {
                    mSlavePlay.delay100ms();
                }
                else if (result.equals(UdpOrder.SPLIT_PLAY_FALSE)) {
                    mSlavePlay.split_play(0);
                }
                else if (result.equals(UdpOrder.SPLIT_PLAY_TRUE)) {
                    mSlavePlay.split_play(1);
                }else if (result.equals(UdpOrder.AUDIO_STOP)) {
                	//vaylb
                    if (!mSlavePlay.exitingState) {
                        mSlavePlay.stopSlave();
                    }
                }
                else {
                    Log.i(TAG, "receive unknown udp! " + result);
                }
            }
        } catch (IOException exception) {
            exception.printStackTrace();
        } finally {
            if (socket != null)
                socket.close();
        }

    }

}
