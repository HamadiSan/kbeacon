import 'package:flutter/services.dart';

class Kbeacon {
  static const MethodChannel _channel = MethodChannel('kbeacon');

  static Future<void> scanAndConnect(int timeout, String uuid, String password) async {
    await _channel.invokeMethod('scanAndConnect', {'timeout': timeout, 'uuid': uuid, 'password': password});
  }

  static Future<void> stopScanning() async {
    await _channel.invokeMethod('stopScanning');
  }

  static void setEventListener(Function(Map<String, dynamic>) listener) {
    _channel.setMethodCallHandler((call) async {
      if (call.method == 'onEventReceived') {
        listener(call.arguments as Map<String, dynamic>);
      }
    });
  }
}
