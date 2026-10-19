export type Language = 'en' | 'ar';
export type AppTab = 'home' | 'catalog' | 'scan' | 'assistant' | 'data';

export interface BodySystem {
  id: string;
  name: string;
  nameAr: string;
  shortName: string;
  shortNameAr: string;
  color: string;
  tint: string;
  icon: string;
  order: number;
}

export interface SourceReference {
  label: string;
  url: string;
  authority: 'NLM' | 'FDA' | 'WHO' | 'EMA' | 'Local' | 'Other';
  accessedAt?: string;
}

export interface Drug {
  id: string;
  genericName: string;
  arabicName: string;
  systemId: string;
  pharmacologyClass: string;
  pharmacologyClassAr: string;
  subclass?: string;
  subclassAr?: string;
  aliases: string[];
  description: string;
  descriptionAr: string;
  mechanism: string;
  mechanismAr: string;
  indications: string[];
  indicationsAr: string[];
  cautions: string[];
  cautionsAr: string[];
  interactions?: string[];
  dosageNote?: string;
  dosageNoteAr?: string;
  image?: string;
  searchTerms: string[];
  sourceRefs: SourceReference[];
  locallyAdded?: boolean;
  createdAt: string;
  updatedAt: string;
}

export interface Product {
  id: string;
  drugId: string;
  brandName: string;
  brandNameAr?: string;
  genericName: string;
  strength: string;
  dosageForm: string;
  dosageFormAr: string;
  manufacturer: string;
  country: string;
  localAgent: string;
  market: string;
  barcode?: string;
  registrationNumber?: string;
  image?: string;
  leafletUrl?: string;
  searchTerms: string[];
  verified: boolean;
  updatedAt: string;
}

export interface ImportRecord {
  id?: number;
  fileName: string;
  fileType: string;
  importedRows: number;
  skippedRows: number;
  importedAt: string;
  status: 'complete' | 'partial' | 'failed';
}

export interface AppMeta {
  key: string;
  value: unknown;
}

export interface CatalogStats {
  drugCount: number;
  productCount: number;
  localProductCount: number;
  favoriteCount: number;
}

export interface SearchResult {
  drug: Drug;
  matchedProduct?: Product;
  score: number;
  matchedOn: string;
}

export interface AssistantMessage {
  id: string;
  role: 'user' | 'assistant';
  text: string;
  timestamp: string;
  sources?: SourceReference[];
  relatedDrugId?: string;
}

export interface OnlineDrugResult {
  rxcui?: string;
  genericName: string;
  brandNames: string[];
  description?: string;
  indications?: string[];
  warnings?: string[];
  sourceRefs: SourceReference[];
}

export interface CommunityMessage {
  id: string;
  parentId?: string;
  deviceId: string;
  userId?: string;
  authorName: string;
  authorEmail?: string;
  body: string;
  messageType: 'question' | 'reply' | 'announcement' | 'support';
  isPublic: boolean;
  status: 'open' | 'answered' | 'closed' | 'hidden';
  createdAt: string;
  updatedAt: string;
}

export interface CloudProfile {
  id: string;
  email: string;
  displayName?: string;
  role: 'user' | 'support' | 'reviewer' | 'pharmacist_editor' | 'admin' | 'owner';
}

export interface CloudSyncStatus {
  configured: boolean;
  syncing: boolean;
  connected: boolean;
  lastSync?: string;
  error?: string;
}
