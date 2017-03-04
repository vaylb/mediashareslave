
package com.pzhao.openslslave;

import android.app.Activity;
import android.content.pm.ActivityInfo;
import android.graphics.PixelFormat;
import android.os.Bundle;
import android.os.Handler;
import android.os.Message;
import android.util.DisplayMetrics;
import android.util.Log;
import android.view.MotionEvent;
import android.view.SurfaceHolder;
import android.view.SurfaceHolder.Callback;
import android.view.SurfaceView;
import android.view.View;
import android.view.View.OnClickListener;
import android.view.Window;
import android.view.WindowManager;
import android.widget.FrameLayout.LayoutParams;
import android.widget.ImageButton;
import android.widget.LinearLayout;
import android.widget.SeekBar;
import android.widget.TextView;
import android.widget.Toast;
import android.widget.SeekBar.OnSeekBarChangeListener;

import com.pzhao.slave.SendUdp;
import com.pzhao.slave.SlavePlay;
import com.pzhao.slave.UdpOrder;

import java.lang.ref.WeakReference;

/**
 * /**
 * 
 * @author 赵鹏
 * @version 创建时间：2014年9月14日 上午10:38:17 说明 从机界面
 */
public class MainActivity extends Activity {
    private static final String TAG = "SlaveActivity";
    private ImageButton btnConnect;
    private ImageButton btnInit;
    private SlavePlay msp;
    public boolean firstInit;
    public Handler mHandler;
    public LockAndUnlockScreen laus;
    private long exitTime = 0;
    private boolean slaveExit=false;
	
	//seekbar
    private TextView delay_tv;
    private SeekBar delay_seekBar;
    private double width, fDensity;
    private String delay_number;
    private DisplayMetrics displaysMetrics;
    
    //vaylb
    private SurfaceView surfaceview;
    public SurfaceHolder surfaceHolder;
    
    private View view;
    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        
        view = getLayoutInflater().from(this).inflate(R.layout.slave_main, null);  
        view.setSystemUiVisibility(View.SYSTEM_UI_FLAG_HIDE_NAVIGATION);  
        view.setOnClickListener(new NavigationOnClickListener());  
        //去除title   
        requestWindowFeature(Window.FEATURE_NO_TITLE);  
         //去掉Activity上面的状态栏
        getWindow().setFlags(WindowManager.LayoutParams. FLAG_FULLSCREEN ,  
                       WindowManager.LayoutParams. FLAG_FULLSCREEN);  
        //setContentView(R.layout.slave_main);
        setContentView(view);
        setRequestedOrientation(ActivityInfo.SCREEN_ORIENTATION_PORTRAIT);
        firstInit = true;
        mHandler = new MyHandler(this);
        laus = new LockAndUnlockScreen(this);
        laus.getAdmin();
        msp = new SlavePlay(MainActivity.this,mHandler,laus);
        msp.registerReceiver();
        
      //vaylb
        surfaceview = (SurfaceView) findViewById(R.id.surfaceView);
        surfaceview.setZOrderOnTop(true);//设置画布  背景透明
        LayoutParams lp = (LayoutParams) surfaceview.getLayoutParams();
        lp.width = 1080;
        lp.height =1920;
        surfaceview.setLayoutParams(lp);
        
        surfaceHolder = surfaceview.getHolder();
        surfaceHolder.setFormat(PixelFormat.TRANSLUCENT);
        surfaceHolder.setFixedSize(1920, 1080);
        surfaceHolder.addCallback(new Callback(){  
  
            @Override  
            public void surfaceCreated(SurfaceHolder holder) {  
                // TODO Auto-generated method stub  
                Log.d(TAG,"vaylb-->surfaceCreated");    
            }  
  
            @Override  
            public void surfaceChanged(SurfaceHolder holder, int format,  
                    int width, int height) {  
                // TODO Auto-generated method stub  
                  
            }  
  
            @Override  
            public void surfaceDestroyed(SurfaceHolder holder) {  
                // TODO Auto-generated method stub  
                  
            }
        });
        
        View.OnTouchListener ImageButtonTouchListener = new View.OnTouchListener() {

            @Override
            public boolean onTouch(View v, MotionEvent event) {
                // TODO Auto-generated method stub
                switch (v.getId()) {
                    case R.id.wifi:
                        if (event.getAction() == MotionEvent.ACTION_DOWN) {
                            btnConnect.setBackgroundResource(R.drawable.wifi_press);
                        }
                        else if (event.getAction() == MotionEvent.ACTION_UP) {
                            btnConnect.setBackgroundResource(R.drawable.wifi);
                            msp.openWifi();
                          /*  Toast.makeText(getApplicationContext(), "请连接主机Wifi热点",
                                    Toast.LENGTH_SHORT).show();*/
                        //    startActivity(new Intent(Settings.ACTION_WIFI_SETTINGS));
                        }
                        break;
                    case R.id.init:
                        if (event.getAction() == MotionEvent.ACTION_DOWN) {
                            btnInit.setBackgroundResource(R.drawable.init_press);
                        }
                        else if (event.getAction() == MotionEvent.ACTION_UP) {
                            btnInit.setBackgroundResource(R.drawable.init);
                            msp.init();
                            
                        }
                        break;
                }
                return false;
            }
        };

        btnConnect = (ImageButton) findViewById(R.id.wifi);
        btnConnect.setOnTouchListener(ImageButtonTouchListener);
        btnConnect.setEnabled(true);
        btnInit = (ImageButton) findViewById(R.id.init);
        btnInit.setOnTouchListener(ImageButtonTouchListener);
        
        //seekBar
        initSeekBarProgress();
    }


    public void onBackPressed() {
        if ((System.currentTimeMillis() - exitTime) > 3000) {
            Toast.makeText(getApplicationContext(), "再按一次退出",
                    Toast.LENGTH_SHORT).show();
             exitTime = System.currentTimeMillis();
        } else {
            slaveExit=true;
            Message msg = new Message();
            msg.what = 4;
            mHandler.sendMessage(msg);
        }
    }
    
    public static  class MyHandler extends Handler {
        private final WeakReference<MainActivity> mActivity;
        public MyHandler(MainActivity activity){
            mActivity = new WeakReference<MainActivity>(activity); 
        }
        @Override
        public void handleMessage(Message msg) {
            MainActivity activity=mActivity.get();
            if(msg.what == 0){
                Toast.makeText(activity, "初始化完成",
                        Toast.LENGTH_SHORT).show();
            }
            else if (msg.what == 1) {
                Toast.makeText(activity,
                        "主机来电，设置静音", Toast.LENGTH_SHORT).show();
            }
            else if (msg.what == 2) {
                Toast.makeText(activity,
                        "主机通话结束，恢复音量", Toast.LENGTH_SHORT).show();
            }
            else if(msg.what==3){
                Log.d(TAG, "pzhao->in msg.what==3");
                //Toast.makeText(activity,
                  //      "出现错误，正在退出..", Toast.LENGTH_SHORT).show();
                Message msg1 = new Message();
                msg1.what = 4;
                //sendMessage(msg1);
            }
            else if (msg.what == 5) {
                Toast.makeText(activity,
                        "请连接Wifi", Toast.LENGTH_SHORT).show();
            }
            else if (msg.what==4) {
                Log.d(TAG, "pzhao->msg.what==4");
                Toast.makeText(activity, "正在退出..",
                        Toast.LENGTH_SHORT).show();
                new Thread(){

                    @Override
                    public void run() {
                        // TODO Auto-generated method stub
                        super.run();
                        try {
                            sleep(500);
                        } catch (InterruptedException e) {
                            // TODO Auto-generated catch block
                            e.printStackTrace();
                        }
                        Message msg = new Message();
                        msg.what = 88;
                        sendMessage(msg);
                    }
                    
                }.start();
            }
            else if (msg.what==6) {
                //Toast.makeText(activity, "通信异常",Toast.LENGTH_SHORT).show();
            }
            else if (msg.what==7) {
                Toast.makeText(activity, "音频播放停止",Toast.LENGTH_SHORT).show();
            }
            else if (msg.what==8) {
                Toast.makeText(activity, "视频播放停止",Toast.LENGTH_SHORT).show();
                activity.surfaceview.setVisibility(View.INVISIBLE);
                Log.e(TAG, "vaylb-->set surface disvisible");
            }
            else if (msg.what==10) {
            	Log.e(TAG, "vaylb-->set surface visible");
                activity.surfaceview.setVisibility(View.VISIBLE);
                activity.msp.native_setVideoSurface(activity.surfaceHolder.getSurface());
            }
            else if(msg.what == 9){
            	activity.msp.native_setVideoSurface(activity.surfaceHolder.getSurface());
            }
            if (msg.what == 88) {
                Log.d(TAG,"vaylb->handleMessage=88,bye");
                if(activity.slaveExit){
                    new Thread(new SendUdp(UdpOrder.SLAVE_EXIT, activity.msp.hostIpAddress)).start();
                }   
                activity.msp.exitingState = true;
                activity.msp.exit();
                //android.os.Process.killProcess(android.os.Process.myPid());
                System.exit(0);

            }
        }
    }
	private void initSeekBarProgress(){
        displaysMetrics = getResources().getDisplayMetrics();
        width = displaysMetrics.widthPixels;
        fDensity = (width - Utils.dip2px(this, 51)) / 100;
        delay_seekBar=(SeekBar)findViewById(R.id.seekBar1);
        delay_tv=(TextView)findViewById(R.id.num_tv);
        
        delay_seekBar.setProgress(60);//max = 40,80
        delay_seekBar.setOnSeekBarChangeListener(mSeekChange);
        LinearLayout.LayoutParams paramsStrength = new LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.WRAP_CONTENT,
                LinearLayout.LayoutParams.WRAP_CONTENT);
        delay_number = 0 + "";
        paramsStrength.leftMargin = (int) (((60*100)/120) * fDensity); //让seekbar上的ms数随着滑块移动
        delay_tv.setLayoutParams(paramsStrength);
        delay_tv.setText(delay_number + " ms");
    }
    
    private OnSeekBarChangeListener mSeekChange = new OnSeekBarChangeListener() {

        @Override
        public void onProgressChanged(SeekBar seekBar, int progress,
                boolean fromUser) {
            if(msp!=null){
                int delay=17+progress-60;
                msp.setSlaveHost(delay);
               
            }
            delay_number = (progress-60)*5 + "";
            LinearLayout.LayoutParams paramsStrength = new LinearLayout.LayoutParams(
                    LinearLayout.LayoutParams.WRAP_CONTENT,
                    LinearLayout.LayoutParams.WRAP_CONTENT);
            paramsStrength.leftMargin = (int) ((progress*100/120) * fDensity);
            delay_tv.setLayoutParams(paramsStrength);
            delay_tv.setText(delay_number + " ms");
        }

        @Override
        public void onStartTrackingTouch(SeekBar seekBar) {
            // TODO Auto-generated method stub

        }

        @Override
        public void onStopTrackingTouch(SeekBar seekBar) {
            // TODO Auto-generated method stub

        }
    };
    
    class NavigationOnClickListener implements OnClickListener{

		@Override
		public void onClick(View v) {
			int i = view.getSystemUiVisibility();  
	        if (i == View.SYSTEM_UI_FLAG_HIDE_NAVIGATION) {  
	        	view.setSystemUiVisibility(View.SYSTEM_UI_FLAG_VISIBLE);  
	        } else if (i == View.SYSTEM_UI_FLAG_VISIBLE){  
	        	view.setSystemUiVisibility(View.SYSTEM_UI_FLAG_HIDE_NAVIGATION); 
	        }
		}
    }

}
