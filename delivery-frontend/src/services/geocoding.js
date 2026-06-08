import {
  formatFromStructured,
  formatShortAddress,
  parseNominatimAddress,
  parseYandexGeoObject,
} from '../utils/formatShortAddress.js'

const YANDEX_KEY = (import.meta.env.VITE_YANDEX_MAPS_API_KEY || '').trim()
const DGIS_KEY = (import.meta.env.VITE_DGIS_API_KEY || '').trim()

/**
 * @typedef {{ id: string, label: string, address: string, lat?: number, lon?: number, source: string }} AddressSuggestion
 */

/**
 * @param {string} query
 * @returns {Promise<AddressSuggestion[]>}
 */
export async function searchAddressSuggestions(query) {
  const q = typeof query === 'string' ? query.trim() : ''
  if (q.length < 2) return []

  if (YANDEX_KEY) {
    const fromSuggest = await yandexSuggest(q).catch(() => [])
    if (fromSuggest.length > 0) return fromSuggest

    const fromGeocode = await yandexGeocodeHttp(q).catch(() => [])
    if (fromGeocode.length > 0) return fromGeocode
  }

  if (DGIS_KEY) {
    const fromDgis = await dgisSuggest(q).catch(() => [])
    if (fromDgis.length > 0) return fromDgis
  }

  return nominatimSearch(q).catch(() => [])
}

/**
 * @param {AddressSuggestion} item
 * @returns {Promise<{ address: string, shortAddress: string, lat: number, lon: number }|null>}
 */
export async function resolveAddressSelection(item) {
  if (!item) return null
  const address = item.address || item.label

  if (item._yandexGeo) {
    return buildResolvedFromYandex(item._yandexGeo, address)
  }

  if (
    typeof item.lat === 'number' &&
    typeof item.lon === 'number' &&
    Number.isFinite(item.lat) &&
    Number.isFinite(item.lon)
  ) {
    const structured = item._structured || null
    return finalizeResolved(address, item.lat, item.lon, structured)
  }

  if (YANDEX_KEY) {
    const resolved = await yandexGeocodeOne(address).catch(() => null)
    if (resolved) return resolved
  }

  if (DGIS_KEY && item.source === 'dgis' && item.id) {
    const resolved = await dgisResolve(item.id).catch(() => null)
    if (resolved) return finalizeResolved(resolved.address || address, resolved.lat, resolved.lon, null)
  }

  const nominatim = await nominatimGeocodeOne(address).catch(() => null)
  if (nominatim) return nominatim

  return null
}

function finalizeResolved(address, lat, lon, structured) {
  const shortAddress = formatFromStructured(structured) || formatShortAddress(address, structured)
  return { address, shortAddress, lat, lon, structured }
}

function buildResolvedFromYandex(geoObject, fallbackAddress) {
  const full =
    geoObject?.metaDataProperty?.GeocoderMetaData?.text?.trim() ||
    geoObject?.name?.trim() ||
    fallbackAddress
  const structured = parseYandexGeoObject(geoObject)
  const pos = geoObject?.Point?.pos
  let lat
  let lon
  if (pos) {
    const parts = String(pos).split(' ').map(Number)
    if (parts.length >= 2) {
      lon = parts[0]
      lat = parts[1]
    }
  }
  if (!Number.isFinite(lat) || !Number.isFinite(lon)) return null
  return finalizeResolved(full, lat, lon, structured)
}

async function yandexSuggest(query) {
  const url = new URL('https://suggest-maps.yandex.ru/v1/suggest')
  url.searchParams.set('apikey', YANDEX_KEY)
  url.searchParams.set('text', query)
  url.searchParams.set('results', '7')
  url.searchParams.set('types', 'geo,house')
  url.searchParams.set('lang', 'ru')

  const res = await fetch(url.toString())
  if (!res.ok) throw new Error(`Yandex suggest: ${res.status}`)
  const data = await res.json()
  const results = data?.results
  if (!Array.isArray(results)) return []

  return results
    .map((row, index) => {
      const title = row?.title?.text?.trim() || ''
      const subtitle = row?.subtitle?.text?.trim() || ''
      const label = subtitle ? `${title}, ${subtitle}` : title
      if (!label) return null
      return {
        id: `yandex-suggest-${index}-${label}`,
        label,
        address: title || label,
        source: 'yandex-suggest',
      }
    })
    .filter(Boolean)
}

async function yandexGeocodeHttp(query) {
  const url = new URL('https://geocode-maps.yandex.ru/1.x/')
  url.searchParams.set('apikey', YANDEX_KEY)
  url.searchParams.set('geocode', query)
  url.searchParams.set('format', 'json')
  url.searchParams.set('results', '7')
  url.searchParams.set('lang', 'ru_RU')

  const res = await fetch(url.toString())
  if (!res.ok) throw new Error(`Yandex geocode: ${res.status}`)
  const data = await res.json()
  const members = data?.response?.GeoObjectCollection?.featureMember
  if (!Array.isArray(members)) return []

  return members
    .map((item, index) => {
      const geo = item?.GeoObject
      const address =
        geo?.metaDataProperty?.GeocoderMetaData?.text?.trim() ||
        geo?.name?.trim() ||
        ''
      if (!address) return null
      const pos = geo?.Point?.pos
      let lat
      let lon
      if (pos) {
        const parts = String(pos).split(' ').map(Number)
        if (parts.length >= 2) {
          lon = parts[0]
          lat = parts[1]
        }
      }
      const structured = parseYandexGeoObject(geo)
      const shortLabel = formatFromStructured(structured) || formatShortAddress(address, structured)
      return {
        id: `yandex-geocode-${index}-${address}`,
        label: shortLabel || address,
        address,
        lat,
        lon,
        source: 'yandex-geocode',
        _yandexGeo: geo,
        _structured: structured,
      }
    })
    .filter(Boolean)
}

async function yandexGeocodeOne(address) {
  const list = await yandexGeocodeHttp(address)
  const first = list[0]
  if (!first?._yandexGeo) return null
  return buildResolvedFromYandex(first._yandexGeo, first.address)
}

async function dgisSuggest(query) {
  const url = new URL('https://catalog.api.2gis.com/3.0/items/geocode')
  url.searchParams.set('key', DGIS_KEY)
  url.searchParams.set('q', query)
  url.searchParams.set('fields', 'items.point,items.full_name')
  url.searchParams.set('page_size', '7')

  const res = await fetch(url.toString())
  if (!res.ok) throw new Error(`2GIS: ${res.status}`)
  const data = await res.json()
  const items = data?.result?.items
  if (!Array.isArray(items)) return []

  return items
    .map((item, index) => {
      const label = item?.full_name?.trim() || item?.name?.trim() || ''
      if (!label) return null
      const point = item?.point
      return {
        id: item?.id ? String(item.id) : `dgis-${index}`,
        label,
        address: label,
        lat: point?.lat,
        lon: point?.lon,
        source: 'dgis',
      }
    })
    .filter(Boolean)
}

async function dgisResolve(itemId) {
  const url = new URL('https://catalog.api.2gis.com/3.0/items/byid')
  url.searchParams.set('key', DGIS_KEY)
  url.searchParams.set('id', itemId)
  url.searchParams.set('fields', 'items.point,items.full_name')

  const res = await fetch(url.toString())
  if (!res.ok) throw new Error(`2GIS resolve: ${res.status}`)
  const data = await res.json()
  const item = data?.result?.items?.[0]
  const point = item?.point
  if (!point || !Number.isFinite(point.lat) || !Number.isFinite(point.lon)) return null
  return {
    address: item?.full_name?.trim() || '',
    lat: point.lat,
    lon: point.lon,
  }
}

async function nominatimSearch(query) {
  const url = new URL('https://nominatim.openstreetmap.org/search')
  url.searchParams.set('q', query)
  url.searchParams.set('format', 'json')
  url.searchParams.set('limit', '7')
  url.searchParams.set('addressdetails', '1')
  url.searchParams.set('countrycodes', 'ru')

  const res = await fetch(url.toString(), {
    headers: { Accept: 'application/json', 'Accept-Language': 'ru' },
  })
  if (!res.ok) throw new Error(`Nominatim: ${res.status}`)
  const data = await res.json()
  if (!Array.isArray(data)) return []

  return data
    .map((row, index) => {
      const label = row?.display_name?.trim() || ''
      if (!label) return null
      const structured = parseNominatimAddress(row)
      const shortLabel = formatFromStructured(structured) || formatShortAddress(label, structured)
      return {
        id: `nominatim-${row.place_id ?? index}`,
        label: shortLabel || label,
        address: label,
        lat: Number(row.lat),
        lon: Number(row.lon),
        source: 'nominatim',
        _structured: structured,
      }
    })
    .filter(Boolean)
}

async function nominatimGeocodeOne(address) {
  const list = await nominatimSearch(address)
  const first = list[0]
  if (!first || !Number.isFinite(first.lat) || !Number.isFinite(first.lon)) return null
  return finalizeResolved(first.address, first.lat, first.lon, first._structured || null)
}

export function getGeocoderProviderLabel() {
  if (YANDEX_KEY) return 'Яндекс Карты'
  if (DGIS_KEY) return '2GIS'
  return 'OpenStreetMap'
}
