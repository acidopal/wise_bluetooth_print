package com.example.wise_bluetooth_print;

import android.bluetooth.BluetoothAdapter;
import android.bluetooth.BluetoothDevice;
import android.bluetooth.BluetoothSocket;
import android.os.Handler;
import android.os.ParcelUuid;

import android.graphics.Bitmap;
import android.graphics.BitmapFactory;
import java.io.IOException;
import android.util.Log;
import android.content.res.AssetManager;
import io.flutter.embedding.engine.FlutterMain;

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

              printImage(result, "assets/icons/icon.png", timeout);

              write(printStr);

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

  @Override
  public void onDetachedFromEngine(@NonNull FlutterPluginBinding binding) {
    if (handler != null && timeoutRunnable != null) {
      // Remove the timeout runnable callback if it is still pending
      handler.removeCallbacks(timeoutRunnable);
    }
    channel.setMethodCallHandler(null);
  }

  private void printImageBytes(Result result, byte[] bytes) {
    if (outputStream == null) {
      result.error("write_error", "Not connected to a Bluetooth device", null);
      return;
    }

    try {
      Bitmap bmp = BitmapFactory.decodeByteArray(bytes, 0, bytes.length);
      if (bmp != null) {
        byte[] command = Utils.decodeBitmap(bmp);
        outputStream.write(PrinterCommands.ESC_ALIGN_CENTER);
        outputStream.write(command);
      } else {
        Log.e("Print Photo error", "The file doesn't exist");
      }
      result.success(true);
    } catch (IOException ex) {
      Log.e(TAG, ex.getMessage(), ex);
      result.error("write_error", ex.getMessage(), exceptionToString(ex));
    }
  }

  private void printImage(Result result, String imagePath, int timeout) {
    try {
      byte[] imageBytes = getImageBytesFromPath(imagePath);
      if (imageBytes != null) {
        printImageBytes(result, imageBytes);
      } else {
        Log.e("Print Image error", "Failed to read image file");
        result.success(false);
      }
    } catch (IOException ex) {
      Log.e(TAG, ex.getMessage(), ex);
      result.error("write_error", ex.getMessage(), exceptionToString(ex));
    }
  }

  private byte[] getImageBytesFromPath(String imagePath) throws IOException {
    AssetManager assetManager = getFlutterAssets();
    InputStream inputStream = assetManager.open(imagePath);
    ByteArrayOutputStream bos = new ByteArrayOutputStream();

    byte[] buffer = new byte[1024];
    int bytesRead;
    while ((bytesRead = inputStream.read(buffer)) != -1) {
      bos.write(buffer, 0, bytesRead);
    }

    inputStream.close();
    return bos.toByteArray();
  }

  private AssetManager getFlutterAssets() throws IOException {
    Method getAssetManager = FlutterMain.class.getDeclaredMethod("getAssetManager");
    getAssetManager.setAccessible(true);
    return (AssetManager) getAssetManager.invoke(null);
  }
}

public static class Utils {
  // UNICODE 0x23 = #
  private static final byte[] UNICODE_TEXT = new byte[] { 0x23, 0x23, 0x23,
      0x23, 0x23, 0x23, 0x23, 0x23, 0x23, 0x23, 0x23, 0x23, 0x23, 0x23, 0x23,
      0x23, 0x23, 0x23, 0x23, 0x23, 0x23, 0x23, 0x23, 0x23, 0x23, 0x23, 0x23,
      0x23, 0x23, 0x23 };

  private static String hexStr = "0123456789ABCDEF";
  private static String[] binaryArray = { "0000", "0001", "0010", "0011",
      "0100", "0101", "0110", "0111", "1000", "1001", "1010", "1011",
      "1100", "1101", "1110", "1111" };

  public static byte[] decodeBitmap(Bitmap bmp) {
    int bmpWidth = bmp.getWidth();
    int bmpHeight = bmp.getHeight();

    List<String> list = new ArrayList<String>(); // binaryString list
    StringBuffer sb;

    int bitLen = bmpWidth / 8;
    int zeroCount = bmpWidth % 8;

    String zeroStr = "";
    if (zeroCount > 0) {
      bitLen = bmpWidth / 8 + 1;
      for (int i = 0; i < (8 - zeroCount); i++) {
        zeroStr = zeroStr + "0";
      }
    }

    for (int i = 0; i < bmpHeight; i++) {
      sb = new StringBuffer();
      for (int j = 0; j < bmpWidth; j++) {
        int color = bmp.getPixel(j, i);

        int r = (color >> 16) & 0xff;
        int g = (color >> 8) & 0xff;
        int b = color & 0xff;

        // if color is close to white, bit='0', else bit='1'
        if (r > 160 && g > 160 && b > 160)
          sb.append("0");
        else
          sb.append("1");
      }
      if (zeroCount > 0) {
        sb.append(zeroStr);
      }
      list.add(sb.toString());
    }

    List<String> bmpHexList = binaryListToHexStringList(list);
    String commandHexString = "1D763000";
    String widthHexString = Integer.toHexString(bmpWidth % 8 == 0 ? bmpWidth / 8
        : (bmpWidth / 8 + 1));
    if (widthHexString.length() > 10) {
      Log.e("decodeBitmap error", "Width is too large");
      return null;
    } else if (widthHexString.length() == 1) {
      widthHexString = "0" + widthHexString;
    }
    widthHexString = widthHexString + "00";

    String heightHexString = Integer.toHexString(bmpHeight);
    if (heightHexString.length() > 10) {
      Log.e("decodeBitmap error", "Height is too large");
      return null;
    } else if (heightHexString.length() == 1) {
      heightHexString = "0" + heightHexString;
    }
    heightHexString = heightHexString + "00";

    List<String> commandList = new ArrayList<String>();
    commandList.add(commandHexString + widthHexString + heightHexString);
    commandList.addAll(bmpHexList);

    return hexList2Byte(commandList);
  }

  public static List<String> binaryListToHexStringList(List<String> list) {
    List<String> hexList = new ArrayList<String>();
    for (String binaryStr : list) {
      StringBuffer sb = new StringBuffer();
      for (int i = 0; i < binaryStr.length(); i += 8) {
        String str = binaryStr.substring(i, i + 8);

        String hexString = myBinaryStrToHexString(str);
        sb.append(hexString);
      }
      hexList.add(sb.toString());
    }
    return hexList;
  }

  public static String myBinaryStrToHexString(String binaryStr) {
    String hex = "";
    String f4 = binaryStr.substring(0, 4);
    String b4 = binaryStr.substring(4, 8);
    for (int i = 0; i < binaryArray.length; i++) {
      if (f4.equals(binaryArray[i]))
        hex += hexStr.substring(i, i + 1);
    }
    for (int i = 0; i < binaryArray.length; i++) {
      if (b4.equals(binaryArray[i]))
        hex += hexStr.substring(i, i + 1);
    }

    return hex;
  }

  public static byte[] hexList2Byte(List<String> list) {
    List<byte[]> commandList = new ArrayList<byte[]>();

    for (String hexStr : list) {
      commandList.add(hexStringToBytes(hexStr));
    }
    byte[] bytes = sysCopy(commandList);
    return bytes;
  }

  public static byte[] hexStringToBytes(String hexString) {
    if (hexString == null || hexString.equals("")) {
      return null;
    }
    hexString = hexString.toUpperCase();
    int length = hexString.length() / 2;
    char[] hexChars = hexString.toCharArray();
    byte[] d = new byte[length];
    for (int i = 0; i < length; i++) {
      int pos = i * 2;
      d[i] = (byte) (charToByte(hexChars[pos]) << 4 | charToByte(hexChars[pos + 1]));
    }
    return d;
  }

  public static byte[] sysCopy(List<byte[]> srcArrays) {
    int len = 0;
    for (byte[] srcArray : srcArrays) {
      len += srcArray.length;
    }
    byte[] destArray = new byte[len];
    int destLen = 0;
    for (byte[] srcArray : srcArrays) {
      System.arraycopy(srcArray, 0, destArray, destLen, srcArray.length);
      destLen += srcArray.length;
    }
    return destArray;
  }

  private static byte charToByte(char c) {
    return (byte) "0123456789ABCDEF".indexOf(c);
  }
}
