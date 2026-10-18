import { ArrowRight, Bot, BookOpenCheck, ExternalLink, LoaderCircle, LockKeyhole, Send, ShieldCheck, Sparkles, Trash2, UserRound } from 'lucide-react';
import { useEffect, useRef, useState } from 'react';
import { useAph } from '../context/AphContext';
import { answerMedicationQuestion, createMessage } from '../services/assistant';
import type { AssistantMessage, Drug } from '../types';

interface AssistantScreenProps {
  onOpenDrug: (drug: Drug) => void;
}

const storedMessages = (): AssistantMessage[] => {
  try { return JSON.parse(localStorage.getItem('aph-assistant-history') || '[]') as AssistantMessage[]; }
  catch { return []; }
};

export function AssistantScreen({ onOpenDrug }: AssistantScreenProps) {
  const { t, language, drugs, online, cloud } = useAph();
  const [messages, setMessages] = useState<AssistantMessage[]>(storedMessages);
  const [query, setQuery] = useState('');
  const [thinking, setThinking] = useState(false);
  const endRef = useRef<HTMLDivElement>(null);

  useEffect(() => {
    localStorage.setItem('aph-assistant-history', JSON.stringify(messages.slice(-30)));
    endRef.current?.scrollIntoView({ behavior: 'smooth' });
  }, [messages]);

  const submit = async (text = query) => {
    const question = text.trim();
    if (!question || thinking) return;
    setMessages((current) => [...current, createMessage('user', question)]);
    setQuery('');
    setThinking(true);
    const answer = await answerMedicationQuestion(question, language);
    await new Promise((resolve) => setTimeout(resolve, 420));
    setMessages((current) => [...current, createMessage('assistant', answer?.text ?? t('assistantNoMatch'), {
      sources: answer?.sources,
      relatedDrugId: answer?.relatedDrugId,
    })]);
    setThinking(false);
  };

  const suggestions = [t('promptOne'), t('promptTwo'), t('promptThree')];

  return (
    <div className="screen assistant-screen">
      <div className="screen-title-row assistant-title-row">
        <div><span className="section-kicker">APH · EVIDENCE AI</span><h1>{t('assistantTitle')}</h1><p>{t('assistantSubtitle')}</p></div>
        <span className="evidence-badge"><BookOpenCheck size={16} /> {t('sourceBacked')}<i className={online ? 'is-online' : ''} /></span>
      </div>

      <div className="assistant-layout">
        <section className="chat-card">
          <header className="chat-card__header">
            <span className="assistant-orb"><Bot size={24} /></span>
            <div><strong>APH Intelligence</strong><small><i /> {online && cloud.configured ? (language === 'ar' ? 'ذكاء خادمي آمن + مصادر موثوقة' : 'Secure server AI + trusted sources') : t('localKnowledge')}</small></div>
            {messages.length > 0 && <button onClick={() => setMessages([])} aria-label="Clear chat"><Trash2 size={17} /></button>}
          </header>

          <div className="chat-messages" aria-live="polite">
            <div className="chat-welcome">
              <span><Sparkles size={21} /></span>
              <div><strong>APH</strong><p>{t('assistantWelcome')}</p></div>
            </div>
            {!messages.length && (
              <div className="chat-suggestions">
                <small>{t('suggested')}</small>
                {suggestions.map((suggestion) => <button key={suggestion} onClick={() => submit(suggestion)}>{suggestion}<ArrowRight size={15} /></button>)}
              </div>
            )}
            {messages.map((message) => (
              <article className={`chat-message chat-message--${message.role}`} key={message.id}>
                <span className="chat-message__avatar">{message.role === 'assistant' ? <Bot size={17} /> : <UserRound size={17} />}</span>
                <div className="chat-message__bubble">
                  <p>{message.text}</p>
                  {message.sources?.length ? (
                    <div className="chat-message__sources">
                      <small><BookOpenCheck size={13} />{t('sourceBacked')}</small>
                      {message.sources.slice(0, 3).map((source) => <a href={source.url} target="_blank" rel="noreferrer" key={source.url}><span>{source.authority}</span>{source.label}<ExternalLink size={12} /></a>)}
                    </div>
                  ) : null}
                  {message.relatedDrugId && (
                    <button className="chat-related" onClick={() => {
                      const drug = drugs.find((item) => item.id === message.relatedDrugId);
                      if (drug) onOpenDrug(drug);
                    }}>{language === 'ar' ? 'فتح ملف الدواء' : 'Open medicine profile'} <ArrowRight size={14} /></button>
                  )}
                  <time>{new Date(message.timestamp).toLocaleTimeString(language === 'ar' ? 'ar-YE' : 'en-US', { hour: '2-digit', minute: '2-digit' })}</time>
                </div>
              </article>
            ))}
            {thinking && <article className="chat-message chat-message--assistant"><span className="chat-message__avatar"><Bot size={17} /></span><div className="chat-message__bubble chat-message__bubble--thinking"><LoaderCircle className="spin" size={16} /><span>{t('thinking')}</span><i /><i /><i /></div></article>}
            <div ref={endRef} />
          </div>

          <footer className="chat-composer">
            <div>
              <textarea value={query} onChange={(event) => setQuery(event.target.value)} onKeyDown={(event) => {
                if (event.key === 'Enter' && !event.shiftKey) { event.preventDefault(); void submit(); }
              }} placeholder={t('assistantPlaceholder')} rows={1} />
              <button onClick={() => submit()} disabled={!query.trim() || thinking} aria-label={t('ask')}><Send size={18} /></button>
            </div>
            <small><LockKeyhole size={12} /> {language === 'ar' ? 'لا تُرسل بيانات المريض الشخصية' : 'Do not enter identifiable patient information'}</small>
          </footer>
        </section>

        <aside className="assistant-trust">
          <div className="assistant-trust__icon"><ShieldCheck size={25} /></div>
          <span className="section-kicker">SAFETY BY DESIGN</span>
          <h2>{language === 'ar' ? 'إجابة موثّقة، لا تخمين.' : 'Cited answers, not guesses.'}</h2>
          <p>{language === 'ar' ? 'يبحث المساعد أولاً في قاعدة APH المحلية ويربط كل إجابة بالمصادر المحفوظة.' : 'The assistant checks your local APH catalog first and links each answer to its saved primary sources.'}</p>
          <ul>
            <li><span>NLM</span><div><strong>RxNorm / DailyMed</strong><small>Drug names & official labels</small></div></li>
            <li><span>FDA</span><div><strong>openFDA</strong><small>Label safety information</small></div></li>
            <li><span>WHO</span><div><strong>WHO medicines</strong><small>Global policy & safety</small></div></li>
          </ul>
          <div className="assistant-trust__notice"><ShieldCheck size={16} /><span>{t('assistantDisclaimer')}</span></div>
        </aside>
      </div>
    </div>
  );
}
