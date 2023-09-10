package com.example.wise_bluetooth_print.blueprint;

import android.content.Context;
import android.hardware.usb.UsbDevice;
import android.hardware.usb.UsbManager;
import android.widget.Toast;

import java.util.HashMap;

public class GPUtils {

	private static Toast toast;

	public static UsbDevice getUsbDeviceFromName(Context context, String usbName) {
		UsbManager usbManager = (UsbManager) context.getSystemService(Context.USB_SERVICE);
		HashMap<String,UsbDevice> usbDeviceList = usbManager.getDeviceList();
		return usbDeviceList.get(usbName);
	}

	public static void toast(Context context, String message) {
		if (toast == null) {
			toast = Toast.makeText(context, message, Toast.LENGTH_SHORT);
		} else {
			toast.setText(message);
		}
		toast.show();
	}
}



