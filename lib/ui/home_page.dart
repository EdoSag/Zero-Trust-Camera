import 'package:flutter/material.dart';
import 'package:flutter_riverpod/flutter_riverpod.dart';
import 'package:url_launcher/url_launcher.dart';
import 'package:zero_trust_core/zero_trust_core.dart';

import '../app/account_gate.dart';
import '../app/auth_providers.dart';
import '../app/camera_bridge.dart';
import '../app/camera_config.dart';
import '../l10n/l10n.dart';
import '../private_vault/vault_page.dart';
import 'data_protection_page.dart';
import 'default_camera.dart';

/// Opened from the camera's settings: the optional Zero-Trust account, what
/// photos keep, language, appearance and about. Needs no account.
class HomePage extends ConsumerWidget {
  const HomePage({super.key, this.onOpenUrl});

  /// Opens a web page; tests pass their own.
  final void Function(Uri url)? onOpenUrl;

  void _open(Uri url) => onOpenUrl != null
      ? onOpenUrl!(url)
      : launchUrl(url, mode: LaunchMode.externalApplication);

  @override
  Widget build(BuildContext context, WidgetRef ref) {
    final l = context.l10n;
    final zt = ZtL10n.of(context);
    final theme = Theme.of(context);
    final user = ref.watch(currentUserProvider);
    final themeMode = ref.watch(themeModeProvider);
    final language = ref.watch(ztLanguageProvider);

    return Scaffold(
      appBar: AppBar(
        leading: IconButton(
          icon: const BackButtonIcon(),
          tooltip: l.backToCamera,
          onPressed: () => ref.read(cameraBridgeProvider).close(),
        ),
        title: Text(l.homeTitle),
      ),
      body: ListView(
        children: [
          Padding(
            padding: const EdgeInsetsDirectional.fromSTEB(16, 16, 16, 8),
            child: Row(
              children: [
                const ZtBrandMark(app: cameraSuiteApp, size: 56),
                const SizedBox(width: 16),
                Expanded(
                  child: Column(
                    crossAxisAlignment: CrossAxisAlignment.start,
                    children: [
                      Text(
                        cameraAppConfig.displayName,
                        style: theme.textTheme.titleMedium,
                      ),
                      const SizedBox(height: 4),
                      Text(l.homeTagline, style: theme.textTheme.bodyMedium),
                    ],
                  ),
                ),
              ],
            ),
          ),

          const DefaultCameraCard(),

          _Header(zt.account),
          if (user == null)
            ListTile(
              leading: const Icon(Icons.account_circle_outlined),
              title: Text(l.accountSignIn),
              subtitle: Text(l.accountOptional),
              onTap: () => _openAccount(context),
            )
          else
            ListTile(
              leading: const Icon(Icons.account_circle_outlined),
              title: Text(user.email ?? zt.account),
              subtitle: Text(l.accountManage),
              trailing: const Icon(Icons.chevron_right),
              onTap: () => _openAccount(context),
            ),

          ListTile(
            leading: const Icon(Icons.enhanced_encryption_outlined),
            title: Text(l.homeVault),
            subtitle: Text(l.homeVaultSubtitle),
            trailing: const Icon(Icons.chevron_right),
            onTap: () => Navigator.of(context).push(PrivateVaultPage.route()),
          ),
          ListTile(
            leading: const Icon(Icons.document_scanner_outlined),
            title: Text(l.homeScanDocument),
            subtitle: Text(l.homeScanDocumentSubtitle),
            trailing: const Icon(Icons.chevron_right),
            onTap: () => ref.read(cameraBridgeProvider).openDocScanner(),
          ),
          ListTile(
            leading: const Icon(Icons.tune),
            title: Text(l.homeProCamera),
            subtitle: Text(l.homeProCameraSubtitle),
            trailing: const Icon(Icons.chevron_right),
            onTap: () => ref.read(cameraBridgeProvider).openProCamera(),
          ),
          ListTile(
            leading: const Icon(Icons.panorama_horizontal_outlined),
            title: Text(l.homePanorama),
            subtitle: Text(l.homePanoramaSubtitle),
            trailing: const Icon(Icons.chevron_right),
            onTap: () => ref.read(cameraBridgeProvider).openPanorama(),
          ),

          _Header(l.privacySection),
          ListTile(
            leading: const Icon(Icons.location_off_outlined),
            title: Text(l.privacyLocation),
            subtitle: Text(l.privacyLocationNever),
          ),
          ListTile(
            leading: const Icon(Icons.event_busy_outlined),
            title: Text(l.privacyDateTime),
            subtitle: Text(l.privacyOffByDefault),
          ),
          ListTile(
            leading: const Icon(Icons.phone_android_outlined),
            title: Text(l.privacyModel),
            subtitle: Text(l.privacyOffByDefault),
          ),
          ListTile(
            leading: const Icon(Icons.apps_outlined),
            title: Text(l.privacyOtherApps),
            subtitle: Text(l.privacyOtherAppsNever),
          ),

          ListTile(
            leading: const Icon(Icons.shield_outlined),
            title: Text(l.protectTitle),
            subtitle: Text(l.protectSubtitle),
            trailing: const Icon(Icons.chevron_right),
            onTap: () => Navigator.of(context).push(
              MaterialPageRoute<void>(
                builder: (_) => const DataProtectionPage(),
              ),
            ),
          ),
          ListTile(
            leading: const Icon(Icons.camera_alt_outlined),
            title: Text(l.defaultCameraTitle),
            subtitle: Text(l.defaultCameraSubtitle),
            trailing: const Icon(Icons.chevron_right),
            onTap: () => Navigator.of(context).push(
              MaterialPageRoute<void>(
                builder: (_) => const DefaultCameraPage(),
              ),
            ),
          ),

          _Header(zt.appearance),
          ListTile(
            leading: const Icon(Icons.language),
            title: Text(zt.language),
            subtitle: Text(
              language == null
                  ? zt.languagePhone
                  : ZtLanguages.nativeName(language),
            ),
            onTap: () => _pickLanguage(context, ref, language),
          ),
          RadioGroup<ThemeMode>(
            groupValue: themeMode,
            onChanged: (m) => ref.read(themeModeProvider.notifier).set(m!),
            child: Column(
              children: [
                RadioListTile(
                  value: ThemeMode.system,
                  title: Text(zt.systemDefault),
                ),
                RadioListTile(value: ThemeMode.light, title: Text(zt.light)),
                RadioListTile(value: ThemeMode.dark, title: Text(zt.dark)),
              ],
            ),
          ),

          _Header(zt.about),
          ListTile(
            leading: const Icon(Icons.help_outline),
            title: Text(zt.helpAndFeedback),
            onTap: () => _open(Uri.parse('https://zerotrust.bz/support')),
          ),
          ListTile(
            leading: const Icon(Icons.policy_outlined),
            title: Text(zt.privacyPolicy),
            onTap: () => _open(Uri.parse('https://zerotrust.bz/privacy')),
          ),
          ListTile(
            leading: const Icon(Icons.gavel_outlined),
            title: Text(zt.termsOfUse),
            onTap: () => _open(Uri.parse('https://zerotrust.bz/terms')),
          ),
          ListTile(
            leading: const Icon(Icons.description_outlined),
            title: Text(zt.openSourceLicences),
            subtitle: Text(l.cameraScreensLicence),
            onTap: () => showLicensePage(
              context: context,
              applicationName: cameraAppConfig.displayName,
              applicationIcon: const Padding(
                padding: EdgeInsets.all(8),
                child: ZtBrandMark(app: cameraSuiteApp, size: 48),
              ),
            ),
          ),
          const SizedBox(height: 24),
        ],
      ),
    );
  }

  void _openAccount(BuildContext context) => Navigator.of(
    context,
  ).push(MaterialPageRoute<void>(builder: (_) => const AccountGate()));

  /// English, Hebrew or the phone's language, as in the shared settings.
  Future<void> _pickLanguage(
    BuildContext context,
    WidgetRef ref,
    Locale? current,
  ) async {
    const phone = 'phone';
    final picked = await showDialog<String>(
      context: context,
      builder: (context) => SimpleDialog(
        title: Text(ZtL10n.of(context).language),
        children: [
          RadioGroup<String>(
            groupValue: current?.languageCode ?? phone,
            onChanged: (code) => Navigator.pop(context, code),
            child: Column(
              mainAxisSize: MainAxisSize.min,
              children: [
                for (final locale in ZtLanguages.supported)
                  RadioListTile<String>(
                    value: locale.languageCode,
                    // Each name in its own language, so it can be found
                    // whatever the app is showing now.
                    title: Text(ZtLanguages.nativeName(locale)),
                  ),
                RadioListTile<String>(
                  value: phone,
                  title: Text(ZtL10n.of(context).languagePhone),
                ),
              ],
            ),
          ),
        ],
      ),
    );
    if (picked == null) return;
    await ref
        .read(ztLanguageProvider.notifier)
        .set(
          picked == phone
              ? null
              : ZtLanguages.supported.firstWhere(
                  (l) => l.languageCode == picked,
                ),
        );
  }
}

class _Header extends StatelessWidget {
  const _Header(this.text);

  final String text;

  @override
  Widget build(BuildContext context) {
    final theme = Theme.of(context);
    return Padding(
      padding: const EdgeInsetsDirectional.fromSTEB(16, 24, 16, 8),
      child: Text(
        text,
        style: theme.textTheme.titleSmall?.copyWith(
          color: theme.colorScheme.primary,
        ),
      ),
    );
  }
}
