package com.example.wise_bluetooth_print.blueprint;

import static android.hardware.usb.UsbManager.ACTION_USB_DEVICE_DETACHED;

import android.content.BroadcastReceiver;
import android.content.Context;
import android.content.Intent;
import android.content.IntentFilter;
import android.hardware.usb.UsbDevice;
import android.os.Bundle;
import android.os.Handler;
import android.os.HandlerThread;
import android.os.Message;
import android.util.Log;

import com.gprinter.io.BluetoothPort;
import com.gprinter.io.EthernetPort;
import com.gprinter.io.PortManager;
import com.gprinter.io.SerialPort;
import com.gprinter.io.UsbPort;

import java.io.IOException;
import java.util.Vector;
import java.util.concurrent.ScheduledExecutorService;
import java.util.concurrent.ScheduledThreadPoolExecutor;
import java.util.concurrent.TimeUnit;

/**
 * Created by Administrator
 *
 * @author 猿史森林
 *         Time 2017/8/2
 */
public class GPDeviceConnFactoryManager {

    private PortManager mPort;

    private static final String TAG = GPDeviceConnFactoryManager.class.getSimpleName();

    private CONN_METHOD connMethod;

    private String ip;

    private int port;

    private String macAddress;

    private UsbDevice mUsbDevice;

    private String mName;

    private Context mContext;

    private String serialPortPath;

    private int baudrate;

    private int id;

    private static GPDeviceConnFactoryManager[] deviceConnFactoryManagers = new GPDeviceConnFactoryManager[4];

    private boolean isOpenPort;
    /**
     * ESC查询打印机实时状态指令
     */
    private byte[] esc = {0x10, 0x04, 0x02};

    /**
     * ESC查询打印机实时状态 缺纸状态
     */
    private static final int ESC_STATE_PAPER_ERR = 0x20;

    /**
     * ESC指令查询打印机实时状态 打印机开盖状态
     */
    private static final int ESC_STATE_COVER_OPEN = 0x04;

    /**
     * ESC指令查询打印机实时状态 打印机报错状态
     */
    private static final int ESC_STATE_ERR_OCCURS = 0x40;

    /**
     * TSC查询打印机状态指令
     */
    private byte[] tsc = {0x1b, '!', '?'};

    /**
     * TSC指令查询打印机实时状态 打印机缺纸状态
     */
    private static final int TSC_STATE_PAPER_ERR = 0x04;

    /**
     * TSC指令查询打印机实时状态 打印机开盖状态
     */
    private static final int TSC_STATE_COVER_OPEN = 0x01;

    /**
     * TSC指令查询打印机实时状态 打印机出错状态
     */
    private static final int TSC_STATE_ERR_OCCURS = 0x80;
    private byte[] sendCommand;
    /**
     * 判断打印机所使用指令是否是ESC指令
     */
    private GPPrinterCommand currentPrinterCommand;
    public static final byte FLAG = 0x10;
    private static final int READ_DATA = 10000;
    private static final String READ_DATA_CNT = "read_data_cnt";
    private static final String READ_BUFFER_ARRAY = "read_buffer_array";
    public static final String ACTION_CONN_STATE = "action_connect_state";
    public static final String ACTION_QUERY_PRINTER_STATE = "action_query_printer_state";
    public static final String STATE = "state";
    public static final String DEVICE_ID = "id";
    public static final int CONN_STATE_DISCONNECT = 0x90;
    public static final int CONN_STATE_CONNECTING = CONN_STATE_DISCONNECT << 1;
    public static final int CONN_STATE_FAILED = CONN_STATE_DISCONNECT << 2;
    public static final int CONN_STATE_CONNECTED = CONN_STATE_DISCONNECT << 3;
    private PrinterReader reader;

    public enum CONN_METHOD {
        //蓝牙连接
        BLUETOOTH("BLUETOOTH"),
        //USB连接
        USB("USB"),
        //wifi连接
        WIFI("WIFI"),
        //串口连接
        SERIAL_PORT("SERIAL_PORT");

        private String name;

        private CONN_METHOD(String name) {
            this.name = name;
        }

        @Override
        public String toString() {
            return this.name;
        }
    }

    public static GPDeviceConnFactoryManager[] getDeviceConnFactoryManagers() {
        return deviceConnFactoryManagers;
    }

    /**
     * 打开端口
     *
     * @return
     */
    public void openPort() {
        deviceConnFactoryManagers[id].isOpenPort = false;
        sendStateBroadcast(CONN_STATE_CONNECTING);
        switch (deviceConnFactoryManagers[id].connMethod) {
            case BLUETOOTH:
                System.out.println("id -> " + id);
                mPort = new BluetoothPort(macAddress);
                isOpenPort = deviceConnFactoryManagers[id].mPort.openPort();

                break;
            case USB:
                mPort = new UsbPort(mContext, mUsbDevice);
                isOpenPort = mPort.openPort();
                if (isOpenPort) {
                    IntentFilter filter = new IntentFilter(ACTION_USB_DEVICE_DETACHED);
                    mContext.registerReceiver(usbStateReceiver, filter);
                }
                break;
            case WIFI:
                mPort = new EthernetPort(ip, port);
                isOpenPort = mPort.openPort();
                break;
            case SERIAL_PORT:
                mPort = new SerialPort(serialPortPath, baudrate, 0);
                isOpenPort = mPort.openPort();
                break;
            default:
                break;
        }
        //端口打开成功后，检查连接打印机所使用的打印机指令ESC、TSC
        if (isOpenPort) {
            queryCommand();
        } else {
            sendStateBroadcast(CONN_STATE_FAILED);
        }
    }

    /**
     * 查询当前连接打印机所使用打印机指令（ESC（EscCommand.java）、TSC（LabelCommand.java））
     */
    private void queryCommand() {
        //开启读取打印机返回数据线程
        reader = new PrinterReader();
        reader.start();

        if (mHandler == null) {
            mHandler = new Handler(reader.getLooper()) {
                @Override
                public void handleMessage(Message msg) {
                    switch (msg.what) {
                        case READ_DATA:
                            int cnt = msg.getData().getInt(READ_DATA_CNT);
                            byte[] buffer = msg.getData().getByteArray(READ_BUFFER_ARRAY);
                            //这里只对查询状态返回值做处理，其它返回值可参考编程手册来解析
                            if (buffer == null) {
                                return;
                            }
                            int result = judgeResponseType(buffer[0]);
                            String status = mName; //String.format(mContext.getString(R.string.str_printer_conn_normal), mName);
                            if (sendCommand == esc) {
                                //设置当前打印机模式为ESC模式
                                if (currentPrinterCommand == null) {
                                    currentPrinterCommand = GPPrinterCommand.ESC;
                                    sendStateBroadcast(CONN_STATE_CONNECTED);
                                } else {//查询打印机状态
                                    if (result == 0) {//打印机状态查询
                                        Intent intent = new Intent(ACTION_QUERY_PRINTER_STATE);
                                        intent.putExtra(DEVICE_ID, id);
                                        mContext.sendBroadcast(intent);
                                    } else if (result == 1) {//查询打印机实时状态
                                        boolean hasProblem = false;
                                        if ((buffer[0] & ESC_STATE_PAPER_ERR) > 0) {
                                            hasProblem = true;
                                            status += " - " + "Runout of paper";
                                        }
                                        if ((buffer[0] & ESC_STATE_COVER_OPEN) > 0) {
                                            hasProblem = true;
                                            status += " - " + "Open cover printer";
                                        }
                                        if ((buffer[0] & ESC_STATE_ERR_OCCURS) > 0) {
                                            hasProblem = true;
                                            status += " - " + "Printer error";
                                        }
                                        System.out.println("State: " + status);
                                        if (hasProblem)
                                            GPUtils.toast(mContext, status);
                                    }
                                }
                            } else if (sendCommand == tsc) {
                                //设置当前打印机模式为TSC模式
                                if (currentPrinterCommand == null) {
                                    currentPrinterCommand = GPPrinterCommand.TSC;
                                    sendStateBroadcast(CONN_STATE_CONNECTED);
                                } else {
                                    if (cnt == 1) {//查询打印机实时状态
                                        if ((buffer[0] & TSC_STATE_PAPER_ERR) > 0) {//缺纸
                                            status += " " + "Run out paper";
                                        }
                                        if ((buffer[0] & TSC_STATE_COVER_OPEN) > 0) {//开盖
                                            status += " " + "Open printer cover";
                                        }
                                        if ((buffer[0] & TSC_STATE_ERR_OCCURS) > 0) {//打印机报错
                                            status += " " + "Printer error";
                                        }
                                        System.out.println("State: " + status);
                                        GPUtils.toast(mContext, status);
                                    } else {//打印机状态查询
                                        Intent intent = new Intent(ACTION_QUERY_PRINTER_STATE);
                                        intent.putExtra(DEVICE_ID, id);
                                        mContext.sendBroadcast(intent);
                                    }
                                }
                            }
                            break;
                        default:
                            break;
                    }
                }
            };
        }
        //查询打印机所使用指令
        queryPrinterCommand();
    }

    /**
     * 获取端口连接方式
     *
     * @return
     */
    public CONN_METHOD getConnMethod() {
        return connMethod;
    }

    /**
     * 获取端口打开状态（true 打开，false 未打开）
     *
     * @return
     */
    public boolean getConnState() {
        return isOpenPort;
    }

    /**
     * 获取连接蓝牙的物理地址
     *
     * @return
     */
    public String getMacAddress() {
        return macAddress;
    }

    /**
     * 获取连接网口端口号
     *
     * @return
     */
    public int getPort() {
        return port;
    }

    /**
     * 获取连接网口的IP
     *
     * @return
     */
    public String getIp() {
        return ip;
    }

    /**
     * 获取连接的USB设备信息
     *
     * @return
     */
    public UsbDevice usbDevice() {
        return mUsbDevice;
    }

    /**
     * 关闭端口
     */
    public void closePort(int id) {
        try {
            if (this.mPort != null) {
                System.out.println("id -> " + id);
                this.mPort.closePort();
                isOpenPort = false;
                currentPrinterCommand = null;
            }
            sendStateBroadcast(CONN_STATE_DISCONNECT);
        }
        catch (Exception e){
            e.printStackTrace();
        }
    }

    /**
     * 获取串口号
     *
     * @return
     */
    public String getSerialPortPath() {
        return serialPortPath;
    }

    /**
     * 获取波特率
     *
     * @return
     */
    public int getBaudrate() {
        return baudrate;
    }

    public static void closeAllPort() {
        try {
            for (GPDeviceConnFactoryManager deviceConnFactoryManager : deviceConnFactoryManagers) {
                if (deviceConnFactoryManager != null) {
                    Log.e(TAG, "cloaseAllPort() id -> " + deviceConnFactoryManager.id);
                    deviceConnFactoryManager.closePort(deviceConnFactoryManager.id);
                    deviceConnFactoryManagers[deviceConnFactoryManager.id] = null;
                }
            }
        }
        catch (Exception e){
            e.printStackTrace();
        }
    }

    private GPDeviceConnFactoryManager(Build build) {
        this.connMethod = build.connMethod;
        this.macAddress = build.macAddress;
        this.port = build.port;
        this.ip = build.ip;
        this.mUsbDevice = build.usbDevice;
        this.mContext = build.context;
        this.mName = build.name;
        this.serialPortPath = build.serialPortPath;
        this.baudrate = build.baudrate;
        this.id = build.id;
        deviceConnFactoryManagers[id] = this;
    }

    /**
     * 获取当前打印机指令
     *
     * @return PrinterCommand
     */
    public GPPrinterCommand getCurrentPrinterCommand() {
        return deviceConnFactoryManagers[id].currentPrinterCommand;
    }

    public static final class Build {
        private String ip;
        private String macAddress;
        private UsbDevice usbDevice;
        private int port;
        private String name;
        private CONN_METHOD connMethod;
        private Context context;
        private String serialPortPath;
        private int baudrate;
        private int id;

        public Build setIp(String ip) {
            this.ip = ip;
            return this;
        }

        public Build setMacAddress(String macAddress) {
            this.macAddress = macAddress;
            return this;
        }

        public Build setUsbDevice(UsbDevice usbDevice) {
            this.usbDevice = usbDevice;
            return this;
        }

        public Build setPort(int port) {
            this.port = port;
            return this;
        }

        public Build setName(String name) {
            this.name = name;
            return this;
        }

        public Build setConnMethod(CONN_METHOD connMethod) {
            this.connMethod = connMethod;
            return this;
        }

        public Build setContext(Context context) {
            this.context = context;
            return this;
        }

        public Build setId(int id) {
            this.id = id;
            return this;
        }

        public Build setSerialPort(String serialPortPath) {
            this.serialPortPath = serialPortPath;
            return this;
        }

        public Build setBaudrate(int baudrate) {
            this.baudrate = baudrate;
            return this;
        }

        public GPDeviceConnFactoryManager build() {
            return new GPDeviceConnFactoryManager(this);
        }
    }

    public boolean sendDataImmediately(final Vector<Byte> data) {
        if (this.mPort == null) {
            return false;
        }
        try {
            //Log.e(TAG, "data -> " + new String(com.gprinter.utils.Utils.convertVectorByteTobytes(data), "gb2312"));
            this.mPort.writeDataImmediately(data, 0, data.size());
            return true;
        } catch (IOException e) {
            e.printStackTrace();
            return false;
        }
    }

    public int readDataImmediately(byte[] buffer) throws IOException {
        return this.mPort.readData(buffer);
    }

    /**
     * 查询打印机当前使用的指令（TSC、ESC）
     */
    private void queryPrinterCommand() {
        GPThreadPool.getInstantiation().addTask(new Runnable() {
            @Override
            public void run() {
                //发送ESC查询打印机状态指令
                sendCommand = esc;
                Vector<Byte> data = new Vector<>(esc.length);
                for (int i = 0; i < esc.length; i++) {
                    data.add(esc[i]);
                }
                sendDataImmediately(data);
                //开启计时器，隔2000毫秒没有没返回值时发送TSC查询打印机状态指令
                final GPThreadFactoryBuilder threadFactoryBuilder = new GPThreadFactoryBuilder("Timer");
                final ScheduledExecutorService scheduledExecutorService = new ScheduledThreadPoolExecutor(1, threadFactoryBuilder);
                scheduledExecutorService.schedule(threadFactoryBuilder.newThread(new Runnable() {
                    @Override
                    public void run() {
                        if (currentPrinterCommand == null || currentPrinterCommand != GPPrinterCommand.ESC) {
                            Log.e(TAG, Thread.currentThread().getName());
                            //发送TSC查询打印机状态指令
                            sendCommand = tsc;
                            Vector<Byte> data = new Vector<>(tsc.length);
                            for (int i = 0; i < tsc.length; i++) {
                                data.add(tsc[i]);
                            }
                            sendDataImmediately(data);
                            //开启计时器，隔2000毫秒打印机没有响应者停止读取打印机数据线程并且关闭端口
                            scheduledExecutorService.schedule(threadFactoryBuilder.newThread(new Runnable() {
                                @Override
                                public void run() {
                                    if (currentPrinterCommand == null) {
                                        if (reader != null) {
                                            reader.cancel();
                                            mPort.closePort();
                                            isOpenPort = false;
                                            sendStateBroadcast(CONN_STATE_FAILED);
                                        }
                                    }
                                }
                            }), 2000, TimeUnit.MILLISECONDS);
                        }
                    }
                }), 2000, TimeUnit.MILLISECONDS);
            }
        });
    }

    class PrinterReader extends HandlerThread {
        private boolean isRun = false;

        private byte[] buffer = new byte[100];

        public PrinterReader() {
            super("olserav3gprintthread");
            isRun = true;
        }

        public PrinterReader(String name, int priority) {
            super(name, priority);
            isRun = true;
        }

        @Override
        public void run() {
            try {
                while (isRun) {
                    //读取打印机返回信息
                    int len = readDataImmediately(buffer);
                    if (len > 0) {
                        Message message = Message.obtain();
                        message.what = READ_DATA;
                        Bundle bundle = new Bundle();
                        bundle.putInt(READ_DATA_CNT, len);
                        bundle.putByteArray(READ_BUFFER_ARRAY, buffer);
                        message.setData(bundle);
                        mHandler.sendMessage(message);
                    }
                }
            } catch (Exception e) {
                if (deviceConnFactoryManagers[id] != null) {
                    closePort(id);
                }
            }
        }

        public void cancel() {
            isRun = false;
        }
    }

    private Handler mHandler;

    private void sendStateBroadcast(int state) {
        Intent intent = new Intent(ACTION_CONN_STATE);
        intent.putExtra(STATE, state);
        intent.putExtra(DEVICE_ID, id);
        mContext.sendBroadcast(intent);
    }

    /**
     * 判断是实时状态（10 04 02）还是查询状态（1D 72 01）
     */
    private int judgeResponseType(byte r) {
        return (byte) ((r & FLAG) >> 4);
    }

    BroadcastReceiver usbStateReceiver = new BroadcastReceiver() {
        @Override
        public void onReceive(Context context, Intent intent) {
            String action = intent.getAction();
            switch (action) {
                case ACTION_USB_DEVICE_DETACHED:
                    sendStateBroadcast(CONN_STATE_DISCONNECT);
                    break;
                default:
                    break;
            }
        }
    };

}