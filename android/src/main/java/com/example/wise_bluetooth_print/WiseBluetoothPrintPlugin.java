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
import android.graphics.BitmapFactory;
import android.util.Base64;
import java.util.Map.Entry;

public class WiseBluetoothPrintPlugin implements FlutterPlugin, MethodCallHandler {
    private MethodChannel channel;
    private Context context;

    private HashMap<String, Pointer> pandaPointers = new HashMap<>();

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
                String content = call.argument("content");
                printBluePrint(content, result);
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
            case "clearPanda": {
                clearPanda(result);
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
        System.out.println("Address");
        System.out.println(address);
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

    private void printBluePrint(String content, Result result) {
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
            Pointer pandaPointer = printerlibs_caysnpos.INSTANCE.CaysnPos_OpenBT2ByConnectA(address);
            pandaPointers.put(address, pandaPointer);
            result.success("success");
        } catch (Exception e) {
            e.printStackTrace();
            result.success(e.toString());
        }
    }

    private void printPanda(String address, String content, String imageUrl, Result result) {
        new Thread() {
            @Override
            public void run() {
                if (pandaPointers.containsKey(address)) {
                    Pointer pandaPointer = pandaPointers.get(address);

                    if (pandaPointer != null) {
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
                                                .CaysnPos_PrintRasterImageFromBitmap(pandaPointer, dstw, dsth, bitmap,
                                                        0);
                                        printerlibs_caysnpos.INSTANCE.CaysnPos_PrintTextA(pandaPointer, "\n");
                                    }
                                } catch (Exception r) {
                                    r.printStackTrace();
                                    printerlibs_caysnpos.INSTANCE.CaysnPos_PrintTextA(pandaPointer,
                                            "FAILED PRINTING IMAGE\nerrormessage : " + r.getMessage());
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
                    } else {
                        result.success(false);
                    }
                } else {
                    result.success(false);
                }
            }
        }.start();
    }

    private void disconnectPanda(String address, @NonNull Result result) {
        Pointer pandaPointer = pandaPointers.get(address);

        if (pandaPointer != null) {
            printerlibs_caysnpos.INSTANCE.CaysnPos_Close(pandaPointer);
            pandaPointers.remove(address);
        }

        result.success(true);
    }

    private void clearPanda(@NonNull Result result) {
        for (Entry<String, Pointer> entry : pandaPointers.entrySet()) {
            Pointer pandaPointer = entry.getValue();

            if (pandaPointer != null) {
                printerlibs_caysnpos.INSTANCE.CaysnPos_Close(pandaPointer);
            }
        }

        pandaPointers.clear();
        result.success(true);
    }

    @Override
    public void onDetachedFromEngine(@NonNull FlutterPluginBinding binding) {
        channel.setMethodCallHandler(null);
    }
}
