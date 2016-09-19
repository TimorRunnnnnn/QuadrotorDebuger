package com.t.as.jizhi3;

import android.app.Application;
import android.bluetooth.BluetoothAdapter;
import android.bluetooth.BluetoothSocket;
import android.os.Handler;
import android.os.Message;
import android.util.Log;
import android.widget.Toast;

import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.util.ArrayList;
import java.util.List;

/**
 * Created by as on 2015/11/8.
 */
public class Data extends Application {
    final int SHAKEHAND_OK = 1;
    final int SHAKEHAND_NONE = 0;

    int connectState = 999;
    String remoteName;
    String remoteAddress;
    BluetoothSocket blSocket;
    int shakeHandState = 0;

    int gyroCalTime = 0;

    List<Fun_Parameter.ParameterProperty> parameterList = new ArrayList<Fun_Parameter.ParameterProperty>();

    public void setShakeHandState(int state) {
        this.shakeHandState = state;
    }

    public int getShakeHandState() {
        return this.shakeHandState;
    }

    public BluetoothSocket getBlSocket() {
        return blSocket;
    }

    public void setBlSocket(BluetoothSocket blSocket) {
        this.blSocket = blSocket;
    }

    public String getRemoteName() {
        return remoteName;
    }

    public String getRemoteAddress() {
        return remoteAddress;
    }

    public int getConnectState() {
        return connectState;
    }

    public void setConnectState(int connectState) {
        this.connectState = connectState;
    }

    public void setRemoteName(String remoteName) {
        this.remoteName = remoteName;
    }

    public void setRemoteAddress(String remoteAddress) {
        this.remoteAddress = remoteAddress;
    }

    public void sendRequest(int request,int data) {
        if (getConnectState()!=BluetoothAdapter.STATE_CONNECTED){
            return;
        }
        try {
            while (this.blSocket.getInputStream().available()!=0){
                blSocket.getInputStream().skip(blSocket.getInputStream().available());
            }
        } catch (IOException e) {
            e.printStackTrace();
        }
        byte buffer[] = new byte[15];
        buffer[0] = HEAD_FIRST;
        buffer[1] = HEAD_SECOND;
        // buffer[2] = 0;
        //buffer[3] = 14;
        buffer[4] = (byte) request;
        byte bytes[]=this.int2byte(data);
        for (int i=0;i<4;i++){
            buffer[5+i]=bytes[i];
        }
//        buffer[5] = 0;
//        buffer[6] = 0;
//        buffer[7] = 0;
//        buffer[8] = 0;
//        CRC32 crc32 = new CRC32();
//        crc32.update(buffer, 2, 4);
//        byte[] tem = int2byte((int) crc32.getValue());
        int checkSum = 0;
        for (int i = 5; i < 9; i++) {
            checkSum += buffer[i];
        }
        byte tem[] = int2byte(checkSum);
        for (int i = 0; i < 4; i++) {
            buffer[9 + i] = tem[i];
        }
        buffer[13] = TAIL_FIRST;
        buffer[14] = TAIL_SECOND;

         bytes = short2byte((short) buffer.length);
        buffer[2] = bytes[0];
        buffer[3] = bytes[1];
        if (getConnectState() == BluetoothAdapter.STATE_CONNECTED) {
            try {
                OutputStream outputStream = blSocket.getOutputStream();
                outputStream.write(buffer);
                outputStream.flush();
            } catch (Exception e) {
                e.printStackTrace();
            }
        } else {
            Toast.makeText(getApplicationContext(), getString(R.string.bluetooth_noconnec_toast), Toast.LENGTH_SHORT).show();
        }
    }

    public void reciveData(final Handler handler, final Data bldata, final byte respond, final int timeout) {
        if (getConnectState()!=BluetoothAdapter.STATE_CONNECTED){
            return;
        }
        new Thread(new Runnable() {
            @Override
            public void run() {
                try {
                   // Thread.sleep(100);//休眠200ms,115200波特率下可以发送2KB数据,接受这200ms内的所有数据
                    InputStream inputStream = bldata.blSocket.getInputStream();
                    int times=0;
                    while(inputStream.available()==0){
                        Thread.sleep(50);
                        times+=50;
                        if (times>timeout){
                            break;
                        }
                    }
                    int count = inputStream.available();
                    if (count == 0) {
                        Message msg = handler.obtainMessage();
                        msg.what = bldata.ERROR_TIMEOUT;
                        handler.sendMessage(msg);
                        return;
                    } else {
                        while(true){
                            Thread.sleep(10);
                            if (count!=inputStream.available()){
                                count=inputStream.available();
                            }else{
                                //直到没有新数据进来的时候
                                break;
                            }
                        }
                        byte bytes[] = new byte[count];
                        int readbyte = 0;
                        ArrayList<Byte> reciveBuffer = new ArrayList<Byte>();
                        while (readbyte < count) {
                            readbyte += inputStream.read(bytes, readbyte, count - readbyte);
                        }
                        int frameStart = -1, frameEnd = -1;
                        for (int i = 0; i < readbyte - 1; i++) {
                            if (frameStart == -1) {
                                if (bytes[i] == bldata.HEAD_FIRST && bytes[i + 1] == bldata.HEAD_SECOND) {
                                    frameStart = i;
                                }
                            }
                            if (frameStart != -1) {
                                //只在帧头后面找帧尾
                                if (bytes[i] == bldata.TAIL_FIRST && bytes[i + 1] == bldata.TAIL_SECOND) {
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
                            Message msg = handler.obtainMessage();
                            msg.what = bldata.ERROR_FRAMEERROR;
                            handler.sendMessage(msg);
                            return;
                        }
                        for (int i = frameStart; i <= frameEnd; i++) {
                            reciveBuffer.add(bytes[i]);
                        }
                        Integer frameLen = 0;
                        frameLen += (reciveBuffer.get(2).shortValue() + reciveBuffer.get(3).shortValue() * 16);//stm32是小端
                        //Log.e("bl", "接收到的帧长:" + frameLen);
                        if (frameLen != reciveBuffer.size() || ((frameLen - 11) % 4 != 0)) {
                            Message msg = handler.obtainMessage();
                            msg.what = bldata.ERROR_FRAMEERROR;
                            handler.sendMessage(msg);
                        }

                        int checkSum = 0;
                        for (int i = 5; i < reciveBuffer.size() - 6; i++) {
                            checkSum += reciveBuffer.get(i);
                        }

                        byte crc_b[] = new byte[4];
                        for (int i = 0; i < 4; i++) {
                            crc_b[i] = reciveBuffer.get(reciveBuffer.size() - 6 + i);
                        }
                        long crcRecive = bldata.byte2int(crc_b);
                        if (checkSum != crcRecive) {
                            Message msg = handler.obtainMessage();
                            msg.what = bldata.ERROR_CRCFAILED;
                            handler.sendMessage(msg);
                            return;
                        }if (reciveBuffer.get(4) != (respond)) {
                            Message msg = handler.obtainMessage();
                            msg.what = bldata.ERROR_FRAMEERROR;
                            handler.sendMessage(msg);
                        } else {
                            Message msg =handler.obtainMessage();
                            msg.what=bldata.ERROR_NOERROR;
                            //移除除数据包以外的其他数据
                            for (int i=0;i<5;i++){
                                reciveBuffer.remove(reciveBuffer.size()-1);
                                reciveBuffer.remove(0);
                            }
                            reciveBuffer.remove(reciveBuffer.size()-1);
                            msg.obj=reciveBuffer;
                            handler.sendMessage(msg);
                        }
                    }
                }catch (Exception e){
                    Log.e("bl","error....!!!");
                }
            }
        }).start();
    }


    public byte[] int2byte(int res) {
        byte[] targets = new byte[4];

        targets[0] = (byte) (res & 0xff);// 最低位
        targets[1] = (byte) ((res >> 8) & 0xff);// 次低位
        targets[2] = (byte) ((res >> 16) & 0xff);// 次高位
        targets[3] = (byte) (res >>> 24);// 最高位,无符号右移。
        return targets;
    }

    public byte[] short2byte(short res) {
        byte[] targets = new byte[2];
        targets[0] = (byte) (res & 0xff);// 最低位
        targets[1] = (byte) ((res >> 8) & 0xff);// 次低位
        return targets;
    }
    public int byte2int(byte[] res) {
// 一个byte数据左移24位变成0x??000000，再右移8位变成0x00??0000

        int targets = (res[0] & 0xff) | ((res[1] << 8) & 0xff00) // | 表示安位或
                              | ((res[2] << 24) >>> 8) | (res[3] << 24);
        return targets;
    }

    public double byte2double(byte[] b, int index) {
        int l;
        l = b[index + 0];
        l &= 0xff;
        l |= ((long) b[index + 1] << 8);
        l &= 0xffff;
        l |= ((long) b[index + 2] << 16);
        l &= 0xffffff;
        l |= ((long) b[index + 3] << 24);

        Double returnValue = (double) (Float.intBitsToFloat(l));

        if (returnValue.isNaN()==false) {
        /*只保留一位小数点,并且四舍五入*/
            String tem = new String(returnValue.toString());
            int x = tem.indexOf(".") + 2;
            if ((tem.length() > (x + 3)) && (tem.charAt(x + 1) == '9')) {
                // returnValue += (float) 0.001;
                if (returnValue > 0) {
                    returnValue += 0.001;
                } else if (returnValue < 0) {
                    returnValue -= 0.001;
                }
                tem = new Float(returnValue).toString();
            }
            String tem2 = tem.substring(0, x);
            returnValue = new Double(tem2).doubleValue();
            return returnValue;
        }else{
            return 999;
        }
    }

    public final byte REQUEST_PARAMETER = (byte) 0x01;
    public final byte RESPOND_PARAMETER = (byte) 0x11;
    public final byte REQUEST_UPDATAPARAMETER = (byte) 0x02;
    public final byte RESPOND_UPDATAPARAMETER = (byte) 0x12;
    public final byte REQUEST_GYRO_CAL = (byte) 0x03;
    public final byte RESPOND_GYRO_CAL = (byte) 0x13;
    public final byte REQUEST_MAG_CAL = (byte) 0x04;
    public final byte RESPOND_MAG_CAL = (byte) 0x14;
    public final byte REQUEST_GYRO_AND_MAG_OFFSET = (byte) 0x05;
    public final byte RESPOND_GYRO_AND_MAG_OFFSET = (byte) 0x15;

    public final byte SHAKE_HAND = (byte) 0x00;

    public final byte HEAD_FIRST = (byte) 0xAA;
    public final byte TAIL_SECOND = (byte) 0xAA;
    public final byte HEAD_SECOND = (byte) 0xBB;
    public final byte TAIL_FIRST = (byte) 0xBB;


    public final int ERROR_FRAMEERROR = 1;
    public final int ERROR_CRCFAILED = 2;
    public final int ERROR_NODATA = 3;
    public final int ERROR_TIMEOUT = 4;
    public final int ERROR_NOERROR = 0;

}
