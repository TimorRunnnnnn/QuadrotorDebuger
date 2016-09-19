package com.t.as.jizhi3;

import android.bluetooth.BluetoothAdapter;
import android.bluetooth.BluetoothDevice;
import android.content.BroadcastReceiver;
import android.content.Context;
import android.content.Intent;
import android.content.IntentFilter;
import android.os.Bundle;
import android.support.design.widget.NavigationView;
import android.support.v4.view.GravityCompat;
import android.support.v4.widget.DrawerLayout;
import android.support.v7.app.ActionBarDrawerToggle;
import android.support.v7.app.AppCompatActivity;
import android.support.v7.widget.Toolbar;
import android.util.Log;
import android.view.MenuItem;
import android.view.WindowManager;
import android.widget.Toast;

import java.io.IOException;


public class MainActivity extends AppCompatActivity
        implements NavigationView.OnNavigationItemSelectedListener {

    private Data bluetoothData;
    private ActionBarDrawerToggle mDrawerToggle;
    private BluetoothConnetStateReviver bluetoothConnetStateReviver;

    private int countOfAbout = 0;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_main);
        Log.e("bl", "MainOnCreate");

        Toolbar toolbar = (Toolbar) findViewById(R.id.toolbar);
        setSupportActionBar(toolbar);
        bluetoothData = (Data) getApplication();

        getWindow().addFlags(WindowManager.LayoutParams.FLAG_TRANSLUCENT_STATUS);

        BluetoothAdapter adapter = BluetoothAdapter.getDefaultAdapter();

        DrawerLayout drawer = (DrawerLayout) findViewById(R.id.drawer_layout);
        ActionBarDrawerToggle toggle = new ActionBarDrawerToggle(
                                                                        this, drawer, toolbar, R.string.navigation_drawer_open, R.string.navigation_drawer_close) {
        };
        drawer.setDrawerListener(toggle);
        toggle.syncState();

        NavigationView navigationView = (NavigationView) findViewById(R.id.nav_view);
        navigationView.setNavigationItemSelectedListener(this);

        IntentFilter intentFilter = new IntentFilter();
        intentFilter.addAction(BluetoothDevice.ACTION_ACL_CONNECTED);
        intentFilter.addAction(BluetoothDevice.ACTION_ACL_DISCONNECTED);
        bluetoothConnetStateReviver = new BluetoothConnetStateReviver();
        registerReceiver(bluetoothConnetStateReviver, intentFilter);


        if (adapter != null) {
            if (adapter.isEnabled() == false) {
                adapter.enable();
                Toast.makeText(getApplicationContext(), "已开启蓝牙:" + adapter.getName(), Toast.LENGTH_SHORT).show();
            }
        }
        if (bluetoothData.getConnectState() != BluetoothAdapter.STATE_CONNECTED) {
            Intent intent = new Intent();
            intent.setClass(MainActivity.this, BlueToothScan.class);
            startActivity(intent);
        }
    }

    @Override
    public void onBackPressed() {
        DrawerLayout drawer = (DrawerLayout) findViewById(R.id.drawer_layout);
        if (drawer.isDrawerOpen(GravityCompat.START)) {
            drawer.closeDrawer(GravityCompat.START);
        } else {
            super.onBackPressed();
        }
    }


//    @Override
//    public boolean onCreateOptionsMenu(Menu menu) {
//        // Inflate the menu; this adds items to the action bar if it is present.
//        getMenuInflater().inflate(R.menu.main, menu);
//        return true;
//    }

//    @Override
//    public boolean onOptionsItemSelected(MenuItem item) {
//        // Handle action bar item clicks here. The action bar will
//        // automatically handle clicks on the Home/Up button, so long
//        // as you specify a parent activity in AndroidManifest.xml.
//        int id = item.getItemId();
//
//        //noinspection SimplifiableIfStatement
//        if (id == R.id.action_settings) {
//            return true;
//        }
//
//        return super.onOptionsItemSelected(item);
//    }

    @SuppressWarnings("StatementWithEmptyBody")
    @Override
    public boolean onNavigationItemSelected(MenuItem item) {
        // Handle navigation view item clicks here.
        int id = item.getItemId();

        if (id == R.id.nav_diy) {
            Intent intent = new Intent();
            intent.setClass(MainActivity.this, Fun_Parameter.class);
            startActivity(intent);
        } else if (id == R.id.nav_zero) {
            //Toast.makeText(getApplicationContext(), "这里什么东西都没有", Toast.LENGTH_SHORT).show();
            Intent intent=new Intent();
            intent.setClass(MainActivity.this,Fun_OffsetCalibrate.class);
            startActivity(intent);
        } else if (id == R.id.nav_broadsetting) {
            Toast.makeText(getApplicationContext(), "这里什么东西都没有", Toast.LENGTH_SHORT).show();
        } else if (id == R.id.nav_textstream) {
            if (bluetoothData.getConnectState() != BluetoothAdapter.STATE_CONNECTED) {
                Toast.makeText(getApplicationContext(), bluetoothData.getString(R.string.bluetooth_noconnec_toast), Toast.LENGTH_LONG).show();
            }
            Intent intent = new Intent();
            intent.setClass(MainActivity.this, Fun_TextStream.class);
            startActivity(intent);
        } else if (id == R.id.nav_blPair) {
            Intent intent = new Intent();
            intent.setClass(MainActivity.this, BlueToothScan.class);
            startActivity(intent);
        } else if (id == R.id.nav_adbout) {
            Toast.makeText(getApplicationContext(), "这里什么东西都没有", Toast.LENGTH_SHORT).show();
        }

        DrawerLayout drawer = (DrawerLayout) findViewById(R.id.drawer_layout);
        drawer.closeDrawer(GravityCompat.START);

//测试CRC
//        CRC32 crc32=new CRC32();
//        crc32.reset();
//        int c=0x01010101;
//        crc32.update(c);
//        long crcJavaCalc=crc32.getValue();
        return true;
    }


    class BluetoothConnetStateReviver extends BroadcastReceiver {

        @Override
        public void onReceive(Context context, Intent intent) {
            String action = intent.getAction();
            if (BluetoothDevice.ACTION_ACL_CONNECTED.equals(action)) {
                BluetoothDevice device = intent.getParcelableExtra(BluetoothDevice.EXTRA_DEVICE);
                Log.e("bl", "监听到了连接:" + device.getName() + ":" + device.getAddress());
                bluetoothData.setRemoteName(device.getName());
                bluetoothData.setRemoteAddress(device.getAddress());
                bluetoothData.setConnectState(BluetoothAdapter.STATE_CONNECTED);
                bluetoothData.parameterList.clear();
                Toast.makeText(MainActivity.this, "连接成功:" + bluetoothData.getRemoteName(), Toast.LENGTH_SHORT).show();
            } else if (BluetoothDevice.ACTION_ACL_DISCONNECTED.equals(action)) {
                bluetoothData.setConnectState(BluetoothAdapter.STATE_DISCONNECTED);
                setTitle(bluetoothData.getString(R.string.app_name) + ":未连接");
                Toast.makeText(MainActivity.this, "已从 " + bluetoothData.getRemoteName() + " 断开", Toast.LENGTH_LONG).show();
            }
        }
    }

    @Override
    protected void onStart() {
        super.onStart();
        if (bluetoothData.getConnectState() == 999) {
            bluetoothData.setConnectState(BluetoothAdapter.STATE_DISCONNECTED);
        }
        if (bluetoothData.getConnectState() == BluetoothAdapter.STATE_CONNECTED) {
            setTitle(bluetoothData.getString(R.string.app_name) + ":" + bluetoothData.getRemoteName());
//            MenuItem item= (MenuItem) findViewById(R.id.nav_blPair);
//            item.setTitle("重新连接");
//            item= (MenuItem) findViewById(R.id.nav_linkstate);
//            item.setTitle("已连接:"+bluetoothData.getRemoteName());
        } else {
            setTitle(bluetoothData.getString(R.string.app_name) + ":未连接");
        }
    }

    @Override
    protected void onDestroy() {
        super.onDestroy();
        if (bluetoothData.getConnectState() == BluetoothAdapter.STATE_CONNECTED) {
            if (bluetoothData.getBlSocket().isConnected() == true) {
                try {
                    bluetoothData.getBlSocket().close();
                } catch (IOException e) {
                    e.printStackTrace();
                }
            }
        }
        unregisterReceiver(bluetoothConnetStateReviver);
        BluetoothAdapter adapter=BluetoothAdapter.getDefaultAdapter();
        if (adapter.isEnabled()==true){
            adapter.disable();
        }
    }
}
