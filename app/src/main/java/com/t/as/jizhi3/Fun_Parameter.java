package com.t.as.jizhi3;

import android.bluetooth.BluetoothAdapter;
import android.content.Context;
import android.os.Bundle;
import android.os.Handler;
import android.os.Message;
import android.support.v7.app.AppCompatActivity;
import android.support.v7.widget.Toolbar;
import android.text.Editable;
import android.text.TextWatcher;
import android.util.Log;
import android.view.LayoutInflater;
import android.view.Menu;
import android.view.MenuItem;
import android.view.MotionEvent;
import android.view.View;
import android.view.ViewGroup;
import android.view.WindowManager;
import android.view.inputmethod.InputMethodManager;
import android.widget.BaseAdapter;
import android.widget.Button;
import android.widget.EditText;
import android.widget.LinearLayout;
import android.widget.ListView;
import android.widget.TextView;
import android.widget.Toast;

import java.io.IOException;
import java.io.InputStream;
import java.util.ArrayList;

/**
 * Created by as on 2015/11/9.
 */
public class Fun_Parameter extends AppCompatActivity {

    private ParameterListAdapter parameterListAdapter;
    private Data bluetoothData;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.parameter_activity);
        Toolbar toolbar = (Toolbar) findViewById(R.id.parametertoolbar);
        setSupportActionBar(toolbar);

        bluetoothData = (Data) getApplication();

        parameterListAdapter = new ParameterListAdapter(this);
        ListView parameterListView = (ListView) findViewById(R.id.listView);
        parameterListView.setAdapter(parameterListAdapter);
        parameterListAdapter.notifyDataSetChanged();

//        这段代码实现点击非参数框强行让参数框失去焦点并收起键盘
        final LinearLayout layout = (LinearLayout) findViewById(R.id.parameter_layout);
        View.OnTouchListener closeImeListener = new View.OnTouchListener() {
            @Override
            public boolean onTouch(View v, MotionEvent event) {
                InputMethodManager inputMethodManager = (InputMethodManager) getApplicationContext().getSystemService(Context.INPUT_METHOD_SERVICE);
                inputMethodManager.hideSoftInputFromWindow(v.getWindowToken(), 0);
                layout.setFocusable(true);
                layout.setFocusableInTouchMode(true);
                layout.requestFocus();
                return false;
            }
        };
        parameterListView.setOnTouchListener(closeImeListener);
        layout.setOnTouchListener(closeImeListener);

        getWindow().addFlags(WindowManager.LayoutParams.FLAG_TRANSLUCENT_STATUS);

        if (bluetoothData.getConnectState() == BluetoothAdapter.STATE_CONNECTED) {
            //在这里发出第一次握手
            bluetoothData.parameterList.clear();
            requestParameter();
        } else {
            Toast.makeText(getApplicationContext(), bluetoothData.getString(R.string.bluetooth_noconnec_toast), Toast.LENGTH_SHORT).show();
        }
    }

    @Override
    public boolean onCreateOptionsMenu(Menu menu) {
        // Inflate the menu; this adds items to the action bar if it is present.
        getMenuInflater().inflate(R.menu.diy_parameter_activity, menu);
        return true;
    }

    @Override
    public boolean onOptionsItemSelected(MenuItem item) {
        int id = item.getItemId();
        if (id == R.id.parameter_shakehand) {
            try {
                int x = bluetoothData.getBlSocket().getInputStream().available();
                if (x != 0) {
                    bluetoothData.getBlSocket().getInputStream().skip(x);
                }
            } catch (IOException e) {
                e.printStackTrace();
            }
            parameterListAdapter.clear();
            requestParameter();
        } else if (id == R.id.parameter_send) {
            ArrayList<Byte> sendBuffer = new ArrayList<Byte>();
            sendBuffer.add(bluetoothData.HEAD_FIRST);
            sendBuffer.add(bluetoothData.HEAD_SECOND);
            sendBuffer.add(bluetoothData.REQUEST_UPDATAPARAMETER);//之后再加上size
            //上面有三个字节的参数列表
            for (int i = 0; i < bluetoothData.parameterList.size(); i++) {
                sendBuffer.add(bluetoothData.parameterList.get(i).address);
                byte tem[] = bluetoothData.int2byte(Float.floatToIntBits(bluetoothData.parameterList.get(i).value.floatValue()));
                for (int j = 0; j < 4; j++) {
                    sendBuffer.add(tem[j]);
                }
            }
            //非4字节补零
            int r = (4 - ((sendBuffer.size() - 3) % 4));
            for (int i = 0; i < r; i++) {
                sendBuffer.add((byte) 0x00);
            }
            //求和校验
            int checkSum = 0;
            for (int i = 3; i < sendBuffer.size(); i++) {
                checkSum += sendBuffer.get(i);
            }
            byte tem[] = bluetoothData.int2byte(checkSum);
            for (int j = 0; j < 4; j++) {
                sendBuffer.add(tem[j]);
            }
            sendBuffer.add(bluetoothData.TAIL_FIRST);
            sendBuffer.add(bluetoothData.TAIL_SECOND);
            short size = (short) (sendBuffer.size() + 2);
            tem = bluetoothData.short2byte(size);
            sendBuffer.add(2, tem[1]);
            sendBuffer.add(2, tem[0]);
            byte sendbytes[] = new byte[sendBuffer.size()];
            for (int i = 0; i < sendBuffer.size(); i++) {
                sendbytes[i] = sendBuffer.get(i);
            }
            if (bluetoothData.getConnectState() == BluetoothAdapter.STATE_CONNECTED) {
                try {
                    bluetoothData.blSocket.getOutputStream().write(sendbytes);
                    final Handler handler = new Handler() {
                        @Override
                        public void handleMessage(Message msg) {
                            super.handleMessage(msg);
                            if (msg.what != 1) {
                                Toast.makeText(Fun_Parameter.this, "数据发送错误", Toast.LENGTH_SHORT).show();
                            }
                        }
                    };

                    new Thread(new Runnable() {
                        @Override
                        public void run() {
                            try {
                                Thread.sleep(50);
                                int count = bluetoothData.blSocket.getInputStream().available();
                                if (count == 15) {
                                    byte bytes[] = new byte[count];
                                    int readbyte = 0;
                                    while (readbyte < count) {
                                        readbyte += bluetoothData.blSocket.getInputStream().read(bytes, readbyte, count - readbyte);
                                    }
                                    if (bytes[4] == bluetoothData.RESPOND_UPDATAPARAMETER) {
                                        Message msg = handler.obtainMessage();
                                        msg.what = 1;
                                        handler.sendMessage(msg);
                                    }
                                } else {
                                    Message msg = handler.obtainMessage();
                                    msg.what = 0;
                                    handler.sendMessage(msg);
                                }
                            } catch (Exception e) {
                                e.printStackTrace();
                            }
                        }
                    });
                } catch (IOException e) {
                    e.printStackTrace();
                }
            }
            // Log.e("bl", "发送序列:" + sendbytes);
        }
        return super.onOptionsItemSelected(item);
    }

    //干脆用这个函数来顺便来解析参数数据好了..只发回一个参数属性Arrylist
    private void requestParameter() {
        bluetoothData.sendRequest(bluetoothData.REQUEST_PARAMETER,0);
        final Handler reciveHandler = new Handler() {
            @Override
            public void handleMessage(Message msg) {
                super.handleMessage(msg);
                if (msg.what == bluetoothData.ERROR_NODATA) {
                    Toast.makeText(getApplicationContext(), "没有收到数据", Toast.LENGTH_SHORT).show();
                } else if (msg.what == bluetoothData.ERROR_CRCFAILED) {
                    Toast.makeText(getApplicationContext(), "CRC校验失败", Toast.LENGTH_SHORT).show();
                } else if (msg.what == bluetoothData.ERROR_FRAMEERROR) {
                    Toast.makeText(getApplicationContext(), "帧错误", Toast.LENGTH_SHORT).show();
                } else if (msg.what == bluetoothData.ERROR_TIMEOUT) {
                    Toast.makeText(getApplicationContext(), "接收数据超时", Toast.LENGTH_SHORT).show();
                } else if (msg.what == bluetoothData.ERROR_NOERROR) {
                    ArrayList<Fun_Parameter.ParameterProperty> reciveParameter = (ArrayList<ParameterProperty>) msg.obj;
                    for (int i = 0; i < reciveParameter.size(); i++) {
                        parameterListAdapter.add(reciveParameter.get(i));
                    }
                    Log.e("bl", "正常接收到帧:" + msg.obj.toString());
                }
            }
        };
        new Thread(new Runnable() {
            @Override
            public void run() {
                ArrayList<Byte> reciveBuffer = new ArrayList<Byte>();
                ArrayList<Fun_Parameter.ParameterProperty> reciveParameter = new ArrayList<Fun_Parameter.ParameterProperty>();
                try {
                    //Thread.sleep(50);//休眠200ms,115200波特率下可以发送2KB数据,接受这200ms内的所有数据
                    InputStream inputStream = bluetoothData.blSocket.getInputStream();
                    int times=0;
                    while(inputStream.available()==0){
                        Thread.sleep(5);
                        times++;
                        if (times>200){
                            Log.e("bl","没有接收到数据");
                            break;
                        }
                    }
                    int count = inputStream.available();
                    if (count == 0) {
                        Message msg = reciveHandler.obtainMessage();
                        msg.what = bluetoothData.ERROR_TIMEOUT;
                        reciveHandler.sendMessage(msg);
                        return;
                    } else {
                        while (true) {
                            Thread.sleep(50);
                            if (count != inputStream.available()) {
                                count = inputStream.available();
                            } else {
                                //直到没有新数据进来的时候
                                break;
                            }
                        }
                        byte bytes[] = new byte[count];
                        int readbyte = 0;
                        while (readbyte < count) {
                            readbyte += inputStream.read(bytes, readbyte, count - readbyte);
                        }
                        Log.e("bl","接收到的数据:"+bytes.toString());
                        int frameStart = -1, frameEnd = -1;
                        for (int i = 0; i < readbyte - 1; i++) {
                            if (frameStart == -1) {
                                if (bytes[i] == bluetoothData.HEAD_FIRST && bytes[i + 1] == bluetoothData.HEAD_SECOND) {
                                    frameStart = i;
                                }
                            }
                            if (frameStart != -1) {
                                //只在帧头后面找帧尾
                                if (bytes[i] == bluetoothData.TAIL_FIRST && bytes[i + 1] == bluetoothData.TAIL_SECOND) {
                                    frameEnd = i + 1;
                                }
                            }
                            if (frameEnd != -1) {
                                //只找第一帧
                                break;
                            }
                        }
                        if (frameStart == -1 || frameEnd == -1) {
                            Log.e("bl", "帧错误,帧头:" + frameStart + ",帧尾:" + frameEnd);
                            Message msg = reciveHandler.obtainMessage();
                            msg.what = bluetoothData.ERROR_FRAMEERROR;
                            reciveHandler.sendMessage(msg);
                            return;
                        }
                        for (int i = frameStart; i <= frameEnd; i++) {
                            reciveBuffer.add(bytes[i]);
                        }

                        //至此如果得到了包含正确帧的数据则放到了reciveBuffer里面,只管接到的第一帧
                        // Log.e("bl", "现在已接收到:" + reciveBuffer.toString());
                        Integer frameLen = 0;
                        int temp=reciveBuffer.get(2).shortValue();
                        if (temp<0){
                            temp=256+temp;
                        }
                        frameLen = (temp + reciveBuffer.get(3).shortValue() * 256);//stm32是小端
                        //Log.e("bl", "接收到的帧长:" + frameLen);
                        if (frameLen != reciveBuffer.size() || ((frameLen - 11) % 4 != 0)) {
                            Message msg = reciveHandler.obtainMessage();
                            msg.what = bluetoothData.ERROR_FRAMEERROR;
                            reciveHandler.sendMessage(msg);
                        }
                        //至此数据帧的帧头帧尾和长度校验完成,未来加上CRC校验
                    /*CRC校验*/
//                    CRC32 crc32=new CRC32();
//                    crc32.reset();
////                    for (int i=5;i<reciveBuffer.size()-6;i++){
////                        crc32.update(reciveBuffer.get(i));
////                    }
//                    byte test[]={(byte)0x01,(byte)0x01,(byte)0x01,(byte)0x02};
//                    for (int i=0;i<4;i++){
//                        crc32.update(test[i]);
//                    }
//                    long crcJavaCalc=crc32.getValue();
//                    byte crc_b[]=new byte[4];
//                    for (int i=0;i<4;i++){
//                        crc_b[i]=reciveBuffer.get(reciveBuffer.size()-6+i);
//                    }
//                    long crcRecive=bluetoothData.byte2int(crc_b);

                        int checkSum = 0;
                        for (int i = 5; i < reciveBuffer.size() - 6; i++) {
                            checkSum += reciveBuffer.get(i);
                        }

                        byte crc_b[] = new byte[4];
                        for (int i = 0; i < 4; i++) {
                            crc_b[i] = reciveBuffer.get(reciveBuffer.size() - 6 + i);
                        }
                        long crcRecive = bluetoothData.byte2int(crc_b);
                        if (checkSum != crcRecive) {
                            Message msg = reciveHandler.obtainMessage();
                            msg.what = bluetoothData.ERROR_CRCFAILED;
                            reciveHandler.sendMessage(msg);
                            return;
                        }
                        int dataCount = 0;
                        if (reciveBuffer.get(4) != (bluetoothData.RESPOND_PARAMETER)) {
                            Message msg = reciveHandler.obtainMessage();
                            msg.what = bluetoothData.ERROR_FRAMEERROR;
                            reciveHandler.sendMessage(msg);
                        } else {
                            while (true)//倒数第三个字节,假如11个字节,size=11,倒数第三个字节下标是9
                            {
                                int nameStart = reciveBuffer.indexOf((byte) '<') + 1;
                                int nameEnd = reciveBuffer.indexOf((byte) '|') - 1;
                                int len = nameEnd - nameStart + 1;
                                dataCount += (len + 2 + 9);
                                byte[] name_b = new byte[len];
                                for (int i = 0; i < len; i++) {
                                    name_b[i] = reciveBuffer.get(i + nameStart);
                                }
                                String name = new String(name_b, "UTF-8");
                                Byte address = reciveBuffer.get(nameEnd + 2);
                                byte Value_b[] = new byte[4];
                                for (int i = 0; i < 4; i++) {
                                    Value_b[i] = reciveBuffer.get(nameEnd + 3 + i);
                                }
                                double currentValue = bluetoothData.byte2double(Value_b, 0);

                                for (int i = 0; i < 4; i++) {
                                    Value_b[i] = reciveBuffer.get(nameEnd + 7 + i);
                                }
                                double step = bluetoothData.byte2double(Value_b, 0);
                                reciveParameter.add(new ParameterProperty(name, address, currentValue, step));
                                for (int i = 0; i < nameEnd + 11; i++) {
                                    reciveBuffer.remove(0);//移除已处理的字节
                                }
                                //移除了一个参数,剩下的一定是帧尾,或者'<'
                                if ((reciveBuffer.get(4 + (4 - dataCount % 4)) == bluetoothData.TAIL_FIRST && reciveBuffer.get(5 + (4 - dataCount % 4)) == bluetoothData.TAIL_SECOND) || reciveBuffer.get(0) == (byte) '<') {
                                    if ((reciveBuffer.get(4 + (4 - dataCount % 4)) == bluetoothData.TAIL_FIRST && reciveBuffer.get(5 + (4 - dataCount % 4)) == bluetoothData.TAIL_SECOND)) {
                                        //表示处理完,发送reciveParameter,否则继续循环
                                        Message msg = reciveHandler.obtainMessage();
                                        msg.what = bluetoothData.ERROR_NOERROR;
                                        msg.obj = reciveParameter;
                                        reciveHandler.sendMessage(msg);
                                        break;
                                    }
                                } else {
                                    Message msg = reciveHandler.obtainMessage();
                                    msg.what = bluetoothData.ERROR_FRAMEERROR;
                                    reciveHandler.sendMessage(msg);
                                }
                                //这里处理完了一帧的数据
                            }
                        }
                    }
                } catch (Exception e) {
                    Message msg = reciveHandler.obtainMessage();
                    msg.what = bluetoothData.ERROR_FRAMEERROR;
                    reciveHandler.sendMessage(msg);
                }
            }
        }).start();
    }

    public class ParameterProperty {
        public ParameterProperty(String name, Byte address, Double value, Double step) {
            this.name = name;
            this.value = value;
            this.address = address;
            this.step = step;
        }

        Double step;
        String name;//显示出来的名字
        Byte address;//HexID
        Double value;//当前值
    }

    public class ParameterListAdapter extends BaseAdapter {
        // private List<ParameterProperty> list;
        Context context;

        public ParameterListAdapter(Context context) {
            this.context = context;
        }

        public void add(ParameterProperty item) {
            bluetoothData.parameterList.add(item);
            notifyDataSetChanged();
        }

        public void clear() {
            bluetoothData.parameterList.clear();
            notifyDataSetChanged();
        }

        @Override
        public int getCount() {
            return (bluetoothData.parameterList == null) ? 0 : bluetoothData.parameterList.size();
        }

        @Override
        public Object getItem(int position) {
            return bluetoothData.parameterList.get(position);
        }

        @Override
        public long getItemId(int position) {
            return position;
        }

        @Override
        public View getView(final int position, View convertView, ViewGroup parent) {

            Log.e("bl", "调用了getView" + position);
            final ParameterProperty parameterProperty = (ParameterProperty) getItem(position);
            convertView = LayoutInflater.from(context).inflate(R.layout.diy_parameter_list, null);

            TextView name = (TextView) convertView.findViewById(R.id.p_name);
            final EditText editText = (EditText) convertView.findViewById(R.id.p_edit);
            name.setText(parameterProperty.name);
            editText.setText(parameterProperty.value.toString());
            editText.addTextChangedListener(new TextWatcher() {
                @Override
                public void beforeTextChanged(CharSequence s, int start, int count, int after) {

                }

                @Override
                public void onTextChanged(CharSequence s, int start, int before, int count) {

                }

                @Override
                public void afterTextChanged(Editable s) {
                    if (s.length() != 0) {
                        String string = s.toString();
                        if ((string.indexOf(".") > 0) && (string.indexOf(".", string.indexOf(".") + 1) > 0)) {
                            Toast.makeText(getApplicationContext(), "输两个小数点干嘛:" + s.toString(), Toast.LENGTH_SHORT).show();
                            string = string.replaceFirst("\\.", ",");
                            string = string.replaceFirst("\\.", "");
                            string = string.replaceFirst(",", ".");
                            bluetoothData.parameterList.get(position).value = Double.valueOf(string);
                            editText.setText(string);
                        } else {
                            boolean b=string.equals("-");
                            if (b==false) {
                                Double n = Double.valueOf(string);
                                bluetoothData.parameterList.get(position).value = n;
                                Log.e("bl", "监听到参数框修改" + bluetoothData.parameterList.get(position).name + ":" + n);
                            }
                        }
                    }
//                    else {
//                        String string = "0";
//                        bluetoothData.parameterList.get(position).value = new Double(string);
//                        editText.setText(string);
//                    }
                }
            });
            //增加button事件
            Button button_l = (Button) convertView.findViewById(R.id.p_buttonleft);
            Button button_r = (Button) convertView.findViewById(R.id.p_buttonright);
            button_l.setOnClickListener(new View.OnClickListener() {
                @Override
                public void onClick(View v) {
                    bluetoothData.parameterList.get(position).value -= bluetoothData.parameterList.get(position).step;
                    String tem = bluetoothData.parameterList.get(position).value.toString();
                    int i = tem.indexOf(".") + 2;
                    if (i != 1) {
                        if ((tem.length() > (i + 3)) && (tem.charAt(i + 1) == '9')) {
                            bluetoothData.parameterList.get(position).value -= 0.001;
                            tem = bluetoothData.parameterList.get(position).value.toString();
                        }
                        String tem2 = tem.substring(0, i);
                        editText.setText(tem2);
                    } else {
                        editText.setText(tem);
                    }
                }
            });
            button_r.setOnClickListener(new View.OnClickListener() {
                @Override
                public void onClick(View v) {
                    bluetoothData.parameterList.get(position).value += bluetoothData.parameterList.get(position).step;
                    String tem = bluetoothData.parameterList.get(position).value.toString();
                    //精度最多到小数点后一位
                    int i = tem.indexOf(".") + 2;
                    if (i != 1) {//等于1说明返回的是-1
                        if ((tem.length() > (i + 3)) && (tem.charAt(i + 1) == '9')) {
                            bluetoothData.parameterList.get(position).value += 0.001;
                            tem = bluetoothData.parameterList.get(position).value.toString();
                        }
                        String tem2 = tem.substring(0, i);
                        editText.setText(tem2);
                    } else {
                        editText.setText(tem);
                    }
                }
            });
            return convertView;
        }
    }
}
