import {
  ArrowRight,
  BrainCircuit,
  Camera,
  CheckCircle2,
  Cpu,
  Download,
  FlipHorizontal,
  Grid3X3,
  Sparkles,
  Smartphone,
  Zap,
  ZoomIn,
} from 'lucide-react'

import { Badge } from '@/components/ui/badge'
import { Button } from '@/components/ui/button'
import { Card, CardContent, CardDescription, CardHeader, CardTitle } from '@/components/ui/card'

const featureCards = [
  {
    title: 'Smart Grid AI',
    description:
      'On-device model predicts composition style in real time and shows the best-fit overlay while framing your shot.',
    icon: BrainCircuit,
  },
  {
    title: 'Confidence Indicator',
    description:
      'Color-aware confidence feedback helps you trust the suggestion before capture.',
    icon: Sparkles,
  },
  {
    title: 'Camera Controls Built In',
    description: 'Flip, zoom, flash, and quick capture with a focused camera-first interface.',
    icon: Camera,
  },
  {
    title: 'Four Composition Modes',
    description: 'Rule of Thirds, Golden Spiral, Leading Lines, and Symmetry overlays.',
    icon: Grid3X3,
  },
]

const compositions = [
  {
    title: 'Rule of Thirds',
    image: '/images/compositions/rule-of-thirds.jpg',
  },
  {
    title: 'Golden Ratio / Spiral',
    image: '/images/compositions/golden-spiral.jpg',
  },
  {
    title: 'Leading Lines',
    image: '/images/compositions/leading-lines.jpg',
  },
  {
    title: 'Symmetry',
    image: '/images/compositions/symmetry.jpg',
  },
]

const flow = [
  {
    title: 'Frame the scene',
    text: 'Launch GridCam, choose front or back camera, and compose your subject in the live preview. You can quickly adjust zoom and framing while the app continuously watches the scene for strong composition patterns.',
  },
  {
    title: 'Let AI classify',
    text: 'GridCam samples frames and runs an on-device MobileNetV2-based model roughly every 1.5 seconds. It scores Rule of Thirds, Golden Spiral, Leading Lines, and Symmetry, then picks the strongest composition match in real time.',
  },
  {
    title: 'Capture confidently',
    text: 'The app overlays the suggested grid and shows confidence feedback so you can reframe before tapping capture. With zoom, flash, and camera flip controls available instantly, you can lock in cleaner shots without breaking flow.',
  },
]

function App() {
  return (
    <div className='relative overflow-x-clip'>
      <div
        className='pointer-events-none absolute inset-x-0 top-0 -z-10 h-[36rem] bg-[radial-gradient(circle_at_50%_20%,rgba(255,202,40,0.35),transparent_45%)]'
        aria-hidden='true'
      />

      <header className='sticky top-0 z-50 border-b border-white/10 bg-slate-950/75 backdrop-blur'>
        <div className='container-shell flex h-16 items-center justify-between'>
          <a href='#home' className='flex items-center gap-3'>
            <img
              src='/images/branding/gridcam-logo.png'
              alt='GridCam logo'
              className='h-10 w-10 rounded-full border border-white/20'
            />
            <span className='text-lg font-semibold tracking-tight'>GridCam</span>
          </a>
          <nav className='hidden items-center gap-6 text-sm text-slate-200 md:flex'>
            <a href='#features' className='transition-colors hover:text-primary'>
              Features
            </a>
            <a href='#how-it-works' className='transition-colors hover:text-primary'>
              How it works
            </a>
            <a href='#compositions' className='transition-colors hover:text-primary'>
              Compositions
            </a>
          </nav>
          <div className='flex items-center gap-2'>
            <Button asChild size='sm'>
              <a
                href='https://github.com/dark7462/gridcam/releases/download/v1.0/gridcam-v1.0.apk'
                target='_blank'
                rel='noreferrer'
              >
                <Download className='size-4' />
                Download
              </a>
            </Button>
            <Button asChild variant='secondary' size='sm'>
              <a href='https://github.com/dark7462/gridcam' target='_blank' rel='noreferrer' aria-label='GridCam GitHub repository'>
                <svg
                  viewBox='0 0 24 24'
                  className='size-4'
                  aria-hidden='true'
                  fill='currentColor'
                >
                  <path d='M12 2C6.48 2 2 6.48 2 12c0 4.41 2.87 8.15 6.84 9.47.5.09.68-.22.68-.48 0-.24-.01-.88-.01-1.73-2.78.61-3.37-1.18-3.37-1.18-.46-1.16-1.12-1.47-1.12-1.47-.91-.62.07-.61.07-.61 1.01.07 1.54 1.04 1.54 1.04.9 1.54 2.35 1.1 2.92.84.09-.65.35-1.1.64-1.35-2.22-.25-4.56-1.11-4.56-4.95 0-1.09.39-1.99 1.03-2.69-.1-.25-.45-1.28.1-2.66 0 0 .84-.27 2.75 1.03A9.5 9.5 0 0 1 12 6.8c.85 0 1.71.11 2.51.34 1.91-1.3 2.75-1.03 2.75-1.03.55 1.38.2 2.41.1 2.66.64.7 1.03 1.6 1.03 2.69 0 3.85-2.35 4.7-4.58 4.95.36.31.68.92.68 1.86 0 1.34-.01 2.42-.01 2.75 0 .27.18.58.69.48A10.01 10.01 0 0 0 22 12c0-5.52-4.48-10-10-10Z' />
                </svg>
              </a>
            </Button>
          </div>
        </div>
      </header>

      <main>
        <section id='home' className='container-shell reveal-up py-20 sm:py-28'>
          <div className='mx-auto max-w-3xl text-center'>
            <Badge variant='secondary' className='border-white/20 bg-black/30 text-slate-200'>
              AI-Powered Composition Camera
            </Badge>
            <h1 className='mt-6 text-balance text-4xl font-semibold tracking-tight text-white sm:text-6xl'>
              Shoot better photos with real-time composition guidance.
            </h1>
            <p className='mx-auto mt-6 max-w-2xl text-pretty text-lg text-slate-300'>
              GridCam analyzes your scene on-device and recommends the best framing grid so
              every shot feels intentional.
            </p>
            <div className='mt-10 flex flex-wrap items-center justify-center gap-3'>
              <Button asChild size='lg'>
                <a href='#features'>
                  Explore features <ArrowRight className='size-4' />
                </a>
              </Button>
              <Button asChild variant='secondary' size='lg'>
                <a href='#compositions'>View compositions</a>
              </Button>
            </div>
          </div>
        </section>

        <section id='features' className='container-shell reveal-up py-16' style={{ animationDelay: '120ms' }}>
          <h2 className='section-title'>Built for fast, guided camera decisions</h2>
          <p className='section-subtitle'>
            Everything in GridCam is optimized for real-world shooting speed without losing
            composition quality.
          </p>
          <div className='mt-10 grid gap-4 sm:grid-cols-2'>
            {featureCards.map(({ icon: Icon, title, description }) => (
              <Card
                key={title}
                className='group border-white/10 bg-black/35 transition-transform duration-300 hover:-translate-y-1 hover:border-primary/50'
              >
                <CardHeader>
                  <div className='mb-3 flex size-10 items-center justify-center rounded-lg border border-primary/40 bg-primary/15 text-primary'>
                    <Icon className='size-5' />
                  </div>
                  <CardTitle>{title}</CardTitle>
                  <CardDescription>{description}</CardDescription>
                </CardHeader>
              </Card>
            ))}
          </div>
        </section>

        <section
          id='how-it-works'
          className='container-shell reveal-up py-16'
          style={{ animationDelay: '180ms' }}
        >
          <h2 className='section-title'>How GridCam works</h2>
          <p className='section-subtitle'>
            Inference runs every ~1.5 seconds on-device, so recommendations stay responsive while
            you frame.
          </p>
          <div className='mt-10 grid gap-4 md:grid-cols-3'>
            {flow.map((item, index) => (
              <Card key={item.title} className='border-white/10 bg-black/35'>
                <CardHeader>
                  <Badge className='w-fit'>{`Step ${index + 1}`}</Badge>
                  <CardTitle className='pt-2'>{item.title}</CardTitle>
                </CardHeader>
                <CardContent>
                  <p className='text-sm text-slate-300'>{item.text}</p>
                </CardContent>
              </Card>
            ))}
          </div>
        </section>

        <section
          id='compositions'
          className='container-shell reveal-up py-16'
          style={{ animationDelay: '240ms' }}
        >
          <h2 className='section-title'>Composition examples</h2>
          <p className='section-subtitle'>
            A quick visual look at the framing styles GridCam recognizes and overlays in-app.
          </p>
          <div className='mt-10 grid gap-4 sm:grid-cols-2'>
            {compositions.map((item) => (
              <Card
                key={item.title}
                className='overflow-hidden border-white/10 bg-black/35 transition-transform duration-300 hover:-translate-y-1'
              >
                <div className='relative h-56 overflow-hidden'>
                  <img
                    src={item.image}
                    alt={`${item.title} composition sample`}
                    className='h-full w-full object-cover transition-transform duration-500 hover:scale-105 motion-reduce:hover:scale-100'
                    loading='lazy'
                  />
                  <div className='absolute inset-x-0 bottom-0 bg-gradient-to-t from-black/85 to-transparent px-4 py-3'>
                    <Badge variant='outline' className='border-white/30 bg-black/40 text-white'>
                      {item.title}
                    </Badge>
                  </div>
                </div>
              </Card>
            ))}
          </div>
        </section>

        <section className='container-shell reveal-up py-16' style={{ animationDelay: '300ms' }}>
          <h2 className='section-title'>Tech stack snapshot</h2>
          <div className='mt-10 grid gap-4 md:grid-cols-2'>
            <Card className='border-white/10 bg-black/35'>
              <CardHeader>
                <CardTitle className='flex items-center gap-2'>
                  <Cpu className='size-4 text-primary' /> AI Runtime
                </CardTitle>
              </CardHeader>
              <CardContent className='space-y-3 text-sm text-slate-300'>
                <p>MobileNetV2-based classifier exported to TFLite / LiteRT.</p>
                <p>Four-class composition output with confidence-aware display threshold.</p>
              </CardContent>
            </Card>
            <Card className='border-white/10 bg-black/35'>
              <CardHeader>
                <CardTitle className='flex items-center gap-2'>
                  <Smartphone className='size-4 text-primary' /> Camera UX
                </CardTitle>
              </CardHeader>
              <CardContent className='space-y-2 text-sm text-slate-300'>
                <p className='flex items-center gap-2'>
                  <CheckCircle2 className='size-4 text-primary' /> <ZoomIn className='size-4' />{' '}
                  Quick zoom switching
                </p>
                <p className='flex items-center gap-2'>
                  <CheckCircle2 className='size-4 text-primary' /> <FlipHorizontal className='size-4' /> Front/back flip
                </p>
                <p className='flex items-center gap-2'>
                  <CheckCircle2 className='size-4 text-primary' /> <Zap className='size-4' /> Flash control + capture pipeline
                </p>
              </CardContent>
            </Card>
          </div>
        </section>

        <section id='cta' className='container-shell reveal-up py-20' style={{ animationDelay: '360ms' }}>
          <Card className='border-primary/40 bg-black/45 text-center'>
            <CardHeader>
              <CardTitle className='text-2xl sm:text-3xl'>Ready to frame smarter shots?</CardTitle>
              <CardDescription className='mx-auto max-w-xl text-slate-300'>
                GridCam combines camera controls and composition intelligence in one focused
                Android shooting experience.
              </CardDescription>
            </CardHeader>
            <CardContent>
              <Button asChild size='lg'>
                <a href='https://github.com/dark7462/gridcam' target='_blank' rel='noreferrer'>
                  View project on GitHub <ArrowRight className='size-4' />
                </a>
              </Button>
            </CardContent>
          </Card>
        </section>
      </main>

      <footer className='border-t border-white/10 py-8 text-center text-sm text-slate-400'>
        <p>GridCam • AI composition assistant for mobile photography</p>
      </footer>
    </div>
  )
}

export default App
