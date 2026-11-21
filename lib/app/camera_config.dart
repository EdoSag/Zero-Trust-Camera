import 'package:flutter/material.dart';
import 'package:flutter_riverpod/flutter_riverpod.dart';
import 'package:zero_trust_core/zero_trust_core.dart';

/// Camera in the suite. Not in the core's `ZtSuite` list yet, so the other
/// apps don't link to it before it's released.
const ZtSuiteApp cameraSuiteApp = ZtSuiteApp(
  appId: 'camera',
  name: 'Camera',
  tagline: 'Photos and videos without location, date or phone details',
  icon: Icons.photo_camera_outlined,
  accent: Color(0xFF14B8A6), // teal
  androidPackage: 'dev.sagron.zerotrustcamera',
);

const ZtAppConfig cameraAppConfig = ZtAppConfig(
  appId: 'camera',
  displayName: 'Zero-Trust Camera',
  accent: Color(0xFF14B8A6),
);

/// The Offline build (`--dart-define=ZT_OFFLINE=true`, with the Android
/// manifest that has no internet permission): no account and no backup, so
/// the screens for them are left out. Tests override it.
final offlineBuildProvider = Provider<bool>(
  (ref) => const bool.fromEnvironment('ZT_OFFLINE'),
);
