package com.gateit.kbeacon;

import android.content.Context;
import android.util.Log;

import androidx.annotation.NonNull;

import com.kkmcn.kbeaconlib2.*;
import com.kkmcn.kbeaconlib2.KBAdvPackage.KBAdvPacketBase;
import com.kkmcn.kbeaconlib2.KBAdvPackage.KBAdvPacketIBeacon;
import com.kkmcn.kbeaconlib2.KBAdvPackage.KBAdvType;


import java.util.HashMap;
import java.util.Locale;
import java.util.Map;
import java.util.Timer;
import java.util.TimerTask;

import io.flutter.embedding.engine.plugins.FlutterPlugin;
import io.flutter.plugin.common.MethodCall;
import io.flutter.plugin.common.MethodChannel;

/** KbeaconPlugin */
public class KbeaconPlugin implements FlutterPlugin, MethodChannel.MethodCallHandler, KBeacon.NotifyDataDelegate {

  private static final String TAG = "KbeaconPlugin";
  private static final String BEACON_UUID = "your-hardcoded-uuid";
  private static final String BEACON_PASSWORD = "0000000000000000"; // Default password
  private boolean isConnecting = false;
  private MethodChannel channel;
  private KBeaconsMgr mBeaconMgr;
  private KBeacon targetBeacon;
  private Context context;

  @Override
  public void onAttachedToEngine(@NonNull FlutterPluginBinding flutterPluginBinding) {
    channel = new MethodChannel(flutterPluginBinding.getBinaryMessenger(), "kbeacon");
    channel.setMethodCallHandler(this);
    this.context = flutterPluginBinding.getApplicationContext();
    mBeaconMgr = KBeaconsMgr.sharedBeaconManager(context);
  }

  @Override
  public void onMethodCall(@NonNull MethodCall call, @NonNull MethodChannel.Result result) {
    if (call.method.equals("scanAndConnect")) {
      // Extract the timeout parameter
      int timeout = call.argument("timeout");
      scanAndConnect(timeout, result);
    } else if (call.method.equals("stopScanning")) {
      stopScanning(result);
    } else {
      result.notImplemented();
    }
  }



  private void scanAndConnect(int timeout, @NonNull MethodChannel.Result result) {
    if (mBeaconMgr == null) {
      result.error("MANAGER_NOT_INITIALIZED", "Beacon manager not initialized", null);
      return;
    }
    if (isConnecting) {
      result.error("ALREADY_CONNECTING", "A connection is already in progress", null);
      return;
    }
    isConnecting = true;
    // Set a timeout for the scanning operation
    final Timer timer = new Timer();
    timer.schedule(new TimerTask() {
      @Override
      public void run() {
        mBeaconMgr.stopScanning();
        isConnecting = false;
        result.success(false); // Return false if connection did not occur within timeout
      }
    }, timeout);

    mBeaconMgr.delegate = new KBeaconsMgr.KBeaconMgrDelegate() {
      @Override
      public void onBeaconDiscovered(KBeacon[] beacons) {
        for (KBeacon beacon : beacons) {
          //get beacon adv common info
          Log.v(TAG, "beacon mac:" + beacon.getMac());
          Log.v(TAG, "beacon name:" + beacon.getName());
          Log.v(TAG, "beacon rssi:" + beacon.getRssi());

          //get adv packet
          for (KBAdvPacketBase advPacket : beacon.allAdvPackets()) {
            if(advPacket.getAdvType() == KBAdvType.IBeacon){
              KBAdvPacketIBeacon advIBeacon = (KBAdvPacketIBeacon) advPacket;
              Log.v(TAG, "iBeacon uuid:" + advIBeacon.getUuid());
              Log.v(TAG, "iBeacon major:" + advIBeacon.getMajorID());
              Log.v(TAG, "iBeacon minor:" + advIBeacon.getMinorID());
              if(advIBeacon.getUuid().equals(BEACON_UUID)){
                // Stop scanning once the target beacon is found
                mBeaconMgr.stopScanning();
                if (timer != null) {
                  timer.cancel();
                }
                targetBeacon = beacon;
                connectToBeacon(timeout, result);
                return;
              }
            }
          }
        }
      }

      @Override
      public void onCentralBleStateChang(int nNewState) {

      }

      @Override
      public void onScanFailed(int errorCode) {


      }
    };


    // Start scanning
    mBeaconMgr.startScanning();
  }

  private void connectToBeacon(int timeout, @NonNull MethodChannel.Result result) {
    if (targetBeacon == null) {
      result.error("BEACON_NOT_FOUND", "Target beacon not found during scanning", null);
      isConnecting = false;
      return;
    }

    try {
      KBConnPara connPara = new KBConnPara();
      connPara.syncUtcTime = true;
      connPara.readCommPara = true;
      connPara.readTriggerPara = true;


      targetBeacon.connectEnhanced(BEACON_PASSWORD, timeout, connPara, new KBeacon.ConnStateDelegate() {
        @Override
        public void onConnStateChange(KBeacon beacon, KBConnState state, int reason) {
          if (state == KBConnState.Connected) {
            Log.i(TAG, "Beacon connected: " + beacon.getMac());
            result.success(true); // Return true on successful connection
            isConnecting = false;
            listenForEvents();
          } else if (state == KBConnState.Disconnected) {
            Log.w(TAG, "Beacon disconnected: " + reason);
            isConnecting = false;
            result.success(false); // Return false if disconnected
          }
        }
      });
    } catch (Exception e) {
      isConnecting = false;
      Log.e(TAG, "Failed to connect to beacon: " + e.getMessage());
      result.error("CONNECTION_FAILED", e.getMessage(), null);
    }
  }




  private void stopScanning(MethodChannel.Result result) {
    if (mBeaconMgr != null) {
      mBeaconMgr.stopScanning();
      mBeaconMgr.clearBeacons();
      result.success("Scanning stopped");
    } else {
      result.error("MANAGER_NOT_INITIALIZED", "Beacon manager not initialized", null);
    }
  }

  private void listenForEvents() {
    if (targetBeacon == null) {
      Log.e(TAG, "No target beacon to listen for events");
      return;
    }

    targetBeacon.subscribeSensorDataNotify(null, KbeaconPlugin.this , (success, error) -> {
      if (success) {
        Log.i(TAG, "Subscribed to sensor data notifications");
      } else {
        Log.e(TAG, "Failed to subscribe: " + (error != null ? error.getMessage() : "unknown error"));
      }
    });

  }


  public void onNotifyDataReceived(KBeacon beacon, int eventType, byte[] sensorData) {
    Log.i(TAG, "Event received from beacon: EventType=" + eventType);

    // Prepare data to forward to Flutter
    Map<String, Object> event = new HashMap<>();
    event.put("eventType", eventType);
    event.put("beaconMac", beacon.getMac());
    event.put("sensorData", sensorData != null ? new String(sensorData) : null);

    // Send event to Flutter
    channel.invokeMethod("onEventReceived", event);
  }

  @Override
  public void onDetachedFromEngine(@NonNull FlutterPluginBinding binding) {
    if (mBeaconMgr != null) {
      mBeaconMgr.stopScanning();
      mBeaconMgr.clearBeacons();
    }
  }
}
