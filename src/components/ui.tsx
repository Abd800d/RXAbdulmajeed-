import type { CSSProperties, ReactNode } from 'react';
import {
  Activity, Bean, Bone, Brain, CircleDot, Droplets, Ear, Eye, HeartPulse, Microscope,
  Orbit, PersonStanding, Ribbon, ScanFace, ShieldCheck, ShieldPlus, Siren, Wind,
} from 'lucide-react';
import type { BodySystem, Drug, Product } from '../types';
import { initials } from '../utils/text';

const iconMap = {
  Activity, Bean, Bone, Brain, CircleDot, Droplets, Ear, Eye, HeartPulse, Microscope,
  Orbit, Ribbon, ScanFace, ShieldCheck, ShieldPlus, Siren, VenusAndMars: PersonStanding, Wind,
};

export function AphLogo({ compact = false, inverse = false }: { compact?: boolean; inverse?: boolean }) {
  return (
    <div className={`aph-logo ${compact ? 'aph-logo--compact' : ''} ${inverse ? 'aph-logo--inverse' : ''}`} aria-label="APH AbdulmajeedPharma">
      <span className="aph-logo__mark" aria-hidden="true">
        <span className="aph-logo__cross aph-logo__cross--h" />
        <span className="aph-logo__cross aph-logo__cross--v" />
        <span className="aph-logo__capsule"><i /><b /></span>
      </span>
      {!compact && (
        <span className="aph-logo__copy">
          <strong>APH</strong>
          <small>ABDULMAJEED<span>PHARMA</span></small>
        </span>
      )}
    </div>
  );
}

export function SystemIcon({ system, size = 22 }: { system: BodySystem; size?: number }) {
  const Icon = iconMap[system.icon as keyof typeof iconMap] ?? Activity;
  return <Icon size={size} strokeWidth={1.8} aria-hidden="true" />;
}

export function SystemBadge({ system, label, compact = false }: { system: BodySystem; label: string; compact?: boolean }) {
  return (
    <span className={`system-badge ${compact ? 'system-badge--compact' : ''}`} style={{ '--badge-color': system.color, '--badge-tint': system.tint } as CSSProperties}>
      <SystemIcon system={system} size={compact ? 14 : 17} />
      {label}
    </span>
  );
}

const palette = [
  ['#0b5c63', '#dceeed'], ['#345f8b', '#e7eff7'], ['#945547', '#f7ece8'],
  ['#71578c', '#f0ebf5'], ['#81703b', '#f5f0df'], ['#3e7960', '#e8f3ed'],
];

const colorFor = (value: string) => {
  const total = [...value].reduce((sum, letter) => sum + letter.charCodeAt(0), 0);
  return palette[total % palette.length];
};

export function MedicineVisual({ drug, product, size = 'medium', className = '' }: { drug?: Drug; product?: Product; size?: 'small' | 'medium' | 'large'; className?: string }) {
  const title = product?.brandName || drug?.genericName || 'APH';
  const subtitle = product ? `${product.strength} · ${product.dosageForm}` : drug?.pharmacologyClass || 'Medicine';
  const image = product?.image || drug?.image;
  const [main, tint] = colorFor(title);

  if (image) {
    return (
      <div className={`medicine-visual medicine-visual--${size} medicine-visual--image ${className}`} style={{ '--pack-color': main, '--pack-tint': tint } as CSSProperties}>
        <img src={image} alt={title} loading="lazy" />
      </div>
    );
  }

  return (
    <div className={`medicine-visual medicine-visual--${size} ${className}`} style={{ '--pack-color': main, '--pack-tint': tint } as CSSProperties} aria-label={`${title} product illustration`}>
      <div className="medicine-visual__shadow" />
      <div className="medicine-visual__box">
        <span className="medicine-visual__aph">APH<span>+</span></span>
        <strong>{title}</strong>
        <small>{subtitle}</small>
        <i className="medicine-visual__stripe" />
        <span className="medicine-visual__initials">{initials(title)}</span>
      </div>
      <div className="medicine-visual__blister"><i /><i /><i /></div>
    </div>
  );
}

export function StatusDot({ online, label }: { online: boolean; label: string }) {
  return <span className={`status-pill ${online ? 'is-online' : 'is-offline'}`}><i />{label}</span>;
}

export function EmptyState({ icon, title, body, action }: { icon: ReactNode; title: string; body: string; action?: ReactNode }) {
  return (
    <div className="empty-state">
      <span className="empty-state__icon">{icon}</span>
      <h3>{title}</h3>
      <p>{body}</p>
      {action}
    </div>
  );
}

export function LoadingScreen() {
  return (
    <div className="splash-screen">
      <AphLogo inverse />
      <div className="splash-screen__pulse"><span /><span /><span /></div>
      <p>Clinical medicine library</p>
      <small>OFFLINE-FIRST · BILINGUAL</small>
    </div>
  );
}

export function Modal({ children, onClose, className = '', labelledBy }: { children: ReactNode; onClose: () => void; className?: string; labelledBy?: string }) {
  return (
    <div className="modal-layer" role="dialog" aria-modal="true" aria-labelledby={labelledBy}>
      <button className="modal-layer__scrim" onClick={onClose} aria-label="Close dialog" />
      <div className={`modal-sheet ${className}`}>{children}</div>
    </div>
  );
}
