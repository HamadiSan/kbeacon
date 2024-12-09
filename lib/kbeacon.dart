import 'package:flutter/services.dart';

class Kbeacon {
  static const MethodChannel _channel = MethodChannel('kbeacon');

  static Future<void> scanAndConnect(
      int timeout, String uuid, String password) async {
    try {
      await _channel.invokeMethod('scanAndConnect',
          {'timeout': timeout, 'uuid': uuid, 'password': password});
    } on PlatformException catch (e) {
      print('Failed to scan and connect: $e');
    }
  }

  static Future<void> stopScanning() async {
    try {
      await _channel.invokeMethod('stopScanning');
    } on PlatformException catch (e) {
      print('Failed to stop scanning: $e');
    }
  }

  static Future<void> emitKeyEvent(int keyCode) async {
    try {
      await _channel.invokeMethod('emitKeyEvent', {'keyCode': keyCode});
    } on PlatformException catch (e) {
      print('Failed to emit key event: $e');
    }
  }

  static void setEventListener(Function(Map<String, dynamic>) listener) {
    print('Setting up event listener...');
    _channel.setMethodCallHandler((call) async {
      print('Method call received: ${call.method}');
      if (call.method == 'onEventReceived') {
        print('Event received in Dart: ${call.arguments}');
        try {
          // Convert the Map<Object?, Object?> to Map<String, dynamic>
          final Map<Object?, Object?> rawMap =
              call.arguments as Map<Object?, Object?>;
          final Map<String, dynamic> eventMap =
              Map<String, dynamic>.from(rawMap);

          listener(eventMap);
          print('Listener executed successfully');
        } catch (e) {
          print('Error in listener: $e');
        }
      }
      return null;
    });
  }
}
