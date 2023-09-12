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
import java.util.List;
import java.util.Map;
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
    private Map<String, Pointer> pandaPointers = new HashMap<>();

    @Override
    public void onAttachedToEngine(@NonNull FlutterPluginBinding flutterPluginBinding) {
        context = flutterPluginBinding.getApplicationContext();
        channel = new MethodChannel(flutterPluginBinding.getBinaryMessenger(), "wise_bluetooth_print");
        channel.setMethodCallHandler(this);
    }

    @Override
    public void onMethodCall(@NonNull MethodCall call, @NonNull Result result) {
        try {
            switch (call.method) {
                case "getPlatformVersion":
                    result.success("Android " + android.os.Build.VERSION.RELEASE);
                    break;
                case "getPairedDevices":
                    getPairedDevices(result);
                    break;
                case "connectBluePrint":
                    connectBluePrint(call.argument("address"), result);
                    break;
                case "printBluePrint":
                    printBluePrint(call.argument("content"), result);
                    break;
                case "disconnectBluePrint":
                    disconnectBluePrint(result);
                    break;
                case "connectPanda":
                    connectPanda(call.argument("address"), result);
                    break;
                case "printPanda":
                    printPanda(call.argument("address"), call.argument("content"), call.argument("imageUrl"), result);
                    break;
                case "disconnectPanda":
                    disconnectPanda(call.argument("address"), result);
                    break;
                case "clearPanda":
                    clearPanda(result);
                    break;
                default:
                    result.notImplemented();
                    break;
            }
        } catch (Exception e) {
            result.error("Error", e.getMessage(), null);
        }
    }

    private void getPairedDevices(Result result) {
        List<Map<String, String>> deviceInfoList = new ArrayList<>();
        BluetoothAdapter bluetooth = BluetoothAdapter.getDefaultAdapter();
        if (bluetooth != null && bluetooth.isEnabled()) {
            Set<BluetoothDevice> pairedDevices = bluetooth.getBondedDevices();
            for (BluetoothDevice device : pairedDevices) {
                Map<String, String> deviceInfo = new HashMap<>();
                deviceInfo.put("name", device.getName());
                deviceInfo.put("address", device.getAddress());
                deviceInfo.put("socket", device.getUuids()[0].getUuid().toString());
                deviceInfoList.add(deviceInfo);
            }
        }
        result.success(deviceInfoList);
    }

    private void connectBluePrint(String address, @NonNull Result result) {
        GPDeviceConnFactoryManager.Build builder = new GPDeviceConnFactoryManager.Build()
                .setId(0)
                .setContext(context)
                .setName("")
                .setConnMethod(GPDeviceConnFactoryManager.CONN_METHOD.BLUETOOTH)
                .setMacAddress(address);

        GPDeviceConnFactoryManager.getDeviceConnFactoryManagers()[0] = builder.build();
        GPThreadPool.getInstantiation().addTask(() -> {
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
        new Thread(() -> {
            try {
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

                Bitmap bitmap = Glide.with(context)
                        .asBitmap()
                        .load(R.drawable.carimage)
                        .apply(new RequestOptions().override(200, 200).downsample(DownsampleStrategy.CENTER_INSIDE))
                        .submit(200, 200)
                        .get();
                esc.addSelectJustification(EscCommand.JUSTIFICATION.CENTER);
                esc.addRastBitImage(bitmap, 200, 0);
                esc.addText("\n\n\n\n");

                GPDeviceConnFactoryManager.getDeviceConnFactoryManagers()[0].sendDataImmediately(esc.getCommand());
                result.success(true);
            } catch (Exception e) {
                e.printStackTrace();
                result.success(false);
            }
        }).start();
    }

    private void disconnectBluePrint(@NonNull Result result) {
        GPDeviceConnFactoryManager.closeAllPort();
        result.success(true);
    }

    private void connectPanda(String address, Result result) {
        try {
            if (pandaPointers.containsKey(address)) {
                result.success("success");
            } else {
                Pointer pandaPointer = printerlibs_caysnpos.INSTANCE.CaysnPos_OpenBT2ByConnectA(address);
                pandaPointers.put(address, pandaPointer);
                result.success("success");
            }
        } catch (Exception e) {
            e.printStackTrace();
            result.success(e.toString());
        }
    }

    private void printPanda(String address, String content, String imageUrl, Result result) {
        new Thread(() -> {
            if (pandaPointers.containsKey(address)) {
                Pointer pandaPointer = pandaPointers.get(address);

                if (pandaPointer != null) {
                    try {
                        if (imageUrl != null) {
                            byte[] imageData = Base64.decode(imageUrl, Base64.DEFAULT);
                            Bitmap bitmap = BitmapFactory.decodeByteArray(imageData, 0, imageData.length);
                            if (bitmap != null) {
                                int width = bitmap.getWidth();
                                int height = bitmap.getHeight();
                                int pageWidth = 384;
                                int dstw = width;
                                int dsth = height;
                                if (dstw > pageWidth) {
                                    dstw = pageWidth;
                                    dsth = (int) (dstw * ((double) height / width));
                                }
                                printerlibs_caysnpos.INSTANCE.CaysnPos_SetAlignment(pandaPointer,
                                        printerlibs_caysnpos.PosAlignment_HCenter);
                                printerlibs_caysnpos.CaysnPos_PrintRasterImage_Helper
                                        .CaysnPos_PrintRasterImageFromBitmap(pandaPointer, dstw, dsth, bitmap, 0);
                                printerlibs_caysnpos.INSTANCE.CaysnPos_PrintTextA(pandaPointer, "\n");
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
        }).start();
    }

    private void disconnectPanda(String address, @NonNull Result result) {
        if (pandaPointers.containsKey(address)) {
            Pointer pandaPointer = pandaPointers.get(address);

            if (pandaPointer != null) {
                printerlibs_caysnpos.INSTANCE.CaysnPos_Close(pandaPointer);
                pandaPointers.remove(address);
            }
            result.success(true);
        } else {
            result.success(true);
        }
    }

    private void clearPanda(@NonNull Result result) {
        for (Pointer pandaPointer : pandaPointers.values()) {
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
