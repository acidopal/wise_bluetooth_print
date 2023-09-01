package com.example.wise_bluetooth_print;

import android.bluetooth.BluetoothAdapter;
import android.bluetooth.BluetoothDevice;
import android.bluetooth.BluetoothSocket;
import android.os.Handler;
import android.os.ParcelUuid;
import android.util.Log;

import androidx.annotation.NonNull;

import io.flutter.embedding.engine.plugins.FlutterPlugin;
import io.flutter.plugin.common.MethodCall;
import io.flutter.plugin.common.MethodChannel;
import io.flutter.plugin.common.MethodChannel.MethodCallHandler;
import io.flutter.plugin.common.MethodChannel.Result;

import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.util.ArrayList;
import java.util.Set;
import java.util.UUID;

import android.app.Activity;
import android.graphics.Bitmap;
import android.graphics.BitmapFactory;

import java.net.URL;
import java.net.HttpURLConnection;
import android.os.StrictMode;

import android.util.Base64;

public class WiseBluetoothPrintPlugin implements FlutterPlugin, MethodCallHandler {
  private MethodChannel channel;
  private OutputStream outputStream;
  private InputStream inStream;
  private String tempText = "0";
  private Handler handler;
  private Runnable timeoutRunnable;
  private boolean printSuccess = false;

  @Override
  public void onAttachedToEngine(@NonNull FlutterPluginBinding flutterPluginBinding) {
    channel = new MethodChannel(flutterPluginBinding.getBinaryMessenger(), "wise_bluetooth_print");
    channel.setMethodCallHandler(this);
  }

  @Override
  public void onMethodCall(@NonNull MethodCall call, @NonNull Result result) {
      String method = call.method;

      if ("getPlatformVersion".equals(method)) {
          result.success("Android " + android.os.Build.VERSION.RELEASE);
      } else if ("getPairedDevices".equals(method)) {
          result.success(getPairedDeviceList());
      } else if ("print".equals(method)) {
          printDocument(call, result);
      } else {
          result.notImplemented();
      }
    }

    private ArrayList<String> getPairedDeviceList() {
        ArrayList<String> deviceInfoList = new ArrayList<>();
        BluetoothAdapter bluetooth = BluetoothAdapter.getDefaultAdapter();

        if (bluetooth != null && bluetooth.isEnabled()) {
            Set<BluetoothDevice> pairedDevices = bluetooth.getBondedDevices();
            for (BluetoothDevice device : pairedDevices) {
                device.fetchUuidsWithSdp();
                ParcelUuid[] uuids = device.getUuids();
                UUID socketUUID = uuids[0].getUuid();

                deviceInfoList.add(device.getName());
                deviceInfoList.add(device.getAddress());
                deviceInfoList.add(socketUUID.toString());
            }
        }

        return deviceInfoList;
    }

    private void printDocument(@NonNull MethodCall call, @NonNull Result result) {
        String uuid = call.argument("deviceUUID");
        int timeout = call.argument("timeout");
        int printIndex = call.argument("printIndex");

        BluetoothAdapter bluetooth = BluetoothAdapter.getDefaultAdapter();
        if (bluetooth == null) {
            result.success(false);
            return;
        }

        Set<BluetoothDevice> pairedDevices = bluetooth.getBondedDevices();
        List<BluetoothDevice> pairedDevicesList = new ArrayList<>(pairedDevices);
        if (printIndex < 0 || printIndex >= pairedDevicesList.size()) {
            result.success(false);
            return;
        }

        BluetoothDevice pairedDevice = pairedDevicesList.get(printIndex);
        ParcelUuid[] uuids = pairedDevice.getUuids();
        if (uuids.length == 0 || !uuids[0].getUuid().toString().equals(uuid)) {
            result.success(false);
            return;
        }

        try {
            BluetoothSocket socket = pairedDevice.createRfcommSocketToServiceRecord(UUID.fromString(uuid));
            socket.connect();

            boolean success = printContent(socket, call);
            socket.close();

            result.success(success);
        } catch (IOException e) {
            e.printStackTrace();
            result.success(false);
        }
    }

    private boolean printContent(BluetoothSocket socket, @NonNull MethodCall call) {
        // Set up socket and streams
        OutputStream outputStream = null;
        InputStream inStream = null;
        boolean printSuccess = false;

        try {
            outputStream = socket.getOutputStream();
            inStream = socket.getInputStream();

            // Print image if available
            String imageUrl = call.argument("imageUrl");
            printImage(outputStream, imageUrl);

            // Print text
            String printStr = call.argument("printText");
            write(outputStream, PrinterCommands.ESC_ALIGN_LEFT);
            write(outputStream, printStr.getBytes());
            printSuccess = true;
        } catch (IOException e) {
            e.printStackTrace();
        } finally {
            try {
                if (outputStream != null) outputStream.close();
                if (inStream != null) inStream.close();
            } catch (IOException e) {
                e.printStackTrace();
            }
        }

        return printSuccess;
    }

    private void write(OutputStream outputStream, byte[] data) throws IOException {
        outputStream.write(data);
    }

    private void printImage(OutputStream outputStream, String imageUrl) throws IOException {
        if (imageUrl == null) return;

        try {
            byte[] imageData = Base64.decode(imageUrl, Base64.DEFAULT);
            Bitmap bmp = BitmapFactory.decodeByteArray(imageData, 0, imageData.length);

            if (bmp != null) {
                byte[] command = Utils.decodeBitmap(bmp);
                write(outputStream, PrinterCommands.ESC_ALIGN_CENTER);
                write(outputStream, command);
                write(outputStream, PrinterCommands.ESC_ENTER);
            } else {
                Log.e("Print Photo error", "Failed to decode image");
            }
        } catch (Exception e) {
            e.printStackTrace();
            Log.e("PrintTools", "Error while printing photo");
        }
    }


  @Override
  public void onDetachedFromEngine(@NonNull FlutterPluginBinding binding) {
    if (handler != null && timeoutRunnable != null) {
      // Remove the timeout runnable callback if it is still pending
      handler.removeCallbacks(timeoutRunnable);
    }
    channel.setMethodCallHandler(null);
  }
}
