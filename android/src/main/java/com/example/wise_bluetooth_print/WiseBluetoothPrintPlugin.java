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

import android.os.AsyncTask;

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
    if (call.method.equals("getPlatformVersion")) {
      result.success("Android " + android.os.Build.VERSION.RELEASE);
    } else if (call.method.equals("getPairedDevices")) {
      ArrayList<String> deviceInfoList = new ArrayList<String>();
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
    } else if (call.method.equals("print")) {
      String imageUrl = call.argument("imageUrl");
      String printStr = call.argument("printText");
      String uuid = call.argument("deviceUUID");
      int timeout = call.argument("timeout");
      int printIndex = call.argument("printIndex");

      BluetoothAdapter bluetooth = BluetoothAdapter.getDefaultAdapter();

      Set<BluetoothDevice> pairedDevices = bluetooth.getBondedDevices();
      BluetoothDevice[] pairedDevicesArray = pairedDevices.toArray(new BluetoothDevice[0]);
      int size = pairedDevicesArray.length;

      for (int i = 0; i < size; i++) {
        if (printIndex == i) {
          BluetoothDevice pairedDevice = pairedDevicesArray[i];
          ParcelUuid[] uuids = pairedDevice.getUuids();
          UUID s = uuids[0].getUuid();
          if (s.toString().equals(uuid)) {
            bluetooth.cancelDiscovery();

            try {
              final BluetoothSocket socket = pairedDevice.createRfcommSocketToServiceRecord(UUID.fromString(uuid));

              socket.connect();
              outputStream = socket.getOutputStream();
              inStream = socket.getInputStream();

              printPhotoFromUrl("https://upload.wikimedia.org/wikipedia/commons/a/a2/Example_logo.jpg");
              // write(printStr);

              // Set timeout runnable to handle timeout case
              timeoutRunnable = new Runnable() {
                @Override
                public void run() {
                  try {
                    socket.close();
                  } catch (IOException e) {
                    tempText = "1";
                  } finally {
                    result.success(printSuccess);
                  }
                }
              };

              // Schedule the timeout runnable
              handler = new Handler();
              handler.postDelayed(timeoutRunnable, timeout);
            } catch (IOException e) {
              tempText = "1";
              result.success(false);
            }
          } else {
            tempText = "1";
            result.success(false);
          }
        }
      }
    } else {
      result.notImplemented();
    }
  }

  public void write(String s) throws IOException {
    outputStream.write(s.getBytes());
    // Set printSuccess flag to true after successful write
    printSuccess = true;
  }

  public void write(byte[] data) throws IOException {
    outputStream.write(data);
    // Set printSuccess flag to true after successful write
    printSuccess = true;
  }

  public void printPhotoFromUrl(String imageUrl) {
    new AsyncTask<String, Void, byte[]>() {
      @Override
      protected byte[] doInBackground(String... params) {
        String imageUrl = params[0];
        try {
          URL url = new URL(imageUrl);
          HttpURLConnection connection = (HttpURLConnection) url.openConnection();
          connection.setDoInput(true);
          connection.connect();

          InputStream inputStream = connection.getInputStream();
          Bitmap bmp = BitmapFactory.decodeStream(inputStream);
          if (bmp != null) {
            ByteArrayOutputStream outputStream = new ByteArrayOutputStream();
            bmp.compress(Bitmap.CompressFormat.PNG, 100, outputStream);
            return outputStream.toByteArray();
          }
        } catch (Exception e) {
          e.printStackTrace();
        }
        return null;
      }

      @Override
      protected void onPostExecute(byte[] command) {
        if (command != null) {
          outputStream.write(PrinterCommands.ESC_ALIGN_CENTER);
          write(command);
        } else {
          Log.e("Print Photo error", "Failed to print image");
        }
      }
    }.execute(imageUrl);
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
