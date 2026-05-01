import { useEffect, useRef } from 'react'

const clamp = (value, min, max) => Math.max(min, Math.min(max, value))
const PALETTE = [
  [7, 18, 32],
  [17, 42, 74],
  [52, 109, 186],
  [255, 170, 66],
]
const SCROLL_INTENSITY = 1.2
const TARGET_FPS = 30
const FRAME_MS = 1000 / TARGET_FPS

export function PixelFieldBackground() {
  const canvasRef = useRef(null)
  const frameRef = useRef(0)

  useEffect(() => {
    const canvas = canvasRef.current
    if (!canvas) return

    const ctx = canvas.getContext('2d', { alpha: true })
    if (!ctx) return

    const mediaQuery = window.matchMedia('(prefers-reduced-motion: reduce)')
    const pointer = { x: -9999, y: -9999 }
    const offscreen = document.createElement('canvas')
    const offCtx = offscreen.getContext('2d')
    if (!offCtx) return

    let width = 0
    let height = 0
    let cols = 0
    let rows = 0
    let baseCellSize = 0
    let cellScale = 1
    let pixelSize = 0
    let reducedMotion = mediaQuery.matches
    let stopped = false
    let scrollShift = 0
    let targetScrollShift = 0
    let scrollPulse = 0
    let lastScrollY = window.scrollY || 0
    let lastScrollTime = performance.now()
    let lastRenderTime = 0
    let frameImageData = null
    let frameBuffer = null

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
      if (!reducedMotion && !frameRef.current) {
        frameRef.current = requestAnimationFrame(drawFrame)
      }
    }

    const onScroll = () => {
      const y = window.scrollY || 0
      const now = performance.now()
      const dt = Math.max(16, now - lastScrollTime)
      const velocity = Math.abs(y - lastScrollY) / dt

      const maxScrollable = Math.max(1, document.documentElement.scrollHeight - window.innerHeight)
      const progress = y / maxScrollable
      targetScrollShift = progress * 16 * SCROLL_INTENSITY
      scrollPulse = clamp(scrollPulse + velocity * 0.7 * SCROLL_INTENSITY, 0, 0.45 * SCROLL_INTENSITY)

      lastScrollY = y
      lastScrollTime = now
    }

    const resize = () => {
      width = window.innerWidth
      height = window.innerHeight

      const dpr = clamp(window.devicePixelRatio || 1, 1, 1.15)
      canvas.width = Math.max(1, Math.floor(width * dpr))
      canvas.height = Math.max(1, Math.floor(height * dpr))
      canvas.style.width = `${width}px`
      canvas.style.height = `${height}px`

      ctx.setTransform(dpr, 0, 0, dpr, 0, 0)
      ctx.imageSmoothingEnabled = false

      baseCellSize = width < 768 ? 9 : 8
      pixelSize = baseCellSize * 0.5
      cellScale = pixelSize / baseCellSize
      const maxCells = width < 768 ? 26000 : 36000
      const minPixelSize = Math.sqrt((width * height) / maxCells)
      pixelSize = Math.max(pixelSize, minPixelSize)
      cellScale = pixelSize / baseCellSize
      cols = Math.max(1, Math.ceil(width / pixelSize))
      rows = Math.max(1, Math.ceil(height / pixelSize))

      offscreen.width = cols
      offscreen.height = rows
      frameImageData = offCtx.createImageData(cols, rows)
      frameBuffer = frameImageData.data
    }

    const drawFrame = (time) => {
      if (stopped) return
      if (time - lastRenderTime < FRAME_MS) {
        frameRef.current = requestAnimationFrame(drawFrame)
        return
      }
      lastRenderTime = time

      if (!frameBuffer || !frameImageData) {
        frameRef.current = requestAnimationFrame(drawFrame)
        return
      }

      const pointerCellX = (pointer.x / Math.max(1, width)) * cols * cellScale
      const pointerCellY = (pointer.y / Math.max(1, height)) * rows * cellScale
      const hasPointer = pointer.x > -1
      const radius = width < 768 ? 13 : 16
      const radiusSq = radius * radius
      const t = time * 0.0014
      scrollShift += (targetScrollShift - scrollShift) * 0.05
      scrollPulse = Math.max(0, scrollPulse * 0.88)

      const bgR = 6
      const bgG = 12
      const bgB = 22

      for (let y = 0; y < rows; y += 1) {
        const rowOffset = y * cols * 4
        for (let x = 0; x < cols; x += 1) {
          const idx = rowOffset + x * 4
          const gridX = x * cellScale
          const gridY = y * cellScale
          const flowX = gridX + scrollShift * 0.28 * SCROLL_INTENSITY
          const flowY = gridY + scrollShift * 0.45 * SCROLL_INTENSITY
          const waveA = Math.sin((flowX + t * 1.5) * 0.26)
          const waveB = Math.cos((flowY - t * 1.2) * 0.22)
          const waveC = Math.sin((gridX + gridY + t * 1.8) * 0.13)
          let energy = 0.5 + waveA * 0.2 + waveB * 0.18 + waveC * 0.22
          energy += scrollPulse * 0.08 * SCROLL_INTENSITY

          frameBuffer[idx] = bgR
          frameBuffer[idx + 1] = bgG
          frameBuffer[idx + 2] = bgB
          frameBuffer[idx + 3] = 255

          if (hasPointer) {
            const dx = gridX - pointerCellX
            const dy = gridY - pointerCellY
            const distSq = dx * dx + dy * dy
            if (distSq < radiusSq) {
              const boost = (radiusSq - distSq) / radiusSq
              energy += boost * 0.6
            }
          }

          energy = clamp(energy, 0, 1)
          if (energy < 0.14) continue

          let paletteIndex = Math.floor(energy * PALETTE.length)
          if (paletteIndex >= PALETTE.length) paletteIndex = PALETTE.length - 1
          const [r, g, b] = PALETTE[paletteIndex]
          const mix = 0.25 + energy * 0.6 + scrollPulse * 0.04 * SCROLL_INTENSITY
          frameBuffer[idx] = Math.floor(bgR + (r - bgR) * mix)
          frameBuffer[idx + 1] = Math.floor(bgG + (g - bgG) * mix)
          frameBuffer[idx + 2] = Math.floor(bgB + (b - bgB) * mix)
          frameBuffer[idx + 3] = 255
        }
      }
      offCtx.putImageData(frameImageData, 0, 0)
      ctx.clearRect(0, 0, width, height)
      ctx.drawImage(offscreen, 0, 0, width, height)

      if (!reducedMotion) {
        frameRef.current = requestAnimationFrame(drawFrame)
      } else {
        frameRef.current = 0
      }
    }

    resize()
    frameRef.current = requestAnimationFrame(drawFrame)

    window.addEventListener('resize', resize)
    window.addEventListener('mousemove', onPointerMove, { passive: true })
    window.addEventListener('touchmove', onTouchMove, { passive: true })
    window.addEventListener('touchstart', onTouchMove, { passive: true })
    window.addEventListener('mouseout', onPointerLeave)
    window.addEventListener('scroll', onScroll, { passive: true })
    mediaQuery.addEventListener('change', onMotionChange)

    return () => {
      stopped = true
      cancelAnimationFrame(frameRef.current)
      window.removeEventListener('resize', resize)
      window.removeEventListener('mousemove', onPointerMove)
      window.removeEventListener('touchmove', onTouchMove)
      window.removeEventListener('touchstart', onTouchMove)
      window.removeEventListener('mouseout', onPointerLeave)
      window.removeEventListener('scroll', onScroll)
      mediaQuery.removeEventListener('change', onMotionChange)
    }
  }, [])

  return (
    <div className='pixel-field-layer' aria-hidden='true'>
      <canvas ref={canvasRef} className='pixel-field-canvas' />
    </div>
  )
}
