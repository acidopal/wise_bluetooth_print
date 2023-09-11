import 'package:flutter/cupertino.dart';
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
  TextEditingController textEditingController = TextEditingController();

  late List<PairedDevice> _devices;
  List<String> pairedDevice = [];

  bool isLoading = false;
  bool isPrinting = false;

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

  Future<void> detailPrint(BuildContext context, String hardwareAddress,
      bool isPanda, bool isConnect) async {
    // You can add more language options other than ZPL and BZPL/ZPL II for printers
    // that don't support them.
    await showDialog(
      context: context,
      barrierDismissible: false,
      builder: (builder) => StatefulBuilder(builder: (context, setState) {
        return AlertDialog(
          title:
              Text(isConnect ? "Printer terconnect" : "Select printer brand"),
          content: Text(
              "PUNTEEEEENNNNN untuk printer ${isPanda ? "Panda" : "Blueprint"}"),
          actions: [
            isLoading
                ? const LinearProgressIndicator()
                : Row(
                    children: [
                      isConnect
                          ? TextButton(
                              onPressed: () async {
                                setState(() {
                                  isLoading = true;
                                });

                                bool? value;

                                if (isPanda) {
                                  value = await WiseBluetoothPrint
                                      .disconnectPanda();
                                } else {
                                  value = await WiseBluetoothPrint
                                      .disconnectBluePrint();
                                }

                                if (value) {
                                  setState(() {
                                    pairedDevice.remove(hardwareAddress);
                                  });
                                }

                                setState(() {
                                  isLoading = false;
                                });

                                Navigator.of(context).pop();
                              },
                              child: const Text("Disconnect"),
                            )
                          : TextButton(
                              onPressed: () async {
                                setState(() {
                                  isLoading = true;
                                });

                                bool? value;

                                if (isPanda) {
                                  value = await WiseBluetoothPrint.connectPanda(
                                      hardwareAddress);
                                } else {
                                  value =
                                      await WiseBluetoothPrint.connectBluePrint(
                                          hardwareAddress);
                                }

                                if (value) {
                                  setState(() {
                                    pairedDevice.add(hardwareAddress);
                                  });
                                }

                                setState(() {
                                  isLoading = false;
                                });

                                Navigator.of(context).pop();
                              },
                              child: const Text("Connect"),
                            ),
                      TextButton(
                        onPressed: () => Navigator.of(context).pop(),
                        child: const Text("Close",
                            style: TextStyle(color: Colors.red)),
                      ),
                    ],
                  ),
          ],
        );
      }),
    ).whenComplete(() {
      setState(() {});
    });
  }

  Future<void> showAlertDialog(BuildContext context, String content) async {
    await showDialog(
      context: context,
      builder: (builder) => StatefulBuilder(builder: (context, setState) {
        return AlertDialog(
          title: const Text("Information"),
          content: Text(content),
          actions: [
            TextButton(
              onPressed: () => Navigator.of(context).pop(),
              child: const Text("Close", style: TextStyle(color: Colors.red)),
            ),
          ],
        );
      }),
    ).whenComplete(() {
      setState(() {});
    });
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
                          onPressed: () async {
                            if (textEditingController.text.isEmpty) {
                              showAlertDialog(context, "Please fill TextField");
                            } else {
                              setState(() {
                                isPrinting = true;
                              });

                              for (var i = 0; i < pairedDevice.length; i++) {
                                await WiseBluetoothPrint.disconnectPanda()
                                    .then((result) async {
                                  await WiseBluetoothPrint.connectPanda(
                                          pairedDevice[i])
                                      .then((value) async {
                                    if (value) {
                                      await WiseBluetoothPrint.printPanda(
                                          textEditingController.text);
                                    }
                                  });
                                });
                              }

                              setState(() {
                                isPrinting = false;
                              });
                            }
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
                  Padding(
                    padding: const EdgeInsets.all(8.0),
                    child: CupertinoTextField(
                      controller: textEditingController,
                      placeholder: "Fill content",
                    ),
                  ),
                  ListView.builder(
                    shrinkWrap: true,
                    itemCount: _devices.length,
                    itemBuilder: (context, index) {
                      return GestureDetector(
                        onTap: () {
                          detailPrint(
                              context,
                              _devices[index].hardwareAddress ?? "",
                              _devices[index]
                                      .name
                                      ?.toLowerCase()
                                      .contains("mpt") ??
                                  false,
                              pairedDevice.any(
                                  (e) => e == _devices[index].hardwareAddress));
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
          if (isPrinting)
            Container(
                color: Colors.black.withOpacity(0.12),
                child: const Center(child: CircularProgressIndicator())),
        ],
      ),
    );
  }
}
