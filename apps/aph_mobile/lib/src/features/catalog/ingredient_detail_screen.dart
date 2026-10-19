import 'package:flutter/material.dart';
import 'package:flutter_riverpod/flutter_riverpod.dart';

import '../../core/localization/aph_localizations.dart';
import '../../domain/body_system.dart';
import '../../domain/catalog_models.dart';
import '../../providers.dart';

class IngredientDetailScreen extends ConsumerWidget {
  const IngredientDetailScreen({required this.ingredientId, super.key});

  final String ingredientId;

  @override
  Widget build(BuildContext context, WidgetRef ref) {
    final ingredient = ref.watch(ingredientProvider(ingredientId));
    return Scaffold(
      body: ingredient.when(
        data: (value) => value == null
            ? Center(child: Text(context.l10n.text('noResults')))
            : _IngredientDetail(ingredient: value),
        loading: () => const Center(child: CircularProgressIndicator()),
        error: (error, stack) => Center(child: Text(error.toString())),
      ),
    );
  }
}

class _IngredientDetail extends ConsumerWidget {
  const _IngredientDetail({required this.ingredient});

  final ActiveIngredient ingredient;

  @override
  Widget build(BuildContext context, WidgetRef ref) {
    final isArabic = context.l10n.isArabic;
    final system = bodySystemById(ingredient.systemId);
    final products = ref.watch(productsProvider(ingredient.id));
    final isFavorite = ref.watch(isFavoriteProvider(ingredient.id)).valueOrNull ?? false;
    return CustomScrollView(
      slivers: [
        SliverAppBar.large(
          pinned: true,
          expandedHeight: 240,
          actions: [
            IconButton(
              onPressed: () => ref.read(catalogRepositoryProvider).toggleFavorite(ingredient.id),
              tooltip: isFavorite ? context.l10n.text('saved') : context.l10n.text('save'),
              icon: Icon(isFavorite ? Icons.bookmark : Icons.bookmark_border),
            ),
          ],
          flexibleSpace: FlexibleSpaceBar(
            titlePadding: const EdgeInsetsDirectional.fromSTEB(58, 0, 58, 18),
            title: Text(
              ingredient.name(isArabic),
              maxLines: 1,
              overflow: TextOverflow.ellipsis,
            ),
            background: DecoratedBox(
              decoration: BoxDecoration(
                gradient: LinearGradient(
                  colors: [
                    system.color.withValues(alpha: 0.08),
                    system.color.withValues(alpha: 0.26),
                  ],
                  begin: Alignment.topCenter,
                  end: Alignment.bottomCenter,
                ),
              ),
              child: Center(
                child: Icon(system.icon, size: 88, color: system.color.withValues(alpha: 0.55)),
              ),
            ),
          ),
        ),
        SliverPadding(
          padding: const EdgeInsets.fromLTRB(18, 20, 18, 32),
          sliver: SliverList.list(
            children: [
              Wrap(
                spacing: 8,
                runSpacing: 8,
                children: [
                  Chip(
                    avatar: Icon(system.icon, size: 17, color: system.color),
                    label: Text(system.localized(isArabic)),
                  ),
                  if (ingredient.atcCode.isNotEmpty) Chip(label: Text('ATC ${ingredient.atcCode}')),
                  Chip(
                    avatar: Icon(
                      ingredient.verificationStatus == 'verified'
                          ? Icons.verified_outlined
                          : Icons.info_outline,
                      size: 17,
                    ),
                    label: Text(
                      ingredient.verificationStatus == 'verified'
                          ? context.l10n.text('verified')
                          : context.l10n.text('unknown'),
                    ),
                  ),
                ],
              ),
              const SizedBox(height: 16),
              _SafetyNotice(text: context.l10n.text('medicalNotice')),
              const SizedBox(height: 18),
              _InfoSection(
                icon: Icons.science_outlined,
                title: context.l10n.text('mechanism'),
                content: isArabic ? ingredient.mechanismAr : ingredient.mechanism,
              ),
              _ListSection(
                icon: Icons.checklist_outlined,
                title: context.l10n.text('indications'),
                items: isArabic ? ingredient.indicationsAr : ingredient.indications,
              ),
              _InfoSection(
                icon: Icons.medication_liquid_outlined,
                title: context.l10n.text('dosing'),
                content: isArabic ? ingredient.dosageNoteAr : ingredient.dosageNote,
              ),
              _InfoSection(
                icon: Icons.water_drop_outlined,
                title: isArabic ? 'تعديل الجرعة الكلوية' : 'Renal adjustment',
                content: isArabic ? ingredient.renalAdjustmentAr : ingredient.renalAdjustment,
              ),
              _InfoSection(
                icon: Icons.health_and_safety_outlined,
                title: isArabic ? 'تعديل الجرعة الكبدية' : 'Hepatic adjustment',
                content: isArabic ? ingredient.hepaticAdjustmentAr : ingredient.hepaticAdjustment,
              ),
              _ListSection(
                icon: Icons.block_outlined,
                title: context.l10n.text('contraindications'),
                items: isArabic ? ingredient.contraindicationsAr : ingredient.contraindications,
              ),
              _ListSection(
                icon: Icons.warning_amber_rounded,
                title: context.l10n.text('warnings'),
                items: isArabic ? ingredient.cautionsAr : ingredient.cautions,
              ),
              _ListSection(
                icon: Icons.sick_outlined,
                title: context.l10n.text('adverseEffects'),
                items: isArabic ? ingredient.adverseEffectsAr : ingredient.adverseEffects,
              ),
              _ListSection(
                icon: Icons.compare_arrows_outlined,
                title: context.l10n.text('interactions'),
                items: ingredient.interactions,
              ),
              _InfoSection(
                icon: Icons.pregnant_woman_outlined,
                title: isArabic ? 'الحمل والرضاعة' : 'Pregnancy & lactation',
                content: isArabic ? ingredient.pregnancyLactationAr : ingredient.pregnancyLactation,
              ),
              const SizedBox(height: 4),
              Text(
                context.l10n.text('brandProducts'),
                style: Theme.of(context).textTheme.titleLarge?.copyWith(fontWeight: FontWeight.w900),
              ),
              const SizedBox(height: 10),
              products.when(
                data: (items) => items.isEmpty
                    ? _MutedMessage(text: context.l10n.text('notDocumented'))
                    : Column(
                        children: items
                            .map(
                              (product) => Padding(
                                padding: const EdgeInsets.only(bottom: 10),
                                child: _ProductCard(product: product),
                              ),
                            )
                            .toList(growable: false),
                      ),
                loading: () => const Center(child: CircularProgressIndicator()),
                error: (error, stack) => _MutedMessage(text: error.toString()),
              ),
              const SizedBox(height: 12),
              Text(
                context.l10n.text('sources'),
                style: Theme.of(context).textTheme.titleLarge?.copyWith(fontWeight: FontWeight.w900),
              ),
              const SizedBox(height: 10),
              if (ingredient.sourceRefs.isEmpty)
                _MutedMessage(text: context.l10n.text('notDocumented'))
              else
                ...ingredient.sourceRefs.map((source) => _SourceCard(source: source)),
            ],
          ),
        ),
      ],
    );
  }
}

class _SafetyNotice extends StatelessWidget {
  const _SafetyNotice({required this.text});

  final String text;

  @override
  Widget build(BuildContext context) => Container(
        padding: const EdgeInsets.all(14),
        decoration: BoxDecoration(
          color: Theme.of(context).colorScheme.secondaryContainer.withValues(alpha: 0.65),
          borderRadius: BorderRadius.circular(18),
        ),
        child: Row(
          crossAxisAlignment: CrossAxisAlignment.start,
          children: [
            Icon(Icons.health_and_safety_outlined, color: Theme.of(context).colorScheme.onSecondaryContainer),
            const SizedBox(width: 10),
            Expanded(child: Text(text, style: Theme.of(context).textTheme.bodySmall)),
          ],
        ),
      );
}

class _InfoSection extends StatelessWidget {
  const _InfoSection({required this.icon, required this.title, required this.content});

  final IconData icon;
  final String title;
  final String content;

  @override
  Widget build(BuildContext context) => _SectionCard(
        icon: icon,
        title: title,
        child: Text(
          content.isEmpty ? context.l10n.text('notDocumented') : content,
          style: Theme.of(context).textTheme.bodyMedium?.copyWith(height: 1.65),
        ),
      );
}

class _ListSection extends StatelessWidget {
  const _ListSection({required this.icon, required this.title, required this.items});

  final IconData icon;
  final String title;
  final List<String> items;

  @override
  Widget build(BuildContext context) => _SectionCard(
        icon: icon,
        title: title,
        child: items.isEmpty
            ? Text(context.l10n.text('notDocumented'))
            : Column(
                children: items
                    .map(
                      (item) => Padding(
                        padding: const EdgeInsets.only(bottom: 8),
                        child: Row(
                          crossAxisAlignment: CrossAxisAlignment.start,
                          children: [
                            Padding(
                              padding: const EdgeInsets.only(top: 7),
                              child: CircleAvatar(
                                radius: 3,
                                backgroundColor: Theme.of(context).colorScheme.primary,
                              ),
                            ),
                            const SizedBox(width: 10),
                            Expanded(child: Text(item)),
                          ],
                        ),
                      ),
                    )
                    .toList(growable: false),
              ),
      );
}

class _SectionCard extends StatelessWidget {
  const _SectionCard({required this.icon, required this.title, required this.child});

  final IconData icon;
  final String title;
  final Widget child;

  @override
  Widget build(BuildContext context) => Padding(
        padding: const EdgeInsets.only(bottom: 12),
        child: Card(
          child: Padding(
            padding: const EdgeInsets.all(17),
            child: Column(
              crossAxisAlignment: CrossAxisAlignment.start,
              children: [
                Row(
                  children: [
                    Icon(icon, size: 20, color: Theme.of(context).colorScheme.primary),
                    const SizedBox(width: 8),
                    Expanded(
                      child: Text(
                        title,
                        style: Theme.of(context).textTheme.titleMedium?.copyWith(fontWeight: FontWeight.w800),
                      ),
                    ),
                  ],
                ),
                const SizedBox(height: 12),
                child,
              ],
            ),
          ),
        ),
      );
}

class _ProductCard extends StatelessWidget {
  const _ProductCard({required this.product});

  final BrandProduct product;

  @override
  Widget build(BuildContext context) => Card(
        child: Padding(
          padding: const EdgeInsets.all(15),
          child: Row(
            children: [
              Container(
                width: 48,
                height: 48,
                decoration: BoxDecoration(
                  color: Theme.of(context).colorScheme.primaryContainer,
                  borderRadius: BorderRadius.circular(14),
                ),
                child: const Icon(Icons.medication_outlined),
              ),
              const SizedBox(width: 12),
              Expanded(
                child: Column(
                  crossAxisAlignment: CrossAxisAlignment.start,
                  children: [
                    Text(
                      product.name(context.l10n.isArabic),
                      style: Theme.of(context).textTheme.titleSmall?.copyWith(fontWeight: FontWeight.w800),
                    ),
                    const SizedBox(height: 4),
                    Text(
                      [product.strength, product.dosageForm, product.route]
                          .where((value) => value.isNotEmpty)
                          .join(' · '),
                      style: Theme.of(context).textTheme.bodySmall,
                    ),
                    if (product.manufacturer.isNotEmpty)
                      Text(
                        product.manufacturer,
                        style: Theme.of(context).textTheme.labelSmall?.copyWith(
                              color: Theme.of(context).colorScheme.onSurfaceVariant,
                            ),
                      ),
                  ],
                ),
              ),
              Tooltip(
                message: product.availabilityStatus,
                child: const Icon(Icons.verified_user_outlined, size: 20),
              ),
            ],
          ),
        ),
      );
}

class _SourceCard extends StatelessWidget {
  const _SourceCard({required this.source});

  final Map<String, dynamic> source;

  @override
  Widget build(BuildContext context) {
    final title = source['label'] ?? source['title'] ?? source['authority'] ?? 'Source';
    final url = source['url']?.toString() ?? '';
    return Padding(
      padding: const EdgeInsets.only(bottom: 8),
      child: Card(
        child: ListTile(
          leading: const Icon(Icons.menu_book_outlined),
          title: Text(title.toString()),
          subtitle: url.isEmpty ? null : Text(url, maxLines: 2, overflow: TextOverflow.ellipsis),
          trailing: const Icon(Icons.verified_outlined),
        ),
      ),
    );
  }
}

class _MutedMessage extends StatelessWidget {
  const _MutedMessage({required this.text});

  final String text;

  @override
  Widget build(BuildContext context) => Padding(
        padding: const EdgeInsets.symmetric(vertical: 14),
        child: Text(
          text,
          style: Theme.of(context).textTheme.bodyMedium?.copyWith(
                color: Theme.of(context).colorScheme.onSurfaceVariant,
              ),
        ),
      );
}
