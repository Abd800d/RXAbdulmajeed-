import 'package:flutter/material.dart';
import 'package:go_router/go_router.dart';

import '../core/localization/aph_localizations.dart';
import '../domain/body_system.dart';
import '../domain/catalog_models.dart';

class IngredientCard extends StatelessWidget {
  const IngredientCard({required this.ingredient, super.key});

  final ActiveIngredient ingredient;

  @override
  Widget build(BuildContext context) {
    final isArabic = context.l10n.isArabic;
    final system = bodySystemById(ingredient.systemId);
    return Card(
      child: InkWell(
        borderRadius: BorderRadius.circular(22),
        onTap: () => context.push('/ingredient/${Uri.encodeComponent(ingredient.id)}'),
        child: Padding(
          padding: const EdgeInsets.all(15),
          child: Row(
            children: [
              Container(
                width: 48,
                height: 48,
                decoration: BoxDecoration(
                  color: system.color.withValues(alpha: 0.12),
                  borderRadius: BorderRadius.circular(15),
                ),
                child: Icon(system.icon, color: system.color),
              ),
              const SizedBox(width: 13),
              Expanded(
                child: Column(
                  crossAxisAlignment: CrossAxisAlignment.start,
                  children: [
                    Text(
                      ingredient.name(isArabic),
                      maxLines: 1,
                      overflow: TextOverflow.ellipsis,
                      style: Theme.of(context).textTheme.titleSmall?.copyWith(fontWeight: FontWeight.w800),
                    ),
                    const SizedBox(height: 3),
                    Text(
                      ingredient.drugClass(isArabic),
                      maxLines: 1,
                      overflow: TextOverflow.ellipsis,
                      style: Theme.of(context).textTheme.bodySmall?.copyWith(
                            color: Theme.of(context).colorScheme.onSurfaceVariant,
                          ),
                    ),
                    const SizedBox(height: 6),
                    Row(
                      children: [
                        Icon(
                          ingredient.verificationStatus == 'verified'
                              ? Icons.verified_outlined
                              : Icons.info_outline,
                          size: 13,
                          color: system.color,
                        ),
                        const SizedBox(width: 4),
                        Text(
                          ingredient.verificationStatus == 'verified'
                              ? context.l10n.text('verified')
                              : context.l10n.text('unknown'),
                          style: Theme.of(context).textTheme.labelSmall?.copyWith(color: system.color),
                        ),
                      ],
                    ),
                  ],
                ),
              ),
              const Icon(Icons.chevron_right_rounded),
            ],
          ),
        ),
      ),
    );
  }
}
