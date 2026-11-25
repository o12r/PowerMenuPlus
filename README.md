# PowerMenuPlus

LSPosed (libxposed API 102) module for RedMagic/Nubia **NX809J, Android 16**. Scope: `com.android.systemui` only. / 红魔 NX809J 的 LSPosed 模块，作用域仅 SystemUI。

## 功能 / Features
- Power menu: adds **Soft reboot** (`su -c "/data/adb/ksud soft-reboot"`, keeps temporary root) and **Restart SystemUI** under the native reboot button. Needs root granted to this app in KernelSU. / 电源菜单加"软重启"和"重启系统界面"，软重启需在 KernelSU 授权本应用 root。
- Long-press power opens the **default assistant**: for invocation type 6 it skips ZTE's `AssistManagerAdapt.handleStartAssist`, which only launches the ZTE assistant. / 长按电源键启动默认助理。
- Hooks: `SingleKeyRule.getVeryLongPressTimeoutMs` (system, power menu delay 2 s); `MfvGlobalActionsDialog$MFVActionsDialog.initView` and the animation methods; `AssistManager.startAssist`. Reverse-engineered from `ls.augment.com` (disable that module's power-menu and assistant options to avoid duplicates). / 参考 `ls.augment.com` 逆向，二者请勿同时启用相同功能。

## 构建 / Build
`bash build.sh <versionCode> <versionName>` → `PowerMenuPlus-<versionName>.apk`. Every installed build needs a new versionCode/versionName. / 每次安装的构建都要新的版本号。

## 相关项目 / Related project
- **[RecognitionStub](../RecognitionStub)** — UI-less placeholder `RecognitionService`. Without it, a soft reboot can wipe `voice_interaction_service`, so the assistant is not bound and the key only vibrates. Install it and run `settings put secure voice_recognition_service dev.oscar.recogstub/.StubRecognitionService`. Also needs `power_button_long_press=5`, `power_button_very_long_press=1`, `voice_assistant_powerkey_control_halo=0`.
  RecognitionStub 保证开机后助理仍被绑定；本模块保证按键能拉起助理。二者独立，建议同装。

## 注意 / Note
- **应用需要 root 授权来运行软重启**：请在 KernelSU 管理器的“超级用户”里给 Power Menu Plus 授权，否则 `su` 对它不可见，软重启会报 “Cannot run program su”。
- **The app needs root permission to run Soft reboot**: grant Power Menu Plus root in the KernelSU manager (Superuser tab); otherwise `su` is invisible to it and Soft reboot fails with "Cannot run program su".
