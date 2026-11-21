import 'package:flutter/material.dart';
import 'package:flutter_riverpod/flutter_riverpod.dart';
import 'package:zero_trust_core/zero_trust_core.dart';

import '../app/camera_bridge.dart';
import '../l10n/l10n.dart';

/// How to make this the camera the phone opens, step by step. Android has
/// no "default camera" switch an app can flip, so these are the steps.
class DefaultCameraPage extends ConsumerWidget {
  const DefaultCameraPage({super.key});

  @override
  Widget build(BuildContext context, WidgetRef ref) {
    final l = context.l10n;
    final theme = Theme.of(context);
    final steps = [l.defaultCameraStep1, l.defaultCameraStep2];
    return Scaffold(
      appBar: AppBar(title: Text(l.defaultCameraTitle)),
      body: ListView(
        padding: const EdgeInsets.all(16),
        children: [
          Text(l.defaultCameraIntro),
          const SizedBox(height: 16),
          for (final (i, step) in steps.indexed)
            Padding(
              padding: const EdgeInsets.only(bottom: 12),
              child: Row(
                crossAxisAlignment: CrossAxisAlignment.start,
                children: [
                  CircleAvatar(
                    radius: 12,
                    child: Text('${i + 1}', style: theme.textTheme.labelMedium),
                  ),
                  const SizedBox(width: 12),
                  Expanded(child: Text(step)),
                ],
              ),
            ),
          const SizedBox(height: 4),
          OutlinedButton.icon(
            icon: const Icon(Icons.settings_outlined),
            label: Text(l.defaultCameraOpenSettings),
            onPressed: () => ref.read(cameraBridgeProvider).openDefaultApps(),
          ),
          const SizedBox(height: 16),
          Text(l.defaultCameraOtherApps, style: theme.textTheme.bodySmall),
        ],
      ),
    );
  }
}

/// A card on the home screen, shown until it is acted on or dismissed,
/// offering to make this the phone's camera.
class DefaultCameraCard extends ConsumerStatefulWidget {
  const DefaultCameraCard({super.key});

  static const prefKey = 'camera_default_prompt_done';

  @override
  ConsumerState<DefaultCameraCard> createState() => _DefaultCameraCardState();
}

class _DefaultCameraCardState extends ConsumerState<DefaultCameraCard> {
  bool _show = false;

  @override
  void initState() {
    super.initState();
    _read();
  }

  Future<void> _read() async {
    String? done;
    try {
      done = await ref
          .read(ztPreferencesStoreProvider)
          .read(DefaultCameraCard.prefKey);
    } on Object {
      // Can't tell: the card stays away rather than nag every time.
      return;
    }
    if (mounted && done != 'true') setState(() => _show = true);
  }

  Future<void> _done() async {
    setState(() => _show = false);
    try {
      await ref
          .read(ztPreferencesStoreProvider)
          .write(DefaultCameraCard.prefKey, 'true');
    } on Object {
      // Shown again next time.
    }
  }

  @override
  Widget build(BuildContext context) {
    if (!_show) return const SizedBox.shrink();
    final l = context.l10n;
    final theme = Theme.of(context);
    return Card(
      margin: const EdgeInsetsDirectional.fromSTEB(16, 8, 16, 0),
      child: Padding(
        padding: const EdgeInsetsDirectional.fromSTEB(16, 16, 8, 8),
        child: Column(
          crossAxisAlignment: CrossAxisAlignment.start,
          children: [
            Text(l.defaultCameraCardTitle, style: theme.textTheme.titleMedium),
            const SizedBox(height: 4),
            Text(l.defaultCameraCardBody),
            const SizedBox(height: 4),
            SizedBox(
              width: double.infinity,
              child:
                  // A Wrap: the two labels don't fit one line in every language.
                  Wrap(
                    alignment: WrapAlignment.end,
                    children: [
                      TextButton(
                        onPressed: _done,
                        child: Text(l.defaultCameraNotNow),
                      ),
                      TextButton(
                        onPressed: () {
                          _done();
                          Navigator.of(context).push(
                            MaterialPageRoute<void>(
                              builder: (_) => const DefaultCameraPage(),
                            ),
                          );
                        },
                        child: Text(l.defaultCameraShowMe),
                      ),
                    ],
                  ),
            ),
          ],
        ),
      ),
    );
  }
}
