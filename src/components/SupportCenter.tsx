import { CheckCircle2, CloudOff, LoaderCircle, LockKeyhole, Mail, MessageCircle, Send, ShieldCheck, UserRound, X } from 'lucide-react';
import { useEffect, useMemo, useState, type FormEvent } from 'react';
import { useAph } from '../context/AphContext';
import { cloudConfigured, listCommunityMessages, sendCommunityMessage, subscribeToMessages, supabase } from '../services/cloud';
import type { CommunityMessage } from '../types';
import { Modal } from './ui';

export function SupportCenter({ onClose }: { onClose: () => void }) {
  const { language, showToast } = useAph();
  const [messages, setMessages] = useState<CommunityMessage[]>([]);
  const [loading, setLoading] = useState(cloudConfigured);
  const [sending, setSending] = useState(false);
  const [error, setError] = useState('');
  const [name, setName] = useState(() => localStorage.getItem('aph-support-name') || '');
  const [email, setEmail] = useState(() => localStorage.getItem('aph-support-email') || '');
  const [body, setBody] = useState('');

  const load = async () => {
    if (!cloudConfigured) return;
    try { setMessages(await listCommunityMessages()); setError(''); }
    catch (cause) { setError(cause instanceof Error ? cause.message : 'Could not load messages.'); }
    finally { setLoading(false); }
  };

  useEffect(() => {
    void load();
    const channel = subscribeToMessages(() => void load());
    return () => { if (channel && supabase) void supabase.removeChannel(channel); };
  }, []);

  const threads = useMemo(() => {
    const replies = new Map<string, CommunityMessage[]>();
    messages.filter((message) => message.parentId).forEach((message) => {
      const list = replies.get(message.parentId!) || [];
      list.push(message);
      replies.set(message.parentId!, list);
    });
    return messages.filter((message) => !message.parentId).map((message) => ({ message, replies: (replies.get(message.id) || []).sort((a, b) => a.createdAt.localeCompare(b.createdAt)) }));
  }, [messages]);

  const submit = async (event: FormEvent) => {
    event.preventDefault();
    if (body.trim().length < 2) return;
    setSending(true);
    try {
      await sendCommunityMessage({ body, authorName: name, authorEmail: email });
      localStorage.setItem('aph-support-name', name);
      localStorage.setItem('aph-support-email', email);
      setBody('');
      await load();
      showToast(language === 'ar' ? 'تم إرسال رسالتك للدكتور عبدالمجيد' : 'Your message was sent to Dr. Abdulmajeed');
    } catch (cause) { setError(cause instanceof Error ? cause.message : 'Could not send message.'); }
    finally { setSending(false); }
  };

  return (
    <Modal onClose={onClose} className="support-modal" labelledBy="support-center-title">
      <header className="support-header">
        <span><MessageCircle size={22} /></span>
        <div><small>APH COMMUNITY & SUPPORT</small><h2 id="support-center-title">{language === 'ar' ? 'الرسائل والدعم' : 'Messages & support'}</h2><p>{language === 'ar' ? 'راسل الدكتور عبدالمجيد وتابع الردود والتحديثات.' : 'Message Dr. Abdulmajeed and follow replies and updates.'}</p></div>
        <button onClick={onClose}><X size={20} /></button>
      </header>

      {!cloudConfigured ? (
        <div className="support-unavailable">
          <span><CloudOff size={30} /></span>
          <h3>{language === 'ar' ? 'المراسلة السحابية بانتظار ربط Supabase' : 'Cloud messaging awaits Supabase setup'}</h3>
          <p>{language === 'ar' ? 'يمكنك التواصل الآن عبر واتساب أو البريد حتى يتم نشر قاعدة البيانات.' : 'Use WhatsApp or email until the production database is deployed.'}</p>
          <div><a href="https://wa.me/967781733795" target="_blank" rel="noreferrer"><MessageCircle size={16} /> WhatsApp</a><a href="mailto:7a8b1d@gmail.com"><Mail size={16} /> Email</a></div>
        </div>
      ) : (
        <div className="support-body">
          <form className="support-compose" onSubmit={submit}>
            <div className="support-compose__badge"><ShieldCheck size={17} /><span>{language === 'ar' ? 'تظهر الرسالة والرد للعامة؛ بريدك لا يظهر.' : 'The question and reply are public; your email stays private.'}</span></div>
            <div className="support-form-row"><label><span>{language === 'ar' ? 'الاسم' : 'Name'}</span><input value={name} onChange={(event) => setName(event.target.value)} maxLength={80} placeholder={language === 'ar' ? 'اسمك' : 'Your name'} /></label><label><span>{language === 'ar' ? 'البريد (اختياري)' : 'Email (optional)'}</span><input value={email} onChange={(event) => setEmail(event.target.value)} type="email" maxLength={180} placeholder="name@example.com" /></label></div>
            <label><span>{language === 'ar' ? 'رسالتك أو سؤالك' : 'Your message or question'}</span><textarea value={body} onChange={(event) => setBody(event.target.value)} maxLength={4000} rows={3} placeholder={language === 'ar' ? 'اكتب سؤالك هنا…' : 'Write your question here…'} /></label>
            <button className="primary-button" disabled={sending || body.trim().length < 2}>{sending ? <LoaderCircle className="spin" size={17} /> : <Send size={17} />}{language === 'ar' ? 'إرسال' : 'Send message'}</button>
          </form>
          {error && <div className="support-error">{error}</div>}
          <div className="community-feed">
            <div className="community-feed__title"><div><strong>{language === 'ar' ? 'أسئلة المجتمع' : 'Community questions'}</strong><small>{threads.length} {language === 'ar' ? 'محادثة' : 'threads'}</small></div><span><i /> LIVE</span></div>
            {loading ? <div className="support-loading"><LoaderCircle className="spin" size={22} /></div> : threads.map(({ message, replies }) => (
              <article className="community-thread" key={message.id}>
                <div className="community-message"><span className="community-avatar"><UserRound size={16} /></span><div><header><strong>{message.authorName}</strong><time>{new Date(message.createdAt).toLocaleDateString(language === 'ar' ? 'ar-YE' : 'en-US')}</time></header><p>{message.body}</p></div></div>
                {replies.map((reply) => <div className="community-reply" key={reply.id}><span><CheckCircle2 size={15} /></span><div><header><strong>{reply.authorName}</strong><em>APH</em></header><p>{reply.body}</p></div></div>)}
              </article>
            ))}
            {!loading && !threads.length && <div className="community-empty"><MessageCircle size={25} /><p>{language === 'ar' ? 'كن أول من يرسل سؤالاً.' : 'Be the first to send a question.'}</p></div>}
          </div>
          <div className="support-privacy"><LockKeyhole size={14} />{language === 'ar' ? 'لا ترسل بيانات شخصية أو تفاصيل مريض يمكن التعرف عليه.' : 'Do not send personal or identifiable patient information.'}</div>
        </div>
      )}
    </Modal>
  );
}
