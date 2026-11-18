import 'package:flutter/material.dart';
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
