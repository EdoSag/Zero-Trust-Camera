import 'package:flutter/material.dart';

import 'package:flutter_riverpod/flutter_riverpod.dart';

import '../app/camera_config.dart';
import '../l10n/l10n.dart';

/// How photos and videos are protected, in plain words, including what the
/// app can't protect.
class DataProtectionPage extends ConsumerWidget {
  const DataProtectionPage({super.key});

  @override
  Widget build(BuildContext context, WidgetRef ref) {
    final l = context.l10n;
    final offline = ref.watch(offlineBuildProvider);
    final theme = Theme.of(context);
    final sections = [
      (Icons.photo_outlined, l.protectGalleryTitle, l.protectGalleryBody),
      (
        Icons.enhanced_encryption_outlined,
        l.protectVaultTitle,
        l.protectVaultBody,
      ),
      (Icons.videocam_outlined, l.protectVideoTitle, l.protectVideoBody),
      (Icons.cloud_outlined, l.protectBackupTitle, l.protectBackupBody),
      (
        Icons.wifi_off_outlined,
        l.protectNetworkTitle,
        offline ? l.protectNetworkOffline : l.protectNetworkBody,
      ),
      (
        Icons.admin_panel_settings_outlined,
        l.protectPermissionsTitle,
        l.protectPermissionsBody,
      ),
      (Icons.warning_amber_outlined, l.protectLimitsTitle, l.protectLimitsBody),
    ];
    return Scaffold(
      appBar: AppBar(title: Text(l.protectTitle)),
      body: ListView(
        padding: const EdgeInsets.symmetric(vertical: 8),
        children: [
          for (final (icon, title, body) in sections)
            Padding(
              padding: const EdgeInsetsDirectional.fromSTEB(16, 12, 16, 12),
              child: Row(
                crossAxisAlignment: CrossAxisAlignment.start,
                children: [
                  Icon(icon, color: theme.colorScheme.primary),
                  const SizedBox(width: 16),
                  Expanded(
                    child: Column(
                      crossAxisAlignment: CrossAxisAlignment.start,
                      children: [
                        Text(title, style: theme.textTheme.titleMedium),
                        const SizedBox(height: 4),
                        Text(body, style: theme.textTheme.bodyMedium),
                      ],
                    ),
                  ),
                ],
              ),
            ),
        ],
      ),
    );
  }
}
