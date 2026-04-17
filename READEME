# ✨ StellarLens (星尘透镜)

[](https://opensource.org/licenses/MIT)
[](https://www.android.com)

**“穿越星尘的轨迹，捕捉瞬息的流光。”**

`StellarLens` 是一款为 UniApp (Vue2/App端) 量身定制的开源原生相机插件。它打破了插件市场的收费壁垒，旨在为开发者提供一个纯净、高效、且完全免费的前置摄像头嵌入方案。

-----

## 🌌 为什么选择 StellarLens？

在许多项目中，我们仅仅需要一个简单的页面内嵌相机，却往往面临复杂的原生集成或昂贵的商业插件。`StellarLens` 秉承开源精神，基于 Google 最新的 **CameraX** 架构开发，提供如星光般纯净的代码实现。

  * **完全开源**：拒绝黑盒，每一行代码都清晰可见。
  * **零成本使用**：再也不用为基础的拍照功能支付高额授权费。
  * **Vue2 友好**：深度适配 UniApp Vue2 架构，无需 nvue 即可实现嵌入式预览。

-----

## 🚀 核心功能

  * **原生预览流**：直接在 Vue 页面组件内渲染，不跳转系统相机。
  * **前置特化**：默认锁定前置摄像头，完美适配人脸识别预览或自拍场景。
  * **瞬时捕捉**：支持原生级别拍照，直接返回 `file://` 本地路径。
  * **极致轻量**：仅引入必要的 CameraX 依赖，不增加 App 体积负担。

-----

## 📦 安装与配置

### 1\. 放置插件

将编译好的 `StellarLens.aar` 放入项目路径：
`nativeplugins/StellarLens/android/StellarLens.aar`

### 2\. package.json 配置

确保你的 `nativeplugins` 下有对应的 `package.json`，声明组件名称为 `StellarLensView`（或你定义的类名）。

-----

## 💻 代码示例

```vue
<template>
  <view class="stellar-container">
    <stellar-lens-view
      ref="lens"
      class="camera-box"
    ></stellar-lens-view>

    <button class="snap-btn" @click="capture">捕捉流星 (Capture)</button>
  </view>
</template>

<script>
export default {
  methods: {
    capture() {
      // 触发原生拍照逻辑
      this.$refs.lens.takePhoto((res) => {
        if (res.path) {
          uni.showToast({ title: '已捕获星光' });
          console.log("照片路径：", res.path);
        }
      });
    }
  }
}
</script>

<style>
.camera-box {
  width: 750rpx;
  height: 800rpx;
  border-radius: 20rpx;
}
</style>
```

-----

## 🛠 技术栈

  * **Core**: Android CameraX (Jetpack)
  * **Bridge**: UniApp Native JS SDK (Java)
  * **Language**: Java
  * **Protocol**: MIT License

-----

## 🌠 开发者寄语

`StellarLens` 的诞生是为了证明：**技术本应像星光一样，照亮每一个角落，而非成为阻碍。** 如果你也曾被昂贵的插件困扰，欢迎加入 `StellarLens` 的维护，或者给这个项目点一个 **Star** 🌟。你的支持，是让星辰持续闪耀的动力。

-----
