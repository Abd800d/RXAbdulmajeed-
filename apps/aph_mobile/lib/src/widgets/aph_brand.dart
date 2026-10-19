import 'package:flutter/material.dart';

import '../core/theme/aph_theme.dart';

class AphBrand extends StatelessWidget {
  const AphBrand({this.compact = false, this.inverse = false, super.key});

  final bool compact;
  final bool inverse;

  @override
  Widget build(BuildContext context) {
    final foreground = inverse ? Colors.white : AphColors.navy;
    return Semantics(
      label: 'APH AbdulmajeedPharma',
      image: true,
      child: Row(
        mainAxisSize: MainAxisSize.min,
        children: [
          Container(
            width: compact ? 34 : 42,
            height: compact ? 34 : 42,
            decoration: BoxDecoration(
              color: inverse ? Colors.white.withValues(alpha: 0.14) : AphColors.teal,
              borderRadius: BorderRadius.circular(13),
            ),
            child: const Icon(Icons.local_pharmacy_rounded, color: Colors.white),
          ),
          if (!compact) ...[
            const SizedBox(width: 10),
            Column(
              crossAxisAlignment: CrossAxisAlignment.start,
              mainAxisSize: MainAxisSize.min,
              children: [
                Text(
                  'APH',
                  style: Theme.of(context).textTheme.titleLarge?.copyWith(
                        color: foreground,
                        fontWeight: FontWeight.w900,
                        letterSpacing: 1.2,
                        height: 1,
                      ),
                ),
                Text(
                  'ABDULMAJEEDPHARMA',
                  style: Theme.of(context).textTheme.labelSmall?.copyWith(
                        color: foreground.withValues(alpha: 0.66),
                        fontSize: 8,
                        letterSpacing: 0.8,
                      ),
                ),
              ],
            ),
          ],
        ],
      ),
    );
  }
}
