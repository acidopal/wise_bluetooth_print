package com.example.wise_bluetooth_print;

import android.bluetooth.BluetoothAdapter;
import android.bluetooth.BluetoothDevice;
import android.content.Context;
import android.graphics.Bitmap;
import android.os.ParcelUuid;

import androidx.annotation.NonNull;

import com.bumptech.glide.Glide;
import com.bumptech.glide.load.resource.bitmap.DownsampleStrategy;
import com.bumptech.glide.request.RequestOptions;
import com.example.wise_bluetooth_print.blueprint.GPDeviceConnFactoryManager;
import com.example.wise_bluetooth_print.blueprint.GPThreadPool;
import com.gprinter.command.EscCommand;
import com.example.wise_bluetooth_print.panda.printerlibs_caysnpos;
import com.sun.jna.Pointer;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.Map;
import java.util.Map.Entry;
import java.util.Set;
import java.util.UUID;

import io.flutter.embedding.engine.plugins.FlutterPlugin;
import io.flutter.plugin.common.MethodCall;
import io.flutter.plugin.common.MethodChannel;
import io.flutter.plugin.common.MethodChannel.MethodCallHandler;
import io.flutter.plugin.common.MethodChannel.Result;
import android.graphics.BitmapFactory;
import android.util.Base64;

public class WiseBluetoothPrintPlugin implements FlutterPlugin, MethodCallHandler {
    private MethodChannel channel;
    private Context context;

    private Pointer pandaPointer;

    @Override
    public void onAttachedToEngine(@NonNull FlutterPluginBinding flutterPluginBinding) {
        context = flutterPluginBinding.getApplicationContext();
        channel = new MethodChannel(flutterPluginBinding.getBinaryMessenger(), "wise_bluetooth_print");
        channel.setMethodCallHandler(this);
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
                connectBluePrint(content, indexPrint, result);
                break;
            }
            case "disconnectBluePrint": {
                disconnectBluePrint(result);
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
        System.out.println("Address");
        System.out.println(address);
        new GPDeviceConnFactoryManager.Build()
                .setId(indexPrint)
                .setContext(context)
                .setName("")
                .setConnMethod(GPDeviceConnFactoryManager.CONN_METHOD.BLUETOOTH)
                .setMacAddress(address)
                .build();

        GPThreadPool threadPool = GPThreadPool.getInstantiation();
        threadPool.addTask(() -> {
            try {
                GPDeviceConnFactoryManager.getDeviceConnFactoryManagers()[indexPrint].openPort();
                result.success(true);
            } catch (Exception e) {
                e.printStackTrace();
                result.success(false);
            }
        });
    }

    private void printBluePrint(String content, int indexPrint, Result result) {
        new Thread() {
            @Override
            public void run() {
                EscCommand esc = new EscCommand();
                esc.addInitializePrinter();
                esc.addSelectJustification(EscCommand.JUSTIFICATION.LEFT);
                esc.addText(content + "\n");
                esc.addSelectJustification(EscCommand.JUSTIFICATION.CENTER);
                esc.addText("CENTER TEXT\n");
                esc.addSelectJustification(EscCommand.JUSTIFICATION.RIGHT);
                esc.addText("RIGHT TEXT\n\n\n");

                esc.addSelectJustification(EscCommand.JUSTIFICATION.CENTER);
                esc.addText("PRINTING IMAGE\n");

                try {
                    Bitmap bitmap = Glide.with(context)
                            .asBitmap()
                            .load(R.drawable.carimage)
                            .apply(new RequestOptions().override(200, 200).downsample(DownsampleStrategy.CENTER_INSIDE))
                            .submit(200, 200)
                            .get();
                    esc.addSelectJustification(EscCommand.JUSTIFICATION.CENTER);
                    esc.addRastBitImage(bitmap, 200, 0);
                    esc.addText("\n\n\n\n");
                } catch (Exception e) {
                    e.printStackTrace();
                    esc.addText("FAILED PRINTING IMAGE\nerrormessage : " + e.getMessage());
                }

                GPDeviceConnFactoryManager.getDeviceConnFactoryManagers()[indexPrint]
                        .sendDataImmediately(esc.getCommand());
                result.success(true);
            }
        }.start();
    }

    private void disconnectBluePrint(@NonNull Result result) {
        GPDeviceConnFactoryManager.closeAllPort();
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
}