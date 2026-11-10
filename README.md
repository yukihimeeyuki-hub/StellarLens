# StellarLens（瑶光）

StellarLens 是一个基于 **CameraX** 的 Android 原生相机组件，并将其封装为 **uni-app 原生组件** `stellar-lens-camera`，用于替代 uni-app 官方 livepusher 中体验不佳的相机部分，以及市面上多数需要付费的同类插件。

当前版本提供的能力：实时预览、拍照、录像、前后摄像头切换、变焦（含双指缩放）、闪光灯控制、照片分辨率选择。

**兼容范围：Android 4.4（API 19）到 Android 16（API 36）。** API 21+ 使用 CameraX；API 19–20 自动切换到传统的 `android.hardware.Camera` 实现，对外 API 完全一致。

> 说明：当前版本聚焦于「相机采集」。直播推流（RTMP）等上游能力尚未内置，可按需在后续版本中扩展。

---

## 特性

- 双实现、单 API：API 21+ 走 CameraX，API 19–20 走传统 `android.hardware.Camera`，上层 `CameraView` / uni-app 组件无需区分版本
- 原生组件形式接入 uni-app（注册名 `stellar-lens-camera`），通过标签属性、事件、方法调用即可使用
- 同时提供纯 Android 的 `CameraView`，不依赖 uni-app，可直接在原生工程中使用
- 内置一次性注册入口 `StellarLensCameraPlugin.register()`，也提供了 `dcloud_uniplugins.json` 声明式注册
- 自动读取设备变焦范围并对输入值做钳制，支持双指捏合缩放
- 回调统一切回主线程；拍照/录像自动跟随屏幕旋转设置方向
- 适配 Android 10+ 分区存储、Android 13+ 媒体权限、Android 15/16 强制 edge-to-edge（演示 App 已处理系统栏 insets）
- 内置 `app` 模块作为独立测试 App，可脱离 uni-app 直接运行验证

## 项目结构

```
StellarLens
├── app/                                  # 独立测试 App（CameraView 演示与验证）
├── stellarlens_camera/                   # 核心库模块
│   ├── libs/uniapp-v8-release.aar        # uni-app 引擎依赖（编译期）
│   └── src/main/
│       ├── assets/dcloud_uniplugins.json # uni-app 原生组件声明
│       ├── AndroidManifest.xml           # 相机/录音/存储权限与硬件特性声明
│       └── java/com/stellarlens/stellarlens_camera/
│           ├── CameraView.java                    # 对外的统一相机视图（公共 API）
│           ├── CameraController.java              # 相机实现抽象
│           ├── CameraXController.java             # API 21+：CameraX + Recorder
│           ├── LegacyCameraController.java        # API 19–20：android.hardware.Camera + MediaRecorder
│           ├── StellarLensCameraComponent.java    # uni-app 组件（属性/方法/事件）
│           ├── StellarLensCameraPlugin.java       # 组件注册入口
│           └── CameraActivity.java                # 库内自带的测试 Activity
├── gradle/libs.versions.toml             # 依赖与插件版本目录
├── build.gradle
├── settings.gradle
└── gradlew / gradlew.bat
```

## 环境要求

| 项目 | 版本 |
| --- | --- |
| JDK | 21（`gradle/gradle-daemon-jvm.properties` 指定 daemon 工具链版本 21） |
| Gradle | 9.3.1（使用仓库自带 Wrapper） |
| Android Gradle Plugin | 9.1.1 |
| compileSdk / targetSdk | 36 |
| minSdk | 19（Android 4.4） |
| Java 兼容级别 | 11 |
| CameraX | 1.3.0（见下方说明） |

> CameraX 版本固定在 1.3.0：1.4.x 的 AAR 声明 minSdk 21，1.5+ 声明 minSdk 23，都会与 minSdk 19 冲突。1.3.0 的类虽然标注 `@RequiresApi(21)`，但官方支持在低 minSdk 工程中按版本条件使用，正好满足本项目「低版本走传统相机、高版本走 CameraX」的结构。

建议使用支持 AGP 9.x 的 Android Studio 打开工程。首次构建需要联网下载 Gradle 与依赖。

## 构建与运行

```powershell
# Windows
.\gradlew.bat :app:assembleDebug
.\gradlew.bat :app:installDebug

# macOS / Linux
./gradlew :app:assembleDebug
./gradlew :app:installDebug
```

Debug APK 输出于 `app/build/outputs/apk/debug/app-debug.apk`。安装后即可在独立 App 中验证预览、拍照、录像、切换摄像头、变焦与闪光灯。

构建库 AAR（用于集成到 uni-app 离线打包工程）：

```powershell
.\gradlew.bat :stellarlens_camera:assembleRelease
```

产物位于 `stellarlens_camera/build/outputs/aar/stellarlens_camera-release.aar`。

单元测试与 Lint：

```powershell
.\gradlew.bat :app:testDebugUnitTest :stellarlens_camera:testDebugUnitTest
.\gradlew.bat :app:lintDebug :stellarlens_camera:lintDebug
```

## 集成到 uni-app

### 1. 引入模块 / AAR

任选其一：

- **源码模块**：将 `stellarlens_camera` 目录拷贝到你的 uni-app 离线打包 Android 工程，并在 `settings.gradle` 中 `include ':stellarlens_camera'`，然后 `implementation project(':stellarlens_camera')`。
- **AAR**：执行上面的 `assembleRelease`，把 AAR 放入宿主工程 `libs/` 目录并添加依赖：

```groovy
implementation files('libs/stellarlens_camera-release.aar')
```

AAR 已内置 consumer ProGuard 规则（保留 `stellar-lens-camera` 组件与注册入口），宿主开启混淆时无需额外配置。

由于 AAR 不携带传递依赖，宿主工程需要自行声明（与库模块保持一致）：

```groovy
def camerax_version = "1.3.0"
implementation "androidx.camera:camera-core:${camerax_version}"
implementation "androidx.camera:camera-camera2:${camerax_version}"
implementation "androidx.camera:camera-lifecycle:${camerax_version}"
implementation "androidx.camera:camera-view:${camerax_version}"
implementation "androidx.camera:camera-video:${camerax_version}"
implementation "androidx.concurrent:concurrent-futures:1.1.0"
```

如果宿主 `minSdk < 21` 且方法数超过 64K（接入 uni-app 引擎后基本必然超过），还需要开启 multidex：

```groovy
android {
    defaultConfig {
        multiDexEnabled true
    }
}
dependencies {
    implementation "androidx.multidex:multidex:2.0.1"
}
```

并让 `Application` 继承 `androidx.multidex.MultiDexApplication`（参见 `app` 模块的 `StellarLensApp`）。

同时宿主工程需已包含 uni-app 引擎（`uniapp-v8-release.aar` 或对应的 SDK 依赖），否则 `io.dcloud.feature.uniapp.*` 无法解析。

### 2. 注册组件

库的 `assets/dcloud_uniplugins.json` 已声明该组件，AAR/模块合入后通常无需额外操作：

```json
{
  "nativePlugins": [
    {
      "plugins": [
        {
          "type": "component",
          "name": "stellar-lens-camera",
          "class": "com.stellarlens.stellarlens_camera.StellarLensCameraComponent"
        }
      ]
    }
  ]
}
```

如使用代码方式注册，请在 `UniSDKEngine` 初始化完成后调用（可重复调用，内部有幂等保护）：

```java
StellarLensCameraPlugin.register();
```

### 3. 页面中使用

> 原生组件需在 **nvue 页面** 中使用（vue 页面由 WebView 渲染，无法承载原生视图）。

```vue
<template>
  <view class="page">
    <stellar-lens-camera
      ref="camera"
      class="preview"
      camera-facing="back"
      flash-mode="auto"
      :zoom="1"
      :quality="1"
      @ready="onReady"
      @phototaken="onPhotoTaken"
      @videosaved="onVideoSaved"
      @error="onError"
    />

    <view class="actions">
      <button @click="takePhoto">拍照</button>
      <button @click="toggleRecord">{{ recording ? '停止录像' : '开始录像' }}</button>
      <button @click="flip">切换摄像头</button>
    </view>
  </view>
</template>

<script>
export default {
  data() {
    return { recording: false };
  },
  methods: {
    onReady() {
      console.log('camera ready');
    },
    onPhotoTaken(e) {
      console.log('photo:', e.tempFilePath);
    },
    onVideoSaved(e) {
      this.recording = false;
      console.log('video:', e.tempFilePath);
    },
    onError(e) {
      console.error('camera error:', e.message);
    },
    takePhoto() {
      this.$refs.camera.takePhoto(res => {
        if (!res.success) console.error(res.message);
      });
    },
    toggleRecord() {
      if (this.recording) {
        this.$refs.camera.stopRecording();
        this.recording = false;
        return;
      }
      this.$refs.camera.startRecording(res => {
        if (!res.success) console.error(res.message);
      });
      this.recording = true;
    },
    flip() {
      this.$refs.camera.switchCamera();
    }
  }
};
</script>

<style>
.page { flex: 1; }
.preview { width: 750rpx; height: 500rpx; }
.actions { flex-direction: row; }
</style>
```

> 组件默认输出到应用的缓存目录，`tempFilePath` 为该目录下的绝对路径，不属于 uni-app 的临时文件体系。需要持久化或上传时请自行复制/读取，或先调用 `setOutputDir` 指定可写目录。

### 组件属性

| 属性 | 类型 | 默认值 | 说明 |
| --- | --- | --- | --- |
| `camera-facing` | String | `back` | 摄像头方向，`back` / `front` |
| `flash-mode` | String | `auto` | 闪光灯模式，`on` / `off` / `auto` |
| `zoom` | Number | `1.0` | 变焦倍率，自动钳制在设备支持的最小/最大倍率之间 |
| `quality` | Number | `1` | 拍照分辨率：`0` = 1280×720，`1` = 1920×1080，`2` = 3840×2160 |

### 组件方法

通过 `this.$refs.camera.<method>(...)` 调用。

| 方法 | 参数 | 回调结果 | 说明 |
| --- | --- | --- | --- |
| `takePhoto` | `callback?` | `{ success, tempFilePath }` 或 `{ success: false, message }` | 拍照 |
| `startRecording` | `callback?` | `{ success, tempFilePath }` 或 `{ success: false, message }` | 开始录像，回调在保存完成时触发 |
| `stopRecording` | 无 | 无 | 停止录像，结果通过 `videosaved` 事件回传 |
| `isRecording` | `callback?` | `{ recording }` | 查询是否正在录像 |
| `setZoom` | `zoom: Number` | 无 | 设置变焦倍率 |
| `getZoom` | `callback?` | `{ zoom, minZoom, maxZoom }` | 获取当前及可用的变焦范围 |
| `switchCamera` | 无 | 无 | 切换前后摄像头 |
| `setFlashMode` | `mode: String` | 无 | 设置闪光灯模式（`on` / `off` / `auto`） |
| `getFlashMode` | `callback?` | `{ flashMode }` | 获取当前闪光灯模式 |
| `setPhotoQuality` | `quality: Number` | 无 | 设置拍照分辨率，运行中设置会重新绑定相机会短暂中断预览 |
| `setOutputDir` | `dirPath: String` | 无 | 设置照片/视频输出目录（需为应用可写路径） |
| `getCameraInfo` | `callback?` | `{ hasFlash, isFrontCamera, isReady, zoom, minZoom, maxZoom }` | 获取相机状态信息 |

### 组件事件

| 事件 | 参数 | 说明 |
| --- | --- | --- |
| `ready` | 无 | 相机绑定完成，可开始拍照/录像 |
| `phototaken` | `{ tempFilePath }` | 拍照完成 |
| `videosaved` | `{ tempFilePath }` | 录像保存完成 |
| `error` | `{ message }` | 相机初始化、绑定或采集出错 |

## 在原生 Android 工程中使用

`stellarlens_camera` 的核心是 `CameraView`，可直接作为普通 View 使用：

```java
CameraView cameraView = new CameraView(context);
container.addView(cameraView, new FrameLayout.LayoutParams(
        FrameLayout.LayoutParams.MATCH_PARENT,
        FrameLayout.LayoutParams.MATCH_PARENT));

cameraView.setLifecycleOwner((LifecycleOwner) context);

cameraView.setOnCameraReadyListener(() -> { /* 相机就绪 */ });
cameraView.setOnPhotoTakenListener(path -> { /* 照片绝对路径 */ });
cameraView.setOnVideoSavedListener(path -> { /* 视频绝对路径 */ });
cameraView.setOnErrorListener(msg -> { /* 错误信息 */ });

cameraView.startCamera();

// 拍照 / 录像 / 控制
cameraView.takePhoto();
cameraView.startRecording();
cameraView.stopRecording();
cameraView.setZoom(2.0f);
cameraView.switchCamera();
cameraView.setFlashModeString("on");
cameraView.setOutputDir(getExternalFilesDir(null));
```

| 方法 | 说明 |
| --- | --- |
| `setLifecycleOwner(LifecycleOwner)` | 绑定生命周期（API 21+ 必须在 `startCamera()` 之前调用；API 19–20 可选） |
| `startCamera()` | 按系统版本创建 CameraX 或传统相机的控制器并启动预览 |
| `takePhoto()` | 拍照，结果通过 `OnPhotoTakenListener` 回调 |
| `startRecording()` / `stopRecording()` / `isRecording()` | 录像控制 |
| `setZoom(float)` / `getZoom()` / `getMinZoom()` / `getMaxZoom()` | 变焦 |
| `switchCamera()` / `getLensFacing()` | 前后摄像头切换（`0` 前置，`1` 后置） |
| `setFlashMode(int)` / `setFlashModeString(String)` / `getFlashMode()` / `getFlashModeString()` / `hasFlash()` | 闪光灯 |
| `setPhotoQuality(int)` / `getPhotoQuality()` | 拍照分辨率（`0` / `1` / `2`） |
| `setOutputDir(File)` / `getOutputDir()` | 输出目录，默认 `Context.getCacheDir()` |
| `releaseCamera()` | 解绑所有 UseCase，释放相机 |

注意事项：

- 所有回调（就绪 / 拍照 / 录像 / 错误）都会统一切回主线程，可直接更新 UI。
- `CameraView` 在 `onDetachedFromWindow` 中会自动 `releaseCamera()`；释放后如需再次使用，重新调用 `startCamera()` 即可。
- 双指捏合缩放已内置于 `CameraView`，无需额外处理 `onTouchEvent`。
- `setPhotoQuality()` 在相机运行中调用会重新绑定 UseCase，预览会短暂中断。
- 缺少 `RECORD_AUDIO` 时录像仍可进行，但没有音轨。

## 权限

库的 `AndroidManifest.xml` 已声明（合入宿主后自动合并）：

| 权限 | 用途 |
| --- | --- |
| `CAMERA` | 预览与拍照（必需） |
| `RECORD_AUDIO` | 录像音轨（可选，未授权时录制无声视频） |
| `FLASHLIGHT` | 闪光灯/手电筒模式 |
| `WRITE_EXTERNAL_STORAGE`（`maxSdkVersion=28`） | 旧版本系统写入文件 |
| `READ_EXTERNAL_STORAGE` / `READ_MEDIA_IMAGES` / `READ_MEDIA_VIDEO` | 读取媒体文件 |

运行时权限需由宿主应用申请。在 uni-app 中可在进入相机页面前调用 `plus.android.requestPermissions(['android.permission.CAMERA', 'android.permission.RECORD_AUDIO'])` 或使用原生侧申请逻辑；未授权时相机不会启动。

## Android 版本适配说明

| 系统版本 | 实现 | 说明 |
| --- | --- | --- |
| Android 4.4（API 19–20） | `LegacyCameraController` | `android.hardware.Camera` + `MediaRecorder`；预览为 `TextureView`；录像期间不能拍照 |
| Android 5.0+（API 21+） | `CameraXController` | CameraX 1.3.0 + `Recorder`；支持拍照与录像并行 |
| Android 6.0+（API 23+） | 同上 | 相机/录音均为运行时权限，需宿主应用申请 |
| Android 10+（API 29+） | 同上 | 不使用公共外部存储，输出只写入应用私有目录或调用方指定的可写目录 |
| Android 13+（API 33+） | 同上 | 已声明 `READ_MEDIA_IMAGES` / `READ_MEDIA_VIDEO`；本组件自身不读取相册 |
| Android 15/16（API 35/36） | 同上 | 演示 App 已处理 edge-to-edge 与系统栏 insets；库本身不依赖窗口边衬区 |

> 说明：Android 4.0–4.3（API 14–18）不在支持范围内。CameraX 运行时要求 API 21+，而低版本只能使用传统相机；同时 uni-app 引擎 AAR 自身声明 minSdk 19，宿主 App 也无法安装到 API 19 以下。

## 已知限制

- 原生组件仅在 nvue 页面可用。
- CameraX 固定在 1.3.0（1.4.x 要求 minSdk 21，1.5+ 要求 minSdk 23）。
- API 19–20 的传统相机分支依赖设备厂商提供 `android.hardware.Camera` 兼容层；极少数设备可能只能打开后置摄像头或无法录像。
- API 19–20 的预览使用 `TextureView`，需要宿主保持硬件加速（Android 默认开启）。
- 预览采集比例为 16:9；拍照分辨率会按设备支持能力就近选择。
- 未内置相册保存、镜像/滤镜、推流等能力。
- 仓库内的 uni-app 引擎 `arm64-v8a` / `x86_64` `.so` 已是 16KB（0x4000）段对齐；如果宿主替换了引擎版本，上架 Android 15+ 16KB 页设备前请自行复核。

## 贡献

欢迎提交 Issue 与 Pull Request。提交前请确认 `.\gradlew.bat :app:assembleDebug` 可正常构建，并尽量保持改动聚焦、单一。

## 许可证

本项目基于 [MIT License](./LICENSE) 开源，Copyright (c) 2026 瑶光。
