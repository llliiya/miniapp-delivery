import { useState } from 'react'
import { formatShortAddress } from '../utils/formatShortAddress.js'

/**
 * @param {{ address: string, structured?: object, className?: string }} props
 */
export default function ShortAddressText({ address, structured, className = '' }) {
  const [expanded, setExpanded] = useState(false)
  const full = (address || '').trim()
  if (!full) return null

  const short = formatShortAddress(full, structured)
  const showToggle = short && full !== short && full.length > short.length + 8

  return (
    <span className={className}>
      <span className="short-address__main">{short || full}</span>
      {showToggle ? (
        <>
          {expanded ? (
            <span className="short-address__full muted"> {full}</span>
          ) : null}
          <button
            type="button"
            className="short-address__toggle"
            onClick={() => setExpanded((v) => !v)}
          >
            {expanded ? 'Свернуть' : 'Подробнее'}
          </button>
        </>
      ) : null}
    </span>
  )
}
