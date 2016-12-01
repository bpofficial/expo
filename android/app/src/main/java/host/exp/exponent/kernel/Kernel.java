// Copyright 2015-present 650 Industries. All rights reserved.

package host.exp.exponent.kernel;

import android.app.Activity;
import android.app.ActivityManager;
import android.app.Application;
import android.content.Context;
import android.content.Intent;
import android.graphics.Bitmap;
import android.os.Build;
import android.os.Bundle;
import android.os.Handler;
import android.os.Looper;
import android.util.Log;

import com.facebook.common.internal.ByteStreams;
import com.facebook.proguard.annotations.DoNotStrip;
import com.facebook.react.ReactInstanceManager;
import com.facebook.react.ReactRootView;
import com.facebook.react.bridge.Arguments;
import com.facebook.react.bridge.ReadableMap;
import com.facebook.react.bridge.WritableMap;
import com.facebook.react.common.LifecycleState;
import com.facebook.react.modules.network.OkHttpClientProvider;
import com.facebook.react.shell.MainReactPackage;
import com.facebook.soloader.SoLoader;
import com.facebook.stetho.okhttp3.StethoInterceptor;

import org.json.JSONException;
import org.json.JSONObject;

import java.io.BufferedOutputStream;
import java.io.BufferedReader;
import java.io.File;
import java.io.FileInputStream;
import java.io.FileOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.io.UnsupportedEncodingException;
import java.lang.ref.WeakReference;
import java.lang.reflect.Field;
import java.net.URLEncoder;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

import javax.inject.Inject;

import de.greenrobot.event.EventBus;
import host.exp.exponent.LauncherActivity;
import host.exp.exponent.di.NativeModuleDepsProvider;
import host.exp.exponent.experience.BaseExperienceActivity;
import host.exp.exponent.experience.ExperienceActivity;
import host.exp.exponent.experience.HomeActivity;
import host.exp.exponentview.BuildConfig;
import host.exp.exponent.Constants;
import host.exp.exponent.ExponentManifest;
import host.exp.exponentview.Exponent;
import host.exp.exponentview.R;
import host.exp.exponent.RNObject;
import host.exp.exponent.analytics.Analytics;
import host.exp.exponent.analytics.EXL;
import host.exp.exponent.exceptions.ExceptionUtils;
import host.exp.exponent.generated.ExponentBuildConstants;
import host.exp.exponent.network.ExponentHttpClient;
import host.exp.exponent.network.ExponentNetwork;
import host.exp.exponent.storage.ExponentSharedPreferences;
import host.exp.exponent.utils.AsyncCondition;
import host.exp.exponent.utils.JSONBundleConverter;
import okhttp3.Call;
import okhttp3.OkHttpClient;
import okhttp3.Request;
import okhttp3.Response;
import versioned.host.exp.exponent.ExponentPackage;
import versioned.host.exp.exponent.ReactUnthemedRootView;
import versioned.host.exp.exponent.ReadableObjectUtils;

// TOOD: need to figure out when we should reload the kernel js. Do we do it every time you visit
// the home screen? only when the app gets kicked out of memory?
public class Kernel implements KernelInterface {

  private static final String TAG = Kernel.class.getSimpleName();

  private static Kernel sInstance;

  public static class KernelStartedRunningEvent {
  }

  public static class ExperienceActivityTask {
    public final String manifestUrl;
    public int taskId;
    public WeakReference<ExperienceActivity> experienceActivity;
    public int activityId;
    public String bundleUrl;

    public ExperienceActivityTask(String manifestUrl) {
      this.manifestUrl = manifestUrl;
    }
  }

  // React
  private ReactInstanceManager mReactInstanceManager;

  // Contexts
  @Inject
  Context mContext;

  @Inject
  Application mApplicationContext;

  private Activity mActivityContext;

  // Activities/Tasks
  private static Map<String, ExperienceActivityTask> sManifestUrlToExperienceActivityTask = new HashMap<>();
  private ExperienceActivity mOptimisticActivity;
  private Integer mOptimisticTaskId;
  private ExperienceActivityTask experienceActivityTaskForTaskId(int taskId) {
    for (ExperienceActivityTask task : sManifestUrlToExperienceActivityTask.values()) {
      if (task.taskId == taskId) {
        return task;
      }
    }

    return null;
  }

  // Misc
  private boolean mIsStarted = false;
  private boolean mIsRunning = false;
  private boolean mHasError = false;

  @Inject
  ExponentManifest mExponentManifest;

  @Inject
  ExponentSharedPreferences mExponentSharedPreferences;

  private static final Map<String, KernelConstants.ExperienceOptions> mManifestUrlToOptions = new HashMap<>();

  @Inject
  ExponentNetwork mExponentNetwork;

  public Kernel() {
    NativeModuleDepsProvider.getInstance().inject(Kernel.class, this);

    sInstance = this;

    updateKernelRNOkHttp();
  }

  private void updateKernelRNOkHttp() {
    OkHttpClient.Builder clientBuilder = OkHttpClientProvider.getOkHttpClient().newBuilder()
        .cache(mExponentNetwork.getCache());
    if (BuildConfig.DEBUG) {
      clientBuilder.addNetworkInterceptor(new StethoInterceptor());
    }
    mExponentNetwork.addOfflineInterceptors(clientBuilder);
    OkHttpClientProvider.replaceOkHttpClient(clientBuilder.build());
  }

  // Don't call this until a loading screen is up, since it has to do some work on the main thread.
  public void startJSKernel() {
    SoLoader.init(mContext, false);

    synchronized (this) {
      if (mIsStarted && !mHasError) {
        return;
      }
      mIsStarted = true;
    }

    mHasError = false;

    // On first run use the embedded kernel js but fire off a request for the new js in the background.
    final String bundleUrl = getBundleUrl();
    if (mExponentSharedPreferences.shouldUseInternetKernel() &&
        mExponentSharedPreferences.getBoolean(ExponentSharedPreferences.IS_FIRST_KERNEL_RUN_KEY)) {
      mExponentSharedPreferences.setBoolean(ExponentSharedPreferences.IS_FIRST_KERNEL_RUN_KEY, false);
      kernelBundleListener().onBundleLoaded(Constants.EMBEDDED_KERNEL_PATH);

      // Now preload bundle for next run
      new Handler().postDelayed(new Runnable() {
        @Override
        public void run() {
          Exponent.getInstance().loadJSBundle(bundleUrl, KernelConstants.KERNEL_BUNDLE_ID, RNObject.UNVERSIONED, new Exponent.BundleListener() {
            @Override
            public void onBundleLoaded(String localBundlePath) {
              EXL.d(TAG, "Successfully preloaded kernel bundle");
            }

            @Override
            public void onError(Exception e) {
              EXL.e(TAG, "Error preloading kernel bundle: " + e.toString());
            }
          });
        }
      }, KernelConstants.DELAY_TO_PRELOAD_KERNEL_JS);
    } else {
      Exponent.getInstance().loadJSBundle(bundleUrl, KernelConstants.KERNEL_BUNDLE_ID, RNObject.UNVERSIONED, kernelBundleListener());
    }
  }

  public void reloadJSBundle() {
    String bundleUrl = getBundleUrl();
    mHasError = false;
    Exponent.getInstance().loadJSBundle(bundleUrl, KernelConstants.KERNEL_BUNDLE_ID, RNObject.UNVERSIONED, kernelBundleListener());
  }

  public static void addIntentDocumentFlags(Intent intent) {
    if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.LOLLIPOP) {
      intent.addFlags(Intent.FLAG_ACTIVITY_CLEAR_TOP);

      intent.addFlags(Intent.FLAG_ACTIVITY_NEW_DOCUMENT);
      intent.addFlags(Intent.FLAG_ACTIVITY_MULTIPLE_TASK);
    } else {
      intent.addFlags(Intent.FLAG_ACTIVITY_SINGLE_TOP);
    }
  }

  private Exponent.BundleListener kernelBundleListener() {
    return new Exponent.BundleListener() {
      @Override
      public void onBundleLoaded(final String localBundlePath) {
        runOnUiThread(new Runnable() {
          @Override
          public void run() {
            ReactInstanceManager.Builder builder = ReactInstanceManager.builder()
                .setApplication(mApplicationContext)
                .setJSBundleFile(localBundlePath)
                .addPackage(new MainReactPackage())
                .addPackage(new ExponentPackage())
                .setInitialLifecycleState(LifecycleState.RESUMED);

            if (mExponentSharedPreferences.isKernelDebugModeEnabled()) {
              // Won't work with ngrok url.
              Exponent.enableDeveloperSupport(ExponentBuildConstants.BUILD_MACHINE_IP_ADDRESS + ":8081",
                  "exponent", RNObject.wrap(builder));
            }

            mReactInstanceManager = builder.build();
            mReactInstanceManager.createReactContextInBackground();
            if (getActivityContext() != null) {
              // RN expects an activity in some places.
              mReactInstanceManager.onHostResume(getActivityContext(), null);
            }

            mIsRunning = true;
            EventBus.getDefault().postSticky(new KernelStartedRunningEvent());
            EXL.d(TAG, "Kernel started running.");
          }
        });
      }

      @Override
      public void onError(Exception e) {
        setHasError();

        if (BuildConfig.DEBUG) {
          handleError(e.getMessage());
        } else {
          handleError(mContext.getString(R.string.error_unable_to_load_kernel));
        }
      }
    };
  }

  public static String defaultLocalKernelUrl() {
    if (ExponentBuildConstants.BUILD_MACHINE_KERNEL_NGROK_URL.isEmpty()) {
      return "http://" + ExponentBuildConstants.BUILD_MACHINE_IP_ADDRESS + ":8081";
    } else {
      return ExponentBuildConstants.BUILD_MACHINE_KERNEL_NGROK_URL;
    }
  }

  private String getBundleUrl() {
    if (mExponentSharedPreferences.shouldUseInternetKernel()) {
      return Constants.KERNEL_URL;
    } else {
      return mExponentSharedPreferences.getString(ExponentSharedPreferences.LOCAL_KERNEL_URL_KEY,
          defaultLocalKernelUrl()) + "/exponent.bundle?dev=true&platform=android";
    }
  }

  public final void runOnUiThread(Runnable action) {
    if (Thread.currentThread() != Looper.getMainLooper().getThread()) {
      new Handler(mContext.getMainLooper()).post(action);
    } else {
      action.run();
    }
  }

  public Boolean isRunning() {
    return mIsRunning && !mHasError;
  }

  public Boolean isStarted() {
    return mIsStarted;
  }

  public Activity getActivityContext() {
    return mActivityContext;
  }

  public void setActivityContext(final Activity activity) {
    if (activity != null) {
      mActivityContext = activity;
    }
  }

  public ReactInstanceManager getReactInstanceManager() {
    return mReactInstanceManager;
  }

  public ReactRootView getReactRootView() {
    ReactRootView reactRootView = new ReactUnthemedRootView(mContext);
    reactRootView.startReactApplication(
        mReactInstanceManager,
        KernelConstants.HOME_MODULE_NAME,
        getKernelLaunchOptions());

    return reactRootView;
  }

  private Bundle getKernelLaunchOptions() {
    JSONObject exponentProps = new JSONObject();
    String referrer = mExponentSharedPreferences.getString(ExponentSharedPreferences.REFERRER_KEY);
    if (referrer != null) {
      try {
        exponentProps.put("referrer", referrer);
      } catch (JSONException e) {
        EXL.e(TAG, e);
      }
    }

    boolean nuxHasFinishedFirstRun = mExponentSharedPreferences.getBoolean(ExponentSharedPreferences.NUX_HAS_FINISHED_FIRST_RUN_KEY);
    try {
      exponentProps.put("nuxHasFinishedFirstRun", nuxHasFinishedFirstRun);
    } catch (JSONException e) {
      EXL.e(TAG, e);
    }

    Bundle bundle = new Bundle();
    bundle.putBundle("exp", JSONBundleConverter.JSONToBundle(exponentProps));
    return bundle;
  }

  public Boolean hasOptionsForManifestUrl(String manifestUrl) {
    return mManifestUrlToOptions.containsKey(manifestUrl);
  }

  public KernelConstants.ExperienceOptions popOptionsForManifestUrl(String manifestUrl) {
    return mManifestUrlToOptions.remove(manifestUrl);
  }

  public ExperienceActivityTask getExperienceActivityTask(String manifestUrl) {
    ExperienceActivityTask task = sManifestUrlToExperienceActivityTask.get(manifestUrl);
    if (task != null) {
      return task;
    }

    task = new ExperienceActivityTask(manifestUrl);
    sManifestUrlToExperienceActivityTask.put(manifestUrl, task);
    return task;
  }

  public void removeExperienceActivityTask(String manifestUrl) {
    if (manifestUrl != null) {
      sManifestUrlToExperienceActivityTask.remove(manifestUrl);
    }
  }

  public void openHomeActivity() {
    if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.LOLLIPOP) {
      ActivityManager manager = (ActivityManager) mContext.getSystemService(Context.ACTIVITY_SERVICE);
      for (ActivityManager.AppTask task : manager.getAppTasks()) {
        Intent baseIntent = task.getTaskInfo().baseIntent;

        if (HomeActivity.class.getName().equals(baseIntent.getComponent().getClassName())) {
          task.moveToFront();
          return;
        }
      }
    }

    Intent intent = new Intent(mActivityContext, HomeActivity.class);
    Kernel.addIntentDocumentFlags(intent);

    if (Build.VERSION.SDK_INT < Build.VERSION_CODES.LOLLIPOP) {
      // Don't want to end up in state where we have
      // ExperienceActivity - HomeActivity - ExperienceActivity
      // Want HomeActivity to be the root activity if it exists
      intent.addFlags(Intent.FLAG_ACTIVITY_CLEAR_TASK);
    }

    mActivityContext.startActivity(intent);
  }

  /*
   *
   * Manifests
   *
   */

  public void openExperience(final KernelConstants.ExperienceOptions options) {
    openManifestUrl(getManifestUrlFromFullUri(options.manifestUri), options, true);
  }

  private String getManifestUrlFromFullUri(String uri) {
    if (uri != null) {
      int deepLinkPosition = uri.indexOf('+');
      if (deepLinkPosition >= 0) {
        uri = uri.substring(0, deepLinkPosition);
      }

      // manifest url doesn't have a trailing slash
      if (uri.length() > 0) {
        char lastUrlChar = uri.charAt(uri.length() - 1);
        if (lastUrlChar == '/') {
          uri = uri.substring(0, uri.length() - 1);
        }
      }

      return uri;
    }
    return null;
  }

  private void openManifestUrl(final String manifestUrl, final KernelConstants.ExperienceOptions options, final Boolean isOptimistic) {
    SoLoader.init(mContext, false);

    if (options == null) {
      mManifestUrlToOptions.remove(manifestUrl);
    } else {
      mManifestUrlToOptions.put(manifestUrl, options);
    }

    if (manifestUrl == null || manifestUrl.equals(KernelConstants.HOME_MANIFEST_URL)) {
      openHomeActivity();
      return;
    }

    ExponentKernelModuleProvider.queueEvent("ExponentKernel.clearConsole", Arguments.createMap(), null);

    final List<ActivityManager.AppTask> tasks = getExperienceActivityTasks();
    ActivityManager.AppTask existingTask = null;
    if (tasks != null) {
      for (int i = 0; i < tasks.size(); i++) {
        ActivityManager.AppTask task = tasks.get(i);
        Intent baseIntent = task.getTaskInfo().baseIntent;
        if (baseIntent.hasExtra(KernelConstants.MANIFEST_URL_KEY) && baseIntent.getStringExtra(KernelConstants.MANIFEST_URL_KEY).equals(manifestUrl)) {
          existingTask = task;
          break;
        }
      }
    }

    if (isOptimistic && existingTask == null) {
      openOptimisticExperienceActivity(manifestUrl);
    }

    final ActivityManager.AppTask finalExistingTask = existingTask;
    mExponentManifest.fetchManifest(manifestUrl, new ExponentManifest.ManifestListener() {
      @Override
      public void onCompleted(final JSONObject manifest) {
        runOnUiThread(new Runnable() {
          @Override
          public void run() {
            try {
              openManifestUrlStep2(manifestUrl, manifest, finalExistingTask);
            } catch (JSONException e) {
              handleError(e);
            }
          }
        });
      }

      @Override
      public void onError(Exception e) {
        handleError(e);
      }

      @Override
      public void onError(String e) {
        handleError(e);
      }
    });
  }

  private void openManifestUrlStep2(String manifestUrl, JSONObject manifest, ActivityManager.AppTask existingTask) throws JSONException {
    String bundleUrl = ExponentUrls.toHttp(manifest.getString("bundleUrl"));
    Kernel.ExperienceActivityTask task = getExperienceActivityTask(manifestUrl);
    task.bundleUrl = bundleUrl;

    manifest = mExponentManifest.normalizeManifest(manifestUrl, manifest);
    boolean isFirstRunFinished = mExponentSharedPreferences.getBoolean(ExponentSharedPreferences.NUX_HAS_FINISHED_FIRST_RUN_KEY);

    // TODO: shouldShowNux used to be set to `!manifestUrl.equals(Constants.INITIAL_URL);`.
    // This caused nux to show up in RNPlay. What's the right behavior here?
    boolean shouldShowNux = !Constants.isShellApp();
    boolean loadNux = shouldShowNux && !isFirstRunFinished;
    JSONObject opts = new JSONObject();
    opts.put("loadNux", loadNux);

    if (existingTask != null) {
      try {
        moveTaskToFront(existingTask.getTaskInfo().id);
      } catch (IllegalArgumentException e) {
        // Sometimes task can't be found.
        existingTask = null;
        openOptimisticExperienceActivity(manifestUrl);
      }
    }

    if (existingTask == null) {
      populateOptimisticExperienceActivity(manifestUrl, manifest, bundleUrl, opts);
    }

    if (loadNux) {
      mExponentSharedPreferences.setBoolean(ExponentSharedPreferences.NUX_HAS_FINISHED_FIRST_RUN_KEY, true);
    }

    WritableMap params = Arguments.createMap();
    params.putString("manifestUrl", manifestUrl);
    params.putString("manifestString", manifest.toString());
    params.putString("bundleUrl", bundleUrl);
    ExponentKernelModuleProvider.queueEvent("ExponentKernel.openManifestUrl", params, new ExponentKernelModuleProvider.KernelEventCallback() {
      @Override
      public void onEventSuccess(ReadableMap result) {
        EXL.d(TAG, "Successfully called ExponentKernel.openManifestUrl in kernel JS.");
      }

      @Override
      public void onEventFailure(String errorMessage) {
        EXL.e(TAG, "Error calling ExponentKernel.openManifestUrl in kernel JS: " + errorMessage);
      }
    });

    killOrphanedLauncherActivities();
  }


  // Called from DevServerHelper
  @DoNotStrip
  public static String getBundleUrlForActivityId(final int activityId, String host, String jsModulePath, boolean devMode, boolean hmr, boolean jsMinify) {
    if (activityId == -1) {
      // This is the kernel
      return sInstance.getBundleUrl();
    }

    for (ExperienceActivityTask task : sManifestUrlToExperienceActivityTask.values()) {
      if (task.activityId == activityId) {
        String url = task.bundleUrl;
        if (url == null) {
          return null;
        }

        if (hmr) {
          if (url.contains("hot=false")) {
            url = url.replace("hot=false", "hot=true");
          } else {
            url = url + "&hot=true";
          }
        }

        return url;
      }
    }

    return null;
  }


  /*
   *
   * Optimistic experiences
   *
   */

  public void openOptimisticExperienceActivity(String manifestUrl) {
    try {
      Intent intent = new Intent(mActivityContext, ExperienceActivity.class);
      addIntentDocumentFlags(intent);
      intent.putExtra(KernelConstants.MANIFEST_URL_KEY, manifestUrl);
      intent.putExtra(KernelConstants.IS_OPTIMISTIC_KEY, true);
      mActivityContext.startActivity(intent);
    } catch (Throwable e) {
      EXL.e(TAG, e);
    }
  }

  public void setOptimisticActivity(ExperienceActivity experienceActivity, int taskId) {
    mOptimisticActivity = experienceActivity;
    mOptimisticTaskId = taskId;

    AsyncCondition.notify(KernelConstants.OPEN_EXPERIENCE_ACTIVITY_KEY);
  }

  public void populateOptimisticExperienceActivity(
      final String manifestUrl, final JSONObject manifest, final String bundleUrl, final JSONObject kernelOptions) {
    AsyncCondition.wait(KernelConstants.OPEN_EXPERIENCE_ACTIVITY_KEY, new AsyncCondition.AsyncConditionListener() {
      @Override
      public boolean isReady() {
        return mOptimisticActivity != null && mOptimisticTaskId != null;
      }

      @Override
      public void execute() {
        mOptimisticActivity.loadExperience(manifestUrl, manifest, bundleUrl, kernelOptions);

        mOptimisticActivity = null;
        mOptimisticTaskId = null;
      }
    });
  }

  /*
   *
   * Tasks
   *
   */

  public List<ActivityManager.AppTask> getTasks() {
    if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.LOLLIPOP) {
      ActivityManager manager = (ActivityManager) mContext.getSystemService(Context.ACTIVITY_SERVICE);
      return manager.getAppTasks();
    } else {
      EXL.e(TAG, "Got to getTasks on pre-Lollipop device");
      return null;
    }
  }

  // Get list of tasks in our format.
  public List<ActivityManager.AppTask> getExperienceActivityTasks() {
    if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.LOLLIPOP) {
      return getTasks();
    } else {
      return null;
    }
  }

  // Sometimes LauncherActivity.finish() doesn't close the activity and task. Not sure why exactly.
  // Thought it was related to launchMode="singleTask" but other launchModes seem to have the same problem.
  // This can be reproduced by creating a shortcut, exiting app, clicking on shortcut, refreshing, pressing
  // home, clicking on shortcut, click recent apps button. There will be a blank LauncherActivity behind
  // the ExperienceActivity. killOrphanedLauncherActivities solves this but would be nice to figure out
  // the root cause.
  private void killOrphanedLauncherActivities() {
    if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.LOLLIPOP) {
      try {
        // Crash with NoSuchFieldException instead of hard crashing at taskInfo.numActivities
        ActivityManager.RecentTaskInfo.class.getDeclaredField("numActivities");

        for (ActivityManager.AppTask task : getTasks()) {
          ActivityManager.RecentTaskInfo taskInfo = task.getTaskInfo();
          if (taskInfo.numActivities == 0 && taskInfo.baseIntent.getAction().equals(Intent.ACTION_MAIN)) {
            task.finishAndRemoveTask();
            return;
          }

          if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.M) {
            if (taskInfo.numActivities == 1 && taskInfo.topActivity.getClassName().equals(LauncherActivity.class.getName())) {
              task.finishAndRemoveTask();
              return;
            }
          }
        }
      } catch (NoSuchFieldException e) {
        // Don't EXL here because this isn't actually a problem
        Log.e(TAG, e.toString());
      } catch (Throwable e) {
        EXL.e(TAG, e);
      }
    }
  }

  public void moveTaskToFront(int taskId) {
    if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.LOLLIPOP) {
      for (ActivityManager.AppTask task : getTasks()) {
        if (task.getTaskInfo().id == taskId) {
          // If we have the task in memory, tell the ExperienceActivity to check for new options.
          // Otherwise options will be added in initialProps when the Experience starts.
          ExperienceActivityTask exponentTask = experienceActivityTaskForTaskId(taskId);
          if (exponentTask != null) {
            ExperienceActivity experienceActivity = exponentTask.experienceActivity.get();
            if (experienceActivity != null) {
              experienceActivity.shouldCheckOptions();
            }
          }

          task.moveToFront();
        }
      }
    } else {
      EXL.e(TAG, "Got to moveTaskToFront on pre-Lollipop device");
    }
  }

  public void killActivityStack(final Activity activity) {
    ExperienceActivityTask exponentTask = experienceActivityTaskForTaskId(activity.getTaskId());
    if (exponentTask != null) {
      removeExperienceActivityTask(exponentTask.manifestUrl);
    }

    if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.LOLLIPOP) {
      // Kill the current task.
      ActivityManager manager = (ActivityManager) activity.getSystemService(Context.ACTIVITY_SERVICE);
      for (ActivityManager.AppTask task : manager.getAppTasks()) {
        if (task.getTaskInfo().id == activity.getTaskId()) {
          task.finishAndRemoveTask();
        }
      }
    }
  }

  public void reloadVisibleExperience(String manifestUrl) {
    // Pre Lollipop we always just open a new activity.
    if (Build.VERSION.SDK_INT < Build.VERSION_CODES.LOLLIPOP) {
      // TODO: make debug mode work here
      openOptimisticExperienceActivity(manifestUrl);
      openManifestUrl(manifestUrl, null, false);
    } else {
      ExperienceActivity activity = null;
      for (final ExperienceActivityTask experienceActivityTask : sManifestUrlToExperienceActivityTask.values()) {
        if (manifestUrl.equals(experienceActivityTask.manifestUrl)) {
          final ExperienceActivity weakActivity = experienceActivityTask.experienceActivity == null ? null : experienceActivityTask.experienceActivity.get();
          activity = weakActivity;
          if (activity == null) {
            // No activity, just force a reload
            break;
          }

          if (weakActivity.isLoading()) {
            // Already loading. Don't need to do anything.
            return;
          } else {
            runOnUiThread(new Runnable() {
              @Override
              public void run() {
                weakActivity.showLongLoadingScreen(null);
              }
            });
            break;
          }
        }
      }

      if (activity != null) {
        killActivityStack(activity);
      }
      openManifestUrl(manifestUrl, null, true);
    }
  }

  /*
   *
   * Error handling
   *
   */

  // Called using reflection from ReactAndroid.
  @DoNotStrip
  public static void handleReactNativeError(String errorMessage, Object detailsUnversioned,
                                            Integer exceptionId, Boolean isFatal) {
    handleReactNativeError(ExponentErrorMessage.developerErrorMessage(errorMessage), detailsUnversioned, exceptionId, isFatal);
  }

  // Called using reflection from ReactAndroid.
  @DoNotStrip
  public static void handleReactNativeError(Throwable throwable, String errorMessage, Object detailsUnversioned,
                                            Integer exceptionId, Boolean isFatal) {
    handleReactNativeError(ExponentErrorMessage.developerErrorMessage(errorMessage), detailsUnversioned, exceptionId, isFatal);
    if (throwable != null) {
      Exponent.logException(throwable);
    }
  }

  private static void handleReactNativeError(ExponentErrorMessage errorMessage, Object detailsUnversioned,
      Integer exceptionId, Boolean isFatal) {
    ArrayList<Bundle> stackList = new ArrayList<>();
    if (detailsUnversioned != null) {
      RNObject details = RNObject.wrap(detailsUnversioned);
      RNObject arguments = new RNObject("com.facebook.react.bridge.Arguments");
      arguments.loadVersion(details.version());

      for (int i = 0; i < (int) details.call("size"); i++) {
        try {
          Bundle bundle = (Bundle) arguments.callStatic("toBundle", details.call("getMap", i));
          stackList.add(bundle);
        } catch (Exception e) {
          e.printStackTrace();
        }
      }
    } else if (BuildConfig.DEBUG) {
      StackTraceElement[] stackTraceElements = Thread.currentThread().getStackTrace();
      // stackTraceElements starts with a bunch of stuff we don't care about.
      for (int i = 2; i < stackTraceElements.length; i++) {
        StackTraceElement element = stackTraceElements[i];
        if (element.getFileName().startsWith(Kernel.class.getSimpleName()) &&
            (element.getMethodName().equals("handleReactNativeError") ||
                element.getMethodName().equals("handleError"))) {
          // Ignore these base error handling methods.
          continue;
        }

        Bundle bundle = new Bundle();
        bundle.putInt("column", 0);
        bundle.putInt("lineNumber", element.getLineNumber());
        bundle.putString("methodName", element.getMethodName());
        bundle.putString("file", element.getFileName());
        stackList.add(bundle);
      }
    }
    Bundle[] stack = stackList.toArray(new Bundle[stackList.size()]);

    BaseExperienceActivity.addError(new ExponentError(errorMessage, stack,
        getExceptionId(exceptionId), isFatal));
  }

  public void handleError(String errorMessage) {
    handleReactNativeError(ExponentErrorMessage.developerErrorMessage(errorMessage), null, -1, true);
  }

  public void handleError(Exception exception) {
    handleReactNativeError(ExceptionUtils.exceptionToErrorMessage(exception), null, -1, true);
    Exponent.logException(exception);
  }

  private static int getExceptionId(Integer originalId) {
    if (originalId == null || originalId == -1) {
      return (int) -(Math.random() * Integer.MAX_VALUE);
    }

    return originalId;
  }

  // TODO: probably need to call this from other places.
  public void setHasError() {
    mHasError = true;
  }

  /*
   *
   * Shortcuts
   *
   */

  public void installShortcut(final String manifestUrl, final ReadableMap manifest, final String bundleUrl) {
    JSONObject manifestJson = ReadableObjectUtils.readableMapToJson(manifest);
    mExponentSharedPreferences.updateManifest(manifestUrl, manifestJson, bundleUrl);
    installShortcut(manifestUrl);
  }

  public void installShortcut(final String manifestUrl) {
    ExponentSharedPreferences.ManifestAndBundleUrl manifestAndBundleUrl = mExponentSharedPreferences.getManifest(manifestUrl);
    final JSONObject manifestJson = manifestAndBundleUrl.manifest;

    // TODO: show loading indicator while fetching bitmap
    final String iconUrl = manifestJson.optString(ExponentManifest.MANIFEST_ICON_URL_KEY);
    mExponentManifest.loadIconBitmap(iconUrl, new ExponentManifest.BitmapListener() {
      @Override
      public void onLoadBitmap(Bitmap bitmap) {
        Intent shortcutIntent = new Intent(mContext, LauncherActivity.class);
        shortcutIntent.setAction(Intent.ACTION_MAIN);
        shortcutIntent.addFlags(Intent.FLAG_ACTIVITY_SINGLE_TOP);
        shortcutIntent.putExtra(KernelConstants.MANIFEST_URL_KEY, manifestUrl);

        Intent addIntent = new Intent();
        addIntent.putExtra(Intent.EXTRA_SHORTCUT_INTENT, shortcutIntent);
        addIntent.putExtra(Intent.EXTRA_SHORTCUT_NAME, manifestJson.optString(ExponentManifest.MANIFEST_NAME_KEY));
        addIntent.putExtra(Intent.EXTRA_SHORTCUT_ICON, bitmap);

        addIntent.setAction("com.android.launcher.action.INSTALL_SHORTCUT");
        mContext.sendBroadcast(addIntent);

        goToHome();
      }
    });
  }

  public void addDevMenu() {
    Intent shortcutIntent = new Intent(mContext, LauncherActivity.class);
    shortcutIntent.setAction(Intent.ACTION_MAIN);
    shortcutIntent.addFlags(Intent.FLAG_ACTIVITY_SINGLE_TOP);
    shortcutIntent.putExtra(KernelConstants.DEV_FLAG, true);

    Intent addIntent = new Intent();
    addIntent.putExtra(Intent.EXTRA_SHORTCUT_INTENT, shortcutIntent);
    addIntent.putExtra(Intent.EXTRA_SHORTCUT_NAME, "Dev Tools");
    addIntent.putExtra(Intent.EXTRA_SHORTCUT_ICON_RESOURCE,
        Intent.ShortcutIconResource.fromContext(mContext, R.mipmap.dev_icon));

    addIntent.setAction("com.android.launcher.action.INSTALL_SHORTCUT");
    mContext.sendBroadcast(addIntent);

    goToHome();
  }

  private void goToHome() {
    Intent startMain = new Intent(Intent.ACTION_MAIN);
    startMain.addCategory(Intent.CATEGORY_HOME);
    startMain.setFlags(Intent.FLAG_ACTIVITY_NEW_TASK);
    mContext.startActivity(startMain);
  }
}
