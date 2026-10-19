import 'package:flutter/material.dart';
import 'package:flutter_riverpod/flutter_riverpod.dart';
import 'package:go_router/go_router.dart';
import 'package:mobile_scanner/mobile_scanner.dart';

import '../../core/localization/aph_localizations.dart';
import '../../domain/catalog_models.dart';
import '../../providers.dart';

class ScannerScreen extends ConsumerStatefulWidget {
  const ScannerScreen({super.key});

  @override
  ConsumerState<ScannerScreen> createState() => _ScannerScreenState();
}

class _ScannerScreenState extends ConsumerState<ScannerScreen> {
  final _scanner = MobileScannerController(
    detectionSpeed: DetectionSpeed.noDuplicates,
    formats: const [BarcodeFormat.all],
  );
  bool _processing = false;
  String? _lastBarcode;

  @override
  void dispose() {
    _scanner.dispose();
    super.dispose();
  }

  Future<void> _detected(BarcodeCapture capture) async {
    if (_processing) return;
    final value = capture.barcodes.isEmpty ? null : capture.barcodes.first.rawValue?.trim();
    if (value == null || value.isEmpty || value == _lastBarcode) return;
    _processing = true;
    _lastBarcode = value;
    final language = context.l10n.locale.languageCode;
    await _scanner.stop();
    ActiveIngredient? match;
    try {
      match = await ref.read(catalogRepositoryProvider).identifyBarcode(value, language);
    } catch (_) {
      match = null;
    }
    if (!mounted) return;
    final confirmedMatch = match;
    await showDialog<void>(
      context: context,
      builder: (dialogContext) => AlertDialog(
        icon: Icon(confirmedMatch == null ? Icons.help_outline : Icons.medication_outlined),
        title: Text(context.l10n.text('confirmMedicine')),
        content: Text(
          confirmedMatch == null
              ? '${context.l10n.text('noResults')}\n$value'
              : '${confirmedMatch.name(context.l10n.isArabic)}\n$value',
        ),
        actions: [
          TextButton(
            onPressed: () => Navigator.pop(dialogContext),
            child: Text(MaterialLocalizations.of(context).cancelButtonLabel),
          ),
          if (confirmedMatch != null)
            FilledButton(
              onPressed: () {
                Navigator.pop(dialogContext);
                context.push('/ingredient/${Uri.encodeComponent(confirmedMatch.id)}');
              },
              child: Text(context.l10n.text('details')),
            ),
        ],
      ),
    );
    if (!mounted) return;
    _processing = false;
    await _scanner.start();
  }

  @override
  Widget build(BuildContext context) => Stack(
        fit: StackFit.expand,
        children: [
          MobileScanner(controller: _scanner, onDetect: _detected),
          DecoratedBox(
            decoration: BoxDecoration(color: Colors.black.withValues(alpha: 0.24)),
            child: Column(
              children: [
                Padding(
                  padding: const EdgeInsets.fromLTRB(20, 20, 20, 12),
                  child: Text(
                    context.l10n.text('scan'),
                    style: Theme.of(context).textTheme.headlineSmall?.copyWith(
                          color: Colors.white,
                          fontWeight: FontWeight.w900,
                        ),
                  ),
                ),
                const Spacer(),
                Container(
                  width: 270,
                  height: 180,
                  decoration: BoxDecoration(
                    border: Border.all(color: Colors.white, width: 3),
                    borderRadius: BorderRadius.circular(24),
                  ),
                ),
                const Spacer(),
                Container(
                  margin: const EdgeInsets.all(18),
                  padding: const EdgeInsets.all(15),
                  decoration: BoxDecoration(
                    color: Colors.black.withValues(alpha: 0.7),
                    borderRadius: BorderRadius.circular(18),
                  ),
                  child: Row(
                    children: [
                      const Icon(Icons.verified_user_outlined, color: Colors.white),
                      const SizedBox(width: 10),
                      Expanded(
                        child: Text(
                          context.l10n.text('scanInstruction'),
                          style: Theme.of(context).textTheme.bodySmall?.copyWith(color: Colors.white),
                        ),
                      ),
                    ],
                  ),
                ),
              ],
            ),
          ),
        ],
      );
}
