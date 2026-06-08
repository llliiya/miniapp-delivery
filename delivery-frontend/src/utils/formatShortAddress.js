/**
 * @typedef {{ city?: string, street?: string, house?: string }} AddressParts
 */

function shortenStreet(street) {
  const s = (street || '').trim()
  if (!s) return ''
  return s
    .replace(/^улица\s+/i, 'ул. ')
    .replace(/^проспект\s+/i, 'пр. ')
    .replace(/^переулок\s+/i, 'пер. ')
}

/**
 * @param {AddressParts} parts
 * @returns {string}
 */
export function formatFromStructured(parts) {
  if (!parts) return ''
  const out = []
  if (parts.city?.trim()) out.push(parts.city.trim())
  if (parts.street?.trim()) out.push(shortenStreet(parts.street))
  if (parts.house?.trim()) out.push(parts.house.trim())
  return out.join(', ')
}

/**
 * @param {Array<{ kind?: string, name?: string }>} components
 * @returns {AddressParts}
 */
export function parseYandexAddressComponents(components) {
  if (!Array.isArray(components)) return {}
  const byKind = {}
  for (const c of components) {
    if (c?.kind && c?.name) byKind[c.kind] = c.name
  }
  return {
    city: byKind.locality || byKind.area || byKind.province || '',
    street: byKind.street || '',
    house: byKind.house || '',
  }
}

/**
 * @param {object} geoObject
 * @returns {AddressParts}
 */
export function parseYandexGeoObject(geoObject) {
  const components =
    geoObject?.metaDataProperty?.GeocoderMetaData?.Address?.Components || []
  return parseYandexAddressComponents(components)
}

/**
 * @param {object} nominatimRow
 * @returns {AddressParts}
 */
export function parseNominatimAddress(nominatimRow) {
  const a = nominatimRow?.address
  if (!a) return {}
  return {
    city: a.city || a.town || a.village || a.municipality || '',
    street: a.road || a.pedestrian || a.footway || '',
    house: a.house_number || '',
  }
}

function isNoisePart(part) {
  const p = part.trim().toLowerCase()
  if (!p) return true
  if (/^\d{6}$/.test(p)) return true
  if (p === 'россия' || p === 'russia') return true
  if (p.includes('федеральный округ')) return true
  if (p.includes('городской округ')) return true
  if (/^республика\s/.test(p)) return true
  if (/область$/.test(p) && p.length > 12) return true
  if (/район$/.test(p)) return true
  if (/^татарстан$/.test(p)) return true
  return false
}

function isStreetPart(part) {
  const t = part.trim()
  return (
    /^(ул\.|улица|пр\.|проспект|пер\.|переулок|бульвар|ш\.|шоссе|наб\.|набережная|пл\.|площадь|туп\.|тупик|ал\.|аллея)\s/i.test(
      t,
    ) || /(магистраль|проспект|бульвар|набережная|шоссе|переулок|аллея|площадь)$/i.test(t)
  )
}

function isHousePart(part) {
  const t = part.trim()
  return /^[\d]/.test(t) && t.length <= 24
}

/**
 * @param {string[]} parts
 * @returns {string}
 */
function formatShortAddressFromParts(parts) {
  const clean = parts.map((p) => p.trim()).filter((p) => p && !isNoisePart(p))
  if (clean.length === 0) return (parts[0] || '').trim()

  let house = ''
  let street = ''
  let city = ''

  for (const p of clean) {
    if (isStreetPart(p)) street = p
    else if (isHousePart(p) && !house) house = p
  }

  for (let i = clean.length - 1; i >= 0; i -= 1) {
    const p = clean[i]
    if (p === house || p === street) continue
    if (isStreetPart(p) || isHousePart(p)) continue
    city = p
    break
  }

  if (!city) {
    for (const p of clean) {
      if (p === house || p === street || isStreetPart(p) || isHousePart(p)) continue
      city = p
    }
  }

  const out = []
  if (city) out.push(city)
  if (street) out.push(shortenStreet(street))
  if (house) out.push(house)
  if (out.length > 0) return out.join(', ')

  return clean.slice(0, 3).join(', ')
}

/**
 * @param {string} full
 * @param {AddressParts|null} [structured]
 * @returns {string}
 */
export function formatShortAddress(full, structured = null) {
  if (!full || typeof full !== 'string') return ''
  const fromStruct = formatFromStructured(structured)
  if (fromStruct) return fromStruct
  return formatShortAddressFromParts(full.split(','))
}
