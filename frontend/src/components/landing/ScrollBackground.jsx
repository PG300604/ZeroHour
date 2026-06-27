import { useEffect, useRef } from 'react'

export default function ScrollBackground() {
  const canvasRef = useRef(null)
  const animRef = useRef(null)
  const scrollRef = useRef(0)

  useEffect(() => {
    const canvas = canvasRef.current
    const ctx = canvas.getContext('2d')

    // Resize handler
    const resize = () => {
      canvas.width = window.innerWidth
      canvas.height = window.innerHeight
    }
    resize()
    window.addEventListener('resize', resize)

    // Scroll handler — normalize scroll to 0-1
    const onScroll = () => {
      const maxScroll = document.documentElement.scrollHeight - window.innerHeight
      scrollRef.current = Math.min(window.scrollY / maxScroll, 1)
    }
    window.addEventListener('scroll', onScroll, { passive: true })

    // Stars — create once
    const STAR_COUNT = 180
    const stars = Array.from({ length: STAR_COUNT }, () => ({
      x: Math.random() * window.innerWidth,
      y: Math.random() * window.innerHeight,
      size: Math.random() * 1.5 + 0.3,
      twinkle: Math.random() * Math.PI * 2, // phase offset
      speed: Math.random() * 0.02 + 0.005,
    }))

    let frame = 0

    const draw = () => {
      frame++
      const t = scrollRef.current // 0 = top, 1 = bottom
      const w = canvas.width
      const h = canvas.height

      // ── Background gradient ──────────────────────────────────────────────
      // Night (t=0): #0D0D0D pure black
      // Dawn (t=1): very subtle dark red-purple #150A0A
      const r = Math.round(13 + t * 8)   // 13 → 21
      const g = Math.round(13 + t * 0)   // 13 → 13
      const b = Math.round(13 + t * 0)   // 13 → 13
      ctx.fillStyle = `rgb(${r},${g},${b})`
      ctx.fillRect(0, 0, w, h)

      // ── Red portal ambient glow — pulsing ────────────────────────────────
      const glowOpacity = 0.06 + Math.sin(frame * 0.01) * 0.02
      const gradient = ctx.createRadialGradient(w * 0.5, h * 0.45, 0, w * 0.5, h * 0.45, w * 0.4)
      gradient.addColorStop(0, `rgba(229,57,53,${glowOpacity})`)
      gradient.addColorStop(1, 'rgba(229,57,53,0)')
      ctx.fillStyle = gradient
      ctx.fillRect(0, 0, w, h)

      // ── Stars — fade out as user scrolls (they "rise above") ─────────────
      const starOpacity = Math.max(0, 1 - t * 2) // fully gone at t=0.5
      if (starOpacity > 0) {
        stars.forEach(star => {
          // Twinkle: opacity oscillates
          const twinkle = 0.3 + Math.sin(frame * star.speed + star.twinkle) * 0.3
          ctx.beginPath()
          ctx.arc(star.x, star.y, star.size, 0, Math.PI * 2)
          ctx.fillStyle = `rgba(255,255,255,${twinkle * starOpacity})`
          ctx.fill()
        })
      }

      // ── Subtle red horizon line at bottom when scrolled ──────────────────
      if (t > 0.3) {
        const horizonOpacity = (t - 0.3) * 0.15
        const horizonGrad = ctx.createLinearGradient(0, h * 0.85, 0, h)
        horizonGrad.addColorStop(0, 'rgba(229,57,53,0)')
        horizonGrad.addColorStop(1, `rgba(229,57,53,${horizonOpacity})`)
        ctx.fillStyle = horizonGrad
        ctx.fillRect(0, h * 0.85, w, h * 0.15)
      }

      animRef.current = requestAnimationFrame(draw)
    }

    draw()

    return () => {
      cancelAnimationFrame(animRef.current)
      window.removeEventListener('resize', resize)
      window.removeEventListener('scroll', onScroll)
    }
  }, [])

  return (
    <canvas
      ref={canvasRef}
      style={{
        position: 'fixed',
        top: 0,
        left: 0,
        width: '100%',
        height: '100%',
        zIndex: 0,
        pointerEvents: 'none',
      }}
    />
  )
}
