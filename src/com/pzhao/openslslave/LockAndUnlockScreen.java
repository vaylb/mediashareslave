package com.pzhao.openslslave;

import android.app.admin.DeviceAdminReceiver;
import android.app.admin.DevicePolicyManager;
import android.content.ComponentName;
import android.content.Context;
import android.content.Intent;
import android.os.PowerManager;
import android.os.PowerManager.WakeLock;

public class LockAndUnlockScreen extends DeviceAdminReceiver{

	static final int RESULT_ENABLE = 1;
	private Context mContext;
	private MainActivity mainActivity;
	private DevicePolicyManager mDPM;
	private ComponentName mDeviceAdminSample;
	private WakeLock mwakelock;
	PowerManager pm;
	public LockAndUnlockScreen(){}//添加到部分
	public LockAndUnlockScreen(Context context){
		this.mContext = context;
		this.mainActivity = (MainActivity)mContext;
	}
	public void getAdmin(){
		Intent intent = new Intent(DevicePolicyManager.ACTION_ADD_DEVICE_ADMIN);
		intent.putExtra(DevicePolicyManager.EXTRA_DEVICE_ADMIN, mDeviceAdminSample);
		intent.putExtra(DevicePolicyManager.EXTRA_ADD_EXPLANATION, "打开设备管理器");
		mainActivity.startActivityForResult(intent,RESULT_ENABLE);
	}
	
	public void lockScreen(){
		mDPM = (DevicePolicyManager) mContext.getSystemService(Context.DEVICE_POLICY_SERVICE);
		mDeviceAdminSample = new ComponentName(mContext,LockAndUnlockScreen.class);
		Boolean active = mDPM.isAdminActive(mDeviceAdminSample);
		if(!active){
			getAdmin();
		}else{
			mDPM.lockNow();
		}
	}
	
	@SuppressWarnings("deprecation")
    public void unLockScreen(){
		pm = (PowerManager)mContext.getSystemService(Context.POWER_SERVICE);
		mwakelock = pm.newWakeLock(PowerManager.ACQUIRE_CAUSES_WAKEUP | PowerManager.SCREEN_DIM_WAKE_LOCK, "Whatever");
		mwakelock.acquire();
		mwakelock.release();
	}
}
