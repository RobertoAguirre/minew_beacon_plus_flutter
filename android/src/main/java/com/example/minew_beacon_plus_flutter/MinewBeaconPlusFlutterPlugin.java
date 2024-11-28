package com.example.minew_beacon_plus_flutter;

import android.util.Log;
import androidx.annotation.NonNull;
import com.minew.beaconplus.sdk.MTCentralManager;
import com.minew.beaconplus.sdk.MTFrameHandler;
import com.minew.beaconplus.sdk.MTPeripheral;
import com.minew.beaconplus.sdk.frames.MinewFrame;
import com.minew.beaconplus.sdk.frames.TemperatureFrame;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.Map;

import io.flutter.embedding.engine.plugins.FlutterPlugin;
import io.flutter.plugin.common.EventChannel;
import io.flutter.plugin.common.MethodCall;
import io.flutter.plugin.common.MethodChannel;
import io.flutter.plugin.common.MethodChannel.MethodCallHandler;
import io.flutter.plugin.common.MethodChannel.Result;

/** MinewBeaconPlusFlutterPlugin */
public class MinewBeaconPlusFlutterPlugin implements FlutterPlugin, MethodCallHandler {
  private MethodChannel channel;
  private EventChannel scanDevicesEventChannel;
  private MTCentralManager mtCentralManager;
  private final ArrayList<MTPeripheral> beaconMtPeriphericals = new ArrayList<>();

  @Override
  public void onAttachedToEngine(@NonNull FlutterPluginBinding flutterPluginBinding) {
    // Init plugin
    mtCentralManager = MTCentralManager.getInstance(flutterPluginBinding.getApplicationContext());
    mtCentralManager.startService();
    mtCentralManager.startScan();

    // Method Channels
    channel = new MethodChannel(flutterPluginBinding.getBinaryMessenger(), "minew_beacon_plus_flutter");
    channel.setMethodCallHandler(this);

    // Event channels
    scanDevicesEventChannel = new EventChannel(flutterPluginBinding.getBinaryMessenger(), "minew_beacon_devices_scan");
    scanDevicesEventChannel.setStreamHandler(scanDevicesEventChannelHandler);
  }

  @Override
  public void onMethodCall(@NonNull MethodCall call, @NonNull Result result) {
    if (call.method.equals("getPlatformVersion")) {
      result.success("Android " + android.os.Build.VERSION.RELEASE);
    }
    if (call.method.equals("startScan")) {
      startScan(call, result);
    }
    if (call.method.equals("stopScan")) {
      stopScan(call, result);
    }
    else {
      result.notImplemented();
    }
  }

  private void startScan(@NonNull MethodCall call, @NonNull Result result){
    try {
      mtCentralManager.startScan();
      result.success(true);
    }catch (Exception e){
      result.error("0", e.getLocalizedMessage(), null);
    }
  }

  private void stopScan(@NonNull MethodCall call, @NonNull Result result){
    try {
      mtCentralManager.stopScan();
      result.success(true);
    }catch (Exception e){
      result.error("0", e.getLocalizedMessage(), null);
    }
  }

  private EventChannel.StreamHandler scanDevicesEventChannelHandler = new EventChannel.StreamHandler() {
    private EventChannel.EventSink eventSink = null;
    private final ArrayList<Map<String,Object>> peripheralsList = new ArrayList<>();

    @Override
    public void onListen(Object arguments, EventChannel.EventSink sink) {
      this.eventSink = sink;

      try {
        mtCentralManager.setMTCentralManagerListener(peripherals -> {
          // Update beaconMtPeriphericals
          beaconMtPeriphericals.clear();
          beaconMtPeriphericals.addAll(peripherals);

          // Create peripheralsList
          peripheralsList.clear();

          for (MTPeripheral mtPeripheral: peripherals) {
            // get FrameHandler of a device.
            final MTFrameHandler mtFrameHandler = mtPeripheral.mMTFrameHandler;
            final String mac = mtFrameHandler.getMac();
            final String name = mtFrameHandler.getName();
            final int battery = mtFrameHandler.getBattery();
            final int rssi = mtFrameHandler.getRssi();
            final ArrayList<MinewFrame> advFrames = mtFrameHandler.getAdvFrames();

            // Create frameItems object
            final ArrayList<HashMap<String, Object>> frameItems = new ArrayList<>();
            String temperature = "N/A";

            for (MinewFrame frame : advFrames) {
              HashMap<String, Object> frameItem = new HashMap<>();
              frameItem.put("type", frame.getFrameType().toString());
              frameItem.put("slot", frame.getCurSlot());

              if (frame instanceof TemperatureFrame) {
                TemperatureFrame tempFrame = (TemperatureFrame) frame;
                double tempValue = tempFrame.getValue();
                frameItem.put("temperature", tempValue);
                temperature = String.valueOf(tempValue);
                Log.d("MinewBeaconPlugin", "Temperature found: " + tempValue);
              }

              frameItems.add(frameItem);
            }

            // Create Device Info object
            final Map<String, Object> device = new HashMap<>();
            device.put("name", name);
            device.put("mac", mac);
            device.put("battery", battery);
            device.put("rssi", rssi);
            device.put("advFrames", frameItems);
            device.put("temperature", temperature);

            peripheralsList.add(device);
          }

          if(eventSink != null){
            eventSink.success(peripheralsList);
          }
        });
      } catch (Exception e) {
        Log.println(Log.ERROR, null, e.getMessage());
        mtCentralManager.stopScan();
      }
    }

    @Override
    public void onCancel(Object arguments) {
    }
  };

  @Override
  public void onDetachedFromEngine(@NonNull FlutterPluginBinding binding) {
    channel.setMethodCallHandler(null);
  }
}