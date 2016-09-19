package com.t.as.jizhi3;

import android.app.ProgressDialog;
import android.content.DialogInterface;
import android.os.Bundle;
import android.os.Handler;
import android.os.Looper;
import android.os.Message;
import android.support.v7.app.AlertDialog;
import android.support.v7.app.AppCompatActivity;
import android.support.v7.widget.Toolbar;
import android.text.InputType;
import android.util.Log;
import android.view.Menu;
import android.view.MenuItem;
import android.view.WindowManager;
import android.widget.EditText;
import android.widget.TextView;
import android.widget.Toast;

import java.util.ArrayList;

/**
 * Created by as on 2015/12/2.
 */
public class Fun_OffsetCalibrate extends AppCompatActivity {
    private Data bluetoothData;
    private ProgressDialog dataWaitDialog;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.fun_offsetcalibrate);
        Toolbar toolbar = (Toolbar) findViewById(R.id.offsettoolbar);
        setSupportActionBar(toolbar);
        getWindow().addFlags(WindowManager.LayoutParams.FLAG_TRANSLUCENT_STATUS);//这个实现标题栏透明
        bluetoothData = (Data) getApplication();

//        ProgressDialog dialog = new ProgressDialog(Fun_OffsetCalibrate.this);
//        dialog.setProgressStyle(ProgressDialog.STYLE_SPINNER);
//        dialog.setMessage("正在请求数据...");
//        dialog.setCancelable(false);
//        dialog.show();

        final boolean[] flagGetData = {false};

        dataWaitDialog = new ProgressDialog(Fun_OffsetCalibrate.this);
        dataWaitDialog.setProgressStyle(ProgressDialog.STYLE_SPINNER);
        dataWaitDialog.setMessage("请求数据...");
        dataWaitDialog.setCancelable(true);
        dataWaitDialog.setOnDismissListener(new DialogInterface.OnDismissListener() {
            @Override
            public void onDismiss(DialogInterface dialog) {
                if (flagGetData[0] == false) {
                    Fun_OffsetCalibrate.this.finish();
                }
            }
        });
        dataWaitDialog.show();


        final Handler resultHandler = new Handler() {
            @Override
            public void handleMessage(Message msg) {
                super.handleMessage(msg);
                Float offsetData[] = new Float[9];
                ArrayList<Byte> reciveBuffer = (ArrayList<Byte>) msg.obj;
                for (int i = 0; i < 9; i++) {
                    byte bytes[] = new byte[4];
                    for (int j = 0; j < 4; j++) {
                        bytes[j] = reciveBuffer.get(j + 4 * i);
                    }
                    offsetData[i] = new Double(bluetoothData.byte2double(bytes, 0)).floatValue();
                }
                bluetoothData.gyroCalTime=reciveBuffer.get(36);
                ((TextView) findViewById(R.id.offset_gyro_x)).setText(offsetData[0].toString());
                ((TextView) findViewById(R.id.offset_gyro_y)).setText(offsetData[1].toString());
                ((TextView) findViewById(R.id.offset_gyro_z)).setText(offsetData[2].toString());
                ((TextView) findViewById(R.id.offset_max_x)).setText(offsetData[3].toString());
                ((TextView) findViewById(R.id.offset_max_y)).setText(offsetData[4].toString());
                ((TextView) findViewById(R.id.offset_max_z)).setText(offsetData[5].toString());
                ((TextView) findViewById(R.id.offset_min_x)).setText(offsetData[6].toString());
                ((TextView) findViewById(R.id.offset_min_y)).setText(offsetData[7].toString());
                ((TextView) findViewById(R.id.offset_min_z)).setText(offsetData[8].toString());

                Float f = new Float((offsetData[3].floatValue() - offsetData[6]) + 0.0001);//加一个数防止整数导致toString没有小数点
                ((TextView) findViewById(R.id.offset_diff_x)).setText(f.toString().substring(0, f.toString().indexOf('.') + 2));
                f = new Float((offsetData[4].floatValue() - offsetData[7]) + 0.0001);
                ((TextView) findViewById(R.id.offset_diff_y)).setText(f.toString().substring(0, f.toString().indexOf('.') + 2));
                f = new Float((offsetData[5].floatValue() - offsetData[8]) + 0.0001);
                ((TextView) findViewById(R.id.offset_diff_z)).setText(f.toString().substring(0, f.toString().indexOf('.') + 2));
            }
        };
        new Thread(new Runnable() {
            @Override
            public void run() {
                Looper.prepare();
                bluetoothData.sendRequest(bluetoothData.REQUEST_GYRO_AND_MAG_OFFSET,0);
                final Handler handler = new Handler() {
                    @Override
                    public void handleMessage(Message msg) {
                        super.handleMessage(msg);
                        if (dataWaitDialog.isShowing() == true) {
                            if (msg.what != bluetoothData.ERROR_NOERROR) {
                                //Toast.makeText(Fun_OffsetCalibrate.this, "未收到数据", Toast.LENGTH_SHORT).show();
                                bluetoothData.sendRequest(bluetoothData.REQUEST_GYRO_AND_MAG_OFFSET,0);
                                bluetoothData.reciveData(this, bluetoothData, bluetoothData.RESPOND_GYRO_AND_MAG_OFFSET,2000);
                            } else {
                                flagGetData[0] = true;
                                dataWaitDialog.dismiss();
                                Message msg2 = resultHandler.obtainMessage();
                                msg2.copyFrom(msg);
                                resultHandler.sendMessage(msg2);
                            }
                        }
                    }
                };
                bluetoothData.reciveData(handler, bluetoothData, bluetoothData.RESPOND_GYRO_AND_MAG_OFFSET,2000);
                Looper.loop();
            }
        }).start();
    }


    @Override
    public boolean onCreateOptionsMenu(Menu menu) {
        getMenuInflater().inflate(R.menu.fun_offsetcalibrate_menu, menu);
        return super.onCreateOptionsMenu(menu);
    }

    @Override
    public boolean onOptionsItemSelected(MenuItem item) {
        if (item.getItemId() == R.id.magcal) {
            bluetoothData.sendRequest(bluetoothData.REQUEST_MAG_CAL,0);
            dataWaitDialog = new ProgressDialog(Fun_OffsetCalibrate.this);
            dataWaitDialog.setProgressStyle(ProgressDialog.STYLE_SPINNER);
            dataWaitDialog.setMessage("等待磁力计数据...");
            dataWaitDialog.setCancelable(false);
            dataWaitDialog.show();

            Handler handler = new Handler() {
                @Override
                public void handleMessage(Message msg) {
                    super.handleMessage(msg);
                    if (dataWaitDialog.isShowing()) {
                        dataWaitDialog.dismiss();
                        if (msg.what != bluetoothData.ERROR_NOERROR) {
                            Toast.makeText(Fun_OffsetCalibrate.this, "数据错误", Toast.LENGTH_LONG).show();
                        } else {
                            Float offsetData[] = new Float[6];
                            ArrayList<Byte> reciveBuffer = (ArrayList<Byte>) msg.obj;
                            for (int i = 0; i < 6; i++) {
                                byte bytes[] = new byte[4];
                                for (int j = 0; j < 4; j++) {
                                    bytes[j] = reciveBuffer.get(j + 4 * i);
                                }
                                offsetData[i] = new Double(bluetoothData.byte2double(bytes, 0)).floatValue();
                            }
                            ((TextView) findViewById(R.id.offset_max_x)).setText(offsetData[0].toString());
                            ((TextView) findViewById(R.id.offset_max_y)).setText(offsetData[1].toString());
                            ((TextView) findViewById(R.id.offset_max_z)).setText(offsetData[2].toString());
                            ((TextView) findViewById(R.id.offset_min_x)).setText(offsetData[3].toString());
                            ((TextView) findViewById(R.id.offset_min_y)).setText(offsetData[4].toString());
                            ((TextView) findViewById(R.id.offset_min_z)).setText(offsetData[5].toString());

                            Float f = new Float((offsetData[0].floatValue() - offsetData[3]) + 0.0001);//加一个数防止整数导致toString没有小数点
                            ((TextView) findViewById(R.id.offset_diff_x)).setText(f.toString().substring(0, f.toString().indexOf('.') + 2));
                            f = new Float((offsetData[1].floatValue() - offsetData[4]) + 0.0001);
                            ((TextView) findViewById(R.id.offset_diff_y)).setText(f.toString().substring(0, f.toString().indexOf('.') + 2));
                            f = new Float((offsetData[2].floatValue() - offsetData[5]) + 0.0001);
                            ((TextView) findViewById(R.id.offset_diff_z)).setText(f.toString().substring(0, f.toString().indexOf('.') + 2));

                            Toast.makeText(Fun_OffsetCalibrate.this, "磁力计校准成功", Toast.LENGTH_SHORT).show();
                        }
                    }
                }
            };
            bluetoothData.reciveData(handler,bluetoothData,bluetoothData.RESPOND_MAG_CAL,60000);
        } else if (item.getItemId() == R.id.gyrocal) {
            bluetoothData.sendRequest(bluetoothData.REQUEST_GYRO_CAL,bluetoothData.gyroCalTime);
            dataWaitDialog = new ProgressDialog(Fun_OffsetCalibrate.this);
            dataWaitDialog.setProgressStyle(ProgressDialog.STYLE_SPINNER);
            dataWaitDialog.setMessage("等待陀螺仪数据...");
            dataWaitDialog.setCancelable(true);
            dataWaitDialog.show();

            Handler handler = new Handler() {
                @Override
                public void handleMessage(Message msg) {
                    super.handleMessage(msg);
                    if (dataWaitDialog.isShowing()) {
                        dataWaitDialog.dismiss();
                        if (msg.what != bluetoothData.ERROR_NOERROR) {
                            Toast.makeText(Fun_OffsetCalibrate.this, "数据错误", Toast.LENGTH_LONG).show();
                        } else {
                            Float offsetData[] = new Float[3];
                            ArrayList<Byte> reciveBuffer = (ArrayList<Byte>) msg.obj;
                            for (int i = 0; i < 3; i++) {
                                byte bytes[] = new byte[4];
                                for (int j = 0; j < 4; j++) {
                                    bytes[j] = reciveBuffer.get(j + 4 * i);
                                }
                                offsetData[i] = new Double(bluetoothData.byte2double(bytes, 0)).floatValue();
                            }
                            ((TextView) findViewById(R.id.offset_gyro_x)).setText(offsetData[0].toString());
                            ((TextView) findViewById(R.id.offset_gyro_y)).setText(offsetData[1].toString());
                            ((TextView) findViewById(R.id.offset_gyro_z)).setText(offsetData[2].toString());
                            Toast.makeText(Fun_OffsetCalibrate.this, "陀螺仪校准成功", Toast.LENGTH_SHORT).show();
                        }
                    }
                }
            };
            bluetoothData.reciveData(handler,bluetoothData,bluetoothData.RESPOND_GYRO_CAL,bluetoothData.gyroCalTime*1000+2000);
        } else if (item.getItemId() == R.id.settime) {
            final EditText editText = new EditText(this);
            editText.setText("" + bluetoothData.gyroCalTime);
            editText.setInputType(InputType.TYPE_CLASS_NUMBER);
            AlertDialog timeDialog = new AlertDialog.Builder(this).setTitle("陀螺仪校准时长(s)")
                                             .setView(editText)
                                             .setPositiveButton("确定", new DialogInterface.OnClickListener() {
                                                 @Override
                                                 public void onClick(DialogInterface dialog, int which) {
                                                     Integer time = new Integer(editText.getText().toString());
                                                     if (time < 1) {
                                                         time = 1;
                                                         Toast.makeText(Fun_OffsetCalibrate.this, "最短时间为1s", Toast.LENGTH_SHORT).show();
                                                     } else if (time > 20) {
                                                         time = 20;
                                                         Toast.makeText(Fun_OffsetCalibrate.this, "最长时间为20s", Toast.LENGTH_SHORT).show();
                                                     }
                                                     bluetoothData.gyroCalTime = time;
                                                     Log.e("bl", "时长:" + time);
                                                 }
                                             })
                                             .setNegativeButton("取消", null).create();
            // timeDialog.setCanceledOnTouchOutside(false);
            timeDialog.show();

        }
        return super.onOptionsItemSelected(item);
    }
}
