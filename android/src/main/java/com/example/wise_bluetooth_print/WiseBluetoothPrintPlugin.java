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

    private HashMap<String, Integer> blueprintIdAddress = new HashMap<>();

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


    @Override
    public void onDetachedFromEngine(@NonNull FlutterPluginBinding binding) {
        channel.setMethodCallHandler(null);
    }
}