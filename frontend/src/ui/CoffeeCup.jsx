/**
 * The tracker's hero: a cup that fills as the order moves through the bar.
 * level: 0..1
 */
export default function CoffeeCup({ level = 0, steaming = false }) {
  const top = 70, bottom = 220, height = bottom - top
  const y = top + (1 - Math.max(0, Math.min(1, level))) * height
  return (
    <svg viewBox="0 0 260 260" className="mx-auto h-56 w-56" role="img" aria-label={`Order progress ${Math.round(level * 100)} percent`}>
      <defs>
        <clipPath id="cup-inside">
          <path d="M58 70 H182 V180 a40 40 0 0 1 -40 40 H98 a40 40 0 0 1 -40 -40 Z" />
        </clipPath>
      </defs>

      {steaming && (
        <g stroke="#6E655E" strokeWidth="4" strokeLinecap="round" fill="none">
          <path className="anim-steam" d="M95 50 c-8 -10 8 -16 0 -28" />
          <path className="anim-steam" style={{ animationDelay: '.6s' }} d="M120 52 c-8 -10 8 -16 0 -28" />
          <path className="anim-steam" style={{ animationDelay: '1.2s' }} d="M145 50 c-8 -10 8 -16 0 -28" />
        </g>
      )}

      <g clipPath="url(#cup-inside)">
        <rect x="40" y="60" width="180" height="180" fill="#FBF0DA" />
        <g style={{ transform: `translateY(${y - top}px)`, transition: 'transform 1.2s cubic-bezier(.2,.8,.2,1)' }}>
          <g className="anim-wave">
            <path d="M40 72 q20 -9 40 0 t40 0 t40 0 t40 0 t40 0 t40 0 t40 0 V260 H40 Z" fill="#4A3428" transform="translate(0 -2)" />
          </g>
          <rect x="40" y="76" width="360" height="200" fill="#4A3428" />
          <path d="M40 72 q20 -9 40 0 t40 0 t40 0 t40 0 t40 0 t40 0 t40 0" fill="none" stroke="#E8B45A" strokeWidth="5" opacity=".9" className="anim-wave" />
        </g>
      </g>

      <path d="M58 70 H182 V180 a40 40 0 0 1 -40 40 H98 a40 40 0 0 1 -40 -40 Z" fill="none" stroke="#2A1A12" strokeWidth="7" strokeLinejoin="round" />
      <path d="M182 100 h14 a26 26 0 0 1 0 52 h-14" fill="none" stroke="#2A1A12" strokeWidth="7" />
      <rect x="40" y="228" width="160" height="8" rx="4" fill="#2A1A12" />
    </svg>
  )
}
