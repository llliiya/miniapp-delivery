export default function DobrovozLogo({ size = 32 }) {
  return (
    <svg
      width={size}
      height={size}
      viewBox="0 0 32 32"
      fill="none"
      xmlns="http://www.w3.org/2000/svg"
      aria-hidden="true"
    >
      <rect width="32" height="32" rx="8" fill="var(--color-primary-light)" />
      <path
        d="M7 20.5V12.5C7 11.67 7.67 11 8.5 11H17.5L20 14.5H23.5C24.33 14.5 25 15.17 25 16V20.5"
        stroke="var(--color-primary)"
        strokeWidth="1.5"
        strokeLinecap="round"
        strokeLinejoin="round"
      />
      <rect x="9" y="13" width="7" height="5" rx="1" fill="var(--color-primary)" opacity="0.15" />
      <circle cx="11" cy="21" r="2" fill="var(--color-primary)" />
      <circle cx="22" cy="21" r="2" fill="var(--color-primary)" />
      <path
        d="M17.5 11V9.5C17.5 8.67 18.17 8 19 8H21.5"
        stroke="var(--color-primary)"
        strokeWidth="1.5"
        strokeLinecap="round"
      />
    </svg>
  )
}
