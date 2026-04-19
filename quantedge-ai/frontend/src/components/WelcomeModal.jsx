import React, { useState, useEffect } from 'react';
import { Search, BarChart2, Cpu, Rocket, ArrowRight, X } from 'lucide-react';
import { C, GRAD } from '../constants.js';

const SLIDES = [
  {
    emoji: '👋',
    titleFn: (username) => `Welcome, ${username}!`,
    body: 'QuantEdge AI is your institutional-grade quant research platform — built for serious traders who want data-driven edge in the Indian markets.',
    highlight: null,
  },
  {
    emoji: '⚡',
    titleFn: () => 'What you can do',
    body: null,
    highlight: [
      { icon: <Search size={14} />,    text: 'Scan NSE stocks in real-time with live WebSocket prices' },
      { icon: <BarChart2 size={14} />, text: 'Get AI-powered deep analysis and trade theses from Claude' },
      { icon: <Cpu size={14} />,       text: 'Train XGBoost / LightGBM models on real OHLCV data' },
      { icon: <Rocket size={14} />,    text: 'Run AlphaScan to find the best setup in NIFTY 500' },
    ],
  },
  {
    emoji: '🚀',
    titleFn: () => 'Ready to start?',
    body: 'Head to the Scanner tab, add stocks to your watchlist, and hit "Run Scan" to get your first real-time analysis. Your paper portfolio starts with ₹10,00,000.',
    highlight: null,
  },
];

export function WelcomeModal({ username, onClose }) {
  const [step, setStep] = useState(0);
  const [animating, setAnimating] = useState(false);

  // Close on Escape
  useEffect(() => {
    const handler = (e) => { if (e.key === 'Escape') onClose(); };
    window.addEventListener('keydown', handler);
    return () => window.removeEventListener('keydown', handler);
  }, [onClose]);

  const goTo = (next) => {
    if (animating) return;
    setAnimating(true);
    setTimeout(() => {
      setStep(next);
      setAnimating(false);
    }, 180);
  };

  const slide = SLIDES[step];
  const isLast = step === SLIDES.length - 1;

  return (
    <div
      style={{
        position: 'fixed', inset: 0, zIndex: 1100,
        background: 'rgba(5, 8, 16, 0.88)',
        backdropFilter: 'blur(10px)',
        WebkitBackdropFilter: 'blur(10px)',
        display: 'flex', alignItems: 'center', justifyContent: 'center',
        padding: '1rem',
        animation: 'qe-fade-in 0.25s ease both',
      }}
    >
      <div
        style={{
          width: '100%', maxWidth: 440,
          background: 'rgba(13, 18, 32, 0.98)',
          border: '1px solid rgba(255,255,255,0.1)',
          borderRadius: 20,
          boxShadow: '0 32px 80px rgba(0,0,0,0.6), 0 0 0 1px rgba(0,212,168,0.08), inset 0 1px 0 rgba(255,255,255,0.06)',
          overflow: 'hidden',
          animation: 'qe-fade-up 0.3s cubic-bezier(0.4,0,0.2,1) both',
          position: 'relative',
        }}
      >
        {/* Ambient glow */}
        <div style={{
          position: 'absolute', top: -60, right: -60,
          width: 200, height: 200, borderRadius: '50%',
          background: 'radial-gradient(circle, rgba(0,212,168,0.1) 0%, transparent 70%)',
          pointerEvents: 'none',
        }} />

        {/* Skip button */}
        <button
          onClick={onClose}
          title="Skip"
          style={{
            position: 'absolute', top: 14, right: 14,
            width: 30, height: 30, borderRadius: 8,
            background: 'rgba(255,255,255,0.05)',
            border: '1px solid rgba(255,255,255,0.08)',
            color: C.muted, cursor: 'pointer',
            display: 'grid', placeItems: 'center',
            transition: 'all 0.15s ease', zIndex: 2,
          }}
          onMouseEnter={(e) => { e.currentTarget.style.color = C.text; e.currentTarget.style.background = 'rgba(255,255,255,0.1)'; }}
          onMouseLeave={(e) => { e.currentTarget.style.color = C.muted; e.currentTarget.style.background = 'rgba(255,255,255,0.05)'; }}
        >
          <X size={14} />
        </button>

        {/* Slide content */}
        <div
          style={{
            padding: '2.2rem 2rem 1.6rem',
            opacity: animating ? 0 : 1,
            transform: animating ? 'translateY(6px)' : 'translateY(0)',
            transition: 'opacity 0.18s ease, transform 0.18s ease',
            minHeight: 220,
          }}
        >
          {/* Emoji */}
          <div style={{ fontSize: 36, marginBottom: 14, lineHeight: 1 }}>
            {slide.emoji}
          </div>

          {/* Title */}
          <h2 style={{
            margin: '0 0 12px', fontSize: 22, fontWeight: 800,
            letterSpacing: -0.5, color: C.text, lineHeight: 1.2,
          }}>
            {slide.titleFn(username)}
          </h2>

          {/* Body text */}
          {slide.body && (
            <p style={{ margin: 0, fontSize: 14, color: C.sub, lineHeight: 1.7 }}>
              {slide.body}
            </p>
          )}

          {/* Highlight list */}
          {slide.highlight && (
            <div style={{ display: 'flex', flexDirection: 'column', gap: 10 }}>
              {slide.highlight.map(({ icon, text }) => (
                <div key={text} style={{ display: 'flex', alignItems: 'center', gap: 10 }}>
                  <div style={{
                    width: 26, height: 26, borderRadius: 7, flexShrink: 0,
                    background: 'rgba(0,212,168,0.1)',
                    border: '1px solid rgba(0,212,168,0.2)',
                    display: 'grid', placeItems: 'center',
                    color: C.teal,
                  }}>
                    {icon}
                  </div>
                  <span style={{ fontSize: 13, color: C.sub, lineHeight: 1.4 }}>{text}</span>
                </div>
              ))}
            </div>
          )}
        </div>

        {/* Step dots + navigation */}
        <div style={{
          padding: '1rem 2rem 1.6rem',
          display: 'flex', alignItems: 'center', justifyContent: 'space-between',
          borderTop: '1px solid rgba(255,255,255,0.06)',
        }}>
          {/* Dots */}
          <div style={{ display: 'flex', gap: 6 }}>
            {SLIDES.map((_, i) => (
              <button
                key={i}
                onClick={() => goTo(i)}
                style={{
                  width: i === step ? 20 : 7,
                  height: 7,
                  borderRadius: 4,
                  background: i === step ? C.teal : 'rgba(255,255,255,0.15)',
                  border: 'none', cursor: 'pointer', padding: 0,
                  transition: 'all 0.25s ease',
                }}
              />
            ))}
          </div>

          {/* Buttons */}
          <div style={{ display: 'flex', gap: 8, alignItems: 'center' }}>
            {!isLast && (
              <button
                onClick={onClose}
                style={{
                  padding: '0.42rem 0.85rem',
                  background: 'transparent',
                  border: '1px solid rgba(255,255,255,0.1)',
                  borderRadius: 9, color: C.muted,
                  fontSize: 13, cursor: 'pointer',
                  transition: 'all 0.15s ease',
                }}
                onMouseEnter={(e) => { e.currentTarget.style.color = C.text; e.currentTarget.style.borderColor = 'rgba(255,255,255,0.2)'; }}
                onMouseLeave={(e) => { e.currentTarget.style.color = C.muted; e.currentTarget.style.borderColor = 'rgba(255,255,255,0.1)'; }}
              >
                Skip
              </button>
            )}
            <button
              onClick={() => isLast ? onClose() : goTo(step + 1)}
              style={{
                padding: '0.42rem 1rem',
                background: GRAD.teal,
                border: 'none', borderRadius: 9,
                color: '#041018', fontWeight: 700, fontSize: 13,
                cursor: 'pointer',
                display: 'inline-flex', alignItems: 'center', gap: 5,
                boxShadow: '0 0 16px rgba(0,212,168,0.3)',
                transition: 'box-shadow 0.18s ease',
              }}
              onMouseEnter={(e) => { e.currentTarget.style.boxShadow = '0 0 24px rgba(0,212,168,0.5)'; }}
              onMouseLeave={(e) => { e.currentTarget.style.boxShadow = '0 0 16px rgba(0,212,168,0.3)'; }}
            >
              {isLast ? 'Get Started' : 'Next'}
              {!isLast && <ArrowRight size={13} />}
            </button>
          </div>
        </div>
      </div>
    </div>
  );
}
