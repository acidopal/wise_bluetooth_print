import 'dart:async';
import 'package:flutter/services.dart';
import 'package:wise_bluetooth_print/classes/paired_device.dart';

class WiseBluetoothPrint {
  static const MethodChannel _channel = MethodChannel('wise_bluetooth_print');

  static Future<String?> get platformVersion async {
    final String? version = await _channel.invokeMethod('getPlatformVersion');
    return version;
  }

  static Future<List<PairedDevice>> getPairedDevices() async {
    var ret = await _channel.invokeMethod('getPairedDevices');
    List<PairedDevice> devices = <PairedDevice>[];
    for (int i = 0; i < ret.length; i = i + 3) {
      devices.add(PairedDevice(
          index: i,
          name: ret[i] as String,
          hardwareAddress: ret[i + 1] as String,
          socketId: ret[i + 2] as String,
          food: false,
          drink: false,
          receipt: false));
    }
    return devices;
  }

  static Future<bool> connectBluePrint(String address) async {
    final Map<String, dynamic> params = <String, dynamic>{
      'address': address,
    };
    var ret = await _channel.invokeMethod('connectBluePrint', params);
    return ret;
  }

  static Future<bool> printBluePrint() async {
    var ret = await _channel.invokeMethod('printBluePrint');
    return ret;
  }

  static Future<bool> disconnectBluePrint() async {
    var ret = await _channel.invokeMethod('disconnectBluePrint');
    return ret;
  }

  static Future<bool> connectPanda(String address) async {
    final Map<String, dynamic> params = <String, dynamic>{
      'address': address,
    };
    var ret = await _channel.invokeMethod('connectPanda', params);
    return Future.value(true);
  }

  static Future<bool> printPanda() async {
    var ret = await _channel.invokeMethod('printPanda');
    return ret;
  }

  static Future<bool> disconnectPanda() async {
    var ret = await _channel.invokeMethod('disconnectPanda');
    return ret;
  }
}
