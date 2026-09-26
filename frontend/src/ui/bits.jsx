import { Coffee, CupSoda, Leaf, Croissant, LoaderCircle } from 'lucide-react'

const GLYPH = {
  'Espresso bar': { Icon: Coffee, cls: 'bg-roast text-crema' },
  'Cold coffee': { Icon: CupSoda, cls: 'bg-leaf text-steam' },
  'Not coffee': { Icon: Leaf, cls: 'bg-crema-soft text-leaf' },
  'Bakery & bites': { Icon: Croissant, cls: 'bg-crema text-roast' },
}

export function ItemGlyph({ category, size = 'md' }) {
  const g = GLYPH[category] || GLYPH['Espresso bar']
  const dims = size === 'lg' ? 'h-14 w-14 rounded-2xl' : 'h-11 w-11 rounded-xl'
  return (
    <div className={`${dims} ${g.cls} grid shrink-0 place-items-center`} aria-hidden="true">
      <g.Icon size={size === 'lg' ? 26 : 20} strokeWidth={1.8} />
    </div>
  )
}

const TAG_LABEL = { vegan: 'Vegan', 'dairy-free': 'Dairy-free', 'low-sugar': 'Low sugar', 'caffeine-free': 'No caffeine' }

export function Tags({ tags = [] }) {
  const shown = tags.filter((t) => TAG_LABEL[t])
  if (!shown.length) return null
  return (
    <div className="mt-1.5 flex flex-wrap gap-1">
      {shown.map((t) => (
        <span key={t} className="rounded-full bg-leaf-soft px-2 py-0.5 text-[11px] font-medium text-leaf">{TAG_LABEL[t]}</span>
      ))}
    </div>
  )
}

export function Spinner({ className = '' }) {
  return <LoaderCircle className={`animate-spin ${className}`} size={18} aria-hidden="true" />
}

export function ErrorNote({ message, onRetry }) {
  if (!message) return null
  return (
    <div role="alert" className="rounded-xl border border-cherry/30 bg-cherry-soft px-4 py-3 text-sm text-cherry">
      {message}
      {onRetry && (
        <button onClick={onRetry} className="ml-2 font-semibold underline underline-offset-2">Try again</button>
      )}
    </div>
  )
}

export const PREFERENCES = [
  { id: 'vegan', label: 'Vegan' },
  { id: 'dairy-free', label: 'Dairy-free' },
  { id: 'low-sugar', label: 'Less sugar' },
  { id: 'caffeine-free', label: 'No caffeine' },
  { id: 'cold', label: 'Prefer cold' },
  { id: 'hot', label: 'Prefer hot' },
]
