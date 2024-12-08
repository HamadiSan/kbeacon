import 'package:flutter/services.dart';

class Kbeacon {
  static const MethodChannel _channel = MethodChannel('kbeacon');

  static Future<void> scanAndConnect() async {
    await _channel.invokeMethod('scanAndConnect');
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
