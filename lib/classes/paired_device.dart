class PairedDevice {
  int? index;
  String? name;
  String? hardwareAddress;
  String? socketId;
  bool? food;
  bool? drink;
  bool? receipt;

  PairedDevice(
      {required this.index,
      required this.name,
      required this.hardwareAddress,
      required this.socketId,
      required this.food,
      required this.drink,
      required this.receipt});

  PairedDevice.fromJson(Map<String, dynamic> json) {
    index = json['index'];
    name = json['name'];
    hardwareAddress = json['hardwareAddress'];
    socketId = json['socketId'];
    food = json['food'];
    drink = json['drink'];
    receipt = json['receipt'];
  }

  Map<String, dynamic> toJson() {
    final Map<String, dynamic> data = <String, dynamic>{};
    data['index'] = index;
    data['name'] = name;
    data['hardwareAddress'] = hardwareAddress;
    data['socketId'] = socketId;
    data['food'] = food;
    data['drink'] = drink;
    data['receipt'] = receipt;
    return data;
  }

  PairedDevice empty = PairedDevice(
      index: 0,
      name: '',
      hardwareAddress: '',
      socketId: '',
      food: false,
      drink: false,
      receipt: false);

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

  PairedDevice copyWith({
    int? index,
    String? name,
    String? hardwareAddress,
    String? socketId,
    bool? food,
    bool? drink,
    bool? receipt,
  }) {
    return PairedDevice(
      index: index ?? this.index,
      name: name ?? this.name,
      hardwareAddress: hardwareAddress ?? this.hardwareAddress,
      socketId: socketId ?? this.socketId,
      food: food ?? this.food,
      drink: drink ?? this.drink,
      receipt: receipt ?? this.receipt,
    );
  }
}
