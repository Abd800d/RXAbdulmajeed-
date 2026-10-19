import 'package:flutter/material.dart';
import 'package:flutter_riverpod/flutter_riverpod.dart';

import '../../core/config/app_config.dart';
import '../../core/localization/aph_localizations.dart';
import '../../data/remote/aph_api_client.dart';
import '../../providers.dart';

class AssistantScreen extends ConsumerStatefulWidget {
  const AssistantScreen({super.key});

  @override
  ConsumerState<AssistantScreen> createState() => _AssistantScreenState();
}

class _AssistantScreenState extends ConsumerState<AssistantScreen> {
  final _question = TextEditingController();
  AssistantReply? _reply;
  String? _error;
  bool _loading = false;

  @override
  void dispose() {
    _question.dispose();
    super.dispose();
  }

  Future<void> _ask() async {
    final question = _question.text.trim();
    if (question.length < 2 || _loading) return;
    setState(() {
      _loading = true;
      _error = null;
    });
    try {
      final reply = await ref.read(apiClientProvider).askAssistant(
            question,
            language: context.l10n.locale.languageCode,
          );
      if (mounted) setState(() => _reply = reply);
    } catch (_) {
      if (mounted) setState(() => _error = context.l10n.text('comingOnline'));
    } finally {
      if (mounted) setState(() => _loading = false);
    }
  }

  @override
  Widget build(BuildContext context) => Scaffold(
        appBar: AppBar(title: Text(context.l10n.text('assistant'))),
        body: ListView(
          padding: const EdgeInsets.fromLTRB(18, 10, 18, 32),
          children: [
            Container(
              padding: const EdgeInsets.all(16),
              decoration: BoxDecoration(
                color: Theme.of(context).colorScheme.tertiaryContainer,
                borderRadius: BorderRadius.circular(20),
              ),
              child: Row(
                crossAxisAlignment: CrossAxisAlignment.start,
                children: [
                  const Icon(Icons.health_and_safety_outlined),
                  const SizedBox(width: 10),
                  Expanded(child: Text(context.l10n.text('medicalNotice'))),
                ],
              ),
            ),
            const SizedBox(height: 18),
            TextField(
              controller: _question,
              minLines: 3,
              maxLines: 7,
              maxLength: 2500,
              enabled: AppConfig.cloudConfigured && !_loading,
              decoration: InputDecoration(
                hintText: context.l10n.isArabic
                    ? 'اسأل عن معلومة في سجل دوائي معتمد…'
                    : 'Ask about an approved medicine record…',
              ),
            ),
            const SizedBox(height: 10),
            FilledButton.icon(
              onPressed: AppConfig.cloudConfigured && !_loading ? _ask : null,
              icon: _loading
                  ? const SizedBox.square(
                      dimension: 18,
                      child: CircularProgressIndicator(strokeWidth: 2),
                    )
                  : const Icon(Icons.auto_awesome_outlined),
              label: Text(context.l10n.text('assistant')),
            ),
            if (!AppConfig.cloudConfigured || _error != null) ...[
              const SizedBox(height: 16),
              Text(
                _error ?? context.l10n.text('comingOnline'),
                style: TextStyle(color: Theme.of(context).colorScheme.error),
              ),
            ],
            if (_reply case final reply?) ...[
              const SizedBox(height: 22),
              Card(
                child: Padding(
                  padding: const EdgeInsets.all(18),
                  child: Column(
                    crossAxisAlignment: CrossAxisAlignment.start,
                    children: [
                      Row(
                        children: [
                          const Icon(Icons.auto_awesome_outlined),
                          const SizedBox(width: 8),
                          Expanded(
                            child: Text(
                              '${context.l10n.text('assistant')} · ${reply.confidence}',
                              style: Theme.of(context).textTheme.titleMedium?.copyWith(
                                    fontWeight: FontWeight.w800,
                                  ),
                            ),
                          ),
                        ],
                      ),
                      const SizedBox(height: 14),
                      SelectableText(reply.answer),
                      const SizedBox(height: 16),
                      Text(
                        reply.disclaimer,
                        style: Theme.of(context).textTheme.bodySmall?.copyWith(
                              color: Theme.of(context).colorScheme.onSurfaceVariant,
                            ),
                      ),
                    ],
                  ),
                ),
              ),
              const SizedBox(height: 16),
              Text(
                context.l10n.text('sources'),
                style: Theme.of(context).textTheme.titleMedium?.copyWith(fontWeight: FontWeight.w800),
              ),
              const SizedBox(height: 8),
              if (reply.sources.isEmpty)
                Text(context.l10n.text('notDocumented'))
              else
                ...reply.sources.map(
                  (source) => Card(
                    child: ListTile(
                      leading: const Icon(Icons.menu_book_outlined),
                      title: Text((source['label'] ?? source['authority'] ?? 'Source').toString()),
                      subtitle: Text((source['url'] ?? '').toString()),
                    ),
                  ),
                ),
            ],
          ],
        ),
      );
}
