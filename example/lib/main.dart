import 'package:flutter/material.dart';
import 'dart:async';

import 'package:flutter/services.dart';
import 'package:wise_bluetooth_print/classes/paired_device.dart';
import 'package:wise_bluetooth_print/wise_bluetooth_print.dart';

void main() {
  runApp(const MyApp());
}

class MyApp extends StatefulWidget {
  const MyApp({Key? key}) : super(key: key);

  @override
  State<MyApp> createState() => _MyAppState();
}

class _MyAppState extends State<MyApp> {
  late List<PairedDevice> _devices;

  @override
  void initState() {
    super.initState();
    _devices = <PairedDevice>[];
    initPlatformState();
  }

  // Platform messages are asynchronous, so we initialize in an async method.
  Future<void> initPlatformState() async {
    List<PairedDevice> devices = <PairedDevice>[];

    try {
      devices = await WiseBluetoothPrint.getPairedDevices();
    } on PlatformException {
      devices = <PairedDevice>[];
    }

    if (!mounted) return;

    setState(() {
      _devices = devices;
    });
  }

  void initPrint(BuildContext context, String hardwareAddress) {
    // You can add more language options other than ZPL and BZPL/ZPL II for printers
    // that don't support them.
    showDialog(
      context: context,
      builder: (builder) => AlertDialog(
        title: const Text("Select printer brand"),
        content: const Text(
            "PUNTEEEEENNNNN"),
        actions: [
          TextButton(
            onPressed: () async {
              Navigator.of(context).pop();
              await WiseBluetoothPrint.connectBluePrint(hardwareAddress);
            },
            child: const Text("Blueprint"),
          ),
          TextButton(
            onPressed: () async {
              Navigator.of(context).pop();
              await WiseBluetoothPrint.connectPanda(hardwareAddress);
            },
            child: const Text("Panda"),
          ),
          TextButton(
            onPressed: () => Navigator.of(context).pop(),
            child: const Text("Close", style: TextStyle(color: Colors.red)),
          ),
        ],
      ),
    );
  }

  @override  
  Widget build(BuildContext context) {
  return MaterialApp(
    home: Scaffold(
      appBar: AppBar(
        title: const Text("Wise Bluetooth Print Plugin example"),
      ),
      body: Padding(
        padding: const EdgeInsets.fromLTRB(10, 0, 10, 0),
        child: Column(
          children: [
            Row(
              children: [
                TextButton(
                  onPressed: () {
                    // Your action when the button is pressed
                    WiseBluetoothPrint.printBluePrint();
                  },
                  child: const Text("PRINT BLUEPRINT"),
                ),
                TextButton(
                  onPressed: () {
                    // Your action when the button is pressed
                    WiseBluetoothPrint.disconnectBluePrint();
                  },
                  child: const Text("DISCONNECT BLUEPRINT"),
                ),
                TextButton(
                  onPressed: () {
                    // Your action when the button is pressed
                    WiseBluetoothPrint.printPanda();
                  },
                  child: const Text("PRINT PANDA"),
                ),
                TextButton(
                  onPressed: () {
                    // Your action when the button is pressed
                    WiseBluetoothPrint.disconnectPanda();
                  },
                  child: const Text("DISCONNECT PANDA"),
                ),
                  TextButton(
                  onPressed: () {
                    // Your action when the button is pressed
                    WiseBluetoothPrint.getPairedDevices();
                  },
                  child: const Text("Paired Device"),
                ),
              ],
            ),
            ListView.builder(
              shrinkWrap: true,
              itemCount: _devices.length,
              itemBuilder: (context, index) {
                return GestureDetector(
                  onTap: () =>
                      initPrint(context, _devices[index].hardwareAddress ?? ""),
                  child: Card(
                    elevation: 1,
                    shape: RoundedRectangleBorder(
                      borderRadius: BorderRadius.circular(5),
                    ),
                    child: Column(
                      crossAxisAlignment: CrossAxisAlignment.stretch,
                      children: [
                        ListTile(
                            title: Row(
                                mainAxisAlignment: MainAxisAlignment.spaceBetween,
                                children: [
                                  Text(_devices[index].name ?? ""),
                                  Text(_devices[index].hardwareAddress ?? "")
                                ]),
                            subtitle: Text(_devices[index].socketId ?? ""))
                      ],
                    ),
                  ),
                );
              },
            ),
          ],
        ),
      ),
    ),
  );
}
}
