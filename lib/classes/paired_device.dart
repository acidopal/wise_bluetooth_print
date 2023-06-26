class PairedDevice {
  String name;
  String hardwareAddress;
  String socketId;
  bool food;
  bool drink;
  bool receipt;

  PairedDevice(
      {required this.name,
      required this.hardwareAddress,
      required this.socketId,
      required this.food,
      required this.drink,
      required this.receipt});

  static PairedDevice empty = PairedDevice(
      name: '',
      hardwareAddress: '',
      socketId: '',
      food: false,
      drink: false,
      receipt: false);

  factory PairedDevice.fromJson(Map<String, dynamic> json) {
    return PairedDevice(
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
