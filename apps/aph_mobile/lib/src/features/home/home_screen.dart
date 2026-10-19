import 'package:flutter/material.dart';
import 'package:flutter_riverpod/flutter_riverpod.dart';
import 'package:go_router/go_router.dart';

import '../../core/config/app_config.dart';
import '../../core/localization/aph_localizations.dart';
import '../../core/theme/aph_theme.dart';
import '../../domain/body_system.dart';
import '../../providers.dart';
import '../../widgets/aph_brand.dart';
import '../../widgets/ingredient_card.dart';

class HomeScreen extends ConsumerStatefulWidget {
  const HomeScreen({super.key});

  @override
  ConsumerState<HomeScreen> createState() => _HomeScreenState();
}

class _HomeScreenState extends ConsumerState<HomeScreen> {
  @override
  void initState() {
    super.initState();
    WidgetsBinding.instance.addPostFrameCallback((_) {
      if (AppConfig.cloudConfigured) {
        ref.read(syncControllerProvider.notifier).synchronize();
      }
    });
  }

  @override
  Widget build(BuildContext context) {
    final ingredients = ref.watch(allIngredientsProvider);
    return CustomScrollView(
      slivers: [
        SliverToBoxAdapter(
          child: Padding(
            padding: const EdgeInsets.fromLTRB(18, 14, 18, 0),
            child: Row(
              children: [
                const AphBrand(),
                const Spacer(),
                IconButton.filledTonal(
                  onPressed: () => context.push('/assistant'),
                  tooltip: context.l10n.text('assistant'),
                  icon: const Icon(Icons.auto_awesome_outlined),
                ),
              ],
            ),
          ),
        ),
        SliverToBoxAdapter(
          child: Padding(
            padding: const EdgeInsets.fromLTRB(18, 22, 18, 0),
            child: const _HeroCard(),
          ),
        ),
        SliverToBoxAdapter(
          child: Padding(
            padding: const EdgeInsets.fromLTRB(18, 24, 18, 12),
            child: _SectionTitle(
              title: context.l10n.text('bodySystems'),
              action: context.l10n.text('catalogue'),
              onTap: () => context.go('/search'),
            ),
          ),
        ),
        SliverPadding(
          padding: const EdgeInsets.symmetric(horizontal: 18),
          sliver: SliverGrid.builder(
            gridDelegate: const SliverGridDelegateWithFixedCrossAxisCount(
              crossAxisCount: 2,
              childAspectRatio: 2.15,
              crossAxisSpacing: 10,
              mainAxisSpacing: 10,
            ),
            itemCount: bodySystems.length,
            itemBuilder: (context, index) => _SystemCard(system: bodySystems[index]),
          ),
        ),
        SliverToBoxAdapter(
          child: Padding(
            padding: const EdgeInsets.fromLTRB(18, 28, 18, 12),
            child: _SectionTitle(
              title: context.l10n.text('recentlyUpdated'),
              action: context.l10n.text('viewAll'),
              onTap: () => context.go('/search'),
            ),
          ),
        ),
        ingredients.when(
          data: (items) => SliverPadding(
            padding: const EdgeInsets.fromLTRB(18, 0, 18, 24),
            sliver: SliverList.separated(
              itemCount: items.take(6).length,
              separatorBuilder: (_, __) => const SizedBox(height: 10),
              itemBuilder: (context, index) => IngredientCard(ingredient: items[index]),
            ),
          ),
          loading: () => const SliverToBoxAdapter(
            child: Padding(
              padding: EdgeInsets.all(40),
              child: Center(child: CircularProgressIndicator()),
            ),
          ),
          error: (error, stack) => SliverToBoxAdapter(
            child: Padding(
              padding: const EdgeInsets.all(24),
              child: Text(error.toString()),
            ),
          ),
        ),
        SliverToBoxAdapter(
          child: Padding(
            padding: const EdgeInsets.fromLTRB(24, 8, 24, 32),
            child: Text(
              '© 2026 ${AppConfig.appOwner} · APH',
              textAlign: TextAlign.center,
              style: Theme.of(context).textTheme.labelSmall?.copyWith(
                    color: Theme.of(context).colorScheme.onSurfaceVariant,
                  ),
            ),
          ),
        ),
      ],
    );
  }
}

class _HeroCard extends StatelessWidget {
  const _HeroCard();

  @override
  Widget build(BuildContext context) => Container(
        padding: const EdgeInsets.all(22),
        decoration: BoxDecoration(
          gradient: const LinearGradient(
            colors: [AphColors.navy, Color(0xFF0B5C67)],
            begin: Alignment.topLeft,
            end: Alignment.bottomRight,
          ),
          borderRadius: BorderRadius.circular(28),
          boxShadow: [
            BoxShadow(
              color: AphColors.navy.withValues(alpha: 0.2),
              blurRadius: 28,
              offset: const Offset(0, 14),
            ),
          ],
        ),
        child: Column(
          crossAxisAlignment: CrossAxisAlignment.start,
          children: [
            Row(
              children: [
                Container(
                  padding: const EdgeInsets.symmetric(horizontal: 10, vertical: 6),
                  decoration: BoxDecoration(
                    color: Colors.white.withValues(alpha: 0.12),
                    borderRadius: BorderRadius.circular(30),
                  ),
                  child: Row(
                    children: [
                      const Icon(Icons.offline_bolt_outlined, size: 15, color: AphColors.mint),
                      const SizedBox(width: 5),
                      Text(
                        context.l10n.text('offlineReady'),
                        style: Theme.of(context).textTheme.labelSmall?.copyWith(color: Colors.white),
                      ),
                    ],
                  ),
                ),
              ],
            ),
            const SizedBox(height: 18),
            Text(
              context.l10n.text('welcome'),
              style: Theme.of(context).textTheme.headlineSmall?.copyWith(
                    color: Colors.white,
                    fontWeight: FontWeight.w800,
                    height: 1.25,
                  ),
            ),
            const SizedBox(height: 18),
            Semantics(
              button: true,
              label: context.l10n.text('searchHint'),
              child: InkWell(
                onTap: () => context.go('/search'),
                borderRadius: BorderRadius.circular(18),
                child: Container(
                  padding: const EdgeInsets.symmetric(horizontal: 16, vertical: 15),
                  decoration: BoxDecoration(
                    color: Colors.white,
                    borderRadius: BorderRadius.circular(18),
                  ),
                  child: Row(
                    children: [
                      const Icon(Icons.search, color: AphColors.teal),
                      const SizedBox(width: 10),
                      Expanded(
                        child: Text(
                          context.l10n.text('searchHint'),
                          style: Theme.of(context).textTheme.bodyMedium?.copyWith(color: Colors.black54),
                        ),
                      ),
                      const Icon(Icons.tune, size: 19, color: Colors.black38),
                    ],
                  ),
                ),
              ),
            ),
          ],
        ),
      );
}

class _SectionTitle extends StatelessWidget {
  const _SectionTitle({required this.title, required this.action, required this.onTap});

  final String title;
  final String action;
  final VoidCallback onTap;

  @override
  Widget build(BuildContext context) => Row(
        children: [
          Expanded(
            child: Text(
              title,
              style: Theme.of(context).textTheme.titleLarge?.copyWith(fontWeight: FontWeight.w800),
            ),
          ),
          TextButton(onPressed: onTap, child: Text(action)),
        ],
      );
}

class _SystemCard extends StatelessWidget {
  const _SystemCard({required this.system});

  final BodySystem system;

  @override
  Widget build(BuildContext context) => Card(
        child: InkWell(
          onTap: () => context.push('/system/${system.id}'),
          borderRadius: BorderRadius.circular(22),
          child: Padding(
            padding: const EdgeInsets.all(12),
            child: Row(
              children: [
                Container(
                  width: 40,
                  height: 40,
                  decoration: BoxDecoration(
                    color: system.color.withValues(alpha: 0.12),
                    borderRadius: BorderRadius.circular(13),
                  ),
                  child: Icon(system.icon, size: 22, color: system.color),
                ),
                const SizedBox(width: 10),
                Expanded(
                  child: Text(
                    system.localized(context.l10n.isArabic),
                    maxLines: 2,
                    overflow: TextOverflow.ellipsis,
                    style: Theme.of(context).textTheme.labelMedium?.copyWith(fontWeight: FontWeight.w800),
                  ),
                ),
              ],
            ),
          ),
        ),
      );
}
