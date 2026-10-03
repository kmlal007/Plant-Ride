/** Decorative plant skyline with a shuttle route, used on the login screen. */
export function PlantIllustration() {
  return (
    <svg className="login-illustration" viewBox="0 0 560 230" role="img" aria-label="Plant with shuttle route">
      <defs>
        <linearGradient id="ill-route" x1="0" x2="1">
          <stop offset="0" stopColor="#f26b1d" />
          <stop offset="1" stopColor="#ffa94d" />
        </linearGradient>
      </defs>
      {/* skyline */}
      <g fill="#ffffff" opacity="0.08">
        <rect x="20" y="120" width="70" height="80" rx="4" />
        <rect x="40" y="70" width="16" height="50" />
        <rect x="64" y="90" width="12" height="30" />
        <rect x="110" y="95" width="110" height="105" rx="4" />
        <path d="M120 95 l25 -30 l25 30 l25 -30 l25 30 Z" />
        <rect x="245" y="40" width="22" height="160" />
        <rect x="275" y="60" width="18" height="140" />
        <rect x="310" y="110" width="120" height="90" rx="4" />
        <circle cx="470" cy="140" r="38" />
        <rect x="455" y="140" width="30" height="60" />
        <rect x="500" y="125" width="44" height="75" rx="4" />
      </g>
      {/* smoke */}
      <g fill="#ffffff" opacity="0.06">
        <circle cx="256" cy="28" r="12" />
        <circle cx="270" cy="16" r="9" />
        <circle cx="284" cy="46" r="10" />
      </g>
      {/* ground + route */}
      <rect x="0" y="200" width="560" height="2" fill="#ffffff" opacity="0.15" />
      <path d="M30 186 C 140 186, 160 150, 260 160 S 420 196, 530 170" fill="none" stroke="url(#ill-route)"
        strokeWidth="6" strokeLinecap="round" strokeDasharray="1 0" />
      {[
        [30, 186],
        [260, 160],
        [530, 170],
      ].map(([x, y]) => (
        <g key={x}>
          <circle cx={x} cy={y} r="10" fill="#ffffff" />
          <circle cx={x} cy={y} r="4.5" fill="#0b1f33" />
        </g>
      ))}
      {/* shuttle */}
      <g transform="translate(355 160)">
        <rect x="0" y="0" width="58" height="26" rx="7" fill="#ffffff" />
        <rect x="6" y="5" width="12" height="9" rx="2" fill="#0e2a47" />
        <rect x="22" y="5" width="12" height="9" rx="2" fill="#0e2a47" />
        <rect x="38" y="5" width="14" height="9" rx="2" fill="#0e2a47" />
        <rect x="0" y="17" width="58" height="3" fill="#f26b1d" />
        <circle cx="14" cy="27" r="5" fill="#0b1f33" />
        <circle cx="44" cy="27" r="5" fill="#0b1f33" />
      </g>
    </svg>
  );
}
