import 'package:flutter/material.dart';
import 'dart:async';

import 'package:flutter/services.dart';
import 'package:permission_handler/permission_handler.dart';
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
  List<String> pairedDevice = [];

  bool isLoading = false;

  @override
  void initState() {
    super.initState();
    _devices = <PairedDevice>[];
    [
      Permission.bluetooth,
      Permission.bluetoothConnect,
      Permission.bluetoothAdvertise,
      Permission.bluetoothScan,
      Permission.location,
      Permission.locationAlways,
      Permission.locationWhenInUse,
    ].request();

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
        content: const Text("PUNTEEEEENNNNN"),
        actions: [
          TextButton(
            onPressed: () async {
              Navigator.of(context).pop();

              setState(() {
                isLoading = true;
              });

              await WiseBluetoothPrint.connectBluePrint(hardwareAddress)
                  .then((value) {
                if (value) {
                  setState(() {
                    pairedDevice.add(hardwareAddress);
                  });
                }

                setState(() {
                  isLoading = false;
                });
              });
            },
            child: const Text("Blueprint"),
          ),
          TextButton(
            onPressed: () async {
              Navigator.of(context).pop();

              setState(() {
                isLoading = true;
              });

              await WiseBluetoothPrint.connectPanda(hardwareAddress)
                  .then((value) {
                if (value) {
                  setState(() {
                    pairedDevice.add(hardwareAddress);
                  });
                }

                setState(() {
                  isLoading = false;
                });
              });
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

  void detailPrint(BuildContext context, String hardwareAddress, bool isPanda) {
    // You can add more language options other than ZPL and BZPL/ZPL II for printers
    // that don't support them.
    showDialog(
      context: context,
      builder: (builder) => AlertDialog(
        title: const Text("Printer terconnect"),
        content: const Text("PUNTEEEEENNNNN"),
        actions: [
          TextButton(
            onPressed: () async {
              Navigator.of(context).pop();

              setState(() {
                isLoading = true;
              });

              bool? value;

              if (isPanda) {
                value = await WiseBluetoothPrint.disconnectPanda();
              } else {
                value = await WiseBluetoothPrint.disconnectBluePrint();
              }

              if (value) {
                setState(() {
                  pairedDevice.remove(hardwareAddress);
                });
              }

              setState(() {
                isLoading = false;
              });
            },
            child: const Text("Disconnect"),
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
      home: Stack(
        children: [
          Scaffold(
            appBar: AppBar(
              title: const Text("Wise Bluetooth Print Plugin example"),
            ),
            body: Padding(
              padding: const EdgeInsets.fromLTRB(10, 0, 10, 0),
              child: Column(
                children: [
                  SizedBox(
                    height: 40,
                    child: ListView(
                      shrinkWrap: true,
                      scrollDirection: Axis.horizontal,
                      physics: const BouncingScrollPhysics(),
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
                            WiseBluetoothPrint.printPanda();
                          },
                          child: const Text("PRINT PANDA"),
                        ),
                        TextButton(
                          onPressed: () {
                            initPlatformState();
                          },
                          child: const Text("Paired Device"),
                        ),
                      ],
                    ),
                  ),
                  ListView.builder(
                    shrinkWrap: true,
                    itemCount: _devices.length,
                    itemBuilder: (context, index) {
                      return GestureDetector(
                        onTap: () {
                          if (pairedDevice.any(
                              (e) => e == _devices[index].hardwareAddress)) {
                            detailPrint(
                                context,
                                _devices[index].hardwareAddress ?? "",
                                _devices[index]
                                        .name
                                        ?.toLowerCase()
                                        .contains("mpt") ??
                                    false);
                          } else {
                            initPrint(
                                context, _devices[index].hardwareAddress ?? "");
                          }
                        },
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
                                    mainAxisAlignment:
                                        MainAxisAlignment.spaceBetween,
                                    children: [
                                      Text(_devices[index].name ?? ""),
                                      Text(
                                          _devices[index].hardwareAddress ?? "")
                                    ]),
                                subtitle: Text(_devices[index].socketId ?? ""),
                                leading: pairedDevice.any((e) =>
                                        e == _devices[index].hardwareAddress)
                                    ? const Icon(
                                        Icons.check_circle_outline_outlined)
                                    : null,
                              )
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
          if (isLoading)
            Container(
                color: Colors.black.withOpacity(0.12),
                child: const Center(child: CircularProgressIndicator())),
        ],
      ),
    );
  }
}
