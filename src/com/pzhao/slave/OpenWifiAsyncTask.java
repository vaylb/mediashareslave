package com.pzhao.slave;

import android.app.AlertDialog;
import android.app.Dialog;
import android.app.ProgressDialog;
import android.content.Context;
import android.content.DialogInterface;
import android.net.wifi.ScanResult;
import android.net.wifi.WifiManager;
import android.os.AsyncTask;
import android.util.Log;
import android.view.LayoutInflater;
import android.view.View;
import android.widget.EditText;
import android.widget.ProgressBar;
import android.widget.Toast;

import com.pzhao.openslslave.R;

import java.util.List;

/*
 *开启wifi异步任务类 
 */

public class OpenWifiAsyncTask extends AsyncTask<Integer, Integer, StringBuffer>{
    public Context mContext;
    private WifiAdmin mWifiAdmin;
    private List<ScanResult> wifiResultList;
    private StringBuffer wifiList= new StringBuffer();
    private String wifiPassword = null;
    private String wifiItemSSID = null;
    private ProgressDialog mDialog;
    public OpenWifiAsyncTask(Context context) {
        mContext=context;
        mWifiAdmin=new WifiAdmin(context);
        dialogInit();
        
    }
    private void dialogInit(){
        mDialog=new ProgressDialog(mContext);
        mDialog.setCancelable(true);
        mDialog.setCanceledOnTouchOutside(false);
        mDialog.setMessage("正在打开Wifi");
        mDialog.setOnCancelListener(new DialogInterface.OnCancelListener() {
            
            @Override
            public void onCancel(DialogInterface arg0) {
                // TODO Auto-generated method stub
                OpenWifiAsyncTask.this.cancel(true);
            }
        });
    }
    @Override
    protected StringBuffer doInBackground(Integer... params) {
        // TODO Auto-generated method stub
        mWifiAdmin.WifiOpen();
        mWifiAdmin.WifiStartScan();
        while (mWifiAdmin.WifiCheckState() != WifiManager.WIFI_STATE_ENABLED) {
            // Log.i("WifiState", String.valueOf(mWifiAdmin.WifiCheckState()));
        }
        wifiResultList = mWifiAdmin.getScanResults();
        mWifiAdmin.getConfiguration();
        while(wifiResultList==null||wifiResultList.isEmpty()){
            wifiResultList = mWifiAdmin.getScanResults();
        }
        scanResultToString(wifiResultList, wifiList);
        return wifiList;
    }
    
     public void scanResultToString(List<ScanResult> listScan, StringBuffer sb) {
            for (int i = 0; i < listScan.size(); i++) {
                ScanResult strScan = listScan.get(i);
                sb.append(strScan.SSID);
                sb.append(";"); // 网络的名字,BSSID
            }
        }

    @Override
    protected void onPreExecute() {
        // TODO Auto-generated method stub
        super.onPreExecute();
      //  Toast.makeText(mContext, "正在打开Wifi。。", Toast.LENGTH_SHORT).show();
        mDialog.show();
    }

    @Override
    protected void onPostExecute(StringBuffer result) {
        // TODO Auto-generated method stub
        mDialog.dismiss();
        super.onPostExecute(result);
        final String[] str = result.toString().split(";");
        Dialog alertDialog = new AlertDialog.Builder(mContext)
        .setTitle("请选择WIFI热点：")
        .setIcon(R.drawable.ic_launcher)
        .setItems(str, new DialogInterface.OnClickListener() {

            @Override
            public void onClick(DialogInterface dialog, int which) {

              //  String[] ItemValue = str[which].split("--");
                wifiItemSSID = str[which];
                int wifiItemId = mWifiAdmin.IsConfiguration("\""
                        + wifiItemSSID + "\"");
                if (wifiItemId != -1) {
                    if (mWifiAdmin.ConnectWifi(wifiItemId)) {
                        Toast.makeText(mContext,
                                "连接" + str[which] + "成功！",
                                Toast.LENGTH_SHORT).show();
                    }
                } else {
                    LayoutInflater factory = LayoutInflater
                            .from(mContext);
                    final View textEntryView = factory.inflate(
                            R.layout.login_dialog, null);
                    final EditText passWord = (EditText) textEntryView
                            .findViewById(R.id.editText1);
                    new AlertDialog.Builder(mContext)
                            .setTitle("请输入WIFI密码：")
                            .setView(textEntryView)
                            .setPositiveButton(
                                    "确定",
                                    new DialogInterface.OnClickListener() {
                                        public void onClick(
                                                DialogInterface dialog,
                                                int whichButton) {
                                            wifiPassword = passWord
                                                    .getText()
                                                    .toString()
                                                    .trim();
                                            if (wifiPassword != null) {
                                                int netId = mWifiAdmin
                                                        .AddWifiConfig(
                                                                wifiResultList,
                                                                wifiItemSSID,
                                                                wifiPassword);
                                                if (netId != -1) {
                                                    mWifiAdmin
                                                            .getConfiguration();
                                                    if (mWifiAdmin
                                                            .ConnectWifi(netId)) {
                                                        // selectedItem.setBackgroundResource(R.color.green);
                                                        int intIP = mWifiAdmin
                                                                .getConnectedIPAddr();
                                                        Log.d("IP",
                                                                ""
                                                                        + intIP);
                                                    }
                                                } else {
                                                    Toast.makeText(
                                                            mContext,
                                                            "网络连接错误",
                                                            Toast.LENGTH_SHORT)
                                                            .show();
                                                    // selectedItem.setBackgroundResource(R.color.burlywood);
                                                }
                                            }
                                        }
                                    })
                            .setNegativeButton(
                                    "取消",
                                    new DialogInterface.OnClickListener() {
                                        public void onClick(
                                                DialogInterface dialog,
                                                int whichButton) {
                                        }

                                    }).show();
                }

            }
        })
        .setNegativeButton("取消",
                new DialogInterface.OnClickListener() {

                    @Override
                    public void onClick(DialogInterface dialog,
                            int which) {
                        // TODO Auto-generated method stub
                    }
                }).create();
        alertDialog.show();
        
    }

}
