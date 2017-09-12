package versioned.host.exp.exponent.modules.api.components.camera;

import com.facebook.react.bridge.Arguments;
import com.facebook.react.bridge.Promise;
import com.facebook.react.bridge.ReactApplicationContext;
import com.facebook.react.bridge.ReactContextBaseJavaModule;
import com.facebook.react.bridge.ReactMethod;
import com.facebook.react.bridge.ReadableMap;
import com.facebook.react.bridge.WritableArray;
import com.google.android.cameraview.AspectRatio;
import com.google.android.cameraview.Constants;

import java.util.Collections;
import java.util.HashMap;
import java.util.Map;
import java.util.Set;

import javax.annotation.Nullable;

import host.exp.exponent.utils.ScopedContext;

public class CameraModule extends ReactContextBaseJavaModule {
  private static final String TAG = "CameraModule";

  private static ReactApplicationContext mReactContext;
  private static ScopedContext mScopedContext;

  static final int VIDEO_2160P = 0;
  static final int VIDEO_1080P = 1;
  static final int VIDEO_720P = 2;
  static final int VIDEO_480P = 3;
  static final int VIDEO_4x3 = 4;

  public CameraModule(ReactApplicationContext reactContext, ScopedContext scopedContext) {
    super(reactContext);
    mReactContext = reactContext;
    mScopedContext = scopedContext;
  }

  public static ReactApplicationContext getReactContextSingleton() {
    return mReactContext;
  }

  public static ScopedContext getScopedContextSingleton() {
    return mScopedContext;
  }

  @Override
  public String getName() {
    return "ExponentCameraModule";
  }

  @Nullable
  @Override
  public Map<String, Object> getConstants() {
    return Collections.unmodifiableMap(new HashMap<String, Object>() {
      {
        put("Type", getTypeConstants());
        put("FlashMode", getFlashModeConstants());
        put("AutoFocus", getAutoFocusConstants());
        put("WhiteBalance", getWhiteBalanceConstants());
        put("VideoQuality", getVideoQualityConstants());
      }

      private Map<String, Object> getTypeConstants() {
        return Collections.unmodifiableMap(new HashMap<String, Object>() {
          {
            put("front", Constants.FACING_FRONT);
            put("back", Constants.FACING_BACK);
          }
        });
      }

      private Map<String, Object> getFlashModeConstants() {
        return Collections.unmodifiableMap(new HashMap<String, Object>() {
          {
            put("off", Constants.FLASH_OFF);
            put("on", Constants.FLASH_ON);
            put("auto", Constants.FLASH_AUTO);
            put("torch", Constants.FLASH_TORCH);
          }
        });
      }

      private Map<String, Object> getAutoFocusConstants() {
        return Collections.unmodifiableMap(new HashMap<String, Object>() {
          {
            put("on", true);
            put("off", false);
          }
        });
      }

      private Map<String, Object> getWhiteBalanceConstants() {
        return Collections.unmodifiableMap(new HashMap<String, Object>() {
          {
            put("auto", Constants.WB_AUTO);
            put("cloudy", Constants.WB_CLOUDY);
            put("sunny", Constants.WB_SUNNY);
            put("shadow", Constants.WB_SHADOW);
            put("fluorescent", Constants.WB_FLUORESCENT);
            put("incandescent", Constants.WB_INCANDESCENT);
          }
        });
      }

      private Map<String, Object> getVideoQualityConstants() {
        return Collections.unmodifiableMap(new HashMap<String, Object>() {
          {
            put("2160p", VIDEO_2160P);
            put("1080p", VIDEO_1080P);
            put("720p", VIDEO_720P);
            put("480p", VIDEO_480P);
            put("4:3", VIDEO_4x3);
          }
        });
      }
    });
  }

  @ReactMethod
  public void takePicture(ReadableMap options, final Promise promise) {
    CameraViewManager.getInstance().takePicture(options, promise);
  }

  @ReactMethod
  public void record(ReadableMap options, final Promise promise) {
    CameraViewManager.getInstance().record(options, promise);
  }

  @ReactMethod
  public void stopRecording() {
    CameraViewManager.getInstance().stopRecording();
  }

  @ReactMethod
  public void getSupportedRatios(final Promise promise) {
    WritableArray result = Arguments.createArray();
    Set<AspectRatio> ratios = CameraViewManager.getInstance().getSupportedRatios();
    if (ratios != null) {
      for (AspectRatio ratio : ratios) {
        result.pushString(ratio.toString());
      }
      promise.resolve(result);
    } else {
      promise.reject("E_CAMERA_UNAVAILABLE", "Camera is not running");
    }
  }
}
