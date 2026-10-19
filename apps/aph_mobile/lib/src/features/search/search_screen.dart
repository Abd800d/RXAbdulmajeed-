import 'dart:async';

import 'package:flutter/material.dart';
import 'package:flutter_riverpod/flutter_riverpod.dart';

import '../../core/config/app_config.dart';
import '../../core/localization/aph_localizations.dart';
import '../../domain/body_system.dart';
import '../../domain/catalog_models.dart';
import '../../providers.dart';
import '../../widgets/ingredient_card.dart';

class SearchScreen extends ConsumerStatefulWidget {
  const SearchScreen({super.key});

  @override
  ConsumerState<SearchScreen> createState() => _SearchScreenState();
}

class _SearchScreenState extends ConsumerState<SearchScreen> {
  final _controller = TextEditingController();
  Timer? _debounce;
  String _query = '';
  String? _systemId;
  bool _cloudSearching = false;

  @override
  void dispose() {
    _debounce?.cancel();
    _controller.dispose();
    super.dispose();
  }

  void _onChanged(String value) {
    setState(() => _query = value.trim());
    _debounce?.cancel();
    if (!AppConfig.cloudConfigured || _query.length < 2) return;
    _debounce = Timer(const Duration(milliseconds: 450), () async {
      if (!mounted) return;
      setState(() => _cloudSearching = true);
      try {
        await ref.read(catalogRepositoryProvider).searchCloud(
              _query,
              context.l10n.locale.languageCode,
            );
      } catch (_) {
        // The permanent local catalogue remains usable when cloud search fails.
      } finally {
        if (mounted) setState(() => _cloudSearching = false);
      }
    });
  }

  @override
  Widget build(BuildContext context) {
    final repository = ref.watch(catalogRepositoryProvider);
    return Column(
      children: [
        Padding(
          padding: const EdgeInsets.fromLTRB(18, 14, 18, 12),
          child: Column(
            crossAxisAlignment: CrossAxisAlignment.start,
            children: [
              Text(
                context.l10n.text('catalogue'),
                style: Theme.of(context).textTheme.headlineSmall?.copyWith(fontWeight: FontWeight.w900),
              ),
              const SizedBox(height: 14),
              TextField(
                controller: _controller,
                autofocus: false,
                textInputAction: TextInputAction.search,
                onChanged: _onChanged,
                decoration: InputDecoration(
                  hintText: context.l10n.text('searchHint'),
                  prefixIcon: const Icon(Icons.search),
                  suffixIcon: _cloudSearching
                      ? const Padding(
                          padding: EdgeInsets.all(14),
                          child: SizedBox.square(
                            dimension: 18,
                            child: CircularProgressIndicator(strokeWidth: 2),
                          ),
                        )
                      : _query.isNotEmpty
                          ? IconButton(
                              onPressed: () {
                                _controller.clear();
                                _onChanged('');
                              },
                              icon: const Icon(Icons.close),
                            )
                          : null,
                ),
              ),
            ],
          ),
        ),
        SizedBox(
          height: 46,
          child: ListView.separated(
            padding: const EdgeInsets.symmetric(horizontal: 18),
            scrollDirection: Axis.horizontal,
            itemCount: bodySystems.length + 1,
            separatorBuilder: (_, __) => const SizedBox(width: 7),
            itemBuilder: (context, index) {
              final system = index == 0 ? null : bodySystems[index - 1];
              final selected = system?.id == _systemId || system == null && _systemId == null;
              return FilterChip(
                selected: selected,
                avatar: system == null ? null : Icon(system.icon, size: 16, color: system.color),
                label: Text(system?.localized(context.l10n.isArabic) ?? context.l10n.text('viewAll')),
                onSelected: (_) => setState(() => _systemId = system?.id),
              );
            },
          ),
        ),
        const SizedBox(height: 8),
        Expanded(
          child: StreamBuilder<List<ActiveIngredient>>(
            stream: repository.searchLocal(_query),
            builder: (context, snapshot) {
              final items = (snapshot.data ?? const <ActiveIngredient>[])
                  .where((ingredient) => _systemId == null || ingredient.systemId == _systemId)
                  .toList(growable: false);
              if (snapshot.connectionState == ConnectionState.waiting && !snapshot.hasData) {
                return const Center(child: CircularProgressIndicator());
              }
              if (items.isEmpty) {
                return const _EmptySearch();
              }
              return ListView.separated(
                padding: const EdgeInsets.fromLTRB(18, 8, 18, 24),
                itemCount: items.length,
                separatorBuilder: (_, __) => const SizedBox(height: 10),
                itemBuilder: (context, index) => IngredientCard(ingredient: items[index]),
              );
            },
          ),
        ),
      ],
    );
  }
}

class _EmptySearch extends StatelessWidget {
  const _EmptySearch();

  @override
  Widget build(BuildContext context) => Center(
        child: Padding(
          padding: const EdgeInsets.all(32),
          child: Column(
            mainAxisSize: MainAxisSize.min,
            children: [
              Icon(
                Icons.manage_search_outlined,
                size: 54,
                color: Theme.of(context).colorScheme.primary,
              ),
              const SizedBox(height: 16),
              Text(
                context.l10n.text('noResults'),
                style: Theme.of(context).textTheme.titleMedium?.copyWith(fontWeight: FontWeight.w800),
              ),
              const SizedBox(height: 6),
              Text(
                context.l10n.text('tryAnotherSearch'),
                textAlign: TextAlign.center,
                style: Theme.of(context).textTheme.bodyMedium?.copyWith(
                      color: Theme.of(context).colorScheme.onSurfaceVariant,
                    ),
              ),
            ],
          ),
        ),
      );
}
