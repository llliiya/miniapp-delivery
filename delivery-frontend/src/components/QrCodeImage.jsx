import { useEffect, useState } from 'react'

/**
 * Renders a QR code for the given text using a lightweight API fallback.
 * No extra npm dependency required.
 */
export default function QrCodeImage({ value, size = 200, alt = 'QR-код' }) {
  const [src, setSrc] = useState('')

  useEffect(() => {
    if (!value) {
      setSrc('')
      return
    }
    const encoded = encodeURIComponent(value)
    setSrc(`https://api.qrserver.com/v1/create-qr-code/?size=${size}x${size}&data=${encoded}`)
  }, [value, size])

  if (!src) return null

  return (
    <img
      src={src}
      alt={alt}
      width={size}
      height={size}
      style={{ display: 'block', margin: '0 auto', borderRadius: 8 }}
    />
  )
}
