class PairedDevice {
  int index;
  String name;
  String hardwareAddress;
  String socketId;
  bool food;
  bool drink;
  bool receipt;

  PairedDevice(
      {required this.index,
      required this.name,
      required this.hardwareAddress,
      required this.socketId,
      required this.food,
      required this.drink,
      required this.receipt});

  static PairedDevice empty = PairedDevice(
      index: 0,
      name: '',
      hardwareAddress: '',
      socketId: '',
      food: false,
      drink: false,
      receipt: false);

  Map<String, dynamic> toJson() {
    return {
      'index': index,
      'name': name,
      'hardwareAddress': hardwareAddress,
      'socketId': socketId,
      'food': food,
      'drink': drink,
      'receipt': receipt,
    };
  }

  factory PairedDevice.fromJson(Map<String, dynamic> json) {
    return PairedDevice(
      index: json['index'],
      name: json['name'],
      hardwareAddress: json['hardwareAddress'],
      socketId: json['socketId'],
      food: json['food'],
      drink: json['drink'],
      receipt: json['receipt'],
    );
  }

  static List<PairedDevice> fromJsonToList(List<dynamic> json) {
    List<PairedDevice> devices = <PairedDevice>[];
    for (int i = 0; i < json.length; i++) {
      devices.add(PairedDevice(
          index: json[i]['index'],
          name: json[i]['name'],
          hardwareAddress: json[i]['hardwareAddress'],
          socketId: json[i]['socketId'],
          food: json[i]['food'],
          drink: json[i]['drink'],
          receipt: json[i]['receipt']));
    }
    return devices;
  }
}
