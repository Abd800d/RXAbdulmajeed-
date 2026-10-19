import 'package:flutter/material.dart';
import 'package:flutter_riverpod/flutter_riverpod.dart';

import '../../core/localization/aph_localizations.dart';
import '../../domain/body_system.dart';
import '../../providers.dart';
import '../../widgets/ingredient_card.dart';

class SystemScreen extends ConsumerWidget {
  const SystemScreen({required this.systemId, super.key});

  final String systemId;

  @override
  Widget build(BuildContext context, WidgetRef ref) {
    final system = bodySystemById(systemId);
    final ingredients = ref.watch(systemIngredientsProvider(systemId));
    return Scaffold(
      appBar: AppBar(title: Text(system.localized(context.l10n.isArabic))),
      body: ingredients.when(
        data: (items) => items.isEmpty
            ? Center(child: Text(context.l10n.text('noResults')))
            : ListView.separated(
                padding: const EdgeInsets.fromLTRB(18, 8, 18, 28),
                itemCount: items.length,
                separatorBuilder: (_, __) => const SizedBox(height: 10),
                itemBuilder: (context, index) => IngredientCard(ingredient: items[index]),
              ),
        loading: () => const Center(child: CircularProgressIndicator()),
        error: (error, stack) => Center(child: Text(error.toString())),
      ),
    );
  }
}
