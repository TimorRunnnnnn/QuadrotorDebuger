package com.t.as.jizhi3;

import android.bluetooth.BluetoothAdapter;
import android.bluetooth.BluetoothSocket;
import android.os.Bundle;
import android.os.Handler;
import android.os.Message;
import android.support.v7.app.AppCompatActivity;
import android.support.v7.widget.Toolbar;
import android.text.method.ScrollingMovementMethod;
import android.util.Log;
import android.view.Menu;
import android.view.MenuItem;
import android.view.View;
import android.view.WindowManager;
import android.widget.Button;
import android.widget.EditText;
import android.widget.ScrollView;
import android.widget.TextView;
import android.widget.Toast;

import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;

/**
 * Created by as on 2015/11/8.
 */
public class Fun_TextStream extends AppCompatActivity {

    private Button sendButton;
    private Data bluetoothData;
    private BluetoothConnetThread bluetoothConnetThread;
    private String stringToTextView = "";
    private TextDealHandler textDealHandler;
    private TextView textViewOfRecive;
    private ScrollView scrollView;
    private Handler textSendHandler;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.fun_textstream);
        getWindow().setSoftInputMode(WindowManager.LayoutParams.SOFT_INPUT_ADJUST_RESIZE);
        Toolbar toolbar = (Toolbar) findViewById(R.id.textstreamtoolbar);
        setSupportActionBar(toolbar);

        getWindow().addFlags(WindowManager.LayoutParams.FLAG_TRANSLUCENT_STATUS);

        textViewOfRecive = (TextView) findViewById(R.id.recivetext);
        textViewOfRecive.setMovementMethod(new ScrollingMovementMethod());

        textDealHandler = new TextDealHandler();

        scrollView= (ScrollView) findViewById(R.id.scrollView);

        bluetoothData = (Data) getApplication();
        if (bluetoothData.getConnectState() == BluetoothAdapter.STATE_CONNECTED) {
            bluetoothConnetThread = new BluetoothConnetThread(bluetoothData.blSocket);
            bluetoothConnetThread.start();
        }
        sendButton = (Button) findViewById(R.id.sendbutton);
        sendButton.setOnClickListener(new SendButtonListener());


    }

    private class TextDealHandler extends android.os.Handler {
        @Override
        public void handleMessage(Message msg) {
            super.handleMessage(msg);
            //这里处理文本框显示的内容
            String s = (String) msg.obj;
            stringToTextView += s;

            int i=stringToTextView.indexOf("\\r\\n");
            while(i!=-1){
               stringToTextView= stringToTextView.replace("\\r\\n","\n");
                i=stringToTextView.indexOf("\\r\\n");
            }

            textViewOfRecive.setText(stringToTextView);

            int offset=textViewOfRecive.getMeasuredHeight()-scrollView.getMeasuredHeight();
            if (offset>0){
                scrollView.scrollTo(0,offset);
            }
        }
    }

    @Override
    public boolean onCreateOptionsMenu(Menu menu) {
        getMenuInflater().inflate(R.menu.fun_textstream_menu, menu);
        return super.onCreateOptionsMenu(menu);
    }

    @Override
    public boolean onOptionsItemSelected(MenuItem item) {
        if (item.getItemId() == R.id.clear) {
            stringToTextView = "";
            textViewOfRecive.setText(stringToTextView);
        }
        return super.onOptionsItemSelected(item);
    }

    public class SendButtonListener implements View.OnClickListener {

        @Override
        public void onClick(View v) {
            if (bluetoothData.getConnectState() != BluetoothAdapter.STATE_CONNECTED) {
                Toast.makeText(getApplicationContext(), "没有连接到蓝牙", Toast.LENGTH_SHORT).show();
            } else {
                EditText editText = (EditText) findViewById(R.id.editText2);
                if (editText.length() != 0) {
                    String input = "";
                    input = editText.getText().toString();
                    Log.e("bl", "得到文本:" + input);
//                    if (textSendHandler!=null){
//                        Message msg=textSendHandler.obtainMessage();
//                        byte[] bytes=input.getBytes();
//                        msg.obj=bytes;
//                        textSendHandler.sendMessage(msg);
//                    }
                    byte[] bytes=input.getBytes();
                    OutputStream blOutStream;
                    try {
                        blOutStream=bluetoothData.getBlSocket().getOutputStream();
                        blOutStream.write(bytes);
                    } catch (IOException e) {
                        e.printStackTrace();
                    }
                }
            }
        }
    }

    @Override
    protected void onStop() {
        if (bluetoothConnetThread!=null) {
            bluetoothConnetThread.user_destroy();
        }
        super.onStop();
    }

    private class BluetoothConnetThread extends Thread {

        private final BluetoothSocket blSocket;
        private final InputStream blInStream;
        private final OutputStream blOutStream;
        private int destroyflag = 0;


        private BluetoothConnetThread(BluetoothSocket blSocket) {
            this.blSocket = blSocket;
            InputStream temin = null;
            OutputStream temout = null;
            try {
                temin = blSocket.getInputStream();
                temout = blSocket.getOutputStream();
            } catch (Exception e) {
            }
            blInStream = temin;
            blOutStream = temout;
        }

        @Override
        public void run() {
//            textSendHandler=new Handler(){
//                @Override
//                public void handleMessage(Message msg) {
//                    super.handleMessage(msg);
//                    byte[] bytes= (byte[]) msg.obj;
//                    write(bytes);
//                }
//            };
            while ((bluetoothData.getConnectState() == BluetoothAdapter.STATE_CONNECTED) && (destroyflag == 0)) {
                try {
                    Thread.sleep(10);//每20ms更新一次ui,不然会太卡
                } catch (InterruptedException e) {
                    e.printStackTrace();
                }

                try {
                    //这段接受蓝牙数据
                    int count = 0;
                    count = blInStream.available();
                    if (count != 0) {
                        byte[] bytes = new byte[count];
                        int readCount = 0; // 已经成功读取的字节的个数
                        while (readCount < count) {
                            readCount += blInStream.read(bytes, readCount, count - readCount);
                        }
                        String stringToBeSent=new String(bytes);
                        //String stringToBeSent = tem.replaceAll("\\r", " ");
                        //这个不管用啊
                        Log.e("bl", "接收到: " + count + " 个字节的数据" + stringToBeSent);
                        Message msg = textDealHandler.obtainMessage();
                        msg.obj = stringToBeSent;
                        textDealHandler.sendMessage(msg);
                    }

                } catch (Exception e1) {
                }

            }
            //super.run();
        }

        public void write(byte[] bytes) {
            try {
                blOutStream.write(bytes);
            } catch (IOException e) {
                e.printStackTrace();
            }
        }

        public void user_destroy() {
            this.destroyflag = 1;
        }
    }

    public static String bytesToHexString(byte[] bytes) {
        String result = "";
        for (int i = 0; i < bytes.length; i++) {
            String hexString = Integer.toHexString(bytes[i] & 0xFF);
            if (hexString.length() == 1) {
                hexString = '0' + hexString;
            }
            result += hexString.toUpperCase();
        }
        return result;
    }

    private byte[] getHexBytes(String message) {
        int len = message.length() / 2;
        char[] chars = message.toCharArray();
        String[] hexStr = new String[len];
        byte[] bytes = new byte[len];
        for (int i = 0, j = 0; j < len; i += 2, j++) {
            hexStr[j] = "" + chars[i] + chars[i + 1];
            bytes[j] = (byte) Integer.parseInt(hexStr[j], 16);
        }
        return bytes;
    }
}