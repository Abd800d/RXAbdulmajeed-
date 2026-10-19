import 'package:dio/dio.dart';

import '../../core/config/app_config.dart';
import '../../domain/catalog_models.dart';

class ApiNotConfiguredException implements Exception {
  const ApiNotConfiguredException();
}

class SyncChange {
  const SyncChange({
    required this.sequence,
    required this.entityType,
    required this.entityId,
    required this.operation,
    required this.entityVersion,
  });

  final int sequence;
  final String entityType;
  final String entityId;
  final String operation;
  final int? entityVersion;

  factory SyncChange.fromJson(Map<String, dynamic> json) => SyncChange(
        sequence: json['sequence'] as int,
        entityType: json['entity_type'] as String,
        entityId: json['entity_id'] as String,
        operation: json['operation'] as String,
        entityVersion: json['entity_version'] as int?,
      );
}

class SyncPage {
  const SyncPage({
    required this.nextSequence,
    required this.hasMore,
    required this.changes,
  });

  final int nextSequence;
  final bool hasMore;
  final List<SyncChange> changes;
}

class AssistantReply {
  const AssistantReply({
    required this.answer,
    required this.confidence,
    required this.disclaimer,
    required this.sources,
  });

  final String answer;
  final String confidence;
  final String disclaimer;
  final List<Map<String, dynamic>> sources;
}

class AphApiClient {
  AphApiClient({Dio? dio})
      : _dio = dio ??
            Dio(
              BaseOptions(
                baseUrl: AppConfig.apiBaseUrl,
                connectTimeout: const Duration(seconds: 12),
                receiveTimeout: const Duration(seconds: 20),
                sendTimeout: const Duration(seconds: 20),
                headers: const {'Accept': 'application/json'},
              ),
            );

  final Dio _dio;

  void _ensureConfigured() {
    if (!AppConfig.cloudConfigured) throw const ApiNotConfiguredException();
  }

  Future<List<ActiveIngredient>> search(String query, {required String language}) async {
    _ensureConfigured();
    final response = await _dio.get<Map<String, dynamic>>(
      '/v1/catalog/search',
      queryParameters: {'q': query, 'lang': language, 'limit': 50},
    );
    final rows = response.data?['results'] as List<dynamic>? ?? const [];
    return rows
        .whereType<Map>()
        .map((row) => row['ingredient'])
        .whereType<Map>()
        .map((item) => ActiveIngredient.fromJson(Map<String, dynamic>.from(item)))
        .toList(growable: false);
  }

  Future<ActiveIngredient> ingredient(String id) async {
    _ensureConfigured();
    final response = await _dio.get<Map<String, dynamic>>('/v1/catalog/ingredients/$id');
    return ActiveIngredient.fromJson(response.data ?? const {});
  }

  Future<BrandProduct> product(String id) async {
    _ensureConfigured();
    final response = await _dio.get<Map<String, dynamic>>('/v1/catalog/products/$id');
    return BrandProduct.fromJson(response.data ?? const {});
  }

  Future<AssistantReply> askAssistant(
    String question, {
    required String language,
    String? ingredientId,
  }) async {
    _ensureConfigured();
    final response = await _dio.post<Map<String, dynamic>>(
      '/v1/assistant/answer',
      data: {
        'question': question,
        'language': language,
        if (ingredientId != null) 'related_ingredient_id': ingredientId,
      },
    );
    final data = response.data ?? const <String, dynamic>{};
    return AssistantReply(
      answer: data['answer']?.toString() ?? '',
      confidence: data['confidence']?.toString() ?? 'low',
      disclaimer: data['disclaimer']?.toString() ?? '',
      sources: (data['sources'] as List<dynamic>? ?? const [])
          .whereType<Map>()
          .map((source) => Map<String, dynamic>.from(source))
          .toList(growable: false),
    );
  }

  Future<SyncPage> changes({required int after, int limit = 500}) async {
    _ensureConfigured();
    final response = await _dio.get<Map<String, dynamic>>(
      '/v1/sync/changes',
      queryParameters: {'after': after, 'limit': limit},
    );
    final data = response.data ?? const <String, dynamic>{};
    final changes = (data['changes'] as List<dynamic>? ?? const [])
        .whereType<Map>()
        .map((item) => SyncChange.fromJson(Map<String, dynamic>.from(item)))
        .toList(growable: false);
    return SyncPage(
      nextSequence: data['next_sequence'] as int? ?? after,
      hasMore: data['has_more'] as bool? ?? false,
      changes: changes,
    );
  }
}
