import { useEffect, useRef } from 'react'

const clamp = (value, min, max) => Math.max(min, Math.min(max, value))

export function PixelFieldBackground() {
  const canvasRef = useRef(null)
  const frameRef = useRef(0)

  useEffect(() => {
    const canvas = canvasRef.current
    if (!canvas) return

    const ctx = canvas.getContext('2d', { alpha: true })
    if (!ctx) return

    const offscreen = document.createElement('canvas')
    const offscreenCtx = offscreen.getContext('2d', { alpha: true })
    if (!offscreenCtx) return

    const mediaQuery = window.matchMedia('(prefers-reduced-motion: reduce)')
    const pointer = { x: -9999, y: -9999 }

    let width = 0
    let height = 0
    let lowW = 0
    let lowH = 0
    let reducedMotion = mediaQuery.matches
    let stopped = false

    const onPointerMove = (event) => {
      pointer.x = event.clientX
      pointer.y = event.clientY
    }

    const onTouchMove = (event) => {
      if (!event.touches.length) return
      pointer.x = event.touches[0].clientX
      pointer.y = event.touches[0].clientY
    }

    const onPointerLeave = () => {
      pointer.x = -9999
      pointer.y = -9999
    }

    const onMotionChange = (event) => {
      reducedMotion = event.matches
    }

    const resize = () => {
      width = window.innerWidth
      height = window.innerHeight

      const dpr = clamp(window.devicePixelRatio || 1, 1, 1.8)
      canvas.width = Math.max(1, Math.floor(width * dpr))
      canvas.height = Math.max(1, Math.floor(height * dpr))
      canvas.style.width = `${width}px`
      canvas.style.height = `${height}px`

      ctx.setTransform(dpr, 0, 0, dpr, 0, 0)

      const cellSize = width < 768 ? 7 : 6
      lowW = Math.max(1, Math.ceil(width / cellSize))
      lowH = Math.max(1, Math.ceil(height / cellSize))
      offscreen.width = lowW
      offscreen.height = lowH
      offscreenCtx.imageSmoothingEnabled = false
      ctx.imageSmoothingEnabled = false
    }

    const drawFrame = (time) => {
      if (stopped) return

      offscreenCtx.clearRect(0, 0, lowW, lowH)

      const pointerCellX = (pointer.x / Math.max(1, width)) * lowW
      const pointerCellY = (pointer.y / Math.max(1, height)) * lowH
      const radius = width < 768 ? 16 : 22
      const t = time * 0.0012

      for (let y = 0; y < lowH; y += 1) {
        for (let x = 0; x < lowW; x += 1) {
          const waveA = Math.sin(x * 0.12 + t * 1.2)
          const waveB = Math.cos(y * 0.1 - t * 0.9)
          const waveC = Math.sin((x + y) * 0.06 + t * 1.4)
          let energy = 0.42 + waveA * 0.18 + waveB * 0.18 + waveC * 0.22

          if (pointer.x > -1) {
            const dx = x - pointerCellX
            const dy = y - pointerCellY
            const distance = Math.sqrt(dx * dx + dy * dy)
            if (distance < radius) {
              const boost = Math.pow((radius - distance) / radius, 1.8)
              energy += boost * 0.85
            }
          }

          energy = clamp(energy, 0, 1)
          if (energy < 0.1) continue

          const blue = Math.floor(110 + energy * 145)
          const cyan = Math.floor(80 + energy * 120)
          const amber = Math.floor(Math.max(0, (energy - 0.6) * 240))
          const alpha = 0.12 + energy * 0.48
          offscreenCtx.fillStyle = `rgba(${amber}, ${cyan}, ${blue}, ${alpha})`
          offscreenCtx.fillRect(x, y, 1, 1)
        }
      }

      ctx.clearRect(0, 0, width, height)
      ctx.globalCompositeOperation = 'source-over'
      ctx.drawImage(offscreen, 0, 0, width, height)

      if (!reducedMotion) {
        frameRef.current = requestAnimationFrame(drawFrame)
      }
    }

    resize()
    frameRef.current = requestAnimationFrame(drawFrame)

    window.addEventListener('resize', resize)
    window.addEventListener('mousemove', onPointerMove, { passive: true })
    window.addEventListener('touchmove', onTouchMove, { passive: true })
    window.addEventListener('touchstart', onTouchMove, { passive: true })
    window.addEventListener('mouseout', onPointerLeave)
    mediaQuery.addEventListener('change', onMotionChange)

    return () => {
      stopped = true
      cancelAnimationFrame(frameRef.current)
      window.removeEventListener('resize', resize)
      window.removeEventListener('mousemove', onPointerMove)
      window.removeEventListener('touchmove', onTouchMove)
      window.removeEventListener('touchstart', onTouchMove)
      window.removeEventListener('mouseout', onPointerLeave)
      mediaQuery.removeEventListener('change', onMotionChange)
    }
  }, [])

  return (
    <div className='pixel-field-layer' aria-hidden='true'>
      <canvas ref={canvasRef} className='pixel-field-canvas' />
    </div>
  )
}
