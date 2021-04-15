---
title: Config Plugins
---

> This guide applies to SDK 41+ projects. The Expo Go app doesn't support custom native modules.

When adding a native module to your project, most of the setup can be done automatically by installing the module in your project, but some modules require more complex setup. For instance, say you installed `expo-camera` in your bare project, you now need to configure the native app to enable camera permissions — this is where config plugins come in. Config plugins are a system for extending the Expo config and customizing the prebuild phase of managed builds.

Internally Expo CLI uses config plugins to generate and configure all the native code for a managed project. Plugins do things like generate app icons, set the app name, and configure the `Info.plist`, `AndroidManifest.xml`, etc.

You can think of plugins like a bundler for native projects, and running `expo eject` as a way to bundle the projects by evaluating all the project plugins. Doing so will generate `ios` and `android` directories. These directories can be modified manually after being generated, but then they can no longer be safely regenerated without potentially overwriting manual modifications.

> 💡 **Hands-on Learners**: Use [this sandbox][sandbox] to play with the core functionality of Expo config plugins. For more complex tests, use a local Expo project, with `expo eject --no-install` to apply changes.

**Quick facts**

- Plugins are functions that can change values on your Expo config.
- Plugins are mostly meant to be used with [`expo eject`][cli-eject] or `eas build` commands.
- We recommend you use plugins with `app.config.json` or `app.config.js` instead of `app.json` (no top-level `expo` object is required).
- `mods` are async functions that modify native project files, such as source code or configuration (plist, xml) files.
- Changes performed with `mods` will require rebuilding the affected native projects.
- `mods` are removed from the public app manifest.
- 💡 Everything in the Expo config must be able to be converted to JSON (with the exception of the `mods` field). So no async functions outside of `mods` in your config plugins!

## Using a plugin in your app

Expo config plugins mostly come from Node modules, you can install them just like other packages in your project.

For instance, `expo-camera` has a plugin that adds camera permissions to the `Info.plist` and `AndroidManifest.xml`.

Install it in your project:

```sh
expo install expo-camera
```

In your app's Expo config (`app.json`, or `app.config.js`), add `expo-camera` to the list of plugins:

```json
{
  "name": "my app",
  "plugins": ["expo-camera"]
}
```

Some plugins can be customized by passing an array, where the second argument is the options:

```json
{
  "name": "my app",
  "plugins": [
    [
      "expo-camera",
      {
        /* Values passed to the plugin */
        "locationWhenInUsePermission": "Allow $(PRODUCT_NAME) to access your location"
      }
    ]
  ]
}
```

If you run `expo eject`, the `mods` will be compiled, and the native files be changed! The changes won't take effect until you rebuild the native project, eg: with Xcode. If you're using config plugins in a managed app, they will be applied during the prebuild phase on `eas build`. 

For instance, if you add a plugin that adds permission messages to your app, the app will need to be rebuilt.

And that's it! Now you're using Config plugins. No more having to interact with the native projects!

> 💡 Check out all the different ways you can import `plugins`: [plugin module resolution](#Plugin-module-resolution)

## What are plugins

Plugins are **synchronous** functions that accept an [`ExpoConfig`][config-docs] and return a modified [`ExpoConfig`][config-docs].

- Plugins should be named using the following convention: `with<Plugin Functionality>` i.e. `withFacebook`.
- Plugins should be synchronous and their return value should be serializable, except for any `mods` that are added.
- Optionally, a second argument can be passed to the plugin to configure it.
- `plugins` are always invoked when the config is read by `@expo/config`s `getConfig` method. However, the `mods` are only invoked during the "syncing" phase of `expo eject`.

## Creating a plugin

> 💡 Hands-on learners: Try this [sandbox](https://codesandbox.io/s/expo-config-plugins-basic-example-xopto?file=/src/project/app.config.js) (check the terminal logs).

Here is an example of the most basic config plugin:

```ts
const withNothing = config => config;
```

Say you wanted to create a plugin which added custom values to `Info.plist` in an iOS project:

```ts
const withMySDK = (config, { apiKey }) => {
  // Ensure the objects exist
  if (!config.ios) {
    config.ios = {};
  }
  if (!config.ios.infoPlist) {
    config.ios.infoPlist = {};
  }

  // Append the apiKey
  config.ios.infoPlist['MY_CUSTOM_NATIVE_IOS_API_KEY'] = apiKey;

  return config;
};

// 💡 Usage:

/// Create a config
const config = {
  name: 'my app',
};

/// Use the plugin
export default withMySDK(config, { apiKey: 'X-XXX-XXX' });
```

### Importing plugins

You may want to create a plugin in a different file, here's how:

- The root file can be any JS file or a file named `app.plugin.js` in the [root of a Node module](#root-app.plugin.js).
- The file should export a function that satisfies the [`ConfigPlugin`][configplugin] type.
- Plugins should be transpiled for Node environments ahead of time!
  - They should support the versions of Node that [Expo supports](https://docs.expo.io/get-started/installation/#requirements) (LTS).
  - No `import/export` keywords, use `module.exports` in the shipped plugin file.
  - Expo only transpiles the user's initial `app.config` file, anything more would require a bundler which would add too many "opinions" for a config file.

Consider the following example that changes the config name:

```
╭── app.config.js ➡️ Expo Config
╰── my-plugin.js ➡️ Our custom plugin file
```

`my-plugin.js`

```js
module.exports = function withCustomName(config, name) {
  // Modify the config
  config.name = 'custom-' + name;
  // Return the results
  return config;
};
```

`app.config.json`

```json
{
  "name": "my-app",
  "plugins": ["./my-plugin", "app"]
}
```

↓ ↓ ↓

**Evaluated config JSON**

```json
{
  "name": "custom-my-app",
  "plugins": ["./my-plugin", "app"]
}
```

### Chaining plugins

Once you add a few plugins, your `app.config.js` code can become difficult to read and manipulate. To combat this, `@expo/config-plugins` provides a `withPlugins` function which can be used to chain plugins together and execute them in order.

```js
/// Create a config
const config = {
  name: 'my app',
};

// ❌ Hard to read
withDelta(withFoo(withBar(config, 'input 1'), 'input 2'), 'input 3');

// ✅ Easy to read
import { withPlugins } from '@expo/config-plugins';

withPlugins(config, [
  [withBar, 'input 1'],
  [withFoo, 'input 2'],
  // When no input is required, you can just pass the method...
  withDelta,
]);
```

To support JSON configs, we also added the `plugins` array which just uses `withPlugins` under the hood.
Here is the same config as above, but even simpler:

```js
export default {
  name: 'my app',
  plugins: [
    [withBar, 'input 1'],
    [withFoo, 'input 2'],
    [withDelta, 'input 3'],
  ],
};
```

## What are mods

A modifier (mod for short) is an async function which accepts a config and a data object, then manipulates and returns both as an object.

Mods are added to the `mods` object of the Expo config. The `mods` object is different to the rest of the Expo config because it doesn't get serialized after the initial reading, this means you can use it to perform actions _during_ code generation. If possible, you should attempt to use basic plugins instead of mods as they're simpler to work with.

- `mods` are omitted from the manifest and **cannot** be accessed via `Updates.manifest`. Mods exist for the sole purpose of modifying native project files during code generation!
- `mods` can be used to read and write files safely during the `expo eject` command. This is how Expo CLI modifies the `Info.plist`, entitlements, xcproj, etc...
- `mods` are platform specific and should always be added to a platform specific object:

`app.config.js`

```js
module.exports = {
  name: 'my-app',
  mods: {
    ios: {
      /* iOS mods... */
    },
    android: {
      /* Android mods... */
    },
  },
};
```

## How mods work

- The config is read using `getConfig` from `@expo/config`
- All of the core functionality supported by Expo is added via plugins in `withExpoIOSPlugins`. This is stuff like name, version, icons, locales, etc.
- The config is passed to the compiler `compileModifiersAsync`
- The compiler adds base mods which are responsible for reading data (like `Info.plist`), executing a named mod (like `mods.ios.infoPlist`), then writing the results to the file system.
- The compiler iterates over all of the mods and asynchronously evaluates them, providing some base props like the `projectRoot`.
  - After each mod, error handling asserts if the mod chain was corrupted by an invalid mod.

<!-- TODO: Move to a section about mod compiler -->

> 💡 Here is a [colorful chart](https://whimsical.com/UjytoYXT2RN43LywvWExfK) of the mod compiler for visual learners.

### Best practices for mods

- Avoid doing long tasks like making fetch requests or installing Node modules in mods.
- Do not add interactive terminal prompts in mods.
- Utilize built-in config plugins like `withXcodeProject` to minimize the amount of times a file is read and parsed.

### Default mods

The following default mods are provided by the mod compiler for common file manipulation:

- `mods.ios.appDelegate` -- Modify the `ios/<name>/AppDelegate.m` as a string.
- `mods.ios.infoPlist` -- Modify the `ios/<name>/Info.plist` as JSON (parsed with [`@expo/plist`](https://www.npmjs.com/package/@expo/plist)).
- `mods.ios.entitlements` -- Modify the `ios/<name>/<product-name>.entitlements` as JSON (parsed with [`@expo/plist`](https://www.npmjs.com/package/@expo/plist)).
- `mods.ios.expoPlist` -- Modify the `ios/<name>/Expo.plist` as JSON (Expo updates config for iOS) (parsed with [`@expo/plist`](https://www.npmjs.com/package/@expo/plist)).
- `mods.ios.xcodeproj` -- Modify the `ios/<name>.xcodeproj` as an `XcodeProject` object (parsed with [`xcode`](https://www.npmjs.com/package/xcode)).

- `mods.android.manifest` -- Modify the `android/app/src/main/AndroidManifest.xml` as JSON (parsed with [`xml2js`](https://www.npmjs.com/package/xml2js)).
- `mods.android.strings` -- Modify the `android/app/src/main/res/values/strings.xml` as JSON (parsed with [`xml2js`](https://www.npmjs.com/package/xml2js)).
- `mods.android.mainActivity` -- Modify the `android/app/src/main/<package>/MainActivity.java` as a string.
- `mods.android.appBuildGradle` -- Modify the `android/app/build.gradle` as a string.
- `mods.android.projectBuildGradle` -- Modify the `android/build.gradle` as a string.
- `mods.android.settingsGradle` -- Modify the `android/settings.gradle` as a string.
- `mods.android.gradleProperties` -- Modify the `android/gradle.properties` as a `Properties.PropertiesItem[]`.

After the mods are resolved, the contents of each mod will be written to disk. Custom default mods can be added to support new native files.
For example, you can create a mod to support the `GoogleServices-Info.plist`, and pass it to other mods.

### Mod plugins

Mods are responsible for a lot of tasks, so they can be pretty difficult to understand at first.
If you're developing a feature that requires mods, it's best not to interact with them directly.

Instead you should use the helper mods provided by `@expo/config-plugins`:

- iOS
  - `withAppDelegate`
  - `withInfoPlist`
  - `withEntitlementsPlist`
  - `withExpoPlist`
  - `withXcodeProject`
- Android
  - `withAndroidManifest`
  - `withStringsXml`
  - `withMainActivity`
  - `withProjectBuildGradle`
  - `withAppBuildGradle`
  - `withSettingsGradle`
  - `withGradleProperties`

A mod plugin gets passed a `config` object with additional properties `modResults` and `modRequest` added to it.

- `modResults`: The object to modify and return. The type depends on the mod that's being used.
- `modRequest`: Additional properties supplied by the mod compiler.
  - `projectRoot: string`: Project root directory for the universal app.
  - `platformProjectRoot: string`: Project root for the specific platform.
  - `modName: string`: Name of the mod.
  - `platform: ModPlatform`: Name of the platform used in the mods config.
  - `projectName?: string`: iOS only: The path component used for querying project files. ex. `projectRoot/ios/[projectName]/`

## Creating a mod

Say you wanted to write a mod to update the Xcode Project's "product name":

```ts
import { ConfigPlugin, withXcodeProject } from '@expo/config-plugins';

const withCustomProductName: ConfigPlugin = (config, customName) => {
  return withXcodeProject(config, async config => {
    // config = { modResults, modRequest, ...expoConfig }

    const xcodeProject = config.modResults;
    xcodeProject.productName = customName;

    return config;
  });
};

// 💡 Usage:

/// Create a config
const config = {
  name: 'my app',
};

/// Use the plugin
export default withCustomProductName(config, 'new_name');
```

### Experimental functionality

Some parts of the mod system aren't fully fleshed out, these parts use `withDangerousModifier` to read/write data without a base mod. These methods essentially act as their own base mod and cannot be extended. Icons, for example, currently use the dangerous mod to perform a single generation step with no ability to customize the results.

```ts
export const withIcons: ConfigPlugin = config => {
  return withDangerousModifier(config, async config => {
    // No modifications are made to the config
    await setIconsAsync(config, config.modRequest.projectRoot);
    return config;
  });
};
```

Be careful using `withDangerousModifier` as it is subject to change in the future.
The order with which it gets executed is not reliable either.
Currently dangerous mods run first before all other modifiers, this is because we use dangerous mods internally for large file system refactoring like when the package name changes.

## Plugin module resolution

The strings passed to the `plugins` array can be resolved in a few different ways.

> Any resolution pattern that isn't specified below is unexpected behavior, and subject to breaking changes.

### Project file

You can quickly create a plugin in your project and use it in your config.

- ✅ `'./my-config-plugin'`
- ❌ `'./my-config-plugin.js'`

```
╭── app.config.js ➡️ Expo Config
╰── my-config-plugin.js ➡️ ✅ `module.exports = (config) => config`
```

### app.plugin.js

Sometimes you want your package to export React components and also support a plugin. To do this, multiple entry points need to be used because the transpilation (Babel preset) may be different.
If an `app.plugin.js` file is present in the root of a Node module's folder, it'll be used instead of the package's `main` file.

- ✅ `'expo-splash-screen'`
- ❌ `'expo-splash-screen/app.plugin.js'`

```
╭── app.config.js ➡️ Expo Config
╰── node_modules/expo-splash-screen/ ➡️ Module installed from NPM (works with Yarn workspaces as well).
    ├── package.json ➡️ The `main` file will be used if `app.plugin.js` doesn't exist.
    ├── app.plugin.js ➡️ ✅ `module.exports = (config) => config` -- must export a function.
    ╰── build/index.js ➡️ ❌ Ignored because `app.plugin.js` exists. This could be used with `expo-splash-screen/build/index.js`
```

### Node module default file

A config plugin in a node module (without an `app.plugin.js`) will use the `main` file defined in the `package.json`.

- ✅ `'expo-splash-screen'`
- ❌ `'expo-splash-screen/build/index'`

```
╭── app.config.js ➡️ Expo Config
╰── node_modules/expo-splash-screen/ ➡️ Module installed from NPM (works with Yarn workspaces as well).
    ├── package.json ➡️ The `main` file points to `build/index.js`
    ╰── build/index.js ➡️  ✅ Node resolves to this module.
```

### Project folder

- ✅ `'./my-config-plugin'`
- ❌ `'./my-config-plugin.js'`

This is different to how Node modules work because `app.plugin.js` won't be resolved by default in a directory. You'll have to manually specify `./my-config-plugin/app.plugin.js` to use it, otherwise `index.js` in the directory will be used.

```
╭── app.config.js ➡️ Expo Config
╰── my-config-plugin/ ➡️ Folder containing plugin code
    ╰── index.js ➡️ ✅ By default, Node resolves a folder's index.js file as the main file.
```

### Module internals

If a file inside a Node module is specified, then the module's root `app.plugin.js` resolution will be skipped. This is referred to as "reaching inside a package" and is considered **bad form**.
We support this to make testing, and plugin authoring easier, but we don't expect library authors to expose their plugins like this as a public API.

- ❌ `'expo-splash-screen/build/index.js'`
- ❌ `'expo-splash-screen/build'`

```
╭── app.config.js ➡️ Expo Config
╰── node_modules/expo-splash-screen/ ➡️ Module installed from npm (works with Yarn workspaces as well).
    ├── package.json ➡️ The `main` file will be used if `app.plugin.js` doesn't exist.
    ├── app.plugin.js ➡️ ❌ Ignored because the reference reaches into the package internals.
    ╰── build/index.js ➡️ ✅ `module.exports = (config) => config`
```

### Raw functions

You can also just pass in a config plugin.

```js
const withCustom = (config, props) => config;

const config = {
  plugins: [
    [
      withCustom,
      {
        /* props */
      },
    ],
    // Without props
    withCustom,
  ],
};
```

One caveat to using functions instead of strings is that serialization will replace the function with the function's name. This keeps **manifests** (kinda like the `index.html` for your app) working as expected.

Here is what the serialized config would look like:

```json
{
  "plugins": [["withCustom", {}], "withCustom"]
}
```

## Why app.plugin.js for plugins

Config resolution searches for a `app.plugin.js` first when a Node module name is provided.
This is because Node environments are often different to iOS, Android, or web JS environments and therefore require different transpilation presets (ex: `module.exports` instead of `import/export`).

Because of this reasoning, the root of a Node module is searched instead of right next to the `index.js`. Imagine you had a TypeScript Node module where the transpiled main file was located at `build/index.js`, if Expo config plugin resolution searched for `build/app.plugin.js` you'd lose the ability to transpile the file differently.

## Developing a Plugin

To make plugin development easier, we've added plugin support to [`expo-module-scripts`](https://www.npmjs.com/package/expo-module-scripts). Refer to the [config plugins guide](https://github.com/expo/expo/tree/master/packages/expo-module-scripts#-config-plugin) for more info on using TypeScript, and Jest to build plugins.

Plugins will generally have `@expo/config-plugins` installed as a dependency, and `expo-module-scripts`, `@expo/config-types` installed as a devDependencies.

### Setting up a playground environment

You can develop plugins easily using JS, but if you want to setup Jest tests and use TypeScript, you're gonna want a monorepo.

A monorepo will enable you to work on a node module and import it in your Expo app like you would if it were published to NPM. Expo config plugins have full monorepo support built-in so all you need to do is setup a project.

We recommend using [`expo-yarn-workspaces`](https://www.npmjs.com/package/expo-yarn-workspaces) which makes Expo monorepos very easy to setup.
In your monorepo's `packages/` folder, create a module, and [bootstrap a config plugin](https://github.com/expo/expo/tree/master/packages/expo-module-scripts#-config-plugin) in it.

### Modifying the AndroidManifest.xml

You can use built-in types and helpers to ease the process of working with complex objects.
Here's an example of adding a `<meta-data android:name="..." android:value="..."/>` to the default `<application android:name=".MainApplication" />`.

```ts
import { AndroidConfig, ConfigPlugin, withAndroidManifest } from '@expo/config-plugins';
import { ExpoConfig } from '@expo/config-types';

// Using helpers keeps error messages unified and helps cut down on XML format changes.
const { addMetaDataItemToMainApplication, getMainApplicationOrThrow } = AndroidConfig.Manifest;

export const withMyCustomConfig: ConfigPlugin = config => {
  return withAndroidManifest(config, async config => {
    // Modifiers can be async, but try to keep them fast.
    config.modResults = await setCustomConfigAsync(config, config.modResults);
    return config;
  });
};

// Splitting this function out of the mod makes it easier to test.
async function setCustomConfigAsync(
  config: Pick<ExpoConfig, 'android'>,
  androidManifest: AndroidConfig.Manifest.AndroidManifest
): Promise<AndroidConfig.Manifest.AndroidManifest> {
  const appId = 'my-app-id';
  // Get the <application /> tag and assert if it doesn't exist.
  const mainApplication = getMainApplicationOrThrow(androidManifest);

  addMetaDataItemToMainApplication(
    mainApplication,
    // value for `android:name`
    'my-app-id-key',
    // value for `android:value`
    appId
  );

  return androidManifest;
}
```

### Modifying the Info.plist

Using the `withInfoPlist` is a bit safer than statically modifying the `expo.ios.infoPlist` object in the `app.json` because it reads the contents of the Info.plist and merges it with the `expo.ios.infoPlist`, this means you can attempt to keep your changes from being overwritten.

Here's an example of adding a `GADApplicationIdentifier` to the `Info.plist`:

```ts
import { ConfigPlugin, InfoPlist, withInfoPlist } from '@expo/config-plugins';
import { ExpoConfig } from '@expo/config-types';

// Pass `<string>` to specify that this plugin requires a string property.
export const withCustomConfig: ConfigPlugin<string> = (config, id) => {
  return withInfoPlist(config, config => {
    config.modResults.GADApplicationIdentifier = id;
    return config;
  });
};
```

### Adding plugins to pluginHistory

`_internal.pluginHistory` was created to prevent duplicate plugins from running while migrating from legacy UNVERSIONED plugins to versioned plugins.

```ts
import { ConfigPlugin, createRunOncePlugin } from '@expo/config-plugins';

// Keeping the name, and version in sync with it's package.
const pkg = require('my-cool-plugin/package.json');

const withMyCoolPlugin: ConfigPlugin = config => config;

// A helper method that wraps `withRunOnce` and appends items to `pluginHistory`.
export default createRunOncePlugin(
  // The plugin to guard.
  withMyCoolPlugin,
  // An identifier used to track if the plugin has already been run.
  pkg.name,
  // Optional version property, if omitted, defaults to UNVERSIONED.
  pkg.version
);
```

## Debugging

You can debug config plugins by running `EXPO_DEBUG=1 expo prebuild`. If `EXPO_DEBUG` is enabled, the plugin stack logs will be printed, these are useful for viewing which mods ran, and in what order they ran in. To view all static plugin resolution errors, enable `EXPO_CONFIG_PLUGIN_VERBOSE_ERRORS`, this should only be needed for plugin authors.
By default some automatic plugin errors are hidden because they're usually related to versioning issues and aren't very helpful (i.e. legacy package doesn't have a config plugin yet).

Running `expo prebuild --clean` with remove the generated native folders before compiling.

You can also run `expo config --type prebuild` to print the results of the plugins with the mods unevaluated (no code is generated).

Expo CLI commands can be profiled using `EXPO_PROFILE=1`.

## Legacy plugins

In order to make `eas build` work the same as the classic `expo build` service, we added support for "legacy plugins" which are applied automatically to a project when they're installed in the project.

For instance, say a project has `expo-camera` installed but doesn't have `plugins: ['expo-camera']` in their `app.json`. Expo CLI would automatically add `expo-camera` to the plugins to ensure that the required camera and microphone permissions are added to the project. The user can still customize the `expo-camera` plugin by adding it to the `plugins` array manually, and the manually defined plugins will take precedence over the automatic plugins.

You can debug which plugins were added by running `expo config --type prebuild` and seeing the `_internal.pluginHistory` property.

This will show an object with all plugins that were added using `withRunOnce` plugin from `@expo/config-plugins`.

Notice that `expo-location` uses `version: '11.0.0'`, and `react-native-maps` uses `version: 'UNVERSIONED'`. This means the following:

- `expo-location` and `react-native-maps` are both installed in the project.
- `expo-location` is using the plugin from the project's `node_modules/expo-location/app.plugin.js`
- The version of `react-native-maps` installed in the project doesn't have a plugin, so it's falling back on the unversioned plugin that is shipped with `expo-cli` for legacy support.

```js
{
  _internal: {
    pluginHistory: {
      'expo-location': {
        name: 'expo-location',
        version: '11.0.0',
      },
      'react-native-maps': {
        name: 'react-native-maps',
        version: 'UNVERSIONED',
      },
    },
  },
};
```

For the most _stable_ experience, you should try to have no `UNVERSIONED` plugins in your project. This is because the `UNVERSIONED` plugin may not support the native code in your project.
For instance, say you have an `UNVERSIONED` Facebook plugin in your project, if the Facebook native code or plugin has a breaking change, that will break the way your project ejects and cause it to error on build.

[config-docs]: https://docs.expo.io/versions/latest/config/app/
[cli-eject]: https://docs.expo.io/workflow/expo-cli/#eject
[sandbox]: https://codesandbox.io/s/expo-config-plugins-8qhof?file=/src/project/app.config.js
[configplugin]: ./src/Plugin.types.ts
