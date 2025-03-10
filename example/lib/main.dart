import 'dart:convert';
import 'dart:developer';

import 'package:flutter/cupertino.dart';
import 'package:flutter/material.dart';
import 'dart:async';

import 'package:flutter/services.dart';
import 'package:permission_handler/permission_handler.dart';
import 'package:wise_bluetooth_print/classes/paired_device.dart';
import 'package:wise_bluetooth_print/wise_bluetooth_print.dart';
import 'package:wise_bluetooth_print_example/device.dart';
import 'package:http/http.dart' as http;
import 'package:image/image.dart' as img;

void main() {
  runApp(const MyApp());
}

class MyApp extends StatefulWidget {
  const MyApp({Key? key}) : super(key: key);

  @override
  State<MyApp> createState() => _MyAppState();
}

class _MyAppState extends State<MyApp> {
  TextEditingController foodEditingController = TextEditingController();
  TextEditingController drinkEditingController = TextEditingController();
  TextEditingController receiptEditingController = TextEditingController();

  late List<PairedDevice> _devices;
  List<Devices> pairedDevice = [];

  bool isPrinting = false;
  String? image;

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
    loadImg();
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

  void loadImg() async {
    String imageUrl =
        "https://e7.pngegg.com/pngimages/646/324/png-clipart-github-computer-icons-github-logo-monochrome.png";

    final response = await http.get(Uri.parse(imageUrl));
    if (response.statusCode == 200) {
      final imageData = response.bodyBytes;
      final originalImage = img.decodeImage(imageData);
      if (originalImage != null) {
        image = base64Encode(img.encodeJpg(originalImage));
      }
    } else {
      log('Failed to load image');
    }
  }

  Future<void> detailPrint(BuildContext context, String hardwareAddress,
      bool isPanda, bool isConnect) async {
    // You can add more language options other than ZPL and BZPL/ZPL II for printers
    // that don't support them.
    await showDialog(
      context: context,
      barrierDismissible: false,
      builder: (builder) {
        bool isLoading = false;

        return StatefulBuilder(builder: (context, setState) {
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

                                  value =
                                      await WiseBluetoothPrint.disconnectPanda(
                                          hardwareAddress);

                                  if (value) {
                                    setState(() {
                                      pairedDevice.removeWhere((e) =>
                                          e.hardwareAddress == hardwareAddress);
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

                                  bool value =
                                      await WiseBluetoothPrint.connectPanda(
                                          hardwareAddress);

                                  if (value) {
                                    setState(() {
                                      pairedDevice.add(Devices(
                                          hardwareAddress: hardwareAddress));
                                    });

                                    setState(() {
                                      isLoading = false;
                                    });

                                    Navigator.of(context).pop();
                                  } else {
                                    setState(() {
                                      isLoading = false;
                                    });
                                  }
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
        });
      },
    ).whenComplete(() {
      setState(() {});
    });
  }

  bool isPanda(String hardwareAddress) {
    return _devices.any((e) => e.hardwareAddress == hardwareAddress)
        ? (_devices
                .firstWhere((e) => e.hardwareAddress == hardwareAddress)
                .name
                ?.toLowerCase()
                .contains("mpt") ??
            false)
        : false;
  }

  Future<void> readyPrint(String type) async {
    final selectedDevices = pairedDevice.where((e) =>
        (type == "food" && (e.food ?? false)) ||
        (type == "drink" && (e.drink ?? false)) ||
        (type == "receipt" && (e.receipt ?? false)));

    final content = type == "food"
        ? foodEditingController.text
        : (type == "drink"
            ? drinkEditingController.text
            : receiptEditingController.text);

    for (var device in selectedDevices) {
      if (_devices.any((e) => e.hardwareAddress == device.hardwareAddress)) {
        try {
          final startTime = DateTime.now();
          final result = await WiseBluetoothPrint.printPanda(
            device.hardwareAddress ?? "",
            content,
            imageUrl: (type == "receipt" && image != null) ? image : null,
          );
          final endTime = DateTime.now();
          final duration = endTime.difference(startTime);

          print("Is print $result ${duration.inMilliseconds}");

          if (result == false) {
            await WiseBluetoothPrint.disconnectPanda(
                device.hardwareAddress ?? "");
            final value = await WiseBluetoothPrint.connectPanda(
                device.hardwareAddress ?? "");

            if (value) {
              await WiseBluetoothPrint.printPanda(
                device.hardwareAddress ?? "",
                content,
                imageUrl: (type == "receipt" && image != null) ? image : null,
              );
            }
          }
        } catch (e) {
          print(e.toString());
        }
      }
    }
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
                          onPressed: () async {
                            if (!isPrinting) {
                              if (foodEditingController.text.isEmpty &&
                                  pairedDevice.any((e) => e.food ?? false)) {
                                //showAlertDialog(context, "Please fill food TextField");
                              } else {
                                setState(() {
                                  isPrinting = true;
                                });

                                await readyPrint("food");

                                setState(() {
                                  isPrinting = false;
                                });
                              }
                            }
                          },
                          child: const Text("PRINT FOOD"),
                        ),
                        TextButton(
                          onPressed: () async {
                            if (!isPrinting) {
                              if (drinkEditingController.text.isEmpty &&
                                  pairedDevice.any((e) => e.drink ?? false)) {
                                //showAlertDialog(context, "Please fill drink TextField");
                              } else {
                                setState(() {
                                  isPrinting = true;
                                });

                                await readyPrint("drink");

                                setState(() {
                                  isPrinting = false;
                                });
                              }
                            }
                          },
                          child: const Text("PRINT DRINK"),
                        ),
                        TextButton(
                          onPressed: () async {
                            if (!isPrinting) {
                              if (receiptEditingController.text.isEmpty &&
                                  pairedDevice.any((e) => e.receipt ?? false)) {
                                //showAlertDialog(context, "Please fill receipt TextField");
                              } else {
                                setState(() {
                                  isPrinting = true;
                                });

                                await readyPrint("receipt");

                                setState(() {
                                  isPrinting = false;
                                });
                              }
                            }
                          },
                          child: const Text("PRINT RECEIPT"),
                        ),
                        TextButton(
                          onPressed: () async {
                            if (!isPrinting) {
                              if (foodEditingController.text.isEmpty &&
                                  pairedDevice.any((e) => e.food ?? false)) {
                                //showAlertDialog(context, "Please fill food TextField");
                              } else if (drinkEditingController.text.isEmpty &&
                                  pairedDevice.any((e) => e.drink ?? false)) {
                                //showAlertDialog(context, "Please fill drink TextField");
                              } else if (receiptEditingController
                                      .text.isEmpty &&
                                  pairedDevice.any((e) => e.receipt ?? false)) {
                                //showAlertDialog(context, "Please fill receipt TextField");
                              } else {
                                setState(() {
                                  isPrinting = true;
                                });

                                await readyPrint("receipt");
                                await Future.delayed(
                                    const Duration(seconds: 4));
                                await readyPrint("food");
                                await readyPrint("drink");

                                setState(() {
                                  isPrinting = false;
                                });
                              }
                            }
                          },
                          child: const Text("PRINT ALL"),
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
                      controller: foodEditingController,
                      placeholder: "Fill food content",
                    ),
                  ),
                  Padding(
                    padding: const EdgeInsets.all(8.0),
                    child: CupertinoTextField(
                      controller: drinkEditingController,
                      placeholder: "Fill drink content",
                    ),
                  ),
                  Padding(
                    padding: const EdgeInsets.all(8.0),
                    child: CupertinoTextField(
                      controller: receiptEditingController,
                      placeholder: "Fill receipt content",
                    ),
                  ),
                  Expanded(
                    child: ListView.builder(
                      shrinkWrap: true,
                      itemCount: _devices.length,
                      physics: const BouncingScrollPhysics(),
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
                                pairedDevice.any((e) =>
                                    e.hardwareAddress ==
                                    _devices[index].hardwareAddress));
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
                                        Text(_devices[index].hardwareAddress ??
                                            "")
                                      ]),
                                  subtitle: pairedDevice.any((e) =>
                                          e.hardwareAddress ==
                                          _devices[index].hardwareAddress)
                                      ? Builder(builder: (context) {
                                          int getIndex =
                                              pairedDevice.indexWhere((e) =>
                                                  e.hardwareAddress ==
                                                  _devices[index]
                                                      .hardwareAddress);

                                          return Row(
                                            children: [
                                              Padding(
                                                padding: const EdgeInsets.only(
                                                    right: 10),
                                                child: Row(
                                                  children: [
                                                    CupertinoCheckbox(
                                                      value:
                                                          pairedDevice[getIndex]
                                                                  .food ??
                                                              false,
                                                      onChanged: (value) {
                                                        setState(() {
                                                          pairedDevice[getIndex]
                                                              .food = value;
                                                        });
                                                      },
                                                    ),
                                                    const Text("Food"),
                                                  ],
                                                ),
                                              ),
                                              Padding(
                                                padding: const EdgeInsets.only(
                                                    right: 10),
                                                child: Row(
                                                  children: [
                                                    CupertinoCheckbox(
                                                      value:
                                                          pairedDevice[getIndex]
                                                                  .drink ??
                                                              false,
                                                      onChanged: (value) {
                                                        setState(() {
                                                          pairedDevice[getIndex]
                                                              .drink = value;
                                                        });
                                                      },
                                                    ),
                                                    const Text("Drink"),
                                                  ],
                                                ),
                                              ),
                                              Padding(
                                                padding: const EdgeInsets.only(
                                                    right: 10),
                                                child: Row(
                                                  children: [
                                                    CupertinoCheckbox(
                                                      value:
                                                          pairedDevice[getIndex]
                                                                  .receipt ??
                                                              false,
                                                      onChanged: (value) {
                                                        setState(() {
                                                          pairedDevice[getIndex]
                                                              .receipt = value;
                                                        });
                                                      },
                                                    ),
                                                    const Text("Receipt"),
                                                  ],
                                                ),
                                              ),
                                            ],
                                          );
                                        })
                                      : null,
                                  leading: pairedDevice.any((e) =>
                                          e.hardwareAddress ==
                                          _devices[index].hardwareAddress)
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
