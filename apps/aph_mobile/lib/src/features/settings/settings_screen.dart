import 'package:flutter/material.dart';
import 'package:flutter_riverpod/flutter_riverpod.dart';
import 'package:intl/intl.dart';

import '../../core/config/app_config.dart';
import '../../core/localization/aph_localizations.dart';
import '../../providers.dart';
import '../../widgets/aph_brand.dart';

class SettingsScreen extends ConsumerWidget {
  const SettingsScreen({super.key});

  @override
  Widget build(BuildContext context, WidgetRef ref) {
    final locale = ref.watch(localeProvider);
    final themeMode = ref.watch(themeModeProvider);
    final syncState = ref.watch(syncControllerProvider);
    final lastSync = ref.watch(lastSyncProvider);
    return ListView(
      padding: const EdgeInsets.fromLTRB(18, 18, 18, 32),
      children: [
        Text(
          context.l10n.text('settings'),
          style: Theme.of(context).textTheme.headlineSmall?.copyWith(fontWeight: FontWeight.w900),
        ),
        const SizedBox(height: 18),
        Card(
          child: Column(
            children: [
              ListTile(
                leading: const Icon(Icons.language_outlined),
                title: Text(context.l10n.text('language')),
                trailing: DropdownButtonHideUnderline(
                  child: DropdownButton<String>(
                    value: locale.languageCode,
                    items: [
                      DropdownMenuItem(value: 'ar', child: Text(context.l10n.text('arabic'))),
                      DropdownMenuItem(value: 'en', child: Text(context.l10n.text('english'))),
                    ],
                    onChanged: (value) async {
                      if (value == null) return;
                      ref.read(localeProvider.notifier).state = Locale(value);
                      await ref.read(sharedPreferencesProvider).setString('locale', value);
                    },
                  ),
                ),
              ),
              const Divider(height: 1),
              ListTile(
                leading: const Icon(Icons.contrast_outlined),
                title: Text(context.l10n.text('appearance')),
                trailing: DropdownButtonHideUnderline(
                  child: DropdownButton<ThemeMode>(
                    value: themeMode,
                    items: const [
                      DropdownMenuItem(value: ThemeMode.system, child: Text('System')),
                      DropdownMenuItem(value: ThemeMode.light, child: Text('Light')),
                      DropdownMenuItem(value: ThemeMode.dark, child: Text('Dark')),
                    ],
                    onChanged: (value) async {
                      if (value == null) return;
                      ref.read(themeModeProvider.notifier).state = value;
                      await ref.read(sharedPreferencesProvider).setString('theme_mode', value.name);
                    },
                  ),
                ),
              ),
            ],
          ),
        ),
        const SizedBox(height: 12),
        Card(
          child: Padding(
            padding: const EdgeInsets.all(17),
            child: Column(
              crossAxisAlignment: CrossAxisAlignment.stretch,
              children: [
                Row(
                  children: [
                    const Icon(Icons.sync_outlined),
                    const SizedBox(width: 9),
                    Expanded(
                      child: Text(
                        context.l10n.text('lastSync'),
                        style: Theme.of(context).textTheme.titleMedium?.copyWith(fontWeight: FontWeight.w800),
                      ),
                    ),
                  ],
                ),
                const SizedBox(height: 8),
                Text(
                  lastSync.when(
                    data: (value) => value == null
                        ? context.l10n.text('never')
                        : DateFormat.yMMMd(locale.languageCode).add_Hm().format(value.toLocal()),
                    loading: () => '…',
                    error: (error, stack) => context.l10n.text('never'),
                  ),
                ),
                const SizedBox(height: 14),
                FilledButton.icon(
                  onPressed: syncState.isLoading
                      ? null
                      : () => ref.read(syncControllerProvider.notifier).synchronize(),
                  icon: syncState.isLoading
                      ? const SizedBox.square(
                          dimension: 18,
                          child: CircularProgressIndicator(strokeWidth: 2),
                        )
                      : const Icon(Icons.cloud_sync_outlined),
                  label: Text(context.l10n.text('syncNow')),
                ),
                if (!AppConfig.cloudConfigured) ...[
                  const SizedBox(height: 10),
                  Text(
                    context.l10n.text('comingOnline'),
                    style: Theme.of(context).textTheme.bodySmall?.copyWith(
                          color: Theme.of(context).colorScheme.onSurfaceVariant,
                        ),
                  ),
                ],
              ],
            ),
          ),
        ),
        const SizedBox(height: 12),
        Card(
          child: Padding(
            padding: const EdgeInsets.all(18),
            child: Column(
              crossAxisAlignment: CrossAxisAlignment.start,
              children: [
                const AphBrand(),
                const SizedBox(height: 18),
                Text(
                  context.l10n.text('about'),
                  style: Theme.of(context).textTheme.titleMedium?.copyWith(fontWeight: FontWeight.w800),
                ),
                const SizedBox(height: 7),
                const Text('APH V2 · Flutter transition build'),
                const SizedBox(height: 16),
                Text(
                  context.l10n.text('rights'),
                  style: Theme.of(context).textTheme.titleSmall?.copyWith(fontWeight: FontWeight.w800),
                ),
                const SizedBox(height: 5),
                Text('© 2026 ${AppConfig.appOwner}'),
                Text(AppConfig.supportEmail),
                const SizedBox(height: 14),
                Text(
                  context.l10n.text('medicalNotice'),
                  style: Theme.of(context).textTheme.bodySmall?.copyWith(
                        color: Theme.of(context).colorScheme.onSurfaceVariant,
                      ),
                ),
              ],
            ),
          ),
        ),
      ],
    );
  }
}
