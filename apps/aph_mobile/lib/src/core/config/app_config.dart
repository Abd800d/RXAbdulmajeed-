class AppConfig {
  const AppConfig._();

  static const apiBaseUrl = String.fromEnvironment('APH_API_BASE_URL');
  static const supabaseUrl = String.fromEnvironment('APH_SUPABASE_URL');
  static const supabaseAnonKey = String.fromEnvironment('APH_SUPABASE_ANON_KEY');
  static const appOwner = 'Dr. Abdulmajeed Abdullah Almakhedhi';
  static const supportEmail = '7a8b1d@gmail.com';

  static bool get cloudConfigured =>
      apiBaseUrl.startsWith('https://') || apiBaseUrl.startsWith('http://10.0.2.2:');

  /// Only the Supabase anon key is permitted here. Service-role and AI keys
  /// belong exclusively to the FastAPI/Edge server environment.
  static bool get authConfigured =>
      supabaseUrl.startsWith('https://') && supabaseAnonKey.isNotEmpty;
}
