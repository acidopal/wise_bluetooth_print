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
import java.util.Set;
import java.util.UUID;

import io.flutter.embedding.engine.plugins.FlutterPlugin;
import io.flutter.plugin.common.MethodCall;
import io.flutter.plugin.common.MethodChannel;
import io.flutter.plugin.common.MethodChannel.MethodCallHandler;
import io.flutter.plugin.common.MethodChannel.Result;

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
                connectBluePrint(address, result);
                break;
            }
            case "printBluePrint": {
                printBluePrint(result);
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
                printPanda(result);
                break;
            }
            case "disconnectPanda": {
                disconnectPanda(result);
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

    private void connectBluePrint(String address, @NonNull Result result) {
        new GPDeviceConnFactoryManager.Build()
                .setId(0)
                .setContext(context)
                .setName("")
                .setConnMethod(GPDeviceConnFactoryManager.CONN_METHOD.BLUETOOTH)
                .setMacAddress(address)
                .build();

        GPThreadPool threadPool = GPThreadPool.getInstantiation();
        threadPool.addTask(() -> {
            try {
                GPDeviceConnFactoryManager.getDeviceConnFactoryManagers()[0].openPort();
                result.success(true);
            } catch (Exception e) {
                e.printStackTrace();
                result.success(false);
            }
        });
    }

    private void printBluePrint(Result result) {
        new Thread() {
            @Override
            public void run() {
                EscCommand esc = new EscCommand();
                esc.addInitializePrinter();
                esc.addSelectJustification(EscCommand.JUSTIFICATION.LEFT);
                esc.addText("LEFT TEXT\n");
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
                } catch (Exception e) {
                    e.printStackTrace();
                    esc.addText("FAILED PRINTING IMAGE\nerrormessage : " + e.getMessage());
                }

                GPDeviceConnFactoryManager.getDeviceConnFactoryManagers()[0].sendDataImmediately(esc.getCommand());
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
        } catch (Exception e) {
            e.printStackTrace();
        } finally {
            if (pandaPointer != null) {
                closePandaPointer(pandaPointer);
            }
        }
    }

    private void closePandaPointer(Pointer pandaPointer) {
        try {
            printerlibs_caysnpos.INSTANCE.CaysnPos_Close(pandaPointer);
        } catch (Exception e) {
            e.printStackTrace();
        }
    }

   private void printPanda(Result result) {
        new Thread(() -> {
            try {
                printerlibs_caysnpos.INSTANCE.CaysnPos_SetAlignment(pandaPointer, printerlibs_caysnpos.PosAlignment_Left);
                printerlibs_caysnpos.INSTANCE.CaysnPos_PrintTextA(pandaPointer, "LEFT_TEXT\n");

                printerlibs_caysnpos.INSTANCE.CaysnPos_SetAlignment(pandaPointer, printerlibs_caysnpos.PosAlignment_HCenter);
                printerlibs_caysnpos.INSTANCE.CaysnPos_PrintTextA(pandaPointer, "CENTER_TEXT\n");

                printerlibs_caysnpos.INSTANCE.CaysnPos_SetAlignment(pandaPointer, printerlibs_caysnpos.PosAlignment_Right);
                printerlibs_caysnpos.INSTANCE.CaysnPos_PrintTextA(pandaPointer, "RIGHT_TEXT\n");

                printerlibs_caysnpos.INSTANCE.CaysnPos_SetAlignment(pandaPointer, printerlibs_caysnpos.PosAlignment_HCenter);
                printerlibs_caysnpos.INSTANCE.CaysnPos_PrintTextA(pandaPointer, "PRINTING IMAGE\n");

                Bitmap bitmap = Glide.with(context)
                        .asBitmap()
                        .load(R.drawable.carimage)
                        .apply(new RequestOptions().override(200, 200).downsample(DownsampleStrategy.CENTER_INSIDE))
                        .submit(200, 200)
                        .get();

                if (bitmap != null) {
                    int pageWidth = 384;
                    int imageWidth = bitmap.getWidth();
                    int imageHeight = bitmap.getHeight();
                    int dstWidth = imageWidth;
                    int dstHeight = imageHeight;

                    if (dstWidth > pageWidth) {
                        dstWidth = pageWidth;
                        dstHeight = (int) (dstWidth * ((double) imageHeight / imageWidth));
                    }

                    printerlibs_caysnpos.INSTANCE.CaysnPos_SetAlignment(pandaPointer, printerlibs_caysnpos.PosAlignment_HCenter);

                    printerlibs_caysnpos.CaysnPos_PrintRasterImage_Helper.CaysnPos_PrintRasterImageFromBitmap(pandaPointer, dstWidth, dstHeight, bitmap, 0);
                }
            } catch (Exception e) {
                e.printStackTrace();
                printerlibs_caysnpos.INSTANCE.CaysnPos_PrintTextA(pandaPointer, "FAILED PRINTING IMAGE\nerrormessage : " + e.getMessage());
            } finally {
                result.success(true);
            }
        }).start();
    }

    private void disconnectPanda(@NonNull Result result) {
        printerlibs_caysnpos.INSTANCE.CaysnPos_Close(pandaPointer);
        result.success(true);
    }

    @Override
    public void onDetachedFromEngine(@NonNull FlutterPluginBinding binding) {
        channel.setMethodCallHandler(null);
    }
}
