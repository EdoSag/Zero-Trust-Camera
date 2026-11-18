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
