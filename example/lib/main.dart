import 'package:flutter/material.dart';
import 'package:kbeacon/kbeacon.dart';

void main() {
  runApp(const MyApp());
}

class MyApp extends StatefulWidget {
  const MyApp({super.key});

  @override
  State<MyApp> createState() => _MyAppState();
}

class _MyAppState extends State<MyApp> {
  @override
  void initState() {
    super.initState();
    Kbeacon.setEventListener((event) {
      print('Event received: $event');
    });
  }

  @override
  Widget build(BuildContext context) {
    return MaterialApp(
      home: Scaffold(
        appBar: AppBar(
          title: const Text('Plugin example app'),
        ),
        body: ElevatedButton(
          onPressed: () {
            Kbeacon.scanAndConnect(5000, "7777772E-6B6B-6D63-6E2E-636F6D000001", "00000000");
          },
          child: const Text('ScanAndConnect'),
        ),
      ),
    );
  }
}
