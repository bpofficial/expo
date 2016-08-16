// Copyright 2015-present 650 Industries. All rights reserved.

package versioned.host.exp.exponent.modules.api;

import org.json.JSONException;
import org.json.JSONObject;

import javax.inject.Inject;

import com.facebook.react.bridge.Arguments;
import com.facebook.react.bridge.Promise;
import com.facebook.react.bridge.ReactApplicationContext;
import com.facebook.react.bridge.ReactContextBaseJavaModule;
import com.facebook.react.bridge.ReactMethod;
import com.facebook.react.bridge.ReadableMap;
import com.facebook.react.bridge.WritableMap;
import host.exp.exponent.ExponentApplication;
import host.exp.exponent.ExponentManifest;
import host.exp.exponent.modules.ExponentKernelModule;
import host.exp.exponent.storage.ExponentSharedPreferences;

public class NotificationsModule extends ReactContextBaseJavaModule {

  @Inject
  ExponentSharedPreferences mExponentSharedPreferences;

  private final JSONObject mManifest;

  public NotificationsModule(ReactApplicationContext reactContext,
                             ExponentApplication application,
                             JSONObject manifest) {
    super(reactContext);
    application.getAppComponent().inject(this);
    mManifest = manifest;
  }

  @Override
  public String getName() {
    return "ExponentNotifications";
  }

  @ReactMethod
  public void getExponentPushTokenAsync(final Promise promise) {
    String uuid = mExponentSharedPreferences.getUUID();
    if (uuid == null) {
      // This should have been set by RegistrationIntentService when Activity was created/resumed.
      promise.reject("Couldn't get GCM token on device.");
      return;
    }

    WritableMap params = Arguments.createMap();
    params.putString("deviceId", uuid);
    try {
      params.putString("experienceId", mManifest.getString(ExponentManifest.MANIFEST_ID_KEY));
    } catch (JSONException e) {
      promise.reject("Requires Experience Id");
      return;
    }

    ExponentKernelModule.queueEvent("ExponentKernel.getExponentPushToken", params, new ExponentKernelModule.KernelEventCallback() {
      @Override
      public void onEventSuccess(ReadableMap result) {
        String exponentPushToken = result.getString("exponentPushToken");
        promise.resolve(exponentPushToken);
      }

      @Override
      public void onEventFailure(String errorMessage) {
        promise.reject(errorMessage);
      }
    });
  }
}
