import React from 'react';

export function GeminiLogo() {
  return (
    <svg viewBox="0 0 24 24" className="w-full h-full" fill="none" xmlns="http://www.w3.org/2000/svg">
      <path d="M12 2C12 7.5 7.5 12 2 12C7.5 12 12 16.5 12 22C12 16.5 16.5 12 22 12C16.5 12 12 7.5 12 2Z" fill="url(#gemini-grad)" />
      <path d="M19 8C19 9.66 17.66 11 16 11C17.66 11 19 12.34 19 14C19 12.34 20.34 11 22 11C20.34 11 19 9.66 19 8Z" fill="url(#gemini-grad-small)" />
      <defs>
        <linearGradient id="gemini-grad" x1="2" y1="2" x2="22" y2="22" gradientUnits="userSpaceOnUse">
          <stop offset="0%" stopColor="#9BC5FF" />
          <stop offset="50%" stopColor="#C1ADFF" />
          <stop offset="100%" stopColor="#FFD2FB" />
        </linearGradient>
        <linearGradient id="gemini-grad-small" x1="16" y1="8" x2="22" y2="14" gradientUnits="userSpaceOnUse">
          <stop offset="0%" stopColor="#9BC5FF" />
          <stop offset="100%" stopColor="#FFD2FB" />
        </linearGradient>
      </defs>
    </svg>
  );
}

export function DriveLogo() {
  return (
    <svg viewBox="0 0 24 24" className="w-full h-full" fill="none" xmlns="http://www.w3.org/2000/svg">
      <path d="M19.35 15.35L14.7 7.2h-5.4l4.65 8.15z" fill="#0066da" />
      <path d="M9.3 7.2L2.35 19.3h9.4L18.7 7.2z" fill="#10a37f" />
      <path d="M14.7 7.2h9.3a1 1 0 0 1 .85 1.5l-9.3 16.1a1 1 0 0 1-.85.5H5.45z" fill="#ffbc00" />
    </svg>
  );
}

export function CalendarLogo() {
  return (
    <svg viewBox="0 0 24 24" className="w-full h-full" fill="none" xmlns="http://www.w3.org/2000/svg">
      <rect x="3" y="4" width="18" height="16" rx="3" fill="#4285F4" />
      <path d="M3 9h18v11H3V9z" fill="#FFF" />
      <path d="M8 2v4M16 2v4" stroke="#4285F4" strokeWidth="2" strokeLinecap="round" />
      <circle cx="8" cy="13" r="1.5" fill="#4285F4" />
      <circle cx="16" cy="13" r="1.5" fill="#4285F4" />
      <circle cx="8" cy="17" r="1.5" fill="#4285F4" />
      <circle cx="16" cy="17" r="1.5" fill="#4285F4" />
    </svg>
  );
}

export function FirebaseLogo() {
  return (
    <svg viewBox="0 0 24 24" className="w-full h-full" fill="none" xmlns="http://www.w3.org/2000/svg">
      <path d="M3.89 15.66L11.08 2.22a.97.97 0 0 1 1.84 0l7.19 13.44a.97.97 0 0 1-.84 1.48H4.73a.97.97 0 0 1-.84-1.48z" fill="#FFA000" />
      <path d="M3.89 15.66L11.08 2.22c.3-.56 1.1-.56 1.4 0l1.96 3.66L6.5 15.66h-2.61z" fill="#F57C00" />
      <path d="M12 17.14V7.5L6.5 15.66h5.5z" fill="#FFCA28" />
    </svg>
  );
}

export function GmailLogo() {
  return (
    <svg viewBox="0 0 24 24" className="w-full h-full" fill="none" xmlns="http://www.w3.org/2000/svg">
      <rect x="2" y="4" width="20" height="16" rx="3" fill="#EA4335" />
      <path d="M2 7l10 6 10-6v11a2 2 0 0 1-2 2H4a2 2 0 0 1-2-2V7z" fill="#fff" />
      <path d="M2 7l10 6 10-6" stroke="#EA4335" strokeWidth="2" strokeLinecap="round" strokeLinejoin="round" />
    </svg>
  );
}

export function MeetLogo() {
  return (
    <svg viewBox="0 0 24 24" className="w-full h-full" fill="none" xmlns="http://www.w3.org/2000/svg">
      <rect x="2" y="5" width="13" height="14" rx="2" fill="#00897B" />
      <path d="M17 9.5l4.5-3.5v12l-4.5-3.5v-5z" fill="#00acc1" />
      <rect x="5" y="8" width="7" height="8" rx="1" fill="#fff" opacity="0.3" />
    </svg>
  );
}
