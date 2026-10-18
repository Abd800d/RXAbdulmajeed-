import {
  ArrowLeft, BarChart3, Bell, CheckCircle2, Cloud, CloudOff, Database, FileUp, Inbox,
  KeyRound, LoaderCircle, LockKeyhole, LogIn, LogOut, Mail, MessageCircle, PackagePlus,
  Pill, Plus, RefreshCw, Search, Send, Server, ShieldCheck, UploadCloud, UserPlus,
} from 'lucide-react';
import { useEffect, useMemo, useState, type FormEvent } from 'react';
import { MedicineEditor, ProductEditor } from '../components/Editors';
import { DrugDetail } from '../components/DrugDetail';
import { AphLogo, LoadingScreen, MedicineVisual } from '../components/ui';
import { useAph } from '../context/AphContext';
import {
  cloudConfigured, cloudUnpublishDrug, cloudUnpublishProduct, cloudUpsertDrug, cloudUpsertProduct, getCloudProfile, getCloudSession,
  getCloudStats, hydrateDrugFromCloud, listAdminMessages, publishLocalCatalog, replyToMessage, searchCloudCatalog, sendAdminOtp,
  signInAdmin, signOutCloud, signUpAdmin, subscribeToMessages, supabase, verifyAdminOtp,
} from '../services/cloud';
import type { CloudProfile, CommunityMessage, Drug, Product } from '../types';
import { DataScreen } from '../screens/DataScreen';
import { formatCount } from '../utils/text';

type AdminTab = 'overview' | 'catalog' | 'imports' | 'inbox';
type AuthMode = 'password' | 'otp' | 'signup';

export function AdminPortal() {
  const { ready, language, setLanguage, drugs, products, stats, refresh, syncNow, cloud, showToast } = useAph();
  const [checkingAuth, setCheckingAuth] = useState(cloudConfigured);
  const [profile, setProfile] = useState<CloudProfile | null>(null);
  const [authMode, setAuthMode] = useState<AuthMode>('password');
  const [email, setEmail] = useState('7a8b1d@gmail.com');
  const [password, setPassword] = useState('');
  const [displayName, setDisplayName] = useState('Dr. Abdulmajeed Almakhedhi');
  const [authMessage, setAuthMessage] = useState('');
  const [otpSent, setOtpSent] = useState(false);
  const [otpCode, setOtpCode] = useState('');
  const [authError, setAuthError] = useState('');
  const [authBusy, setAuthBusy] = useState(false);
  const [tab, setTab] = useState<AdminTab>('overview');
  const [cloudStats, setCloudStats] = useState({ drugs: 0, products: 0, messages: 0 });
  const [query, setQuery] = useState('');
  const [cloudMatches, setCloudMatches] = useState<Drug[]>([]);
  const [catalogSearching, setCatalogSearching] = useState(false);
  const [viewingDrug, setViewingDrug] = useState<Drug>();
  const [editingDrug, setEditingDrug] = useState<Drug | 'new'>();
  const [editingProduct, setEditingProduct] = useState<{ drug: Drug; product?: Product }>();
  const [publishing, setPublishing] = useState<{ done: number; total: number }>();
  const [messages, setMessages] = useState<CommunityMessage[]>([]);
  const [selectedMessage, setSelectedMessage] = useState<CommunityMessage>();
  const [reply, setReply] = useState('');
  const [replying, setReplying] = useState(false);

  const loadProfile = async () => {
    if (!cloudConfigured) { setCheckingAuth(false); return; }
    try {
      const session = await getCloudSession();
      setProfile(session ? await getCloudProfile() : null);
    } catch (cause) {
      setProfile(null);
      setAuthError(cause instanceof Error ? cause.message : 'Could not verify the administrator session.');
    } finally { setCheckingAuth(false); }
  };

  const loadAdminData = async () => {
    if (!profile || !['admin', 'editor'].includes(profile.role)) return;
    try {
      const [nextStats, nextMessages] = await Promise.all([getCloudStats(), listAdminMessages(250)]);
      setCloudStats(nextStats);
      setMessages(nextMessages);
    } catch (cause) {
      showToast(cause instanceof Error ? cause.message : 'Could not refresh administrator data.', 'error');
    }
  };

  useEffect(() => {
    void loadProfile();
    if (!supabase) return undefined;
    const { data } = supabase.auth.onAuthStateChange(() => window.setTimeout(() => void loadProfile(), 0));
    return () => data.subscription.unsubscribe();
  }, []);

  useEffect(() => { void loadAdminData(); }, [profile]);

  useEffect(() => {
    if (!profile) return undefined;
    const channel = subscribeToMessages(() => void loadAdminData());
    return () => { if (channel && supabase) void supabase.removeChannel(channel); };
  }, [profile]);

  const authenticate = async (event: FormEvent) => {
    event.preventDefault();
    setAuthBusy(true); setAuthError(''); setAuthMessage('');
    try {
      if (authMode === 'password') await signInAdmin(email, password);
      if (authMode === 'otp') {
        if (otpSent) {
          await verifyAdminOtp(email, otpCode);
          setAuthMessage(language === 'ar' ? 'تم التحقق من رمز الدخول.' : 'Sign-in code verified.');
        } else {
          await sendAdminOtp(email);
          setOtpSent(true);
          setAuthMessage(language === 'ar' ? 'أرسلنا رمزاً أو رابط دخول آمناً إلى بريدك.' : 'A secure email code or sign-in link was sent.');
        }
      }
      if (authMode === 'signup') {
        const result = await signUpAdmin(email, password, displayName);
        setAuthMessage(result.session
          ? (language === 'ar' ? 'تم إنشاء الحساب. يجب أن يمنحه مدير حالي صلاحية الإدارة.' : 'Account created. An existing administrator must grant its role.')
          : (language === 'ar' ? 'تحقق من بريدك لتأكيد الحساب ثم اطلب صلاحية الإدارة.' : 'Confirm your email, then ask an administrator to grant access.'));
      }
      await loadProfile();
    } catch (cause) { setAuthError(cause instanceof Error ? cause.message : 'Authentication failed.'); }
    finally { setAuthBusy(false); }
  };

  useEffect(() => {
    if (!profile || query.trim().length < 2) {
      setCloudMatches([]);
      setCatalogSearching(false);
      return undefined;
    }
    let active = true;
    setCatalogSearching(true);
    const timer = window.setTimeout(async () => {
      try {
        const results = await searchCloudCatalog(query, 150);
        if (active) setCloudMatches([...new Map(results.map((result) => [result.drug.id, result.drug])).values()]);
      } catch { /* The bounded offline catalog remains searchable. */ }
      finally { if (active) setCatalogSearching(false); }
    }, 180);
    return () => { active = false; window.clearTimeout(timer); };
  }, [profile, query]);

  const visibleDrugs = useMemo(() => {
    const needle = query.trim().toLowerCase();
    if (!needle) return drugs.slice(0, 100);
    const local = drugs.filter((drug) => [drug.genericName, drug.arabicName, drug.pharmacologyClass, ...drug.aliases].some((value) => value.toLowerCase().includes(needle)));
    return [...new Map([...cloudMatches, ...local].map((drug) => [drug.id, drug])).values()].slice(0, 150);
  }, [cloudMatches, drugs, query]);

  const inspectDrug = async (drug: Drug) => {
    setViewingDrug(drug);
    try {
      const current = await hydrateDrugFromCloud(drug.id, 2000, true);
      if (current) setViewingDrug((open) => open?.id === drug.id ? current : open);
      await refresh();
    } catch (cause) {
      showToast(cause instanceof Error ? cause.message : 'Could not load cloud products.', 'error');
    }
  };

  const publishAll = async () => {
    setPublishing({ done: 0, total: drugs.length + products.length });
    try {
      await publishLocalCatalog(drugs, products, (done, total) => setPublishing({ done, total }));
      await syncNow();
      await loadAdminData();
      showToast(language === 'ar' ? 'تم نشر قاعدة البيانات للمستخدمين' : 'Catalog published to all users');
    } catch (cause) { showToast(cause instanceof Error ? cause.message : 'Publish failed', 'error'); }
    finally { setPublishing(undefined); }
  };

  const sendReply = async (event: FormEvent) => {
    event.preventDefault();
    if (!selectedMessage || reply.trim().length < 2) return;
    setReplying(true);
    try {
      await replyToMessage(selectedMessage.id, reply, true);
      setReply('');
      await loadAdminData();
      showToast(language === 'ar' ? 'تم نشر الرد' : 'Reply published');
    } catch (cause) { showToast(cause instanceof Error ? cause.message : 'Reply failed', 'error'); }
    finally { setReplying(false); }
  };

  if (!ready || checkingAuth) return <LoadingScreen />;

  if (!cloudConfigured) return (
    <div className="admin-setup-page">
      <div className="admin-setup-card">
        <AphLogo />
        <span className="admin-setup-icon"><CloudOff size={34} /></span>
        <small>PRODUCTION SETUP REQUIRED</small>
        <h1>{language === 'ar' ? 'اربط مشروع Supabase أولاً' : 'Connect a Supabase project first'}</h1>
        <p>{language === 'ar' ? 'تم تجهيز قاعدة PostgreSQL والسياسات والمزامنة والموقع الإداري، لكن لا يمكن إنشاء مشروع داخل حسابك دون تفويضك.' : 'The PostgreSQL schema, security policies, realtime sync, and admin site are ready, but a project cannot be created inside your account without authorization.'}</p>
        <ol>
          <li><span>1</span><div><strong>Create a Supabase project</strong><code>supabase db push</code></div></li>
          <li><span>2</span><div><strong>Add public client values to `.env.local`</strong><code>VITE_SUPABASE_URL · VITE_SUPABASE_ANON_KEY</code></div></li>
          <li><span>3</span><div><strong>Add fresh AI keys as Edge secrets</strong><code>supabase secrets set OPENAI_API_KEY=… GEMINI_API_KEY=…</code></div></li>
          <li><span>4</span><div><strong>Bootstrap the verified owner role</strong><code>See docs/DEPLOYMENT.md · no signup is auto-promoted</code></div></li>
        </ol>
        <div className="admin-security-note"><LockKeyhole size={17} /><span>{language === 'ar' ? 'لا تضع مفتاح OpenAI أو Google داخل VITE_ أو APK. المفاتيح المكشوفة سابقاً يجب إلغاؤها.' : 'Never put OpenAI or Google secrets in VITE_ variables or the APK. Previously exposed keys must be revoked.'}</span></div>
        <a className="primary-button" href="/"><ArrowLeft size={17} />{language === 'ar' ? 'العودة للتطبيق' : 'Back to app'}</a>
      </div>
    </div>
  );

  if (!profile) return (
    <div className="admin-login-page">
      <section className="admin-login-brand"><AphLogo inverse /><div><span>APH CONTROL CENTER</span><h1>{language === 'ar' ? 'مكتبتك الدوائية، تحت سيطرتك.' : 'Your medicine library, under control.'}</h1><p>{language === 'ar' ? 'حدّث دواءً أو منتجاً مرة واحدة ليظهر فوراً لدى جميع المستخدمين.' : 'Update a medicine or product once and publish it instantly to every user.'}</p></div><footer>© Dr. Abdulmajeed Abdullah Almakhedhi</footer></section>
      <section className="admin-login-form-wrap">
        <div className="admin-login-tools"><a href="/"><ArrowLeft size={16} />{language === 'ar' ? 'التطبيق' : 'App'}</a><button onClick={() => setLanguage(language === 'ar' ? 'en' : 'ar')}>{language === 'ar' ? 'EN' : 'ع'}</button></div>
        <form className="admin-login-form" onSubmit={authenticate}>
          <span className="admin-login-lock"><ShieldCheck size={24} /></span>
          <small>SECURE ADMIN ACCESS</small><h2>{language === 'ar' ? 'تسجيل دخول الإدارة' : 'Admin sign in'}</h2><p>{language === 'ar' ? 'الدخول محمي بواسطة Supabase Auth وسياسات قاعدة البيانات.' : 'Protected by Supabase Auth and row-level security.'}</p>
          <div className="admin-auth-modes"><button type="button" className={authMode === 'password' ? 'is-active' : ''} onClick={() => { setAuthMode('password'); setOtpSent(false); setOtpCode(''); }}><KeyRound size={15} />{language === 'ar' ? 'كلمة المرور' : 'Password'}</button><button type="button" className={authMode === 'otp' ? 'is-active' : ''} onClick={() => { setAuthMode('otp'); setOtpSent(false); setOtpCode(''); }}><Mail size={15} />OTP</button><button type="button" className={authMode === 'signup' ? 'is-active' : ''} onClick={() => { setAuthMode('signup'); setOtpSent(false); setOtpCode(''); }}><UserPlus size={15} />{language === 'ar' ? 'إنشاء' : 'Create'}</button></div>
          {authMode === 'signup' && <label><span>{language === 'ar' ? 'الاسم' : 'Display name'}</span><input value={displayName} onChange={(event) => setDisplayName(event.target.value)} required /></label>}
          <label><span>{language === 'ar' ? 'البريد الإلكتروني' : 'Email address'}</span><input type="email" value={email} onChange={(event) => { setEmail(event.target.value); setOtpSent(false); setOtpCode(''); }} required autoComplete="email" /></label>
          {authMode !== 'otp' && <label><span>{language === 'ar' ? 'كلمة المرور' : 'Password'}</span><input type="password" value={password} onChange={(event) => setPassword(event.target.value)} required minLength={8} autoComplete={authMode === 'signup' ? 'new-password' : 'current-password'} /></label>}
          {authMode === 'otp' && otpSent && <label><span>{language === 'ar' ? 'رمز البريد' : 'Email code'}</span><input value={otpCode} onChange={(event) => setOtpCode(event.target.value.replace(/\s/g, ''))} required minLength={6} inputMode="numeric" autoComplete="one-time-code" placeholder="123456" /></label>}
          {authError && <div className="admin-auth-error">{authError}</div>}{authMessage && <div className="admin-auth-success"><CheckCircle2 size={15} />{authMessage}</div>}
          <button className="primary-button admin-login-submit" disabled={authBusy}>{authBusy ? <LoaderCircle className="spin" size={17} /> : <LogIn size={17} />}{authMode === 'otp' ? (otpSent ? (language === 'ar' ? 'تحقق من الرمز' : 'Verify code') : (language === 'ar' ? 'إرسال رمز الدخول' : 'Send email code')) : (language === 'ar' ? 'متابعة' : 'Continue')}</button>
        </form>
      </section>
    </div>
  );

  if (!['admin', 'editor'].includes(profile.role)) return (
    <div className="admin-setup-page"><div className="admin-setup-card"><AphLogo /><span className="admin-setup-icon"><LockKeyhole size={32} /></span><h1>{language === 'ar' ? 'الحساب ليس مديراً' : 'Admin access pending'}</h1><p>{language === 'ar' ? 'يجب أن يمنح مدير موثوق هذا الحساب دور editor أو admin. لتهيئة أول مدير اتبع docs/DEPLOYMENT.md.' : 'A trusted administrator must grant this account the editor or admin role. For the first administrator, follow docs/DEPLOYMENT.md.'}<br />{profile.email}</p><button className="secondary-button" onClick={() => void signOutCloud()}><LogOut size={16} />{language === 'ar' ? 'تسجيل الخروج' : 'Sign out'}</button></div></div>
  );

  const nav = [
    { id: 'overview' as const, icon: BarChart3, label: language === 'ar' ? 'نظرة عامة' : 'Overview' },
    { id: 'catalog' as const, icon: Pill, label: language === 'ar' ? 'دليل الأدوية' : 'Catalog' },
    { id: 'imports' as const, icon: FileUp, label: language === 'ar' ? 'الاستيراد والنشر' : 'Import & publish' },
    { id: 'inbox' as const, icon: Inbox, label: language === 'ar' ? 'صندوق الرسائل' : 'Inbox', badge: messages.filter((message) => !message.parentId && message.status === 'open').length },
  ];

  return (
    <div className="admin-shell">
      <aside className="admin-sidebar">
        <AphLogo inverse />
        <div className="admin-workspace-label"><small>WORKSPACE</small><strong>APH Production</strong><span><i /> Supabase connected</span></div>
        <nav>{nav.map(({ id, icon: Icon, label, badge }) => <button key={id} className={tab === id ? 'is-active' : ''} onClick={() => setTab(id)}><Icon size={18} /><span>{label}</span>{badge ? <em>{badge}</em> : null}</button>)}</nav>
        <div className="admin-sidebar-footer"><a href="/"><ArrowLeft size={17} />{language === 'ar' ? 'فتح التطبيق' : 'Open mobile app'}</a><button onClick={async () => { await signOutCloud(); setProfile(null); }}><LogOut size={17} />{language === 'ar' ? 'خروج' : 'Sign out'}</button><div><span>AA</span><p><strong>{profile.displayName || 'Dr. Abdulmajeed'}</strong><small>{profile.email}</small></p></div></div>
      </aside>
      <main className="admin-main">
        <header className="admin-topbar"><div><button className="admin-mobile-menu"><Database size={18} /></button><span>{nav.find((item) => item.id === tab)?.label}</span></div><div><button onClick={() => setLanguage(language === 'ar' ? 'en' : 'ar')}>{language === 'ar' ? 'EN' : 'ع'}</button><button onClick={() => void syncNow()} className={cloud.syncing ? 'spin-child' : ''}><RefreshCw size={17} /></button><span className="admin-live"><i /> LIVE</span></div></header>
        <div className="admin-content">
          {tab === 'overview' && <>
            <div className="admin-page-title"><div><small>APH · PRODUCTION</small><h1>{language === 'ar' ? 'مرحباً د. عبدالمجيد' : 'Welcome, Dr. Abdulmajeed'}</h1><p>{language === 'ar' ? 'تابع قاعدة البيانات والمزامنة والرسائل من مكان واحد.' : 'Monitor your catalog, synchronization, and messages from one place.'}</p></div><button className="primary-button" onClick={() => setEditingDrug('new')}><Plus size={17} />{language === 'ar' ? 'إضافة دواء' : 'Add medicine'}</button></div>
            <div className="admin-stat-grid"><article><span><Pill size={21} /></span><div><small>{language === 'ar' ? 'الأدوية السحابية' : 'Cloud medicines'}</small><strong>{formatCount(cloudStats.drugs, language)}</strong><em>{stats.drugCount} cached offline</em></div></article><article><span><PackagePlus size={21} /></span><div><small>{language === 'ar' ? 'المنتجات التجارية' : 'Trade products'}</small><strong>{formatCount(cloudStats.products, language)}</strong><em>{products.length} on this device</em></div></article><article><span><MessageCircle size={21} /></span><div><small>{language === 'ar' ? 'الرسائل' : 'Messages'}</small><strong>{formatCount(cloudStats.messages, language)}</strong><em>{messages.filter((m) => m.status === 'open').length} open</em></div></article><article><span><Cloud size={21} /></span><div><small>{language === 'ar' ? 'حالة المزامنة' : 'Sync status'}</small><strong className="admin-status-word">{cloud.connected ? 'Online' : 'Cached'}</strong><em>{cloud.lastSync ? new Date(cloud.lastSync).toLocaleString() : 'Ready'}</em></div></article></div>
            <div className="admin-overview-grid"><section className="admin-panel"><header><div><small>RECENT CATALOG</small><h2>{language === 'ar' ? 'أحدث الأدوية' : 'Recent medicines'}</h2></div><button onClick={() => setTab('catalog')}>{language === 'ar' ? 'عرض الكل' : 'View all'}</button></header><div className="admin-recent-list">{drugs.slice(0, 6).map((drug) => <button key={drug.id} onClick={() => setEditingDrug(drug)}><MedicineVisual drug={drug} size="small" /><span><strong>{drug.genericName}</strong><small>{drug.pharmacologyClass}</small></span><em>{new Date(drug.updatedAt).toLocaleDateString()}</em></button>)}</div></section><section className="admin-panel"><header><div><small>LIVE INBOX</small><h2>{language === 'ar' ? 'آخر الرسائل' : 'Latest messages'}</h2></div><button onClick={() => setTab('inbox')}>{language === 'ar' ? 'فتح الصندوق' : 'Open inbox'}</button></header><div className="admin-message-preview">{messages.filter((message) => !message.parentId).slice(0, 5).map((message) => <button key={message.id} onClick={() => { setSelectedMessage(message); setTab('inbox'); }}><span>{message.authorName.slice(0, 1).toUpperCase()}</span><div><strong>{message.authorName}</strong><p>{message.body}</p></div><i className={message.status === 'open' ? 'is-open' : ''} /></button>)}</div></section></div>
          </>}

          {tab === 'catalog' && <>
            <div className="admin-page-title"><div><small>CATALOG MANAGEMENT</small><h1>{language === 'ar' ? 'الأدوية والمنتجات' : 'Medicines & products'}</h1><p>{language === 'ar' ? 'كل تعديل منشور يصل للمستخدمين عبر Realtime.' : 'Published changes reach users through Realtime.'}</p></div><button className="primary-button" onClick={() => setEditingDrug('new')}><Plus size={17} />{language === 'ar' ? 'إضافة دواء' : 'Add medicine'}</button></div>
            <div className="admin-catalog-toolbar"><div>{catalogSearching ? <LoaderCircle className="spin" size={18} /> : <Search size={18} />}<input value={query} onChange={(event) => setQuery(event.target.value)} placeholder={language === 'ar' ? 'ابحث بالاسم العلمي أو المجموعة…' : 'Search scientific name or class…'} /></div><span>{visibleDrugs.length} / {stats.drugCount}</span></div>
            <div className="admin-catalog-table"><div className="admin-table-head"><span>{language === 'ar' ? 'الدواء' : 'Medicine'}</span><span>{language === 'ar' ? 'المجموعة' : 'Class'}</span><span>{language === 'ar' ? 'المنتجات' : 'Products'}</span><span>{language === 'ar' ? 'التحديث' : 'Updated'}</span><span /></div>{visibleDrugs.map((drug) => { const count = products.filter((product) => product.drugId === drug.id).length; return <div key={drug.id}><span><MedicineVisual drug={drug} size="small" /><b><strong>{drug.genericName}</strong><small>{drug.arabicName}</small></b></span><span>{drug.pharmacologyClass}</span><span><button className="admin-products-open" onClick={() => void inspectDrug(drug)}>{count} {language === 'ar' ? 'عرض' : 'view'}</button><button onClick={() => setEditingProduct({ drug })} aria-label={language === 'ar' ? 'إضافة منتج' : 'Add product'}><PackagePlus size={14} /></button></span><span>{new Date(drug.updatedAt).toLocaleDateString()}</span><span><button onClick={() => setEditingDrug(drug)}>{language === 'ar' ? 'تعديل' : 'Edit'}</button></span></div>; })}</div>
          </>}

          {tab === 'imports' && <>
            <div className="admin-publish-banner"><span><UploadCloud size={27} /></span><div><small>GLOBAL PUBLISH</small><h2>{language === 'ar' ? 'انشر البيانات المحلية لجميع المستخدمين' : 'Publish local data to every user'}</h2><p>{language === 'ar' ? 'يتم الرفع على دفعات مع إبقاء التطبيق متاحاً دون إنترنت.' : 'Records are uploaded in batches while offline access remains available.'}</p>{publishing && <div className="admin-publish-progress"><i style={{ width: `${Math.round((publishing.done / Math.max(publishing.total, 1)) * 100)}%` }} /><span>{publishing.done} / {publishing.total}</span></div>}</div><button className="primary-button" onClick={() => void publishAll()} disabled={!!publishing}>{publishing ? <LoaderCircle className="spin" size={17} /> : <Cloud size={17} />}{language === 'ar' ? 'نشر الآن' : 'Publish now'}</button></div>
            <div className="admin-cli-note"><Server size={22} /><div><strong>{language === 'ar' ? 'ملفات ضخمة أو أكثر من مليون سجل؟' : 'Very large or million-row dataset?'}</strong><p>{language === 'ar' ? 'استخدم أداة الخادم الموثوقة للاستيراد المتدفق والقابل للاستئناف ورفع الصور؛ استيراد المتصفح مناسب للملفات المتوسطة.' : 'Use the trusted resumable streaming CLI with image ingestion; browser import is intended for moderate files.'}</p><code>node scripts/import-to-supabase.mjs catalog.csv --publish --resume</code></div></div>
            <DataScreen onAddMedicine={() => setEditingDrug('new')} />
          </>}

          {tab === 'inbox' && <>
            <div className="admin-page-title"><div><small>REALTIME SUPPORT</small><h1>{language === 'ar' ? 'صندوق الرسائل' : 'Community inbox'}</h1><p>{language === 'ar' ? 'ردودك العامة تظهر فوراً في تطبيقات المستخدمين.' : 'Your public replies appear instantly in users’ apps.'}</p></div><span className="admin-inbox-count"><Bell size={16} />{messages.filter((message) => !message.parentId && message.status === 'open').length} open</span></div>
            <div className="admin-inbox-layout"><div className="admin-thread-list">{messages.filter((message) => !message.parentId).map((message) => <button key={message.id} className={selectedMessage?.id === message.id ? 'is-active' : ''} onClick={() => setSelectedMessage(message)}><span>{message.authorName.slice(0,1).toUpperCase()}</span><div><header><strong>{message.authorName}</strong><time>{new Date(message.createdAt).toLocaleDateString()}</time></header><p>{message.body}</p><small className={`status-${message.status}`}>{message.status}</small></div></button>)}</div><div className="admin-thread-detail">{selectedMessage ? <><header><span>{selectedMessage.authorName.slice(0,1).toUpperCase()}</span><div><strong>{selectedMessage.authorName}</strong><small>{selectedMessage.authorEmail || 'Email hidden / not provided'}</small></div></header><article><p>{selectedMessage.body}</p><time>{new Date(selectedMessage.createdAt).toLocaleString()}</time></article>{messages.filter((message) => message.parentId === selectedMessage.id).map((message) => <article className="is-reply" key={message.id}><strong>APH · {message.authorName}</strong><p>{message.body}</p><time>{new Date(message.createdAt).toLocaleString()}</time></article>)}<form onSubmit={sendReply}><textarea value={reply} onChange={(event) => setReply(event.target.value)} rows={4} placeholder={language === 'ar' ? 'اكتب رد الدكتور عبدالمجيد…' : 'Write Dr. Abdulmajeed’s reply…'} /><button className="primary-button" disabled={replying || reply.trim().length < 2}>{replying ? <LoaderCircle className="spin" size={16} /> : <Send size={16} />}{language === 'ar' ? 'نشر الرد' : 'Publish reply'}</button></form></> : <div className="admin-select-thread"><Inbox size={36} /><p>{language === 'ar' ? 'اختر رسالة للرد عليها' : 'Select a message to reply'}</p></div>}</div></div>
          </>}
        </div>
      </main>

      {viewingDrug && <DrugDetail drug={viewingDrug} onClose={() => setViewingDrug(undefined)} onEdit={(drug) => setEditingDrug(drug)} onAddProduct={(drug) => setEditingProduct({ drug })} onEditProduct={(drug, product) => setEditingProduct({ drug, product })} />}
      {editingDrug && <MedicineEditor drug={editingDrug === 'new' ? undefined : editingDrug} onClose={() => setEditingDrug(undefined)} onSaved={async (drug) => { const published = await cloudUpsertDrug(drug, true); setViewingDrug((open) => open?.id === published.id ? published : open); await refresh(); await loadAdminData(); }} onDeleted={async (drugId) => { await cloudUnpublishDrug(drugId); setViewingDrug((open) => open?.id === drugId ? undefined : open); await loadAdminData(); }} />}
      {editingProduct && <ProductEditor {...editingProduct} onClose={() => setEditingProduct(undefined)} onSaved={async (product) => { await cloudUpsertProduct(product, true); await refresh(); await loadAdminData(); }} onDeleted={async (productId) => { await cloudUnpublishProduct(productId); await loadAdminData(); }} />}
    </div>
  );
}
