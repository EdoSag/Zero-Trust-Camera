import 'package:flutter/material.dart';
import 'package:flutter/services.dart';
import 'package:flutter_riverpod/flutter_riverpod.dart';
import 'package:intl/intl.dart';

import '../l10n/l10n.dart';
import 'vault_backend.dart';

/// Scan history, inside the Private vault: the switch that keeps scanned
/// codes (off by default) and the codes kept so far. Opened only while the
/// vault is unlocked; if it locks, this page closes.
class ScanHistoryPage extends ConsumerStatefulWidget {
  const ScanHistoryPage({super.key});

  @override
  ConsumerState<ScanHistoryPage> createState() => _ScanHistoryPageState();
}

class _ScanHistoryPageState extends ConsumerState<ScanHistoryPage> {
  late final VaultBackend _vault = ref.read(vaultBackendProvider);
  bool? _on;
  List<ScanRecord>? _scans;

  @override
  void initState() {
    super.initState();
    _load();
  }

  Future<void> _load() async {
    try {
      final on = await _vault.scanHistory();
      final scans = await _vault.scans();
      if (!mounted) return;
      setState(() {
        _on = on;
        _scans = scans;
      });
    } on VaultLockedException {
      if (mounted) Navigator.of(context).pop();
    }
  }

  Future<void> _setOn(bool on) async {
    await _vault.setScanHistory(on);
    if (mounted) setState(() => _on = on);
  }

  Future<void> _delete(List<String> ids) async {
    await _vault.deleteScans(ids);
    await _load();
  }

  Future<void> _clearAll(AppL10n l) async {
    final scans = _scans ?? const [];
    if (scans.isEmpty) return;
    final ok = await showDialog<bool>(
      context: context,
      builder: (context) => AlertDialog(
        title: Text(l.scanHistoryClearTitle),
        content: Text(l.scanHistoryClearMessage(scans.length)),
        actions: [
          TextButton(
            onPressed: () => Navigator.of(context).pop(false),
            child: Text(MaterialLocalizations.of(context).cancelButtonLabel),
          ),
          TextButton(
            onPressed: () => Navigator.of(context).pop(true),
            child: Text(l.scanHistoryClear),
          ),
        ],
      ),
    );
    if (ok == true) await _delete([for (final s in scans) s.id]);
  }

  Future<void> _copy(AppL10n l, String text) async {
    await Clipboard.setData(ClipboardData(text: text));
    if (!mounted) return;
    ScaffoldMessenger.of(context)
      ..hideCurrentSnackBar()
      ..showSnackBar(SnackBar(content: Text(l.scanHistoryCopied)));
  }

  @override
  Widget build(BuildContext context) {
    final l = AppL10n.of(context);
    final scans = _scans;
    final locale = Localizations.localeOf(context).toLanguageTag();
    return Scaffold(
      appBar: AppBar(
        title: Text(l.scanHistoryTitle),
        actions: [
          if (scans != null && scans.isNotEmpty)
            IconButton(
              icon: const Icon(Icons.delete_sweep_outlined),
              tooltip: l.scanHistoryClear,
              onPressed: () => _clearAll(l),
            ),
        ],
      ),
      body: scans == null
          ? const Center(child: CircularProgressIndicator())
          : ListView(
              children: [
                SwitchListTile(
                  key: const Key('scan-history-switch'),
                  title: Text(l.scanHistoryKeep),
                  subtitle: Text(l.scanHistoryKeepHint),
                  value: _on ?? false,
                  onChanged: _setOn,
                ),
                const Divider(),
                if (scans.isEmpty)
                  Padding(
                    padding: const EdgeInsets.all(24),
                    child: Text(
                      l.scanHistoryEmpty,
                      textAlign: TextAlign.center,
                      style: Theme.of(context).textTheme.bodyMedium,
                    ),
                  ),
                for (final s in scans)
                  ListTile(
                    key: ValueKey('scan-${s.id}'),
                    leading: const Icon(Icons.qr_code_2),
                    title: Text(
                      s.text,
                      maxLines: 3,
                      overflow: TextOverflow.ellipsis,
                    ),
                    subtitle: Text(_when(s.at, locale)),
                    onTap: () => _copy(l, s.text),
                    trailing: IconButton(
                      icon: const Icon(Icons.delete_outline),
                      tooltip: l.scanHistoryDelete,
                      onPressed: () => _delete([s.id]),
                    ),
                  ),
              ],
            ),
    );
  }

  static String _when(DateTime t, String locale) =>
      DateFormat.yMMMd(locale).add_Hm().format(t);
}
