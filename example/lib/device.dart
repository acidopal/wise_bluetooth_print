class Devices {
  String? hardwareAddress;
  int? index;
  String? type;
  bool? food;
  bool? drink;
  bool? receipt;

  Devices(
      {this.hardwareAddress,
      this.index,
      this.type,
      this.food,
      this.drink,
      this.receipt});

  Devices.fromJson(Map<String, dynamic> json) {
    hardwareAddress = json['hardware_address'];
    index = json['index'];
    type = json['type'];
    food = json['food'];
    drink = json['drink'];
    receipt = json['receipt'];
  }

  Map<String, dynamic> toJson() {
    final Map<String, dynamic> data = <String, dynamic>{};
    data['hardware_address'] = hardwareAddress;
    data['index'] = index;
    data['type'] = type;
    data['food'] = food;
    data['drink'] = drink;
    data['receipt'] = receipt;
    return data;
  }
}
