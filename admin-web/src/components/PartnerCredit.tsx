import { useState } from 'react';

const PARTNER_NAME = import.meta.env.VITE_PARTNER_NAME || 'SRM Eco Tech';
const PARTNER_LOGO = import.meta.env.VITE_PARTNER_LOGO || '/partner-logo.png';

/**
 * "by SRM Eco Tech" co-brand credit. Shows the partner's logo when brand/partner artwork has been
 * installed (see brand/partner/README.md), otherwise the partner name as text.
 */
export function PartnerCredit({ tone = 'dark', size = 'md' }: { tone?: 'dark' | 'light'; size?: 'sm' | 'md' }) {
  const [logoFailed, setLogoFailed] = useState(false);
  const height = size === 'sm' ? 22 : 30;
  return (
    <div className={`partner-credit ${tone} ${size}`}>
      <span className="partner-by">by</span>
      {logoFailed ? (
        <span className="partner-name">{PARTNER_NAME}</span>
      ) : (
        <img src={PARTNER_LOGO} alt={PARTNER_NAME} style={{ height }} onError={() => setLogoFailed(true)} />
      )}
    </div>
  );
}
