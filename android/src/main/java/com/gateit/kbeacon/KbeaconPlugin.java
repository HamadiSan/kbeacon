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
  private static final String BEACON_PASSWORD = "0000000000000000"; // Default password
  private boolean isConnecting = false;
  private boolean resultHandled = false;
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
      int timeout = call.argument("timeout");
      String uuid = call.argument("uuid");
      String password = call.argument("password");
      scanAndConnect(timeout, uuid, password, result);
    } else if (call.method.equals("stopScanning")) {
      stopScanning(result);
    } else {
      result.notImplemented();
    }
  }

  private void scanAndConnect(int timeout, String uuid, String password, @NonNull MethodChannel.Result result) {
    if (mBeaconMgr == null) {
      completeResult(result, () -> result.error("MANAGER_NOT_INITIALIZED", "Beacon manager not initialized", null));
      return;
    }
    if (isConnecting) {
      completeResult(result, () -> result.error("ALREADY_CONNECTING", "A connection is already in progress", null));
      return;
    }

    isConnecting = true;
    resultHandled = false;

    final Timer timer = new Timer();
    timer.schedule(new TimerTask() {
      @Override
      public void run() {
        if (!resultHandled) {
          completeResult(result, () -> result.success(false));
        }
        Log.e(TAG, "Timeout reached! Stopping scanning");
        mBeaconMgr.stopScanning();
        isConnecting = false;
      }
    }, timeout* 1000L);

    mBeaconMgr.delegate = new KBeaconsMgr.KBeaconMgrDelegate() {
      @Override
      public void onBeaconDiscovered(KBeacon[] beacons) {
        for (KBeacon beacon : beacons) {
          for (KBAdvPacketBase advPacket : beacon.allAdvPackets()) {
            if (advPacket.getAdvType() == KBAdvType.IBeacon) {
              KBAdvPacketIBeacon advIBeacon = (KBAdvPacketIBeacon) advPacket;
              if (advIBeacon.getUuid().equals(uuid)) {
                // Log
                Log.e(TAG, "Beacon with UUID " + uuid + "Found. Stopping Scanning!");
                mBeaconMgr.stopScanning();
                if (timer != null) {
                  timer.cancel();
                }
                targetBeacon = beacon;
                connectToBeacon(timeout, password, result);
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
        if (!resultHandled) {
          completeResult(result, () -> result.error("SCAN_FAILED", "Scan failed with error code: " + errorCode, null));
        }
      }
    };

    mBeaconMgr.startScanning();
  }

  private void connectToBeacon(int timeout, String password, @NonNull MethodChannel.Result result) {
    if (targetBeacon == null) {
      completeResult(result, () -> result.error("BEACON_NOT_FOUND", "Target beacon not found during scanning", null));
      isConnecting = false;
      return;
    }

    try {
      KBConnPara connPara = new KBConnPara();
      connPara.syncUtcTime = true;
      connPara.readCommPara = true;
      connPara.readTriggerPara = true;

      targetBeacon.connectEnhanced(password, timeout, connPara, new KBeacon.ConnStateDelegate() {
        @Override
        public void onConnStateChange(KBeacon beacon, KBConnState state, int reason) {
          if (state == KBConnState.Connected) {
            Log.e(TAG, "Connected to iBeacon with UUID" + beacon.getMac() + " Successfully" + reason);
            completeResult(result, () -> result.success(true));
            isConnecting = false;
            listenForEvents();
          } else if (state == KBConnState.Disconnected) {
            if (!resultHandled) {
              completeResult(result, () -> result.success(false));
            }
            Log.e(TAG, "Disconnect from iBeacon with UUID" + beacon.getMac() + " due to " + reason);
            isConnecting = false;
          }
        }
      });
    } catch (Exception e) {
      isConnecting = false;
      completeResult(result, () -> result.error("CONNECTION_FAILED", e.getMessage(), null));
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

    targetBeacon.subscribeSensorDataNotify(null, KbeaconPlugin.this, (success, error) -> {
      if (success) {
        Log.i(TAG, "Subscribed to sensor data notifications");
      } else {
        Log.e(TAG, "Failed to subscribe: " + (error != null ? error.getMessage() : "unknown error"));
      }
    });
  }

  private void completeResult(MethodChannel.Result result, Runnable completion) {
    if (!resultHandled) {
      completion.run();
      resultHandled = true;
    }
  }

  public void onNotifyDataReceived(KBeacon beacon, int eventType, byte[] sensorData) {
    Log.i(TAG, "Event received from beacon: EventType=" + eventType);

    Map<String, Object> event = new HashMap<>();
    event.put("eventType", eventType);
    event.put("beaconMac", beacon.getMac());
    event.put("sensorData", sensorData != null ? new String(sensorData) : null);

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
