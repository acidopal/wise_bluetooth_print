package com.inkanteen.print;

import android.app.Activity;
import android.bluetooth.BluetoothAdapter;
import android.bluetooth.BluetoothDevice;
import android.content.Context;
import android.graphics.Bitmap;
import android.os.ParcelUuid;

import androidx.annotation.NonNull;

import com.bumptech.glide.Glide;
import com.bumptech.glide.load.resource.bitmap.DownsampleStrategy;
import com.bumptech.glide.request.RequestOptions;
import com.inkanteen.print.blueprint.DeviceConnFactoryManager;
import com.inkanteen.print.blueprint.ThreadPool;
import com.gprinter.command.EscCommand;
import com.inkanteen.print.panda.printerlibs_caysnpos;
import com.sun.jna.Pointer;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.Map;
import java.util.Map.Entry;
import java.util.Set;
import java.util.UUID;
import android.util.Log;

import io.flutter.embedding.engine.plugins.FlutterPlugin;
import io.flutter.plugin.common.MethodCall;
import io.flutter.plugin.common.MethodChannel;
import io.flutter.plugin.common.MethodChannel.MethodCallHandler;
import io.flutter.plugin.common.MethodChannel.Result;
import io.flutter.plugin.common.EventChannel;
import io.flutter.plugin.common.EventChannel.EventSink;
import io.flutter.plugin.common.EventChannel.StreamHandler;
import android.content.Intent;
import android.content.IntentFilter;

import android.graphics.BitmapFactory;
import android.util.Base64;

import android.content.BroadcastReceiver;

public class WiseBluetoothPrintPlugin implements FlutterPlugin, MethodCallHandler {
    private static final String TAG = "WiseBluetoothPrintPlugin";
    private MethodChannel channel;
    private EventChannel stateChannel;
    private Activity activity;

    private Context context;
    private ThreadPool threadPool;
    
    private Pointer pandaPointer;

    @Override
    public void onAttachedToEngine(@NonNull FlutterPluginBinding flutterPluginBinding) {
        this.context = flutterPluginBinding.getApplicationContext();
        this.channel = new MethodChannel(flutterPluginBinding.getBinaryMessenger(), "wise_bluetooth_print");
        this.stateChannel = new EventChannel(flutterPluginBinding.getBinaryMessenger(), "wise_bluetooth_print/state");
        channel.setMethodCallHandler(this);
        stateChannel.setStreamHandler(stateStreamHandler);
    }

    @Override
    public void onMethodCall(@NonNull MethodCall call, @NonNull Result result) {
        switch (call.method) {
            case "getPlatformVersion":
                result.success("Android " + android.os.Build.VERSION.RELEASE);
                break;
            case "getPairedDevices": {
                getPairedDevices(result);
                break;
            }

            case "connectBluePrint": {
                String address = call.argument("address");
                int indexPrint = call.argument("index_print");
                connectBluePrint(address, indexPrint, result);
                break;
            }
            case "printBluePrint": {
                String content = call.argument("content");
                int indexPrint = call.argument("index_print");
                String imageUrl = call.argument("imageUrl");
                printBluePrint(content, indexPrint, imageUrl, result);
                break;
            }
            case "disconnectBluePrint": {
                int indexPrint = call.argument("index_print");
                disconnectBluePrint(indexPrint, result);
                break;
            }
            case "connectPanda": {
                String address = call.argument("address");
                connectPanda(address, result);
                break;
            }
            case "printPanda": {
                String address = call.argument("address");
                String content = call.argument("content");
                String imageUrl = call.argument("imageUrl");
                printPanda(address, content, imageUrl, result);
                break;
            }
            case "disconnectPanda": {
                String address = call.argument("address");
                disconnectPanda(address, result);
                break;
            }

            default:
                result.notImplemented();
                break;
        }
    }

    private void getPairedDevices(Result result) {
        ArrayList<String> deviceInfoList = new ArrayList<>();
        BluetoothAdapter bluetooth = BluetoothAdapter.getDefaultAdapter();
        if (bluetooth != null) {
            if (bluetooth.isEnabled()) {
                Set<BluetoothDevice> pairedDevices = bluetooth.getBondedDevices();

                if (pairedDevices.size() > 0) {
                    for (BluetoothDevice device : pairedDevices) {
                        String deviceName = device.getName();
                        String deviceHardwareAddress = device.getAddress();
                        device.fetchUuidsWithSdp();
                        ParcelUuid[] uuids = device.getUuids();
                        UUID socket = uuids[0].getUuid();

                        deviceInfoList.add(deviceName);
                        deviceInfoList.add(deviceHardwareAddress);
                        deviceInfoList.add(socket.toString());
                    }
                }
            }
        }
        bluetooth.cancelDiscovery();
        result.success(deviceInfoList);
    }

    private void connectBluePrint(String address, int indexPrint, @NonNull Result result) {

        System.out.println("=============");
        System.out.println("connectBluePrint");
        System.out.println(address);
        System.out.println(indexPrint);
        System.out.println("=============");
        
        new DeviceConnFactoryManager.Build()
                .setId(indexPrint)
                .setConnMethod(DeviceConnFactoryManager.CONN_METHOD.BLUETOOTH)
                .setMacAddress(address)
                .build();

        threadPool = ThreadPool.getInstantiation();
        threadPool.addParallelTask(new Runnable() {
            @Override
            public void run() {
                DeviceConnFactoryManager.getDeviceConnFactoryManagers()[indexPrint].openPort();
            }
        });

        result.success(true);
    }

    private void printBluePrint(String content, int indexPrint, String imageUrl, Result result) {
      threadPool = ThreadPool.getInstantiation();
      threadPool.addParallelTask(new Runnable() {
            @Override
            public void run() {
                System.out.println("=============");
                System.out.println("content");
                System.out.println(content);
                System.out.println(indexPrint);
                System.out.println("=============");

                EscCommand esc = new EscCommand();
                esc.addInitializePrinter();
                esc.addSelectJustification(EscCommand.JUSTIFICATION.LEFT);
                esc.addText(content + "\n");
                esc.addSelectJustification(EscCommand.JUSTIFICATION.CENTER);
                esc.addText("PRINTING IMAGE\n");

                 if (imageUrl != null) {
                    try {
                        byte[] imageData = Base64.decode(imageUrl, Base64.DEFAULT);
                        Bitmap bitmap = BitmapFactory.decodeByteArray(imageData, 0, imageData.length);

                        if (bitmap != null) {
                            int width = bitmap.getWidth();
                            int height = bitmap.getHeight();
                            int page_width = 384;
                            int dstw = width;
                            int dsth = height;
                            if (dstw > page_width) {
                                dstw = page_width;
                                dsth = (int) (dstw * ((double) height / width));
                            }

                            esc.addSelectJustification(EscCommand.JUSTIFICATION.CENTER);
                            esc.addRastBitImage(bitmap, dstw, dsth);
                        }
                    } catch (Exception e) {
                        e.printStackTrace();
                        esc.addText("FAILED PRINTING IMAGE\nerrormessage : " + e.getMessage());
                    }
                }

                if (DeviceConnFactoryManager.getDeviceConnFactoryManagers()[indexPrint] == null ||
                        !DeviceConnFactoryManager.getDeviceConnFactoryManagers()[indexPrint].getConnState()) {
                    System.out.println("=============");
                    System.out.println("Device Undefined");
                    System.out.println("=============");
                    result.success(false);
                }else{
                    System.out.println("=============");
                    System.out.println("On Printing");
                    System.out.println("=============");
                    DeviceConnFactoryManager.getDeviceConnFactoryManagers()[indexPrint].sendDataImmediately(esc.getCommand());
                    result.success(true);
                }
            }
        });
    }

    private void disconnectBluePrint(int indexPrint, @NonNull Result result) {
        if (DeviceConnFactoryManager.getDeviceConnFactoryManagers()[indexPrint] != null) {
            DeviceConnFactoryManager.getDeviceConnFactoryManagers()[indexPrint].closePort(indexPrint);
        }
        result.success(true);
    }

    private void connectPanda(String address, Result result) {
        try {

            pandaPointer = printerlibs_caysnpos.INSTANCE.CaysnPos_OpenBT2ByConnectA(address);

            int printerStatus = printerlibs_caysnpos.INSTANCE.CaysnPos_QueryPrinterStatus(pandaPointer, 3000);
            boolean isOutOfPaper = printerlibs_caysnpos.PL_PRINTERSTATUS_Helper.PL_PRINTERSTATUS_NOPAPER(printerStatus);
            boolean isOffline = printerlibs_caysnpos.PL_PRINTERSTATUS_Helper.PL_PRINTERSTATUS_OFFLINE(printerStatus);

            System.out.println("printerStatus: " + printerStatus);
            System.out.println("isOutOfPaper: " + isOutOfPaper);
            System.out.println("isOffline: " + isOffline);

            if (isOutOfPaper || isOffline) {
                result.success(false);
            } else {
                result.success(true);
            }
        } catch (Exception e) {
            e.printStackTrace();
            // result.success(false);
            result.error("EXCEPTION_ERROR", "An exception occurred: " + e.getMessage(), null);
        }
    }

    private void printPanda(String address, String content, String imageUrl, Result result) {
        int resultPointer = printerlibs_caysnpos.INSTANCE.CaysnPos_ResetPrinter(pandaPointer);
        int printerStatus = printerlibs_caysnpos.INSTANCE.CaysnPos_QueryPrinterStatus(pandaPointer, 3000);
        boolean isOutOfPaper = printerlibs_caysnpos.PL_PRINTERSTATUS_Helper.PL_PRINTERSTATUS_NOPAPER(printerStatus);
        boolean isOffline = printerlibs_caysnpos.PL_PRINTERSTATUS_Helper.PL_PRINTERSTATUS_OFFLINE(printerStatus);

        System.out.println("printerStatus: " + printerStatus);
        System.out.println("isOutOfPaper: " + isOutOfPaper);
        System.out.println("isOffline: " + isOffline);

        if (isOutOfPaper || isOffline) {
            result.success(false);
            return;
        }

        new Thread(() -> {
            try {
                if (imageUrl != null) {
                    try {
                        byte[] imageData = Base64.decode(imageUrl, Base64.DEFAULT);
                        Bitmap bitmap = BitmapFactory.decodeByteArray(imageData, 0, imageData.length);

                        if (bitmap != null) {
                            int width = bitmap.getWidth();
                            int height = bitmap.getHeight();
                            int page_width = 384;
                            int dstw = width;
                            int dsth = height;
                            if (dstw > page_width) {
                                dstw = page_width;
                                dsth = (int) (dstw * ((double) height / width));
                            }
                            printerlibs_caysnpos.INSTANCE.CaysnPos_SetAlignment(pandaPointer,
                                    printerlibs_caysnpos.PosAlignment_HCenter);
                            printerlibs_caysnpos.CaysnPos_PrintRasterImage_Helper
                                    .CaysnPos_PrintRasterImageFromBitmap(pandaPointer, dstw, dsth,
                                            bitmap,
                                            0);
                            printerlibs_caysnpos.INSTANCE.CaysnPos_PrintTextA(pandaPointer, "\n");
                        }
                    } catch (Exception e) {
                        e.printStackTrace();
                        printerlibs_caysnpos.INSTANCE.CaysnPos_PrintTextA(pandaPointer,
                                "FAILED PRINTING IMAGE\nerrormessage : " + e.getMessage());
                    }
                }

                printerlibs_caysnpos.INSTANCE.CaysnPos_SetAlignment(pandaPointer,
                        printerlibs_caysnpos.PosAlignment_Left);
                printerlibs_caysnpos.INSTANCE.CaysnPos_PrintTextA(pandaPointer, content + "\n\n");
                result.success(true);
            } catch (Exception e) {
                e.printStackTrace();
                result.success(false);
            }
        }).start();
    }

    private void disconnectPanda(String address, @NonNull Result result) {
        printerlibs_caysnpos.INSTANCE.CaysnPos_Close(pandaPointer);
        result.success(true);
    }

    @Override
    public void onDetachedFromEngine(@NonNull FlutterPluginBinding binding) {
        channel.setMethodCallHandler(null);
    }

    private final StreamHandler stateStreamHandler = new StreamHandler() {
    private EventSink sink;

    private final BroadcastReceiver mReceiver = new BroadcastReceiver() {
      @Override
      public void onReceive(Context context, Intent intent) {
        final String action = intent.getAction();
        Log.d(TAG, "stateStreamHandler, current action: " + action);

        if (BluetoothAdapter.ACTION_STATE_CHANGED.equals(action)) {
          threadPool = null;
          sink.success(intent.getIntExtra(BluetoothAdapter.EXTRA_STATE, -1));
        } else if (BluetoothDevice.ACTION_ACL_CONNECTED.equals(action)) {
          sink.success(1);
        } else if (BluetoothDevice.ACTION_ACL_DISCONNECTED.equals(action)) {
          threadPool = null;
          sink.success(0);
        }
      }
    };

    @Override
    public void onListen(Object o, EventSink eventSink) {
      sink = eventSink;
      IntentFilter filter = new IntentFilter(BluetoothAdapter.ACTION_STATE_CHANGED);
      filter.addAction(BluetoothAdapter.ACTION_CONNECTION_STATE_CHANGED);
      filter.addAction(BluetoothDevice.ACTION_ACL_CONNECTED);
      filter.addAction(BluetoothDevice.ACTION_ACL_DISCONNECTED);
      activity.registerReceiver(mReceiver, filter);
    }

    @Override
    public void onCancel(Object o) {
      sink = null;
      activity.unregisterReceiver(mReceiver);
    }
  };
}