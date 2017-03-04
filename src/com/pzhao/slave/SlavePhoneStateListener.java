
package com.pzhao.slave;

import android.telephony.PhoneStateListener;
import android.telephony.TelephonyManager;
import android.util.Log;

public class SlavePhoneStateListener extends PhoneStateListener {
    private static final String TAG = "PhoneStateListener";
    public SlavePlay mSlavePlay;

    public SlavePhoneStateListener(SlavePlay object) {
        this.mSlavePlay = object;
    }

    public void onCallStateChanged(int state, String incomingNumber)
    {
        switch (state)
        {
            case TelephonyManager.CALL_STATE_IDLE:
                if (mSlavePlay.hasConnected&&mSlavePlay.isStart)
                    new Thread(new SendUdp(UdpOrder.SLAVE_CALL_GO, mSlavePlay.hostIpAddress))
                            .start();
                Log.d(TAG, "slave no call");
                break;

            case TelephonyManager.CALL_STATE_RINGING:
                if (mSlavePlay.hasConnected&&mSlavePlay.isStart)
                    new Thread(new SendUdp(UdpOrder.SLAVE_CALL_COME, mSlavePlay.hostIpAddress))
                            .start();
                Log.d(TAG, "slave ring");
                break;

            case TelephonyManager.CALL_STATE_OFFHOOK:
                // Toast.makeText(context, "in a call",
                // Toast.LENGTH_LONG).show();
                Log.d(TAG, "slave off hook");
                break;
        }
    }

}
