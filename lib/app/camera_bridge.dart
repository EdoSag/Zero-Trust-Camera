import 'package:flutter/services.dart';
import 'package:flutter_riverpod/flutter_riverpod.dart';

/// The link to the camera screens, which are native Android code (the
/// `capture` module) and not Flutter. Matches ZtAppActivity.kt.
class CameraBridge {
  const CameraBridge();

  static const _channel = MethodChannel('dev.sagron.zerotrustcamera/app');

  /// Shows the camera screens in [languageCode] ("he", "en"), or in the
  /// phone's language when null.
  Future<void> setLanguage(String? languageCode) async {
    try {
      await _channel.invokeMethod<void>('setLanguage', {
        'language': languageCode,
      });
    } on PlatformException {
      // The camera keeps its language until the next change.
    } on MissingPluginException {
      // Not on Android.
    }
  }

  /// The screen the camera asked these screens to show ("vault"), once:
  /// null after it has been read, or when the camera asked for none.
  Future<String?> takeScreen() async {
    try {
      return await _channel.invokeMethod<String>('takeScreen');
    } on PlatformException {
      return null;
    } on MissingPluginException {
      return null;
    }
  }

  /// Opens the pro camera: ISO, shutter, focus, white balance and RAW (native).
  Future<void> openProCamera() async {
    try {
      await _channel.invokeMethod<void>('openProCamera');
    } on PlatformException {
      // Nothing opens; the home screen stays.
    } on MissingPluginException {
      // Not on Android.
    }
  }

  /// Opens the panorama mode (native).
  Future<void> openPanorama() async {
    try {
      await _channel.invokeMethod<void>('openPanorama');
    } on PlatformException {
      // Nothing opens; the home screen stays.
    } on MissingPluginException {
      // Not on Android.
    }
  }

  /// Opens the document scanner (native, in the capture module).
  Future<void> openDocScanner() async {
    try {
      await _channel.invokeMethod<void>('openDocScanner');
    } on PlatformException {
      // Nothing opens; the home screen stays.
    } on MissingPluginException {
      // Not on Android.
    }
  }

  /// Closes these screens, back to the viewfinder.
  Future<void> close() async {
    try {
      await _channel.invokeMethod<void>('close');
    } on PlatformException {
      // Stays open; the phone's back button still works.
    } on MissingPluginException {
      // Not on Android.
    }
  }
}

final cameraBridgeProvider = Provider<CameraBridge>(
  (ref) => const CameraBridge(),
);
