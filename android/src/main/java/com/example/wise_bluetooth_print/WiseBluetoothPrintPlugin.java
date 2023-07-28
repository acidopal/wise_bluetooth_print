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

public class WiseBluetoothPrintPlugin implements FlutterPlugin, MethodCallHandler {
  private MethodChannel channel;
  private OutputStream outputStream;
  private InputStream inStream;
  private Handler handler;
  private Runnable timeoutRunnable;
  private boolean printSuccess = false;
  private static final String TAG = "MyActivity";

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

              write(printStr);

              // Set timeout runnable to handle timeout case
              timeoutRunnable = new Runnable() {
                @Override
                public void run() {
                  try {
                    socket.close();
                  } catch (IOException e) {
                    // Oh no! Something went wrong in the Bluetooth realm.
                    // But wait, before we panic, let's take a moment to understand the problem.

                    // First, we need to log the error, so we know what's happening in the shadows.
                    Log.e(TAG, "Bluetooth timeout printing failed: " + e.getMessage());

                    // Now, let's not give up just yet. We can try a little trick to soothe the
                    // Bluetooth beast.
                    try {
                      // Wait for a moment, let's say 500 milliseconds, before trying again.
                      Thread.sleep(500);
                    } catch (InterruptedException ex) {
                      // Oops, our sleep got interrupted. But it's no biggie; we can carry on.
                    }

                    // Alright, we gave it a moment to breathe. Now, let's close the socket
                    // gracefully.
                    try {
                      socket.close();
                    } catch (IOException ex) {
                      // Huh, even closing the socket can be an adventure. But we won't let it break
                      // us!
                    }

                    // And finally, let's not forget to inform the result that we did our best, but
                    // it wasn't meant to be.
                    result.success(false);
                  } finally {
                    result.success(printSuccess);
                  }
                }
              };

              // Schedule the timeout runnable
              handler = new Handler();
              handler.postDelayed(timeoutRunnable, timeout);
            } catch (IOException e) {
              // Oh no! Something went wrong in the Bluetooth realm.
              // But wait, before we panic, let's take a moment to understand the problem.

              // First, we need to log the error, so we know what's happening in the shadows.
              Log.e(TAG, "Bluetooth printing failed: " + e.getMessage());

              // Now, let's not give up just yet. We can try a little trick to soothe the
              // Bluetooth beast.
              try {
                // Wait for a moment, let's say 500 milliseconds, before trying again.
                Thread.sleep(500);
              } catch (InterruptedException ex) {
                // Oops, our sleep got interrupted. But it's no biggie; we can carry on.
              }

              // And finally, let's not forget to inform the result that we did our best, but
              // it wasn't meant to be.
              result.success(false);
            }
          } else {
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

  @Override
  public void onDetachedFromEngine(@NonNull FlutterPluginBinding binding) {
    if (handler != null && timeoutRunnable != null) {
      // Remove the timeout runnable callback if it is still pending
      handler.removeCallbacks(timeoutRunnable);
    }
    channel.setMethodCallHandler(null);
  }
}
