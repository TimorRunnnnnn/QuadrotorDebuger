package com.t.as.jizhi3;

import android.app.ProgressDialog;
import android.bluetooth.BluetoothAdapter;
import android.bluetooth.BluetoothDevice;
import android.bluetooth.BluetoothSocket;
import android.content.BroadcastReceiver;
import android.content.Context;
import android.content.Intent;
import android.content.IntentFilter;
import android.os.Bundle;
import android.os.Handler;
import android.os.Message;
import android.support.design.widget.NavigationView;
import android.support.v7.app.AppCompatActivity;
import android.support.v7.widget.Toolbar;
import android.util.Log;
import android.view.LayoutInflater;
import android.view.Menu;
import android.view.MenuItem;
import android.view.View;
import android.view.ViewGroup;
import android.view.WindowManager;
import android.widget.AdapterView;
import android.widget.BaseAdapter;
import android.widget.ListView;
import android.widget.ProgressBar;
import android.widget.TextView;
import android.widget.Toast;

import java.io.IOException;
import java.util.ArrayList;
import java.util.List;
import java.util.UUID;

/**
 * Created by as on 2015/11/6.
 */
public class BlueToothScan extends AppCompatActivity implements NavigationView.OnNavigationItemSelectedListener {

    private BluetoothAdapter mBluetoothAdapter = BluetoothAdapter.getDefaultAdapter();
    private ProgressBar scanProgressBar;
    private BluetoothReciver bluetoothReciver;
    private ListView scanResultView;
    private List<blDeviceProperty> bluetoothDevices;
    private BluetoothListAdapter bluetoothListAdapter;
    private static BluetoothSocket blSocket;
    private IntentFilter blActionIntentFilter;
    private Data bluetoothData;

    private ProgressDialog blConnectWaitDialog;
    private Handler blConnectWaitHandler;

    static final String SPP_UUID = "00001101-0000-1000-8000-00805F9B34FB"; //串口的UUID

    //private
    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.bluetooth_scan);
        bluetoothData = (Data) getApplication();


        Log.e("bl", "scanOnCreate");
        bluetoothDevices = new ArrayList<blDeviceProperty>();
        bluetoothListAdapter = new BluetoothListAdapter(this, bluetoothDevices);
        Toolbar toolbar = (Toolbar) findViewById(R.id.bluetoothscantoolbar);
        setSupportActionBar(toolbar);

//        DrawerLayout drawer = (DrawerLayout) findViewById(R.id.drawer_layout);
//        ActionBarDrawerToggle toggle = new ActionBarDrawerToggle(
//                                                                        this, drawer, toolbar, R.string.navigation_drawer_open, R.string.navigation_drawer_close) {
//        };
//        drawer.setDrawerListener(toggle);
//        toggle.syncState();
//
//        NavigationView navigationView = (NavigationView) findViewById(R.id.nav_view);
//        navigationView.setNavigationItemSelectedListener(this);



        getWindow().addFlags(WindowManager.LayoutParams.FLAG_TRANSLUCENT_STATUS);

        scanResultView = (ListView) findViewById(R.id.blresultlistView);
        scanResultView.setAdapter(bluetoothListAdapter);
        scanResultView.setOnItemClickListener(new BluetoothListOnclick());

        scanProgressBar = (ProgressBar) findViewById(R.id.blscanprogressBar);




        bluetoothReciver = new BluetoothReciver();
        blActionIntentFilter = new IntentFilter();
        blActionIntentFilter.addAction(BluetoothDevice.ACTION_FOUND);
        blActionIntentFilter.addAction(BluetoothDevice.ACTION_BOND_STATE_CHANGED);
        blActionIntentFilter.addAction(mBluetoothAdapter.ACTION_DISCOVERY_FINISHED);
        blActionIntentFilter.addAction(mBluetoothAdapter.ACTION_STATE_CHANGED);

        registerReceiver(bluetoothReciver, blActionIntentFilter);

        if (mBluetoothAdapter != null) {
            if (bluetoothData.getConnectState() == BluetoothAdapter.STATE_CONNECTED) {
                setTitle("蓝牙连接:已连接");
                bluetoothListAdapter.add(new blDeviceProperty(bluetoothData.getRemoteName(), bluetoothData.getRemoteAddress(), 0));
                scanProgressBar.setVisibility(View.INVISIBLE);
            } else {
                blueStartScan();
            }
        } else {
            Toast toast = Toast.makeText(getApplicationContext(), "根本没有找到蓝牙!", Toast.LENGTH_LONG);
            toast.show();
            scanProgressBar.setVisibility(View.INVISIBLE);
            setTitle("蓝牙连接:本机根本没有蓝牙设备");
        }
    }

    @Override
    public boolean onNavigationItemSelected(MenuItem item) {
        return false;
    }

    private class BluetoothReciver extends BroadcastReceiver {

        @Override
        public void onReceive(Context context, Intent intent) {
            String action = intent.getAction();
            if (BluetoothDevice.ACTION_FOUND.equals(action)) {
                BluetoothDevice device = intent.getParcelableExtra(BluetoothDevice.EXTRA_DEVICE);

                String name = device.getName();
                String mac = device.getAddress();
                int rssi = 0;
                rssi = (int) (intent.getExtras().getShort(BluetoothDevice.EXTRA_RSSI));
                if (device.getBondState() == BluetoothDevice.BOND_NONE) {
                    Log.e("Bluetooth", "未配对");
                } else if (device.getBondState() == BluetoothDevice.BOND_BONDED) {
                    Log.e("Bluetooth", "已配对");
                }
                Log.e("Bluetooth", "找到一个蓝牙:" + name + ":" + mac + ";" + rssi);
                bluetoothListAdapter.add(new blDeviceProperty(name + "", mac + "", rssi));

            } else if (BluetoothAdapter.ACTION_DISCOVERY_FINISHED.equals(action)) {
                setTitle("蓝牙连接");
                Log.e("Bluetooth", "搜索完成");
                scanProgressBar.setVisibility(View.INVISIBLE);
            } else if (BluetoothDevice.ACTION_BOND_STATE_CHANGED.equals(action)) {
                BluetoothDevice devices = intent.getParcelableExtra(BluetoothDevice.EXTRA_DEVICE);
                switch (devices.getBondState()) {
                    case BluetoothDevice.BOND_BONDING:
                        setTitle("蓝牙连接:正在配对");
                        Log.e("BL", "正在配对:" + devices.getName());
                        break;
                    case BluetoothDevice.BOND_BONDED:
                        Log.e("BL", "配对完成");
                        blConnect(devices);
                        break;
                    case BluetoothDevice.BOND_NONE:
                        Log.e("BL", "取消配对");
                        setTitle("蓝牙连接:取消配对");
                        break;
                }
            }
        }
    }

    public boolean onCreateOptionsMenu(Menu menu) {
        // Inflate the menu; this adds items to the action bar if it is present.
        getMenuInflater().inflate(R.menu.bluetooth_menu, menu);
        return true;
    }

    private void blueStartScan() {
        scanProgressBar.setVisibility(View.VISIBLE);
        bluetoothListAdapter.clear();//清空上次的列表

        if (mBluetoothAdapter.isEnabled() == false) {
            mBluetoothAdapter.enable();
            mBluetoothAdapter.cancelDiscovery();
        }
        if (mBluetoothAdapter.isDiscovering() == true) {
            unregisterReceiver(bluetoothReciver);
            mBluetoothAdapter.cancelDiscovery();
            try {
                Thread.sleep(20);
            } catch (InterruptedException e) {
                e.printStackTrace();
            }
            registerReceiver(bluetoothReciver, blActionIntentFilter);
        }
        while (!mBluetoothAdapter.startDiscovery()) {
            try {
                Thread.sleep(2);
            } catch (InterruptedException e) {
                e.printStackTrace();
            }
        }
        setTitle("蓝牙连接:正在搜索...");
    }

    @Override
    public boolean onOptionsItemSelected(MenuItem item) {
        // Handle action bar item clicks here. The action bar will
        // automatically handle clicks on the Home/Up button, so long
        // as you specify a parent activity in AndroidManifest.xml.
        int id = item.getItemId();

        //noinspection SimplifiableIfStatement
        if (id == R.id.Blue_scan) {
            if (mBluetoothAdapter == null) {
                Toast toast = Toast.makeText(getApplicationContext(), "扫描个蛋,根本没有蓝牙", Toast.LENGTH_LONG);
                toast.show();
            } else {
                if (bluetoothData.getConnectState() == BluetoothAdapter.STATE_CONNECTED) {
                    try {
                        bluetoothData.getBlSocket().close();
                        setTitle("蓝牙连接");
                        blueStartScan();
                    } catch (IOException e) {
                        e.printStackTrace();
                    }
                } else {
                    blueStartScan();
                }
            }
        }

        return super.onOptionsItemSelected(item);
    }

    //listview点击事件
    class BluetoothListOnclick implements AdapterView.OnItemClickListener {

        @Override
        public void onItemClick(AdapterView<?> parent, View view, int position, long id) {
            if (mBluetoothAdapter.isDiscovering() == true) {
                mBluetoothAdapter.cancelDiscovery();
                Log.e("Bl", "停止搜寻,点击了:" + bluetoothDevices.get(position).name);
                //setTitle("蓝牙连接:停止搜索");
                scanProgressBar.setVisibility(View.INVISIBLE);
            }
            blDeviceProperty d = bluetoothDevices.get(position);
            String address = d.mac;
            BluetoothDevice btDev = mBluetoothAdapter.getRemoteDevice(address);
            if (btDev.getBondState() == BluetoothDevice.BOND_NONE) {
                //准备配对
//                Method createBondMethod = null;
//                try {
//                    createBondMethod = BluetoothDevice.class.getMethod("createBond");
//                } catch (NoSuchMethodException e) {
//                    e.printStackTrace();
//                }
//                try {
//                    createBondMethod.invoke(btDev);
//                } catch (Exception e) {
//                    setTitle("蓝牙连接:配对失败");
//                    e.printStackTrace();
//                }
                btDev.createBond();
                Log.e("BL", "开始配对");
                setTitle("蓝牙连接:开始配对");

            } else if (btDev.getBondState() == BluetoothDevice.BOND_BONDED) {
                blConnect(btDev);
            }
        }
    }

    private void blConnect(BluetoothDevice btDev) {
        UUID uuid = UUID.fromString(SPP_UUID);
        try {
            setTitle("蓝牙连接:正在连接...");
            blSocket = btDev.createRfcommSocketToServiceRecord(uuid);

            blConnectWaitDialog = new ProgressDialog(BlueToothScan.this);
            blConnectWaitDialog.setProgressStyle(ProgressDialog.STYLE_SPINNER);
            blConnectWaitDialog.setMessage("正在连接:" + btDev.getName());
            blConnectWaitDialog.setCancelable(false);
            blConnectWaitDialog.show();
            blConnectWaitHandler = new Handler() {
                @Override
                public void handleMessage(Message msg) {
                    super.handleMessage(msg);
                    blConnectWaitDialog.dismiss();
                    if (msg.what == 1) {
                        bluetoothData.setBlSocket(blSocket);
                        setTitle("蓝牙连接:连接成功");
                        finish();
                    } else {
                        setTitle("蓝牙连接:连接失败");
                        Toast.makeText(getApplicationContext(),"连接失败",Toast.LENGTH_SHORT).show();
                    }
                }
            };

            new Thread(new Runnable() {
                @Override
                public void run() {
                    try {
                        Thread.sleep(20);
                        blSocket.connect();
                        Message msg = blConnectWaitHandler.obtainMessage();
                        msg.what = 1;
                        blConnectWaitHandler.sendMessage(msg);
                    } catch (Exception e) {
                        Message msg = blConnectWaitHandler.obtainMessage();
                        msg.what = 0;
                        blConnectWaitHandler.sendMessage(msg);
                        try {
                            blSocket.close();
                        } catch (Exception e1) {

                        }
                        e.printStackTrace();
                    }
                }
            }).start();
        } catch (Exception e) {
            setTitle("蓝牙连接:连接失败");
            e.printStackTrace();
        }
    }

    @Override
    protected void onPause() {
        super.onPause();
//        if (mBluetoothAdapter.isDiscovering() == true) {
//            //unregisterReceiver(bluetoothReciver);
//            mBluetoothAdapter.cancelDiscovery();
//        }
    }

    @Override
    protected void onStop() {
        super.onStop();
        if (mBluetoothAdapter.isDiscovering() == true) {
            //
            mBluetoothAdapter.cancelDiscovery();
        }

        setTitle("蓝牙连接:停止搜索");
    }

    @Override
    protected void onDestroy() {
        unregisterReceiver(bluetoothReciver);
        super.onDestroy();
    }

    public class BluetoothListAdapter extends BaseAdapter {

        private List<blDeviceProperty> list;
        Context context;

        public BluetoothListAdapter(Context context, List<blDeviceProperty> list) {
            this.list = list;
            this.context = context;
        }

        public void add(blDeviceProperty item) {
            list.add(item);
            notifyDataSetChanged();
        }

        public void clear() {
            list.clear();
            notifyDataSetChanged();
        }

        @Override
        public int getCount() {
            return (list == null) ? 0 : list.size();
        }

        @Override
        public Object getItem(int position) {
            return list.get(position);
        }

        @Override
        public long getItemId(int position) {
            return position;
        }

        @Override
        public View getView(int position, View convertView, ViewGroup parent) {
            final blDeviceProperty bld = (blDeviceProperty) getItem(position);
            convertView = LayoutInflater.from(context).inflate(R.layout.bluetooth_list, null);
            TextView name = (TextView) convertView.findViewById(R.id.blnametextview);
            TextView mac = (TextView) convertView.findViewById(R.id.blmactextview);
            name.setText(bld.name + "(" + bld.rssi + ")");
            mac.setText(bld.mac);
            return convertView;
        }
    }


    public class blDeviceProperty {
        public String name;
        public String mac;
        public int rssi;

        public blDeviceProperty(String name, String mac, int rssi) {
            this.name = name;
            this.mac = mac;
            this.rssi = rssi;
        }
    }
}
