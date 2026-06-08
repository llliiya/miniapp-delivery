import { useEffect, useId, useRef, useState } from 'react'
import { resolveAddressSelection, searchAddressSuggestions } from '../services/geocoding.js'
import './AddressSuggestInput.css'

/**
 * @param {{
 *   value: string
 *   onChange: (value: string) => void
 *   onResolved: (payload: { address: string, shortAddress: string, lat: number, lon: number }) => void
 *   onClearSelection?: () => void
 *   disabled?: boolean
 *   placeholder?: string
 * }} props
 */
export default function AddressSuggestInput({
  value,
  onChange,
  onResolved,
  onClearSelection,
  disabled = false,
  placeholder = 'Начните вводить адрес…',
}) {
  const listId = useId()
  const rootRef = useRef(null)
  const [suggestions, setSuggestions] = useState([])
  const [open, setOpen] = useState(false)
  const [busy, setBusy] = useState(false)
  const [error, setError] = useState('')
  const debounceRef = useRef(null)

  useEffect(() => {
    const onDocClick = (e) => {
      if (rootRef.current && !rootRef.current.contains(e.target)) {
        setOpen(false)
      }
    }
    document.addEventListener('mousedown', onDocClick)
    return () => document.removeEventListener('mousedown', onDocClick)
  }, [])

  const scheduleSearch = (text) => {
    if (debounceRef.current) clearTimeout(debounceRef.current)
    if (!text || text.trim().length < 2) {
      setSuggestions([])
      setOpen(false)
      return
    }
    debounceRef.current = setTimeout(async () => {
      setBusy(true)
      setError('')
      try {
        const items = await searchAddressSuggestions(text.trim())
        setSuggestions(items)
        setOpen(items.length > 0)
      } catch {
        setSuggestions([])
        setOpen(false)
        setError('Не удалось найти адреса. Проверьте подключение.')
      } finally {
        setBusy(false)
      }
    }, 350)
  }

  const handleInput = (e) => {
    const next = e.target.value
    onChange(next)
    onClearSelection?.()
    scheduleSearch(next)
  }

  const handleSelect = async (item) => {
    setOpen(false)
    setSuggestions([])
    setBusy(true)
    setError('')
    try {
      const resolved = await resolveAddressSelection(item)
      if (!resolved) {
        setError('Не удалось определить координаты. Выберите другой вариант.')
        onChange('')
        onClearSelection?.()
        return
      }
      onChange(resolved.shortAddress || resolved.address)
      onResolved(resolved)
    } catch {
      setError('Ошибка геокодирования. Попробуйте ещё раз.')
      onClearSelection?.()
    } finally {
      setBusy(false)
    }
  }

  return (
    <div className="address-suggest" ref={rootRef}>
      <input
        className="input"
        type="text"
        value={value}
        onChange={handleInput}
        onFocus={() => {
          if (suggestions.length > 0) setOpen(true)
        }}
        disabled={disabled}
        placeholder={placeholder}
        role="combobox"
        aria-expanded={open}
        aria-controls={listId}
        autoComplete="off"
      />
      {busy ? <p className="address-suggest__hint">Поиск адресов…</p> : null}
      {error ? <p className="address-suggest__error">{error}</p> : null}
      {open && suggestions.length > 0 ? (
        <ul className="address-suggest__list" id={listId} role="listbox">
          {suggestions.map((item) => (
            <li key={item.id}>
              <button
                type="button"
                className="address-suggest__option"
                role="option"
                onMouseDown={(e) => e.preventDefault()}
                onClick={() => void handleSelect(item)}
              >
                {item.label}
              </button>
            </li>
          ))}
        </ul>
      ) : null}
    </div>
  )
}
