
package com.pzhao.slave;

import android.content.BroadcastReceiver;
import android.content.Context;
import android.content.Intent;
import android.content.IntentFilter;
import android.media.AudioManager;
import android.net.ConnectivityManager;
import android.net.NetworkInfo;
import android.net.wifi.ScanResult;
import android.net.wifi.WifiManager;
import android.os.Handler;
import android.os.Message;
import android.os.PowerManager;
import android.os.PowerManager.WakeLock;
import android.telephony.PhoneStateListener;
import android.telephony.TelephonyManager;
import android.util.Log;
import android.view.Surface;
import android.widget.Toast;

import com.pzhao.openslslave.LockAndUnlockScreen;

import java.lang.reflect.InvocationTargetException;
import java.lang.reflect.Method;
import java.net.InetAddress;
import java.net.UnknownHostException;
import java.nio.ByteBuffer;
import java.util.List;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;

/**
 * /**
 * 
 * @author 赵鹏
 * @version 创建时间：2014年9月14日 上午10:37:56 说明 从机类
 */
public class SlavePlay {
    private static final String TAG = "SlavePlay";
    public Context mContext;
    private WifiAdmin mWifiAdmin;
    public LockAndUnlockScreen laus;
    public volatile boolean hasConnected;
    private ReceiveUdp mReceiveUdp;
    public SlaveUdpThread mUdpThread;

    public static SlaveTCPThread mSlaveTCPThread;

    public AudioManager mAudioManager;

    public static volatile boolean hasInit = false;
    public volatile boolean isStart;

    public int exitType;
    public volatile boolean exitingState = false;
    public volatile boolean tcpFlag;
    public volatile boolean standbyFlag;
    public volatile boolean socketFlag;
    public String hostIp;
    public String slaveIp;
    public InetAddress hostIpAddress;
    public long time;
    public long slaveUdpTime;
    public long recUdpTime;
    public Handler mHandler;
    public static final int DEFAULT_BUFFER = 128;
    public static final int DEFAULT_COUNT = 480;
    public static volatile int frameCount = 0;

    public SlavePhoneStateListener mHostPhoneStateListener;
    public WakeLock mwakeLock;
    public TelephonyManager mTelManager;
    public ConnectivityManager mConnmanager;
    public WifiManager mWifiManager;
    public BroadcastReceiver mConnectivityReceiver;
    public BroadcastReceiver mScreenReceiver;
    public IntentFilter filter_screen;
    public IntentFilter filter_wifi;
    public ByteBuffer buffer;
    public static volatile int slave_host = 17;
    public static volatile int check_begin = 20;
    public static volatile int check_end = 15;

    public long send, revd;
    private ExecutorService slaveExecutor;
	public GetWriteUdp getWriteUdp = null;
    private boolean video_share_flag = false;
    
    public boolean teamshare_audio_getframecount_flag = false;

    public SlavePlay(Context context, Handler handler, LockAndUnlockScreen lock) {
        this.mContext = context;
        this.laus = lock;
        this.mWifiAdmin = new WifiAdmin(context);
        this.mHandler = handler;
    }

    // ----------------------------------------
    // 打开连接WIFI
    // --------------------------------------

    public int openWifi() {
        OpenWifiAsyncTask openWifiAsyncTask=new OpenWifiAsyncTask(mContext);
        openWifiAsyncTask.execute(0);
        return 0;
    }

    public void scanResultToString(List<ScanResult> listScan, StringBuffer sb) {
        for (int i = 0; i < listScan.size(); i++) {
            ScanResult strScan = listScan.get(i);
            sb.append(strScan.SSID + "--" + strScan.BSSID);
            sb.append(";"); // 网络的名字,BSSID
        }
    }

    public void prepare() {
        slaveIp = mWifiAdmin.getLocalAdress();
        hostIp = mWifiAdmin.getSeverAdress();
        
        if (hostIp != null)
            try {
                hostIpAddress = InetAddress.getByName(hostIp);
                Log.d(TAG, "pzhao->get host ip " + hostIpAddress);
//                if(!video_share_flag){
//                	video_share_flag = true;
//                	native_setupVideoPlay(hostIpAddress.toString().substring(1));
//                }
            } catch (UnknownHostException e) {
                // TODO Auto-generated catch block
                e.printStackTrace();
            }
        slaveExecutor = Executors.newCachedThreadPool();
        //vaylb
        slaveExecutor.execute(new InitUdp(hostIpAddress, this, UdpOrder.INIT));
        mReceiveUdp = new ReceiveUdp(this);
        slaveExecutor.execute(mReceiveUdp);
    }
    
    public void getAudioFrameCount(){
    	slaveExecutor.execute(new InitUdp(hostIpAddress, this, UdpOrder.GET_FRAME_COUNT));
    }
    
    public void prepare_video(){
    	hostIp = mWifiAdmin.getSeverAdress();
        if (hostIp != null)
            try {
                hostIpAddress = InetAddress.getByName(hostIp);
                Log.d(TAG, "pzhao->get host ip " + hostIpAddress);
                if(!video_share_flag){
                	video_share_flag = true;
                	native_setupVideoPlay(hostIpAddress.toString().substring(1));
                }
            } catch (UnknownHostException e) {
                // TODO Auto-generated catch block
                e.printStackTrace();
            }
        Message msg = new Message();
        msg.what = 9;
        mHandler.sendMessage(msg);
    }

    // ---------------------------------
    // 初始化
    // -----------------------------------
    public int init() {
        if (!mWifiManager.isWifiEnabled()) {
            Toast.makeText(mContext, "请先打开Wifi",
                    Toast.LENGTH_SHORT).show();
            return -1;
        }
        if (!hasConnect()) {
            Toast.makeText(mContext, "请先连接Wifi热点",
                    Toast.LENGTH_SHORT).show();
            return -1;
        }
        // hasinit true,sendUdp then return;
        if (hasInit)
            return 0;
        Log.d(TAG, "pzhao->init()");
        prepare();
        setJniEnv();
        socketFlag = false;
        standbyFlag = true;
        tcpFlag = true;
        mAudioManager.setStreamVolume(AudioManager.STREAM_MUSIC, 14, AudioManager.FLAG_SHOW_UI);
        mTelManager = (TelephonyManager) mContext.getSystemService(Context.TELEPHONY_SERVICE);
        mHostPhoneStateListener = new SlavePhoneStateListener(this);
        mTelManager.listen(mHostPhoneStateListener, PhoneStateListener.LISTEN_CALL_STATE);
        return 0;
    }

    public void afterGetFrameCount(int count) {
        hasInit = true;
        tcpFlag = true;
        if (count <= 0)
            frameCount = DEFAULT_COUNT;
        else
            frameCount = count;
        buffer = ByteBuffer.allocateDirect(frameCount * DEFAULT_BUFFER);
        createEngine(buffer, frameCount);
        createAudioPlayer();
        
        //vaylb:与主机进行同步
        getWriteUdp = new GetWriteUdp(hostIpAddress, this);
        slaveExecutor.execute(getWriteUdp);
        
        mSlaveTCPThread = new SlaveTCPThread(this);
        slaveExecutor.execute(mSlaveTCPThread);
    }
    
    public void initsuccess() {
    	Message msg = new Message();
        msg.what = 0;
        mHandler.sendMessage(msg);
	}

    public void registerReceiver() {
        filter_wifi = new IntentFilter(ConnectivityManager.CONNECTIVITY_ACTION);
        mConnectivityReceiver = new BroadcastReceiver() {

            @Override
            public void onReceive(Context context, Intent intent) {
                // TODO Auto-generated method stub
                mConnmanager = (ConnectivityManager) mContext
                        .getSystemService(Context.CONNECTIVITY_SERVICE);
                NetworkInfo wifiInfo = mConnmanager.getNetworkInfo(ConnectivityManager.TYPE_WIFI);
                if (wifiInfo != null && wifiInfo.isConnected()) {
                    Log.d(TAG, "pzhao->wifi connect");
                }
                else {
                    Log.d(TAG, "pzhao->wifi lost");
                    if (mSlaveTCPThread != null && mSlaveTCPThread.runFlag) {
                       Message msg = new Message();
                        msg.what = 3;
                       mHandler.sendMessage(msg);
                    } else {
                       /* Message msg = new Message();
                        msg.what = 5;
                       mHandler.sendMessage(msg);*/
                    }
                }

            }
        };
        mContext.registerReceiver(mConnectivityReceiver, filter_wifi);

        filter_screen = new IntentFilter();
        filter_screen.addAction(Intent.ACTION_SCREEN_ON);
        filter_screen.addAction(Intent.ACTION_SCREEN_OFF);
        mScreenReceiver = new BroadcastReceiver() {

            @Override
            public void onReceive(Context arg0, Intent arg1) {
                // TODO Auto-generated method stub
                String action = arg1.getAction();
                if (action.equals(Intent.ACTION_SCREEN_ON)) {
                    Log.d(TAG, "screen_on");
                }
                else if (action.equals(Intent.ACTION_SCREEN_OFF)) {
                    Log.d(TAG, "screen_off");
                }
            }
        };
        mContext.registerReceiver(mScreenReceiver, filter_screen);
        mWifiManager = (WifiManager) mContext.getSystemService(Context.WIFI_SERVICE);
        mAudioManager = (AudioManager) mContext.getSystemService(Context.AUDIO_SERVICE);
     //   aquireWakeLock();
    }

    public boolean hasConnect() {
        if (mConnmanager == null)
            mConnmanager = (ConnectivityManager) mContext
                    .getSystemService(Context.CONNECTIVITY_SERVICE);
        NetworkInfo wifiInfo = mConnmanager.getNetworkInfo(ConnectivityManager.TYPE_WIFI);
        if (wifiInfo != null && wifiInfo.isConnected()) {
            hasConnected = true;
            return true;
        }

        else {
            return false;
        }
    }

    public void stopSlave() {
    	Log.e(TAG, "vaylb-->audio stop received, stop slave");
        tcpFlag = false;
        teamshare_audio_getframecount_flag = false;
        getWriteUdp.quit();
        getWriteUdp = null;
        shutdown();
    }
    
    public void split_play(int split){
    	native_setSplitPlay(split);
    }

    public void exit() {
        Log.d(TAG, "vaylb->slave exit");
        //vaylb
        native_exitVideoPlay();
    //    releaseWakeLock();
        if (mConnectivityReceiver != null)
            mContext.unregisterReceiver(mConnectivityReceiver);
        if (mScreenReceiver != null)
            mContext.unregisterReceiver(mScreenReceiver);
        if (mTelManager != null)
            mTelManager.listen(mHostPhoneStateListener, PhoneStateListener.LISTEN_NONE);
        if (!hasInit)
            return;
        if(mReceiveUdp != null) mReceiveUdp.stop();
        if(getWriteUdp != null) getWriteUdp.quit();
        slaveExecutor.shutdown();
        shutdown();
    }

    private void fromJni(int i) {
        isStart=true;
        slaveExecutor.execute(new SendUdp(UdpOrder.START_PLAY, hostIpAddress));
        Log.d(TAG, "pzhao-->from Jni send start udp to host " + i);
    }
    
    public void startgetSlaveWrite() {
        if(getWriteUdp != null)getWriteUdp.start();
    }

    public void getSlaveWrite() {
    	if(getWriteUdp != null)getWriteUdp.setCount(10);
    }
    
    public void setSlaveHost(int delay) {
        slave_host = delay;
        check_begin = slave_host + 4;
        check_end = slave_host - 4;
        if (getWriteUdp != null)
            getWriteUdp.setCount(10);
        Log.d(TAG, "vaylbpzhao->slave_host " + slave_host);
    }
    
    public void defaultMode() {
        /*
         * slave_host = 17; check_begin = 20; check_end = 15;
         */
        slave_host = 17;
        check_begin = 20;
        check_end = 15;
        getWriteUdp.setCount(10);
        Log.d(TAG, "vaylbpzhao->defaultMode ");
    }

    public void delay50ms() {
        /*
         * slave_host = 7; check_begin = 10; check_end = 5;
         */
        slave_host  = 27;
        check_begin = 30;
        check_end = 25;
        getWriteUdp.setCount(10);
        Log.d(TAG, "vaylbpzhao->delay50ms ");
    }

    public void delay100ms() {
        slave_host = -3 + 50;
        check_begin = 0 + 50;
        check_end = -5 + 50;
        getWriteUdp.setCount(10);
        Log.d(TAG, "vaylbpzhao->delay100ms ");
    }

    public void getLatency() {
        Method m;
        try {
            m = mAudioManager.getClass().getMethod("getOutputLatency", int.class);
            int latency = (Integer) m.invoke(mAudioManager, AudioManager.STREAM_MUSIC);
            Log.d(TAG, "audio latency: " + latency);
        } catch (NoSuchMethodException e) {
            // TODO Auto-generated catch block
            e.printStackTrace();
        } catch (IllegalAccessException e) {
            // TODO Auto-generated catch block
            e.printStackTrace();
        } catch (IllegalArgumentException e) {
            // TODO Auto-generated catch block
            e.printStackTrace();
        } catch (InvocationTargetException e) {
            // TODO Auto-generated catch block
            e.printStackTrace();
        }

    }

    // 添加：aquireWakeLock() by hui.
    public WakeLock mwakelock;

    public void aquireWakeLock() {
        PowerManager pm = (PowerManager) mContext.
                getSystemService(Context.POWER_SERVICE);
        if (mwakelock == null) {
            mwakelock = pm.newWakeLock(PowerManager.PARTIAL_WAKE_LOCK, TAG);
        }
        mwakelock.acquire();

    }

    // 添加：releaseWakeLock().
    public void releaseWakeLock() {
        if (mwakelock != null) {
            mwakelock.release();
            mwakelock = null;
        }
    }

    static {
        System.loadLibrary("OpenSLTest");
        System.loadLibrary("video_share_slave");
    }

    private native void setJniEnv();

    public static native void createEngine(ByteBuffer buffer, int frameCount);

    public static native boolean checkWrite();

    public static native void setWritePos(int pos);

    public static native int haswrite();
    
    public static native void readAhead(int readahead);

    public static native void createAudioPlayer();

    public static native void setPlayingUriAudioPlayer(boolean isPlaying);

    public static native void setMuteUriAudioPlayer(boolean mute);

    public static native void shutdown();
	public static native void setPlaybackStat(boolean stat);
    //vaylb
    public static native void native_setupVideoPlay(String ip);
    public static native void native_exitVideoPlay();
    public native boolean native_setVideoSurface(Surface surface);
    public native void native_setSplitPlay(int split);
    
    

}
