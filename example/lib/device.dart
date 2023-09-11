class Devices {
  String? hardwareAddress;
  bool? food;
  bool? drink;
  bool? receipt;

  Devices({this.hardwareAddress, this.food, this.drink, this.receipt});

  Devices.fromJson(Map<String, dynamic> json) {
    hardwareAddress = json['hardware_address'];
    food = json['food'];
    drink = json['drink'];
    receipt = json['receipt'];
  }

  Map<String, dynamic> toJson() {
    final Map<String, dynamic> data = <String, dynamic>{};
    data['hardware_address'] = hardwareAddress;
    data['food'] = food;
    data['drink'] = drink;
    data['receipt'] = receipt;
    return data;
  }
}
