import { useEffect, useId, useMemo, useRef, useState } from 'react'
import './RestaurantSearchSelect.css'

/**
 * @param {{
 *   options: Array<{ id: string, name: string }>
 *   value: string
 *   onChange: (id: string) => void
 *   disabled?: boolean
 *   required?: boolean
 *   placeholder?: string
 * }} props
 */
export default function RestaurantSearchSelect({
  options,
  value,
  onChange,
  disabled = false,
  required = false,
  placeholder = 'Выберите объект',
}) {
  const listId = useId()
  const rootRef = useRef(null)
  const inputRef = useRef(null)
  const [open, setOpen] = useState(false)
  const [query, setQuery] = useState('')

  const selected = useMemo(
    () => options.find((item) => item.id === value) || null,
    [options, value],
  )

  const filtered = useMemo(() => {
    const normalized = query.trim().toLowerCase()
    if (!normalized) return options
    return options.filter((item) => item.name.toLowerCase().includes(normalized))
  }, [options, query])

  useEffect(() => {
    const onDocClick = (event) => {
      if (rootRef.current && !rootRef.current.contains(event.target)) {
        setOpen(false)
        setQuery(selected?.name || '')
      }
    }
    document.addEventListener('mousedown', onDocClick)
    return () => document.removeEventListener('mousedown', onDocClick)
  }, [selected])

  useEffect(() => {
    setQuery(selected?.name || '')
  }, [selected])

  const handleFocus = () => {
    if (disabled) return
    setOpen(true)
    setQuery('')
  }

  const handleInputChange = (event) => {
    setQuery(event.target.value)
    setOpen(true)
    if (!event.target.value.trim() && value) {
      onChange('')
    }
  }

  const handleSelect = (item) => {
    onChange(item.id)
    setQuery(item.name)
    setOpen(false)
  }

  return (
    <div className="restaurant-search-select" ref={rootRef}>
      <input
        ref={inputRef}
        className="input"
        type="text"
        role="combobox"
        aria-expanded={open}
        aria-controls={listId}
        aria-autocomplete="list"
        value={query}
        onChange={handleInputChange}
        onFocus={handleFocus}
        placeholder={placeholder}
        disabled={disabled}
        required={required && !value}
        autoComplete="off"
      />
      {open && !disabled ? (
        <ul id={listId} className="restaurant-search-select__list" role="listbox">
          {filtered.length === 0 ? (
            <li className="restaurant-search-select__empty muted">Ничего не найдено</li>
          ) : (
            filtered.map((item) => (
              <li key={item.id}>
                <button
                  type="button"
                  role="option"
                  aria-selected={item.id === value}
                  className={
                    item.id === value
                      ? 'restaurant-search-select__option restaurant-search-select__option--active'
                      : 'restaurant-search-select__option'
                  }
                  onMouseDown={(event) => event.preventDefault()}
                  onClick={() => handleSelect(item)}
                >
                  {item.name}
                </button>
              </li>
            ))
          )}
        </ul>
      ) : null}
    </div>
  )
}
